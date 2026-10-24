package com.sablednah.crawlspace.neoforge;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/**
 * ZombieMod genera for a theme's monsters and bosses ({@code "genus":
 * "zombiemod:harvester"} on a mob or boss entry), asked for by ZARP so a lab
 * can hold Patients and Harvesters rather than whatever ZombieMod rolls.
 *
 * <p>Through {@code com.sablednah.zombiemod.api.ZombieModApi.applyGenus(Mob,
 * ResourceLocation)}, found by name: its signature is ZombieMod's public contract and
 * every type in it is vanilla, so there is no dependency to declare and no
 * class of theirs to load when ZombieMod is absent. It is called as soon as the
 * mob exists, before {@code finalizeSpawn} (ZombieMod's own spawn hook then
 * leaves it alone) and before a boss's name, size, health and bar go on top.
 * Server thread only, which is where every caller here runs.</p>
 *
 * <p>Without ZombieMod, or when the genus is unknown or not of this mob's type,
 * the plain mob spawns and the server log says so, once per theme and genus.</p>
 */
final class Genera {

    private static volatile Method apply;
    private static volatile boolean looked;
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private Genera() {
    }

    /** Dresses {@code mob} as {@code genus}; false, with a warning naming {@code where}, if it could not. */
    static boolean apply(Mob mob, ResourceLocation genus, String where) {
        if (genus == null) {
            return false;
        }
        Method m = method();
        boolean ok = false;
        if (m != null) {
            try {
                ok = Boolean.TRUE.equals(m.invoke(null, mob, genus));
            } catch (ReflectiveOperationException | RuntimeException e) {
                ThemeData.LOG.warn("CrawlSpace {}: ZombieMod refused genus '{}': {}", where, genus, e.toString());
            }
        }
        if (!ok && WARNED.add(where + "|" + genus)) {
            ThemeData.LOG.warn("CrawlSpace {}: genus '{}' was not applied to a {} ({}), so it spawns as a plain one",
                    where, genus, net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()),
                    m == null ? "ZombieMod, or its API, is not installed" : "unknown genus, or its base is another mob type");
        }
        return ok;
    }

    private static Method method() {
        if (!looked) {
            looked = true;
            if (net.minecraftforge.fml.ModList.get().isLoaded("zombiemod")) {
                try {
                    apply = Class.forName("com.sablednah.zombiemod.api.ZombieModApi")
                            .getMethod("applyGenus", Mob.class, ResourceLocation.class);
                } catch (ReflectiveOperationException | LinkageError e) {
                    ThemeData.LOG.warn("CrawlSpace: ZombieMod is installed but has no ZombieModApi.applyGenus ({}): "
                            + "genus entries spawn plain mobs", e.toString());
                }
            }
        }
        return apply;
    }

    /** "zombiemod:harvester" from a theme file, or null with a warning if it is not an id. */
    static ResourceLocation parse(com.google.gson.JsonObject o, String key, String where) {
        if (o == null || !o.has(key)) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(o.get(key).getAsString());
        if (id == null) {
            ThemeData.LOG.warn("CrawlSpace {}: '{}' is not a genus id", where, o.get(key).getAsString());
        }
        return id;
    }
}
