package com.sablednah.crawlspace.neoforge;

import java.util.Locale;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/** Picks how the entrance tower is dressed from the biome it stands in. */
public final class EntranceStyle {

    private EntranceStyle() {
    }

    /** A datapack style and the biomes (ids, or #tags) it claims. Checked before the built-in rules. */
    private record Claim(String style, java.util.Set<net.minecraft.resources.Identifier> biomes,
            java.util.List<net.minecraft.tags.TagKey<Biome>> tags) {
    }

    private static volatile java.util.List<Claim> claims = java.util.List.of();

    /** From the entrance files ThemeData read: those with a "biomes" list claim them. */
    static void apply(java.util.Map<String, com.google.gson.JsonObject> entrances) {
        java.util.List<Claim> out = new java.util.ArrayList<>();
        entrances.forEach((style, json) -> {
            if (!json.has("biomes")) {
                return;
            }
            java.util.Set<net.minecraft.resources.Identifier> ids = new java.util.HashSet<>();
            java.util.List<net.minecraft.tags.TagKey<Biome>> tags = new java.util.ArrayList<>();
            for (com.google.gson.JsonElement e : json.getAsJsonArray("biomes")) {
                String s = e.getAsString();
                if (s.startsWith("#")) {
                    tags.add(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME,
                            net.minecraft.resources.Identifier.parse(s.substring(1))));
                } else {
                    ids.add(net.minecraft.resources.Identifier.parse(s));
                }
            }
            out.add(new Claim(style, ids, tags));
        });
        claims = java.util.List.copyOf(out);
    }

    public static String of(Holder<Biome> biome) {
        for (Claim c : claims) {
            if (biome.unwrapKey().map(k -> c.biomes().contains(k.identifier())).orElse(false)
                    || c.tags().stream().anyMatch(biome::is)) {
                return c.style();
            }
        }
        String path = biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("").toLowerCase(Locale.ROOT);
        if (biome.is(BiomeTags.IS_BADLANDS)) {
            return "terracotta";
        }
        if (path.contains("mushroom")) {
            return "mushroom";
        }
        if (path.contains("cherry")) {
            return "cherry";
        }
        if (path.contains("pale")) {
            return "pale";
        }
        if (path.contains("mangrove")) {
            return "mangrove";
        }
        if (path.contains("desert") || path.contains("beach")) {
            return "sandstone";
        }
        if (path.contains("swamp") || biome.is(BiomeTags.IS_JUNGLE)) {
            return "mossy";
        }
        if (path.contains("snow") || path.contains("ice") || path.contains("frozen") || path.contains("grove")
                || path.contains("peaks") || path.contains("slopes")) {
            return "snowy";
        }
        if (biome.is(BiomeTags.IS_SAVANNA)) {
            return "terracotta";
        }
        if (biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_FOREST)) {
            return "woodland";
        }
        return "stone";
    }
}
