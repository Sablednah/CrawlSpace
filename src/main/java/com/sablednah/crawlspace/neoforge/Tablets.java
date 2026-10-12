package com.sablednah.crawlspace.neoforge;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Replay tablets (Sable's ruling, 2026-10-12): dropped, one to three, by an
 * objective room's job. Each carries one boon and one bane for a whole
 * dungeon. Using one wakes the dungeon again at once, harder, for everyone in
 * it, so a party can retry together (see {@link Heroic}).
 */
public final class Tablets {

    /** What a tablet gives. */
    public enum Boon {
        XP("Twice the experience from its monsters"),
        LOOT("Its chests a tier richer"),
        BLESSED("Its blessings last twice as long"),
        GOLD("Its monsters carry gold");

        public final String text;

        Boon(String text) {
            this.text = text;
        }
    }

    /** What a tablet costs. */
    public enum Bane {
        TOUGH("Its monsters have half again their health"),
        FIERCE("Its monsters hit half again as hard"),
        HORDE("Half again as many monsters"),
        CHAMPIONS("Champions twice as common");

        public final String text;

        Bane(String text) {
            this.text = text;
        }
    }

    static final String TAG = "crawlspace_tablet";

    private Tablets() {
    }

    static ItemStack make(RandomSource random) {
        Boon boon = Boon.values()[random.nextInt(Boon.values().length)];
        Bane bane = Bane.values()[random.nextInt(Bane.values().length)];
        ItemStack t = new ItemStack(Items.FLOW_POTTERY_SHERD);
        t.set(DataComponents.CUSTOM_NAME, Component.literal("Replay Tablet").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.AQUA)));
        t.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("+ " + boon.text).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GREEN)),
                Component.literal("- " + bane.text).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.RED)),
                Component.literal("Use it in a dungeon: it wakes again, harder, for everyone in it.")
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)))));
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG, boon.name() + ":" + bane.name());
        t.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        t.set(DataComponents.RARITY, Rarity.RARE);
        t.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return t;
    }

    /** "BOON:BANE", or null if the stack is no tablet. */
    static String traitsOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        String s = data.copyTag().getStringOr(TAG, "");
        return s.isEmpty() ? null : s;
    }
}
