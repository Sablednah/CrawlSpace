package com.sablednah.crawlspace.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Blueprint;
import com.sablednah.crawlspace.build.Part;
import com.sablednah.crawlspace.build.Trigger;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * The thing you should not touch (Death Frost Doom; Sable's list, 2026-10-11).
 * A sealed sarcophagus, ringed with skulls, warns anyone who comes near. Use
 * it anyway and it pays: a hoard two tiers richer, at once. And the Restless
 * Dead rise: every room already cleared on that level fills again, a purple
 * bar says so, and the risen sometimes carry loot. Leaving the level ends it,
 * and the game says that the moment it starts.
 */
public final class Forbidden {

    static final String RESTLESS = "crawlspace_restless";
    /** Seconds with nobody on the level before the dead lie still. */
    private static final int EMPTY_FOR = 10;
    private static final Map<UUID, Set<Long>> WARNED = new HashMap<>();

    private static final class Rising {
        final ServerLevel level;
        final Site site;
        final int li;
        long emptySince = -1;
        final ServerBossEvent bar = new ServerBossEvent(Component.literal("The Restless Dead").withStyle(ChatFormatting.DARK_PURPLE),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);

        Rising(ServerLevel level, Site site, int li) {
            this.level = level;
            this.site = site;
            this.li = li;
        }
    }

    private static final List<Rising> RISINGS = new ArrayList<>();

    private Forbidden() {
    }

    /** Once a session, within six: the warning, which is the whole point of the thing. */
    static void tick(ServerLevel level, ServerPlayer player, Site site) {
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() != Trigger.Kind.FORBIDDEN) {
                continue;
            }
            BlockPos at = site.origin().offset(t.x(), t.y(), t.z());
            if (at.closerThan(player.blockPosition(), 6) && WARNED.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(at.asLong())
                    && !CrawlState.of(level).hasFired(at)) {
                Triggers.tell(player, "A sealed tomb, chained shut and ringed with skulls. Everything about it says: do not touch.");
            }
        }
    }

    /** Using a block: the forbidden thing, if it is. Returns whether it was. */
    static boolean use(ServerLevel level, ServerPlayer player, Site site, BlockPos pos) {
        BlockPos o = site.origin();
        int code = site.built().blueprint().get(pos.getX() - o.getX(), pos.getY() - o.getY(), pos.getZ() - o.getZ());
        if (code == 0 || Blueprint.part(code) != Part.FORBIDDEN) {
            return false;
        }
        CrawlState state = CrawlState.of(level);
        if (state.hasFired(pos)) {
            Triggers.tell(player, "The seal is broken. It is empty now.");
            return true;
        }
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            Triggers.tell(player, "The seal will not break. (The world is on Peaceful.)");
            return true;
        }
        state.fire(pos);
        int li = Blueprint.level(code);
        // It pays, at once.
        var loot = level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.LOOT_TABLE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(CrawlSpace.MODID, "chests/tier" + Math.min(5, 3 + li / 2))));
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, net.minecraft.world.phys.Vec3.atCenterOf(pos.above()))
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        for (ItemStack stack : loot.getRandomItems(params)) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, stack);
        }
        level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 60, 1.5, 1, 1.5, 0.05);
        level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8f, 0.6f);
        // And the dead rise.
        int refilled = 0;
        BlockPos origin = site.origin();
        for (Trigger t : site.built().blueprint().triggers()) {
            if (!t.kind().isEncounter() || t.kind() == Trigger.Kind.BOSS || t.level() != li || !state.hasFired(origin.offset(t.x(), t.y(), t.z()))) {
                continue;
            }
            if (Bestiary.wake(level, site, t) != null) {
                refilled++;
                BlockPos at = origin.offset(t.x(), t.y(), t.z());
                for (Mob m : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(at).inflate(14),
                        m -> m.getTags().contains(Bestiary.KIN) && !m.getTags().contains(RESTLESS) && m.tickCount < 5)) {
                    m.addTag(RESTLESS);
                }
            }
        }
        Rising r = new Rising(level, site, li);
        RISINGS.add(r);
        for (ServerPlayer p : onLevel(r)) {
            r.bar.addPlayer(p);
            p.sendSystemMessage(Component.literal("The seal breaks. The Restless Dead rise: "
                    + (refilled == 0 ? "nothing here was cleared yet, but they are waking."
                            : refilled + (refilled == 1 ? " room" : " rooms") + " you cleared on this level " + (refilled == 1 ? "is" : "are") + " full again.")
                    + " Leave the level and they lie still.").withStyle(ChatFormatting.DARK_PURPLE));
        }
        CrawlSpace.LOGGER.info("CrawlSpace: the forbidden thing at {} opened; {} room(s) refilled", pos, refilled);
        return true;
    }

    private static List<ServerPlayer> onLevel(Rising r) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : r.level.players()) {
            if (Arrivals.levelAt(r.site, p.blockPosition()) == r.li && Dungeons.at(r.level, p.blockPosition()).orElse(null) == r.site) {
                out.add(p);
            }
        }
        return out;
    }

    public static void tick(long gameTime) {
        if (gameTime % 20 != 0 || RISINGS.isEmpty()) {
            return;
        }
        for (Rising r : new ArrayList<>(RISINGS)) {
            List<ServerPlayer> here = onLevel(r);
            for (ServerPlayer p : new ArrayList<>(r.bar.getPlayers())) {
                if (!here.contains(p)) {
                    r.bar.removePlayer(p);
                }
            }
            for (ServerPlayer p : here) {
                r.bar.addPlayer(p);
            }
            if (!here.isEmpty()) {
                r.emptySince = -1;
                continue;
            }
            long now = r.level.getGameTime();
            if (r.emptySince < 0) {
                r.emptySince = now;
            } else if (now - r.emptySince >= EMPTY_FOR * 20L) {
                end(r);
            }
        }
    }

    private static void end(Rising r) {
        RISINGS.remove(r);
        r.bar.removeAllPlayers();
        int n = 0;
        for (Mob m : r.level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(r.site.origin()).inflate(200),
                m -> m.getTags().contains(RESTLESS))) {
            r.level.sendParticles(ParticleTypes.SOUL, m.getX(), m.getY() + 1, m.getZ(), 10, 0.3, 0.6, 0.3, 0.02);
            m.discard();
            n++;
        }
        CrawlSpace.LOGGER.info("CrawlSpace: the Restless Dead lie still ({} gone)", n);
    }

    /** A restless one falls: sometimes it carried something. */
    static void died(ServerLevel level, Mob mob) {
        if (!mob.getTags().contains(RESTLESS) || level.getRandom().nextFloat() > 0.3f) {
            return;
        }
        var loot = level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.LOOT_TABLE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(CrawlSpace.MODID, "chests/tier3")));
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, mob.position())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        List<ItemStack> items = loot.getRandomItems(params);
        if (!items.isEmpty()) {
            mob.spawnAtLocation(level, items.get(0));
        }
    }

    public static void clear() {
        for (Rising r : RISINGS) {
            r.bar.removeAllPlayers();
        }
        RISINGS.clear();
        WARNED.clear();
    }
}
