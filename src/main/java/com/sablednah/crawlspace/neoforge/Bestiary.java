package com.sablednah.crawlspace.neoforge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Trigger;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Which monsters live in which theme, how they are armed for their depth, and
 * each theme's boss. Vanilla mobs only, so a vanilla client sees them all;
 * bosses are made big with vanilla's own scale attribute.
 */
public final class Bestiary {

    /** @param armour whether it wears armour by depth; only mobs that show armour should */
    private record Pick(EntityType<?> type, int weight, boolean armour) {
        Pick(EntityType<?> type, int weight) {
            this(type, weight, HUMANOIDS.contains(type));
        }
    }

    /**
     * A theme's boss: what it is, what it is called, what it carries, its bar's
     * colour and its size. The rest is for the stranger ones:
     *
     * @param health    its maximum health outright, or 0 for its own plus the boss's bonus by depth
     * @param damage    its attack damage outright, or 0 for its own plus the bonus
     * @param speed     a multiplier on its speed (a five-times silverfish is slow)
     * @param powers    "summon", "blink"
     * @param minion    what "summon" calls up, or null
     * @param follower  what stands with it in its lair instead of the theme's monsters, or null
     * @param followerScale  ...how big they are
     * @param followerHealth ...and their health and damage outright, or 0 for their own
     */
    private record BossSpec(EntityType<?> type, String name, Item weapon, BossEvent.BossBarColor colour, double scale,
            double health, double damage, double speed, java.util.Set<String> powers, EntityType<?> minion,
            EntityType<?> follower, double followerScale, double followerHealth, double followerDamage) {
        BossSpec(EntityType<?> type, String name, Item weapon, BossEvent.BossBarColor colour, double scale) {
            this(type, name, weapon, colour, scale, 0, 0, 1, java.util.Set.of(), null, null, 1, 0, 0);
        }
    }

    private static final java.util.Set<EntityType<?>> HUMANOIDS = java.util.Set.of(EntityType.ZOMBIE, EntityType.SKELETON,
            EntityType.DROWNED, EntityType.STRAY, EntityType.BOGGED, EntityType.WITHER_SKELETON, EntityType.HUSK);

    private static final Map<String, List<Pick>> COMMON = new HashMap<>();
    /** Each theme's bosses: a lair gets one of them, from its own dice. */
    private static final Map<String, List<BossSpec>> BOSSES = new HashMap<>();
    /** The built-in tables with any datapack's on top; swapped whole on reload. */
    private static volatile Map<String, List<Pick>> common = Map.of();
    private static volatile Map<String, List<BossSpec>> bosses = Map.of();

    static {
        COMMON.put("Crypt", List.of(new Pick(EntityType.ZOMBIE, 4), new Pick(EntityType.SKELETON, 4), new Pick(EntityType.SPIDER, 1)));
        COMMON.put("Sunken Halls", List.of(new Pick(EntityType.DROWNED, 4), new Pick(EntityType.ZOMBIE, 2),
                new Pick(EntityType.SKELETON, 2), new Pick(EntityType.BOGGED, 2)));
        COMMON.put("Old Mines", List.of(new Pick(EntityType.ZOMBIE, 3), new Pick(EntityType.SKELETON, 2),
                new Pick(EntityType.CAVE_SPIDER, 3), new Pick(EntityType.SPIDER, 2), new Pick(EntityType.CREEPER, 1)));
        COMMON.put("Caverns", List.of(new Pick(EntityType.SPIDER, 3), new Pick(EntityType.CAVE_SPIDER, 3),
                new Pick(EntityType.BOGGED, 2), new Pick(EntityType.CREEPER, 1), new Pick(EntityType.WITCH, 1)));
        COMMON.put("Deep Halls", List.of(new Pick(EntityType.VINDICATOR, 3), new Pick(EntityType.PILLAGER, 2),
                new Pick(EntityType.STRAY, 2), new Pick(EntityType.WITHER_SKELETON, 2), new Pick(EntityType.WITCH, 1)));

        BOSSES.put("Crypt", List.of(new BossSpec(EntityType.SKELETON, "the Bone Warden", Items.BOW, BossEvent.BossBarColor.WHITE, 1.3)));
        BOSSES.put("Sunken Halls", List.of(new BossSpec(EntityType.DROWNED, "the Drowned Reeve", Items.TRIDENT, BossEvent.BossBarColor.BLUE, 1.35)));
        BOSSES.put("Old Mines", List.of(
                new BossSpec(EntityType.ZOMBIE, "the Foreman", Items.DIAMOND_PICKAXE, BossEvent.BossBarColor.YELLOW, 1.35),
                // A giant endermite that blinks to you and sheds lesser mites (Sable's "weird bosses via the scale").
                new BossSpec(EntityType.ENDERMITE, "the Gnawing Mite", Items.AIR, BossEvent.BossBarColor.PURPLE, 4.0,
                        0, 6, 0.8, java.util.Set.of("blink", "summon"), EntityType.ENDERMITE, null, 1, 0, 0)));
        BOSSES.put("Caverns", List.of(
                new BossSpec(EntityType.SPIDER, "the Broodmother", Items.AIR, BossEvent.BossBarColor.GREEN, 1.8),
                // Jabba the Hutt as a silverfish: vast, slow, too wide for a door, and birthing her brood.
                new BossSpec(EntityType.SILVERFISH, "the Brood Queen", Items.AIR, BossEvent.BossBarColor.WHITE, 5.0,
                        0, 5, 0.45, java.util.Set.of("summon"), EntityType.SILVERFISH, null, 1, 0, 0)));
        BOSSES.put("Deep Halls", List.of(
                new BossSpec(EntityType.VINDICATOR, "the Gaoler", Items.DIAMOND_AXE, BossEvent.BossBarColor.PURPLE, 1.3),
                // Wardens shrunk to Wardlings: blind, hunting by sound, and a pack of them. Their boom is scaled down by size.
                // Measured on the rig: at 10 and 5 a matriarch and three Wardlings took 200 health in 20 s.
                new BossSpec(EntityType.WARDEN, "the Wardling Matriarch", Items.AIR, BossEvent.BossBarColor.BLUE, 0.6,
                        110, 6, 1, java.util.Set.of(), null, EntityType.WARDEN, 0.4, 20, 3)));
        common = Map.copyOf(COMMON);
        bosses = Map.copyOf(BOSSES);
    }

    /**
     * A theme file's "mobs" replace that theme's list outright; its "boss"
     * changes only the fields it names. Anything unknown is skipped with a
     * warning naming it.
     */
    static void apply(Map<String, com.google.gson.JsonObject> themes) {
        Map<String, List<Pick>> c = new HashMap<>(COMMON);
        Map<String, List<BossSpec>> b = new HashMap<>(BOSSES);
        themes.forEach((theme, json) -> {
            if (json.has("mobs")) {
                List<Pick> picks = new java.util.ArrayList<>();
                for (com.google.gson.JsonElement el : json.getAsJsonArray("mobs")) {
                    com.google.gson.JsonObject o = el.isJsonObject() ? el.getAsJsonObject() : null;
                    String id = o != null ? o.get("type").getAsString() : el.getAsString();
                    EntityType<?> type = entity(id, theme);
                    if (type != null) {
                        int weight = o != null && o.has("weight") ? Math.max(1, o.get("weight").getAsInt()) : 1;
                        boolean armour = o != null && o.has("armour") ? o.get("armour").getAsBoolean() : HUMANOIDS.contains(type);
                        picks.add(new Pick(type, weight, armour));
                    }
                }
                if (!picks.isEmpty()) {
                    c.put(theme, List.copyOf(picks));
                }
            }
            if (json.has("boss")) {
                com.google.gson.JsonObject o = json.getAsJsonObject("boss");
                BossSpec old = b.getOrDefault(theme, BOSSES.get("Crypt")).get(0);
                EntityType<?> type = o.has("type") ? entity(o.get("type").getAsString(), theme) : null;
                Item weapon = old.weapon();
                if (o.has("weapon")) {
                    weapon = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getOptional(Identifier.tryParse(o.get("weapon").getAsString())).orElse(weapon);
                }
                BossEvent.BossBarColor bar = old.colour();
                if (o.has("bar")) {
                    for (BossEvent.BossBarColor col : BossEvent.BossBarColor.values()) {
                        if (col.getName().equals(o.get("bar").getAsString())) {
                            bar = col;
                        }
                    }
                }
                // A datapack's boss is that theme's only boss, the first built-in one changed where it says.
                b.put(theme, List.of(new BossSpec(type != null ? type : old.type(),
                        o.has("name") ? o.get("name").getAsString() : old.name(), weapon, bar,
                        o.has("scale") ? o.get("scale").getAsDouble() : old.scale(), old.health(), old.damage(), old.speed(),
                        old.powers(), old.minion(), old.follower(), old.followerScale(), old.followerHealth(), old.followerDamage())));
            }
        });
        common = Map.copyOf(c);
        bosses = Map.copyOf(b);
    }

    private static EntityType<?> entity(String id, String theme) {
        EntityType<?> type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.tryParse(id)).orElse(null);
        if (type == null) {
            ThemeData.LOG.warn("CrawlSpace theme '{}': no entity called '{}'", theme, id);
        }
        return type;
    }

    /** A theme's monsters and boss as a datapack would write them, for /crawlspace export. */
    static void export(String theme, com.google.gson.JsonObject into) {
        com.google.gson.JsonArray mobs = new com.google.gson.JsonArray();
        for (Pick p : common.getOrDefault(theme, List.of())) {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("type", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(p.type()).toString());
            o.addProperty("weight", p.weight());
            o.addProperty("armour", p.armour());
            mobs.add(o);
        }
        into.add("mobs", mobs);
        BossSpec spec = bosses.containsKey(theme) ? bosses.get(theme).get(0) : null;
        if (spec != null) {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("type", net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(spec.type()).toString());
            o.addProperty("name", spec.name());
            o.addProperty("weapon", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(spec.weapon()).toString());
            o.addProperty("bar", spec.colour().getName());
            o.addProperty("scale", spec.scale());
            into.add("boss", o);
        }
    }

    /** The most of a strange boss's own pack that wake with it. */
    private static final int MAX_PACK = 3;

    /** Every monster a room wakes carries this tag. */
    static final String KIN = "crawlspace_mob";

    private Bestiary() {
    }

    /**
     * A dungeon's monsters do not fight each other. A skeleton boss's stray
     * arrows hit the zombies around it, they turned on it, and the Bone Warden
     * was dead seconds after it woke, before anyone reached the lair. Damage
     * between two of them is cancelled, so is turning on one another.
     */
    public static void onHurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e) {
        if (e.getEntity().entityTags().contains(KIN) && e.getSource().getEntity() instanceof Mob attacker
                && attacker.entityTags().contains(KIN)) {
            e.setCanceled(true);
        }
    }

    public static void onTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent e) {
        if (e.getEntity().entityTags().contains(KIN) && e.getNewAboutToBeSetTarget() != null
                && e.getNewAboutToBeSetTarget().entityTags().contains(KIN)) {
            e.setCanceled(true);
        }
    }

    static EntityType<?> common(String theme, RandomSource random) {
        List<Pick> picks = common.getOrDefault(theme, common.get("Crypt"));
        int total = picks.stream().mapToInt(Pick::weight).sum();
        int r = random.nextInt(total);
        for (Pick p : picks) {
            r -= p.weight();
            if (r < 0) {
                return p.type();
            }
        }
        return picks.get(0).type();
    }

    /**
     * Wakes a room: its monsters appear where the blueprint put them. Returns
     * the message for whoever woke it, or null if nothing could be spawned.
     */
    static String wake(ServerLevel level, Site site, Trigger t) {
        String theme = site.built().plan().levels().get(t.level()).theme.name();
        int depth = t.level();
        RandomSource random = level.getRandom();
        BlockPos o = site.origin();
        int spawned = 0;
        int elites = 0;
        String bossName = null;
        // Which of the theme's bosses: from the dungeon's own dice, so a lair always holds the same one.
        List<BossSpec> choices = bosses.getOrDefault(theme, bosses.get("Crypt"));
        BossSpec spec = choices.get(com.sablednah.crawlspace.plan.Dice.of(site.seed(), t.level(), 0xB055L).nextInt(choices.size()));
        boolean lair = t.kind() == Trigger.Kind.BOSS;
        for (int k = 0; k < t.targets().length; k++) {
            int[] s = t.targets()[k];
            BlockPos pos = o.offset(s[0], s[1], s[2]);
            boolean boss = lair && k == 0;
            boolean follower = lair && k > 0 && spec.follower() != null;
            if (follower && k > MAX_PACK) {
                continue; // a strange boss's own pack stays small: three Wardlings are a fight, six are a wipe
            }
            EntityType<?> type = boss ? spec.type() : follower ? spec.follower() : common(theme, random);
            Entity e = type.create(level, EntitySpawnReason.STRUCTURE);
            if (!(e instanceof Mob mob)) {
                continue;
            }
            mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
            if (boss || follower) {
                // Ours alone: the direct call fires no spawn event, and the tag tells a mod that hooks it anyway.
                mob.addTag(Powers.NOROLL);
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
            } else {
                // A room's ordinary monsters go through NeoForge's spawn event, so another mod may make them its
                // own (ZombieMod rolls a zombie type). One that comes back named is not made an elite on top.
                net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(pos),
                        EntitySpawnReason.STRUCTURE, null);
            }
            arm(mob, depth + (boss ? 2 : 0), random);
            if (boss) {
                crown(mob, spec, depth);
                Bosses.track(level, mob, spec.colour());
                bossName = spec.name();
            } else if (follower) {
                follow(mob, spec);
            } else if (elites < 2 && random.nextDouble() < Powers.eliteChance(depth) && Powers.elite(mob, depth, random)) {
                elites++;
            }
            mob.addTag(KIN);
            mob.setPersistenceRequired();
            level.addFreshEntityWithPassengers(mob);
            Powers.track(level, mob);
            spawned++;
        }
        if (spawned == 0) {
            return null;
        }
        if (lair && spec.type() == EntityType.WARDEN) {
            // Wardens are blind and find you by sound; a pack woken by someone hunts them from the start.
            net.minecraft.world.entity.player.Player near = level.getNearestPlayer(o.getX() + t.x(), o.getY() + t.y(), o.getZ() + t.z(), 24, false);
            if (near != null) {
                for (net.minecraft.world.entity.monster.warden.Warden w : level.getEntitiesOfClass(net.minecraft.world.entity.monster.warden.Warden.class,
                        near.getBoundingBox().inflate(32), w -> w.entityTags().contains(KIN))) {
                    w.increaseAngerAt(near, 80, false);
                }
            }
        }
        return bossName != null ? capitalise(bossName) + " rises from its lair!" : "Something stirs in the dark.";
    }

    /**
     * For testing and for showing off: a theme's boss (its {@code index}th, from
     * 0) and its pack at a spot, or, with a null theme, one elite. As a lair or
     * room would make them, at {@code depth}. Returns what was made, for the message.
     */
    static String summon(ServerLevel level, BlockPos pos, String theme, int index, int depth) {
        RandomSource random = level.getRandom();
        if (theme == null) {
            Entity e = common("Crypt", random).create(level, EntitySpawnReason.COMMAND);
            if (!(e instanceof Mob mob)) {
                return null;
            }
            mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.COMMAND, null);
            arm(mob, depth, random);
            Powers.elite(mob, depth, random);
            mob.addTag(KIN);
            level.addFreshEntityWithPassengers(mob);
            Powers.track(level, mob);
            return mob.getDisplayName().getString();
        }
        List<BossSpec> choices = bosses.get(theme);
        if (choices == null || index < 0 || index >= choices.size()) {
            return null;
        }
        BossSpec spec = choices.get(index);
        for (int k = 0; k < (spec.follower() != null ? 4 : 1); k++) {
            Entity e = (k == 0 ? spec.type() : spec.follower()).create(level, EntitySpawnReason.COMMAND);
            if (!(e instanceof Mob mob)) {
                continue;
            }
            mob.snapTo(pos.getX() + 0.5 + (k == 0 ? 0 : random.nextInt(5) - 2), pos.getY(), pos.getZ() + 0.5 + (k == 0 ? 0 : random.nextInt(5) - 2), 0, 0);
            mob.addTag(Powers.NOROLL);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.COMMAND, null);
            if (k == 0) {
                crown(mob, spec, depth);
                Bosses.track(level, mob, spec.colour());
            } else {
                follow(mob, spec);
            }
            mob.addTag(KIN);
            mob.setPersistenceRequired();
            level.addFreshEntityWithPassengers(mob);
            Powers.track(level, mob);
        }
        if (spec.type() == EntityType.WARDEN) {
            net.minecraft.world.entity.player.Player near = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), 24, false);
            if (near != null) {
                for (net.minecraft.world.entity.monster.warden.Warden w : level.getEntitiesOfClass(net.minecraft.world.entity.monster.warden.Warden.class,
                        near.getBoundingBox().inflate(32), w -> w.entityTags().contains(KIN))) {
                    w.increaseAngerAt(near, 80, false);
                }
            }
        }
        return capitalise(spec.name());
    }

    /**
     * A hunted level's hunter: one of the theme's monsters as a two-affix
     * elite, bigger, with a long reach, let loose in the room furthest from
     * where {@code player} arrived and set on them.
     */
    static void hunter(ServerLevel level, Site site, int li, net.minecraft.world.entity.player.Player player) {
        com.sablednah.crawlspace.plan.LevelPlan lp = site.built().plan().levels().get(li);
        String theme = lp.theme.name();
        BlockPos o = site.origin();
        com.sablednah.crawlspace.plan.Room far = null;
        double best = -1;
        for (com.sablednah.crawlspace.plan.Room r : lp.rooms) {
            if (r.role == com.sablednah.crawlspace.plan.Role.PUZZLE || r.role == com.sablednah.crawlspace.plan.Role.SECRET) {
                continue;
            }
            double d = player.distanceToSqr(o.getX() + r.centerX() + 0.5, player.getY(), o.getZ() + r.centerZ() + 0.5);
            if (d > best) {
                best = d;
                far = r;
            }
        }
        if (far == null) {
            return;
        }
        RandomSource random = level.getRandom();
        // Plain floor in that room, nearest its middle: the middle itself may be a stairwell, a pillar or a pool.
        BlockPos pos = null;
        for (int d = 0; d <= Math.max(far.w, far.h) && pos == null; d++) {
            for (int dx = -d; dx <= d && pos == null; dx++) {
                for (int dz = -d; dz <= d && pos == null; dz++) {
                    int x = far.centerX() + dx;
                    int z = far.centerZ() + dz;
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == d && lp.cell(x, z) == com.sablednah.crawlspace.plan.Cell.FLOOR
                            && lp.region(x, z) == far.id) {
                        int y = o.getY() + com.sablednah.crawlspace.build.Blueprinter.floorY(site.built().plan(), li) + lp.height(x, z);
                        BlockPos c = new BlockPos(o.getX() + x, y, o.getZ() + z);
                        if (level.getBlockState(c).isAir() && level.getBlockState(c.above()).isAir() && !level.getBlockState(c.below()).isAir()) {
                            pos = c;
                        }
                    }
                }
            }
        }
        if (pos == null) {
            return;
        }
        Entity e = common(theme, random).create(level, EntitySpawnReason.EVENT);
        if (!(e instanceof Mob mob)) {
            return;
        }
        mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
        mob.addTag(Powers.NOROLL);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        arm(mob, li + 2, random);
        Powers.elite(mob, Math.max(li, 3), random, 2);
        modify(mob, Attributes.SCALE, "hunter_scale", 0.35, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(mob, Attributes.MAX_HEALTH, "hunter_health", 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (range != null) {
            range.setBaseValue(96);
        }
        mob.setHealth(mob.getMaxHealth());
        if (mob.getCustomName() != null) {
            mob.setCustomName(Component.literal(mob.getCustomName().getString().replace(
                    mob.getType().getDescription().getString(), "Hunter")).withStyle(ChatFormatting.DARK_RED));
        }
        mob.addTag(KIN);
        mob.addTag(Powers.HUNTER);
        mob.setPersistenceRequired();
        level.addFreshEntityWithPassengers(mob);
        mob.setTarget(player);
        Powers.track(level, mob);
        CrawlSpace.LOGGER.info("CrawlSpace: a hunter, {}, is loose on level {} of the dungeon at {}, set on {}",
                mob.getDisplayName().getString(), li + 1, o, player.getName().getString());
    }

    /**
     * An ambush trap's monsters: two or three of the level's own, round the
     * player, a few blocks off, on floor they can stand on, and set on them.
     * Returns how many came.
     */
    static int ambush(ServerLevel level, Site site, int li, net.minecraft.server.level.ServerPlayer player) {
        String theme = site.built().plan().levels().get(li).theme.name();
        RandomSource random = level.getRandom();
        int want = 2 + random.nextInt(2);
        int made = 0;
        for (int tries = 0; tries < 24 && made < want; tries++) {
            double a = random.nextDouble() * Math.PI * 2;
            BlockPos p = BlockPos.containing(player.getX() + Math.cos(a) * 3.5, player.getY(), player.getZ() + Math.sin(a) * 3.5);
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir() || level.getBlockState(p.below()).isAir()) {
                continue;
            }
            Entity e = common(theme, random).create(level, EntitySpawnReason.EVENT);
            if (!(e instanceof Mob mob)) {
                continue;
            }
            mob.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, random.nextFloat() * 360f, 0f);
            net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(p), EntitySpawnReason.EVENT, null);
            arm(mob, li, random);
            mob.addTag(KIN);
            level.addFreshEntityWithPassengers(mob);
            mob.setTarget(player);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 8, 0.3, 0.4, 0.3, 0.02);
            made++;
        }
        return made;
    }

    /** A wandering band for the dungeon clock: the level's monsters at {@code at}, set on {@code target}. */
    static int band(ServerLevel level, Site site, int li, BlockPos at, int count, net.minecraft.server.level.ServerPlayer target) {
        String theme = site.built().plan().levels().get(li).theme.name();
        RandomSource random = level.getRandom();
        int made = 0;
        for (int k = 0; k < count; k++) {
            BlockPos p = at.offset(k % 2, 0, k / 2);
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir() || level.getBlockState(p.below()).isAir()) {
                p = at;
            }
            Entity e = common(theme, random).create(level, EntitySpawnReason.EVENT);
            if (!(e instanceof Mob mob)) {
                continue;
            }
            mob.snapTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, random.nextFloat() * 360f, 0f);
            net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(p), EntitySpawnReason.EVENT, null);
            arm(mob, li, random);
            if (random.nextDouble() < Powers.eliteChance(li) && Powers.elite(mob, li, random)) {
                Powers.track(level, mob);
            }
            mob.addTag(KIN);
            AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
            if (range != null && range.getBaseValue() < 48) {
                range.setBaseValue(48);
            }
            level.addFreshEntityWithPassengers(mob);
            mob.setTarget(target);
            made++;
        }
        return made;
    }

    /** A swarm for the dungeon clock: silverfish, or endermites from the third level, boiling up round a player. */
    static int swarm(ServerLevel level, net.minecraft.server.level.ServerPlayer p, int li) {
        RandomSource random = level.getRandom();
        EntityType<?> type = li >= 2 && random.nextBoolean() ? EntityType.ENDERMITE : EntityType.SILVERFISH;
        int made = 0;
        for (int k = 0; k < 4 + random.nextInt(3); k++) {
            double a = random.nextDouble() * Math.PI * 2;
            BlockPos at = BlockPos.containing(p.getX() + Math.cos(a) * 2, p.getY(), p.getZ() + Math.sin(a) * 2);
            if (!level.getBlockState(at).isAir() || level.getBlockState(at.below()).isAir()) {
                continue;
            }
            Entity e = type.create(level, EntitySpawnReason.EVENT);
            if (!(e instanceof Mob mob)) {
                continue;
            }
            mob.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360f, 0f);
            mob.addTag(Powers.NOROLL);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
            mob.addTag(KIN);
            mob.addTag(Powers.SPAWN);
            level.addFreshEntity(mob);
            mob.setTarget(p);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.01);
            made++;
        }
        return made;
    }

    /** A stranger for the dungeon clock: a wandering trader, named, who leaves after a few minutes. */
    static boolean stranger(ServerLevel level, net.minecraft.server.level.ServerPlayer p) {
        RandomSource random = level.getRandom();
        for (int tries = 0; tries < 12; tries++) {
            double a = random.nextDouble() * Math.PI * 2;
            BlockPos at = BlockPos.containing(p.getX() + Math.cos(a) * 3, p.getY(), p.getZ() + Math.sin(a) * 3);
            if (!level.getBlockState(at).isAir() || !level.getBlockState(at.above()).isAir() || level.getBlockState(at.below()).isAir()) {
                continue;
            }
            net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader t = EntityType.WANDERING_TRADER.create(level, EntitySpawnReason.EVENT);
            if (t == null) {
                return false;
            }
            t.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360f, 0f);
            t.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
            t.setDespawnDelay(3 * 60 * 20);
            t.setCustomName(Component.literal("the Stranger").withStyle(ChatFormatting.DARK_AQUA));
            t.addTag(KIN);
            level.addFreshEntity(t);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 12, 0.3, 0.6, 0.3, 0.01);
            return true;
        }
        return false;
    }

    /** A glint for the dungeon clock: one thing from the level's supplies, dropped at a player's feet. */
    static void glint(ServerLevel level, net.minecraft.server.level.ServerPlayer p, int li) {
        var table = level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(CrawlSpace.MODID, "chests/tier" + Math.min(5, 1 + li / 2))));
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, p.position())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        java.util.List<ItemStack> items = table.getRandomItems(params);
        if (!items.isEmpty()) {
            ItemStack it = items.get(level.getRandom().nextInt(items.size()));
            level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, p.getX(), p.getY() + 0.3, p.getZ(), it));
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.WAX_OFF, p.getX(), p.getY() + 0.2, p.getZ(), 8, 0.4, 0.1, 0.4, 0.01);
    }

    /** How many bosses a theme has, for the command's help. */
    static int bossCount(String theme) {
        return bosses.getOrDefault(theme, List.of()).size();
    }

    private static final List<Item[]> ARMOUR = List.of(
            new Item[] {Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS},
            new Item[] {Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS},
            new Item[] {Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS},
            new Item[] {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS});
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Armour and a little extra health, more of both deeper down. Armour is worn only by mobs that show it. */
    private static void arm(Mob mob, int depth, RandomSource random) {
        EntityType<?> t = mob.getType();
        // A datapack's "armour" for this mob, where it gave one; otherwise whether it shows armour at all.
        boolean humanoid = common.values().stream().flatMap(List::stream).filter(p -> p.type() == t)
                .map(Pick::armour).findFirst().orElse(HUMANOIDS.contains(t));
        if (humanoid) {
            double chance = Math.min(0.9, 0.1 + 0.1 * depth);
            Item[] set = ARMOUR.get(Math.min(ARMOUR.size() - 1, depth / 2));
            for (int k = 0; k < 4; k++) {
                if (random.nextDouble() < chance) {
                    mob.setItemSlot(SLOTS[k], new ItemStack(set[k]));
                    mob.setDropChance(SLOTS[k], 0.05f);
                }
            }
            if ((t == EntityType.ZOMBIE || t == EntityType.HUSK) && random.nextDouble() < 0.2 + 0.08 * depth) {
                mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(depth >= 5 ? Items.DIAMOND_SWORD : Items.IRON_SWORD));
                mob.setDropChance(EquipmentSlot.MAINHAND, 0.05f);
            }
        }
        modify(mob, Attributes.MAX_HEALTH, "depth_health", 2.0 * depth, AttributeModifier.Operation.ADD_VALUE);
        mob.setHealth(mob.getMaxHealth());
    }

    /** A boss: its weapon, a great deal more health, a harder hit, its size and its name. */
    private static void crown(Mob mob, BossSpec spec, int depth) {
        if (spec.weapon() != Items.AIR) {
            mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(spec.weapon()));
            mob.setDropChance(EquipmentSlot.MAINHAND, 0.25f);
        }
        if (spec.health() > 0) {
            base(mob, Attributes.MAX_HEALTH, spec.health() + 8.0 * depth);
        } else {
            modify(mob, Attributes.MAX_HEALTH, "boss_health", 30 + 12.0 * depth, AttributeModifier.Operation.ADD_VALUE);
        }
        if (spec.damage() > 0) {
            base(mob, Attributes.ATTACK_DAMAGE, spec.damage() + depth / 2.0);
        } else {
            modify(mob, Attributes.ATTACK_DAMAGE, "boss_damage", 1 + depth, AttributeModifier.Operation.ADD_VALUE);
        }
        modify(mob, Attributes.SCALE, "boss_scale", spec.scale() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(mob, Attributes.MOVEMENT_SPEED, "boss_speed", spec.speed() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        // A boss notices anyone in its lair: an endermite's own 16 left the Gnawing Mite blind to a player across a big room.
        reach(mob);
        if (spec.scale() != 1) {
            mob.addTag(Powers.SCALED);
        }
        for (String power : spec.powers()) {
            mob.addTag(Powers.POWER + power);
        }
        if (spec.minion() != null) {
            mob.addTag(Powers.MINION + net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(spec.minion()));
        }
        mob.setHealth(mob.getMaxHealth());
        mob.setCustomName(Component.literal(capitalise(spec.name())).withStyle(ChatFormatting.GOLD));
        mob.setCustomNameVisible(true);
        mob.addTag(Bosses.TAG);
        mob.addTag(Bosses.COLOUR_TAG + spec.colour().getName());
    }

    private static void reach(Mob mob) {
        AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (range != null && range.getBaseValue() < 40) {
            range.setBaseValue(40);
        }
    }

    /** One of a strange boss's own pack (a Wardling): its size, health and bite. */
    private static void follow(Mob mob, BossSpec spec) {
        reach(mob);
        modify(mob, Attributes.SCALE, "follower_scale", spec.followerScale() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (spec.followerHealth() > 0) {
            base(mob, Attributes.MAX_HEALTH, spec.followerHealth());
        }
        if (spec.followerDamage() > 0) {
            base(mob, Attributes.ATTACK_DAMAGE, spec.followerDamage());
        }
        if (spec.followerScale() != 1) {
            mob.addTag(Powers.SCALED);
        }
        mob.setHealth(mob.getMaxHealth());
    }

    private static void base(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, double value) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }

    private static void modify(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
            String id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null && amount != 0) {
            inst.addPermanentModifier(new AttributeModifier(Identifier.fromNamespaceAndPath(CrawlSpace.MODID, id), amount, op));
        }
    }

    private static String capitalise(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
