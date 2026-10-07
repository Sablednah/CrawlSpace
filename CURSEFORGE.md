# CrawlSpace

![CrawlSpace](https://raw.githubusercontent.com/Sablednah/CrawlSpace/main/src/main/resources/crawlspace.png)

**Procedural roguelike dungeons for NeoForge 1.21.11 and 26.x.**

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

- **Monsters wake as you come near**: no endless spawning, but spawners turn up from the first level and multiply deeper down.
- **Every lair has a named boss** with a boss bar. Its minions never turn on it, so it will be waiting for you.
- **Traps under pressure plates and tripwires** fire darts or poison gas, and some plates are decoys. Faint red sparks give the real ones away. Break a plate or sneak-use it to try to disarm it, but it might go off in your face.
- **Secret walls** hide treasure rooms, and **levers** open the iron doors that lock off part of each level.
- **Loot improves with depth**: Mending books, rare enchantments, armour trim templates, the netherite upgrade, and trimmed, enchanted armour.

![A cavern pool with its spawner](https://raw.githubusercontent.com/Sablednah/CrawlSpace/main/docs/images/cavern-pool.jpg)

![The same cavern, as you will meet it: bring a torch](https://raw.githubusercontent.com/Sablednah/CrawlSpace/main/docs/images/cavern-pool-dark.jpg)

## Play it with LegendQuest

With **LegendQuest** (2.9.0 or newer) installed, your character finds the traps. A trap's plate stays hidden until a Wisdom check spots it, harder the deeper you are, and disarming is a Dexterity check. Dwarves are good with traps. You see your roll when you spot something, and nothing at all when you don't.

![Planned, not tiled: every level of one dungeon](https://raw.githubusercontent.com/Sablednah/CrawlSpace/main/docs/images/plan-sheet.png)

## Server-side

CrawlSpace runs on the server. Players with an unmodded client can join and play everything.

## Make it yours

- **Config** (`config/crawlspace-server.toml`): how common dungeons are, how deep they go, whether players get hints, and the chance of disarming a trap.
- **Datapacks**: change any theme's blocks, monsters and boss, restyle entrance buildings, or add new entrance styles for the biomes you choose. `/crawlspace export` writes the built-in themes out as a datapack to start from.
- **Loot tables** are ordinary datapack loot tables, so packs can change those too.
- **Operators** can place a dungeon where they stand with `/crawlspace build`.

## Works with

- **LegendQuest**: perception and disarm rolls, as above.
- **CityWorld**: dungeons generate in CityWorld worlds, and cities keep clear of the entrance.
- **Other RPG mods** can supply their own checks through `CrawlSpaceApi`.

Minecraft 1.21.11, 26.1.2, 26.2 and 26.3 on NeoForge. MIT licensed.
