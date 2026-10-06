package com.sablednah.crawlspace.neoforge.worldgen;

import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sablednah.crawlspace.neoforge.CrawlConfig;

import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Vanilla's random spread, with spacing and separation read from the
 * world's config instead of the data file, so a server owner can change how
 * common dungeons are without writing a datapack.
 *
 * <p>It extends {@link RandomSpreadStructurePlacement} on purpose: that is the
 * type {@code /locate} knows how to search, and it searches through
 * {@link #spacing()} and {@link #getPotentialStructureChunk}, which read the
 * config here.</p>
 */
public final class ConfiguredSpread extends RandomSpreadStructurePlacement {

    public static final MapCodec<ConfiguredSpread> CODEC = RecordCodecBuilder.mapCodec(
            i -> placementCodec(i).apply(i, ConfiguredSpread::new));

    private ConfiguredSpread(Vec3i locateOffset, StructurePlacement.FrequencyReductionMethod method, float frequency,
            int salt, Optional<StructurePlacement.ExclusionZone> exclusion) {
        super(locateOffset, method, frequency, salt, exclusion, 36, 16, RandomSpreadType.LINEAR);
    }

    @Override
    public int spacing() {
        return CrawlConfig.spacing();
    }

    @Override
    public int separation() {
        return CrawlConfig.separation();
    }

    @Override
    public ChunkPos getPotentialStructureChunk(long seed, int x, int z) {
        int spacing = spacing();
        int rx = Math.floorDiv(x, spacing);
        int rz = Math.floorDiv(z, spacing);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, rx, rz, salt());
        int range = spacing - separation();
        int ox = RandomSpreadType.LINEAR.evaluate(random, range);
        int oz = RandomSpreadType.LINEAR.evaluate(random, range);
        return new ChunkPos(rx * spacing + ox, rz * spacing + oz);
    }

    @Override
    public StructurePlacementType<?> type() {
        return CrawlWorldgen.CONFIGURED_SPREAD.get();
    }
}
