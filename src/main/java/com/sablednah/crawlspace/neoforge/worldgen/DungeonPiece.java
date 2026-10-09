package com.sablednah.crawlspace.neoforge.worldgen;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Blueprint;
import com.sablednah.crawlspace.build.Blueprinter;
import com.sablednah.crawlspace.neoforge.Palettes;
import com.sablednah.crawlspace.neoforge.Site;
import net.minecraft.world.level.block.Block;
import com.sablednah.crawlspace.plan.LevelPlan;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.util.RandomSource;

/**
 * A dungeon as two pieces: the TOWER (its columns, from the surface all the way
 * down) and the BURIED levels (every other column). Each chunk asks each piece
 * for its slice of the blueprint.
 *
 * <p>Two pieces, not one, for CityWorld: it keeps a city off any chunk near a
 * piece whose box reaches the ground, and with one piece covering the whole
 * footprint that would be a fifteen-chunk meadow in the middle of a city.
 * Split, only the tower's few chunks are kept clear, and the buried piece's box
 * stops three blocks under the lowest ground above it. A piece saved before
 * the split has no part and places everything.</p>
 */
public final class DungeonPiece extends StructurePiece {

    public enum Part { ALL, TOWER, BURIED }

    private final Site site;
    private final Part part;

    DungeonPiece(Site site, Part part) {
        super(CrawlWorldgen.DUNGEON_PIECE.get(), 0, box(site, part));
        this.site = site;
        this.part = part;
    }

    DungeonPiece(CompoundTag tag) {
        super(CrawlWorldgen.DUNGEON_PIECE.get(), tag);
        this.site = Site.load(tag.getCompoundOrEmpty("site"));
        this.part = Part.valueOf(tag.getStringOr("part", "ALL"));
        if (site.planner() != Site.PLANNER_VERSION) {
            CrawlSpace.LOGGER.warn("CrawlSpace dungeon at {} was planned by planner {}, this is {}: "
                    + "parts not yet generated may not match", site.origin(), site.planner(), Site.PLANNER_VERSION);
        }
    }

    private static BoundingBox box(Site site, Part part) {
        BlockPos o = site.origin();
        int top = o.getY() + Blueprinter.TOP;
        if (part == Part.TOWER) {
            int r = Site.TOWER_REACH;
            return new BoundingBox(o.getX() - r, site.bottomY(), o.getZ() - r, o.getX() + r, top, o.getZ() + r);
        }
        int r = LevelPlan.RADIUS;
        int buriedTop = part == Part.BURIED ? o.getY() + site.built().buriedTop() : top;
        return new BoundingBox(o.getX() - r, site.bottomY(), o.getZ() - r, o.getX() + r, buriedTop, o.getZ() + r);
    }

    public Site site() {
        return site;
    }

    /** Whether this piece places the blueprint column at (x, z), relative to the origin. */
    public boolean owns(int x, int z) {
        return part == Part.ALL || (part == Part.TOWER) == Site.towerColumn(x, z);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.put("site", site.save());
        tag.putString("part", part.name());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
            RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        Site.Built built = site.built();
        Blueprint bp = built.blueprint();
        BlockPos o = site.origin();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        java.util.List<BlockPos> connect = new java.util.ArrayList<>();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                Blueprint.Column col = bp.column(x - o.getX(), z - o.getZ());
                if (col == null || !owns(x - o.getX(), z - o.getZ())) {
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
                    level.setBlock(pos, site.state(built, codes[i], pos), 2);
                    site.afterPlace(built, level, pos, codes[i], random);
                    if (Palettes.connects(Blueprint.part(codes[i]))) {
                        connect.add(pos.immutable());
                    }
                }
            }
        }
        // Railings and fences join up with what is now around them.
        for (BlockPos p : connect) {
            level.setBlock(p, Block.updateFromNeighbourShapes(level.getBlockState(p), level, p), 2);
        }
    }
}
