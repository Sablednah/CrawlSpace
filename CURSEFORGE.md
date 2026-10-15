# CrawlSpace

![CrawlSpace](https://raw.githubusercontent.com/Sablednah/CrawlSpace/main/src/main/resources/crawlspace.png)

**Procedural roguelike dungeons for NeoForge 1.21.11 and 26.x, and Forge 1.20.1.**

Played Roguelike Dungeons or Dungeon Crawl, and wondered where they went on newer versions? CrawlSpace is the answer: deep, themed, procedurally planned dungeons under the world, waiting to be crawled.

![A whole dungeon, cut away: every level under the spiral stair](https://raw.githubusercontent.com/Sablednah/CrawlSpace/main/docs/images/cutaway-whole-dungeon.jpg)

## What you'll find

A building on the surface marks the way in. Depending on the biome it is a castle keep, a round tower, a stepped pyramid or a columned temple, built in that biome's materials. A spiral stair takes you down through levels that get darker and more dangerous the deeper you go:

![The castle keep](https://raw.githubusercontent.com/Sablednah/CrawlSpace/main/docs/images/tower-keep.jpg)

![The pyramid](https://raw.githubusercontent.com/Sablednah/CrawlSpace/main/docs/images/tower-pyramid.jpg)

![The temple](https://raw.githubusercontent.com/Sablednah/CrawlSpace/main/docs/images/tower-temple.jpg)

![The round tower](https://raw.githubusercontent.com/Sablednah/CrawlSpace/main/docs/images/tower-round.jpg)

- **Crypt**: sarcophagi, skulls and candlelit niches
- **Sunken Halls**: prismarine pillars, pools and moss
- **Old Mines**: timber frames, rails and barrels
- **Caverns**: stalagmites, roots and overgrowth
- **Deep Halls**: soul fire, chains and the worst of what lives down there

![Looking down from the Crypt into the Sunken Halls](https://raw.githubusercontent.com/Sablednah/CrawlSpace/main/docs/images/cutaway-crypt-over-sunken-halls.jpg)

Every level is planned fresh. Rooms of every shape loop round and cross each other, and the corridors between them run straight, curve, wind and ramp, so it never feels like a grid. Rooms are dressed for what they are: panelled halls with arches and statue niches, guard rooms with mess tables, shrines with altars, and lairs.

![The Old Mines](https://raw.githubusercontent.com/Sablednah/CrawlSpace/main/docs/images/old-mines-hall.jpg)

![A Caverns level: no grid, corridors curve and wind](https://raw.githubusercontent.com/Sablednah/CrawlSpace/main/docs/images/cutaway-caverns.jpg)

## Danger and reward

- **Monsters wake as you come near**: no endless spawning. Spawners turn up from the first level, multiply deeper down, and burn out after a while, so a dungeon cannot be farmed for ever.
- **Every lair has a named boss** with a boss bar, and champions with powers of their own. Some lairs sleep: sneak, and you can take the hoard from under their noses. Some keep a prisoner, freed once the beast is dead, with a gift and a true rumour. And some lairs are a pretender's, with the real one behind a secret wall.
- **Encounters with a twist**: survival rooms that bar their doors until you have held out for a minute, numbered guards to be killed in order, sentries who sound the alarm if you are seen, mimics, and a gelatinous cube gliding down a corridor with its last meals inside.
- **Traps** under pressure plates and tripwires: darts, gas, alarms, webs, falling stalactites, frost, fire and ambushes, and pit traps that crumble under you. Some plates are decoys; tells give the real ones away. Portcullises drop behind you and one-way doors close.
- **Puzzle rooms on nearly every level**: mazes of narrow paths over the void, ice boards to cross in one go round the pillars, and teleport mazes. A solved puzzle blesses you.
- **Vaults** where you may take only one treasure, token vaults whose guards carry the key, secret walls and locked doors with their keys, and the thing you should not touch.
- **Loot improves with depth**: Mending books, rare enchantments, armour trim templates, the netherite upgrade, and trimmed, enchanted armour.

## Objective rooms

Big set pieces from the old dungeon card games, each with a job to do: the Fighting Pit, the Firechasm with its crumbling bridge and its dragon, the Fountain of Light, the Tomb Chamber and the Idol Chamber. Each has two jobs so far, guards from the level below, a reward chest, replay tablets, and a way out to the surface built into the room itself.

## The bottom, and coming back

- **The finale**: the deepest lair's boss sleeps in a sunken arena until you set the seals you have won from each level's lair or puzzle on its altar, and offer gold. Beat it, and the altar can call it back, stronger each time.
- **Restocking**: every 12 real hours a dungeon fills again, rooms, traps and chests, and broken chests and doors are mended. Never while someone else is inside.
- **Replay tablets** from objective rooms wake a dungeon again, harder, with a boon and a bane for everyone in it: more XP but tougher monsters, richer loot but more of them. Collect them, trade them, look for a good combination.

![A cavern pool with its spawner](https://raw.githubusercontent.com/Sablednah/CrawlSpace/main/docs/images/cavern-pool.jpg)

![The same cavern, as you will meet it: bring a torch](https://raw.githubusercontent.com/Sablednah/CrawlSpace/main/docs/images/cavern-pool-dark.jpg)

## Play it with LegendQuest

With **LegendQuest** (2.10.0 or newer) installed, your character finds the traps. A trap's plate stays hidden until a Wisdom check spots it, harder the deeper you are, and disarming is a Dexterity check. Dwarves are good with traps. Slipping off a puzzle path or a crumbling bridge is an Athletics or Dexterity check to catch yourself. Intelligence spots a mimic, Sleight of Hand draws a chest's needle, Wisdom reads a trap's tell and a shrine's omen, and Stealth slips past a sentry. You see your roll when you spot something, and nothing at all when you don't.

![Planned, not tiled: every level of one dungeon](https://raw.githubusercontent.com/Sablednah/CrawlSpace/main/docs/images/plan-sheet.png)

## Server-side

CrawlSpace runs on the server. Players with an unmodded client can join and play everything. Inside a dungeon its walls cannot be dug round: the way down is through it.

## Make it yours

- **Config** (`config/crawlspace-server.toml`): how common dungeons are, how deep they go, whether players get hints, and the chance of disarming a trap.
- **Datapacks**: change any theme's blocks, monsters and boss, restyle entrance buildings, or add new entrance styles for the biomes you choose. `/crawlspace export` writes the built-in themes out as a datapack to start from.
- **Loot tables** are ordinary datapack loot tables, so packs can change those too.
- **Operators** can place a dungeon where they stand with `/crawlspace build`, see everything on every level with `/crawlspace info full`, and build `/crawlspace showcase` (one of everything) or `/crawlspace devshowcase` (every objective room in a row).

## Works with

- **LegendQuest**: perception and disarm rolls, as above.
- **CityWorld**: dungeons generate in CityWorld worlds, and cities keep clear of the entrance.
- **Other RPG mods** can supply their own checks through `CrawlSpaceApi`.

Minecraft 1.21.11, 26.1.2, 26.2 and 26.3 on NeoForge, and 1.20.1 on Forge 47.4 or newer. On 26.3, NeoForge 26.3.0.58-beta or newer. On 1.20.1, which predates mob scaling, bosses and their packs are normal size. MIT licensed.
