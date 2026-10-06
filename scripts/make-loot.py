#!/usr/bin/env python3
"""Writes CrawlSpace's chest loot tables. Edit the tiers here and rerun; do not hand-edit the JSON.

Tier n is for levels 2n-1 and 2n (1 for levels 1-2, 2 for 3-4, ...); a hoard (treasure room or
lair) rolls one tier richer. 'supplies' fills barrels.

Every tier has a second, rare pool (Sable, 2026-10-06: "more enchanted and rare enchants like
mending, and smithing templates and armour trims"): mending books, rare books, armour trim
templates (rarer patterns deeper), the netherite upgrade from tier 4, and trimmed, enchanted
armour. Its empty entry sets how often a roll pays at all; the script prints the odds per tier
when it runs (about half the chests at tier 1, every chest from tier 5).
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

import random

def empty(weight):
    return {"type": "minecraft:empty", "weight": weight}

def book(weight, options):
    """A book that becomes an enchanted book with one of these enchantments, at a random level."""
    return {"type": "minecraft:item", "name": "minecraft:book", "weight": weight,
            "functions": [{"function": "minecraft:enchant_randomly",
                           "options": ["minecraft:" + o for o in options]}]}

def trimmed(name, weight, pattern, material, levels):
    """A piece of armour already trimmed, and enchanted."""
    return {"type": "minecraft:item", "name": "minecraft:" + name, "weight": weight, "functions": [
        {"function": "minecraft:set_components", "components": {
            "minecraft:trim": {"material": "minecraft:" + material, "pattern": "minecraft:" + pattern}}},
        {"function": "minecraft:enchant_with_levels", "levels": {"type": "minecraft:uniform", "min": levels[0], "max": levels[1]}}]}

RARE_BOOKS = ["unbreaking", "silk_touch", "fortune", "looting", "frost_walker", "soul_speed", "infinity",
              "feather_falling", "protection", "sharpness", "efficiency", "power", "respiration",
              "depth_strider", "thorns", "swift_sneak", "multishot", "piercing"]
TRIMS_COMMON = ["sentry", "dune", "coast", "wild", "tide"]
TRIMS_MID = ["snout", "rib", "spire", "wayfinder", "raiser", "shaper", "host", "flow", "bolt"]
TRIMS_RARE = ["ward", "vex", "eye", "silence"]
MATERIALS = ["gold", "emerald", "amethyst", "redstone", "lapis", "quartz", "copper", "diamond"]

def template(pattern, weight):
    return item(pattern + "_armor_trim_smithing_template", weight)

def rare_pool(tier):
    """Mending first, rare books, smithing templates, and trimmed enchanted armour: richer the deeper the tier."""
    rng = random.Random(1000 + tier)  # fixed, so regenerating gives the same tables
    entries = [book(1 + tier, ["mending"]), book(6, RARE_BOOKS)]
    for t in TRIMS_COMMON:
        entries.append(template(t, 2))
    if tier >= 2:
        for t in TRIMS_MID:
            entries.append(template(t, 1))
    if tier >= 3:
        for t in TRIMS_RARE:
            if t != "silence" or tier >= 4:
                entries.append(template(t, 1))
    if tier >= 4:
        entries.append(item("netherite_upgrade_smithing_template", 2 + (tier - 4) * 2))
    metal = "iron" if tier <= 3 else "diamond"
    levels = (5 + 5 * tier, 10 + 7 * tier)
    for piece in ["helmet", "chestplate", "leggings", "boots"]:
        pattern = rng.choice(TRIMS_COMMON + (TRIMS_MID if tier >= 2 else []) + (TRIMS_RARE if tier >= 4 else []))
        entries.append(trimmed(metal + "_" + piece, 2, pattern, rng.choice(MATERIALS), levels))
    # How often the rare pool pays at all: rarely on the first levels, every chest at the bottom.
    entries.append(empty({1: 30, 2: 18, 3: 8, 4: 3, 5: 0}[tier]))
    rolls = {1: (1, 1), 2: (1, 1), 3: (1, 2), 4: (1, 2), 5: (2, 3)}[tier]
    return {"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "entries": [e for e in entries if e.get("weight", 1) > 0]}

TIERS = {
    "tier1": table((3, 6), [
        item("bread", 15, 1, 3), item("apple", 8, 1, 3), item("arrow", 10, 4, 12), item("torch", 10, 4, 12),
        item("coal", 10, 2, 6), item("iron_ingot", 6, 1, 3), item("gold_nugget", 6, 2, 8), item("string", 6, 1, 4),
        item("bone", 6, 1, 4), item("stone_sword", 3, enchant=(1, 8)), item("leather_chestplate", 3, enchant=(1, 8)),
        item("iron_pickaxe", 1, enchant=(5, 12)), item("book", 3, enchant="random"), item("name_tag", 1), item("saddle", 1)]),
    "tier2": table((4, 7), [
        item("bread", 10, 2, 4), item("cooked_beef", 6, 1, 3), item("arrow", 8, 6, 16), item("iron_ingot", 10, 1, 4),
        item("gold_ingot", 5, 1, 3), item("redstone", 6, 2, 8), item("lapis_lazuli", 5, 2, 8),
        item("iron_sword", 3, enchant=(5, 15)), item("bow", 2, enchant=(5, 15)), item("iron_pickaxe", 2, enchant=(5, 15)),
        item("chainmail_helmet", 2, enchant=(5, 12)), item("chainmail_chestplate", 2, enchant=(5, 12)),
        item("book", 5, enchant=(10, 20)), item("golden_apple", 1), item("music_disc_13", 1), item("experience_bottle", 3, 1, 3)]),
    "tier3": table((4, 8), [
        item("iron_ingot", 10, 2, 5), item("gold_ingot", 8, 1, 4), item("diamond", 3, 1, 2), item("emerald", 4, 1, 3),
        item("iron_helmet", 2, enchant=(10, 20)), item("iron_chestplate", 2, enchant=(10, 20)), item("iron_sword", 2, enchant=(10, 22)),
        item("crossbow", 2, enchant=(10, 22)), item("diamond_pickaxe", 1, enchant=(10, 22)),
        item("book", 6, enchant=(15, 25)), item("golden_apple", 3), item("ender_pearl", 3, 1, 2),
        item("experience_bottle", 4, 2, 5), item("cooked_porkchop", 6, 1, 4), item("music_disc_cat", 1)]),
    "tier4": table((5, 8), [
        item("diamond", 6, 1, 3), item("emerald", 5, 2, 5), item("gold_block", 2), item("diamond_sword", 3, enchant=(20, 30)),
        item("diamond_chestplate", 2, enchant=(20, 30)), item("diamond_pickaxe", 2, enchant=(20, 30)),
        item("diamond_helmet", 2, enchant=(20, 30)), item("bow", 2, enchant=(20, 30)),
        item("book", 7, enchant=(25, 30)), item("golden_apple", 5), item("enchanted_golden_apple", 1),
        item("ender_pearl", 4, 1, 3), item("experience_bottle", 5, 4, 8), item("cooked_beef", 5, 2, 5)]),
    "tier5": table((6, 9), [
        item("diamond", 8, 2, 5), item("emerald", 6, 4, 10), item("netherite_scrap", 2, 1, 2),
        item("enchanted_golden_apple", 2), item("book", 7, enchant=(30, 39)), item("diamond_sword", 3, enchant=(30, 39)),
        item("diamond_helmet", 2, enchant=(30, 39)), item("diamond_chestplate", 2, enchant=(30, 39)),
        item("diamond_leggings", 2, enchant=(30, 39)), item("diamond_boots", 2, enchant=(30, 39)),
        item("totem_of_undying", 1), item("experience_bottle", 5, 6, 12), item("golden_apple", 5, 1, 3)]),
    "supplies": table((2, 4), [
        item("bread", 10, 1, 4), item("potato", 8, 2, 6), item("carrot", 8, 2, 6), item("coal", 8, 2, 8),
        item("torch", 8, 4, 16), item("arrow", 6, 4, 12), item("string", 5, 1, 4), item("stick", 5, 2, 8),
        item("iron_nugget", 5, 2, 9), item("flint", 4, 1, 3), item("oak_planks", 4, 4, 12)]),
}

for n in range(1, 6):
    TIERS["tier%d" % n]["pools"].append(rare_pool(n))

for name, t in TIERS.items():
    with open(os.path.join(OUT, name + ".json"), "w") as f:
        json.dump(t, f, indent=2)
        f.write("\n")
for n in range(1, 6):
    pool = TIERS["tier%d" % n]["pools"][1]
    total = sum(e["weight"] for e in pool["entries"])
    blank = sum(e["weight"] for e in pool["entries"] if e["type"] == "minecraft:empty")
    print("tier%d: a rare roll pays %.0f%% of the time, mending %.1f%%" % (
        n, 100.0 * (total - blank) / total, 100.0 * pool["entries"][0]["weight"] / total))
print("wrote", len(TIERS), "tables")
