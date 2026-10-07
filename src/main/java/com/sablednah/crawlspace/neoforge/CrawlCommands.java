package com.sablednah.crawlspace.neoforge;

import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Blueprint;
import com.sablednah.crawlspace.build.Blueprinter;
import com.sablednah.crawlspace.plan.DungeonPlan;
import com.sablednah.crawlspace.plan.LevelPlan;
import com.sablednah.crawlspace.plan.Planner;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * {@code /crawlspace}: build a dungeon where you stand, visit its levels, undo
 * it. Operators only (permission level 2): a build replaces whatever is in its
 * way. This is the test bench until dungeons generate with the world.
 */
public final class CrawlCommands {

    static final int MAX_LEVELS = 9;

    private CrawlCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("crawlspace")
                .requires(src -> Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(src))
                .executes(CrawlCommands::help)
                .then(Commands.literal("build")
                        .executes(ctx -> build(ctx, 0, null))
                        .then(Commands.argument("levels", IntegerArgumentType.integer(1, MAX_LEVELS))
                                .executes(ctx -> build(ctx, IntegerArgumentType.getInteger(ctx, "levels"), null))
                                .then(Commands.argument("seed", LongArgumentType.longArg())
                                        .executes(ctx -> build(ctx, IntegerArgumentType.getInteger(ctx, "levels"),
                                                LongArgumentType.getLong(ctx, "seed"))))))
                .then(Commands.literal("goto")
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, MAX_LEVELS))
                                .executes(ctx -> go(ctx, IntegerArgumentType.getInteger(ctx, "level")))
                                .then(Commands.argument("what", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Tour.KINDS, b))
                                        .executes(ctx -> tour(ctx, IntegerArgumentType.getInteger(ctx, "level"),
                                                StringArgumentType.getString(ctx, "what"))))))
                .then(Commands.literal("info").executes(CrawlCommands::info))
                .then(Commands.literal("undo").executes(CrawlCommands::undo))
                .then(Commands.literal("cancel").executes(CrawlCommands::cancel))
                .then(Commands.literal("perception")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.List.of("always", "never", "half", "off"), b))
                                .executes(ctx -> perception(ctx, StringArgumentType.getString(ctx, "mode"))))));
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        say(ctx.getSource(), "CrawlSpace builds a dungeon where you stand.\n"
                + "/crawlspace build [levels] [seed]  - the tower goes 6 blocks south of you\n"
                + "/crawlspace goto <level> [what]  - 0 is the tower, 1 the first level down;\n"
                + "    what: a corridor style (straight, angled, winding, curved) or a room (lair, hall, exit...)\n"
                + "/crawlspace info  - your last build's seed and where it is\n"
                + "/crawlspace undo  - put back what your last build replaced\n"
                + "/crawlspace cancel  - stop builds in progress");
        return 1;
    }

    private static int build(CommandContext<CommandSourceStack> ctx, int levels, Long seedArg) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        ServerLevel level = (ServerLevel) player.level();
        BlockPos origin = player.blockPosition().offset(0, 0, 6);
        int room = (origin.getY() - level.getMinY() - 6 - DungeonPlan.MIN_TOP) / Planner.LEVEL_SPACING + 1;
        if (room < 1) {
            fail(src, "Too close to the bottom of the world for even one level. Go up and try again.");
            return 0;
        }
        int wanted = levels == 0 ? Math.min(6, room) : levels;
        wanted = Math.min(wanted, room);
        long seed = seedArg != null ? seedArg : level.getRandom().nextLong();
        long t0 = System.nanoTime();
        // Plan, then sink the levels until the land over every part of them is deep enough;
        // fewer levels if that would reach the bottom of the world.
        Site site;
        try {
            site = Site.fit(seed, wanted, 1, origin, (x, z) -> ground(level, origin.getX() + x, origin.getZ() + z) - origin.getY(),
                    level.getMinY(), EntranceStyle.of(level.getBiome(origin)));
        } catch (IllegalStateException e) {
            fail(src, "Could not plan seed " + seed + ": " + e.getMessage());
            return 0;
        }
        if (site == null) {
            fail(src, "The ground dips too low near here for even one level above the bottom of the world.");
            return 0;
        }
        Site.Built built = site.built();
        Blueprint bp = built.blueprint();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (site.levels() < wanted) {
            say(src, "Only room for " + site.levels() + " of " + wanted + " level(s) above the bottom of the world here.");
        }
        say(src, "Planned seed " + seed + ", " + site.levels() + " level(s), in " + ms + " ms. Building "
                + bp.blockCount() + " blocks, the first level " + site.top() + " down, a " + site.style()
                + " tower 6 blocks south of you.");
        DungeonPlan plan = built.plan();
        Builds.Placed placed = new Builds.Placed(level, site, built);
        long started = System.currentTimeMillis();
        Builds.build(player.getUUID(), placed, bp,
                pct -> say(src, "Building... " + pct),
                count -> {
                    if (count < 0) {
                        say(src, "Build stopped.");
                    } else {
                        say(src, "Built: " + count + " blocks changed in "
                                + (System.currentTimeMillis() - started) / 1000 + " s. "
                                + "/crawlspace goto 1 visits the first level.");
                        CrawlState.of(level).addBuilt(site); // so its triggers are found after a restart
                        CrawlSpace.LOGGER.info("CrawlSpace built seed {} at {} ({} levels, first {} down, {} tower, {} blocks)",
                                seed, origin, plan.levels().size(), plan.top(), site.style(), count);
                    }
                });
        return 1;
    }

    /**
     * Where the ground is: the lower of two heightmaps, because OCEAN_FLOOR
     * counts treetops as ground and MOTION_BLOCKING_NO_LEAVES counts the top of
     * the sea.
     *
     * <p>The chunk is loaded first. {@code Level.getHeight} answers the world's
     * minimum Y for a chunk that is not loaded, and a footprint read that way
     * looks bottomless: a build just after a teleport was refused on a coast
     * because most of it was still loading.</p>
     */
    static int ground(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return Math.min(level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z),
                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
    }

    private static int go(CommandContext<CommandSourceStack> ctx, int index) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        Builds.Placed last = current(player);
        if (last == null) {
            fail(src, "You are not in a dungeon, and have not built one since the server started. "
                    + "/locate structure crawlspace:dungeon finds one; /crawlspace build makes one.");
            return 0;
        }
        DungeonPlan plan = last.plan();
        if (index > plan.levels().size()) {
            fail(src, "That dungeon has " + plan.levels().size() + " level(s).");
            return 0;
        }
        BlockPos o = last.origin();
        double x;
        double y;
        double z;
        if (index == 0) {
            x = o.getX() + 0.5;
            y = o.getY() + 1;
            z = o.getZ() - 5.5; // outside the tower door
        } else {
            LevelPlan level = plan.levels().get(index - 1);
            int[] s = level.stairsUp.get(0);
            x = o.getX() + s[0] + 2.5; // beside the stair well, in the clear ring round it
            y = o.getY() + Blueprinter.floorY(plan, index - 1) + level.height(s[0] + 2, s[1]);
            z = o.getZ() + s[1] + 0.5;
        }
        player.teleportTo(last.level(), x, y, z, Set.of(), player.getYRot(), player.getXRot(), false);
        say(src, index == 0 ? "At the tower." : "Level " + index + ": " + plan.levels().get(index - 1).theme.name() + ".");
        return 1;
    }

    /** The dungeon you are standing in (generated, or built by anybody, even before a restart), else the last one you built. */
    private static Builds.Placed current(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        return Dungeons.at(level, player.blockPosition())
                .map(site -> new Builds.Placed(level, site, site.built()))
                .orElse(Builds.last(player.getUUID()));
    }

    private static int tour(CommandContext<CommandSourceStack> ctx, int index, String what) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = src.getPlayerOrException();
        Builds.Placed last = current(player);
        if (last == null || index < 1 || index > last.plan().levels().size()) {
            fail(src, last == null ? "No build yet this session." : "That dungeon has levels 1 to " + last.plan().levels().size() + ".");
            return 0;
        }
        LevelPlan level = last.plan().levels().get(index - 1);
        double[] spot = switch (what) {
            case "lever", "trap", "decoy", "secretdoor", "locked" -> Tour.findTrigger(level, last.built().blueprint(), what);
            default -> Tour.find(level, what, level.index * 31L + System.nanoTime() % 7);
        };
        if (spot == null) {
            fail(src, "No " + what + " on level " + index + ". Try one of: " + String.join(", ", Tour.KINDS));
            return 0;
        }
        BlockPos o = last.origin();
        int cx = (int) Math.floor(spot[0]);
        int cz = (int) Math.floor(spot[1]);
        double y = o.getY() + Blueprinter.floorY(last.plan(), index - 1) + level.height(cx, cz);
        player.teleportTo(last.level(), o.getX() + spot[0], y, o.getZ() + spot[1], Set.of(), (float) spot[2], 0f, false);
        say(src, "Level " + index + ", " + what + ".");
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        Builds.Placed last = current(src.getPlayerOrException());
        if (last == null) {
            say(src, "No build yet this session.");
            return 0;
        }
        StringBuilder b = new StringBuilder("Seed " + last.plan().seed() + ", tower at " + last.origin().toShortString() + ".");
        for (LevelPlan l : last.plan().levels()) {
            b.append("\n  ").append(l.index + 1).append(": ").append(l.theme.name()).append(", ")
                    .append(l.rooms.size()).append(" rooms, ").append(l.stairsDown.size()).append(" stair(s) down, ")
                    .append(l.pits.size()).append(" pit(s)");
        }
        // The level you are standing on: its encounters and traps, woken and sprung.
        ServerPlayer player = src.getPlayerOrException();
        BlockPos o = last.origin();
        int py = player.blockPosition().getY() - o.getY();
        for (int li = 0; li < last.plan().levels().size(); li++) {
            int fy = Blueprinter.floorY(last.plan(), li);
            if (py < fy - 4 || py > fy + 8) {
                continue;
            }
            CrawlState state = CrawlState.of(last.level());
            int enc = 0;
            int woken = 0;
            int traps = 0;
            int sprung = 0;
            double nearest = Double.MAX_VALUE;
            for (com.sablednah.crawlspace.build.Trigger t : last.built().blueprint().triggers()) {
                if (t.level() != li) {
                    continue;
                }
                BlockPos p = o.offset(t.x(), t.y(), t.z());
                boolean fired = state.hasFired(p);
                if (t.kind().isEncounter()) {
                    enc++;
                    woken += fired ? 1 : 0;
                    if (!fired) {
                        nearest = Math.min(nearest, Math.sqrt(p.distToCenterSqr(player.position())));
                    }
                } else if (t.kind().isTrap()) {
                    traps++;
                    sprung += fired ? 1 : 0;
                }
            }
            b.setLength(0);
            b.append("You are on level ").append(li + 1).append(": ").append(woken).append(" of ").append(enc)
                    .append(" rooms awake, ").append(sprung).append(" of ").append(traps).append(" traps sprung");
            if (nearest < Double.MAX_VALUE) {
                b.append(", the nearest sleeping room ").append((int) nearest).append(" blocks away");
            }
            say(src, b.append('.').toString());
        }
        return 1;
    }

    private static int undo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        Builds.Undo undo = Builds.takeUndo(src.getPlayerOrException().getUUID());
        if (undo == null) {
            fail(src, "Nothing to undo (or the last build was too big to remember).");
            return 0;
        }
        undo.done = n -> say(src, "Put back " + n + " blocks.");
        Builds.add(undo);
        say(src, "Undoing your last build...");
        return 1;
    }

    /**
     * A stand-in perception check, for testing the hook without an RPG mod:
     * always, never or half the time; off removes it. An RPG mod that
     * registers its own replaces this.
     */
    private static int perception(CommandContext<CommandSourceStack> ctx, String mode) {
        java.util.Random random = new java.util.Random();
        switch (mode) {
            case "off" -> com.sablednah.crawlspace.api.CrawlSpaceApi.setPerception(null);
            case "always", "never", "half" -> com.sablednah.crawlspace.api.CrawlSpaceApi.setPerception(
                    new com.sablednah.crawlspace.api.Perception() {
                        private boolean roll() {
                            return mode.equals("always") || (mode.equals("half") && random.nextBoolean());
                        }

                        @Override
                        public boolean notices(ServerPlayer player, Hidden what, int depth) {
                            return roll();
                        }

                        @Override
                        public boolean disarms(ServerPlayer player, int depth) {
                            return roll();
                        }
                    });
            default -> {
                fail(ctx.getSource(), "always, never, half or off.");
                return 0;
            }
        }
        say(ctx.getSource(), mode.equals("off") ? "Perception: none (CrawlSpace's hints decide)."
                : "Perception: a stand-in that succeeds " + mode + ". Hints set to AUTO now hide what nobody noticed.");
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) {
        say(ctx.getSource(), "Stopped " + Builds.cancelAll() + " build(s).");
        return 1;
    }

    private static void say(CommandSourceStack src, String text) {
        src.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    private static void fail(CommandSourceStack src, String text) {
        src.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.RED));
    }
}
