package com.sablednah.crawlspace.neoforge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Trigger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

/**
 * Ice boards (Hypixel's Ice Fill): every tile crossed exactly once. A tile
 * stepped on turns to packed ice; stepping on packed ice again cracks the whole
 * board back to fresh ice. Every tile packed, and the hoard appears where the
 * board's trigger says, with a blessing for whoever finished it. The board's
 * progress is kept in memory: a restart gives a fresh board.
 */
public final class IceBoards {

    /** Per board (its trigger's key): the tiles crossed so far, as world positions. */
    private static final Map<Long, Set<Long>> CROSSED = new HashMap<>();
    /** Per player: the tile they were last on, so standing still counts once. */
    private static final Map<java.util.UUID, Long> ON = new HashMap<>();

    private IceBoards() {
    }

    /** From Triggers' tick: a player on the ground in a dungeon, with their feet at {@code feet}. */
    static void step(ServerLevel level, ServerPlayer player, Site site, BlockPos feet) {
        BlockPos below = feet.below();
        if (!level.getBlockState(below).is(Blocks.ICE) && !level.getBlockState(below).is(Blocks.PACKED_ICE)) {
            ON.remove(player.getUUID());
            return;
        }
        Long was = ON.put(player.getUUID(), below.asLong());
        if (was != null && was == below.asLong()) {
            return;
        }
        BlockPos o = site.origin();
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() != Trigger.Kind.ICE_BOARD) {
                continue;
            }
            boolean mine = false;
            for (int k = 1; k < t.targets().length && !mine; k++) {
                mine = o.offset(t.targets()[k][0], t.targets()[k][1], t.targets()[k][2]).equals(below);
            }
            if (!mine) {
                continue;
            }
            BlockPos key = o.offset(t.x(), t.y(), t.z());
            CrawlState state = CrawlState.of(level);
            if (state.hasFired(key)) {
                return; // solved
            }
            Set<Long> crossed = CROSSED.computeIfAbsent(key.asLong(), k -> new HashSet<>());
            if (!crossed.add(below.asLong())) {
                // Twice on one tile: the board cracks back.
                for (long p : crossed) {
                    level.setBlock(BlockPos.of(p), Blocks.ICE.defaultBlockState(), 3);
                }
                crossed.clear();
                // The tile you stand on is where you start again: on the rig, without this, the tile a
                // reset left you on was never counted, and a perfect walk from there fell one short.
                crossed.add(below.asLong());
                level.setBlock(below, Blocks.PACKED_ICE.defaultBlockState(), 3);
                level.playSound(null, below, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1f, 1.4f);
                Triggers.tell(player, "The ice cracks back. Start again from here: every tile once, and only once.");
                return;
            }
            level.setBlock(below, Blocks.PACKED_ICE.defaultBlockState(), 3);
            level.playSound(null, below, SoundEvents.GLASS_STEP, SoundSource.BLOCKS, 0.8f, 1.6f);
            if (crossed.size() == t.targets().length - 1) {
                state.fire(key);
                CROSSED.remove(key.asLong());
                BlockPos at = o.offset(t.targets()[0][0], t.targets()[0][1], t.targets()[0][2]);
                level.setBlock(at, Blocks.CHEST.defaultBlockState(), 3);
                net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), at,
                        net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
                                net.minecraft.resources.Identifier.fromNamespaceAndPath(CrawlSpace.MODID,
                                        "chests/tier" + Math.min(5, 2 + t.level() / 2))));
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 30, 0.4, 0.6, 0.4, 0.2);
                level.playSound(null, at, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1f);
                Triggers.tell(player, "Every tile crossed once! Something appears beyond the ice.");
                Powers.bless(level, player, at);
            }
            return;
        }
    }

    public static void forget(java.util.UUID player) {
        ON.remove(player);
    }

    public static void clear() {
        CROSSED.clear();
        ON.clear();
    }
}
