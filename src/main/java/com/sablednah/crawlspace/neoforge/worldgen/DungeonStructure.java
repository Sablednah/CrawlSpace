package com.sablednah.crawlspace.neoforge.worldgen;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.neoforge.CrawlConfig;
import com.sablednah.crawlspace.neoforge.EntranceStyle;
import com.sablednah.crawlspace.neoforge.Site;
import com.sablednah.crawlspace.plan.Dice;
import com.sablednah.crawlspace.plan.DungeonPlan;
import com.sablednah.crawlspace.plan.Planner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A CrawlSpace dungeon as a vanilla structure: it starts in one chunk, plans
 * the whole dungeon there, and each chunk it reaches places its own slice
 * ({@link DungeonPiece}). Pieces reach at most 8 chunks from the start, which
 * {@code LevelPlan.RADIUS} is sized to fit.
 */
public final class DungeonStructure extends Structure {

    public static final MapCodec<DungeonStructure> CODEC = simpleCodec(DungeonStructure::new);
    /** Ground samples are this far apart; each cell uses the lowest of the four round it. */
    private static final int SAMPLE = 8;

    public DungeonStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext ctx) {
        if (!CrawlConfig.enabled()) {
            return Optional.empty();
        }
        ChunkPos cp = ctx.chunkPos();
        int x = cp.getMiddleBlockX();
        int z = cp.getMiddleBlockZ();
        int ground = ctx.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.heightAccessor(), ctx.randomState());
        int minY = ctx.heightAccessor().getMinY();
        int minLevels = CrawlConfig.minLevels();
        // A cheap first refusal before any planning: not even the shallowest dungeon fits.
        if (ground - DungeonPlan.MIN_TOP - (minLevels - 1) * Planner.LEVEL_SPACING - 4 < minY + 6) {
            return Optional.empty();
        }
        BlockPos origin = new BlockPos(x, ground, z);
        return Optional.of(new GenerationStub(origin, builder -> {
            long seed = Dice.mix(ctx.seed() ^ Dice.mix(cp.pack()));
            Map<Long, Integer> samples = new HashMap<>();
            Site site = Site.fit(seed, CrawlConfig.maxLevels(), minLevels, origin, (dx, dz) -> {
                int lo = Integer.MAX_VALUE;
                int gx = Math.floorDiv(dx, SAMPLE) * SAMPLE;
                int gz = Math.floorDiv(dz, SAMPLE) * SAMPLE;
                for (int sx = gx; sx <= gx + SAMPLE; sx += SAMPLE) {
                    for (int sz = gz; sz <= gz + SAMPLE; sz += SAMPLE) {
                        final int fx = sx;
                        final int fz = sz;
                        lo = Math.min(lo, samples.computeIfAbsent(((long) sx << 32) ^ (sz & 0xffffffffL),
                                k -> ctx.chunkGenerator().getBaseHeight(x + fx, z + fz, Heightmap.Types.OCEAN_FLOOR_WG,
                                        ctx.heightAccessor(), ctx.randomState())));
                    }
                }
                return lo - ground;
            }, minY, EntranceStyle.of(ctx.biomeSource().getNoiseBiome(
                    QuartPos.fromBlock(x), QuartPos.fromBlock(ground), QuartPos.fromBlock(z), ctx.randomState().sampler())));
            if (site == null) {
                return; // no pieces: the start is invalid and nothing is placed
            }
            CrawlSpace.LOGGER.debug("CrawlSpace dungeon at {}: {} levels, first {} down, {}", origin, site.levels(), site.top(), site.style());
            builder.addPiece(new DungeonPiece(site, DungeonPiece.Part.TOWER));
            builder.addPiece(new DungeonPiece(site, DungeonPiece.Part.BURIED));
        }));
    }

    @Override
    public StructureType<?> type() {
        return CrawlWorldgen.DUNGEON.get();
    }
}
