package com.sablednah.crawlspace.neoforge;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Trigger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * False lairs (Tomb of Horrors; Sable, 2026-10-11). The planner makes some
 * lairs a pretender's and hangs the real lair off it behind a secret wall.
 * The pretender looks the part, name and bar and all, at four tenths of the
 * health. When it falls the game says it was too easy, and the real thing
 * growls from behind the wall, so the player knows to look and roughly where.
 * LegendQuest's WIS check on secret doors is how a character finds the seam.
 */
public final class Decoys {

    static final String TAG = "crawlspace_decoy";

    private Decoys() {
    }

    /** Makes a freshly crowned boss a pretender: the same look, much less to it. */
    static void pretend(Mob mob) {
        var health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.addOrReplacePermanentModifier(new AttributeModifier(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(CrawlSpace.MODID, "decoy_health"), -0.6,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            mob.setHealth(mob.getMaxHealth());
        }
        mob.addTag(TAG);
    }

    /** A pretender falls: say so, and let the real one be heard. */
    static void died(ServerLevel level, Mob mob) {
        if (!mob.entityTags().contains(TAG)) {
            return;
        }
        Site site = Dungeons.at(level, mob.blockPosition()).orElse(null);
        BlockPos real = null;
        if (site != null) {
            int li = Arrivals.levelAt(site, mob.blockPosition());
            for (Trigger t : site.built().blueprint().triggers()) {
                if (t.kind() == Trigger.Kind.BOSS && t.level() == li) {
                    real = site.origin().offset(t.x(), t.y(), t.z());
                }
            }
        }
        if (real != null) {
            level.playSound(null, real, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 3f, 0.5f);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, real.getX() + 0.5, real.getY() + 1, real.getZ() + 0.5, 15, 0.6, 0.6, 0.6, 0.01);
        }
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(mob) < 40 * 40) {
                Triggers.tell(p, "That was too easy... It was only a pretender. Something far worse growls behind these walls.");
            }
        }
    }
}
