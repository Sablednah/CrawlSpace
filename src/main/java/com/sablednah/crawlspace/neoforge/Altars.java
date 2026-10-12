package com.sablednah.crawlspace.neoforge;

import java.util.HashMap;
import java.util.HashSet;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The finale's altar (L_Ender's Cataclysm's deliberate summon, Dungeon Now
 * Loading's rematch; Sable's pick, 2026-10-11). The bottom lair's boss sleeps
 * until a gold ingot is offered here, so nobody blunders into the last fight.
 * Once beaten it can be called back with another offering, as "Recalled II",
 * then III: half again its health and a quarter again its bite each time, and
 * a hoard a tier richer when it falls. The count is kept in the world's
 * CrawlState, so it survives a restart.
 */
public final class Altars {

    private static final String RECALLED = "crawlspace_recalled_";
    /** Marker offset for the count of summons: altar.above(BASE + n) is fired once the n-th has been made. */
    private static final int BASE = 500;
    /** Per player: altars whose hint they have had this session. */
    private static final Map<UUID, Set<Long>> HINTED = new HashMap<>();

    private Altars() {
    }

    /** This level's altar, or null. */
    static Trigger altarOf(Site site, int li) {
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() == Trigger.Kind.ALTAR && t.level() == li) {
                return t;
            }
        }
        return null;
    }

    private static Trigger bossOf(Site site, int li) {
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() == Trigger.Kind.BOSS && t.level() == li) {
                return t;
            }
        }
        return null;
    }

    /** True for a boss that waits on its altar: nothing but an offering may wake it. */
    static boolean sleeps(Site site, Trigger t) {
        return t.kind() == Trigger.Kind.BOSS && altarOf(site, t.level()) != null;
    }

    private static int summons(CrawlState state, BlockPos altar) {
        int n = 0;
        while (state.hasFired(altar.above(BASE + n))) {
            n++;
        }
        return n;
    }

    private static String name(Site site, int li) {
        String n = Bestiary.bossName(site, li);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    private static Mob awake(ServerLevel level, BlockPos near) {
        for (Mob m : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(near).inflate(48),
                m -> m.isAlive() && m.entityTags().contains(Bosses.TAG))) {
            return m;
        }
        return null;
    }

    /** Once a session, near an altar: what it is for, so nobody has to guess. */
    static void tick(ServerLevel level, ServerPlayer player, Site site) {
        int li = Arrivals.levelAt(site, player.blockPosition());
        Trigger a = li < 0 ? null : altarOf(site, li);
        if (a == null) {
            return;
        }
        BlockPos at = site.origin().offset(a.x(), a.y(), a.z());
        if (!player.blockPosition().closerThan(at, 7) || !HINTED.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(at.asLong())) {
            return;
        }
        int n = summons(CrawlState.of(level), at);
        if (n == 0 && !Seals.open(level, site, at)) {
            Triggers.tell(player, "An altar stands at the head of the stairs, sealed. It wants " + Seals.sources(site).size()
                    + " seals from the levels above before it will take an offering.");
            return;
        }
        Triggers.tell(player, n == 0
                ? "An altar stands at the head of the stairs. Offer a gold ingot on it to wake " + Bestiary.bossName(site, li) + "."
                : "The altar can call " + Bestiary.bossName(site, li) + " back, stronger. Offer a gold ingot.");
    }

    /** Using a block: the altar, if it is one. Returns whether it was. */
    static boolean use(ServerLevel level, ServerPlayer player, Site site, BlockPos pos) {
        BlockPos o = site.origin();
        int code = site.built().blueprint().get(pos.getX() - o.getX(), pos.getY() - o.getY(), pos.getZ() - o.getZ());
        if (code == 0 || Blueprint.part(code) != Part.BOSS_ALTAR) {
            return false;
        }
        int li = Blueprint.level(code);
        Trigger boss = bossOf(site, li);
        if (boss == null) {
            return true;
        }
        BlockPos bossAt = o.offset(boss.x(), boss.y(), boss.z());
        String name = name(site, li);
        if (awake(level, bossAt) != null) {
            Triggers.tell(player, name + " is already awake.");
            return true;
        }
        // Sealed until every seal is set (or it has waited long enough): a seal or token in hand goes on first.
        if (Seals.use(level, player, site, pos)) {
            return true;
        }
        InteractionHand hand = player.getMainHandItem().is(Items.GOLD_INGOT) ? InteractionHand.MAIN_HAND
                : player.getOffhandItem().is(Items.GOLD_INGOT) ? InteractionHand.OFF_HAND : null;
        CrawlState state = CrawlState.of(level);
        int n = summons(state, pos);
        if (hand == null) {
            Triggers.tell(player, n == 0 ? "The altar wants an offering: hold a gold ingot and use it to wake " + Bestiary.bossName(site, li) + "."
                    : "Hold a gold ingot and use the altar to call " + Bestiary.bossName(site, li) + " back, as Recalled " + Powers.roman(n + 1) + ".");
            return true;
        }
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            Triggers.tell(player, "Nothing answers. (The world is on Peaceful.)");
            return true;
        }
        if (!player.isCreative()) {
            player.getItemInHand(hand).shrink(1);
        }
        state.fire(pos.above(BASE + n));
        state.fire(bossAt);
        Bestiary.wake(level, site, boss);
        Mob mob = awake(level, bossAt);
        if (mob != null && n > 0) {
            recall(mob, n);
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.3, 0.5, 0.3, 0.03);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.playSound(null, bossAt, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 1.5f, 0.8f);
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().closerThan(bossAt, 40)) {
                Triggers.tell(p, n == 0 ? "The offering is taken. " + name + " wakes!"
                        : name + " answers the altar again: Recalled " + Powers.roman(n + 1) + "!");
            }
        }
        CrawlSpace.LOGGER.info("CrawlSpace: altar at {} woke its boss (summons {})", pos, n + 1);
        return true;
    }

    /**
     * A recalled boss: stronger by half its health and a quarter its bite each
     * time, and named for it. A multiplier on the total, not the base: most of a
     * boss's health is a modifier on top of its base, and scaling the base took
     * a 124-health boss to only 134 on the rig.
     */
    private static void recall(Mob mob, int n) {
        scale(mob.getAttribute(Attributes.MAX_HEALTH), "recalled_health", 0.5 * n);
        scale(mob.getAttribute(Attributes.ATTACK_DAMAGE), "recalled_damage", 0.25 * n);
        mob.setHealth(mob.getMaxHealth());
        Component was = mob.getCustomName() != null ? mob.getCustomName() : mob.getName();
        mob.setCustomName(Component.literal(was.getString() + ", Recalled " + Powers.roman(n + 1)).withStyle(ChatFormatting.DARK_RED));
        mob.addTag(RECALLED + n);
        Bosses.rename(mob);
    }

    private static void scale(AttributeInstance a, String id, double by) {
        if (a != null) {
            a.addOrReplacePermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(CrawlSpace.MODID, id), by,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** A recalled boss falls: a hoard a tier richer each time, where it fell. Any altar boss: say the altar can call it back. */
    static void died(ServerLevel level, Mob mob) {
        if (!mob.entityTags().contains(Bosses.TAG)) {
            return;
        }
        Site site = Dungeons.at(level, mob.blockPosition()).orElse(null);
        if (site == null) {
            return;
        }
        // Found by its altar, not by Arrivals.levelAt: the arena's pit is below the band that counts as the level,
        // and a boss dying on its floor was taken for one on no level at all (the first rig run dropped nothing).
        int li = -1;
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() == Trigger.Kind.ALTAR && site.origin().offset(t.x(), t.y(), t.z()).closerThan(mob.blockPosition(), 48)) {
                li = t.level();
            }
        }
        if (li < 0) {
            return;
        }
        int n = 0;
        for (String t : mob.entityTags()) {
            if (t.startsWith(RECALLED)) {
                n = Integer.parseInt(t.substring(RECALLED.length()));
            }
        }
        if (n > 0) {
            var loot = level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.LOOT_TABLE,
                    net.minecraft.resources.Identifier.fromNamespaceAndPath(CrawlSpace.MODID, "chests/tier" + Math.min(5, 3 + n))));
            var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                    .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, mob.position())
                    .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
            for (ItemStack stack : loot.getRandomItems(params)) {
                mob.spawnAtLocation(level, stack);
            }
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, mob.getX(), mob.getY() + 1, mob.getZ(), 40, 0.6, 0.8, 0.6, 0.3);
        }
        String name = name(site, li);
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().closerThan(mob.blockPosition(), 40)) {
                Triggers.tell(p, name + " falls. The altar can call it back, stronger, for another gold ingot.");
            }
        }
    }

    public static void clear() {
        HINTED.clear();
    }
}
