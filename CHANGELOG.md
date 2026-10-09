# Changelog

## 0.3.0 — unreleased

- **Dungeons can no longer be dug round.** In survival, the dungeon's walls, floors, ceilings, stairs, tower, locked doors and puzzle rooms will not break: the way down is through it. Hitting them says so: "The dungeon's stonework holds. Find another way." Chests, spawners, pots, cobwebs, furniture, wooden doors, trap plates and anything you placed yourself still break as usual. Creative is unaffected.
- Explosions still hurt and still break the dressing, but not the walls. Pistons cannot move them, and blocks that mining machines remove without the usual break event are put back after a few seconds.
- No building over a puzzle room's void.
- **Vaults.** Some treasure rooms hold three treasures on three pedestals. Take one, and the cages slam shut on the others.
- **The dungeon clock.** Every 45 seconds the dungeon rolls a die (`play.eventSeconds`, 0 for never), and on a 1 something happens: a wandering band you hear coming and from where, a sound in the dark, the light snuffed out, a swarm, a curse, a ghostly knight's warning, a stranger who will trade, falling rocks, or something glinting at your feet. Fighting is noisy and makes it likelier. `/crawlspace event <name>` to try one.
- **Solving a puzzle room blesses you**: opening its hoard gives a buff for six minutes.
- **Spawners burn out** after 24 monsters (`play.spawnerUses`, 0 for never), so a dungeon is not a farm.
- **More traps**, deeper down: alarms that wake the rooms around, webs, falling stalactites, freezing mist, fire, and ambushes. Spotted and disarmed like the others. `/crawlspace trap <kind>` to try one.
- **Level feelings.** Arriving on a level now shows its number and theme, and sometimes its mood: hollow walls (more secrets), damp air (pools everywhere), darkness (no lanterns, better loot), many feet (more monsters), clicking floors (twice the traps), or something large hunting the halls. A hunter is an elite that tracks you down, room by room, and you hear it coming.
- **Elites.** Now and then a dungeon monster is an elite, its affixes in its name: Venomous, Frenzied, Armoured, Hulking, Blinking, Burning, Splitting, Vampiric, two of them deeper down. They are tougher, and drop loot and extra experience.
- **New bosses**, chosen per lair: the Brood Queen, a silverfish five times the size of a normal one who births her brood; the Gnawing Mite, a giant endermite that blinks to you; and the Wardling Matriarch with her pack of tiny wardens, blind and hunting by sound. Every boss is enraged at half health.
- With ZombieMod installed, a dungeon's ordinary zombies may be ZombieMod's own kinds; its bosses never are.
- `/crawlspace summon boss <theme> [which]` and `/crawlspace summon elite [depth]` to try them out.
- **Works with Lootr** for per-player loot: every dungeon chest and barrel becomes a Lootr container, so each player gets their own. The key chest stays an ordinary chest, since there is one key per lock.
- **Portcullises.** On some levels the locked door is a portcullis, and its key is in a chest in the key room: a named key that opens that level's portcullis and no other. Deeper down, a portcullis may slam shut behind you as you walk into a room. A winch inside raises it, and it lifts on its own after a couple of minutes. The scored sill under the arch gives it away, and with LegendQuest you can spot it and jam it like any other trap.
- **One-way doors.** Some shortcuts are an iron door with its lever on one side only: you find the way back from the far end and open it from there. From the near side it will not open, and says so.
- **Rooms you can only fall into.** Some rooms have no way in but a pit from the level above: from outside, an iron door that will not open; drop in, and a lever by the door lets you out.
- **A finale.** The lair at the bottom of a dungeon is now often a sunken arena: a gallery round the walls, and stairs down into a pit twice the usual height where the boss waits, with a second hoard at the far end. The reason to go all the way down.
- **Windows.** Here and there, a corridor passing close to a lair, treasure room or shrine has iron bars through the wall: you see what is in there long before you find the way in.
- Fixed: a stair's railing could cover a trap plate, or cut a small room off from its own door.
- Fixed: features from neighbouring chunks (tuff, gravel, clay, moss, ore, water) could spill into a dungeon's walls as the world generated. New chunks of a dungeon are now put right as they load.
- **All of it is optional**: `[protection] enabled = false` in `crawlspace-server.toml` makes dungeons ordinary minable blocks again. There are separate switches for explosions and repair, and changes apply without a restart.

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
