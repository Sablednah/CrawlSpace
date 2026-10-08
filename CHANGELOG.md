# Changelog

## 0.2.0 — 2026-10-08

For Minecraft 1.21.11, 26.1.2, 26.2 and 26.3. **On 26.3 it now needs NeoForge 26.3.0.58-beta or newer**; for 26.3.0.33-beta to 26.3.0.36-beta, stay on 0.1.0. Your config carries over unchanged.

- **Puzzle rooms.** Some levels turn a plain room into a maze of one-wide paths over the void. Step off the path and the void throws you back to a restart block at the centre. Paths lead to every doorway, and the deepest dead end holds a hoard chest. On the first levels nothing needs a jump.
- **Deeper puzzle rooms get harder.** From level 2, paths have gaps to jump, crumbling stone bricks that give way a moment after you step on them (and re-form a few seconds later), and big dripleaf that tips you off if you stand still.
- **Leap of faith** (level 3 and deeper): the whole floor shows void, and the path over it is invisible. Throw things to find it: what lands on the path stays put; what falls in comes back to you.
- **Pit traps.** A 3x3 patch of stone brick in a room cracks and crumbles under you in about half a second, into a four-deep pit of dripstone spikes. A ladder appears so you can climb out. Spotted, hinted and disarmed like the other traps.
- **With LegendQuest** (2.9.1 or newer), stepping off a puzzle path, or standing on a pit as it goes, is an Athletics (Strength) check: pass and you are back on the block you last stood on.
- `CrawlSpaceApi`: `Perception.recovers(player, depth)`, optional, for that check. Mods that registered before it keep working.
- Datapacks: new roles `puzzle_void` (end portal by default), `puzzle_restart` (crying obsidian), `puzzle_hidden` (barrier), `puzzle_crumble` and `pit_tile` (stone bricks).
- **Fixed:** a stair right outside a doorway, which could not be climbed either way; corridors now meet every doorway level. New dungeons only: ones already generated keep their stairs.

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
