package com.sablednah.crawlspace.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Trigger;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;

/**
 * Seals for the deepest door (DCSS runes, Tomb of Annihilation; Sable's ruling,
 * 2026-10-12). Each level above the bottom with a lair gives a seal when its
 * true boss falls; a level with no lair but a puzzle gives one from the
 * puzzle's hoard; a level with neither asks nothing. The finale's altar takes
 * no offering until every seal is set on it, each hanging over it as it goes in.
 *
 * <p>It can never lock anyone out. A vault token from this dungeon stands in
 * for a seal. And after half an hour with someone on the bottom level, the
 * altar takes gold alone, and says so.</p>
 */
public final class Seals {

    private static final String TAG = "crawlspace_seal";
    /** Markers on the altar: a seal of level n set, at BASE + n; a stand-in, at STAND_IN + k; patience, at PATIENCE + k. */
    private static final int BASE = 900;
    private static final int STAND_IN = 940;
    private static final int PATIENCE = 980;
    private static final int PATIENCE_STEPS = 6;
    private static final long PATIENCE_STEP_TICKS = 5 * 60 * 20;
    /** Per altar: when the last patience step was taken, in game ticks. Memory only: a restart costs at most a step. */
    private static final Map<Long, Long> LAST_STEP = new HashMap<>();

    private Seals() {
    }

    /** The levels that give a seal: above the bottom, with a true lair, or else a puzzle. */
    static List<Integer> sources(Site site) {
        List<Integer> out = new ArrayList<>();
        int bottom = site.built().plan().levels().size() - 1;
        for (int li = 0; li < bottom; li++) {
            if (hasBoss(site, li) || puzzle(site, li) != null) {
                out.add(li);
            }
        }
        return out;
    }

    private static boolean hasBoss(Site site, int li) {
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() == Trigger.Kind.BOSS && t.level() == li) {
                return true;
            }
        }
        return false;
    }

    private static Trigger puzzle(Site site, int li) {
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.level() == li && (t.kind() == Trigger.Kind.PUZZLE || t.kind() == Trigger.Kind.ICE_BOARD || t.kind() == Trigger.Kind.TELEPORT_MAZE)) {
                return t;
            }
        }
        return null;
    }

    static ItemStack make(Site site, int li) {
        String theme = site.built().plan().levels().get(li).theme.name();
        ItemStack seal = new ItemStack(Items.ECHO_SHARD);
        ItemNbt.name(seal, Component.literal("Seal of the " + theme + " (level " + (li + 1) + ")")
                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.LIGHT_PURPLE)));
        ItemNbt.lore(seal, List.of(Component.literal("Set it on the altar at the bottom of this dungeon.")
                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY))));
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG, Keys.id(site, li));
        ItemNbt.data(seal, tag);
        // (no per-item rarity in 1.20.1: the name carries the colour)
        ItemNbt.glint(seal);
        return seal;
    }

    private static String sealId(ItemStack stack) {
        net.minecraft.nbt.CompoundTag data = ItemNbt.data(stack);
        return data == null ? null : data.getString(TAG).isEmpty() ? null : data.getString(TAG);
    }

    /** A true lair's boss falls above the bottom: its level's seal. */
    static void died(ServerLevel level, Mob mob) {
        if (!mob.getTags().contains(Bosses.TAG) || mob.getTags().contains(Decoys.TAG)) {
            return;
        }
        Site site = Dungeons.at(level, mob.blockPosition()).orElse(null);
        if (site == null) {
            return;
        }
        int li = Arrivals.levelAt(site, mob.blockPosition());
        if (li < 0 || li >= site.built().plan().levels().size() - 1 || !hasBoss(site, li)) {
            return;
        }
        mob.spawnAtLocation(make(site, li));
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(mob) < 32 * 32) {
                Triggers.tell(p, "It drops a seal. The altar at the bottom of this dungeon wants it.");
            }
        }
    }

    /** A puzzle's hoard opened, on a level with no lair: its seal, once. */
    static void hoard(ServerLevel level, ServerPlayer player, Site site, BlockPos pos) {
        int li = Arrivals.levelAt(site, pos);
        if (li < 0 || li >= site.built().plan().levels().size() - 1 || hasBoss(site, li) || puzzle(site, li) == null) {
            return;
        }
        CrawlState state = CrawlState.of(level);
        if (state.hasFired(pos.above(BASE))) {
            return;
        }
        state.fire(pos.above(BASE));
        ItemStack seal = make(site, li);
        if (!player.getInventory().add(seal)) {
            level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), seal));
        }
        Triggers.tell(player, "A seal lies with the hoard. The altar at the bottom of this dungeon wants it.");
    }

    /** How many of the altar's seals are set, stand-ins counted. */
    private static int set(CrawlState state, BlockPos altar, List<Integer> needed) {
        int n = 0;
        for (int li : needed) {
            n += state.hasFired(altar.above(BASE + li)) ? 1 : 0;
        }
        int k = 0;
        while (state.hasFired(altar.above(STAND_IN + k))) {
            k++;
        }
        return Math.min(needed.size(), n + k);
    }

    private static boolean patient(CrawlState state, BlockPos altar) {
        return state.hasFired(altar.above(PATIENCE + PATIENCE_STEPS - 1));
    }

    /** Whether the altar takes an offering yet: every seal set, or it has waited long enough. */
    static boolean open(ServerLevel level, Site site, BlockPos altar) {
        CrawlState state = CrawlState.of(level);
        List<Integer> needed = sources(site);
        return needed.isEmpty() || patient(state, altar) || set(state, altar, needed) >= needed.size();
    }

    /**
     * Using the altar while it is sealed: a seal or a token in hand goes on;
     * otherwise, what it still wants and where from. Returns true if the use
     * was about seals (so the offering is not tried).
     */
    static boolean use(ServerLevel level, ServerPlayer player, Site site, BlockPos altar) {
        if (open(level, site, altar)) {
            return false;
        }
        CrawlState state = CrawlState.of(level);
        List<Integer> needed = sources(site);
        ItemStack hand = player.getMainHandItem();
        String sid = sealId(hand);
        String tid = hand.is(Items.TRIPWIRE_HOOK) ? tokenId(hand) : null;
        String prefix = Keys.id(site, 0);
        prefix = prefix.substring(0, prefix.lastIndexOf('/') + 1);
        if (sid != null && sid.startsWith(prefix)) {
            int li = Keys.levelOf(sid);
            if (!needed.contains(li) || state.hasFired(altar.above(BASE + li))) {
                Triggers.tell(player, "That seal is already set.");
                return true;
            }
            hand.shrink(1);
            state.fire(altar.above(BASE + li));
            show(level, altar, set(state, altar, needed), make(site, li));
        } else if (tid != null && tid.startsWith(prefix)) {
            hand.shrink(1);
            int k = 0;
            while (state.hasFired(altar.above(STAND_IN + k))) {
                k++;
            }
            state.fire(altar.above(STAND_IN + k));
            show(level, altar, set(state, altar, needed), Tokens.make(site, Keys.levelOf(tid)));
        } else {
            int have = set(state, altar, needed);
            StringBuilder from = new StringBuilder();
            for (int li : needed) {
                if (!state.hasFired(altar.above(BASE + li))) {
                    from.append(from.isEmpty() ? "" : ", ").append(li + 1);
                }
            }
            Triggers.tell(player, "The altar is sealed: " + have + " of " + needed.size() + " seals. Still wanted from level"
                    + (from.indexOf(",") >= 0 ? "s " : " ") + from + ". (A vault token will stand in for one; and in time it will take gold alone.)");
            return true;
        }
        int have = set(state, altar, needed);
        Triggers.tell(player, have >= needed.size() ? "The last seal is set. The altar will take an offering of gold now."
                : "A seal is set: " + have + " of " + needed.size() + ".");
        return true;
    }

    private static String tokenId(ItemStack stack) {
        net.minecraft.nbt.CompoundTag data = ItemNbt.data(stack);
        String id = data == null ? "" : data.getString("crawlspace_token");
        return id.isEmpty() ? null : id;
    }

    /** A set seal hangs over the altar, turning slowly. */
    private static void show(ServerLevel level, BlockPos altar, int index, ItemStack what) {
        Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(level);
        if (d != null) {
            double a = index * 1.2;
            d.getSlot(0).set(what.copyWithCount(1));
            d.moveTo(altar.getX() + 0.5 + Math.cos(a) * 0.6, altar.getY() + 1.6, altar.getZ() + 0.5 + Math.sin(a) * 0.6, index * 40f, 0);
            d.addTag("crawlspace_seal_shown");
            level.addFreshEntity(d);
        }
        level.playSound(null, altar, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1f, 1f);
        level.sendParticles(ParticleTypes.END_ROD, altar.getX() + 0.5, altar.getY() + 1.5, altar.getZ() + 0.5, 20, 0.3, 0.4, 0.3, 0.02);
    }

    /** From Triggers' tick: someone on the bottom level of a sealed altar's dungeon, so its patience runs. */
    static void wait(ServerLevel level, ServerPlayer player, Site site) {
        int li = Arrivals.levelAt(site, player.blockPosition());
        Trigger a = li < 0 ? null : Altars.altarOf(site, li);
        if (a == null) {
            return;
        }
        BlockPos altar = site.origin().offset(a.x(), a.y(), a.z());
        CrawlState state = CrawlState.of(level);
        if (sources(site).isEmpty() || patient(state, altar)) {
            return;
        }
        long now = level.getGameTime();
        long last = LAST_STEP.computeIfAbsent(altar.asLong(), k -> now);
        if (now - last < PATIENCE_STEP_TICKS) {
            return;
        }
        LAST_STEP.put(altar.asLong(), now);
        int k = 0;
        while (state.hasFired(altar.above(PATIENCE + k))) {
            k++;
        }
        state.fire(altar.above(PATIENCE + k));
        if (k == PATIENCE_STEPS - 1) {
            for (ServerPlayer p : level.players()) {
                if (Arrivals.levelAt(site, p.blockPosition()) == li) {
                    Triggers.tell(p, "The altar has waited long enough. It will take an offering of gold alone now.");
                }
            }
            CrawlSpace.LOGGER.info("CrawlSpace: the altar at {} ran out of patience", altar);
        }
    }

    public static void clear() {
        LAST_STEP.clear();
    }
}
