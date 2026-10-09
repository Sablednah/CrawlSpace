package com.sablednah.crawlspace.neoforge;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * A level's portcullis key: a vanilla tripwire hook with a name, a line of lore
 * and a tag a vanilla client never sees. The tag is what opens the portcullis,
 * so a hook renamed on an anvil opens nothing. One dungeon level, one key.
 */
public final class Keys {

    private static final String TAG = "crawlspace_key";

    private Keys() {
    }

    /** Which dungeon and level a key belongs to. */
    static String id(Site site, int level) {
        return site.origin().getX() + "," + site.origin().getY() + "," + site.origin().getZ() + "/" + level;
    }

    static ItemStack make(Site site, int level, String theme) {
        ItemStack key = new ItemStack(Items.TRIPWIRE_HOOK);
        key.set(DataComponents.CUSTOM_NAME, Component.literal(theme + " Key")
                .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
        key.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Opens the portcullis on level " + (level + 1) + ".")
                        .withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GRAY)))));
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG, id(site, level));
        key.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        key.set(DataComponents.RARITY, Rarity.UNCOMMON);
        key.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return key;
    }

    /** The id this stack opens, or null if it is not one of our keys. */
    static String idOf(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        String id = data.copyTag().getStringOr(TAG, "");
        return id.isEmpty() ? null : id;
    }

    /** The level (0-based) a key id names, or -1. */
    static int levelOf(String id) {
        int slash = id == null ? -1 : id.lastIndexOf('/');
        try {
            return slash < 0 ? -1 : Integer.parseInt(id.substring(slash + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
