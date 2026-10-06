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

/**
 * Boss bars for lair bosses: vanilla's own, so every client shows them. A bar
 * is shown to players within range and follows the boss's health until it
 * dies. Kept in memory: after a restart a living boss carries on without its
 * bar.
 */
public final class Bosses {

    private static final double RANGE = 40;

    private record Tracked(ServerLevel level, UUID mob, ServerBossEvent bar) {
    }

    private static final Map<UUID, Tracked> TRACKED = new HashMap<>();

    private Bosses() {
    }

    static void track(ServerLevel level, Mob mob, BossEvent.BossBarColor colour) {
        ServerBossEvent bar = new ServerBossEvent(mob.getDisplayName(), colour, BossEvent.BossBarOverlay.NOTCHED_10);
        TRACKED.put(mob.getUUID(), new Tracked(level, mob.getUUID(), bar));
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
