# Changelog

## 0.1.0 — 2026-10-07

The first version.

- **Dungeons generate with the world.** An entrance building leads down a spiral stair to themed levels: Crypt, Sunken Halls, Old Mines, Caverns and Deep Halls. The building is a keep, a round tower, a pyramid or a temple, chosen and dressed by biome.
- **Levels are planned, not tiled.** Each level is a loop of rooms with rooms in its middle, branches and shortcuts. Corridors run straight, at angles, curved or winding, and ramp between rooms with stairs on every step. Every level is checked: everything reachable, and nothing needs a jump.
- **Monsters by theme and depth.** Rooms wake their monsters when you approach, spawners appear from the first level and multiply deeper down, corridors hold ambushes, and lairs have named bosses with boss bars. Natural monster spawning is off inside dungeons.
- **Traps, secrets and locks.** Dart and gas traps sit under pressure plates and tripwires, among decoys that do nothing. Break a plate or wire, or sneak-use it, to try to disarm it (75% by default, `play.disarmChance`); fail and it goes off. There are also secret walls, and a lever that opens its level's locked iron doors. Pits drop to the level below.
- **With LegendQuest** (2.9.0 or newer), a trap's plate or wire stays hidden until a character notices it with a Wisdom check, and disarming is a Dexterity check.
- **Datapacks** can change any theme's blocks, monsters and boss, change entrance styles and add new ones for chosen biomes. `/crawlspace export` writes the built-in ones as a datapack to edit.
- **Loot** in five tiers by depth, with a rare pool: Mending, rare books, armour trim templates, the netherite upgrade, and trimmed, enchanted armour.
- **Boss bars** come back after a restart, and a room's monsters never fight each other, so a lair's boss is waiting for you rather than killed by its own minions.
- **Hints** for players without a perception skill: secret walls in a tell-tale block, and faint particles near traps, secret doors and treasure. `play.hints` sets this to AUTO, ALWAYS or NEVER.
- **`CrawlSpaceApi`** lets an RPG mod supply its own perception and disarm checks.
- **CityWorld:** dungeons are allowed by default, and cities keep clear of the entrance.
- **Commands** (operators): `/crawlspace build`, `goto`, `info`, `undo`, `cancel`, `export`, `perception`.
- **Config:** spacing (36), separation (16), max and min levels, hints, disarm chance.
