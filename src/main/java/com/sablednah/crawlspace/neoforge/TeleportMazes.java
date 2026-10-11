package com.sablednah.crawlspace.neoforge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.sablednah.crawlspace.build.Blueprint;
import com.sablednah.crawlspace.build.Part;
import com.sablednah.crawlspace.build.Trigger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The teleport maze's pads (see Blueprinter.teleportMaze): stepping onto one
 * sends you to the middle of the cell it names. A pad cannot send you twice in
 * a row without your stepping off it, and the first arrival in the centre says
 * so.
 */
public final class TeleportMazes {

    /** Per player: the pad they were last sent from, until they step off it, and when. */
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Map<UUID, Long> WHEN = new HashMap<>();
    private static final Set<String> REACHED = new HashSet<>();

    private TeleportMazes() {
    }

    /** From Triggers' tick: a player on the ground in a dungeon, feet at {@code feet}. */
    static void step(ServerLevel level, ServerPlayer player, Site site, BlockPos feet) {
        BlockPos below = feet.below();
        BlockPos o = site.origin();
        int code = site.built().blueprint().get(below.getX() - o.getX(), below.getY() - o.getY(), below.getZ() - o.getZ());
        if (code == 0 || Blueprint.part(code) != Part.TP_PAD) {
            LAST.remove(player.getUUID());
            return;
        }
        long now = level.getGameTime();
        Long was = LAST.get(player.getUUID());
        if (was != null && was == below.asLong() || now - WHEN.getOrDefault(player.getUUID(), -100L) < 10) {
            return;
        }
        int lx = below.getX() - o.getX();
        int ly = below.getY() - o.getY();
        int lz = below.getZ() - o.getZ();
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() != Trigger.Kind.TELEPORT_MAZE) {
                continue;
            }
            for (int k = 1; k < t.targets().length; k++) {
                int[] p = t.targets()[k];
                if (p[0] != lx || p[1] != ly || p[2] != lz) {
                    continue;
                }
                BlockPos to = o.offset(p[3], p[4], p[5]);
                level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.3, 0.6, 0.3, 0.3);
                level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.2f);
                player.teleportTo(level, to.getX() + 0.5, to.getY(), to.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, to.getX() + 0.5, to.getY() + 1, to.getZ() + 0.5, 30, 0.3, 0.6, 0.3, 0.1);
                LAST.put(player.getUUID(), to.below().asLong());
                WHEN.put(player.getUUID(), now);
                if (p[3] == t.x() && p[5] == t.z() && REACHED.add(player.getUUID() + "@" + t.x() + "," + t.z() + "," + site.origin().asLong())) {
                    Triggers.tell(player, "The heart of the maze! The pad by the hoard takes you back out.");
                }
                return;
            }
        }
    }

    public static void forget(UUID player) {
        LAST.remove(player);
        WHEN.remove(player);
    }

    public static void clear() {
        LAST.clear();
        WHEN.clear();
        REACHED.clear();
    }
}
