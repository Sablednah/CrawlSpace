package com.sablednah.crawlspace.neoforge.worldgen;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Blueprint;
import com.sablednah.crawlspace.build.Blueprinter;
import com.sablednah.crawlspace.neoforge.Site;
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
 * The whole dungeon as one piece. Each chunk it covers asks for its slice:
 * the blueprint's columns inside that chunk, set block by block.
 */
public final class DungeonPiece extends StructurePiece {

    private final Site site;

    DungeonPiece(Site site) {
        super(CrawlWorldgen.DUNGEON_PIECE.get(), 0, box(site));
        this.site = site;
    }

    DungeonPiece(CompoundTag tag) {
        super(CrawlWorldgen.DUNGEON_PIECE.get(), tag);
        this.site = Site.load(tag.getCompoundOrEmpty("site"));
        if (site.planner() != Site.PLANNER_VERSION) {
            CrawlSpace.LOGGER.warn("CrawlSpace dungeon at {} was planned by planner {}, this is {}: "
                    + "parts not yet generated may not match", site.origin(), site.planner(), Site.PLANNER_VERSION);
        }
    }

    private static BoundingBox box(Site site) {
        BlockPos o = site.origin();
        int r = LevelPlan.RADIUS;
        return new BoundingBox(o.getX() - r, site.bottomY(), o.getZ() - r,
                o.getX() + r, o.getY() + Blueprinter.TOWER_HEIGHT + 3, o.getZ() + r);
    }

    public Site site() {
        return site;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.put("site", site.save());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
            RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pivot) {
        Site.Built built = site.built();
        Blueprint bp = built.blueprint();
        BlockPos o = site.origin();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
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
                    level.setBlock(pos, site.state(built, codes[i], pos), 2);
                }
            }
        }
    }
}
