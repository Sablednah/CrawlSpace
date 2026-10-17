package com.sablednah.crawlspace.neoforge;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

/**
 * Which loot tables a theme's containers roll, from its "loot":
 *
 * <pre>
 * "loot": {
 *   "chests":   "zarp:bunker/crate",        // or five, tier 1 to 5: ["zarp:t1", ..., "zarp:t5"]
 *   "hoard":    ["zarp:t2", ..., "zarp:t5", "zarp:t5"],  // a treasure room or lair; one tier richer if not given
 *   "supplies": "zarp:bunker/rations"       // barrels
 * }
 * </pre>
 *
 * Without it, CrawlSpace's own tables: {@code crawlspace:chests/tier1..5} and
 * {@code crawlspace:chests/supplies}, which a datapack can also override.
 */
final class ThemeLoot {

    /** Per theme: chest tables by tier (index 0 is tier 1), hoard tables by tier, supplies. Missing means the default. */
    private record Tables(String[] chests, String[] hoard, String supplies) {
    }

    private static volatile Map<String, Tables> loot = Map.of();

    private ThemeLoot() {
    }

    static void apply(Map<String, JsonObject> themes) {
        Map<String, Tables> out = new HashMap<>();
        themes.forEach((theme, json) -> {
            if (!json.has("loot") || !json.get("loot").isJsonObject()) {
                return;
            }
            JsonObject l = json.getAsJsonObject("loot");
            String where = "theme '" + theme + "' loot";
            out.put(theme, new Tables(tiers(l, "chests", where), tiers(l, "hoard", where),
                    l.has("supplies") ? id(l.get("supplies").getAsString(), where) : null));
        });
        loot = Map.copyOf(out);
    }

    /** One table for every tier, or a list of up to five; a short list repeats its last. */
    private static String[] tiers(JsonObject l, String key, String where) {
        if (!l.has(key)) {
            return null;
        }
        JsonElement e = l.get(key);
        String[] out = new String[5];
        if (e.isJsonArray() && !e.getAsJsonArray().isEmpty()) {
            for (int i = 0; i < 5; i++) {
                out[i] = id(e.getAsJsonArray().get(Math.min(i, e.getAsJsonArray().size() - 1)).getAsString(), where);
            }
        } else if (e.isJsonPrimitive()) {
            java.util.Arrays.fill(out, id(e.getAsString(), where));
        } else {
            return null;
        }
        for (String s : out) {
            if (s == null) {
                return null;
            }
        }
        return out;
    }

    private static String id(String s, String where) {
        ResourceLocation id = ResourceLocation.tryParse(s);
        if (id == null) {
            ThemeData.LOG.warn("CrawlSpace {}: '{}' is not a loot table id", where, s);
            return null;
        }
        return id.toString();
    }

    /** A chest's table at a tier (1 to 5) in a theme. */
    static String chest(String theme, int tier) {
        Tables t = loot.get(theme);
        return t != null && t.chests() != null ? t.chests()[tier - 1] : "crawlspace:chests/tier" + tier;
    }

    /** A hoard's table, for a level of loot tier {@code tier}: one tier richer than its chests, unless the theme says. */
    static String hoard(String theme, int tier) {
        Tables t = loot.get(theme);
        if (t != null && t.hoard() != null) {
            return t.hoard()[tier - 1];
        }
        return chest(theme, Math.min(5, tier + 1));
    }

    static String supplies(String theme) {
        Tables t = loot.get(theme);
        return t != null && t.supplies() != null ? t.supplies() : "crawlspace:chests/supplies";
    }

    /** Every table a theme rolls that is not CrawlSpace's own, for /crawlspace export. */
    static void export(String theme, JsonObject into) {
        Tables t = loot.get(theme);
        if (t == null) {
            return;
        }
        JsonObject o = new JsonObject();
        if (t.chests() != null) {
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            for (String s : t.chests()) {
                a.add(s);
            }
            o.add("chests", a);
        }
        if (t.hoard() != null) {
            com.google.gson.JsonArray a = new com.google.gson.JsonArray();
            for (String s : t.hoard()) {
                a.add(s);
            }
            o.add("hoard", a);
        }
        if (t.supplies() != null) {
            o.addProperty("supplies", t.supplies());
        }
        into.add("loot", o);
    }
}
