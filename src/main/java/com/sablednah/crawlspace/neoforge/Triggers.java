package com.sablednah.crawlspace.neoforge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.sablednah.crawlspace.build.Trigger;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Watches the dungeon's triggers: ordinary blocks and floor tiles, so a lever
 * needs no redstone behind it and a vanilla client needs nothing installed.
 * Levers open their level's locked doors; using a secret wall opens it; a
 * hidden tile springs its trap once.
 */
public final class Triggers {

    /** How often a player's dungeon is looked up again, in ticks. */
    private static final int RECHECK = 40;

    private record Here(Site site, long until) {
    }

    private static final Map<UUID, Here> HERE = new HashMap<>();

    private Triggers() {
    }

    public static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = e.getPos();
        Site site = Dungeons.at(level, pos).orElse(null);
        if (site == null) {
            return;
        }
        // The trap's floor tile, or something standing on the trap's own cell (a corridor stair, a rug).
        if (player.isShiftKeyDown() && (disarm(level, player, site, pos.above()) || disarm(level, player, site, pos))) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        Trigger t = triggerAt(site, pos);
        if (t == null) {
            return;
        }
        switch (t.kind()) {
            case LEVER -> {
                BlockState lever = level.getBlockState(pos);
                if (!(lever.getBlock() instanceof LeverBlock)) {
                    return;
                }
                // The event comes before the lever flips, so the doors follow the state it is about to take.
                boolean open = !lever.getValue(LeverBlock.POWERED);
                int moved = 0;
                for (int[] target : t.targets()) {
                    BlockPos door = site.origin().offset(target[0], target[1], target[2]);
                    BlockState ds = level.getBlockState(door);
                    if (ds.getBlock() instanceof DoorBlock db && db.isOpen(ds) != open) {
                        db.setOpen(null, level, ds, door, open);
                        moved++;
                    }
                }
                if (moved > 0) {
                    tell(player, open ? "Somewhere on this level, an iron door grinds open."
                            : "Somewhere on this level, an iron door slams shut.");
                }
            }
            case SECRET -> {
                CrawlState state = CrawlState.of(level);
                if (state.hasFired(pos)) {
                    return;
                }
                for (int[] target : t.targets()) {
                    BlockPos wall = site.origin().offset(target[0], target[1], target[2]);
                    level.destroyBlock(wall, false);
                    state.fire(wall);
                }
                tell(player, "The wall gives way: a secret door!");
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
            }
            default -> {
            }
        }
    }

    /**
     * Sneak-using the floor tile of a trap you know about: disarm it. With a
     * perception mod registered it is that mod's roll, and a failure springs
     * the trap; without one a known trap disarms.
     */
    private static boolean disarm(ServerLevel level, ServerPlayer player, Site site, BlockPos trapPos) {
        Trigger t = triggerAt(site, trapPos);
        if (t == null || !t.kind().isTrap()) {
            return false;
        }
        CrawlState state = CrawlState.of(level);
        if (state.hasFired(trapPos) || !known(player, trapPos)) {
            return false;
        }
        boolean ok = com.sablednah.crawlspace.api.CrawlSpaceApi.perception().map(p -> p.disarms(player, t.level())).orElse(true);
        state.fire(trapPos);
        if (ok) {
            level.playSound(null, trapPos, SoundEvents.TRIPWIRE_CLICK_OFF, SoundSource.BLOCKS, 1f, 0.8f);
            tell(player, "You disarm the " + (t.kind() == Trigger.Kind.DARTS ? "dart trap." : "gas trap."));
        } else if (t.kind() == Trigger.Kind.DARTS) {
            darts(level, player, trapPos);
        } else {
            gas(level, player, trapPos);
        }
        return true;
    }

    public static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || player.isSpectator() || player.tickCount % 2 != 0) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos feet = player.blockPosition();
        Here here = HERE.get(player.getUUID());
        long now = level.getGameTime();
        if (here == null || now >= here.until() || (here.site() != null && !here.site().contains(feet))) {
            here = new Here(Dungeons.at(level, feet).orElse(null), now + RECHECK);
            HERE.put(player.getUUID(), here);
        }
        if (here.site() == null) {
            return;
        }
        if (player.tickCount % 20 == 0) {
            final Site site = here.site();
            if (CrawlConfig.hints()) {
                hints(level, player, site, null);
            } else {
                com.sablednah.crawlspace.api.CrawlSpaceApi.perception().ifPresent(p -> {
                    notice(level, player, site, p);
                    hints(level, player, site, NOTICED.getOrDefault(player.getUUID(), java.util.Map.of()));
                });
            }
        }
        if (player.tickCount % 10 == 0 && !player.isCreative()) {
            wake(level, player, here.site());
        }
        // Hints show however you move; traps want a foot on the tile.
        if (!player.onGround()) {
            return;
        }
        Trigger t = triggerAt(here.site(), feet);
        if (t == null || !t.kind().isTrap()) {
            return;
        }
        CrawlState state = CrawlState.of(level);
        if (state.hasFired(feet)) {
            return;
        }
        state.fire(feet);
        if (t.kind() == Trigger.Kind.DARTS) {
            darts(level, player, feet);
        } else {
            gas(level, player, feet);
        }
    }

    /** How near a room's monsters wake, in blocks. */
    private static final int WAKE_RANGE = 10;

    /**
     * Wakes any room whose monsters have not appeared yet once a player comes
     * near. Creative players are ignored, so building and testing does not use
     * a room up. On peaceful nothing wakes, and the room stays asleep for when
     * the difficulty changes.
     */
    private static void wake(ServerLevel level, ServerPlayer player, Site site) {
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            return;
        }
        CrawlState state = CrawlState.of(level);
        BlockPos o = site.origin();
        for (Trigger t : site.built().blueprint().triggers()) {
            if (!t.kind().isEncounter()) {
                continue;
            }
            BlockPos p = o.offset(t.x(), t.y(), t.z());
            if (Math.abs(p.getX() - player.getX()) > WAKE_RANGE || Math.abs(p.getZ() - player.getZ()) > WAKE_RANGE
                    || Math.abs(p.getY() - player.getY()) > 5 || state.hasFired(p)) {
                continue;
            }
            state.fire(p);
            String message = Bestiary.wake(level, site, t);
            if (message != null) {
                tell(player, message);
            }
        }
    }

    private static final DustParticleOptions RED = new DustParticleOptions(0xD8322A, 0.9f);
    private static final DustParticleOptions GREEN = new DustParticleOptions(0x3FD24A, 0.9f);
    /** How near a hint has to be before it shows, in blocks. */
    private static final int HINT_RANGE = 9;

    /**
     * Faint dust, sent to this player only, near the things a perception skill
     * would find: red over an unsprung trap, green at a secret wall not yet
     * opened and over a treasure room's hoard.
     */
    /** Per player: hidden things already asked about, and whether they were noticed. Memory only: a restart re-rolls. */
    private static final Map<UUID, Map<Long, Boolean>> NOTICED = new HashMap<>();
    /** How near a hidden thing has to be before a player gets a chance to notice it. */
    private static final int NOTICE_RANGE = 5;

    /**
     * Asks the registered perception about each hidden thing a player has
     * just come near: once per player per thing, so walking past again does
     * not reroll. What they notice they are told about, and shown from then on.
     */
    private static void notice(ServerLevel level, ServerPlayer player, Site site, com.sablednah.crawlspace.api.Perception perception) {
        CrawlState state = CrawlState.of(level);
        Map<Long, Boolean> asked = NOTICED.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        BlockPos o = site.origin();
        for (Trigger t : site.built().blueprint().triggers()) {
            boolean trap = t.kind().isTrap();
            boolean secret = t.kind() == Trigger.Kind.SECRET && t.targets().length > 0 && t.targets()[0][1] == t.y();
            if (!trap && !secret) {
                continue;
            }
            BlockPos p = o.offset(t.x(), t.y(), t.z());
            if (asked.containsKey(p.asLong()) || state.hasFired(p) || p.distSqr(player.blockPosition()) > NOTICE_RANGE * NOTICE_RANGE) {
                continue;
            }
            boolean seen = perception.notices(player, trap ? com.sablednah.crawlspace.api.Perception.Hidden.TRAP
                    : com.sablednah.crawlspace.api.Perception.Hidden.SECRET_DOOR, t.level());
            asked.put(p.asLong(), seen);
            if (seen) {
                tell(player, trap ? "You notice a trap in the floor ahead. Sneak and use it to disarm it."
                        : "Something about this wall is not right...");
            }
        }
    }

    /** Whether this player knows about the hidden thing at {@code pos}: hints show all, else only what they noticed. */
    private static boolean known(ServerPlayer player, BlockPos pos) {
        if (CrawlConfig.hints()) {
            return true;
        }
        Boolean seen = NOTICED.getOrDefault(player.getUUID(), Map.of()).get(pos.asLong());
        return seen != null && seen;
    }

    /**
     * @param noticed null to show every hint (hints on), else only the hidden
     *                things this player noticed (treasure is not hidden, and
     *                with a perception mod in charge it is not pointed out)
     */
    private static void hints(ServerLevel level, ServerPlayer player, Site site, Map<Long, Boolean> noticed) {
        CrawlState state = CrawlState.of(level);
        BlockPos o = site.origin();
        for (Trigger t : site.built().blueprint().triggers()) {
            BlockPos p = o.offset(t.x(), t.y(), t.z());
            if (Math.abs(p.getX() - player.getX()) > HINT_RANGE || Math.abs(p.getZ() - player.getZ()) > HINT_RANGE
                    || Math.abs(p.getY() - player.getY()) > 4) {
                continue;
            }
            if (noticed != null && !Boolean.TRUE.equals(noticed.get(p.asLong()))) {
                continue;
            }
            switch (t.kind()) {
                case DARTS, GAS -> {
                    if (!state.hasFired(p)) {
                        level.sendParticles(player, RED, false, false, p.getX() + 0.5, p.getY() + 0.1, p.getZ() + 0.5, 3, 0.3, 0.02, 0.3, 0);
                    }
                }
                case SECRET -> {
                    // One puff per doorway, from its lower block, spilling out of the wall's faces.
                    if (t.targets().length > 0 && t.targets()[0][1] == t.y() && !state.hasFired(p)) {
                        level.sendParticles(player, GREEN, false, false, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 4, 0.6, 0.6, 0.6, 0);
                    }
                }
                case TREASURE -> level.sendParticles(player, GREEN, false, false, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 3, 0.4, 0.3, 0.4, 0);
                default -> {
                }
            }
        }
    }

    /**
     * No natural monster spawns inside a dungeon built by command: its rooms'
     * own monsters are the danger, and dark rooms otherwise fill with whatever
     * the night brings (five creepers once, which blew the lair's boss half to
     * death before anyone arrived). Generated dungeons get the same through
     * their structure's spawn_overrides. Spawners are untouched.
     */
    public static void onSpawnCheck(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck e) {
        if (e.getSpawnType() != net.minecraft.world.entity.EntitySpawnReason.NATURAL
                || e.getEntityType().getCategory() != net.minecraft.world.entity.MobCategory.MONSTER) {
            return;
        }
        ServerLevel level = e.getLevel().getLevel();
        for (Site s : CrawlState.of(level).built()) {
            if (s.contains(e.getPos())) {
                e.setResult(net.neoforged.neoforge.event.entity.living.MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
                return;
            }
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        HERE.remove(e.getEntity().getUUID());
        NOTICED.remove(e.getEntity().getUUID());
    }

    private static Trigger triggerAt(Site site, BlockPos pos) {
        BlockPos o = site.origin();
        return site.built().blueprint().triggerAt(pos.getX() - o.getX(), pos.getY() - o.getY(), pos.getZ() - o.getZ());
    }

    /**
     * Three darts from the face of the nearest wall, from just above head
     * height and angled down at the chest. A narrow corridor's wall is one
     * block away, and darts fired level from it start inside the player and
     * miss: the first version did exactly that.
     */
    private static void darts(ServerLevel level, ServerPlayer player, BlockPos feet) {
        Direction from = null;
        int best = Integer.MAX_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int k = 1; k <= 4; k++) {
                if (!level.getBlockState(feet.relative(d, k).above()).isAir()) {
                    if (k < best) {
                        best = k;
                        from = d;
                    }
                    break;
                }
            }
        }
        double sx = feet.getX() + 0.5;
        double sy = feet.getY() + 2.4;
        double sz = feet.getZ() + 0.5;
        if (from != null) {
            sx += from.getStepX() * (best - 0.55);
            sz += from.getStepZ() * (best - 0.55);
        }
        for (int i = 0; i < 3; i++) {
            Arrow arrow = new Arrow(level, sx, sy, sz, new ItemStack(Items.ARROW), null);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.shoot(player.getX() - sx, player.getY() + 1.1 - sy, player.getZ() - sz, 1.5f, 4f);
            level.addFreshEntity(arrow);
        }
        level.playSound(null, sx, sy, sz, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 1f, 1.2f);
        tell(player, "Click. Darts fly from the wall!");
    }

    private static void gas(ServerLevel level, ServerPlayer player, BlockPos feet) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, feet.getX() + 0.5, feet.getY() + 0.1, feet.getZ() + 0.5);
        cloud.setRadius(2.5f);
        cloud.setDuration(140);
        cloud.setRadiusPerTick(-0.01f);
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
        level.addFreshEntity(cloud);
        level.playSound(null, feet, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1f, 0.6f);
        tell(player, "Hiss. Poison gas seeps from the floor: step out of the cloud!");
    }

    private static void tell(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GOLD), true);
    }
}
