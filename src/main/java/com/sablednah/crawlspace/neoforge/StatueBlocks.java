package com.sablednah.crawlspace.neoforge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.sablednah.crawlspace.CrawlSpace;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The objective rooms' statues, from the blueprint's written-out block states
 * (as /setblock takes them). Parsed once each; a state that does not parse is
 * logged and stands as stone bricks, so a typo shows as a plain block rather
 * than a hole.
 */
final class StatueBlocks {

    private static final Map<String, BlockState> PARSED = new ConcurrentHashMap<>();

    private StatueBlocks() {
    }

    /** The block for {@code text}, turned {@code turns} quarter turns clockwise. */
    static BlockState state(String text, int turns) {
        BlockState b = PARSED.computeIfAbsent(text, t -> {
            try {
                return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), t, false).blockState();
            } catch (CommandSyntaxException e) {
                CrawlSpace.LOGGER.warn("CrawlSpace: statue block '{}' does not parse: {}", t, e.getMessage());
                return Blocks.STONE_BRICKS.defaultBlockState();
            }
        });
        return turns == 0 ? b : b.rotate(Rotation.values()[turns & 3]);
    }
}
