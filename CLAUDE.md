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

**Overnight 2026-10-06/07:** ported to `mc26.1`, `mc26.2` and `mc26.3` (see
"Versions"). Also: boss bars that survive restarts, monsters that leave each
other alone, and README, CHANGELOG and CURSEFORGE.md for 0.1.0. Nothing is
released, and there is no CurseForge project yet.

**2026-10-07 (day):** traps you can see, with decoys (see "Traps you can
see"), and datapack themes, monsters and entrances (see "Datapacks").
LegendQuest's perception support is merged for **LegendQuest 2.9.0** (not yet
released). Sable's MobHealth - Forge instance runs both, with `hints = "AUTO"`.

Not yet:
- The StoryTeller seam.
- The level layout as data. That is deliberate; see "Datapacks".

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

- **No step beside a doorway.** A stair next to a door cannot be climbed
  in either direction (Sable, seed 774: a stair outside a door blocked it).
  `solveHeights` holds every corridor cell beside a doorway level with it
  (an apron), as a fixed value in the Laplace solve, so the ramp starts a
  cell further out. `PlanCheck` rejects any corridor cell beside a doorway
  at a different height. Before, 26 such rises appeared in the first 42
  test dungeons.

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
- **...but neighbours spill.** A chunk decorated later puts features a few
  blocks into its neighbours, including a dungeon already built there.
  `/crawlspace breaches` found 0.3–1% of the shell replaced by tuff, gravel,
  clay, moss, granite, water and ore on three fresh dungeons. **`Restamp`**
  puts each new chunk of a dungeon right once, the tick after it loads: every
  blueprint block that differs is set again. A chunk only loads as full once
  every neighbour has finished its features, so nothing spills in afterwards.
  Three more fresh dungeons on the rig audited at 0, one of them 16 blocks
  from a mineshaft.
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

## Traps you can see (Sable, 2026-10-07)

Sable's rule: without LegendQuest, plates and wires you can see, with decoys,
and the particles pick out the real ones. With LegendQuest, the plate or wire
appears when you pass the check. Breaking it is a disarm attempt that can set
it off.

- **The blueprint carries the look.** `TRAP_PLATE` or `TRAP_WIRE` sits on a
  trap's cell: a plate in rooms, mostly wire in corridors. `DECOY_PLATE` or
  `DECOY_WIRE` sits on a `Trigger.Kind.DECOY` cell, about one per trap, kept
  off doorways and stairs and 2 cells from any trigger. All of it comes from
  its own `Dice` stream, so adding it moved nothing else in a dungeon.
- **Visibility is decided at placement.** `Palettes` builds a real trap's part
  as its plate only when no perception provider is registered
  (`CrawlConfig.trapsVisible()`), and as air otherwise. Decoys always show.
  A trap that lands on a corridor step's stair moves to the nearest clear
  flat cell within three (`Blueprinter.flatSpot`), or is left out, so every
  trap can be seen. Before this, 22 of 987 stayed hidden.
- **Revealed for everyone.** When a player notices a trap, `Triggers.reveal`
  places its plate or wire in the world. It is a real block, not a per-player
  fake, so breaking it is an ordinary block event, and a party can point it
  out to each other.
- **Breaking is a disarm attempt** (`Triggers.onBreak`): the block goes with
  no drop, then it is the perception mod's roll, or `play.disarmChance` (0.75)
  without one. A failure springs the trap and says "You set it off!". Sneak
  using the plate does the same. Breaking a decoy drops it and says "it was a
  decoy". Creative players break things as normal.
- `Dresser` keeps props, rugs and mine frames off plates. The new test
  `trapsAndDecoysHaveTheirPlates` caught both overwrites.

## Puzzle rooms (Sable, 2026-10-08)

Sable's brief was a parkour-style maze: the floor off the path looks like
end-portal void, and touching it sends you to a restart block at the centre.
Paths lead to the exits and one to a loot chest. With LegendQuest, an
Athletics check sends you to the block you last touched instead.

- **Chosen after everything else, from its own dice.** `Planner.puzzleRoom`
  runs once pits and second stairs are in. It turns one ROOM into a PUZZLE:
  rectangular, all open floor, 7 to 17 a side, on 45% of levels. Rooms are
  created with their role, so `Room.role` is no longer final, for this one
  change. No wall, room or other role moves. The trap and decoy counts in
  the tests are identical before and after.
- **The maze** (`Blueprinter.maze`) is a depth-first spanning tree over
  every other cell, grown from the centre. Each way in (a room cell beside
  walkable floor outside the room) joins it by the shortest path. The
  deepest remaining dead end gets a HOARD_CHEST, facing back along the
  path. Every other floor cell becomes `Part.VOID` **in place of the floor
  block** (y = floor - 1), with solid below. The end portal's surface then
  sits a quarter-block under the path, so it reads as sunken. Its inside
  shape is below a player standing on the path, so walking the edge is safe.
- **Paths are one wide and walkable: no jumps**, keeping Sable's standing
  rule. Sneaking stops you walking off an edge, as in vanilla.
- **The void is a real `end_portal`** for the look. So `Triggers.onTravel`
  cancels `EntityTravelToDimensionEvent` to the End for anything inside a
  dungeon. A diamond and a pig dropped in stayed in the Overworld (rig,
  2026-10-08). Players are moved by the tick first: a foot in the void, or
  on it if a datapack made it solid, means a check, then a move.
- **Catching yourself:** `Perception.recovers` is a default method (false),
  so older providers still compile and run. LegendQuest answers with an
  Athletics (STR) roll against 10 + level and always shows it. A pass goes
  to `LAST_SAFE`, the last path block stood on, if within 3. Otherwise the
  player goes to the room's `Trigger.Kind.PUZZLE` (the restart block).
  `/crawlspace perception always|never` drives this too.
- Dressing, monsters, traps and decoys stay out of puzzle rooms.
- **Tests:** `puzzleRoomsAreSolvable` walks every puzzle room from its restart
  block over path cells only, in 40 dungeons of 6 levels (66 rooms): every
  way in is reached, and the chest stands beside a reached cell.
  `noticesACutOffPuzzleDoor` voids one doorway's first step and expects
  that walk to complain, which proves it can fail. The reachability walk
  treats the void as a wall.
- Seen on the rig: the maze, the void, the restart block and the chest. A
  LegendQuest roll against 14 failed and went to the centre. The stand-in
  `always` caught and `never` went to the centre.
- `PLANNER_VERSION` is 5: puzzle rooms change what a seed builds.

**Harder with depth (Sable, 2026-10-08: "i dont mind jumps in the puzzle
room").** Jumps are allowed **inside puzzle rooms only**; everywhere else,
nothing needs one. Each room picks, from its own dice:
- **Leap of faith,** from level 3 (35%). Every floor cell shows `VOID` one
  block lower (f-2) over a floor at f-3. Path cells are `PATH_HIDDEN`, a
  barrier at f-1, and the rest are explicit air. Thrown items that miss come
  back: `onTravel` moves an `ItemEntity` that hits the void to its thrower.
  On the rig a diamond stayed on the path and an emerald returned. It needs
  the two layers below the room free (`roomFree`), else it is a plain maze.
- **Otherwise a maze,** with three hazards placed with odds that rise with
  depth (all zero on level 1). A **gap**: a tree edge's middle cell made
  void, so a straight one-block jump. A **`CRUMBLE`** block: stone brick,
  cracked, cobblestone, gone at 5 ticks a stage, re-formed 100 ticks later,
  so a room can never be left unsolvable. A **`DRIPLEAF`**: a big dripleaf
  on moss, vanilla's tilt. The centre and its four neighbours, the ways in,
  the chest and the cells beside it are never hazards. A gap beside the
  chest left nowhere to stand to open it, and the test caught that.
- **"Fallen" in a puzzle room** means below the path layer inside its bounds
  (the PUZZLE trigger's targets), or inside a void block. That rule covers
  crumbled holes, tipped dripleaf and the leap's open air. Standing *on* the
  void counts only where a datapack made it solid.
- **The maze void is sunk a block** (air at f-1, `VOID` at f-2, floor at
  f-3, and floor under every path block at f-2), where the two layers below
  the room are free. With the void in the floor layer, a player at a path's
  edge had their *centre* over the next column, and the check sent them
  back: Sable could not cross a room, though the leap of faith, whose void
  was already sunk, was fine. On the rig, standing with the centre 0.1 over
  the void column now holds, and stepping off still sends you to the centre.
  Where there is no room below, the void keeps the floor layer.
- `LAST_SAFE` is now recorded anywhere in a dungeon: the last block stood on
  whose floor is not void, air, crumble, dripleaf or a pit tile.

## Pit traps (Sable, 2026-10-08)

Stone brick that cracks and crumbles in a few steps, then is gone, over
dripstone spikes; with LegendQuest, a check to jump clear.

- **From the trap list, not the planner.** In `Blueprinter.triggers`, a trap
  on room floor becomes a pit half the time (`PIT_CHANCE`, own dice), if it
  fits. The 5x5 round it must be flat floor of one room, with nothing of the
  dungeon's in the six layers below. Otherwise it stays darts or gas. Tests:
  934 plate traps plus 53 pits, the same 987 in total as before.
- **The pit** has 9 `PIT_TILE`s at f-1, air at f-2 and f-3, `STALAGMITE` at
  f-4 on floor at f-5, and a 5x5 ring of wall from f-5 to f-2. The leak test
  proves it sealed. **Four deep, not three:** from three, the spikes cost 1
  health on the rig, and from four, 4 (two hearts).
- **Triggers:** a `PIT` at the middle, whose targets are the nine tiles, and
  a `PIT_EDGE` on each outer tile, pointing at the middle. Stepping on any
  tile fires it. Only the middle is a trap, for hints and noticing. Disarm
  by sneak-using a tile: success wedges it safe, failure sets it off.
- **Collapse** (`Crumbles`, 3 ticks a stage, never re-formed). Anyone on the
  patch may `recovers` back to `LAST_SAFE`; the rig showed "always" working.
  Forty ticks later a ladder goes up the north wall, so nobody is stuck: no
  jump is needed to get out.
- `Crumbles` is an in-memory scheduler, also used for the puzzle blocks, with
  `later(...)` for delayed jobs. A restart leaves a crumble at whatever stage
  it had reached.

## Portcullises (Sable, 2026-10-09)

Two kinds, both in the blueprint layer on their own dice, so neither moves a
wall. `PLANNER_VERSION` 6.

- **Keyed lock.** On half the levels that have a lock (`Blueprinter.keyLock`,
  `KEY_LOCK_CHANCE`), the locked doorway is iron bars (`Part.PORTCULLIS`)
  instead of an iron door. The KEY room then holds a `KEY_CHEST`, not a
  lever: the supplies loot table plus the key in slot 13 (`Keys`). The key
  is a tripwire hook with a name, lore, glint and a `crawlspace_key` tag in
  `custom_data`, `"x,y,z/level"`. The tag opens the bars, so a hook renamed
  on an anvil opens nothing. Using the bars (`Trigger.Kind.PORTCULLIS`, on
  the lower bar) with the right key spends it and raises them bottom-first.
  A key for another level says which; with none, it says the key is in a
  chest on this level. The lock is still on the loop, so a lost key costs a
  shortcut, never the way down.
- **Drops behind you** (from level 2, 40%). Over an ARCH into a ROOM, HALL,
  GUARD or LAIR with at least two doorways, so it bars the way back and
  never the way on. Its trigger (`PORTCULLIS_TRAP`, a trap for notice, hints
  and disarm) is the floor two cells in. It fires only when you walk *in*:
  `Triggers.PREV` holds where the feet were last look, and the drop needs
  them to have been nearer the arch. The archway is `PORTCULLIS_GAP` (air
  until it falls), and the floor under it is a `PORTCULLIS_SILL`, the tell
  (`floor_inlay` with hints on, otherwise the corridor floor). A `WINCH`
  lever inside raises it, and so does a timer after 2 minutes. Anyone in
  the arch is pushed into the room, never into the bars. Dropped bars are
  protected; raised, there is nothing to protect.
- **Seen on the rig (seed 6, level 2, 2026-10-09):**
  - Walked out through the arch: the bars stayed up.
  - Walked in: they dropped, with "Clang! A portcullis slams down behind
    you. A winch in this room raises it."
  - Using the bars said "It will not budge. Somewhere in the room, a winch
    raises it."
  - The winch raised them.
  - At the keyed portcullis with no key: "Locked. Its key is in a chest
    somewhere on this level."
  - The key chest held `crawlspace_key: "5000,47,5006/1"`. With that key:
    "The key turns. The portcullis grinds up.", the bars rose bottom-first,
    and the key was spent.
- **Testing aid:** `/crawlspace goto <level> portcullis|droptrap|winch`.
  `droptrap` stands you two outside the arch, facing in.
- The hall's aisle carpet used to lay itself over a winch. It now breaks
  round reserved cells.
- **Rig gotchas:** RCON `tp ... facing` did not move the client's pitch, so
  use explicit yaw and pitch. Clear the inventory before clicking, or a
  right-click places a block. Action-bar text is not in the client log, so
  screenshot it.

## One-way doors (2026-10-09)

A **shortcut back** (Dormans' "hidden shortcut"; Dark Souls' unlocked gate).
In `LevelPlanner.addShortcuts`, a shortcut between rooms at different hop
counts from the entry may be `LinkKind.ONEWAY` (`ONEWAY_CHANCE`, 0.35). The
link is oriented so `a` is the room further from the entry. Its `doorA`
becomes `Cell.DOOR_ONEWAY`, an iron door (`ONEWAY_LOWER`/`UPPER`), and
`Blueprinter.onewayLevers` puts a floor lever (`Trigger.Kind.ONEWAY`) just
inside room `a`, beside the doorway. From the near side the door says "It
will not open from this side."; the lever says "The iron door swings open: a
way back."
- `PlanCheck`'s without-locks walk treats it like the locked door: the exit
  and the lever must be reachable without it.
- A puzzle room is never a one-way door's far room, since its lever would
  stand on the maze.
- **Seen on the rig** (seed 14, level 1): closed from the near side and
  using it changed nothing; the lever opened it.
- Test: `onewayDoorsOpenFromTheFarSide` (each lever is in its door's own
  room; more than 15 in 30 dungeons).
- **Two old bugs the new layouts brought out**, both now fixed and tested:
  - A second stair added after the traps could put its railing over a trap
    plate. Traps within 2 of a stair or pit are now dropped.
  - The railing round a stair hole checked that it left the whole room
    reachable, but not its doorways, and it once railed a small treasure
    room off from its own door. Doorways are now checked too.
- The dressing walker in `BlueprintTest` now passes through levers, which
  have no collision, as in the game.

## Windows (foreshadowing, 2026-10-09)

`Blueprinter.windows`, after the dressing, on its own dice (`WINDOW_CHANCE`
0.65 per room). Where a corridor passes exactly three cells from a LAIR,
TREASURE, KEY or SHRINE room, the two wall cells between them become
`WINDOW_BARS` at eye level (f+1): one or two wide.
- **Conditions:** straight wall either side, the room and corridor floors
  at the same height, and no doorway, stair or pit within two of either end.
- **The cells in front, at eye level,** must be clear or hold only something
  hung on the wall, which comes down: a banner on a wall that is now bars
  would float.
- **They are rare**, about one level in four: corridors seldom hug a room at
  exactly three. A thicker wall would make a tunnel of bars through natural
  rock, so rare stays.
- **Tested:** `windowsSeeThrough` checks every bar has air on both sides in a
  straight line, and that 30 dungeons have more than 30 bars.
- **Seen on the rig** (seed 9, level 1, `goto 1 window`): the room's
  furniture shows through.

## The finale arena (Sable, 2026-10-09)

The reason to reach the bottom. On the bottom level, the LAIR becomes a
sunken arena (`Blueprinter.arenaPlan` / `arena`), after Warhammer Quest's
objective rooms (the Fighting Pit).
- **Shape.** Room cells with the whole 5x5 round them in the room are the
  pit; the rest is a gallery two cells wide at the doorways' height. The pit
  floor is `ARENA_DEPTH` (5) lower, so the arena stands eleven high. Under
  the gallery is solid wall down to the pit floor.
- **Flights.** Two straight flights of five `STEP`s, each facing back up on
  solid fill, from opposite edges where they fit, else one. A lair with a
  pillar or pool in the pit, a pit under 25 cells, or no room for a flight
  stays a plain lair: 23 of 40 four-level dungeons got one.
- **No railing.** The lair's furniture lines the gallery's outer ring, so a
  railing on the inner ring closed the gallery off (the dressing test caught
  it). The fall is five blocks, about a heart, and sneaking stops it.
- **Contents.** A second hoard chest goes at the pit cell furthest from the
  flights (with a TREASURE trigger), and a chandelier hangs over the middle.
  The boss and its followers stand in the pit: on the first rig run the boss
  woke on the gallery, beside the player.
- **Order.** The pit is settled before the triggers (`Blueprint.markArena`),
  so `clearAround` and `flatSpot` keep traps, decoys and winches off it. Before
  that, the pit erased a winch and a decoy placed there. The dressing runs as
  usual and its props in the pit are cleared with it.
- **Deeper.** The blueprint and the site's lowest block reach `BELOW` (10)
  under the bottom level's floor, not 4, and `Site.fit` keeps that clear of
  bedrock.
- **Tested:** `finaleArenasCanBeWalkedInto` walks from the gallery to the pit
  floor, climbing only by stairs.
- **Testing aid:** `/crawlspace goto <bottom level> arena` stands you on the
  gallery's inner corner.
- **Seen on the rig** (seed 4, two levels): the pit, both flights, and the
  furniture round the gallery. With the boss moved into the pit, three of its
  group were still there 8 s after waking, and the rest, the Bone Warden
  among them, had climbed a flight and reached the player on the gallery. They
  hunt; the pit is where they start, not a cage.

## Protection (Sable, 2026-10-09)

**`neoforge/Protection`** keeps the shell whole, so the way down is through the
dungeon, not round it. The shell is `Part.shell()`: walls, floors, ceilings,
stairs, tower, iron doors and their lever, beams, and the puzzle rooms.
Everything else breaks as usual: dressing, trap plates (still a disarm
attempt) and whatever a player placed. A block counts as the dungeon's only
while it is still the block the palette put there.

- **The client is stopped, not corrected.** A survival player inside a
  dungeon (feet in a cell the blueprint set) gets a transient
  `block_break_speed` modifier of ×0. A client then never cracks or predicts
  a break. Measured on Vivo 2026-10-09: an Efficiency V diamond pick held 4 s
  left the wall untouched; at ×1 it broke stone in 1 s. Hitting the shell
  from outside (from a cave) puts the hold on for 3 s.
- **Breakable blocks are mined by the server.** At ×0 the client still sends
  START and ABORT; the server times the dig from vanilla's own
  `getDestroyProgress` (computed with the modifier briefly removed), sends
  the cracks under an id that is not the player's own (the level never sends
  a breaker its own progress), and calls `gameMode.destroyBlock`. Measured: a
  placed dirt block broke by hand within 3 s, with cracks showing.
- **Behind it, for anything without hands:** the break event is cancelled on
  the shell (fake players, drills); explosions keep only non-shell blocks;
  pistons cannot push, pull or crush the shell; and every 5 s shell missing
  within 8 blocks of a player is put back from the blueprint. Repair only
  fills air, never where an entity is, and never touches what goes by
  design: crumble, dripleaf, pit tiles, iron doors, opened secret walls.
  Measured: a wall removed by `/setblock` came back within 7 s, and TNT took
  a placed dirt block but not the wall beside it.
- **No placing a block within two above a VOID cell**, which is a bridge.
- **Config `[protection]`**: `enabled`, `explosions`, `repair`. All default
  on, and they apply live: the hold came off and back on as the file was
  edited, with no restart.
- `BlueprintTest.protectedShellSealsEveryLevel`: no breakable block
  underground may touch the world. It found BEAM in the ceiling layer, which
  is why BEAM is in the shell. `noticesABreakableHoleInTheShell` proves the
  test can fail.

## Datapacks (2026-10-07)

`ThemeData` (a `ResourceManagerReloadListener`, registered on
`AddServerReloadListenersEvent`) reads two folders on every load and
`/reload`:

- `data/<ns>/crawlspace/theme/<name>.json` has `blocks` (role to block, or a
  weighted list), `mobs` (which replaces the theme's list) and `boss` (which
  changes only the fields it names). The file names its theme with "theme",
  or by file name (`sunken_halls.json`).
- `data/<ns>/crawlspace/entrance/<style>.json` has `blocks`, `designs`
  (keep/round/pyramid/temple weights) and `biomes` (ids or `#tags`, which
  claim those biomes ahead of the built-in rules). A new name makes a new
  style, starting from "stone".

Everything is laid over the code defaults key by key. Unknown roles, blocks
and entities are skipped with a warning naming the file and the key.
`/crawlspace export` writes what is in force as a complete datapack in
`<world>/crawlspace-export/`. `examples/datapack` turns the Crypt into
sandstone with "the Dune King", and adds a basalt entrance that claims
badlands. On the rig both took effect after a `/reload`.

- **Every role is looked up by name**, from records flattened by reflection
  (`Palettes.fields`), so a field added to `Fittings` is a new role with no
  second list to keep in step. **Every block property is set through
  `with()`**, which skips properties the block lacks: a pack can put a plain
  block where a stair was without crashing worldgen. A non-door block given
  as a door fills the doorway.
- **The layout is never data, on purpose.** A generated dungeon is planned
  again from its seed for every chunk it touches. If a pack could move a
  wall, installing or editing one mid-world would change half a dungeon.
  Blocks, monsters and towers can change between sessions without breaking
  one. `Theme`'s shape and corridor weights stay in code.
- Loot was already data: the `crawlspace:chests/tier1..5` and `supplies`
  tables can be overridden like any loot table.

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
  - `Bosses` keeps a vanilla `ServerBossEvent` per boss, in memory. The
    boss carries `crawlspace_boss` and `crawlspace_bar_<colour>` as entity
    tags, and takes its bar again on `EntityJoinLevelEvent`, after a restart
    or a chunk reload. Seen on the rig, 2026-10-07.
- **A room's monsters never fight each other.** Everything `wake` spawns is
  tagged `crawlspace_mob`, and damage and retargeting between two tagged mobs
  are cancelled (`Bestiary.onHurt`, `onTarget`). Without this, the Bone
  Warden's stray arrows hit its zombies, they turned on it, and it was
  "slain by Zombie" seconds after waking, before anyone reached the lair.
  Spawner mobs are untagged and behave as vanilla.
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
- **Traps** (see also "Traps you can see"): checked every 2 ticks for a player on the ground, looked up
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
- **`goto trap` assumes a straight line, and dressing can block it.** On
  2026-10-07 it stood the player facing a polished andesite pilaster, with
  the trap behind it and every walkable neighbour of the trap filled. Check
  the cells with `execute if block` before blaming input.
- **Disarming sneaks, and sneaking lowers the eye from 1.62 to 1.27.** From
  two cells out, the trap tile is at pitch 32, not the 38 that aims a
  standing click at a lever, and 38 lands one cell short. In general, aim at
  atan(1.27/n). Hold the key with `xdotool keydown --window $W shift`. A
  notice message does not say which trap was noticed, so look for its red
  particles: on 2026-10-07 the trap `goto` faced failed its roll, and the
  noticed one was two cells further on.
- **The rig is peaceful in `server.properties`.** A restart re-applies it and
  vanilla discards every hostile mob as it loads, so a "does X survive a
  restart" test needs `difficulty=normal` in the file, not just the command.
  Put it back afterwards.
- Give TestBuddy night vision: the dungeon is meant to be dark.
- **Store screenshots** (the four towers, 2026-10-07):
  - Restart display `:6` at 1920x1080 and resize the client window with
    `xdotool windowmove $W 0 0; xdotool windowsize $W 1920 1080`. The
    `overrideWidth` option did nothing.
  - On Vivo, `~/rig/crawlspace/place.sh <x> <z> <seed>` stands the player on
    an exact block and builds, printing the tower origin.
    `cam.sh <origin> <dx dy dz> <aim-dy> shoot` previews or shoots with F1 and
    F2. Read the origin back instead of computing it after a spreadplayers.
  - A datapack with only `"designs": {"keep": 1}` per style pins a design.
  - **1.21.11 renamed `doDaylightCycle` to `advance_time`.** The old name is a
    parse error over RCON, and the sun kept moving.
  - The shots are in Sable's `Downloads/crawlspace-screens`.
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

## Artwork

Sable's logos (2026-10-07) are in `art/`, with full-size originals on solid
black. `src/main/resources/crawlspace-icon.png` (256px square) and
`crawlspace.png` (the nameplate, 1100px wide) are cut from them with the black
cleared. The mods.toml template declares `iconFile`, `bannerFile` and
`logoFile`, as LegendQuest does. The cut flood-fills the background from the
edge after closing gaps in the outline. A plain flood fill leaked through
WadCraft's open badge and holed the dark panel behind its letters; the closing
is what stopped that. `art/` is on `main` only.

## Licence: MIT, so ideas only from GPL sources

CrawlSpace is MIT. **CityWorld (GPL-3)** and **Shattered Pixel Dungeon (GPL-3)**
were read for ideas only: CityWorld's deterministic seeded planning, contexts
and palettes, and SPD's loop builder and connection rooms. **No code was
copied**, and none may be, or the licence would have to change.

## Versions and dev ports

Branch per version, ported forwards (`main` → `mc26.1` → `mc26.2` → `mc26.3`),
as in LegendQuest (its `docs/VERSIONS.md`). **Docs live on `main` only.**

| Branch | Minecraft | NeoForge | Java | ModDevGradle |
|---|---|---|---|---|
| `main` | 1.21.11 | 21.11.42 | 21 | 2.0.141 |
| `mc26.1` | 26.1.2 | 26.1.2.95 | 25 | 2.0.141 |
| `mc26.2` | 26.2 | 26.2.0.59 | 25 | 2.0.144 |
| `mc26.3` | 26.3 | 26.3.0.33-beta (capped below .37) | 25 | 2.0.147 |

What each drop cost, so the next port knows where to look:
- **26.1:** four renames. `ServerBossEvent` takes a UUID, `SavedDataType` an
  `Identifier`, `displayClientMessage` became `sendOverlayMessage`, and
  `ChunkPos.toLong` became `pack`, with the same encoding, so seeds keep
  their dungeons.
- **26.2:** entity constants moved to `EntityTypes`. Dyed blocks are
  `ColorCollection`s (`Blocks.CARPET.pick(DyeColor.RED)`), and
  `DripstoneThickness` became `SpeleothemThickness`.
- **26.3:** structure placement was reworked. `StructurePlacement` is an
  interface, and the registry holds `MapCodec`s with no placement type.
  `ConfiguredSpread` returns its codec typed as vanilla's random spread.
  Structures sample biomes through the context's `biomeResolver`, and
  `getStructureWithPieceAt` takes coordinates.

`~/rig/verify-version.sh <branch>` on Vivo (fed `git archive <branch>` on
stdin) boots a server-only dev server for a branch on ports 25588/25598. It
checks `/locate`, `/place structure`, the loot tables and the log for
errors, then stops. Run it after every port. `/place` needs every chunk
under the buried piece loaded, which is ±120 blocks (`LevelPlan.RADIUS`),
exactly the 256-chunk force-load limit. A smaller force-load just answers
"That position is not loaded". The script writes `~/rig/cs-verify/<branch>.result`.

- **26.x renamed `Entity.getTags()` to `entityTags()`.** Main-branch code that
  touches tags needs that edit when it is carried forward.

`gradlew` lost its executable bit coming in from the Windows drive. That is
fixed on every branch; if a new script arrives the same way, use
`git update-index --chmod=+x`.

The dev server's ports are **25587 (game) / 25597 (RCON)**, the next free pair
after WadCraft's 25586/25596. Check `~/dev/README.md` on Vivo before claiming
them for a rig.
