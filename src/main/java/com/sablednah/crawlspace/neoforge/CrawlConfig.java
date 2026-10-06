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

    /** Whether secret walls look different and traps and treasure give off particles. */
    public enum Hints {
        /** On, unless LegendQuest is installed: then its perception checks do the finding. */
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
                "AUTO is on unless LegendQuest is installed.")
                .defineEnum("hints", Hints.AUTO);
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

    public static boolean hints() {
        return switch (get(HINTS, Hints.AUTO)) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> !net.neoforged.fml.ModList.get().isLoaded("legendquest");
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
