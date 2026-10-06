#!/usr/bin/env python3
"""Writes CrawlSpace's chest loot tables. Edit the tiers here and rerun; do not hand-edit the JSON.

Tier n is for levels 2n-1 and 2n (1 for levels 1-2, 2 for 3-4, ...); a hoard (treasure room or
lair) rolls one tier richer. 'supplies' fills barrels.
"""
import json, os

OUT = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/data/crawlspace/loot_table/chests')

def item(name, weight, lo=1, hi=1, enchant=None):
    e = {"type": "minecraft:item", "name": "minecraft:" + name, "weight": weight}
    fns = []
    if hi > 1:
        fns.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if enchant == "random":
        fns.append({"function": "minecraft:enchant_randomly"})
    elif enchant:
        fns.append({"function": "minecraft:enchant_with_levels", "levels": {"type": "minecraft:uniform", "min": enchant[0], "max": enchant[1]}})
    if fns:
        e["functions"] = fns
    return e

def table(rolls, entries):
    return {"type": "minecraft:chest", "pools": [{"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "entries": entries}]}

TIERS = {
    "tier1": table((3, 6), [
        item("bread", 15, 1, 3), item("apple", 8, 1, 3), item("arrow", 10, 4, 12), item("torch", 10, 4, 12),
        item("coal", 10, 2, 6), item("iron_ingot", 6, 1, 3), item("gold_nugget", 6, 2, 8), item("string", 6, 1, 4),
        item("bone", 6, 1, 4), item("stone_sword", 3), item("leather_chestplate", 3), item("book", 2, enchant="random"),
        item("name_tag", 1), item("saddle", 1)]),
    "tier2": table((4, 7), [
        item("bread", 10, 2, 4), item("cooked_beef", 6, 1, 3), item("arrow", 8, 6, 16), item("iron_ingot", 10, 1, 4),
        item("gold_ingot", 5, 1, 3), item("redstone", 6, 2, 8), item("lapis_lazuli", 5, 2, 8), item("iron_sword", 3),
        item("chainmail_helmet", 2), item("chainmail_chestplate", 2), item("book", 4, enchant="random"),
        item("golden_apple", 1), item("music_disc_13", 1), item("experience_bottle", 3, 1, 3)]),
    "tier3": table((4, 8), [
        item("iron_ingot", 10, 2, 5), item("gold_ingot", 8, 1, 4), item("diamond", 3, 1, 2), item("emerald", 4, 1, 3),
        item("iron_helmet", 2, enchant=(5, 15)), item("iron_chestplate", 2, enchant=(5, 15)), item("iron_sword", 2, enchant=(5, 15)),
        item("book", 5, enchant=(10, 20)), item("golden_apple", 3), item("ender_pearl", 3, 1, 2),
        item("experience_bottle", 4, 2, 5), item("cooked_porkchop", 6, 1, 4), item("music_disc_cat", 1)]),
    "tier4": table((5, 8), [
        item("diamond", 6, 1, 3), item("emerald", 5, 2, 5), item("gold_block", 2), item("diamond_sword", 2, enchant=(15, 30)),
        item("diamond_chestplate", 2, enchant=(15, 30)), item("diamond_pickaxe", 2, enchant=(15, 30)),
        item("book", 6, enchant=(20, 30)), item("golden_apple", 5), item("enchanted_golden_apple", 1),
        item("ender_pearl", 4, 1, 3), item("experience_bottle", 5, 4, 8), item("cooked_beef", 5, 2, 5)]),
    "tier5": table((6, 9), [
        item("diamond", 8, 2, 5), item("emerald", 6, 4, 10), item("netherite_scrap", 2, 1, 2),
        item("enchanted_golden_apple", 2), item("book", 6, enchant=(30, 39)), item("diamond_sword", 3, enchant=(25, 39)),
        item("diamond_helmet", 2, enchant=(25, 39)), item("diamond_chestplate", 2, enchant=(25, 39)),
        item("totem_of_undying", 1), item("experience_bottle", 5, 6, 12), item("golden_apple", 5, 1, 3)]),
    "supplies": table((2, 4), [
        item("bread", 10, 1, 4), item("potato", 8, 2, 6), item("carrot", 8, 2, 6), item("coal", 8, 2, 8),
        item("torch", 8, 4, 16), item("arrow", 6, 4, 12), item("string", 5, 1, 4), item("stick", 5, 2, 8),
        item("iron_nugget", 5, 2, 9), item("flint", 4, 1, 3), item("oak_planks", 4, 4, 12)]),
}

for name, t in TIERS.items():
    with open(os.path.join(OUT, name + ".json"), "w") as f:
        json.dump(t, f, indent=2)
        f.write("\n")
print("wrote", len(TIERS), "tables")
