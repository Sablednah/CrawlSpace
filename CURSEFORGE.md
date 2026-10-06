# CrawlSpace

**Procedural roguelike dungeons for NeoForge 1.21.11 and 26.x.**

If you played Roguelike Dungeons or Dungeon Crawl and wondered where that went on newer versions, this is the answer: deep, themed, procedurally planned dungeons under the world, waiting to be crawled.

## What you'll find

A building on the surface marks the way in. Depending on the biome it is a castle keep, a round tower, a stepped pyramid or a columned temple. A spiral stair goes down through levels that get darker and more dangerous the deeper you go:

- **Crypt**: sarcophagi, skulls and candlelit niches
- **Sunken Halls**: prismarine pillars, pools and moss
- **Old Mines**: timber frames, rails and barrels
- **Caverns**: stalagmites, roots and overgrowth
- **Deep Halls**: soul fire, chains and the worst of what lives down there

Every level is planned fresh. Rooms of all shapes are joined by corridors that run straight, curve, wind and ramp, so it never feels like a grid.

## Danger and reward

- Rooms wake their monsters as you come near. Spawners turn up from the first level and multiply deeper down.
- Every lair has a named boss with a boss bar.
- Hidden traps fire darts or gas. Spot one, and you can sneak and disarm it.
- Secret walls hide treasure rooms, and levers open locked doors.
- Loot gets better with depth: Mending books, rare enchantments, armour trim templates, the netherite upgrade, and trimmed, enchanted armour.

## Server-side

CrawlSpace runs on the server only. Players with an unmodded client can join and play everything.

## Settings

`config/crawlspace-server.toml` sets how common dungeons are, how deep they go, and whether players get hints for traps and secrets.

Operators can also use `/crawlspace build` to place a dungeon where they stand.

## Works with

- **CityWorld**: dungeons generate in CityWorld worlds, and cities leave room for the entrance.
- **RPG mods**: perception and disarm checks can come from a mod through `CrawlSpaceApi`. LegendQuest support is planned.
