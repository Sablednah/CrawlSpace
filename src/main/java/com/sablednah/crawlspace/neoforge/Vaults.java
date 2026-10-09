package com.sablednah.crawlspace.neoforge;

import java.util.ArrayList;
import java.util.List;

import com.sablednah.crawlspace.CrawlSpace;
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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Vaults: take one of three. A vault's items appear over its pedestals the
 * first time anyone comes within ten blocks, rolled from the loot a tier above
 * the level's, three different things. Using a pedestal takes its item, and
 * the other two are caged in iron bars for good, their items left to be seen.
 * One choice per vault, shared by everyone.
 */
public final class Vaults {

    static final String TAG = "crawlspace_vault";

    private Vaults() {
    }

    /** Where the vault's state is kept in CrawlState: under keys no block of the dungeon uses. */
    private static BlockPos shown(BlockPos lead) {
        return lead.above(200);
    }

    private static BlockPos chosen(BlockPos lead) {
        return lead.above(201);
    }

    private static BlockPos lead(Site site, Trigger t) {
        return site.origin().offset(t.targets()[0][0], t.targets()[0][1], t.targets()[0][2]);
    }

    /** From Triggers' tick, once a second: fills any vault near the player that has not been filled. */
    static void tick(ServerLevel level, ServerPlayer player, Site site) {
        CrawlState state = CrawlState.of(level);
        BlockPos o = site.origin();
        for (Trigger t : site.built().blueprint().triggers()) {
            if (t.kind() != Trigger.Kind.VAULT || t.targets()[0][0] != t.x() || t.targets()[0][2] != t.z()) {
                continue; // one fill per vault: its first pedestal's
            }
            BlockPos lead = o.offset(t.x(), t.y(), t.z());
            if (lead.distSqr(player.blockPosition()) > 10 * 10 || state.hasFired(shown(lead))) {
                continue;
            }
            state.fire(shown(lead));
            List<ItemStack> items = roll(level, t.level(), lead);
            for (int k = 0; k < t.targets().length; k++) {
                BlockPos p = o.offset(t.targets()[k][0], t.targets()[k][1], t.targets()[k][2]);
                Display.ItemDisplay d = EntityType.ITEM_DISPLAY.create(level, EntitySpawnReason.TRIGGERED);
                if (d == null) {
                    continue;
                }
                d.snapTo(p.getX() + 0.5, p.getY() + 1.4, p.getZ() + 0.5, 0, 0);
                d.getSlot(0).set(items.get(k));
                d.addTag(TAG);
                level.addFreshEntity(d);
                level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 1.4, p.getZ() + 0.5, 6, 0.15, 0.15, 0.15, 0.01);
            }
            level.playSound(null, lead, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.5f, 0.8f);
            Triggers.tell(player, "Three treasures on three pedestals. You may take one.");
        }
    }

    /** Three different things from the loot a tier above the level's, made up with gems where the table runs short. */
    private static List<ItemStack> roll(ServerLevel level, int li, BlockPos at) {
        int tier = Math.min(5, 2 + li / 2);
        var table = level.getServer().reloadableRegistries().getLootTable(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath(CrawlSpace.MODID, "chests/tier" + tier)));
        var params = new net.minecraft.world.level.storage.loot.LootParams.Builder(level)
                .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN, at.getCenter())
                .create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.CHEST);
        List<ItemStack> out = new ArrayList<>();
        for (int tries = 0; tries < 6 && out.size() < 3; tries++) {
            List<ItemStack> got = new ArrayList<>(table.getRandomItems(params));
            // The best first: things that do not stack (gear, books) before handfuls.
            got.sort((a, b) -> Integer.compare(a.getMaxStackSize(), b.getMaxStackSize()));
            for (ItemStack s : got) {
                boolean dup = out.stream().anyMatch(o -> o.getItem() == s.getItem());
                if (!dup && out.size() < 3) {
                    out.add(s);
                }
            }
        }
        ItemStack[] spare = {new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.EMERALD, 6), new ItemStack(Items.GOLDEN_APPLE)};
        for (int k = 0; out.size() < 3; k++) {
            out.add(spare[k % spare.length].copy());
        }
        return out;
    }

    /** Using a pedestal: take its item, and cage the others; or be told the choice is made. */
    static boolean use(ServerLevel level, ServerPlayer player, Site site, Trigger t) {
        if (t.kind() != Trigger.Kind.VAULT) {
            return false;
        }
        CrawlState state = CrawlState.of(level);
        BlockPos lead = lead(site, t);
        if (!state.hasFired(shown(lead))) {
            return true;
        }
        if (state.hasFired(chosen(lead))) {
            Triggers.tell(player, "The cages hold. The choice was made.");
            return true;
        }
        BlockPos mine = site.origin().offset(t.x(), t.y(), t.z());
        Display.ItemDisplay d = display(level, mine);
        if (d == null) {
            return true;
        }
        state.fire(chosen(lead));
        ItemStack item = d.getSlot(0).get().copy();
        d.discard();
        if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }
        for (int[] o : t.targets()) {
            BlockPos p = site.origin().offset(o[0], o[1], o[2]);
            if (!p.equals(mine)) {
                level.setBlock(p.above(), Blocks.IRON_BARS.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 1.5, p.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.01);
            }
        }
        level.playSound(null, mine, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.8f, 1.4f);
        player.displayClientMessage(Component.literal("You take ").append(item.getHoverName())
                .append(Component.literal(". The other cages slam shut.")).withStyle(ChatFormatting.GOLD), true);
        return true;
    }

    private static Display.ItemDisplay display(ServerLevel level, BlockPos pedestal) {
        List<Display.ItemDisplay> found = level.getEntitiesOfClass(Display.ItemDisplay.class, new AABB(pedestal.above()).inflate(0.5),
                e -> e.getTags().contains(TAG));
        return found.isEmpty() ? null : found.get(0);
    }
}
