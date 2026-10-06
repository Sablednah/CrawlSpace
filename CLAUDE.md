# CLAUDE.md

Guidance for Claude Code working in this repository.

## What this is

**CrawlSpace**: procedural roguelike dungeons for NeoForge. A tower pokes out
of the ground, a spiral stair goes down, and each level below is a planned
network of rooms and corridors, themed by depth and harder the further down.

*Tagline:* "Procedural roguelike dungeons for NeoForge — for everyone who
misses Roguelike Dungeons and Dungeon Crawl." Name the old mods once, in prose,
in the store copy and on the website, never as keyword stuffing. Searching for
"crawl" also turns up crawling-posture mods, so the copy has to say "dungeon"
early.

It started (Sable, 2026-10-05) to fill the gap left by those older mods, none
of which reached 26.x. The design notes it grew from are in LegendQuest's
`docs/IDEAS.md` ("A roguelike dungeon for the fantasy setting"). **It works
without LegendQuest.** With LegendQuest it will gain perception and disarm
rolls; with StoryTeller, a games master. Both plug in through an
inward-pointing seam (LegendQuest's `PartyVoice`/`VoiceSupport` pattern), and
CrawlSpace never imports either.

**Server-side, vanilla first.** Everything is ordinary blocks and vanilla
entities, so an unmodded client can play: `displayTest="IGNORE_ALL_VERSION"`.

## Where it stands (2026-10-06, afternoon)

**Dungeons generate with the world, and their levers, secret walls and traps
work.** `/crawlspace build` still makes one on demand. Everything below was
seen on the Vivo rig.

- **Worldgen:** `crawlspace:dungeon`, a structure set whose spacing comes from
  the config (see "Worldgen"). The entrance tower is dressed for its biome.
  `/locate structure crawlspace:dungeon` finds them.
- **Triggers:** a floor lever in the KEY room opens the level's locked iron
  doors, right-clicking a secret wall crumbles it, and hidden trap tiles fire
  darts or gas, once each (see "Triggers").
- **Inner rooms:** one to three rooms inside each loop, with paths across it,
  so a level no longer reads as a ring.
- `./gradlew test` plans thousands of dungeons, checks every one, proves the
  blueprint is sealed and the triggers are wired, and draws sheets to
  `build/plan-renders/*.png`, traps included. **Look at the sheets after any
  planner change.**

**Populated and dressed (2026-10-06, evening):** rooms wake their monsters,
lairs have bosses with boss bars, chests and barrels roll loot by depth,
guard rooms may hold a spawner, and every room is dressed by role and theme.
See "Population" and "Dressing".

Not yet:
- Themes, bestiary, entrance styles and dressing as data. They are
  hard-coded while Sable judges the look.
- Visible tripwires and pressure plates. The trigger system takes them as
  they are (another `Trigger.Kind` and a block in the blueprint); only the
  invisible tiles exist so far.
- LegendQuest's perception check for traps and secret walls. Until it
  exists, `hints = AUTO` hides every tell in a pack that has LegendQuest, so
  Sable's MobHealth - Forge instance has `hints = "ALWAYS"` written by hand.
- The LegendQuest and StoryTeller seams.

## Layout

| Package | What | Minecraft imports? |
|---|---|---|
| `plan` | the planner: topology, layout, routing, cells, heights, checks | **no** |
| `build` | `Blueprinter`: a plan as blocks by role (`Part`), not block states | **no** |
| `debug` | `PlanRenderer`, PNG sheets (AWT, headless: never call from a client) | **no** |
| `neoforge` | `Site` (one dungeon's identity), `Palettes`, `EntranceStyle`, `Builds`, `Triggers`, `CrawlState`, `CrawlConfig`, commands, `Tour` | yes |
| `neoforge.worldgen` | `DungeonStructure`, `DungeonPiece`, `ConfiguredSpread`, registration | yes |
| (root) | `CrawlSpace`, the mod class | yes |

Keep `plan` free of Minecraft. That is what lets the tests run thousands of
dungeons in seconds, and what keeps a version port to the thin layer that
places blocks.

## How a level is planned

`Planner` → `LevelPlanner` per level → `Router` per corridor → `PlanCheck`.

1. **Topology.** A loop through entry and exit, so there are always two ways
   to the stair down, plus branches (1–2 rooms) and a few shortcuts. One loop
   edge may be LOCKED, and its lever (role KEY) goes at the end of a branch.
2. **Layout.** Loop rooms sit on a lumpy closed curve, and branches point
   outward. Overlaps are pushed apart along the shallow axis (`relax`). The
   entry is pinned on the stair from above.
3. **Routing** (`Router`): A* over (cell, heading). The **turn cost** is the
   whole difference between STRAIGHT (square turns), ANGLED (45°), WINDING
   (cheap turns over value noise) and CURVED (ANGLED, then Chaikin-smoothed if
   the curve stays clear). Existing corridor is cheap to reuse, and the cells
   just beside it are dear, so junctions form and corridors don't run fused.
   **Spurs** (half of all branch heads) run to the nearest *connected*
   corridor and make T-junctions.
4. **Finishing:** walls (every rock cell touching open space), stairwells,
   pillars, pools, then heights. Room floors are chosen, and corridor floors
   come from a Laplace solve between them, so corridors ramp. A walkable step
   over 1 block flattens the level.
5. **Extra ways down** (`Planner.extraRoutes`): a pit (it lands in a pool on
   the level below) or a second stair, wherever a room sits over a room. Each
   is kept only if both levels still pass the check.

Every level shares one square, ±`LevelPlan.RADIUS` (120) around the tower.
That keeps a whole dungeon within vanilla's structure reach (pieces have to
stay within about 8 chunks of the start chunk; **re-verify against 1.21.11
source when building the worldgen**).

## How it is built (`build` + `neoforge`)

Level `i`'s floor is at `-top - i * 12` relative to the ground at the tower.
`top` starts at 14 and `Blueprinter.requiredTop` deepens it until every
ceiling has `COVER` (3) blocks of ground over it. The command measures the
ground as the **lower** of `OCEAN_FLOOR` (which counts treetops) and
`MOTION_BLOCKING_NO_LEAVES` (which counts the sea's surface).

The vertical band of a level is floor block −3 to ceiling +8 (heights ±2,
clear height up to 6). That is why the spacing is 12: two levels can never
share a block. Each open cell gets floor, air and ceiling. Each wall cell is
solid from the lowest floor beside it to the highest ceiling. `BlueprintTest`
proves **no air the dungeon makes touches the world**, and that the proof can
fail.

The spiral stair is a 3×3 well: newel in the centre, one step per block
climbed round the 8 ring cells, lined in rock between levels. A pit is a 3×3
shaft into a one-deep pool, which breaks the fall.

Blocks are set with client updates and no neighbour updates, 20,000 a tick,
as in WadCraft.

## Worldgen

One structure, one piece covering the whole footprint. `DungeonStructure`
plans in the start chunk. It samples the ground every 8 blocks with
`getBaseHeight(OCEAN_FLOOR_WG)`, and each cell takes the lowest of the four
samples round it. `Site.fit` then sinks the levels under that and drops
levels until the lowest block clears the bedrock layers. A site where fewer
than `minLevels` fit gets no pieces, so nothing is placed there. Each chunk's
`postProcess` places only the blueprint columns inside its box.

- **A `Site` (seed, levels, top, style, origin) is saved in the piece, not the
  plan.** A saved plan would be hundreds of KB in one chunk. The plan and
  blueprint are regenerated from the seed and cached (six at most), so
  **`Site.PLANNER_VERSION` must be bumped by any change that would alter an
  existing seed's dungeon.** A mismatch is logged, because parts of a dungeon
  not yet generated would no longer match the parts that were.
- Traps come from their own dice (`Dice.of(seed, index, 0x7EA95)`), so tuning
  them never moves a wall.
- **Spacing is config, not data:** `ConfiguredSpread` extends vanilla's
  `RandomSpreadStructurePlacement`, because that is the only type `/locate`
  searches. It overrides `spacing()`, `separation()` and
  `getPotentialStructureChunk`. The defaults are 36/16. Dungeon Crawl uses
  32/12, but a CrawlSpace dungeon is up to 15 chunks across, and below
  separation 16 two can overlap. The config is `config/crawlspace-server.toml`;
  NeoForge 21.x keeps server configs there, not per world.
- **The structure places in the LAST step (`top_layer_modification`).**
  Sable's rule: ours is the cutter, never the cut. Mineshafts, ores, springs
  and other structures are all laid first, and the dungeon carves through
  them. That also keeps springs out of its walls. Snow settles on the tower
  afterwards, which is right.
- Biomes are Dungeon Crawl's list (`#crawlspace:has_structure/dungeon`), plus
  mushroom fields, mangroves, cherry groves and the pale garden. No ocean, river
  or deep lowland.
- **Planning runs on worldgen threads.** The planner is pure and allocates its
  own state. Its only statics are diagnostics, and those are concurrent.

## Hints (Sable, 2026-10-06)

With LegendQuest, secret walls blend in, because its perception rolls are
meant to find them. Without it, `play.hints` (AUTO, ALWAYS, NEVER) turns tells
on:
- **Secret walls** use a related block that is in the theme's palette but not
  in its wall mix (chiseled among plain, cracked among mossy).
- **Dust**, sent only to the player within 9 blocks, about once a second:
  **red** over an unsprung trap, **green** at an unopened secret wall and over
  a treasure room's hoard.

Hints show however the player moves. Only traps need a foot on the ground.
The first version returned early for any airborne player, so a flying
creative tester saw nothing.

## Population

- **Encounters, not endless spawns.** Each room may hold an `ENCOUNTER`
  trigger, and a lair holds a `BOSS`. The trigger sits a block above the
  floor, so its key never collides with a floor trigger, and its targets are
  spots the dressing left free. `Triggers.wake` spawns the group when a
  non-creative player comes within 10 blocks.
  - **On peaceful nothing wakes and nothing is used up.** The rig's
    `server.properties` says peaceful and reapplies it on every restart, so
    set `/difficulty normal` before testing.
- **`Bestiary`:** vanilla mobs per theme. Armour (leather, chain, iron,
  diamond by depth) goes only on mobs that show it, and every mob gets extra
  health by depth.
  - **Bosses:** a named mob per theme, with much more health, a harder hit,
    and vanilla's SCALE attribute. Clients see it big with nothing
    installed.
  - `Bosses` keeps a vanilla `ServerBossEvent` per boss, in memory only. A
    boss alive across a restart loses its bar.
- **Natural monster spawns are off inside dungeons.** For generated ones this
  is the structure's `spawn_overrides`; for command builds,
  `Triggers.onSpawnCheck`, which refuses only NATURAL spawns, so spawners
  still work. Without this, five creepers spawned in the dark and blew the
  lair boss half to death before anyone arrived.
- **Loot:** `data/crawlspace/loot_table/chests/tier1..5` and `supplies`,
  generated by `scripts/make-loot.py`; **edit the script, not the JSON.** Every tier also has a
  **rare pool**: Mending books first, rare books, armour trim templates (rarer
  patterns deeper), the netherite upgrade from tier 4, and trimmed, enchanted
  armour. The script prints how often a rare roll pays per tier, from 46% at
  tier 1 to 100% at tier 5, so check those numbers rather than a comment. A
  level's tier is `1 + index / 2`, and a hoard (treasure room or lair) is one
  tier richer. `Site.afterPlace` sets a chest's table, and a spawner's mob,
  as the block is placed, for both worldgen and command builds.

## Entrances (`Towers`)

There are four designs, each wrapped round the stair's hole, with a floor at
y = -1 and the door or opening to the north:
- **KEEP:** buttressed quoined corners, string courses, barred windows, and
  corbels under a crenellated parapet. It has a storey above the ground
  floor.
- **ROUND:** banded, with a stepped cone and finial, or battlements.
- **PYRAMID:** stair-faced steps, a capstone, a tunnel and a chamber.
- **TEMPLE:** a stepped platform, a colonnade, an entablature and a
  pediment.

The biome style weights the choice: deserts lean to pyramids and temples,
jungles to mossy pyramids, snow to keeps and round towers. The style's
`Style` record supplies the materials (trim, stair, window, pillar, roof).

Constraints every design keeps:
- Nothing below ground touches the 5×5 round the hole, which is the stair
  well's.
- The ground floor stays clear to y = 6, the newel's top.
- Each design fits within `Towers.REACH` (6), which is also CityWorld's
  tower piece.
- The blueprint reaches `Blueprinter.TOP` above ground.

`BlueprintTest.everyTowerDesignIsSoundAndClimbable` builds every design in
every style.

## CityWorld

CityWorld (`../CityWorld-ReForged`, its own session's repo: **read it, never
edit it**) only builds structure sets on `#cityworld:allowed`. It keeps its
city off any chunk near a structure **piece** whose box reaches the natural
ground, if the structure is a surface one or declares `reserve` in its
`structure_fit` data map. CrawlSpace ships both declarations in its own jar:
- `data/cityworld/tags/worldgen/structure_set/allowed.json`, with ours as an
  optional entry, so new CityWorld worlds have dungeons with no ticking. An
  existing world keeps its own saved choice.
- `data/cityworld/data_maps/worldgen/structure/structure_fit.json`:
  `crawlspace:dungeon` with `reserve: true`.

Without CityWorld neither file is read. The tag names an optional entry, and
the data map type is never registered.

**The dungeon is two pieces for this** (`DungeonPiece.Part`): TOWER is the
7×7 columns round the origin, all the way down; BURIED is every other
column, with a box that tops out at the highest buried block, at least
three below the ground. Only the tower surfaces, so the city leaves a small
plaza rather than a fifteen-chunk meadow. A piece saved before the split
has no part and places everything.

## Dressing (`Dresser`, pure)

- Props come by role and theme:
  - the crypt has sarcophagi, skulls and candles
  - the mines have barrels, timber frames and rails
  - the caverns have stalagmites and moss
  - the deep halls have braziers and chains
  - shrines get an altar, lairs a hoard, a throne and bones, halls a carpet
    aisle, and treasure rooms their hoard.
- Every room also gets a finish: a floor border and medallion, pilasters
  every fourth wall cell, and ceiling beams in crypts and mines. Built themes
  get a skirting course too.
- **Reserved cells:**
  - two cells round doorways
  - the five-by-five of stairs and pits, which is seven-by-seven for a stair
    down
  - triggers, and the cells round a lever or hoard.

  A room's props are committed only if its doorways, stairs, lever and hoard
  are still reachable. `BlueprintTest.dressingLeavesEverythingReachable` checks
  the whole level afterwards, treating props as walls.
- **Railings round a stair down** are placed only if a walk from the
  railing's gap still reaches the whole room. A cross-shaped room had its
  arms cut off from the stair. The tower gets none: its floor is only the
  ring round the hole. Falling into any spiral lands on a step at most four
  blocks down.
- The interior finish: coving all round under the ceiling, panelling with a
  dado rail in lived-in rooms (planks in the mines), table sets, pots, rugs,
  and chandeliers in rooms six or more tall.
- Railings, fences and coving are set again once their chunk is in
  (`Block.updateFromNeighbourShapes`), so they join up.

## Perception: the seam for RPG mods (`api/`)

`CrawlSpaceApi.setPerception(Perception)` lets an RPG mod decide who notices
traps and secret doors, and who disarms traps. CrawlSpace never imports the
mod. That is the inward-pointing seam from the design notes, like
LegendQuest's `PartyVoice`.

- `notices(player, TRAP | SECRET_DOOR, depth)` is asked once per player per
  thing, when they come within 5 blocks, and remembered in memory (a restart
  rerolls). Whatever a player noticed is then shown to them with the hint
  particles, and nothing else is.
- `disarms(player, depth)`: sneak-use a known trap's floor tile, or anything
  standing on its cell. A failure springs it. With no provider, a known trap
  simply disarms.
- `hints = AUTO` now means "on unless a provider is registered". Before, it
  meant "off if LegendQuest is installed", which hid everything in a pack
  where nothing yet did the finding.
- `/crawlspace perception always|never|half|off` registers a stand-in, for
  testing the hook without an RPG mod.

## Triggers

All of them are ordinary blocks or floor tiles that `Triggers` watches, never
redstone: a vanilla client sees a lever and an iron door, and the mod does the
wiring. `Blueprinter` emits `Trigger`s beside the blocks, at blueprint
positions. At runtime `Dungeons.at(level, pos)` finds the `Site`: generated
dungeons through the structure manager, command builds through `CrawlState`.
The site's cached blueprint then answers "is there a trigger here".

- **Lever:** the event fires before the lever flips, so the doors follow the
  state it is about to take. `DoorBlock.setOpen` moves both halves and plays
  the sound.
- **Secret wall:** right-click it, and it is destroyed, with particles and no
  drop.
- **Traps:** checked every 2 ticks for a player on the ground, looked up
  through a per-player cache refreshed every 40 ticks. **Darts fire from the
  nearest wall's face above head height, angled down.** Fired level from a
  narrow corridor's wall, they started inside the player and missed.
- `CrawlState` (SavedData) keeps which triggers have fired, so a sprung trap
  stays sprung across restarts. It also keeps command-built sites.

## Testing on Vivo

The rig is `~/rig/crawlspace` on Vivo: display `:6`, game 25587, RCON 25597
(password `csdev`), a normal-terrain world (`level-seed=crawlspace`).
`restart.sh` stops and restarts both server and client. Refresh it with a tar
of `git ls-files -co --exclude-standard` over `~/rig/crawlspace/CrawlSpace`.

Drive it over RCON:
- `execute as TestBuddy at TestBuddy run crawlspace build 6 42`
- `execute as TestBuddy run crawlspace goto 4 hall`: a corridor style or room
  role, facing along or across it, which is how screenshots get aimed.
- `/crawlspace info` also reports the level you stand on: rooms awake,
  traps sprung, and the nearest sleeping room.
- `goto <level> <role>` now stands in a room's corner, looking across it, so
  screenshots show the room rather than a pillar.
- `goto <level> lever|locked|trap|secretdoor` stands you two cells from a
  trigger, facing it. Step onto a trap with `tp TestBuddy ^ ^ ^2`. For the lever
  or wall, aim with `tp ~ ~ ~ ~ 38` and click with
  `xdotool mousemove --window $W 640 360 click --window $W 3`.
- Give TestBuddy night vision: the dungeon is meant to be dark.
- **`Level.getHeight` answers the world's minimum Y for an unloaded chunk.** A
  build just after a teleport was refused on a coast because most of its
  footprint read as bottomless. `ground()` loads the chunk first.
- Framing a tower: spectator, then `tp X Y Z facing ox oy oz` from above and
  to one side. A camera at ground level ends up inside a tree.
- The world on the rig is `worldgen1` (`level-name` in `server.properties`).

Builds are remembered in memory only, so after a restart, rebuild the same
seed in the same place to use `goto`. The same seed means the same blueprint,
which sets 0 blocks.

## Decisions worth not relitigating

- **`Dice` (SplitMix64), never `java.util.Random` or `Math.random`.** Derive a
  stream per job with `Dice.of(seed, salts...)`, so one extra roll doesn't
  reshuffle every later dungeon.
- **Clearance is measured, not hoped for.** A corridor's centre stays at least
  `2 + halfWidth` (Chebyshev) from every room floor, so a wall always survives
  between a corridor and a room it is not entering.
- **Doorways sit on straight wall, one wide.** The floor either side of the
  door cell is the room's, and the wall either side is not.
- **Odd-sized rooms**, so there is a centre cell for a 3×3 stairwell.
- **A spur may only join a corridor already connected to the entry.** The
  nearest corridor is often the spur's own branch, and joining it made an
  island: two rooms joined only to each other. That was 90% of rejected
  attempts until fixed; `PlanCheck` caught it, which is what it is for.

## The tests prove each check can fail

`PlannerTest` breaks sound plans on purpose (walls up a doorway, breaches a
wall, makes a cliff, removes a landing) and asserts the check notices. Add one
of those for every new check. A check only ever run against good plans has
unmeasured power (LegendQuest's CLAUDE.md, "Known traps").

`RejectedTest` draws the first attempt the planner threw away
(`build/plan-renders/rejected.png`) and prints its problems. Run it when retries
climb: `manySeedsAreSound` prints the retry count and the reasons.

## Build

There is no system Java:

```bash
export JAVA_HOME=/home/sable/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2
./gradlew test               # 300 seeds x 8 levels, plus the sheets
./gradlew test -Pseeds=2000  # a longer soak
./gradlew build
```

Never report success from a command that prints it unconditionally; grep the
output for `error:|FAIL`.

## Licence: MIT, so ideas only from GPL sources

CrawlSpace is MIT. **CityWorld (GPL-3)** and **Shattered Pixel Dungeon (GPL-3)**
were read for ideas only: CityWorld's deterministic seeded planning, contexts
and palettes, and SPD's loop builder and connection rooms. **No code was
copied**, and none may be, or the licence would have to change.

## Versions and dev ports

`main` = 1.21.11 only for now. Version branches follow LegendQuest's rule
(`docs/VERSIONS.md` there) once there is something to port.

The dev server's ports are **25587 (game) / 25597 (RCON)**, the next free pair
after WadCraft's 25586/25596. Check `~/dev/README.md` on Vivo before claiming
them for a rig.
