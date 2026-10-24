package com.sablednah.crawlspace.plan;

import java.util.List;

/**
 * How a theme furnishes its rooms: every rule the dresser used to decide by
 * theme name, as a value a datapack can set. The built-in themes' values
 * reproduce exactly what the hard-coded rules did (FingerprintTest).
 *
 * @param flavour      what ordinary rooms are filled with: "crypt" (sarcophagi, skulls),
 *                     "sunken_halls" (tables, moss), "old_mines" (tables, barrels),
 *                     "caverns" (stalagmites, moss), "deep_halls" (braziers, chains) or "none"
 * @param built        masonry: skirting, door frames and wall niches
 * @param finished     floor borders, coving, panelling and arches (off for raw caves)
 * @param columns      grand rooms get columns standing out from their walls
 * @param cobwebs      chance of a cobweb in each corner
 * @param shelves      chance an ordinary room gets bookshelves
 * @param clutter      what lies about by the walls, by part name; repeat one to make it commoner
 * @param overgrowth   chance a room is overgrown with vines, moss and plants
 * @param roots        overgrown rooms also get roots hanging from the ceiling
 * @param nicheStatues chance an ordinary room lines its niches with statues (lairs and shrines always do)
 * @param beams        ceiling beams across bigger rooms
 * @param timber       mine-style: timber posts, no floor medallion, rails in wide corridors
 * @param panels       chance an ordinary room or hall is panelled
 * @param pillarShelves chance a room's columns stand in a skirt of shelves
 * @param wallShelves  chance a room's walls are skirted with shelves, with a column in each corner
 */
public record Dressing(
        String flavour,
        boolean built,
        boolean finished,
        boolean columns,
        double cobwebs,
        double shelves,
        List<String> clutter,
        double overgrowth,
        boolean roots,
        double nicheStatues,
        boolean beams,
        boolean timber,
        double panels,
        double pillarShelves,
        double wallShelves) {

    public static final List<String> FLAVOURS = List.of("crypt", "sunken_halls", "old_mines", "caverns", "deep_halls", "none");

    /** The things clutter may be: floor props that stand by a wall. */
    public static final List<String> CLUTTER = List.of("skull", "candles", "pot", "floor_lantern", "bones", "cobweb",
            "cauldron", "moss", "barrel", "anvil", "grindstone", "stalagmite", "mushroom", "lectern");

    public Dressing {
        clutter = List.copyOf(clutter);
    }

    static final Dressing CRYPT = new Dressing("crypt", true, true, true, 0.4, 0.25,
            List.of("skull", "candles", "pot", "floor_lantern", "bones", "cobweb"), 0.2, false, 0.5, true, false, 0.5, 0.2, 0.06);
    static final Dressing SUNKEN_HALLS = new Dressing("sunken_halls", true, true, true, 0, 0,
            List.of("pot", "candles", "cauldron", "floor_lantern", "moss"), 0.6, true, 0, false, false, 0.5, 0.15, 0.05);
    static final Dressing OLD_MINES = new Dressing("old_mines", false, true, false, 0.4, 0,
            List.of("barrel", "barrel", "floor_lantern", "anvil", "grindstone", "cauldron", "cobweb"), 0.25, false, 0, true, true, 0.6, 0.3, 0.1);
    static final Dressing CAVERNS = new Dressing("caverns", false, false, false, 0, 0,
            List.of("stalagmite", "mushroom", "mushroom", "candles", "pot"), 0.7, true, 0, false, false, 0.5, 0, 0);
    static final Dressing DEEP_HALLS = new Dressing("deep_halls", true, true, true, 0, 0,
            List.of("skull", "candles", "floor_lantern", "cauldron", "lectern", "pot"), 0.1, false, 0, false, false, 0.5, 0.2, 0.06);
}
