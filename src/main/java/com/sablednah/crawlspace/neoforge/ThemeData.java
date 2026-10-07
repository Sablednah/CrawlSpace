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

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Datapack themes and entrances, read on every load and /reload:
 *
 * <ul>
 * <li>{@code data/<namespace>/crawlspace/theme/<name>.json}: a theme's blocks by
 * role, its monsters and its boss. The file names its theme with "theme", or by
 * its own name ({@code sunken_halls.json} is Sunken Halls).</li>
 * <li>{@code data/<namespace>/crawlspace/entrance/<style>.json}: an entrance
 * style's blocks, the weights of its four tower designs, and the biomes it
 * claims. A new name makes a new style.</li>
 * </ul>
 *
 * <p>Everything is laid over the built-in defaults key by key, so a file need
 * only say what it changes. {@code /crawlspace export} writes what is in force
 * as a complete datapack to start from.</p>
 *
 * <p><b>What data cannot change is deliberate:</b> a level's layout. A
 * generated dungeon is planned again from its seed for every chunk it
 * touches, so anything that moved a wall would let a pack change half a
 * dungeon. Blocks, monsters and towers can change between sessions without
 * breaking one; the plan cannot.</p>
 */
public final class ThemeData implements ResourceManagerReloadListener {

    static final Logger LOG = LogUtils.getLogger();
    public static final ThemeData INSTANCE = new ThemeData();
    public static final Identifier ID = Identifier.fromNamespaceAndPath("crawlspace", "themes");

    private static final String[] DESIGNS = {"keep", "round", "pyramid", "temple"};

    private ThemeData() {
    }

    @Override
    public void onResourceManagerReload(ResourceManager rm) {
        Map<String, JsonObject> themes = read(rm, "crawlspace/theme", "theme", Palettes.themeNames());
        Map<String, JsonObject> entrances = read(rm, "crawlspace/entrance", "style", null);
        Palettes.apply(themes, entrances);
        Bestiary.apply(themes);
        EntranceStyle.apply(entrances);
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
        if (!themes.isEmpty() || !entrances.isEmpty()) {
            LOG.info("CrawlSpace: datapack themes {} and entrances {}", themes.keySet(), entrances.keySet());
        }
    }

    /**
     * Every file under {@code dir}, merged per name in file-id order: a later
     * file's keys replace an earlier one's, and "blocks" merges role by role.
     */
    private static Map<String, JsonObject> read(ResourceManager rm, String dir, String nameKey, Set<String> known) {
        Map<String, JsonObject> out = new TreeMap<>();
        Map<Identifier, Resource> files = new TreeMap<>(rm.listResources(dir, id -> id.getPath().endsWith(".json")));
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
            if (known != null && !known.contains(name)) {
                LOG.warn("CrawlSpace: skipping {}: no theme called '{}'. Themes are {}", id, name, known);
                return;
            }
            JsonObject into = out.computeIfAbsent(name, k -> new JsonObject());
            for (Map.Entry<String, JsonElement> e : json.entrySet()) {
                if (e.getKey().equals("blocks") && into.has("blocks") && e.getValue().isJsonObject()) {
                    e.getValue().getAsJsonObject().entrySet().forEach(b -> into.getAsJsonObject("blocks").add(b.getKey(), b.getValue()));
                } else {
                    into.add(e.getKey(), e.getValue());
                }
            }
        });
        return out;
    }

    /** {@code sunken_halls} is "Sunken Halls"; an entrance's file name is its style. */
    private static String byFileName(String file, Set<String> known) {
        if (known != null) {
            for (String k : known) {
                if (k.toLowerCase(Locale.ROOT).replace(' ', '_').equals(file)) {
                    return k;
                }
            }
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
        java.nio.file.Files.writeString(root.resolve("pack.mcmeta"), """
                {
                  "pack": {
                    "description": "CrawlSpace themes and entrances, exported to edit",
                    "min_format": 82,
                    "max_format": 999
                  }
                }
                """);
        int n = 1;
        for (Map.Entry<String, JsonObject> e : Palettes.export(false).entrySet()) {
            Bestiary.export(e.getKey(), e.getValue());
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
        return n;
    }
}
