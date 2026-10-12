package com.sablednah.crawlspace.neoforge;

import java.util.List;

import com.sablednah.crawlspace.CrawlSpace;
import com.sablednah.crawlspace.build.Trigger;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Token vaults (Wynncraft; Sable's pick, 2026-10-12). One guard room on a level
 * carries vault tokens, one dropped by each of its monsters; a treasure room on
 * the same level is sealed with a portcullis, a cauldron beside it. Tokens go in
 * the cauldron; with enough, the portcullis rises. A token is a vanilla trial key
 * with a name, a line of lore and a tag a vanilla client never sees, like a
 * portcullis key, so one from another level or dungeon opens nothing.
 */
public final class Tokens {

    static final String CARRIER = "crawlspace_token_carrier";
    private static final String TAG = "crawlspace_token";
    /** Markers: the n-th token in, at cauldron.above(BASE + n). */
    private static final int BASE = 700;

    private Tokens() {
    }

    static ItemStack make(Site site, int li) {
        ItemStack token = new ItemStack(Items.TRIAL_KEY);
        token.set(DataComponents.CUSTOM_NAME, Component.literal("Vault Token").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
        token.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("For the cauldron by the sealed vault on level " + (li + 1) + ".")
                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)))));
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG, Keys.id(site, li));
        token.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        token.set(DataComponents.RARITY, Rarity.UNCOMMON);
        token.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return token;
    }

    private static String idOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        String id = data.copyTag().getStringOr(TAG, "");
        return id.isEmpty() ? null : id;
    }

    /** A carrier falls: its token, for its level. */
    static void died(ServerLevel level, Mob mob) {
        if (!mob.getTags().contains(CARRIER)) {
            return;
        }
        Site site = Dungeons.at(level, mob.blockPosition()).orElse(null);
        int li = site == null ? -1 : Arrivals.levelAt(site, mob.blockPosition());
        if (li >= 0) {
            mob.spawnAtLocation(level, make(site, li));
        }
    }

    private static int stored(CrawlState state, BlockPos cauldron) {
        int n = 0;
        while (state.hasFired(cauldron.above(BASE + n))) {
            n++;
        }
        return n;
    }

    /** Using a block: a token vault's cauldron, if it is one. Returns whether it was. */
    static boolean use(ServerLevel level, ServerPlayer player, Site site, BlockPos pos) {
        BlockPos o = site.origin();
        Trigger t = site.built().blueprint().triggerAt(pos.getX() - o.getX(), pos.getY() - o.getY(), pos.getZ() - o.getZ());
        if (t == null || t.kind() != Trigger.Kind.TOKEN_VAULT) {
            return false;
        }
        CrawlState state = CrawlState.of(level);
        int needed = t.targets()[0][0];
        int have = stored(state, pos);
        if (have >= needed) {
            Triggers.tell(player, "The vault is open.");
            return true;
        }
        ItemStack hand = player.getMainHandItem();
        String id = Keys.id(site, t.level());
        if (!id.equals(idOf(hand))) {
            Triggers.tell(player, idOf(hand) != null ? "That token is for another vault."
                    : "The cauldron wants vault tokens: " + (needed - have) + " more. One guard room on this level carries them: each of its monsters drops one.");
            return true;
        }
        int put = Math.min(hand.getCount(), needed - have);
        hand.shrink(put);
        for (int k = 0; k < put; k++) {
            state.fire(pos.above(BASE + have + k));
        }
        have += put;
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1f, 1.2f);
        level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.3);
        if (have < needed) {
            Triggers.tell(player, have + " of " + needed + " tokens. " + (needed - have) + " more to go.");
            return true;
        }
        int[][] bars = new int[t.targets().length - 1][];
        System.arraycopy(t.targets(), 1, bars, 0, bars.length);
        Triggers.raise(level, site, bars);
        Triggers.tell(player, "The last token sinks. The vault's portcullis rises!");
        CrawlSpace.LOGGER.info("CrawlSpace: a token vault opened at {}", pos);
        return true;
    }
}
