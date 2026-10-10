package com.sablednah.crawlspace.neoforge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Blueprint;
import com.sablednah.crawlspace.build.Part;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

/**
 * Keeps a dungeon's shell whole ({@link Part#shell()}), so the way down is
 * through it, not round it. Optional: {@code protection.enabled} in the config.
 *
 * <p><b>The client is stopped from mining at all, not corrected afterwards.</b>
 * Inside a dungeon a survival player's {@code block_break_speed} is held at 0,
 * and a vanilla client then never cracks or breaks anything: measured on the
 * Vivo rig on 2026-10-09 (dirt by hand, stone with iron and Efficiency V
 * picks, held four seconds, intact; at 1 all three broke). Cancelling breaks
 * on the server alone would let the client show the block gone and then put
 * it back, a visible correction.</p>
 *
 * <p>Blocks that may still be broken (dressing, trap plates, whatever a player
 * placed) are mined by the server instead: it times the dig from the block and
 * the tool, sends the cracks, and breaks the block when the time is up, so it
 * looks like ordinary mining. The client still reports starting and stopping.</p>
 *
 * <p>Behind that, for everything that does not go through a player's hands:
 * break events on the shell are cancelled (fake players, drills), explosions
 * take only the dressing, pistons cannot move the shell, and shell blocks
 * found missing near a player are put back from the blueprint, for machines
 * that remove blocks without an event at all.</p>
 */
public final class Protection {

    private static final Identifier ID = Identifier.fromNamespaceAndPath(CrawlSpace.MODID, "dungeon_protection");
    /** Multiplies the total by (1 - 1): no mining progress at all, so the client never predicts a break. */
    private static final AttributeModifier NO_MINING = new AttributeModifier(ID, -1.0,
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    /** How long hitting a protected block from outside keeps the hold on, in ticks: you are digging at the dungeon. */
    private static final int HOLD = 60;
    /** How often missing shell near a player is looked for, and how far, in ticks and blocks. */
    private static final int REPAIR_EVERY = 100;
    private static final int REPAIR_RANGE = 8;
    /** How often the "it holds" message may repeat, in ticks. */
    private static final int TELL_EVERY = 200;

    private record Dig(BlockPos pos, BlockState state, long start, float perTick, int stage) {
    }

    private static final Map<UUID, Dig> DIGS = new HashMap<>();
    private static final Map<UUID, Long> HOLD_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> TOLD = new HashMap<>();

    private Protection() {
    }

    /** Whether the block at {@code pos} is part of the dungeon's shell, and still the block the dungeon put there. */
    static boolean isProtected(ServerLevel level, Site site, BlockPos pos) {
        if (!current(site)) {
            return false;
        }
        int code = codeAt(site, pos);
        if (level.getBlockState(pos).is(Vaults.CAGE)) {
            // A vault's cage, over a pedestal: it is what keeps the choice a choice.
            int under = codeAt(site, pos.below());
            if (under != 0 && Blueprint.part(under) == Part.PEDESTAL) {
                return true;
            }
        }
        if (code != 0 && Blueprint.part(code) == Part.PORTCULLIS_GAP) {
            // A dropped portcullis holds; raised, its archway is air and there is nothing to hold.
            return level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.IRON_BARS);
        }
        if (code == 0 || !Blueprint.part(code).shell()) {
            return false;
        }
        BlockState now = level.getBlockState(pos);
        if (now.isAir()) {
            return false;
        }
        // A player's block in a gap the dungeon made (an opened secret wall, say) is theirs to take back.
        return now.getBlock() == site.state(site.built(), code, pos).getBlock();
    }

    /**
     * Whether this dungeon was planned by the planner we have. The blueprint is
     * regenerated from the seed, so one planned by an older version is not the
     * dungeon in the world: protecting it would guard the wrong blocks, and
     * repair would build today's walls into its corridors. Found on the rig's
     * world, planned by planner 1: a quarter of today's shell did not match.
     * Such dungeons are left alone, ordinary blocks.
     */
    static boolean current(Site site) {
        return site.planner() == Site.PLANNER_VERSION;
    }

    private static int codeAt(Site site, BlockPos pos) {
        BlockPos o = site.origin();
        return site.built().blueprint().get(pos.getX() - o.getX(), pos.getY() - o.getY(), pos.getZ() - o.getZ());
    }

    /** Players this applies to: survival ones. Creative builds and tests; adventure cannot break anyway. */
    private static boolean applies(ServerPlayer player) {
        return !(player instanceof FakePlayer) && player.gameMode.isSurvival() && !player.isCreative() && !player.isSpectator();
    }

    /**
     * Every other tick, from {@link Triggers#onTick}: the hold goes on while a
     * survival player is inside a dungeon (their feet in a block it set), or
     * has just hit its shell from outside, and comes off otherwise. Also moves
     * the server-timed digs on, and every few seconds repairs nearby shell.
     */
    static void tick(ServerLevel level, ServerPlayer player, Site site, BlockPos feet) {
        long now = level.getGameTime();
        boolean inside = site != null && current(site) && (codeAt(site, feet) != 0 || codeAt(site, feet.above()) != 0);
        boolean want = CrawlConfig.protect() && applies(player)
                && (inside || HOLD_UNTIL.getOrDefault(player.getUUID(), 0L) > now);
        hold(player, want);
        Dig dig = DIGS.get(player.getUUID());
        if (dig != null) {
            continueDig(level, player, dig, now);
        }
        // Only from inside: Dungeons.at answers for the whole footprint, a box some 240 blocks across and
        // down to the bottom level, and a miner in a mineshaft or cave in that box must not have the
        // tunnels round him filled in where they happen to cross a wall position.
        if (inside && CrawlConfig.protectRepair() && player.tickCount % REPAIR_EVERY == 0) {
            repair(level, site, feet);
        }
    }

    private static void hold(ServerPlayer player, boolean on) {
        AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        if (speed == null || speed.hasModifier(ID) == on) {
            return;
        }
        if (on) {
            speed.addTransientModifier(NO_MINING);
        } else {
            speed.removeModifier(ID);
            stopDig(player);
        }
    }

    /**
     * Left click on a block, as the server hears it. On the shell: say it holds,
     * and put the hold on (a player digging at the dungeon from a cave outside
     * it has no hold yet). On anything else while held: the server mines it.
     */
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof ServerPlayer player)
                || !CrawlConfig.protect() || !applies(player)) {
            return;
        }
        switch (e.getAction()) {
            case ABORT -> stopDig(player);
            case START -> {
                stopDig(player);
                BlockPos pos = e.getPos();
                Site site = Dungeons.at(level, pos).orElse(null);
                if (site != null && isProtected(level, site, pos)) {
                    HOLD_UNTIL.put(player.getUUID(), level.getGameTime() + HOLD);
                    hold(player, true);
                    tellHolds(level, player);
                    return;
                }
                AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
                if (speed != null && speed.hasModifier(ID)) {
                    startDig(level, player, pos);
                }
            }
            default -> {
            }
        }
    }

    /** How fast this player would mine this block without the hold: vanilla's own sum, tool, enchantments, effects and all. */
    private static void startDig(ServerLevel level, ServerPlayer player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        AttributeInstance speed = player.getAttribute(Attributes.BLOCK_BREAK_SPEED);
        speed.removeModifier(ID);
        float perTick = state.getDestroyProgress(player, level, pos);
        speed.addTransientModifier(NO_MINING);
        if (Float.isNaN(perTick) || perTick <= 0f) {
            return; // unbreakable to this player: bedrock, or a block their tool cannot touch at all
        }
        // An instant block (a torch, or dirt with a fast enough tool) goes on the next tick, not inside the
        // event: vanilla's own handling of this same click runs after it returns.
        DIGS.put(player.getUUID(), new Dig(pos.immutable(), state, level.getGameTime(), perTick, -1));
    }

    private static void continueDig(ServerLevel level, ServerPlayer player, Dig dig, long now) {
        if (level.getBlockState(dig.pos()) != dig.state() || !inReach(player, dig.pos())) {
            stopDig(player);
            return;
        }
        float done = (now - dig.start() + 1) * dig.perTick();
        if (done >= 1f) {
            stopDig(player);
            player.gameMode.destroyBlock(dig.pos());
            return;
        }
        int stage = Math.min(9, (int) (done * 10f));
        if (stage != dig.stage()) {
            level.destroyBlockProgress(crackId(player), dig.pos(), stage);
            DIGS.put(player.getUUID(), new Dig(dig.pos(), dig.state(), dig.start(), dig.perTick(), stage));
        }
    }

    private static boolean inReach(ServerPlayer player, BlockPos pos) {
        double reach = player.blockInteractionRange() + 1.0;
        return player.getEyePosition().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) <= reach * reach;
    }

    private static void stopDig(ServerPlayer player) {
        Dig dig = DIGS.remove(player.getUUID());
        if (dig != null && player.level() instanceof ServerLevel level) {
            level.destroyBlockProgress(crackId(player), dig.pos(), -1);
        }
    }

    /**
     * The id the cracks are sent under. Not the player's own: the level sends a
     * breaker's progress to everyone but the breaker, whose client draws its
     * own, and here the client is drawing nothing.
     */
    private static int crackId(ServerPlayer player) {
        return -1 - player.getId();
    }

    /** Behind the hold: whatever breaks a shell block by event (a fake player, a drill, a mod's tool) is refused. */
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !CrawlConfig.protect() || e.getPlayer().isCreative()) {
            return;
        }
        BlockPos pos = e.getPos();
        Site site = Dungeons.at(level, pos).orElse(null);
        if (site == null || !isProtected(level, site, pos)) {
            return;
        }
        e.setCanceled(true);
        if (e.getPlayer() instanceof ServerPlayer player && !(player instanceof FakePlayer)) {
            tellHolds(level, player);
        }
    }

    /**
     * No building over a puzzle room's void: a block on it, or in the air just
     * above it, would be a bridge, and the puzzle is finding the path.
     */
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof ServerPlayer player)
                || !CrawlConfig.protect() || !applies(player)) {
            return;
        }
        BlockPos pos = e.getPos();
        Site site = Dungeons.at(level, pos).orElse(null);
        if (site == null) {
            return;
        }
        if (isVoid(codeAt(site, pos)) || isVoid(codeAt(site, pos.below())) || isVoid(codeAt(site, pos.below(2)))) {
            e.setCanceled(true);
            player.inventoryMenu.sendAllDataToRemote();
            tell(level, player, "The void swallows it. Find the path instead.");
        }
    }

    private static boolean isVoid(int code) {
        return code != 0 && Blueprint.part(code) == Part.VOID;
    }

    /** Explosions keep their damage and break the dressing, but not the shell. */
    public static void onExplode(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !CrawlConfig.protectExplosions()) {
            return;
        }
        Map<Long, Site> sites = new HashMap<>();
        e.getAffectedBlocks().removeIf(pos -> {
            Site site = sites.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4),
                    k -> Dungeons.at(level, pos).orElse(null));
            if (site == null || !site.contains(pos)) {
                site = Dungeons.at(level, pos).orElse(null);
            }
            return site != null && isProtected(level, site, pos);
        });
    }

    /** A piston may not push or pull the shell, nor crush it. */
    public static void onPiston(PistonEvent.Pre e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !CrawlConfig.protect()) {
            return;
        }
        Site site = Dungeons.at(level, e.getPos()).orElse(null);
        if (site == null) {
            return;
        }
        net.minecraft.world.level.block.piston.PistonStructureResolver r = e.getStructureHelper();
        if (r == null || !r.resolve()) {
            return;
        }
        for (BlockPos p : r.getToPush()) {
            if (isProtected(level, site, p)) {
                e.setCanceled(true);
                return;
            }
        }
        for (BlockPos p : r.getToDestroy()) {
            if (isProtected(level, site, p)) {
                e.setCanceled(true);
                return;
            }
        }
    }

    /**
     * Puts back shell blocks missing near a player: the catch-all for whatever
     * removed them without an event. Only into air, never where an entity
     * stands, and never what comes and goes by design (crumbling floor, pit
     * tiles, iron doors, an opened secret wall).
     */
    private static void repair(ServerLevel level, Site site, BlockPos around) {
        BlockPos o = site.origin();
        Blueprint bp = site.built().blueprint();
        CrawlState state = CrawlState.of(level);
        int put = 0;
        for (BlockPos p : BlockPos.betweenClosed(around.offset(-REPAIR_RANGE, -REPAIR_RANGE, -REPAIR_RANGE),
                around.offset(REPAIR_RANGE, REPAIR_RANGE, REPAIR_RANGE))) {
            int code = bp.get(p.getX() - o.getX(), p.getY() - o.getY(), p.getZ() - o.getZ());
            if (code == 0) {
                continue;
            }
            Part part = Blueprint.part(code);
            if (!part.shell() || part.mayBeMissing() || !level.getBlockState(p).isAir()
                    || part == Part.SECRET_WALL && state.hasFired(p) || !level.isLoaded(p)) {
                continue;
            }
            BlockState want = site.state(site.built(), code, p);
            if (want.isAir() || !level.getEntities((net.minecraft.world.entity.Entity) null,
                    new net.minecraft.world.phys.AABB(p)).isEmpty()) {
                continue;
            }
            level.setBlock(p, want, 3);
            put++;
        }
        if (put > 0) {
            CrawlSpace.LOGGER.debug("CrawlSpace protection put back {} shell block(s) near {}", put, around);
        }
    }

    private static void tellHolds(ServerLevel level, ServerPlayer player) {
        tell(level, player, "The dungeon's stonework holds. Find another way.");
    }

    private static void tell(ServerLevel level, ServerPlayer player, String text) {
        long now = level.getGameTime();
        Long last = TOLD.get(player.getUUID());
        if (last != null && now - last < TELL_EVERY) {
            return;
        }
        TOLD.put(player.getUUID(), now);
        player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GOLD), true);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        DIGS.remove(id);
        HOLD_UNTIL.remove(id);
        TOLD.remove(id);
    }
}
