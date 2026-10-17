# Making CrawlSpace your own: datapacks

Everything here is ordinary datapack JSON. Drop a pack in a world's
`datapacks` folder and `/reload`. Dungeons generated from then on use it.

The quickest start is `/crawlspace export`. It writes everything in force
(every theme, entrance and dungeon type, with every number filled in) to
`<world>/crawlspace-export/` as a complete datapack. Copy that, keep only what
you change, and delete the rest.

`examples/apocalypse` is a worked pack for a zombie-apocalypse world: research
bases, an old mansion in the woods and fallout bunkers, with the fantasy
dungeons switched off.

There are three kinds of file:

| Folder | What it is |
|---|---|
| `data/<ns>/crawlspace/dungeon/<id>.json` | A **dungeon type**: which themes, top to bottom, and where it is found |
| `data/<ns>/crawlspace/theme/<name>.json` | A **theme**: one level's blocks, layout, furnishing, monsters, bosses and loot |
| `data/<ns>/crawlspace/entrance/<style>.json` | An **entrance style**: the tower on the surface |

Every file only needs to say what it changes. Anything left out keeps what it
was. Anything unknown (a block, a mob, a role, a theme) is skipped with a
warning in the server log that names the file and the key, so a typo costs
one setting, not the pack.

## Dungeon types

Each dungeon picks a type from its own seed, weighted, among the types that
may stand in its biome. So one world can hold crypts, labs and bunkers side by
side.

```json
{
  "name": "Research Base",
  "weight": 10,
  "biomes": ["minecraft:plains", "#minecraft:is_badlands"],
  "exclusive": false,
  "entrance": "bunker",
  "levels": ["Research Lab", "Research Lab", "Containment", "Maintenance Tunnels"],
  "max_levels": 6
}
```

| Key | Meaning | Default |
|---|---|---|
| `name` | What players and `/crawlspace` call it | the file name in title case |
| `weight` | How often it is picked against the others. **0 turns it off** | 10 |
| `levels` | Themes by depth, the top first. Past the end, the last one repeats | required |
| `biomes` | Biome ids or `#tags` it may stand in. Without it, it may stand anywhere | anywhere |
| `exclusive` | With `biomes`: in those biomes, only exclusive types are found | false |
| `entrance` | An entrance style for its tower. Without it, the biome picks | by biome |
| `max_levels` | The most levels it goes down (the config's maximum still applies) | the config's |

The built-in type is **`classic`**: Crypt, Sunken Halls, Old Mines, Caverns,
Deep Halls. A file called `classic.json` (any namespace) changes it, and
`{"weight": 0}` turns the fantasy dungeons off.

If every type is off, or none may stand in a biome, no dungeon is placed there.

## Themes

A theme is how one level looks and plays. The five built-in themes can be
changed, and **a new name makes a new theme**: `research_lab.json`, or any file
with `"theme": "Research Lab"`. Names match however they are written, so
"Research Lab", `research_lab` and "research-lab" are the same theme.

A new theme starts as a copy of its **`base`** (the Crypt if it names none),
and the base can be another new theme. `"base": "Research Lab"` in
`containment.json` gives you the lab with a few changes. The built-in themes
cannot take a base.

```json
{
  "base": "Deep Halls",
  "blocks":   { "wall": "minecraft:white_concrete", "door": "minecraft:birch_door" },
  "layout":   { "shapes": { "rect": 6, "hall": 3, "cave": 0 }, "narrow_chance": 0.2 },
  "dressing": { "flavour": "old_mines", "clutter": ["cauldron", "lectern", "barrel"] },
  "mobs":     [ { "type": "minecraft:zombie", "weight": 5 } ],
  "bosses":   [ { "type": "minecraft:zombie", "name": "Subject Zero", "scale": 2.0, "health": 120 } ],
  "loot":     { "supplies": "minecraft:chests/village/village_weaponsmith" }
}
```

### `blocks`: what everything is made of

Each role takes a block id, or a weighted list:

```json
"wall": [ { "block": "minecraft:white_concrete", "weight": 6 }, { "block": "minecraft:light_gray_concrete", "weight": 2 } ]
```

The roles:

- **Structure:** `wall`, `floor`, `corridor_floor`, `ceiling`, `pillar`,
  `step` (stairs), `landing`, `newel` (the stair's core), `door`.
- **Trim:** `cove` (stairs along the ceiling), `dado` (the rail above
  panelling), `panel`, `pilaster`, `floor_accent` (the floor border),
  `floor_inlay` (the medallion), `accent`, `railing` (a wall block), `beam`,
  `support` (a fence).
- **Furniture:** `carpet`, `rug`, `banner` (a wall banner), `table` (a fence),
  `table_top` (a pressure plate), `chair` (stairs), `throne` (stairs),
  `altar`, `sarcophagus`, `pedestal`, `brazier` (a campfire), `candle`,
  `torch` (a wall torch), `light` (a lantern, hung from the ceiling).
- **Traps and puzzles:** `secret_wall`, `trap_plate`, `trap_wire`,
  `portcullis`, `portcullis_sill`, `window_bars`, `pit_tile`, `ice_tile`,
  `puzzle_void`, `puzzle_restart`, `puzzle_hidden`, `puzzle_crumble`.

`/crawlspace export` lists every role a theme has, with what it is now.

Things to know:

- **Make `secret_wall` close to the walls, but not one of them.** It is how a
  sharp-eyed player finds a secret. If it is in the `wall` list, secrets
  cannot be found by looking.
- **Doors must open by hand.** An iron door needs redstone, and nobody will
  have any.
- A block that lacks a property the role uses (a plain block where a stair
  was) still goes in, without it.
- The theme's walls keep the world out. Walls of planks or wool are fine, but
  they burn like planks and wool.

### `layout`: the shape of a level

```json
"layout": {
  "shapes":    { "rect": 4, "rounded": 1, "circle": 0.5, "octagon": 2, "cross": 2, "l": 1, "cave": 0, "hall": 1.5 },
  "corridors": { "straight": 6, "angled": 2, "winding": 0, "curved": 1 },
  "narrow_chance": 0.35,
  "height_spread": 0,
  "pool_chance": 0.0,
  "room_scale": 1.0
}
```

| Key | Meaning | Range |
|---|---|---|
| `shapes` | How often each room shape is drawn (weights; leave out any to keep them) | at least one above 0 |
| `corridors` | How often each corridor style is drawn | at least one above 0 |
| `narrow_chance` | Chance a corridor is one wide instead of three | 0 to 1 |
| `height_spread` | How far a room's floor may sit above or below the level's | 0 to 2 |
| `pool_chance` | Chance a shrine or cave room gets a pool | 0 to 1 |
| `room_scale` | Multiplies room sizes | 0.8 to 1.3 |

Numbers outside the range are brought inside it. Every shape and corridor
style on its own, at both ends of these ranges, has been planned and checked
sealed by the test suite (`ThemeKnobsTest`).

**A dungeon keeps the layout it was born with.** Each one saves a copy of its
themes' layout and furnishing numbers. Editing or removing a pack later
changes what is harmless (blocks, monsters, bosses, loot) and never moves a
wall in a dungeon that already exists.

### `dressing`: how rooms are furnished

```json
"dressing": {
  "flavour": "crypt",
  "built": true, "finished": true, "columns": true,
  "cobwebs": 0.4, "shelves": 0.25,
  "clutter": ["skull", "candles", "pot", "floor_lantern", "bones", "cobweb"],
  "overgrowth": 0.2, "roots": false,
  "niche_statues": 0.5, "beams": true, "timber": false, "panels": 0.5
}
```

| Key | Meaning |
|---|---|
| `flavour` | What fills an ordinary room's middle: `crypt` (sarcophagi, skulls), `sunken_halls` (a table set, moss), `old_mines` (a table set, barrels), `caverns` (stalagmites, moss), `deep_halls` (a brazier, chains), or `none` |
| `built` | Masonry: skirting, door frames and wall niches |
| `finished` | Floor borders, coving, panelling and arches. Off for raw caves |
| `columns` | Grand rooms get columns standing out from their walls |
| `cobwebs` | Chance of a cobweb in each corner |
| `shelves` | Chance an ordinary room gets bookshelves |
| `clutter` | What lies about by the walls. Repeat one to make it more common. Any of `skull`, `candles`, `pot`, `floor_lantern`, `bones`, `cobweb`, `cauldron`, `moss`, `barrel`, `anvil`, `grindstone`, `stalagmite`, `mushroom`, `lectern`. `[]` for none |
| `overgrowth` | Chance a room is overgrown with vines, moss and plants |
| `roots` | Overgrown rooms also get roots hanging from the ceiling |
| `niche_statues` | Chance an ordinary room lines its niches with statues (lairs and shrines always do) |
| `beams` | Ceiling beams across bigger rooms |
| `timber` | Mine-style: timber posts, no floor medallion, rails in wide corridors |
| `panels` | Chance an ordinary room or hall is panelled |

Chances are 0 to 1.

### `mobs`: what wakes in its rooms

```json
"mobs": [ { "type": "minecraft:zombie", "weight": 5, "armour": true }, "minecraft:husk" ]
```

The list replaces the theme's own. Any entity id works, including modded
ones. `armour` decides whether they are given armour by depth; it defaults to
on for humanoids that show it.

### `bosses`: who holds the lair

```json
"bosses": [
  { "type": "minecraft:zombie", "name": "Subject Zero", "weapon": "minecraft:air", "bar": "red",
    "scale": 2.0, "health": 120, "damage": 9, "speed": 0.9 },
  { "type": "minecraft:zombie_villager", "name": "the Head Researcher", "weapon": "minecraft:iron_sword",
    "scale": 1.3, "powers": ["summon"], "minion": "minecraft:zombie_villager" }
]
```

The list replaces the theme's bosses, and each lair takes one of them from
the dungeon's own dice. Every field is optional:

| Key | Meaning |
|---|---|
| `type` | The entity |
| `name` | Its name and boss-bar title |
| `weapon` | An item for its hand; `minecraft:air` for none |
| `bar` | The boss bar's colour: `pink`, `blue`, `red`, `green`, `yellow`, `purple`, `white` |
| `scale` | Its size (vanilla's scale attribute; 1 is normal) |
| `health` | Its maximum health, plus 8 per level down; 0 keeps the mob's own plus the usual boss bonus |
| `damage` | Its attack damage, plus half a point per level down; 0 keeps its own |
| `speed` | Times its own speed |
| `powers` | `blink` (teleports to you) and `summon` (calls its `minion`) |
| `minion` | What `summon` calls up |
| `follower` | A pack of these stands with it, instead of the theme's monsters |
| `follower_scale`, `follower_health`, `follower_damage` | ...and how big and strong they are |

A single `"boss": {...}` (the older form) changes only the theme's first boss,
field by field, and makes it the only one.

### `loot`: what its containers hold

```json
"loot": {
  "chests":   "mypack:lab/crate",
  "hoard":    ["mypack:t2", "mypack:t3", "mypack:t4", "mypack:t5", "mypack:t5"],
  "supplies": "minecraft:chests/village/village_weaponsmith"
}
```

| Key | Containers | Default |
|---|---|---|
| `chests` | Chests, by tier 1 to 5 | `crawlspace:chests/tier1` to `tier5` |
| `hoard` | Treasure-room and lair chests, by tier | one tier richer than `chests` |
| `supplies` | Barrels | `crawlspace:chests/supplies` |

Each takes one table for every tier, or a list of five (a shorter list
repeats its last). Levels 1 and 2 are tier 1, 3 and 4 tier 2, and so on to
5; a dark level is a tier richer.
CrawlSpace's own tables can also be overridden like any loot table.

## Entrance styles

The tower on the surface. A new file name makes a new style, starting from
`stone`.

```json
{
  "biomes": ["minecraft:basalt_deltas", "#minecraft:is_badlands"],
  "blocks": { "wall": "minecraft:polished_basalt", "top": "minecraft:blackstone" },
  "designs": { "keep": 1, "round": 3, "pyramid": 0, "temple": 0 }
}
```

The roles are `wall`, `floor`, `top`, `door`, `trim`, `stair`, `window`,
`pillar` and `roof`. `designs` weights the four tower shapes. `biomes` makes
this style the one used in those biomes, ahead of the built-in rules. A style
only used by a dungeon type's `entrance` does not need `biomes`.

## Commands for pack makers

| Command | What |
|---|---|
| `/crawlspace types` | Every dungeon type in force: weight, biomes, entrance and themes |
| `/crawlspace build <levels> <seed> <type>` | Build one of a type where you stand |
| `/crawlspace showcase <levels> <seed> <type>` | One of everything, in a type's themes |
| `/crawlspace here` | The dungeon you are standing in: its type, seed and themes |
| `/crawlspace summon boss <theme> [which]` | A theme's boss, to look at |
| `/crawlspace export` | Everything in force, as a datapack to edit |
