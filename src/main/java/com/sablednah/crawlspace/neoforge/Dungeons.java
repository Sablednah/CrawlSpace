package com.sablednah.crawlspace.neoforge;

import java.util.Optional;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.neoforge.worldgen.DungeonPiece;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/** Finds which dungeon a position belongs to: one the world generated, or one built by command. */
public final class Dungeons {

    private Dungeons() {
    }

    /** The dungeon whose footprint holds {@code pos}: generated first, then those built by command. */
    public static Optional<Site> at(ServerLevel level, BlockPos pos) {
        Optional<Site> generated = generatedAt(level, pos);
        if (generated.isPresent()) {
            return generated;
        }
        for (Site s : CrawlState.of(level).built()) {
            if (s.contains(pos)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /** The generated dungeon whose footprint holds {@code pos}, if any. */
    public static Optional<Site> generatedAt(ServerLevel level, BlockPos pos) {
        Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
                .getValue(Identifier.fromNamespaceAndPath(CrawlSpace.MODID, "dungeon"));
        if (structure == null) {
            return Optional.empty();
        }
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, structure);
        if (!start.isValid()) {
            return Optional.empty();
        }
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof DungeonPiece d) {
                return Optional.of(d.site());
            }
        }
        return Optional.empty();
    }
}
