package com.sablednah.crawlspace.neoforge;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * What CrawlSpace remembers about one world: which triggers have fired (so a
 * sprung trap stays sprung and a found secret door stays open across
 * restarts), and the dungeons built by command, which have no structure data
 * of their own to be found by.
 */
public final class CrawlState extends SavedData {

    private static final Codec<CrawlState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.listOf().fieldOf("fired").forGetter(s -> new ArrayList<>(s.fired)),
            CompoundTag.CODEC.listOf().fieldOf("built").forGetter(s -> s.built.stream().map(Site::save).toList()),
            // Optional, so a world saved before it existed loads as it was.
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("values", java.util.Map.of()).forGetter(s -> s.values)
    ).apply(i, (fired, built, values) -> {
        CrawlState s = new CrawlState();
        s.fired.addAll(fired);
        built.forEach(t -> s.built.add(Site.load(t)));
        s.values.putAll(values);
        return s;
    }));

    private static final SavedDataType<CrawlState> TYPE = new SavedDataType<>("crawlspace", CrawlState::new, CODEC);

    private final Set<Long> fired = new HashSet<>();
    private final List<Site> built = new ArrayList<>();
    /** Small facts by name: a dungeon's last restock, its replay traits. */
    private final java.util.Map<String, String> values = new java.util.HashMap<>();

    public static CrawlState of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public boolean hasFired(BlockPos pos) {
        return fired.contains(pos.asLong());
    }

    public void fire(BlockPos pos) {
        if (fired.add(pos.asLong())) {
            setDirty();
        }
    }

    /** Forgets that a trigger fired: a restocked room wakes again. */
    public void unfire(BlockPos pos) {
        if (fired.remove(pos.asLong())) {
            setDirty();
        }
    }

    public String get(String key) {
        return values.get(key);
    }

    public void put(String key, String value) {
        if (value == null ? values.remove(key) != null : !value.equals(values.put(key, value))) {
            setDirty();
        }
    }

    public void addBuilt(Site site) {
        built.removeIf(s -> s.origin().equals(site.origin()));
        built.add(site);
        setDirty();
    }

    public void removeBuilt(BlockPos origin) {
        if (built.removeIf(s -> s.origin().equals(origin))) {
            setDirty();
        }
    }

    public List<Site> built() {
        return built;
    }
}
