package com.sablednah.crawlspace.neoforge;

import java.util.ArrayDeque;
import java.util.Deque;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Blueprint;
import com.sablednah.crawlspace.neoforge.worldgen.DungeonPiece;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Ours is the cutter, never the cut (Sable). The dungeon places in the last
 * worldgen step, after mineshafts, ores and springs, so within its own chunk
 * nothing comes after it. But a chunk decorated later spills its features a
 * few blocks into its neighbours, and those can land in a dungeon already
 * built: tuff, gravel, clay, moss, granite, water, the odd ore. Measured with
 * {@code /crawlspace breaches} on three fresh dungeons on the rig: 0.3 to 1% of
 * the shell, every one a gap protection could not hold.
 *
 * <p>A chunk is only loaded as a full chunk once every neighbour has finished
 * its features, so nothing can spill into it after that. Each new chunk inside
 * a dungeon is therefore put right once, the tick after it loads (the event may
 * not touch the level itself): every block the blueprint sets that is not the
 * block it should be is set again.</p>
 */
public final class Restamp {

    private static final ResourceKey<Structure> DUNGEON = ResourceKey.create(Registries.STRUCTURE,
            Identifier.fromNamespaceAndPath(CrawlSpace.MODID, "dungeon"));

    private record Pending(ResourceKey<Level> dimension, ChunkPos chunk) {
    }

    private static final Deque<Pending> QUEUE = new ArrayDeque<>();
    /** Chunks put right per tick: each is a few tens of thousands of block reads. */
    private static final int PER_TICK = 4;

    private Restamp() {
    }

    public static void onLoad(ChunkEvent.Load e) {
        if (e.isNewChunk() && e.getLevel() instanceof ServerLevel level) {
            synchronized (QUEUE) {
                QUEUE.add(new Pending(level.dimension(), e.getChunk().getPos()));
            }
        }
    }

    public static void tick(net.minecraft.server.MinecraftServer server) {
        for (int n = 0; n < PER_TICK; n++) {
            Pending p;
            synchronized (QUEUE) {
                p = QUEUE.poll();
            }
            if (p == null) {
                return;
            }
            ServerLevel level = server.getLevel(p.dimension());
            if (level != null && level.hasChunk(p.chunk().x(), p.chunk().z())) {
                stamp(level, p.chunk());
            }
        }
    }

    public static void clear() {
        synchronized (QUEUE) {
            QUEUE.clear();
        }
    }

    private static void stamp(ServerLevel level, ChunkPos chunk) {
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(DUNGEON);
        if (structure == null) {
            return;
        }
        for (StructureStart start : level.structureManager().startsForStructure(chunk, s -> s == structure)) {
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof DungeonPiece d && d.site().planner() == Site.PLANNER_VERSION) {
                    stamp(level, chunk, d);
                }
            }
        }
    }

    private static void stamp(ServerLevel level, ChunkPos chunk, DungeonPiece piece) {
        Site site = piece.site();
        Site.Built built = site.built();
        Blueprint bp = built.blueprint();
        BlockPos o = site.origin();
        var box = piece.getBoundingBox();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int fixed = 0;
        for (int x = chunk.getMinBlockX(); x <= chunk.getMaxBlockX(); x++) {
            for (int z = chunk.getMinBlockZ(); z <= chunk.getMaxBlockZ(); z++) {
                if (!piece.owns(x - o.getX(), z - o.getZ())) {
                    continue;
                }
                Blueprint.Column col = bp.column(x - o.getX(), z - o.getZ());
                if (col == null) {
                    continue;
                }
                int[] codes = col.codes();
                for (int i = 0; i < codes.length; i++) {
                    if (codes[i] == 0) {
                        continue;
                    }
                    pos.set(x, o.getY() + col.y0() + i, z);
                    if (!box.isInside(pos)) {
                        continue;
                    }
                    BlockState want = site.state(built, codes[i], pos);
                    BlockState now = level.getBlockState(pos);
                    if (now.getBlock() == want.getBlock()) {
                        continue;
                    }
                    level.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    site.afterPlace(built, level, pos.immutable(), codes[i], level.getRandom());
                    fixed++;
                }
            }
        }
        if (fixed > 0) {
            CrawlSpace.LOGGER.debug("CrawlSpace put right {} block(s) in chunk {} of the dungeon at {}", fixed, chunk, o);
        }
    }
}
