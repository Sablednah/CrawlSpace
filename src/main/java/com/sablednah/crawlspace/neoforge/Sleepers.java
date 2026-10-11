package com.sablednah.crawlspace.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.sablednah.crawlspace.build.Trigger;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.BarrelBlock;

/**
 * Sleeping guardians (Mowzie's Frostmaw; Sable's list, 2026-10-11). Some lairs
 * above the bottom level hold a boss asleep with its pack: no AI, snoring. A
 * player who comes within eight blocks without sneaking wakes them, as does
 * hitting one, or opening a chest nearby without sneaking. Sneak, and you can
 * take the lair's hoard from under its nose. The bottom lair has its altar
 * instead, and is never asleep.
 */
public final class Sleepers {

    static final String TAG = "crawlspace_asleep";
    static final double CHANCE = 0.4;
    private static final int HEARS = 8;
    private static final int CHEST_HEARS = 12;

    private record Sleeper(ServerLevel level, UUID id) {
    }

    private static final Map<UUID, Sleeper> ASLEEP = new HashMap<>();

    private Sleepers() {
    }

    /** Whether this lair's boss sleeps: not one waiting on an altar, and on the dungeon's dice. */
    static boolean sleeps(Site site, Trigger t) {
        if (t.kind() != Trigger.Kind.BOSS || Altars.sleeps(site, t)) {
            return false;
        }
        return com.sablednah.crawlspace.plan.Showcase.on(site.seed())
                || com.sablednah.crawlspace.plan.Dice.of(site.seed(), t.level(), 0x5133L).chance(CHANCE);
    }

    /** Puts a freshly woken lair's monster to sleep. */
    static void lull(ServerLevel level, Mob mob) {
        mob.setNoAi(true);
        mob.addTag(TAG);
        ASLEEP.put(mob.getUUID(), new Sleeper(level, mob.getUUID()));
    }

    /** A sleeper loaded back in after a restart: NoAI and the tag were saved with it. */
    public static void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getLevel() instanceof ServerLevel level && e.getEntity() instanceof Mob mob && mob.entityTags().contains(TAG)) {
            ASLEEP.put(mob.getUUID(), new Sleeper(level, mob.getUUID()));
        }
    }

    public static void tick(long gameTime) {
        if (gameTime % 5 != 0 || ASLEEP.isEmpty()) {
            return;
        }
        // A copy: waking one wakes its pack, which changes the map.
        for (Sleeper s : new ArrayList<>(ASLEEP.values())) {
            Entity e = s.level().getEntity(s.id());
            if (!(e instanceof Mob mob) || !mob.isAlive() || !mob.entityTags().contains(TAG)) {
                ASLEEP.remove(s.id());
                continue;
            }
            for (ServerPlayer p : s.level().players()) {
                if (p.isCreative() || p.isSpectator() || p.isShiftKeyDown() || p.distanceToSqr(mob) > HEARS * HEARS
                        || Math.abs(p.getY() - mob.getY()) > 6) {
                    continue;
                }
                wake(s.level(), mob, p, "hears you");
                break;
            }
            if (mob.entityTags().contains(TAG) && gameTime % 60 == mob.getId() % 12 * 5) {
                s.level().playSound(null, mob.blockPosition(), SoundEvents.FOX_SLEEP, SoundSource.HOSTILE, 0.8f, 0.5f);
                s.level().sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getEyeY() + 0.4, mob.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
            }
        }
    }

    /** Hitting a sleeper wakes it, and its pack. */
    public static void onHurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e) {
        if (e.getEntity() instanceof Mob mob && mob.entityTags().contains(TAG) && mob.level() instanceof ServerLevel level) {
            ServerPlayer p = e.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
            wake(level, mob, p, "is struck awake");
        }
    }

    /** Opening a chest or barrel near sleepers, unless sneaking: the lid creaks. */
    static void noise(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (ASLEEP.isEmpty() || player.isCreative() || player.isSpectator()) {
            return;
        }
        var block = level.getBlockState(pos).getBlock();
        if (!(block instanceof ChestBlock) && !(block instanceof BarrelBlock)) {
            return;
        }
        for (Sleeper s : new ArrayList<>(ASLEEP.values())) {
            if (s.level() != level || !(level.getEntity(s.id()) instanceof Mob mob) || mob.distanceToSqr(pos.getCenter()) > CHEST_HEARS * CHEST_HEARS) {
                continue;
            }
            if (player.isShiftKeyDown()) {
                player.sendOverlayMessage(Component.literal("You ease the lid open. It does not stir.").withStyle(ChatFormatting.GRAY));
            } else {
                wake(level, mob, player, "hears the lid creak");
            }
            return;
        }
    }

    /** Wakes a sleeper and everything asleep near it, set on {@code player} if there is one. */
    private static void wake(ServerLevel level, Mob first, ServerPlayer player, String why) {
        for (Sleeper s : new ArrayList<>(ASLEEP.values())) {
            if (s.level() != level || !(level.getEntity(s.id()) instanceof Mob mob) || mob.distanceToSqr(first) > 20 * 20) {
                continue;
            }
            mob.setNoAi(false);
            mob.removeTag(TAG);
            ASLEEP.remove(s.id());
            if (player != null) {
                mob.setTarget(player);
            }
        }
        level.playSound(null, first.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2f, 0.8f);
        String name = first.hasCustomName() ? first.getCustomName().getString() : first.getName().getString();
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(first) < 32 * 32) {
                Triggers.tell(p, name + " " + why + ", and wakes!");
            }
        }
    }

    public static void clear() {
        ASLEEP.clear();
    }
}
