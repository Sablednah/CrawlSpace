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
        if (player.tickCount % 20 == 0 && CrawlConfig.hints()) {
            hints(level, player, here.site());
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

    private static final DustParticleOptions RED = new DustParticleOptions(0xD8322A, 0.9f);
    private static final DustParticleOptions GREEN = new DustParticleOptions(0x3FD24A, 0.9f);
    /** How near a hint has to be before it shows, in blocks. */
    private static final int HINT_RANGE = 9;

    /**
     * Faint dust, sent to this player only, near the things a perception skill
     * would find: red over an unsprung trap, green at a secret wall not yet
     * opened and over a treasure room's hoard.
     */
    private static void hints(ServerLevel level, ServerPlayer player, Site site) {
        CrawlState state = CrawlState.of(level);
        BlockPos o = site.origin();
        for (Trigger t : site.built().blueprint().triggers()) {
            BlockPos p = o.offset(t.x(), t.y(), t.z());
            if (Math.abs(p.getX() - player.getX()) > HINT_RANGE || Math.abs(p.getZ() - player.getZ()) > HINT_RANGE
                    || Math.abs(p.getY() - player.getY()) > 4) {
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

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        HERE.remove(e.getEntity().getUUID());
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
