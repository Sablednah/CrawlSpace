package com.sablednah.crawlspace.neoforge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sablednah.crawlspace.plan.Dice;
import com.sablednah.crawlspace.plan.Theme;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Kinds of dungeon, and the themes they are made of. Each dungeon picks its
 * type from its own seed (and the biome it stands in), so one world can hold
 * crypts, research bases and bunkers side by side.
 *
 * <p>{@code data/<namespace>/crawlspace/dungeon/<id>.json}:</p>
 * <pre>
 * {
 *   "name": "Research Base",
 *   "weight": 10,
 *   "biomes": ["minecraft:plains", "#minecraft:is_badlands"],
 *   "entrance": "bunker",
 *   "levels": ["Research Lab", "Research Lab", "Containment"],
 *   "max_levels": 6
 * }
 * </pre>
 * <ul>
 * <li>"levels" are themes by depth, the top first; past its end the last repeats.</li>
 * <li>A type with "biomes" is only found there; one without, anywhere.
 * With {@code "exclusive": true} as well, it is the only kind found there
 * (with any other exclusive type that also claims that biome).</li>
 * <li>"entrance" names an entrance style; without it the biome picks, as before.</li>
 * <li>The built-in type is "classic" (Crypt down to the Deep Halls). A file
 * called {@code classic.json} changes it: {@code "weight": 0} turns it off.</li>
 * </ul>
 */
public final class DungeonTypes {

    public static final String CLASSIC = "classic";

    /** One kind of dungeon, as read. Biomes are ids or tags; empty means anywhere. */
    public record Type(String id, String name, int weight, List<String> levels, String entrance, int maxLevels,
            Set<Identifier> biomes, List<TagKey<Biome>> tags, boolean exclusive) {

        boolean anywhere() {
            return biomes.isEmpty() && tags.isEmpty();
        }

        boolean fits(Holder<Biome> biome) {
            return anywhere() || biome.unwrapKey().map(k -> biomes.contains(k.identifier())).orElse(false)
                    || tags.stream().anyMatch(biome::is);
        }
    }

    private static final Type CLASSIC_TYPE = new Type(CLASSIC, "Classic", 10,
            Theme.BY_DEPTH.stream().map(Theme::name).toList(), null, 0, Set.of(), List.of(), false);

    /** Every theme in force by id (see {@link #id}): the built-in five, as a datapack left them, and any it added. */
    private static volatile Map<String, Theme> themes = builtIns();
    private static volatile Map<String, Type> types = Map.of(CLASSIC, CLASSIC_TYPE);

    private DungeonTypes() {
    }

    /** "Sunken Halls", "sunken_halls" and "Sunken-Halls" are one theme. */
    public static String id(String name) {
        return Theme.id(name);
    }

    private static Map<String, Theme> builtIns() {
        Map<String, Theme> m = new LinkedHashMap<>();
        Theme.BUILT_IN.forEach(t -> m.put(id(t.name()), t));
        return Map.copyOf(m);
    }

    /** A built-in theme by name, or null. */
    static Theme builtIn(String name) {
        return Theme.builtIn(name);
    }

    /** The theme in force by any spelling of its name, or null. */
    public static Theme theme(String name) {
        return themes.get(id(name));
    }

    public static java.util.Collection<Theme> themes() {
        return themes.values();
    }

    public static java.util.Collection<Type> types() {
        return types.values();
    }

    static Type type(String id) {
        return types.get(id(id));
    }

    /** From ThemeData, on every load and /reload. */
    static void apply(Map<String, Theme> newThemes, Map<String, JsonObject> typeData) {
        Map<String, Theme> t = new LinkedHashMap<>(builtIns());
        newThemes.forEach((name, theme) -> t.put(id(name), theme));
        themes = Map.copyOf(t);

        Map<String, Type> out = new LinkedHashMap<>();
        out.put(CLASSIC, CLASSIC_TYPE);
        typeData.forEach((file, json) -> {
            String id = id(file);
            Type base = out.getOrDefault(id, null);
            Type type = read(id, json, base);
            if (type != null) {
                out.put(id, type);
            }
        });
        types = Map.copyOf(out);
    }

    private static Type read(String id, JsonObject json, Type base) {
        String where = "dungeon type '" + id + "'";
        String name = json.has("name") ? json.get("name").getAsString() : base != null ? base.name() : title(id);
        int weight = json.has("weight") ? Math.max(0, json.get("weight").getAsInt()) : base != null ? base.weight() : 10;
        List<String> levels = base != null ? base.levels() : List.of();
        if (json.has("levels")) {
            List<String> l = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("levels")) {
                Theme theme = theme(e.getAsString());
                if (theme == null) {
                    ThemeData.LOG.warn("CrawlSpace {}: no theme called '{}', so the type is skipped. Themes are {}", where,
                            e.getAsString(), themes.values().stream().map(Theme::name).toList());
                    return null;
                }
                l.add(theme.name());
            }
            levels = List.copyOf(l);
        }
        if (levels.isEmpty()) {
            ThemeData.LOG.warn("CrawlSpace {}: it has no \"levels\", so it is skipped", where);
            return null;
        }
        String entrance = json.has("entrance") ? json.get("entrance").getAsString() : base != null ? base.entrance() : null;
        if (entrance != null && !Palettes.towerStyles().contains(entrance)) {
            ThemeData.LOG.warn("CrawlSpace {}: no entrance style called '{}', so the biome picks one. Styles are {}",
                    where, entrance, Palettes.towerStyles());
            entrance = null;
        }
        int maxLevels = json.has("max_levels") ? Math.max(0, json.get("max_levels").getAsInt()) : base != null ? base.maxLevels() : 0;
        Set<Identifier> biomes = base != null ? base.biomes() : Set.of();
        List<TagKey<Biome>> tags = base != null ? base.tags() : List.of();
        if (json.has("biomes")) {
            Set<Identifier> ids = new java.util.HashSet<>();
            List<TagKey<Biome>> tl = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray("biomes")) {
                String s = e.getAsString();
                Identifier parsed = Identifier.tryParse(s.startsWith("#") ? s.substring(1) : s);
                if (parsed == null) {
                    ThemeData.LOG.warn("CrawlSpace {}: '{}' is not a biome id or #tag", where, s);
                } else if (s.startsWith("#")) {
                    tl.add(TagKey.create(Registries.BIOME, parsed));
                } else {
                    ids.add(parsed);
                }
            }
            biomes = Set.copyOf(ids);
            tags = List.copyOf(tl);
        }
        boolean exclusive = json.has("exclusive") ? json.get("exclusive").getAsBoolean() : base != null && base.exclusive();
        return new Type(id, name, weight, levels, entrance, maxLevels, biomes, tags, exclusive);
    }

    /** "research_base" is "Research Base". */
    static String title(String id) {
        StringBuilder b = new StringBuilder();
        for (String w : id.split("[_\\s-]+")) {
            if (!w.isEmpty()) {
                b.append(b.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return b.toString();
    }

    /**
     * The type for a new dungeon: from its seed, among those that may stand in
     * its biome, by weight. If an exclusive type claims the biome, only
     * exclusive ones are drawn. Null if nothing may stand here.
     */
    public static Type pick(long seed, Holder<Biome> biome) {
        List<Type> exclusive = new ArrayList<>();
        List<Type> open = new ArrayList<>();
        for (Type t : types.values()) {
            if (t.weight() <= 0 || !t.fits(biome)) {
                continue;
            }
            (t.exclusive() && !t.anywhere() ? exclusive : open).add(t);
        }
        List<Type> from = exclusive.isEmpty() ? open : exclusive;
        if (from.isEmpty()) {
            return null;
        }
        if (from.size() == 1) {
            return from.get(0); // no dice: a world with only the classic type plans exactly as before
        }
        double[] w = new double[from.size()];
        for (int i = 0; i < w.length; i++) {
            w[i] = from.get(i).weight();
        }
        return from.get(Dice.of(seed, 0, 0x7E1DL).weighted(w));
    }

    /** A type's themes by depth, as they are now. */
    public static List<Theme> themesOf(Type type) {
        if (type.id().equals(CLASSIC) && type.levels().equals(CLASSIC_TYPE.levels())
                && Theme.BUILT_IN.stream().allMatch(t -> theme(t.name()) == t)) {
            return Theme.BY_DEPTH; // unchanged: the very list the planner has always used
        }
        List<Theme> out = new ArrayList<>();
        for (String name : type.levels()) {
            Theme t = theme(name);
            out.add(t != null ? t : Theme.CRYPT);
        }
        return List.copyOf(out);
    }

    static String snapshot(List<Theme> byDepth) {
        return ThemeJson.snapshot(byDepth);
    }

    static List<Theme> restore(String snapshot) {
        return ThemeJson.restore(snapshot);
    }
}
