package com.sablednah.crawlspace.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Blueprinter;
import com.sablednah.crawlspace.build.Trigger;
import com.sablednah.crawlspace.plan.LevelPlan;
import com.sablednah.crawlspace.plan.Link;
import com.sablednah.crawlspace.plan.LinkKind;
import com.sablednah.crawlspace.plan.Room;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Survival rooms (Hades, Dungeon Now Loading; Sable's pick, 2026-10-11). As
 * the room wakes and a player is in it, clear of every doorway, its doorways fill
 * with iron bars, a bar counts down a minute,
 * and a band joins the fight every fifteen seconds. When the minute is up the
 * bars lift, always, and a chest appears with a blessing. The bars say why
 * they are there the moment they drop. A restart mid-fight loses the
 * countdown, so a sealed doorway with no fight behind it is opened the moment
 * a player comes near: a door that never reopens is the bug this guards.
 */
public final class SurvivalRooms {

    static final int SECONDS = 60;
    static final int WAVE_EVERY = 15;
    /** Markers: sealed at door.above(600), opened at door.above(601). */
    private static final int SEALED = 600;
    private static final int OPENED = 601;

    private static final class Fight {
        final ServerLevel level;
        final Site site;
        final Trigger trigger;
        final Room room;
        final List<BlockPos> doors;
        long start = -1;
        final Map<BlockPos, BlockState> was = new HashMap<>();
        final ServerBossEvent bar = new ServerBossEvent(java.util.UUID.randomUUID(), Component.literal("Hold out!").withStyle(ChatFormatting.RED),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6);
        int waves = 1;

        Fight(ServerLevel level, Site site, Trigger trigger, Room room) {
            this.level = level;
            this.site = site;
            this.trigger = trigger;
            this.room = room;
            this.doors = doors(site, trigger, room);
        }
    }

    private static final List<Fight> FIGHTS = new ArrayList<>();

    private SurvivalRooms() {
    }

    private static Room roomOf(Site site, Trigger t) {
        LevelPlan lp = site.built().plan().levels().get(t.level());
        return lp.room(lp.region(t.x(), t.z()));
    }

    /** The bottom block of each of the room's doorways, secret ones left out. */
    private static List<BlockPos> doors(Site site, Trigger t, Room room) {
        LevelPlan lp = site.built().plan().levels().get(t.level());
        List<BlockPos> out = new ArrayList<>();
        for (Link l : lp.links) {
            if (l.kind == LinkKind.SECRET) {
                continue;
            }
            for (int side = 0; side < 2; side++) {
                int[] d = side == 0 ? l.doorA : l.doorB;
                Room r = side == 0 ? l.a : l.b;
                if (d != null && r == room) {
                    out.add(site.origin().offset(d[0], Blueprinter.floorAt(site.built().plan(), t.level(), d[0], d[1]), d[1]));
                }
            }
        }
        return out;
    }

    /**
     * From Bestiary.wake: the first wave is in. The room wakes as a player comes
     * near, often from the corridor, so the doors wait until someone is inside and
     * nobody stands in a doorway: bars dropped on a player push them out of the
     * fight they were meant to be shut into.
     */
    static void start(ServerLevel level, Site site, Trigger t) {
        Room room = roomOf(site, t);
        if (room == null) {
            return;
        }
        FIGHTS.add(new Fight(level, site, t, room));
    }

    /** Whether the doors can drop now: someone inside, nobody within reach of a doorway. */
    private static boolean ready(Fight f, List<ServerPlayer> in) {
        if (in.isEmpty()) {
            return false;
        }
        for (ServerPlayer p : f.level.players()) {
            for (BlockPos door : f.doors) {
                if (Math.abs(p.getX() - (door.getX() + 0.5)) < 2.5 && Math.abs(p.getZ() - (door.getZ() + 0.5)) < 2.5
                        && Math.abs(p.getY() - door.getY()) < 3) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Seals the doors and starts the clock. */
    private static void seal(Fight fight) {
        ServerLevel level = fight.level;
        Trigger t = fight.trigger;
        Site site = fight.site;
        fight.start = level.getGameTime();
        CrawlState state = CrawlState.of(level);
        for (BlockPos door : fight.doors) {
            for (int dy = 0; dy < 3; dy++) {
                BlockPos p = door.above(dy);
                BlockState s = level.getBlockState(p);
                if (s.isAir() || s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
                    fight.was.put(p.immutable(), s);
                }
            }
            state.fire(door.above(SEALED));
        }
        // Doors come down top half first, so a door never pops off as an item.
        fight.was.keySet().stream().sorted((a, b) -> b.getY() - a.getY())
                .forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16));
        for (BlockPos p : fight.was.keySet()) {
            level.setBlock(p, Blocks.IRON_BARS.defaultBlockState(), 3);
        }
        for (BlockPos p : fight.was.keySet()) {
            level.setBlock(p, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), 3);
        }
        BlockPos centre = site.origin().offset(t.x(), t.y(), t.z());
        level.playSound(null, centre, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 2f, 0.5f);
        level.playSound(null, centre, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1f, 0.5f);
        for (ServerPlayer p : inside(fight)) {
            fight.bar.addPlayer(p);
            Triggers.tell(p, "The doors slam shut! Hold out for a minute, and they open again.");
        }
        CrawlSpace.LOGGER.info("CrawlSpace: survival room at {} sealed {} block(s)", centre, fight.was.size());
    }

    private static List<ServerPlayer> inside(Fight f) {
        List<ServerPlayer> out = new ArrayList<>();
        BlockPos o = f.site.origin();
        for (ServerPlayer p : f.level.players()) {
            int x = p.getBlockX() - o.getX();
            int z = p.getBlockZ() - o.getZ();
            if (f.room.contains(x, z) && Math.abs(p.getY() - (o.getY() + f.trigger.y())) < 6) {
                out.add(p);
            }
        }
        return out;
    }

    public static void tick(long gameTime) {
        if (gameTime % 10 != 0 || FIGHTS.isEmpty()) {
            return;
        }
        for (Fight f : new ArrayList<>(FIGHTS)) {
            if (f.start < 0) {
                List<ServerPlayer> in = inside(f);
                if (ready(f, in)) {
                    seal(f);
                }
                continue;
            }
            long elapsed = (f.level.getGameTime() - f.start) / 20;
            List<ServerPlayer> in = inside(f);
            for (ServerPlayer p : in) {
                f.bar.addPlayer(p);
            }
            f.bar.setProgress(Math.max(0f, 1f - elapsed / (float) SECONDS));
            f.bar.setName(Component.literal("Hold out! " + Math.max(0, SECONDS - elapsed) + " s").withStyle(ChatFormatting.RED));
            if (elapsed >= SECONDS) {
                FIGHTS.remove(f);
                finish(f, in);
                continue;
            }
            if (elapsed >= (long) f.waves * WAVE_EVERY && !in.isEmpty()) {
                f.waves++;
                int[] s = f.trigger.targets()[f.level.getRandom().nextInt(f.trigger.targets().length)];
                BlockPos at = f.site.origin().offset(s[0], s[1], s[2]);
                int made = Bestiary.band(f.level, f.site, f.trigger.level(), at, 2 + f.trigger.level() / 2, in.get(0));
                f.level.sendParticles(ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 20, 0.4, 0.6, 0.4, 0.02);
                for (ServerPlayer p : in) {
                    p.sendOverlayMessage(Component.literal("More of them! (" + made + ")").withStyle(ChatFormatting.RED));
                }
            }
        }
    }

    private static void finish(Fight f, List<ServerPlayer> in) {
        open(f.level, f.was);
        CrawlSpace.LOGGER.info("CrawlSpace: survival room at {} held; {} wave(s); doors open", f.site.origin().offset(f.trigger.x(),
                f.trigger.y(), f.trigger.z()), f.waves);
        CrawlState state = CrawlState.of(f.level);
        for (BlockPos door : doors(f.site, f.trigger, f.room)) {
            state.fire(door.above(OPENED));
        }
        f.bar.removeAllPlayers();
        BlockPos at = f.site.origin().offset(f.trigger.x(), f.trigger.y() - 1, f.trigger.z());
        if (!f.level.getBlockState(at).isAir()) {
            at = at.above();
        }
        f.level.setBlock(at, Blocks.CHEST.defaultBlockState(), 3);
        net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(f.level, f.level.getRandom(), at,
                net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(CrawlSpace.MODID, "chests/tier" + Math.min(5, 2 + f.trigger.level() / 2))));
        f.level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.2);
        f.level.playSound(null, at, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1f);
        for (ServerPlayer p : in) {
            Triggers.tell(p, "The bars lift. You held! A chest appears in the middle of the room.");
            Powers.bless(f.level, p, at);
        }
    }

    /** Puts the doorways back as they were: bars out, doors in, bottom half first. */
    private static void open(ServerLevel level, Map<BlockPos, BlockState> was) {
        was.keySet().stream().sorted((a, b) -> a.getY() - b.getY()).forEach(p -> {
            if (level.getBlockState(p).is(Blocks.IRON_BARS)) {
                level.setBlock(p, was.get(p), 2 | 16);
            }
        });
        if (!was.isEmpty()) {
            level.playSound(null, was.keySet().iterator().next(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 2f, 0.6f);
        }
    }

    /**
     * From Triggers' tick: a sealed doorway with no fight behind it (a restart
     * cut one short) is opened as a player comes near. Its doors come back as
     * the plan has them.
     */
    static void mend(ServerLevel level, ServerPlayer player, Site site) {
        CrawlState state = CrawlState.of(level);
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() != Trigger.Kind.SURVIVAL) {
                continue;
            }
            BlockPos centre = site.origin().offset(t.x(), t.y(), t.z());
            if (!centre.closerThan(player.blockPosition(), 24) || FIGHTS.stream().anyMatch(f -> f.trigger == t && f.site == site)) {
                continue;
            }
            Room room = roomOf(site, t);
            if (room == null) {
                continue;
            }
            for (BlockPos door : doors(site, t, room)) {
                if (state.hasFired(door.above(SEALED)) && !state.hasFired(door.above(OPENED))) {
                    for (int dy = 0; dy < 3; dy++) {
                        if (level.getBlockState(door.above(dy)).is(Blocks.IRON_BARS)) {
                            level.setBlock(door.above(dy), Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                    state.fire(door.above(OPENED));
                    Triggers.tell(player, "The bars across a doorway have lifted.");
                }
            }
        }
    }

    public static void clear() {
        for (Fight f : FIGHTS) {
            f.bar.removeAllPlayers();
            open(f.level, f.was);
        }
        FIGHTS.clear();
    }
}
