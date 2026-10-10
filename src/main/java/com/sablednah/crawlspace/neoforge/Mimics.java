package com.sablednah.crawlspace.neoforge;

import com.sablednah.crawlspace.build.Blueprint;
import com.sablednah.crawlspace.build.Part;
import com.sablednah.crawlspace.build.Trigger;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Chests that are not what they seem (the DMG's guarded treasure; every
 * dungeon game's mimic). From the third level, about one ordinary chest in ten,
 * fixed by the dungeon's dice: half bite (the chest becomes a Mimic, wearing
 * it, which drops the chest's loot when killed), half are trapped (it opens,
 * and springs a trap on whoever opened it). Each happens once.
 */
public final class Mimics {

    static final String TAG = "crawlspace_mimic";
    static final double CHANCE = 0.1;

    private Mimics() {
    }

    /** Using a block: a dungeon chest's surprise, if it has one. Returns true if the opening is to be cancelled. */
    static boolean open(ServerLevel level, ServerPlayer player, Site site, BlockPos pos) {
        BlockPos o = site.origin();
        int code = site.built().blueprint().get(pos.getX() - o.getX(), pos.getY() - o.getY(), pos.getZ() - o.getZ());
        if (code == 0 || Blueprint.part(code) != Part.CHEST) {
            return false;
        }
        int li = Blueprint.level(code);
        if (li < 2) {
            return false;
        }
        int what = kind(site, pos);
        if (what == 0) {
            return false;
        }
        com.sablednah.crawlspace.plan.Dice dice = com.sablednah.crawlspace.plan.Dice.of(site.seed(), pos.asLong(), 0x31CL);
        dice.chance(CHANCE);
        CrawlState state = CrawlState.of(level);
        BlockPos marker = pos.above(150);
        if (state.hasFired(marker)) {
            return false;
        }
        state.fire(marker);
        dice.chance(0.5);
        // A Lootr chest is one chest per player: a mimic would take it from every one of them.
        if (what == 1 && level.getBlockState(pos).is(Blocks.CHEST)) {
            bite(level, player, pos, li);
            return true;
        }
        Trigger.Kind[] kinds = {Trigger.Kind.WEBS, Trigger.Kind.GAS, Trigger.Kind.ALARM, Trigger.Kind.ROCKFALL};
        Trigger.Kind kind = kinds[dice.nextInt(kinds.length)];
        Triggers.tell(player, "Click. The chest was trapped!");
        Triggers.springHere(level, player, kind);
        return false;
    }

    /** What a dungeon chest at {@code pos} is: 0 a chest, 1 a mimic, 2 trapped. Its dice, the same every time. */
    static int kind(Site site, BlockPos pos) {
        com.sablednah.crawlspace.plan.Dice dice = com.sablednah.crawlspace.plan.Dice.of(site.seed(), pos.asLong(), 0x31CL);
        if (!dice.chance(CHANCE)) {
            return 0;
        }
        return dice.chance(0.5) ? 1 : 2;
    }

    /** For /crawlspace chests: how many of each on a level, and the nearest of each to {@code from}. */
    static String survey(Site site, int li, BlockPos from) {
        int[] n = new int[3];
        BlockPos[] near = new BlockPos[3];
        BlockPos o = site.origin();
        site.built().blueprint().forEachColumn(col -> {
            for (int k = 0; k < col.codes().length; k++) {
                int code = col.codes()[k];
                if (code == 0 || Blueprint.part(code) != Part.CHEST || Blueprint.level(code) != li) {
                    continue;
                }
                BlockPos p = o.offset(col.x(), col.y0() + k, col.z());
                int what = li >= 2 ? kind(site, p) : 0;
                n[what]++;
                if (near[what] == null || p.distSqr(from) < near[what].distSqr(from)) {
                    near[what] = p;
                }
            }
        });
        return n[0] + " plain chests, " + n[1] + " mimics (nearest " + (near[1] == null ? "-" : near[1].toShortString()) + "), "
                + n[2] + " trapped (nearest " + (near[2] == null ? "-" : near[2].toShortString()) + ")";
    }

    static final String LOOT = "crawlspace_mimic_loot";

    /** A mimic dies: what its chest held, rolled now, at its feet. */
    static void died(ServerLevel level, Mob mob) {
        String table = mob.getPersistentData().getStringOr(LOOT, "");
        if (!mob.getTags().contains(TAG) || table.isEmpty()) {
            return;
        }
        Identifier id = Identifier.tryParse(table);
        if (id == null) {
            return;
        }
        net.minecraft.world.level.storage.loot.LootTable loot = level.getServer().reloadableRegistries()
                .getLootTable(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, id));
        net.minecraft.world.level.storage.loot.LootParams params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, mob.position())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        int n = 0;
        for (ItemStack stack : loot.getRandomItems(params)) {
            mob.spawnAtLocation(level, stack);
            n++;
        }
        com.sablednah.crawlspace.CrawlSpace.LOGGER.info("CrawlSpace: a mimic died at {} and dropped {} stack(s) of {}", mob.blockPosition(), n, table);
        mob.spawnAtLocation(level, new ItemStack(Items.CHEST));
    }

    /** The chest is gone, and in its place a Mimic, wearing it, set on whoever opened it. */
    private static void bite(ServerLevel level, ServerPlayer player, BlockPos pos, int li) {
        String table = "";
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity chest) {
            if (chest.getLootTable() != null) {
                table = chest.getLootTable().identifier().toString();
            } else {
                // Already rolled (someone looked in from creative): what it holds is what it held.
                net.minecraft.world.Containers.dropContents(level, pos, chest);
            }
        }
        level.removeBlock(pos, false);
        Mob mimic = EntityType.HUSK.create(level, EntitySpawnReason.EVENT);
        if (mimic == null) {
            return;
        }
        mimic.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot() + 180, 0);
        mimic.addTag(Powers.NOROLL);
        mimic.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.EVENT, null);
        mimic.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHEST));
        mimic.setDropChance(EquipmentSlot.HEAD, 0f);
        mimic.getAttribute(Attributes.MAX_HEALTH).setBaseValue(30 + 4.0 * li);
        mimic.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6 + li);
        mimic.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.3);
        mimic.setHealth(mimic.getMaxHealth());
        mimic.setCustomName(Component.literal("Mimic").withStyle(ChatFormatting.DARK_RED));
        mimic.setCustomNameVisible(true);
        mimic.addTag(TAG);
        mimic.getPersistentData().putString(LOOT, table);
        mimic.addTag(Powers.ELITE);
        mimic.addTag(Powers.DEPTH + (li + 2));
        mimic.addTag(Bestiary.KIN);
        mimic.setPersistenceRequired();
        level.addFreshEntity(mimic);
        mimic.setTarget(player);
        level.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.2);
        level.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.HOSTILE, 1.5f, 0.5f);
        level.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1.5f, 0.8f);
        Triggers.tell(player, "The chest bites! It was a Mimic. Kill it for what it held.");
    }
}
