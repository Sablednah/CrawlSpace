package com.sablednah.crawlspace.neoforge;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Boss bars for lair bosses: vanilla's own, so every client shows them. A bar
 * is shown to players within range and follows the boss's health until it
 * dies. The bars are kept in memory, but a boss carries its tags, so after a
 * restart or a chunk reload it picks its bar up again when it rejoins the
 * level.
 */
public final class Bosses {

    private static final double RANGE = 40;

    /** Every lair boss carries this tag. */
    static final String TAG = "crawlspace_boss";
    /** ...and this one, followed by its bar's colour, so the bar comes back the same. */
    static final String COLOUR_TAG = "crawlspace_bar_";

    private record Tracked(ServerLevel level, UUID mob, ServerBossEvent bar) {
    }

    private static final Map<UUID, Tracked> TRACKED = new HashMap<>();

    private Bosses() {
    }

    static void track(ServerLevel level, Mob mob, BossEvent.BossBarColor colour) {
        ServerBossEvent bar = new ServerBossEvent(java.util.UUID.randomUUID(), mob.getDisplayName(), colour, BossEvent.BossBarOverlay.NOTCHED_10);
        TRACKED.put(mob.getUUID(), new Tracked(level, mob.getUUID(), bar));
    }

    /** A boss coming back from disk takes its bar again. A new boss is already tracked by the time it joins. */
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof Mob mob)
                || !mob.getTags().contains(TAG) || TRACKED.containsKey(mob.getUUID())) {
            return;
        }
        BossEvent.BossBarColor colour = BossEvent.BossBarColor.PURPLE;
        for (String tag : mob.getTags()) {
            for (BossEvent.BossBarColor c : BossEvent.BossBarColor.values()) {
                if (tag.equals(COLOUR_TAG + c.getName())) {
                    colour = c;
                }
            }
        }
        track(level, mob, colour);
    }

    public static void tick(long gameTime) {
        if (gameTime % 10 != 0 || TRACKED.isEmpty()) {
            return;
        }
        Iterator<Tracked> it = TRACKED.values().iterator();
        while (it.hasNext()) {
            Tracked t = it.next();
            Entity e = t.level().getEntity(t.mob());
            if (!(e instanceof LivingEntity boss) || !boss.isAlive()) {
                t.bar().removeAllPlayers();
                it.remove();
                continue;
            }
            t.bar().setProgress(Math.max(0f, boss.getHealth() / boss.getMaxHealth()));
            for (ServerPlayer p : t.level().players()) {
                if (p.distanceToSqr(boss) <= RANGE * RANGE) {
                    t.bar().addPlayer(p);
                } else {
                    t.bar().removePlayer(p);
                }
            }
        }
    }

    public static void clear() {
        TRACKED.values().forEach(t -> t.bar().removeAllPlayers());
        TRACKED.clear();
    }
}
