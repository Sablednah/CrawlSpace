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
 * <p>The built-in palettes below are the defaults. A datapack can replace any
 * role of any theme or entrance style, or add entrance styles: see
 * {@link ThemeData}.</p>
 */
public final class Palettes {

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

        com.google.gson.JsonElement toJson() {
            if (blocks.length == 1) {
                return new com.google.gson.JsonPrimitive(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blocks[0]).toString());
            }
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            for (int i = 0; i < blocks.length; i++) {
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("block", net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blocks[i]).toString());
                o.addProperty("weight", weights[i]);
                a.add(o);
            }
            return a;
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

    /** What a theme's rooms are furnished with. */
    private record Fittings(Block carpet, Block banner, Block altar, Block sarcophagus, Block brazier, Block railing,
            Block support, Block beam, Block accent, Block throne, Block torch, Block candle,
            Block floorAccent, Block floorInlay, Block pilaster) {
    }

    private static final Map<String, Fittings> FITTINGS = new HashMap<>();

    static {
        FITTINGS.put("Crypt", new Fittings(Blocks.RED_CARPET, Blocks.RED_WALL_BANNER, Blocks.CHISELED_STONE_BRICKS,
                Blocks.POLISHED_ANDESITE, Blocks.CAMPFIRE, Blocks.STONE_BRICK_WALL, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_LOG,
                Blocks.SMOOTH_STONE, Blocks.STONE_BRICK_STAIRS, Blocks.WALL_TORCH, Blocks.WHITE_CANDLE,
                Blocks.POLISHED_ANDESITE, Blocks.CHISELED_STONE_BRICKS, Blocks.POLISHED_ANDESITE));
        FITTINGS.put("Sunken Halls", new Fittings(Blocks.GREEN_CARPET, Blocks.CYAN_WALL_BANNER, Blocks.PRISMARINE_BRICKS,
                Blocks.POLISHED_ANDESITE, Blocks.CAMPFIRE, Blocks.MOSSY_STONE_BRICK_WALL, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_LOG,
                Blocks.POLISHED_ANDESITE, Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.WALL_TORCH, Blocks.CYAN_CANDLE,
                Blocks.DARK_PRISMARINE, Blocks.PRISMARINE, Blocks.DARK_PRISMARINE));
        FITTINGS.put("Old Mines", new Fittings(Blocks.BROWN_CARPET, Blocks.BROWN_WALL_BANNER, Blocks.POLISHED_ANDESITE,
                Blocks.COBBLESTONE, Blocks.CAMPFIRE, Blocks.OAK_FENCE, Blocks.OAK_FENCE, Blocks.OAK_LOG,
                Blocks.COBBLESTONE, Blocks.OAK_STAIRS, Blocks.WALL_TORCH, Blocks.CANDLE,
                Blocks.OAK_PLANKS, Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG));
        FITTINGS.put("Caverns", new Fittings(Blocks.MOSS_CARPET, Blocks.GRAY_WALL_BANNER, Blocks.POLISHED_TUFF,
                Blocks.POLISHED_DEEPSLATE, Blocks.SOUL_CAMPFIRE, Blocks.COBBLED_DEEPSLATE_WALL, Blocks.SPRUCE_FENCE,
                Blocks.SPRUCE_LOG, Blocks.POLISHED_TUFF, Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.SOUL_WALL_TORCH, Blocks.CANDLE,
                Blocks.POLISHED_TUFF, Blocks.CHISELED_TUFF, Blocks.POLISHED_TUFF));
        FITTINGS.put("Deep Halls", new Fittings(Blocks.BLACK_CARPET, Blocks.PURPLE_WALL_BANNER,
                Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE, Blocks.SOUL_CAMPFIRE,
                Blocks.DEEPSLATE_BRICK_WALL, Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_LOG, Blocks.POLISHED_DEEPSLATE,
                Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.SOUL_WALL_TORCH, Blocks.PURPLE_CANDLE,
                Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GILDED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS));
    }

    /** A theme's interior finish: coving, panelling, the rail above it, tables, chairs and rugs. */
    private record Finish(Block cove, Block panel, Block dado, Block table, Block chair, Block rug, Block plate) {
    }

    private static final Map<String, Finish> FINISHES = new HashMap<>();

    static {
        FINISHES.put("Crypt", new Finish(Blocks.STONE_BRICK_STAIRS, Blocks.SPRUCE_PLANKS, Blocks.POLISHED_ANDESITE,
                Blocks.SPRUCE_FENCE, Blocks.SPRUCE_STAIRS, Blocks.RED_CARPET, Blocks.SPRUCE_PRESSURE_PLATE));
        FINISHES.put("Sunken Halls", new Finish(Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE,
                Blocks.SPRUCE_FENCE, Blocks.SPRUCE_STAIRS, Blocks.CYAN_CARPET, Blocks.SPRUCE_PRESSURE_PLATE));
        FINISHES.put("Old Mines", new Finish(Blocks.OAK_STAIRS, Blocks.OAK_PLANKS, Blocks.STRIPPED_OAK_LOG,
                Blocks.OAK_FENCE, Blocks.OAK_STAIRS, Blocks.BROWN_CARPET, Blocks.OAK_PRESSURE_PLATE));
        FINISHES.put("Caverns", new Finish(Blocks.COBBLED_DEEPSLATE_STAIRS, Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS,
                Blocks.SPRUCE_FENCE, Blocks.SPRUCE_STAIRS, Blocks.MOSS_CARPET, Blocks.SPRUCE_PRESSURE_PLATE));
        FINISHES.put("Deep Halls", new Finish(Blocks.DEEPSLATE_BRICK_STAIRS, Blocks.DARK_OAK_PLANKS, Blocks.POLISHED_BLACKSTONE_BRICKS,
                Blocks.DARK_OAK_FENCE, Blocks.DARK_OAK_STAIRS, Blocks.PURPLE_CARPET, Blocks.DARK_OAK_PRESSURE_PLATE));
    }

    /** Parts whose look depends on their neighbours: set again once the chunk around them is in. */
    public static boolean connects(Part part) {
        return part == Part.RAILING || part == Part.SUPPORT || part == Part.COVE || part == Part.STATUE
                || part == Part.TOWER_STAIR || part == Part.TOWER_CORBEL || part == Part.TOWER_WINDOW;
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
    private record Style(Mix wall, Mix floor, Block top, Block door,
            Block trim, Block stair, Block window, Block pillar, Block roof) {
    }

    private static final Map<String, Style> STYLES = new HashMap<>();

    static {
        STYLES.put("stone", new Style(
                Mix.of(Blocks.STONE_BRICKS, 65, Blocks.MOSSY_STONE_BRICKS, 20, Blocks.CRACKED_STONE_BRICKS, 15),
                Mix.of(Blocks.STONE_BRICKS, 1), Blocks.STONE_BRICKS, Blocks.SPRUCE_DOOR,
                Blocks.POLISHED_ANDESITE, Blocks.STONE_BRICK_STAIRS, Blocks.IRON_BARS, Blocks.POLISHED_DIORITE, Blocks.DEEPSLATE_TILES));
        STYLES.put("sandstone", new Style(
                Mix.of(Blocks.SANDSTONE, 55, Blocks.CUT_SANDSTONE, 25, Blocks.SMOOTH_SANDSTONE, 20),
                Mix.of(Blocks.SMOOTH_SANDSTONE, 1), Blocks.CHISELED_SANDSTONE, Blocks.BIRCH_DOOR,
                Blocks.CUT_SANDSTONE, Blocks.SANDSTONE_STAIRS, Blocks.IRON_BARS, Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE));
        STYLES.put("terracotta", new Style(
                Mix.of(Blocks.TERRACOTTA, 40, Blocks.ORANGE_TERRACOTTA, 25, Blocks.RED_SANDSTONE, 20, Blocks.BROWN_TERRACOTTA, 15),
                Mix.of(Blocks.CUT_RED_SANDSTONE, 1), Blocks.CHISELED_RED_SANDSTONE, Blocks.ACACIA_DOOR,
                Blocks.CUT_RED_SANDSTONE, Blocks.RED_SANDSTONE_STAIRS, Blocks.IRON_BARS, Blocks.SMOOTH_RED_SANDSTONE, Blocks.TERRACOTTA));
        STYLES.put("mossy", new Style(
                Mix.of(Blocks.MOSSY_COBBLESTONE, 45, Blocks.MOSSY_STONE_BRICKS, 35, Blocks.COBBLESTONE, 20),
                Mix.of(Blocks.MOSSY_STONE_BRICKS, 1), Blocks.MOSS_BLOCK, Blocks.JUNGLE_DOOR,
                Blocks.CHISELED_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.IRON_BARS, Blocks.MOSSY_STONE_BRICKS, Blocks.MOSS_BLOCK));
        STYLES.put("mangrove", new Style(
                Mix.of(Blocks.MUD_BRICKS, 70, Blocks.PACKED_MUD, 30),
                Mix.of(Blocks.MANGROVE_PLANKS, 1), Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_DOOR,
                Blocks.PACKED_MUD, Blocks.MUD_BRICK_STAIRS, Blocks.IRON_BARS, Blocks.MANGROVE_LOG, Blocks.MANGROVE_PLANKS));
        STYLES.put("snowy", new Style(
                Mix.of(Blocks.STONE_BRICKS, 55, Blocks.POLISHED_DIORITE, 25, Blocks.CRACKED_STONE_BRICKS, 20),
                Mix.of(Blocks.SPRUCE_PLANKS, 1), Blocks.SNOW_BLOCK, Blocks.SPRUCE_DOOR,
                Blocks.POLISHED_DIORITE, Blocks.STONE_BRICK_STAIRS, Blocks.IRON_BARS, Blocks.STRIPPED_SPRUCE_LOG, Blocks.SPRUCE_PLANKS));
        STYLES.put("woodland", new Style(
                Mix.of(Blocks.COBBLESTONE, 50, Blocks.MOSSY_COBBLESTONE, 25, Blocks.STRIPPED_SPRUCE_LOG, 25),
                Mix.of(Blocks.SPRUCE_PLANKS, 1), Blocks.SPRUCE_PLANKS, Blocks.DARK_OAK_DOOR,
                Blocks.SPRUCE_PLANKS, Blocks.COBBLESTONE_STAIRS, Blocks.IRON_BARS, Blocks.SPRUCE_LOG, Blocks.SPRUCE_PLANKS));
        STYLES.put("mushroom", new Style(
                Mix.of(Blocks.MUSHROOM_STEM, 70, Blocks.BROWN_MUSHROOM_BLOCK, 30),
                Mix.of(Blocks.MYCELIUM, 1), Blocks.RED_MUSHROOM_BLOCK, Blocks.OAK_DOOR,
                Blocks.MUSHROOM_STEM, Blocks.OAK_STAIRS, Blocks.IRON_BARS, Blocks.MUSHROOM_STEM, Blocks.RED_MUSHROOM_BLOCK));
        STYLES.put("cherry", new Style(
                Mix.of(Blocks.CALCITE, 50, Blocks.STONE_BRICKS, 30, Blocks.CHERRY_PLANKS, 20),
                Mix.of(Blocks.CHERRY_PLANKS, 1), Blocks.CHERRY_PLANKS, Blocks.CHERRY_DOOR,
                Blocks.CALCITE, Blocks.CHERRY_STAIRS, Blocks.IRON_BARS, Blocks.CALCITE, Blocks.CHERRY_PLANKS));
        STYLES.put("pale", new Style(
                Mix.of(Blocks.STONE_BRICKS, 50, Blocks.PALE_OAK_PLANKS, 25, Blocks.CRACKED_STONE_BRICKS, 25),
                Mix.of(Blocks.PALE_OAK_PLANKS, 1), Blocks.PALE_MOSS_BLOCK, Blocks.PALE_OAK_DOOR,
                Blocks.PALE_OAK_PLANKS, Blocks.STONE_BRICK_STAIRS, Blocks.IRON_BARS, Blocks.PALE_OAK_LOG, Blocks.PALE_OAK_PLANKS));
    }

    static java.util.Set<String> styles() {
        return STYLES.keySet();
    }

    private Palettes() {
    }

    // ---- what a datapack can change: every role by name ----

    /** A theme's blocks by role ("wall", "carpet", ...): the built-in palette, with any datapack's on top. */
    private static volatile Map<String, Map<String, Mix>> skins = Map.of();
    /** The same for entrance styles ("stone", "sandstone", ...). */
    private static volatile Map<String, Map<String, Mix>> towers = Map.of();
    private static final Map<String, Map<String, Mix>> DEFAULT_SKINS = new HashMap<>();
    private static final Map<String, Map<String, Mix>> DEFAULT_TOWERS = new HashMap<>();

    /** What a theme's trap plates are made of. */
    private static final Map<String, Block> PLATES = Map.of(
            "Crypt", Blocks.STONE_PRESSURE_PLATE,
            "Sunken Halls", Blocks.STONE_PRESSURE_PLATE,
            "Old Mines", Blocks.OAK_PRESSURE_PLATE,
            "Caverns", Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE,
            "Deep Halls", Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE);

    static {
        for (String theme : THEMES.keySet()) {
            Map<String, Mix> m = new java.util.TreeMap<>();
            THEMES.get(theme).forEach((part, mix) -> m.put(part == Part.DOOR_LOWER ? "door" : part.name().toLowerCase(java.util.Locale.ROOT), mix));
            m.remove("door_upper");
            fields(FITTINGS.get(theme), m);
            fields(FINISHES.get(theme), m);
            m.put("table_top", m.remove("plate"));
            m.put("trap_plate", Mix.of(PLATES.getOrDefault(theme, Blocks.STONE_PRESSURE_PLATE), 1));
            m.put("trap_wire", Mix.of(Blocks.TRIPWIRE, 1));
            DEFAULT_SKINS.put(theme, m);
        }
        STYLES.forEach((style, st) -> {
            Map<String, Mix> m = new java.util.TreeMap<>();
            fields(st, m);
            DEFAULT_TOWERS.put(style, m);
        });
        skins = Map.copyOf(DEFAULT_SKINS);
        towers = Map.copyOf(DEFAULT_TOWERS);
    }

    /** A record's fields by snake_case name, each a Mix or a single block. Saves listing them twice. */
    private static void fields(Record r, Map<String, Mix> into) {
        for (java.lang.reflect.RecordComponent c : r.getClass().getRecordComponents()) {
            try {
                java.lang.reflect.Method m = c.getAccessor();
                m.setAccessible(true);
                Object v = m.invoke(r);
                String key = c.getName().replaceAll("([A-Z])", "_$1").toLowerCase(java.util.Locale.ROOT);
                into.put(key, v instanceof Mix mix ? mix : Mix.of(v, 1));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    static java.util.Set<String> themeNames() {
        return DEFAULT_SKINS.keySet();
    }

    /**
     * Datapack themes and entrances, as ThemeData read them: each file's
     * "blocks" are laid over the built-in roles key by key. An unknown role or
     * block is skipped with a warning naming it, so a typo costs one role, not
     * the pack. A new entrance style starts from "stone".
     */
    static void apply(Map<String, com.google.gson.JsonObject> themeData, Map<String, com.google.gson.JsonObject> entranceData) {
        skins = overlay(DEFAULT_SKINS, themeData, "Crypt", "theme");
        towers = overlay(DEFAULT_TOWERS, entranceData, "stone", "entrance");
    }

    private static Map<String, Map<String, Mix>> overlay(Map<String, Map<String, Mix>> defaults,
            Map<String, com.google.gson.JsonObject> data, String base, String what) {
        Map<String, Map<String, Mix>> out = new HashMap<>();
        defaults.forEach((k, v) -> out.put(k, new java.util.TreeMap<>(v)));
        data.forEach((name, json) -> {
            Map<String, Mix> m = out.computeIfAbsent(name, k -> new java.util.TreeMap<>(defaults.get(base)));
            if (!json.has("blocks")) {
                return;
            }
            for (Map.Entry<String, com.google.gson.JsonElement> e : json.getAsJsonObject("blocks").entrySet()) {
                if (!m.containsKey(e.getKey())) {
                    ThemeData.LOG.warn("CrawlSpace {} '{}': no role called '{}'. Roles: {}", what, name, e.getKey(), m.keySet());
                    continue;
                }
                Mix mix = parse(e.getValue(), what + " '" + name + "' role '" + e.getKey() + "'");
                if (mix != null) {
                    m.put(e.getKey(), mix);
                }
            }
        });
        return Map.copyOf(out);
    }

    /** "minecraft:stone", or a list of those or of {"block": ..., "weight": n}. */
    private static Mix parse(com.google.gson.JsonElement el, String where) {
        java.util.List<Object> pairs = new java.util.ArrayList<>();
        Iterable<com.google.gson.JsonElement> items = el.isJsonArray() ? el.getAsJsonArray() : java.util.List.of(el);
        for (com.google.gson.JsonElement item : items) {
            String id = item.isJsonObject() ? item.getAsJsonObject().get("block").getAsString() : item.getAsString();
            int weight = item.isJsonObject() && item.getAsJsonObject().has("weight") ? item.getAsJsonObject().get("weight").getAsInt() : 1;
            java.util.Optional<Block> block = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getOptional(net.minecraft.resources.Identifier.tryParse(id));
            if (block.isEmpty()) {
                ThemeData.LOG.warn("CrawlSpace {}: no block called '{}', so this role keeps its old blocks", where, id);
                return null;
            }
            pairs.add(block.get());
            pairs.add(Math.max(1, weight));
        }
        return pairs.isEmpty() ? null : Mix.of(pairs.toArray());
    }

    /** What is in force now, as JSON a datapack could carry: for /crawlspace export. */
    static Map<String, com.google.gson.JsonObject> export(boolean entrances) {
        Map<String, com.google.gson.JsonObject> out = new java.util.TreeMap<>();
        (entrances ? towers : skins).forEach((name, roles) -> {
            com.google.gson.JsonObject blocks = new com.google.gson.JsonObject();
            roles.forEach((role, mix) -> blocks.add(role, mix.toJson()));
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty(entrances ? "style" : "theme", name);
            o.add("blocks", blocks);
            out.put(name, o);
        });
        return out;
    }

    private static Block block(Map<String, Mix> roles, String role, long hash) {
        return roles.get(role).pick(hash);
    }

    private static <T extends Comparable<T>> BlockState with(BlockState st,
            net.minecraft.world.level.block.state.properties.Property<T> p, T v) {
        return st.hasProperty(p) ? st.setValue(p, v) : st;
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
        Map<String, Mix> s = skins.getOrDefault(theme, skins.get("Crypt"));
        Map<String, Mix> tw = towers.getOrDefault(style, towers.get("stone"));
        java.util.function.Function<String, BlockState> b = role -> block(s, role, hash).defaultBlockState();
        java.util.function.Function<String, BlockState> t = role -> block(tw, role, hash).defaultBlockState();
        return switch (part) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case WATER -> Blocks.WATER.defaultBlockState();
            case TOWER -> t.apply("wall");
            case TOWER_FLOOR -> t.apply("floor");
            case TOWER_TOP -> t.apply("top");
            case TOWER_DOOR_LOWER, TOWER_DOOR_UPPER -> door(block(tw, "door", hash), dir, part == Part.TOWER_DOOR_UPPER);
            case TOWER_TRIM -> t.apply("trim");
            case TOWER_STAIR -> stairs(block(tw, "stair", hash), dir, false);
            case TOWER_CORBEL -> stairs(block(tw, "stair", hash), dir, true);
            case TOWER_WINDOW -> t.apply("window");
            case TOWER_PILLAR -> t.apply("pillar");
            case TOWER_ROOF -> t.apply("roof");
            case LOCKED_LOWER, LOCKED_UPPER -> door(Blocks.IRON_DOOR, dir, part == Part.LOCKED_UPPER);
            case DOOR_LOWER, DOOR_UPPER -> door(block(s, "door", hash), dir, part == Part.DOOR_UPPER);
            case STEP -> with(b.apply("step"), StairBlock.FACING, dir);
            case LIGHT -> with(b.apply("light"), LanternBlock.HANGING, true);
            case LEVER -> Blocks.LEVER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                    .setValue(net.minecraft.world.level.block.LeverBlock.FACING, dir);
            // With hints on, a related block that is not in the wall's mix; otherwise the wall itself.
            case SECRET_WALL -> b.apply(CrawlConfig.hints() ? "secret_wall" : "wall");
            case CHEST, HOARD_CHEST -> Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, dir);
            case BARREL -> Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.UP);
            case SPAWNER -> Blocks.SPAWNER.defaultBlockState();
            case COBWEB -> Blocks.COBWEB.defaultBlockState();
            case SKULL -> Blocks.SKELETON_SKULL.defaultBlockState().setValue(net.minecraft.world.level.block.SkullBlock.ROTATION, facing * 4 + (int) Math.floorMod(hash, 3L));
            case BONES -> Blocks.BONE_BLOCK.defaultBlockState();
            case CANDLES -> with(with(b.apply("candle"), net.minecraft.world.level.block.CandleBlock.CANDLES, Math.min(4, facing + 1)),
                    net.minecraft.world.level.block.CandleBlock.LIT, true);
            case CARPET -> b.apply("carpet");
            case MOSS -> Blocks.MOSS_CARPET.defaultBlockState();
            case RAIL -> Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,
                    facing == 1 ? net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST
                            : net.minecraft.world.level.block.state.properties.RailShape.NORTH_SOUTH);
            case ALTAR -> b.apply("altar");
            case SARCOPHAGUS -> b.apply("sarcophagus");
            case BRAZIER -> b.apply("brazier");
            case STALAGMITE -> Blocks.POINTED_DRIPSTONE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.PointedDripstoneBlock.TIP_DIRECTION, Direction.UP)
                    .setValue(net.minecraft.world.level.block.PointedDripstoneBlock.THICKNESS,
                            net.minecraft.world.level.block.state.properties.DripstoneThickness.TIP);
            case BANNER -> with(b.apply("banner"), net.minecraft.world.level.block.WallBannerBlock.FACING, dir);
            case WALL_TORCH -> with(b.apply("torch"), net.minecraft.world.level.block.WallTorchBlock.FACING, dir);
            case CHAIN -> Blocks.IRON_CHAIN.defaultBlockState();
            case BEAM -> with(b.apply("beam"), net.minecraft.world.level.block.RotatedPillarBlock.AXIS,
                    facing == 0 ? Direction.Axis.X : Direction.Axis.Z);
            case SUPPORT -> b.apply("support");
            case RAILING, STATUE -> b.apply("railing");
            case SHELF -> Blocks.BOOKSHELF.defaultBlockState();
            case WALL_ACCENT -> b.apply("accent");
            case THRONE -> with(b.apply("throne"), StairBlock.FACING, dir);
            case FLOOR_ACCENT -> b.apply("floor_accent");
            case FLOOR_INLAY -> b.apply("floor_inlay");
            case PILASTER -> b.apply("pilaster");
            case COVE -> with(with(b.apply("cove"), StairBlock.FACING, dir), StairBlock.HALF,
                    net.minecraft.world.level.block.state.properties.Half.TOP);
            case PANEL -> b.apply("panel");
            case DADO -> b.apply("dado");
            case TABLE -> b.apply("table");
            case TABLE_TOP -> b.apply("table_top");
            case CHAIR -> with(b.apply("chair"), StairBlock.FACING, dir);
            case POT -> Blocks.DECORATED_POT.defaultBlockState();
            case RUG -> b.apply("rug");
            case FLOOR_LANTERN -> with(b.apply("light"), LanternBlock.HANGING, false);
            case ANVIL -> Blocks.CHIPPED_ANVIL.defaultBlockState().setValue(net.minecraft.world.level.block.AnvilBlock.FACING, dir);
            case GRINDSTONE -> Blocks.GRINDSTONE.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.GrindstoneBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                    .setValue(net.minecraft.world.level.block.GrindstoneBlock.FACING, dir);
            case CAULDRON -> Blocks.CAULDRON.defaultBlockState();
            case LECTERN -> Blocks.LECTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LecternBlock.FACING, dir);
            case MUSHROOM -> (Math.floorMod(hash, 2L) == 0 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState();
            case VINE -> Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.getPropertyForFace(dir), true);
            case PLANT -> (facing == 0 ? Blocks.FERN : Blocks.SHORT_GRASS).defaultBlockState();
            case MOSS_FLOOR -> Blocks.MOSS_BLOCK.defaultBlockState();
            case LEAVES -> (facing == 0 ? Blocks.AZALEA_LEAVES : Blocks.FLOWERING_AZALEA_LEAVES).defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
            case ROOTS -> Blocks.HANGING_ROOTS.defaultBlockState();
            case LADDER -> Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, dir);
            case TRAP_PLATE -> CrawlConfig.trapsVisible() ? trapBlock(theme, false) : Blocks.AIR.defaultBlockState();
            case TRAP_WIRE -> CrawlConfig.trapsVisible() ? trapBlock(theme, true) : Blocks.AIR.defaultBlockState();
            case DECOY_PLATE -> trapBlock(theme, false);
            case DECOY_WIRE -> trapBlock(theme, true);
            default -> b.apply(part.name().toLowerCase(java.util.Locale.ROOT));
        };
    }

    /** A trap's plate or wire as it looks once it can be seen. */
    static BlockState trapBlock(String theme, boolean wire) {
        Map<String, Mix> s = skins.getOrDefault(theme, skins.get("Crypt"));
        return s.get(wire ? "trap_wire" : "trap_plate").pick(0).defaultBlockState();
    }

    /** A stair; a non-stair block (a style with none) is placed whole. */
    private static BlockState stairs(Block block, Direction facing, boolean upsideDown) {
        BlockState st = block.defaultBlockState();
        if (!st.hasProperty(StairBlock.FACING)) {
            return st;
        }
        return st.setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF,
                upsideDown ? net.minecraft.world.level.block.state.properties.Half.TOP
                        : net.minecraft.world.level.block.state.properties.Half.BOTTOM);
    }

    private static BlockState door(Block block, Direction facing, boolean upper) {
        if (!(block instanceof DoorBlock)) {
            return block.defaultBlockState(); // a datapack gave a plain block: it fills the doorway
        }
        return block.defaultBlockState()
                .setValue(DoorBlock.FACING, facing)
                .setValue(DoorBlock.HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
    }
}
