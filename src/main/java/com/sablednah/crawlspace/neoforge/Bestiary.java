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

    private record Pick(EntityType<?> type, int weight) {
    }

    /** A theme's boss: what it is, what it is called, what it carries, its bar's colour. */
    private record BossSpec(EntityType<?> type, String name, Item weapon, BossEvent.BossBarColor colour, double scale) {
    }

    private static final Map<String, List<Pick>> COMMON = new HashMap<>();
    private static final Map<String, BossSpec> BOSSES = new HashMap<>();

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

        BOSSES.put("Crypt", new BossSpec(EntityType.SKELETON, "the Bone Warden", Items.BOW, BossEvent.BossBarColor.WHITE, 1.3));
        BOSSES.put("Sunken Halls", new BossSpec(EntityType.DROWNED, "the Drowned Reeve", Items.TRIDENT, BossEvent.BossBarColor.BLUE, 1.35));
        BOSSES.put("Old Mines", new BossSpec(EntityType.ZOMBIE, "the Foreman", Items.DIAMOND_PICKAXE, BossEvent.BossBarColor.YELLOW, 1.35));
        BOSSES.put("Caverns", new BossSpec(EntityType.SPIDER, "the Broodmother", Items.AIR, BossEvent.BossBarColor.GREEN, 1.8));
        BOSSES.put("Deep Halls", new BossSpec(EntityType.VINDICATOR, "the Gaoler", Items.DIAMOND_AXE, BossEvent.BossBarColor.PURPLE, 1.3));
    }

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
        if (e.getEntity().getTags().contains(KIN) && e.getSource().getEntity() instanceof Mob attacker
                && attacker.getTags().contains(KIN)) {
            e.setCanceled(true);
        }
    }

    public static void onTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent e) {
        if (e.getEntity().getTags().contains(KIN) && e.getNewAboutToBeSetTarget() != null
                && e.getNewAboutToBeSetTarget().getTags().contains(KIN)) {
            e.setCanceled(true);
        }
    }

    static EntityType<?> common(String theme, RandomSource random) {
        List<Pick> picks = COMMON.getOrDefault(theme, COMMON.get("Crypt"));
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
        String bossName = null;
        for (int k = 0; k < t.targets().length; k++) {
            int[] s = t.targets()[k];
            BlockPos pos = o.offset(s[0], s[1], s[2]);
            boolean boss = t.kind() == Trigger.Kind.BOSS && k == 0;
            BossSpec spec = BOSSES.getOrDefault(theme, BOSSES.get("Crypt"));
            EntityType<?> type = boss ? spec.type() : common(theme, random);
            Entity e = type.create(level, EntitySpawnReason.STRUCTURE);
            if (!(e instanceof Mob mob)) {
                continue;
            }
            mob.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360f, 0f);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.STRUCTURE, null);
            arm(mob, depth + (boss ? 2 : 0), random);
            if (boss) {
                crown(mob, spec, depth);
                Bosses.track(level, mob, spec.colour());
                bossName = spec.name();
            }
            mob.addTag(KIN);
            mob.setPersistenceRequired();
            level.addFreshEntityWithPassengers(mob);
            spawned++;
        }
        if (spawned == 0) {
            return null;
        }
        return bossName != null ? capitalise(bossName) + " rises from its lair!" : "Something stirs in the dark.";
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
        boolean humanoid = t == EntityType.ZOMBIE || t == EntityType.SKELETON || t == EntityType.DROWNED
                || t == EntityType.STRAY || t == EntityType.BOGGED || t == EntityType.WITHER_SKELETON || t == EntityType.HUSK;
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
        modify(mob, Attributes.MAX_HEALTH, "boss_health", 30 + 12.0 * depth, AttributeModifier.Operation.ADD_VALUE);
        modify(mob, Attributes.ATTACK_DAMAGE, "boss_damage", 1 + depth, AttributeModifier.Operation.ADD_VALUE);
        modify(mob, Attributes.SCALE, "boss_scale", spec.scale() - 1, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mob.setHealth(mob.getMaxHealth());
        mob.setCustomName(Component.literal(capitalise(spec.name())).withStyle(ChatFormatting.GOLD));
        mob.setCustomNameVisible(true);
        mob.addTag(Bosses.TAG);
        mob.addTag(Bosses.COLOUR_TAG + spec.colour().getName());
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
