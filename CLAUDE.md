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

Not yet:
- Mobs, spawners and loot scaled by depth; room dressing beyond pillars and
  pools; themes and entrance styles as data.
- Visible tripwires and pressure plates. The trigger system takes them as
  they are (another `Trigger.Kind` and a block in the blueprint); only the
  invisible tiles exist so far.
- Secret walls carry no tell: a cracked or mossy block in a wall that already
  has some. LegendQuest's perception check is the intended way to spot them.
  Whether to add a visual hint is Sable's call.
- Vanilla structures can overlap a dungeon: one mineshaft's planks crossed a
  generated dungeon. **Unverified:** whether water or lava springs can appear
  in Old Mines and Caverns walls, whose stone and deepslate count as natural
  rock to the spring feature.
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
- Biomes are Dungeon Crawl's list (`#crawlspace:has_structure/dungeon`), plus
  mushroom fields, mangroves, cherry groves and the pale garden. No ocean, river
  or deep lowland.
- **Planning runs on worldgen threads.** The planner is pure and allocates its
  own state. Its only statics are diagnostics, and those are concurrent.

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
