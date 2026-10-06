package com.sablednah.crawlspace.neoforge;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import com.sablednah.crawlspace.build.Part;
import com.sablednah.crawlspace.plan.Dice;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Which blocks each theme is built from: CityWorld's idea of a palette, a
 * weighted list of blocks per role, so a wall is mostly stone brick with the
 * odd mossy or cracked one. The choice per position comes from a hash of it,
 * so a rebuild of the same seed in the same place is identical.
 *
 * <p>Hard-coded while the look is being judged. These are meant to become
 * datapack JSON so a pack can add a theme.</p>
 */
final class Palettes {

    /** A weighted choice of blocks for one role. */
    private record Mix(Block[] blocks, int[] weights, int total) {
        static Mix of(Object... pairs) {
            Block[] b = new Block[pairs.length / 2];
            int[] w = new int[pairs.length / 2];
            int total = 0;
            for (int i = 0; i < pairs.length; i += 2) {
                b[i / 2] = (Block) pairs[i];
                w[i / 2] = (Integer) pairs[i + 1];
                total += w[i / 2];
            }
            return new Mix(b, w, total);
        }

        Block pick(long hash) {
            int r = (int) Math.floorMod(hash, (long) total);
            for (int i = 0; i < blocks.length; i++) {
                r -= weights[i];
                if (r < 0) {
                    return blocks[i];
                }
            }
            return blocks[blocks.length - 1];
        }
    }

    private static final Map<String, Map<Part, Mix>> THEMES = new HashMap<>();

    static {
        Mix crypt = Mix.of(Blocks.STONE_BRICKS, 70, Blocks.MOSSY_STONE_BRICKS, 15, Blocks.CRACKED_STONE_BRICKS, 15);
        theme("Crypt",
                crypt,
                Mix.of(Blocks.STONE_BRICKS, 60, Blocks.CRACKED_STONE_BRICKS, 25, Blocks.MOSSY_STONE_BRICKS, 15),
                Mix.of(Blocks.COBBLESTONE, 60, Blocks.MOSSY_COBBLESTONE, 25, Blocks.COARSE_DIRT, 15),
                crypt,
                Mix.of(Blocks.CHISELED_STONE_BRICKS, 1),
                Blocks.STONE_BRICK_STAIRS, Blocks.CHISELED_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS,
                Blocks.LANTERN, Blocks.SPRUCE_DOOR);
        theme("Sunken Halls",
                Mix.of(Blocks.MOSSY_STONE_BRICKS, 50, Blocks.STONE_BRICKS, 30, Blocks.MOSSY_COBBLESTONE, 20),
                Mix.of(Blocks.POLISHED_ANDESITE, 70, Blocks.ANDESITE, 20, Blocks.MOSS_BLOCK, 10),
                Mix.of(Blocks.MOSSY_COBBLESTONE, 60, Blocks.MOSS_BLOCK, 20, Blocks.COBBLESTONE, 20),
                Mix.of(Blocks.MOSSY_STONE_BRICKS, 60, Blocks.STONE_BRICKS, 40),
                Mix.of(Blocks.DARK_PRISMARINE, 1),
                Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.DARK_PRISMARINE, Blocks.CRACKED_STONE_BRICKS,
                Blocks.LANTERN, Blocks.SPRUCE_DOOR);
        theme("Old Mines",
                Mix.of(Blocks.STONE, 50, Blocks.ANDESITE, 20, Blocks.COBBLESTONE, 20, Blocks.TUFF, 10),
                Mix.of(Blocks.COARSE_DIRT, 50, Blocks.PACKED_MUD, 30, Blocks.STONE, 20),
                Mix.of(Blocks.COARSE_DIRT, 60, Blocks.ROOTED_DIRT, 15, Blocks.COBBLESTONE, 25),
                Mix.of(Blocks.STONE, 60, Blocks.OAK_PLANKS, 25, Blocks.ANDESITE, 15),
                Mix.of(Blocks.OAK_LOG, 1),
                Blocks.OAK_STAIRS, Blocks.OAK_LOG, Blocks.MOSSY_COBBLESTONE,
                Blocks.LANTERN, Blocks.OAK_DOOR);
        theme("Caverns",
                Mix.of(Blocks.DEEPSLATE, 50, Blocks.TUFF, 25, Blocks.COBBLED_DEEPSLATE, 25),
                Mix.of(Blocks.DEEPSLATE, 50, Blocks.MOSS_BLOCK, 20, Blocks.TUFF, 30),
                Mix.of(Blocks.COBBLED_DEEPSLATE, 70, Blocks.DEEPSLATE, 30),
                Mix.of(Blocks.DEEPSLATE, 60, Blocks.TUFF, 25, Blocks.DRIPSTONE_BLOCK, 15),
                Mix.of(Blocks.DRIPSTONE_BLOCK, 1),
                Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.POLISHED_DEEPSLATE, Blocks.CHISELED_TUFF,
                Blocks.SOUL_LANTERN, Blocks.SPRUCE_DOOR);
        theme("Deep Halls",
                Mix.of(Blocks.DEEPSLATE_BRICKS, 60, Blocks.CRACKED_DEEPSLATE_BRICKS, 20, Blocks.DEEPSLATE_TILES, 20),
                Mix.of(Blocks.POLISHED_DEEPSLATE, 60, Blocks.DEEPSLATE_TILES, 40),
                Mix.of(Blocks.DEEPSLATE_TILES, 70, Blocks.CRACKED_DEEPSLATE_TILES, 30),
                Mix.of(Blocks.DEEPSLATE_BRICKS, 1),
                Mix.of(Blocks.CHISELED_DEEPSLATE, 50, Blocks.POLISHED_BLACKSTONE, 50),
                Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.CHISELED_DEEPSLATE, Blocks.CRACKED_DEEPSLATE_TILES,
                Blocks.SOUL_LANTERN, Blocks.DARK_OAK_DOOR);
    }

    private static void theme(String name, Mix wall, Mix floor, Mix corridorFloor, Mix ceiling, Mix pillar,
            Block step, Block newel, Block secret, Block light, Block door) {
        Map<Part, Mix> m = new EnumMap<>(Part.class);
        m.put(Part.WALL, wall);
        m.put(Part.FLOOR, floor);
        m.put(Part.CORRIDOR_FLOOR, corridorFloor);
        m.put(Part.CEILING, ceiling);
        m.put(Part.PILLAR, pillar);
        m.put(Part.STEP, Mix.of(step, 1));
        m.put(Part.LANDING, Mix.of(landingFor(step), 1));
        m.put(Part.NEWEL, Mix.of(newel, 1));
        m.put(Part.SECRET_WALL, Mix.of(secret, 1));
        m.put(Part.LIGHT, Mix.of(light, 1));
        m.put(Part.DOOR_LOWER, Mix.of(door, 1));
        m.put(Part.DOOR_UPPER, Mix.of(door, 1));
        THEMES.put(name, m);
    }

    /** The full block a stair is cut from, for the spiral's corner landings. */
    private static Block landingFor(Block stair) {
        if (stair == Blocks.MOSSY_STONE_BRICK_STAIRS) {
            return Blocks.MOSSY_STONE_BRICKS;
        }
        if (stair == Blocks.OAK_STAIRS) {
            return Blocks.OAK_PLANKS;
        }
        if (stair == Blocks.COBBLED_DEEPSLATE_STAIRS) {
            return Blocks.COBBLED_DEEPSLATE;
        }
        if (stair == Blocks.DEEPSLATE_BRICK_STAIRS) {
            return Blocks.DEEPSLATE_BRICKS;
        }
        return Blocks.STONE_BRICKS;
    }

    /** How the entrance tower looks: chosen from the biome it stands in. */
    private record Style(Mix wall, Mix floor, Block top, Block door) {
    }

    private static final Map<String, Style> STYLES = new HashMap<>();

    static {
        STYLES.put("stone", new Style(
                Mix.of(Blocks.STONE_BRICKS, 65, Blocks.MOSSY_STONE_BRICKS, 20, Blocks.CRACKED_STONE_BRICKS, 15),
                Mix.of(Blocks.STONE_BRICKS, 1), Blocks.STONE_BRICKS, Blocks.SPRUCE_DOOR));
        STYLES.put("sandstone", new Style(
                Mix.of(Blocks.SANDSTONE, 55, Blocks.CUT_SANDSTONE, 25, Blocks.SMOOTH_SANDSTONE, 20),
                Mix.of(Blocks.SMOOTH_SANDSTONE, 1), Blocks.CHISELED_SANDSTONE, Blocks.BIRCH_DOOR));
        STYLES.put("terracotta", new Style(
                Mix.of(Blocks.TERRACOTTA, 40, Blocks.ORANGE_TERRACOTTA, 25, Blocks.RED_SANDSTONE, 20, Blocks.BROWN_TERRACOTTA, 15),
                Mix.of(Blocks.CUT_RED_SANDSTONE, 1), Blocks.CHISELED_RED_SANDSTONE, Blocks.ACACIA_DOOR));
        STYLES.put("mossy", new Style(
                Mix.of(Blocks.MOSSY_COBBLESTONE, 45, Blocks.MOSSY_STONE_BRICKS, 35, Blocks.COBBLESTONE, 20),
                Mix.of(Blocks.MOSSY_STONE_BRICKS, 1), Blocks.MOSS_BLOCK, Blocks.JUNGLE_DOOR));
        STYLES.put("mangrove", new Style(
                Mix.of(Blocks.MUD_BRICKS, 70, Blocks.PACKED_MUD, 30),
                Mix.of(Blocks.MANGROVE_PLANKS, 1), Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_DOOR));
        STYLES.put("snowy", new Style(
                Mix.of(Blocks.STONE_BRICKS, 55, Blocks.POLISHED_DIORITE, 25, Blocks.CRACKED_STONE_BRICKS, 20),
                Mix.of(Blocks.SPRUCE_PLANKS, 1), Blocks.SNOW_BLOCK, Blocks.SPRUCE_DOOR));
        STYLES.put("woodland", new Style(
                Mix.of(Blocks.COBBLESTONE, 50, Blocks.MOSSY_COBBLESTONE, 25, Blocks.STRIPPED_SPRUCE_LOG, 25),
                Mix.of(Blocks.SPRUCE_PLANKS, 1), Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_DOOR));
        STYLES.put("mushroom", new Style(
                Mix.of(Blocks.MUSHROOM_STEM, 70, Blocks.BROWN_MUSHROOM_BLOCK, 30),
                Mix.of(Blocks.MYCELIUM, 1), Blocks.RED_MUSHROOM_BLOCK, Blocks.OAK_DOOR));
        STYLES.put("cherry", new Style(
                Mix.of(Blocks.CALCITE, 50, Blocks.STONE_BRICKS, 30, Blocks.CHERRY_PLANKS, 20),
                Mix.of(Blocks.CHERRY_PLANKS, 1), Blocks.CHERRY_PLANKS, Blocks.CHERRY_DOOR));
        STYLES.put("pale", new Style(
                Mix.of(Blocks.STONE_BRICKS, 50, Blocks.PALE_OAK_PLANKS, 25, Blocks.CRACKED_STONE_BRICKS, 25),
                Mix.of(Blocks.PALE_OAK_PLANKS, 1), Blocks.PALE_MOSS_BLOCK, Blocks.PALE_OAK_DOOR));
    }

    static java.util.Set<String> styles() {
        return STYLES.keySet();
    }

    private Palettes() {
    }

    /** The block for a part, dressed in {@code theme} (the tower in {@code style}), at a position. */
    static BlockState state(String theme, String style, Part part, int facing, int x, int y, int z) {
        Direction dir = switch (facing) {
            case 1 -> Direction.EAST;
            case 2 -> Direction.SOUTH;
            case 3 -> Direction.WEST;
            default -> Direction.NORTH;
        };
        long hash = Dice.mix(Dice.mix(Dice.mix(x * 0x9E3779B1L) ^ y * 0x85EBCA77L) ^ z * 0xC2B2AE3DL);
        Map<Part, Mix> palette = THEMES.getOrDefault(theme, THEMES.get("Crypt"));
        Style tower = STYLES.getOrDefault(style, STYLES.get("stone"));
        return switch (part) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case WATER -> Blocks.WATER.defaultBlockState();
            case TOWER -> tower.wall().pick(hash).defaultBlockState();
            case TOWER_FLOOR -> tower.floor().pick(hash).defaultBlockState();
            case TOWER_TOP -> tower.top().defaultBlockState();
            case TOWER_DOOR_LOWER, TOWER_DOOR_UPPER -> door(tower.door(), dir, part == Part.TOWER_DOOR_UPPER);
            case LOCKED_LOWER, LOCKED_UPPER -> door(Blocks.IRON_DOOR, dir, part == Part.LOCKED_UPPER);
            case DOOR_LOWER, DOOR_UPPER -> door(palette.get(part).pick(hash), dir, part == Part.DOOR_UPPER);
            case STEP -> palette.get(Part.STEP).pick(hash).defaultBlockState().setValue(StairBlock.FACING, dir);
            case LIGHT -> palette.get(Part.LIGHT).pick(hash).defaultBlockState().setValue(LanternBlock.HANGING, true);
            case LEVER -> Blocks.LEVER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                    .setValue(net.minecraft.world.level.block.LeverBlock.FACING, dir);
            // With hints on, a related block that is not in the wall's mix; otherwise the wall itself.
            case SECRET_WALL -> (CrawlConfig.hints() ? palette.get(Part.SECRET_WALL) : palette.get(Part.WALL))
                    .pick(hash).defaultBlockState();
            default -> palette.get(part).pick(hash).defaultBlockState();
        };
    }

    private static BlockState door(Block block, Direction facing, boolean upper) {
        return block.defaultBlockState()
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
    }
}
