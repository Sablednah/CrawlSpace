package com.sablednah.crawlspace.neoforge;

import java.io.Reader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Datapack themes and entrances, read on every load and /reload:
 *
 * <ul>
 * <li>{@code data/<namespace>/crawlspace/theme/<name>.json}: a theme's blocks by
 * role, its monsters, bosses and loot, and its layout and dressing numbers.
 * The file names its theme with "theme", or by its own name
 * ({@code sunken_halls.json} is Sunken Halls, {@code research_lab.json} a
 * new theme called Research Lab). A new theme starts as a copy of its
 * "base" (Crypt if it names none), which may be any other theme.</li>
 * <li>{@code data/<namespace>/crawlspace/dungeon/<id>.json}: a kind of dungeon,
 * its themes by depth and where it is found (DungeonTypes).</li>
 * <li>{@code data/<namespace>/crawlspace/entrance/<style>.json}: an entrance
 * style's blocks, the weights of its four tower designs, and the biomes it
 * claims. A new name makes a new style.</li>
 * </ul>
 *
 * <p>Everything is laid over the built-in defaults key by key, so a file need
 * only say what it changes. {@code /crawlspace export} writes what is in force
 * as a complete datapack to start from.</p>
 *
 * <p><b>A dungeon keeps the layout it was born with.</b> It is planned again
 * from its seed for every chunk it touches, so a layout number that changed
 * under it would move walls in half a dungeon. Each one saves a copy of its
 * themes' layout and dressing (Site), so an edited or removed pack changes
 * only what is harmless: blocks, monsters, bosses and loot.</p>
 */
public final class ThemeData implements ResourceManagerReloadListener {

    static final Logger LOG = LogUtils.getLogger();
    public static final ThemeData INSTANCE = new ThemeData();
    public static final ResourceLocation ID = new ResourceLocation("crawlspace", "themes");

    private static final String[] DESIGNS = {"keep", "round", "pyramid", "temple"};

    private ThemeData() {
    }

    @Override
    public void onResourceManagerReload(ResourceManager rm) {
        Map<String, JsonObject> raw = read(rm, "crawlspace/theme", "theme", Palettes.themeNames());
        Map<String, JsonObject> entrances = read(rm, "crawlspace/entrance", "style", null);
        Map<String, JsonObject> dungeons = read(rm, "crawlspace/dungeon", "id", null);
        // Each theme flattened over its bases, and the built-in theme at the root of that chain.
        Map<String, JsonObject> themes = new TreeMap<>();
        Map<String, String> roots = new HashMap<>();
        for (String name : raw.keySet()) {
            flatten(name, raw, themes, roots, new java.util.ArrayList<>());
        }
        Palettes.apply(themes, roots, entrances);
        Bestiary.apply(themes, roots);
        ThemeLoot.apply(themes);
        EntranceStyle.apply(entrances);
        Map<String, com.sablednah.crawlspace.plan.Theme> made = new TreeMap<>();
        themes.forEach((name, json) -> made.put(name, ThemeJson.read(name, json, DungeonTypes.builtIn(roots.get(name)))));
        DungeonTypes.apply(made, dungeons);
        Map<String, double[]> designs = new HashMap<>();
        entrances.forEach((style, json) -> {
            if (json.has("designs")) {
                JsonObject d = json.getAsJsonObject("designs");
                double[] w = new double[DESIGNS.length];
                double total = 0;
                for (int i = 0; i < DESIGNS.length; i++) {
                    w[i] = d.has(DESIGNS[i]) ? Math.max(0, d.get(DESIGNS[i]).getAsDouble()) : 0;
                    total += w[i];
                }
                if (total > 0) {
                    designs.put(style, w);
                } else {
                    LOG.warn("CrawlSpace entrance '{}': its designs are all 0 (use keep, round, pyramid, temple)", style);
                }
            }
        });
        com.sablednah.crawlspace.build.Towers.designOverride = Map.copyOf(designs)::get;
        if (!themes.isEmpty() || !entrances.isEmpty() || !dungeons.isEmpty()) {
            LOG.info("CrawlSpace: datapack themes {}, entrances {} and dungeon types {}", themes.keySet(), entrances.keySet(),
                    dungeons.keySet());
        }
    }

    /** These merge key by key down a chain of bases; anything else a child gives replaces its base's. */
    private static final Set<String> MERGED = Set.of("blocks", "layout", "dressing", "loot", "boss");

    /**
     * {@code name}'s file laid over its base's, all the way down to a built-in
     * theme, which is recorded in {@code roots}. A built-in theme's own file has
     * itself as its root; a new theme with no "base" starts from the Crypt.
     */
    private static JsonObject flatten(String name, Map<String, JsonObject> raw, Map<String, JsonObject> out,
            Map<String, String> roots, java.util.List<String> chain) {
        if (out.containsKey(name)) {
            return out.get(name);
        }
        JsonObject json = raw.get(name);
        com.sablednah.crawlspace.plan.Theme self = DungeonTypes.builtIn(name);
        if (self != null && json.has("base")) {
            LOG.warn("CrawlSpace theme '{}': a built-in theme cannot take a \"base\"; make a new theme with that base instead", name);
        }
        String baseName = self != null ? self.name() : json.has("base") ? json.get("base").getAsString() : "Crypt";
        String base = null;
        for (String k : raw.keySet()) {
            if (DungeonTypes.id(k).equals(DungeonTypes.id(baseName))) {
                base = k;
            }
        }
        JsonObject flat;
        if (base != null && !base.equals(name) && !chain.contains(base)) {
            chain.add(name);
            JsonObject parent = flatten(base, raw, out, roots, chain);
            flat = merge(parent, json);
            roots.put(name, roots.get(base));
        } else {
            if (base != null && chain.contains(base)) {
                LOG.warn("CrawlSpace theme '{}': its bases go round in a circle ({} -> {}), so it starts from the Crypt", name, chain, base);
                baseName = "Crypt";
            }
            com.sablednah.crawlspace.plan.Theme root = DungeonTypes.builtIn(baseName);
            if (root == null) {
                LOG.warn("CrawlSpace theme '{}': no theme called '{}' to base it on, so it starts from the Crypt", name, baseName);
                root = com.sablednah.crawlspace.plan.Theme.CRYPT;
            }
            flat = json.deepCopy();
            roots.put(name, root.name());
        }
        flat.remove("base");
        flat.addProperty("theme", name);
        out.put(name, flat);
        return flat;
    }

    private static JsonObject merge(JsonObject parent, JsonObject child) {
        JsonObject out = parent.deepCopy();
        for (Map.Entry<String, JsonElement> e : child.entrySet()) {
            if (MERGED.contains(e.getKey()) && out.has(e.getKey()) && out.get(e.getKey()).isJsonObject() && e.getValue().isJsonObject()) {
                JsonObject into = out.getAsJsonObject(e.getKey());
                e.getValue().getAsJsonObject().entrySet().forEach(b -> into.add(b.getKey(), b.getValue().deepCopy()));
            } else {
                out.add(e.getKey(), e.getValue().deepCopy());
            }
        }
        return out;
    }

    /**
     * Every file under {@code dir}, merged per name in file-id order: a later
     * file's keys replace an earlier one's, and "blocks" merges role by role.
     */
    private static Map<String, JsonObject> read(ResourceManager rm, String dir, String nameKey, Set<String> known) {
        Map<String, JsonObject> out = new TreeMap<>();
        Map<ResourceLocation, Resource> files = new TreeMap<>(rm.listResources(dir, id -> id.getPath().endsWith(".json")));
        files.forEach((id, res) -> {
            JsonObject json;
            try (Reader r = res.openAsReader()) {
                json = JsonParser.parseReader(r).getAsJsonObject();
            } catch (Exception e) {
                LOG.warn("CrawlSpace: skipping {}: {}", id, e.getMessage());
                return;
            }
            String file = id.getPath().substring(id.getPath().lastIndexOf('/') + 1).replace(".json", "");
            String name = json.has(nameKey) ? json.get(nameKey).getAsString() : byFileName(file, known);
            if (known != null) {
                // Any spelling of a built-in name is that theme; anything else is a new one, named as written.
                for (String k : known) {
                    if (DungeonTypes.id(k).equals(DungeonTypes.id(name))) {
                        name = k;
                    }
                }
            }
            JsonObject into = out.computeIfAbsent(name, k -> new JsonObject());
            for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                if (MERGED.contains(e.getKey()) && into.has(e.getKey()) && into.get(e.getKey()).isJsonObject() && e.getValue().isJsonObject()) {
                    e.getValue().getAsJsonObject().entrySet().forEach(b -> into.getAsJsonObject(e.getKey()).add(b.getKey(), b.getValue()));
                } else {
                    into.add(e.getKey(), e.getValue());
                }
            }
        });
        return out;
    }

    /**
     * {@code sunken_halls} is "Sunken Halls", and a new theme's file name is
     * its name in title case ({@code research_lab} is "Research Lab"). An
     * entrance's or a dungeon type's file name is its id.
     */
    private static String byFileName(String file, Set<String> known) {
        if (known != null) {
            for (String k : known) {
                if (k.toLowerCase(Locale.ROOT).replace(' ', '_').equals(file)) {
                    return k;
                }
            }
            return DungeonTypes.title(file);
        }
        return file;
    }

    /**
     * What is in force now, as a datapack folder: {@code pack.mcmeta}, a theme
     * file per theme and an entrance file per style. Returns how many files.
     */
    static int export(java.nio.file.Path root) throws java.io.IOException {
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        java.nio.file.Path data = root.resolve("data/crawlspace/crawlspace");
        java.nio.file.Files.createDirectories(data.resolve("theme"));
        java.nio.file.Files.createDirectories(data.resolve("entrance"));
        java.nio.file.Files.createDirectories(data.resolve("dungeon"));
        java.nio.file.Files.writeString(root.resolve("pack.mcmeta"), """
                {
                  "pack": {
                    "description": "CrawlSpace themes, entrances and dungeon types, exported to edit",
                    "min_format": 82,
                    "max_format": 999
                  }
                }
                """);
        int n = 1;
        for (Map.Entry<String, JsonObject> e : Palettes.export(false).entrySet()) {
            Bestiary.export(e.getKey(), e.getValue());
            ThemeLoot.export(e.getKey(), e.getValue());
            com.sablednah.crawlspace.plan.Theme t = DungeonTypes.theme(e.getKey());
            if (t != null) {
                ThemeJson.write(t).entrySet().forEach(x -> e.getValue().add(x.getKey(), x.getValue()));
            }
            java.nio.file.Files.writeString(data.resolve("theme/" + e.getKey().toLowerCase(Locale.ROOT).replace(' ', '_') + ".json"),
                    gson.toJson(e.getValue()));
            n++;
        }
        for (Map.Entry<String, JsonObject> e : Palettes.export(true).entrySet()) {
            JsonObject d = new JsonObject();
            double[] w = com.sablednah.crawlspace.build.Towers.designWeights(e.getKey());
            for (int i = 0; i < DESIGNS.length; i++) {
                d.addProperty(DESIGNS[i], w[i]);
            }
            e.getValue().add("designs", d);
            java.nio.file.Files.writeString(data.resolve("entrance/" + e.getKey() + ".json"), gson.toJson(e.getValue()));
            n++;
        }
        for (DungeonTypes.Type t : DungeonTypes.types()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", t.name());
            o.addProperty("weight", t.weight());
            com.google.gson.JsonArray levels = new com.google.gson.JsonArray();
            t.levels().forEach(levels::add);
            o.add("levels", levels);
            if (t.entrance() != null) {
                o.addProperty("entrance", t.entrance());
            }
            if (t.maxLevels() > 0) {
                o.addProperty("max_levels", t.maxLevels());
            }
            if (!t.anywhere()) {
                com.google.gson.JsonArray biomes = new com.google.gson.JsonArray();
                t.biomes().stream().map(ResourceLocation::toString).sorted().forEach(biomes::add);
                t.tags().stream().map(k -> "#" + k.location()).sorted().forEach(biomes::add);
                o.add("biomes", biomes);
                o.addProperty("exclusive", t.exclusive());
            }
            java.nio.file.Files.writeString(data.resolve("dungeon/" + t.id() + ".json"), gson.toJson(o));
            n++;
        }
        return n;
    }
}
