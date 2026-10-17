package com.sablednah.crawlspace.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sablednah.crawlspace.plan.Dressing;
import com.sablednah.crawlspace.plan.Theme;

/**
 * A theme's "layout" and "dressing" as JSON, both ways. Read lays a file over
 * a base theme key by key, so a file need only say what it changes; write is
 * complete, for /crawlspace export and for the copy a dungeon keeps of the
 * themes it was planned with (Site).
 *
 * <pre>
 * "layout": {
 *   "shapes": {"rect": 4, "rounded": 1, "circle": 0.5, "octagon": 2, "cross": 2, "l": 1, "cave": 0, "hall": 1.5},
 *   "corridors": {"straight": 6, "angled": 2, "winding": 0, "curved": 1},
 *   "narrow_chance": 0.35, "height_spread": 0, "pool_chance": 0, "room_scale": 1.0
 * },
 * "dressing": {
 *   "flavour": "crypt", "built": true, "finished": true, "columns": true,
 *   "cobwebs": 0.4, "shelves": 0.25, "clutter": ["skull", "candles"],
 *   "overgrowth": 0.2, "roots": false, "niche_statues": 0.5,
 *   "beams": true, "timber": false, "panels": 0.5
 * }
 * </pre>
 */
final class ThemeJson {

    /**
     * The planner's proven range: every shape alone, at both ends of these, plans
     * and seals (ThemeKnobsTest). Outside it a level may find no valid plan.
     */
    static final int MAX_SPREAD = com.sablednah.crawlspace.plan.Theme.MAX_SPREAD;
    static final double MIN_SCALE = com.sablednah.crawlspace.plan.Theme.MIN_SCALE;
    static final double MAX_SCALE = com.sablednah.crawlspace.plan.Theme.MAX_SCALE;

    /** Its own logger, not ThemeData's: this class is tested without Minecraft, and ThemeData is a Minecraft listener. */
    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("CrawlSpace");

    private ThemeJson() {
    }

    /** {@code base} with whatever {@code json}'s "layout" and "dressing" change, renamed {@code name}. */
    static Theme read(String name, JsonObject json, Theme base) {
        String where = "theme '" + name + "'";
        JsonObject l = object(json, "layout", where);
        double[] shapes = weights(l, "shapes", Theme.SHAPES, base.shapeWeights(), where);
        double[] corridors = weights(l, "corridors", Theme.CORRIDORS, base.styleWeights(), where);
        double narrow = unit(l, "narrow_chance", base.narrowChance(), where);
        int spread = Math.max(0, Math.min(MAX_SPREAD, num(l, "height_spread", base.heightSpread(), where).intValue()));
        double pool = unit(l, "pool_chance", base.poolChance(), where);
        double scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, num(l, "room_scale", base.roomScale(), where).doubleValue()));

        Dressing b = base.dressing();
        JsonObject d = object(json, "dressing", where);
        String flavour = d.has("flavour") ? d.get("flavour").getAsString().toLowerCase(Locale.ROOT).replace(' ', '_') : b.flavour();
        if (!Dressing.FLAVOURS.contains(flavour)) {
            LOG.warn("CrawlSpace {}: no flavour called '{}'. Flavours: {}", where, flavour, Dressing.FLAVOURS);
            flavour = b.flavour();
        }
        List<String> clutter = b.clutter();
        if (d.has("clutter")) {
            clutter = new ArrayList<>();
            for (JsonElement e : d.getAsJsonArray("clutter")) {
                String c = e.getAsString().toLowerCase(Locale.ROOT);
                if (Dressing.CLUTTER.contains(c)) {
                    clutter.add(c);
                } else {
                    LOG.warn("CrawlSpace {}: no clutter called '{}'. Clutter: {}", where, c, Dressing.CLUTTER);
                }
            }
        }
        Dressing dressing = new Dressing(flavour,
                bool(d, "built", b.built()), bool(d, "finished", b.finished()), bool(d, "columns", b.columns()),
                unit(d, "cobwebs", b.cobwebs(), where), unit(d, "shelves", b.shelves(), where), clutter,
                unit(d, "overgrowth", b.overgrowth(), where), bool(d, "roots", b.roots()),
                unit(d, "niche_statues", b.nicheStatues(), where), bool(d, "beams", b.beams()),
                bool(d, "timber", b.timber()), unit(d, "panels", b.panels(), where));
        return new Theme(name, shapes, corridors, narrow, spread, pool, scale, dressing);
    }

    /** Every knob, so the file can be edited or the theme rebuilt exactly. */
    static JsonObject write(Theme t) {
        JsonObject l = new JsonObject();
        l.add("shapes", names(Theme.SHAPES, t.shapeWeights()));
        l.add("corridors", names(Theme.CORRIDORS, t.styleWeights()));
        l.addProperty("narrow_chance", t.narrowChance());
        l.addProperty("height_spread", t.heightSpread());
        l.addProperty("pool_chance", t.poolChance());
        l.addProperty("room_scale", t.roomScale());
        Dressing dr = t.dressing();
        JsonObject d = new JsonObject();
        d.addProperty("flavour", dr.flavour());
        d.addProperty("built", dr.built());
        d.addProperty("finished", dr.finished());
        d.addProperty("columns", dr.columns());
        d.addProperty("cobwebs", dr.cobwebs());
        d.addProperty("shelves", dr.shelves());
        JsonArray c = new JsonArray();
        dr.clutter().forEach(c::add);
        d.add("clutter", c);
        d.addProperty("overgrowth", dr.overgrowth());
        d.addProperty("roots", dr.roots());
        d.addProperty("niche_statues", dr.nicheStatues());
        d.addProperty("beams", dr.beams());
        d.addProperty("timber", dr.timber());
        d.addProperty("panels", dr.panels());
        JsonObject o = new JsonObject();
        o.add("layout", l);
        o.add("dressing", d);
        return o;
    }

    private static JsonObject names(List<String> names, double[] w) {
        JsonObject o = new JsonObject();
        for (int i = 0; i < names.size(); i++) {
            o.addProperty(names.get(i), w[i]);
        }
        return o;
    }

    private static JsonObject object(JsonObject json, String key, String where) {
        if (json.has(key) && json.get(key).isJsonObject()) {
            return json.getAsJsonObject(key);
        }
        if (json.has(key)) {
            LOG.warn("CrawlSpace {}: \"{}\" should be an object", where, key);
        }
        return new JsonObject();
    }

    /** A weight per name; names not given keep the base's. At least one must stay above 0. */
    private static double[] weights(JsonObject o, String key, List<String> names, double[] base, String where) {
        double[] w = base.clone();
        if (!o.has(key)) {
            return w;
        }
        for (var e : o.getAsJsonObject(key).entrySet()) {
            int i = names.indexOf(e.getKey().toLowerCase(Locale.ROOT));
            if (i < 0) {
                LOG.warn("CrawlSpace {}: no {} called '{}'. They are {}", where, key, e.getKey(), names);
                continue;
            }
            w[i] = Math.max(0, e.getValue().getAsDouble());
        }
        double total = 0;
        for (double x : w) {
            total += x;
        }
        if (total <= 0) {
            LOG.warn("CrawlSpace {}: its {} are all 0, so it keeps its base's", where, key);
            return base.clone();
        }
        return w;
    }

    private static Number num(JsonObject o, String key, Number fallback, String where) {
        if (!o.has(key)) {
            return fallback;
        }
        try {
            return o.get(key).getAsDouble();
        } catch (RuntimeException e) {
            LOG.warn("CrawlSpace {}: \"{}\" should be a number", where, key);
            return fallback;
        }
    }

    private static double unit(JsonObject o, String key, double fallback, String where) {
        return Math.max(0, Math.min(1, num(o, key, fallback, where).doubleValue()));
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.has(key) ? o.get(key).getAsBoolean() : fallback;
    }

    /**
     * The themes a dungeon was planned with, as it keeps them: by depth, and
     * every number of every theme that is not a built-in one untouched. A
     * pack edited or removed later then changes its blocks and monsters, which
     * is harmless, and never its walls. Null for the built-in list.
     */
    static String snapshot(List<Theme> byDepth) {
        if (byDepth == Theme.BY_DEPTH) {
            return null;
        }
        JsonObject o = new JsonObject();
        JsonArray names = new JsonArray();
        JsonObject defs = new JsonObject();
        for (Theme t : byDepth) {
            names.add(t.name());
            if (Theme.builtIn(t.name()) != t && !defs.has(t.name())) {
                defs.add(t.name(), ThemeJson.write(t));
            }
        }
        o.add("by_depth", names);
        o.add("themes", defs);
        return o.toString();
    }

    /** The inverse of {@link #snapshot}; the built-in list for null or anything unreadable. */
    static List<Theme> restore(String snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return Theme.BY_DEPTH;
        }
        try {
            JsonObject o = JsonParser.parseString(snapshot).getAsJsonObject();
            JsonObject defs = o.getAsJsonObject("themes");
            Map<String, Theme> made = new HashMap<>();
            List<Theme> out = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("by_depth")) {
                String name = e.getAsString();
                Theme t = made.computeIfAbsent(name, n -> {
                    Theme b = Theme.builtIn(n);
                    if (defs.has(n)) {
                        return ThemeJson.read(n, defs.getAsJsonObject(n), b != null ? b : Theme.CRYPT);
                    }
                    return b != null ? b : Theme.CRYPT;
                });
                out.add(t);
            }
            return out.isEmpty() ? Theme.BY_DEPTH : List.copyOf(out);
        } catch (RuntimeException e) {
            LOG.error("CrawlSpace: a dungeon's saved themes could not be read, so it uses the built-in ones: {}", e.toString());
            return Theme.BY_DEPTH;
        }
    }
}
