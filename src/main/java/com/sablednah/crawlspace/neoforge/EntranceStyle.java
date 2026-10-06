package com.sablednah.crawlspace.neoforge;

import java.util.Locale;

import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/** Picks how the entrance tower is dressed from the biome it stands in. */
public final class EntranceStyle {

    private EntranceStyle() {
    }

    public static String of(Holder<Biome> biome) {
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
