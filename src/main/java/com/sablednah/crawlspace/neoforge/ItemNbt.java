package com.sablednah.crawlspace.neoforge;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Item data the 1.20.1 way: names, lore and our own flags in the stack's NBT,
 * where 1.20.5 and later have data components. The Forge 1.20.1 line only.
 */
final class ItemNbt {

    private ItemNbt() {
    }

    static void name(ItemStack stack, Component name) {
        stack.setHoverName(name);
    }

    static void lore(ItemStack stack, List<? extends Component> lines) {
        ListTag lore = new ListTag();
        for (Component c : lines) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(c)));
        }
        stack.getOrCreateTagElement("display").put("Lore", lore);
    }

    /** Merges our keys into the stack's tag. */
    static void data(ItemStack stack, CompoundTag tag) {
        stack.getOrCreateTag().merge(tag);
    }

    /** The stack's tag, or null: where our keys are. */
    static CompoundTag data(ItemStack stack) {
        return stack.getTag();
    }

    /**
     * The enchanted shimmer, with no enchantment: an entry no client can name,
     * and the enchantment line hidden. A stack with a non-empty enchantment list
     * shimmers on every client, vanilla included.
     */
    static void glint(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag list = new ListTag();
        CompoundTag e = new CompoundTag();
        e.putString("id", "crawlspace:glint");
        e.putShort("lvl", (short) 0);
        list.add(e);
        tag.put("Enchantments", list);
        tag.putInt("HideFlags", tag.getInt("HideFlags") | 1);
    }
}
