package com.sablednah.crawlspace.neoforge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sablednah.crawlspace.CrawlSpace;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * What makes a dungeon monster more than a vanilla one: an elite's affixes,
 * named in its nameplate, and a boss's powers and its enrage at half health.
 * The pattern is ZombieMod's (abilities that run beside a mob's own AI, kept
 * on the mob and rebuilt when it rejoins the level), not its code: CrawlSpace
 * works without it.
 *
 * <p>Everything a mob has is in its tags, so a restart or a chunk reload loses
 * nothing: {@link #onJoin} picks it back up. The running state (cooldowns) is
 * memory only, and starts fresh.</p>
 */
public final class Powers {

    /** An elite's affix: its prefix, and what it does. */
    public enum Affix {
        VENOMOUS("Venomous", ChatFormatting.DARK_GREEN),
        FRENZIED("Frenzied", ChatFormatting.RED),
        ARMOURED("Armoured", ChatFormatting.GRAY),
        HULKING("Hulking", ChatFormatting.GOLD),
        BLINKING("Blinking", ChatFormatting.DARK_PURPLE),
        BURNING("Burning", ChatFormatting.GOLD),
        SPLITTING("Splitting", ChatFormatting.AQUA),
        VAMPIRIC("Vampiric", ChatFormatting.DARK_RED);

        final String prefix;
        final ChatFormatting colour;

        Affix(String prefix, ChatFormatting colour) {
            this.prefix = prefix;
            this.colour = colour;
        }

        String tag() {
            return AFFIX + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    static final String AFFIX = "crawlspace_affix_";
    static final String ELITE = "crawlspace_elite";
    /** Its dungeon depth, for its loot: "crawlspace_depth_3". */
    static final String DEPTH = "crawlspace_depth_";
    /** A boss's powers: "crawlspace_power_blink", "crawlspace_power_summon". */
    static final String POWER = "crawlspace_power_";
    /** What a summoner calls up: "crawlspace_minion_minecraft:silverfish". */
    static final String MINION = "crawlspace_minion_";
    static final String ENRAGED = "crawlspace_enraged";
    /**
     * Left alone by mods that roll their own variant on to a mob as it spawns:
     * ZombieMod's generic opt-out tag, which it asked us to use (any mod may).
     * Bosses, their packs and summoned minions carry it; they also skip the
     * spawn event entirely, by calling {@code finalizeSpawn} directly.
     */
    public static final String NOROLL = "zombiemod.noroll";
    /** Made small or large by us: sonic booms are scaled by size. */
    static final String SCALED = "crawlspace_scaled";
    /** A summoned minion: it carries no loot and its summoner counts it. */
    static final String SPAWN = "crawlspace_spawn";

    private static final class State {
        final ServerLevel level;
        final UUID id;
        long nextBlink;
        long nextSummon;
        final List<UUID> minions = new ArrayList<>();

        State(ServerLevel level, UUID id) {
            this.level = level;
            this.id = id;
        }
    }

    private static final Map<UUID, State> TRACKED = new HashMap<>();

    private Powers() {
    }

    // ---- making them ----

    /** How likely a room's monster is to be an elite at this depth, and to carry a second affix. */
    static double eliteChance(int depth) {
        return Math.min(0.3, 0.05 + 0.035 * depth);
    }

    /**
     * Makes {@code mob} an elite with one affix, or two from the fourth level
     * down: attributes now, name and tags for what runs later. A mob some other
     * mod already named (a ZombieMod genus) is left as it is.
     */
    static boolean elite(Mob mob, int depth, RandomSource random) {
        if (mob.hasCustomName()) {
            return false;
        }
        List<Affix> pool = new ArrayList<>(List.of(Affix.values()));
        List<Affix> got = new ArrayList<>();
        got.add(pool.remove(random.nextInt(pool.size())));
        if (depth >= 3 && random.nextDouble() < 0.35) {
            got.add(pool.remove(random.nextInt(pool.size())));
        }
        StringBuilder name = new StringBuilder();
        for (Affix a : got) {
            mob.addTag(a.tag());
            name.append(a.prefix).append(' ');
            switch (a) {
                case FRENZIED -> modify(mob, Attributes.MOVEMENT_SPEED, "elite_speed", 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                case ARMOURED -> {
                    modify(mob, Attributes.ARMOR, "elite_armour", 8, AttributeModifier.Operation.ADD_VALUE);
                    modify(mob, Attributes.KNOCKBACK_RESISTANCE, "elite_knockback", 0.5, AttributeModifier.Operation.ADD_VALUE);
                }
                case HULKING -> {
                    modify(mob, Attributes.SCALE, "elite_scale", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                    modify(mob, Attributes.MAX_HEALTH, "elite_health", 0.75, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                    modify(mob, Attributes.ATTACK_DAMAGE, "elite_damage", 2, AttributeModifier.Operation.ADD_VALUE);
                }
                case BURNING -> mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, -1, 0, false, false));
                default -> {
                }
            }
        }
        modify(mob, Attributes.MAX_HEALTH, "elite_toughness", 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mob.setHealth(mob.getMaxHealth());
        name.append(mob.getType().getDescription().getString());
        mob.setCustomName(Component.literal(name.toString()).withStyle(got.size() > 1 ? ChatFormatting.LIGHT_PURPLE : got.get(0).colour));
        mob.setCustomNameVisible(true);
        mob.addTag(ELITE);
        mob.addTag(DEPTH + depth);
        return true;
    }

    /** Starts tracking a mob that has something to run: a blinker, a summoner, any boss (for its enrage). */
    static void track(ServerLevel level, Mob mob) {
        if (needsTicking(mob)) {
            TRACKED.putIfAbsent(mob.getUUID(), new State(level, mob.getUUID()));
        }
    }

    private static boolean needsTicking(Mob mob) {
        for (String t : mob.entityTags()) {
            if (t.startsWith(POWER) || t.equals(Affix.BLINKING.tag()) || t.equals(Bosses.TAG)) {
                return true;
            }
        }
        return false;
    }

    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel() instanceof ServerLevel level && e.getEntity() instanceof Mob mob && mob.entityTags().contains(Bestiary.KIN)) {
            track(level, mob);
        }
    }

    // ---- running them ----

    public static void tick(long gameTime) {
        if (gameTime % 5 != 0 || TRACKED.isEmpty()) {
            return;
        }
        Iterator<State> it = TRACKED.values().iterator();
        while (it.hasNext()) {
            State s = it.next();
            Entity e = s.level.getEntity(s.id);
            if (!(e instanceof Mob mob) || !mob.isAlive()) {
                it.remove();
                continue;
            }
            long now = s.level.getGameTime();
            LivingEntity target = mob.getTarget();
            java.util.Set<String> tags = mob.entityTags();
            if ((tags.contains(Affix.BLINKING.tag()) || tags.contains(POWER + "blink")) && target != null && now >= s.nextBlink
                    && mob.distanceToSqr(target) > 16) {
                blink(s.level, mob, target);
                s.nextBlink = now + 100 + mob.getRandom().nextInt(80);
            }
            if (tags.contains(POWER + "summon") && target != null && now >= s.nextSummon) {
                summon(s.level, mob, s, 2 + mob.getRandom().nextInt(2));
                s.nextSummon = now + (tags.contains(ENRAGED) ? 100 : 160);
            }
            if (tags.contains(Bosses.TAG) && !tags.contains(ENRAGED) && mob.getHealth() <= mob.getMaxHealth() / 2) {
                enrage(s.level, mob, s);
            }
            if (mob.getType() == EntityTypes.WARDEN) {
                // A warden calm for long enough digs back into the ground and is gone; ours live here.
                mob.getBrain().setMemoryWithExpiry(net.minecraft.world.entity.ai.memory.MemoryModuleType.DIG_COOLDOWN,
                        net.minecraft.util.Unit.INSTANCE, 1200L);
            }
        }
    }

    /** To just behind its target, in a puff of portal: the way an enderman goes. */
    private static void blink(ServerLevel level, Mob mob, LivingEntity target) {
        net.minecraft.world.phys.Vec3 back = target.getLookAngle().multiply(-1, 0, -1).normalize();
        for (int k = 0; k < 8; k++) {
            double x = target.getX() + back.x * (1.5 + k * 0.25) + (mob.getRandom().nextDouble() - 0.5);
            double z = target.getZ() + back.z * (1.5 + k * 0.25) + (mob.getRandom().nextDouble() - 0.5);
            double y = target.getY();
            if (level.noCollision(mob, mob.getBoundingBox().move(x - mob.getX(), y - mob.getY(), z - mob.getZ()))) {
                level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + 1, mob.getZ(), 20, 0.3, 0.6, 0.3, 0.3);
                mob.teleportTo(x, y, z);
                level.playSound(null, mob.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.8f, 1.2f);
                return;
            }
        }
    }

    /** A summoner calls up its minions round it, never more than eight alive at once. */
    private static void summon(ServerLevel level, Mob mob, State s, int count) {
        s.minions.removeIf(id -> !(level.getEntity(id) instanceof LivingEntity m) || !m.isAlive());
        EntityType<?> type = minionOf(mob);
        if (type == null) {
            return;
        }
        for (int k = 0; k < count && s.minions.size() < 8; k++) {
            Entity e = type.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (!(e instanceof Mob minion)) {
                return;
            }
            double a = mob.getRandom().nextDouble() * Math.PI * 2;
            double r = mob.getBbWidth() / 2 + 1;
            minion.snapTo(mob.getX() + Math.cos(a) * r, mob.getY(), mob.getZ() + Math.sin(a) * r, mob.getRandom().nextFloat() * 360, 0);
            minion.addTag(NOROLL);
            minion.finalizeSpawn(level, level.getCurrentDifficultyAt(minion.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
            minion.addTag(Bestiary.KIN);
            minion.addTag(SPAWN);
            if (mob.getTarget() != null) {
                minion.setTarget(mob.getTarget());
            }
            level.addFreshEntity(minion);
            s.minions.add(minion.getUUID());
            level.sendParticles(ParticleTypes.POOF, minion.getX(), minion.getY() + 0.3, minion.getZ(), 6, 0.2, 0.2, 0.2, 0.02);
        }
        level.playSound(null, mob.blockPosition(), SoundEvents.SILVERFISH_AMBIENT, SoundSource.HOSTILE, 1.2f, 0.5f);
    }

    private static EntityType<?> minionOf(Mob mob) {
        for (String t : mob.entityTags()) {
            if (t.startsWith(MINION)) {
                return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getOptional(Identifier.tryParse(t.substring(MINION.length()))).orElse(null);
            }
        }
        return null;
    }

    /** At half health a boss turns faster and harder, says so, and a summoner calls a burst at once. */
    private static void enrage(ServerLevel level, Mob mob, State s) {
        mob.addTag(ENRAGED);
        modify(mob, Attributes.MOVEMENT_SPEED, "enraged_speed", 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(mob, Attributes.ATTACK_DAMAGE, "enraged_damage", 2, AttributeModifier.Operation.ADD_VALUE);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight(), mob.getZ(), 8, 0.6, 0.3, 0.6, 0);
        level.playSound(null, mob.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5f, 0.8f);
        if (mob.entityTags().contains(POWER + "summon")) {
            summon(level, mob, s, 4);
        }
        Component name = mob.getDisplayName();
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(mob) < 40 * 40) {
                p.sendOverlayMessage(Component.empty().append(name).append(Component.literal(" is enraged!"))
                        .withStyle(ChatFormatting.RED));
            }
        }
    }

    // ---- what they do when they hit, are hit, and die ----

    /** Venom, fire and thirst on a hit; and a small warden's sonic boom made small with it. */
    public static void onHurt(LivingIncomingDamageEvent e) {
        if (!(e.getSource().getEntity() instanceof Mob attacker) || !(attacker.level() instanceof ServerLevel)) {
            return;
        }
        java.util.Set<String> tags = attacker.entityTags();
        if (tags.contains(SCALED) && e.getSource().is(DamageTypes.SONIC_BOOM)) {
            // Its sonic boom does the same whatever its size: a Wardling would hit as hard as a warden.
            double scale = attacker.getAttributeValue(Attributes.SCALE);
            e.setAmount((float) (e.getAmount() * Math.min(1, scale * scale)));
        }
        if (e.getSource().getDirectEntity() != attacker) {
            return; // the rest are melee: not an arrow, not a boom from across the room
        }
        LivingEntity victim = e.getEntity();
        if (tags.contains(Affix.VENOMOUS.tag())) {
            victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0), attacker);
        }
        if (tags.contains(Affix.BURNING.tag())) {
            victim.igniteForSeconds(4);
        }
        if (tags.contains(Affix.VAMPIRIC.tag())) {
            attacker.heal(e.getAmount() * 0.5f);
            ((ServerLevel) attacker.level()).sendParticles(ParticleTypes.DAMAGE_INDICATOR, attacker.getX(), attacker.getY() + 1,
                    attacker.getZ(), 4, 0.3, 0.3, 0.3, 0);
        }
    }

    /** A splitting elite bursts into two or three smaller copies of itself; elites and bosses give extra experience. */
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        java.util.Set<String> tags = mob.entityTags();
        if (tags.contains(Affix.SPLITTING.tag())) {
            int n = 2 + mob.getRandom().nextInt(2);
            for (int k = 0; k < n; k++) {
                Entity c = mob.getType().create(level, EntitySpawnReason.MOB_SUMMONED);
                if (!(c instanceof Mob copy)) {
                    break;
                }
                copy.snapTo(mob.getX() + (mob.getRandom().nextDouble() - 0.5), mob.getY(), mob.getZ() + (mob.getRandom().nextDouble() - 0.5),
                        mob.getRandom().nextFloat() * 360, 0);
                copy.addTag(NOROLL);
                copy.finalizeSpawn(level, level.getCurrentDifficultyAt(copy.blockPosition()), EntitySpawnReason.MOB_SUMMONED, null);
                modify(copy, Attributes.SCALE, "split_scale", -0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                modify(copy, Attributes.MAX_HEALTH, "split_health", -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                copy.setHealth(copy.getMaxHealth());
                copy.addTag(Bestiary.KIN);
                copy.addTag(SPAWN);
                if (mob.getTarget() != null) {
                    copy.setTarget(mob.getTarget());
                }
                level.addFreshEntity(copy);
            }
            level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 12, 0.4, 0.4, 0.4, 0.05);
        }
        int xp = tags.contains(Bosses.TAG) ? 60 : tags.contains(ELITE) ? 12 : 0;
        if (xp > 0) {
            net.minecraft.world.entity.ExperienceOrb.award(level, mob.position(), xp);
        }
    }

    /** An elite drops something from the loot of its depth, as a boss does from its hoard. */
    public static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || !mob.entityTags().contains(ELITE)) {
            return;
        }
        int depth = 0;
        for (String t : mob.entityTags()) {
            if (t.startsWith(DEPTH)) {
                try {
                    depth = Integer.parseInt(t.substring(DEPTH.length()));
                } catch (NumberFormatException ignored) {
                    // not ours
                }
            }
        }
        int tier = Math.min(5, 1 + depth / 2);
        var table = level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(CrawlSpace.MODID, "chests/tier" + tier)));
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, mob.position())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        List<net.minecraft.world.item.ItemStack> items = new ArrayList<>(table.getRandomItems(params));
        java.util.Collections.shuffle(items, new java.util.Random(mob.getRandom().nextLong()));
        BlockPos at = mob.blockPosition();
        for (int k = 0; k < Math.min(2, items.size()); k++) {
            e.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, items.get(k)));
        }
    }

    /**
     * A dungeon's own monsters never change blocks: a silverfish merges into
     * stone and leaves infested stone in its place, which is how a five-times
     * Brood Queen would vanish into a wall, and every merge would swap a
     * protected wall block for one protection does not know. Creepers still
     * explode; that is the explosion filter's to handle.
     */
    public static void onGrief(net.neoforged.neoforge.event.entity.EntityMobGriefingEvent e) {
        if (e.getEntity().entityTags().contains(Bestiary.KIN) && !(e.getEntity() instanceof net.minecraft.world.entity.monster.Creeper)) {
            e.setCanGrief(false);
        }
    }

    public static void clear() {
        TRACKED.clear();
    }

    static void modify(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, String id, double amount,
            AttributeModifier.Operation op) {
        AttributeInstance inst = mob.getAttribute(attr);
        Identifier key = Identifier.fromNamespaceAndPath(CrawlSpace.MODID, id);
        if (inst != null && amount != 0 && !inst.hasModifier(key)) {
            inst.addPermanentModifier(new AttributeModifier(key, amount, op));
        }
    }
}
