package com.sablednah.crawlspace.neoforge.worldgen;

import com.sablednah.crawlspace.CrawlSpace;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The three registry entries worldgen needs. The structure, its set and its
 * biome tag are data, under {@code data/crawlspace/worldgen}.
 */
public final class CrawlWorldgen {

    private static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, CrawlSpace.MODID);
    private static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, CrawlSpace.MODID);
    private static final DeferredRegister<MapCodec<? extends StructurePlacement>> PLACEMENTS =
            DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, CrawlSpace.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<DungeonStructure>> DUNGEON =
            TYPES.register("dungeon", () -> () -> DungeonStructure.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> DUNGEON_PIECE =
            PIECES.register("dungeon", () -> (StructurePieceType.ContextlessType) DungeonPiece::new);
    /** On 26.3 the placement registry holds codecs directly; there is no placement type any more. */
    public static final DeferredHolder<MapCodec<? extends StructurePlacement>, MapCodec<? extends StructurePlacement>> CONFIGURED_SPREAD =
            PLACEMENTS.register("configured_spread", () -> ConfiguredSpread.CODEC);

    private CrawlWorldgen() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        PIECES.register(modBus);
        PLACEMENTS.register(modBus);
    }
}
