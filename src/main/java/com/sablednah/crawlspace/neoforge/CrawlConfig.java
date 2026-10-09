package com.sablednah.crawlspace.neoforge;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server settings ({@code config/crawlspace-server.toml}; NeoForge 21.x keeps server configs there, not per world). Read live
 * by worldgen, so a change applies to chunks generated afterwards; dungeons
 * already in the world stay where they are.
 */
public final class CrawlConfig {

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.IntValue SPACING;
    private static final ModConfigSpec.IntValue SEPARATION;
    private static final ModConfigSpec.IntValue MAX_LEVELS;
    private static final ModConfigSpec.IntValue MIN_LEVELS;
    private static final ModConfigSpec.EnumValue<Hints> HINTS;
    private static final ModConfigSpec.DoubleValue DISARM_CHANCE;
    private static final ModConfigSpec.IntValue SPAWNER_USES;
    private static final ModConfigSpec.BooleanValue PROTECT;
    private static final ModConfigSpec.BooleanValue PROTECT_EXPLOSIONS;
    private static final ModConfigSpec.BooleanValue PROTECT_REPAIR;

    /** Whether secret walls look different and traps and treasure give off particles. */
    public enum Hints {
        /** On, unless a mod has registered a perception check (CrawlSpaceApi): then that does the finding. */
        AUTO,
        ALWAYS,
        NEVER
    }

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("worldgen");
        ENABLED = b.comment("Generate dungeons in new chunks.").define("enabled", true);
        SPACING = b.comment(
                "Average distance between dungeons, in chunks: one per spacing x spacing square.",
                "Dungeon Crawl uses 32 and villages 34. CrawlSpace dungeons are up to 15 chunks across, so",
                "36 keeps them about as common as villages.")
                .defineInRange("spacing", 36, 8, 512);
        SEPARATION = b.comment(
                "Minimum distance between two dungeons, in chunks. Must be less than spacing.",
                "Below 16 two dungeons can overlap underground.")
                .defineInRange("separation", 16, 4, 511);
        MAX_LEVELS = b.comment("Levels below the entrance, at most. Fewer where the world is not deep enough.")
                .defineInRange("maxLevels", 6, 1, 9);
        MIN_LEVELS = b.comment("Skip a site where fewer than this many levels fit above the bottom of the world.")
                .defineInRange("minLevels", 2, 1, 9);
        b.pop();
        b.push("play");
        HINTS = b.comment(
                "Tells for players without a perception skill: secret walls built from a related but different block,",
                "and faint particles near you, red for an unsprung trap, green for a secret door or treasure.",
                "AUTO is on unless a mod (LegendQuest, say) has registered its own perception checks.")
                .defineEnum("hints", Hints.AUTO);
        DISARM_CHANCE = b.comment(
                "Without a perception mod: the chance that breaking a trap's plate or wire, or sneak-using it,",
                "disarms it rather than setting it off. A perception mod (LegendQuest) rolls its own instead.")
                .defineInRange("disarmChance", 0.75, 0.0, 1.0);
        SPAWNER_USES = b.comment(
                "How many monsters a dungeon's spawner makes before it burns out, so a dungeon is not a farm.",
                "0: spawners never burn out.")
                .defineInRange("spawnerUses", 24, 0, 100000);
        b.pop();
        b.push("protection");
        PROTECT = b.comment(
                "Keep the dungeon whole: players in survival cannot break its walls, floors, ceilings, stairs, tower,",
                "locked doors or puzzle rooms, so the way down is through it rather than round it. Dressing still breaks",
                "(chests, spawners, pots, cobwebs, furniture, wooden doors), as do trap plates and anything a player placed.",
                "Off: dungeons are ordinary blocks, minable like the rest of the world.")
                .define("enabled", true);
        PROTECT_EXPLOSIONS = b.comment(
                "With protection on: creepers and TNT still hurt, and still break the dressing, but not the shell.")
                .define("explosions", true);
        PROTECT_REPAIR = b.comment(
                "With protection on: put back any shell block found missing near a player, every few seconds.",
                "Catches machines and mods that remove blocks without the usual break event (drills, lasers, tunnellers).")
                .define("repair", true);
        b.pop();
        SPEC = b.build();
    }

    private CrawlConfig() {
    }

    public static boolean enabled() {
        return get(ENABLED, true);
    }

    public static int spacing() {
        return get(SPACING, 36);
    }

    /** Never as large as spacing: vanilla's own placement refuses that, and so do we. */
    public static int separation() {
        return Math.min(get(SEPARATION, 16), spacing() - 1);
    }

    public static int maxLevels() {
        return get(MAX_LEVELS, 6);
    }

    public static int minLevels() {
        return Math.min(get(MIN_LEVELS, 2), maxLevels());
    }

    public static int spawnerUses() {
        return get(SPAWNER_USES, 24);
    }

    public static double disarmChance() {
        return get(DISARM_CHANCE, 0.75);
    }

    public static boolean protect() {
        return get(PROTECT, true);
    }

    public static boolean protectExplosions() {
        return protect() && get(PROTECT_EXPLOSIONS, true);
    }

    public static boolean protectRepair() {
        return protect() && get(PROTECT_REPAIR, true);
    }

    /**
     * Whether real traps are built with their plate or wire showing. Only when
     * no perception mod is registered: with one, a trap's plate appears when a
     * player notices it.
     */
    public static boolean trapsVisible() {
        return com.sablednah.crawlspace.api.CrawlSpaceApi.perception().isEmpty();
    }

    public static boolean hints() {
        return switch (get(HINTS, Hints.AUTO)) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> com.sablednah.crawlspace.api.CrawlSpaceApi.perception().isEmpty();
        };
    }

    /** Before the world's config is loaded, the defaults. */
    private static <T> T get(ModConfigSpec.ConfigValue<T> v, T fallback) {
        try {
            return v.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }
}
