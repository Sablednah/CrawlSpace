package com.sablednah.crawlspace.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sablednah.crawlspace.build.Trigger;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Alarm sentries (the Caves of Chaos; Sable's pick, 2026-10-11). Some guard
 * rooms post a Sentry holding a goat horn. Once it sees you (16 blocks, 8 if
 * you are sneaking, and only with a clear line of sight) it raises the horn,
 * and you have three seconds: kill it, and nothing happens; fail, and the horn
 * sounds and the two nearest rooms still asleep on this level wake and come
 * for you. Each sentry sounds at most once.
 */
public final class Sentries {

    static final String TAG = "crawlspace_sentry";
    /** How often a guard room posts a sentry. Always, in a showcase. */
    static final double CHANCE = 0.5;
    private static final int SIGHT = 16;
    private static final int SIGHT_SNEAKING = 8;
    private static final int WARNING_TICKS = 60;
    private static final int ROOMS = 2;

    private static final class Watch {
        final ServerLevel level;
        final UUID mob;
        final Site site;
        final int li;
        long hornAt = -1;
        UUID target;
        /** Players who slipped this sentry's eye, or failed to: asked once each. */
        final java.util.Map<UUID, Boolean> asked = new HashMap<>();

        Watch(ServerLevel level, UUID mob, Site site, int li) {
            this.level = level;
            this.mob = mob;
            this.site = site;
            this.li = li;
        }
    }

    private static final Map<UUID, Watch> WATCHES = new HashMap<>();

    private Sentries() {
    }

    /** Whether this guard room posts a sentry: the dungeon's dice, so the same room always does. */
    static boolean posts(Site site, Trigger t) {
        return com.sablednah.crawlspace.plan.Showcase.on(site.seed())
                || com.sablednah.crawlspace.plan.Dice.of(site.seed(), t.level(), t.x() * 31L + t.z(), 0x5E47L).chance(CHANCE);
    }

    /** Makes {@code mob} the room's sentry. */
    static void post(ServerLevel level, Mob mob, Site site, int li) {
        mob.addTag(TAG);
        // A ZombieMod genus keeps its name after the title: "Sentry Harvester".
        mob.setCustomName(Powers.genusOnly(mob)
                ? Component.literal("Sentry ").withStyle(ChatFormatting.GOLD).append(mob.getCustomName().copy())
                : Component.literal("Sentry").withStyle(ChatFormatting.GOLD));
        mob.setCustomNameVisible(true);
        mob.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOAT_HORN));
        mob.setDropChance(net.minecraft.world.entity.EquipmentSlot.OFFHAND, 0f);
        WATCHES.put(mob.getUUID(), new Watch(level, mob.getUUID(), site, li));
    }

    public static void tick(long gameTime) {
        if (gameTime % 5 != 0 || WATCHES.isEmpty()) {
            return;
        }
        // A copy: sounding the horn wakes rooms, whose monsters join mid-loop (see Crumbles.tick).
        for (Watch w : new ArrayList<>(WATCHES.values())) {
            Entity e = w.level.getEntity(w.mob);
            ServerPlayer target = w.target == null ? null : w.level.getServer().getPlayerList().getPlayer(w.target);
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                if (w.hornAt >= 0 && target != null) {
                    Triggers.tell(target, "The sentry falls before it can sound the alarm.");
                }
                WATCHES.remove(w.mob);
                continue;
            }
            long now = w.level.getGameTime();
            if (w.hornAt < 0) {
                ServerPlayer seen = spot(w, mob);
                if (seen != null) {
                    w.hornAt = now + WARNING_TICKS;
                    w.target = seen.getUUID();
                    mob.setTarget(seen);
                    w.level.playSound(null, mob.blockPosition(), SoundEvents.ARMOR_EQUIP_GENERIC, SoundSource.HOSTILE, 1f, 0.6f);
                    seen.displayClientMessage(Component.literal("The sentry raises its horn! Kill it before it sounds the alarm.")
                            .withStyle(ChatFormatting.RED), true);
                }
                continue;
            }
            if (now >= w.hornAt) {
                WATCHES.remove(w.mob);
                sound(w, mob, target);
            }
        }
    }

    /** The nearest player this sentry can see, or null. Creative and spectating players are not seen. */
    private static ServerPlayer spot(Watch w, Mob mob) {
        ServerLevel level = w.level;
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator() || Boolean.TRUE.equals(w.asked.get(p.getUUID()))) {
                continue;
            }
            double range = p.isShiftKeyDown() ? SIGHT_SNEAKING : SIGHT;
            double d = p.distanceToSqr(mob);
            if (d <= range * range && d < bestD && mob.hasLineOfSight(p)) {
                best = p;
                bestD = d;
            }
        }
        if (best != null && !w.asked.containsKey(best.getUUID())) {
            ServerPlayer p = best;
            boolean slipped = com.sablednah.crawlspace.api.CrawlSpaceApi.perception().map(q -> q.evades(p, w.li)).orElse(false);
            w.asked.put(p.getUUID(), slipped);
            if (slipped) {
                p.displayClientMessage(Component.literal("The sentry's eyes pass over you.").withStyle(ChatFormatting.GRAY), true);
                return null;
            }
        }
        return best;
    }

    /** The horn: the nearest rooms still asleep on this level wake, and everything they hold comes for the player. */
    private static void sound(Watch w, Mob mob, ServerPlayer target) {
        ServerLevel level = w.level;
        BlockPos at = mob.blockPosition();
        level.playSound(null, at, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), SoundSource.HOSTILE, 6f, 1f);
        CrawlState state = CrawlState.of(level);
        BlockPos o = w.site.origin();
        List<BlockPos> asleep = new ArrayList<>();
        List<Trigger> rooms = new ArrayList<>();
        for (Trigger t : w.site.built().blueprint().triggers()) {
            BlockPos p = o.offset(t.x(), t.y(), t.z());
            if (t.kind().isEncounter() && t.level() == w.li && !state.hasFired(p) && !Altars.sleeps(w.site, t)) {
                asleep.add(p);
                rooms.add(t);
            }
        }
        int woke = 0;
        while (woke < ROOMS && !asleep.isEmpty()) {
            int best = 0;
            for (int k = 1; k < asleep.size(); k++) {
                if (asleep.get(k).distSqr(at) < asleep.get(best).distSqr(at)) {
                    best = k;
                }
            }
            BlockPos p = asleep.remove(best);
            Trigger t = rooms.remove(best);
            state.fire(p);
            if (Bestiary.wake(level, w.site, t) == null) {
                continue;
            }
            woke++;
            if (target != null) {
                for (Mob m : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(p).inflate(14),
                        m -> m.getTags().contains(Bestiary.KIN) && m.getTarget() == null)) {
                    var follow = m.getAttribute(Attributes.FOLLOW_RANGE);
                    if (follow != null && follow.getBaseValue() < 64) {
                        follow.setBaseValue(64);
                    }
                    m.setTarget(target);
                }
            }
        }
        if (target != null) {
            target.displayClientMessage(Component.literal(woke > 0
                    ? "The horn sounds! " + (woke == 1 ? "A room" : "Rooms") + " nearby wake" + (woke == 1 ? "s" : "") + " and come for you."
                    : "The horn sounds, and echoes. Nothing answers.").withStyle(ChatFormatting.RED), true);
        }
    }

    public static void clear() {
        WATCHES.clear();
    }
}
