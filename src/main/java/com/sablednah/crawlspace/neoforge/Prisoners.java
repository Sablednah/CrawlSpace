package com.sablednah.crawlspace.neoforge;

import java.util.ArrayList;
import java.util.List;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Trigger;
import com.sablednah.crawlspace.plan.Cell;
import com.sablednah.crawlspace.plan.LevelPlan;
import com.sablednah.crawlspace.plan.Room;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * The prisoner (Against the Giants, Goblin Punch; Sable's list, 2026-10-11).
 * Half the lairs above the bottom keep a captive: a villager in a cage of iron
 * bars against a wall, unable to move or be hurt. While the lair's boss lives
 * the prisoner will not leave. Once it is dead, using them lifts the bars:
 * they press a gift on you, tell you something true about the dungeon (never
 * one of the rumour book's lies), and slip away.
 */
public final class Prisoners {

    static final String TAG = "crawlspace_prisoner";
    static final double CHANCE = 0.5;

    private Prisoners() {
    }

    static boolean keeps(Site site, Trigger t) {
        if (t.kind() != Trigger.Kind.BOSS || Altars.sleeps(site, t)) {
            return false;
        }
        return com.sablednah.crawlspace.plan.Showcase.on(site.seed())
                || com.sablednah.crawlspace.plan.Dice.of(site.seed(), t.level(), 0x9815L).chance(CHANCE);
    }

    /** From Bestiary.wake, for a lair that keeps one: a cell against a wall, barred, with the prisoner in it. */
    static void place(ServerLevel level, Site site, Trigger t) {
        LevelPlan lp = site.built().plan().levels().get(t.level());
        Room r = lp.room(lp.region(t.x(), t.z()));
        if (r == null) {
            return;
        }
        BlockPos o = site.origin();
        BlockPos boss = o.offset(t.targets()[0][0], t.targets()[0][1], t.targets()[0][2]);
        BlockPos best = null;
        for (int x = r.minX(); x <= r.maxX(); x++) {
            for (int z = r.minZ(); z <= r.maxZ(); z++) {
                if (!r.contains(x, z) || lp.cell(x, z) != Cell.FLOOR || nearDoor(lp, x, z)) {
                    continue;
                }
                int walls = 0;
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    walls += lp.cell(x + d[0], z + d[1]) == Cell.WALL ? 1 : 0;
                }
                if (walls != 2) {
                    continue; // a corner: two walls, two sides to bar
                }
                int f = com.sablednah.crawlspace.build.Blueprinter.floorAt(site.built().plan(), t.level(), x, z);
                BlockPos p = o.offset(x, f, z);
                if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir() || level.getBlockState(p.below()).isAir()) {
                    continue;
                }
                if (best == null || p.distSqr(boss) > best.distSqr(boss)) {
                    best = p;
                }
            }
        }
        if (best == null) {
            return;
        }
        // Bars on the open sides, two high, where there is air. Two open sides at right angles
        // meet only diagonally, and bars join only straight across, so the corner between them
        // is barred too: without it the two sides stand as separate stubs.
        List<int[]> open = new ArrayList<>();
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (level.getBlockState(best.offset(d[0], 0, d[1])).isAir()) {
                open.add(d);
            }
        }
        List<int[]> spots = new ArrayList<>(open);
        if (open.size() == 2 && open.get(0)[0] * open.get(1)[0] + open.get(0)[1] * open.get(1)[1] == 0) {
            spots.add(new int[] {open.get(0)[0] + open.get(1)[0], open.get(0)[1] + open.get(1)[1]});
        }
        List<BlockPos> bars = new ArrayList<>();
        for (int[] d : spots) {
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos b = best.offset(d[0], dy, d[1]);
                if (level.getBlockState(b).isAir()) {
                    bars.add(b);
                }
            }
        }
        if (bars.isEmpty()) {
            return;
        }
        for (BlockPos b : bars) {
            level.setBlock(b, Blocks.IRON_BARS.defaultBlockState(), 3);
        }
        // A placed block keeps the shape it was given; only its neighbours are told. Each bar
        // works out its own joins once they are all in.
        for (BlockPos b : bars) {
            level.setBlock(b, Block.updateFromNeighbourShapes(level.getBlockState(b), level, b), 3);
        }
        Mob prisoner = EntityType.VILLAGER.create(level, EntitySpawnReason.EVENT);
        if (prisoner == null) {
            return;
        }
        prisoner.snapTo(best.getX() + 0.5, best.getY(), best.getZ() + 0.5, 0, 0);
        prisoner.setNoAi(true);
        prisoner.setPersistenceRequired();
        prisoner.addTag(TAG);
        prisoner.setCustomName(Component.literal("Prisoner").withStyle(ChatFormatting.AQUA));
        prisoner.setCustomNameVisible(true);
        level.addFreshEntity(prisoner);
        CrawlSpace.LOGGER.info("CrawlSpace: a prisoner at {}", best);
    }

    private static boolean nearDoor(LevelPlan lp, int x, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (lp.cell(x + dx, z + dz).isDoor()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A caged prisoner takes no harm. A cancelled hurt rather than
     * {@code setInvulnerable}, which 26.3 no longer has: this is the same on
     * every line.
     */
    public static void onHurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e) {
        if (e.getEntity().getTags().contains(TAG)) {
            e.setCanceled(true);
        }
    }

    /** Using a prisoner: freed, if the lair's boss is dead. Cancels the villager's own trade screen either way. */
    public static void onInteract(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof Mob prisoner) || !prisoner.getTags().contains(TAG)
                || !(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        tryFree(level, prisoner, player);
    }

    /**
     * Using a cage's bars is using the prisoner: the bars stand between you, and
     * a click at the villager mostly lands on them. Returns whether there was
     * a prisoner behind these bars.
     */
    static boolean useBars(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (!level.getBlockState(pos).is(Blocks.IRON_BARS)) {
            return false;
        }
        for (Mob m : level.getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(pos).inflate(1.0),
                m -> m.getTags().contains(TAG))) {
            tryFree(level, m, player);
            return true;
        }
        return false;
    }

    private static void tryFree(ServerLevel level, Mob prisoner, ServerPlayer player) {
        for (Mob m : level.getEntitiesOfClass(Mob.class, prisoner.getBoundingBox().inflate(48), m -> m.isAlive() && m.getTags().contains(Bosses.TAG))) {
            Triggers.tell(player, "The prisoner shrinks back. \"Not while it lives! Kill the beast, and I'll tell you what I know.\"");
            return;
        }
        free(level, prisoner, player);
    }

    private static void free(ServerLevel level, Mob prisoner, ServerPlayer player) {
        BlockPos at = prisoner.blockPosition();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    BlockPos b = at.offset(dx, dy, dz);
                    if (level.getBlockState(b).is(Blocks.IRON_BARS)) {
                        level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
        level.playSound(null, at, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1f, 1f);
        level.playSound(null, at, SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
        Site site = Dungeons.at(level, at).orElse(null);
        String secret = "There is nothing more down here. Get out while you can.";
        int li = 2;
        if (site != null) {
            List<String> truths = Rumours.truths(site, site.built());
            if (!truths.isEmpty()) {
                secret = truths.get(level.getRandom().nextInt(truths.size()));
            }
            li = Math.max(0, Arrivals.levelAt(site, at));
        }
        player.sendSystemMessage(Component.literal("Prisoner: \"Free! Thank you. Take this, and hear me: " + secret + "\"")
                .withStyle(ChatFormatting.AQUA));
        var loot = level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.LOOT_TABLE,
                net.minecraft.resources.Identifier.fromNamespaceAndPath(CrawlSpace.MODID, "chests/tier" + Math.min(5, 1 + li / 2))));
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, prisoner.position())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        List<ItemStack> gifts = loot.getRandomItems(params);
        for (int k = 0; k < Math.min(3, gifts.size()); k++) {
            prisoner.spawnAtLocation(level, gifts.get(k));
        }
        prisoner.removeTag(TAG);
        Crumbles.later(level, 60, () -> {
            if (prisoner.isAlive()) {
                level.sendParticles(ParticleTypes.POOF, prisoner.getX(), prisoner.getY() + 1, prisoner.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
                prisoner.discard();
            }
        });
    }
}
