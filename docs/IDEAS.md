# Ideas for CrawlSpace

Research from 2026-10-08, done overnight at Sable's request. It looked at three
sources: other Minecraft dungeon mods, classic pen-and-paper dungeons and their
design theory, and roguelike and video-game dungeons. Nothing here is built yet.
Each idea names its source and fits CrawlSpace's rules:

- vanilla clients see everything;
- interactive things are ordinary blocks the mod watches;
- content is data;
- LegendQuest checks are optional extras.

Effort: **S** is a day or so, **M** several days, **L** a subsystem.
**LQ** marks an idea that gets better with LegendQuest.

## 1. Cheap, high impact, built on what exists

| Idea | Source | How it would work | Effort |
|---|---|---|---|
| **Level feelings** | Pixel Dungeon, DCSS, Brogue | Each level rolls zero or one feeling, announced on arrival: "The walls are hollow" doubles the secret walls; "The air is damp" floods rooms; "Something large hunts here" adds a champion; "It is very dark" means no lights but better loot. A feeling is a datapack set of generator weight overrides. | S |
| **Wider trap pool** | Pixel Dungeon, DMG Appendix A | Add these to darts, gas and pits: alarm (a bell wakes nearby rooms), rockfall (gravel or anvils), gripping (cobweb plus Slowness), frost (powder snow), fire, teleport (to a random room), a chute (one-way, to an unvisited part of the level below), and disarming. Disarming sends your held item into a random chest on the level, with a message that names the remedy. Each trap is weighted by theme and depth, in the same decoy mix as today. | S each |
| **Telegraphed tells** | Goblin Punch's checklist | Every trap type gets dressing nearby: bones, scorch marks, arrows stuck in walls, cobwebs over a hole. Vanilla players learn to read the dungeon. **LQ:** a WIS notice turns a tell into a chat warning. | S |
| **Trapped chests and mimics** | DMG treasure guards, Dungeon Now Loading | Opening one of these chests sets off a poison needle, a puff of gas or a horn, or releases a short burst of theme mobs, after which it is a normal chest. **LQ:** DEX disarms the trap, INT spots it. | S |
| **Puzzle blessings** | Hypixel Catacombs | Solving a puzzle grants a buff until you leave the level (Strength, Regeneration or Haste), announced on the action bar. Puzzles then pay off at once rather than feeling like chores. | S |
| **Take one of three** | Brogue vaults, Pixel Dungeon Crystal Choice, Minecraft Dungeons Tower | Three items sit on display under iron bars. Take one and the other cages slam shut, with a line saying so. It is a real choice at no cost to balance. | S–M |
| **Door reward previews** | Hades | At a junction, the doorway is marked with a block showing what lies beyond: gold for treasure, a skull for a lair, a cauldron for a shrine. Players pick a route by its reward. | S |
| **Engravings, rumours, lore books** | NetHack, Keep on the Borderlands, Greymerk | A rumour board at the entrance, some rumours false ("The Crypt's lord fears fire"). Epitaphs in the Crypt. Warnings before trap rooms. Books hinting at a secret wall or a boss weakness. All text pools are data. | S |
| **Shrines and fountains with names** | DMG magic pools, Diablo shrines, NetHack fountains | Use a cauldron or well once for an effect drawn from a weighted table: heal, a buff, reveal the key room, nausea, a summon. Name it in chat ("Shrine of Sight"). Keep a codex so a shrine a player has met before is named on sight. **LQ:** INT or WIS reads the omen before you drink. | S |
| **Alarm sentries** | Caves of Chaos | A GUARD room's sentry blows a goat horn unless it is killed fast or avoided, and that wakes one or two neighbouring rooms, which come to help. **LQ:** DEX stealth gets you past without waking it. | S–M |

## 2. Level structure: the biggest single win

**Cycle types on the loop planner** (Joris Dormans' cyclic generation, as used
in *Unexplored*; M–L). The planner already makes a lumpy loop. Give each loop
and sub-loop a *type* from a weighted datapack list, and let the type decide
what each arc of the loop holds:

- **lock and key**: the short arc is locked; the long arc holds the key (today's one lock becomes one case of this);
- **two keys**: half the requirement on each arc;
- **dangerous route**: a short arc with a hazard, or a long safe one;
- **hidden shortcut**: a secret or one-way link back to the start, found after the goal;
- **blocked retreat**: a valve closes behind you, so the only way is on;
- **altered return**: the way back has changed (flooded, collapsed, unlocked);
- **foreshadowing**: you see the goal early and reach it late;
- **false goal**: what looks like the goal is a dead end or a trap;
- **gambit**: a risky side arc whose reward helps on the main arc.

Themes weight the types: the Crypt favours blocked retreat, the Mines dangerous
route. Several of the ideas below come almost free once this exists:

| Idea | Source | How | Effort |
|---|---|---|---|
| **Foreshadowing windows** | Dormans, Zelda | Where a corridor passes within a few blocks of a locked or treasure room, swap the wall between them for iron bars or glass. The chest is seen early and reached late. | S–M |
| **One-way valves** | Dormans, Jaquays | A drop too high to climb back up, a lever on one side of a door only, a bridge that crumbles behind you (`Crumbles` already does this), a chute to the level below. The planner's connectivity check becomes directed. | S |
| **Shortcuts where they matter** | Brogue | Rather than placing shortcuts at random, add one where two rooms are close in space but far apart on the path, often as a secret wall or a door that opens from one side only. | S |
| **Key situations** | Brogue key holders | The KEY room's lever sits in a situation drawn from a list: pulling it floods the room, collapses the floor to the level below, shuts the door and wakes a spawner for 30 s, or bursts silverfish from the walls. The lever is always obtainable. | M |
| **Key carrier** | Minecraft Dungeons key golem | The key is a small mob that follows you to the door and runs back to the KEY room if you take damage. | M |
| **Pit room** | Pixel Dungeon | A treasure room on level N+1 that can only be reached by falling through a weak floor on level N, foreshadowed by "the floor here sounds hollow". | S |
| **5-room branches** | Johnn Four | Dead-end branches follow a fixed shape: guardian, then puzzle or trick, then setback, then a small climax, then the reward. Branches always feel worth taking. | S |
| **Sub-levels and side portals** | Greyhawk, Undermountain, DCSS portal vaults | A rare secret leads to a 5–8 room pocket (a forgotten library, a flooded ossuary) with better loot. A timed variant announces itself ("You hear rushing water") and collapses when a boss-bar countdown runs out. | M–L |
| **Multi-level room** | Jaquays, Dyson Logos | A tall cavern spanning two levels, with balconies, so you see the next level's monsters before you reach it. | M–L |

## 2a. Sable's additions (2026-10-09)

**Portcullis and key** (S–M). A portcullis of iron bars closes a corridor or a
room, and its key lies in a chest somewhere else on the level. It is a second
kind of lock beside the lever and iron door, and it fits the cycle types above
(the key is on the long arc). Use the portcullis while holding the key and it
rises a row at a time, with a chain sound; the key is used up. Use it without
the key and the action bar says where the key might be, for example "The key
must be somewhere on this level", or with LQ the direction of the chest.

- **The key is a vanilla item**: a `trial_key` or tripwire hook given a name
  ("Crypt Key"), lore ("Opens the portcullis on level 2") and a
  `crawlspace:key` tag in `custom_data`. The mod matches the tag, not the name,
  so a key renamed on an anvil does not open anything.
- **A key from another level** opens nothing, and the message says which level
  it belongs to.
- **Lose the key** (lava, despawn) and a spare turns up in the KEY room's
  chest, so the level is never left unwinnable.
- **LQ:** a STR check lifts the portcullis partway: a crawl gap under it, with
  a slower and riskier way through.

**Objective rooms** (Advanced HeroQuest's quest room; M–L). Each level's
climax is a big, fancy, purpose-built room, not just a larger room of the
usual kind: a throne room with a dais and banners, a flooded temple with an
island altar, a mine's great hall with a broken lift, or a crypt's ossuary
nave. It holds the lair boss or the stairs down, and the level's best loot.

- **What sets it apart:**
  - two to three times the size of a normal room;
  - two storeys, with galleries;
  - columns, a raised dais, its own lighting;
  - a grand double doorway, so you know it on sight.
- **How it is built:** each theme has a few hand-built templates
  (structure NBT in the datapack, with block substitution so one template
  has variants), or a procedural "grand" builder taking columns, dais and
  galleries as parameters.
- **Where it goes:** the planner reserves its footprint first, then routes
  the level to it. It is the natural target for foreshadowing windows, and
  for the boss's deliberate summon.

## 3. Puzzles proven on vanilla clients

Hypixel's Catacombs runs about ten puzzles for unmodded clients, all
server-watched. That is CrawlSpace's model exactly, so these are known to work.

| Puzzle | How | Effort |
|---|---|---|
| **Ice fill** | Walk every tile exactly once. Ice turns to packed ice behind you; stepping on it again resets the board. Easy to generate solvable. | S |
| **Teleport maze** | Cells of pressure-plate pads on a generated mapping. It reuses the void maze's teleport. | S |
| **Ordered kills** | Mobs show their health in their names; kill them in order. A wrong kill respawns the set. | S |
| **Token quota** | Arena mobs drop a named token; drop N into a cauldron to open the door. A guard room becomes an objective. (Wynncraft) | S |
| **Liars' chests** | Three named villagers each make a statement; exactly one consistent answer. Wrong chest springs a trap. **LQ:** an Insight check gives a hint. | S–M |
| **Item answers a riddle** | A lectern riddle answered by placing an *item* in a frame ("a face and hands but no arms": a clock), found on the same level. No typing, and it works in any language. **LQ:** INT gives a hint. (White Plume Mountain) | M |
| **Item-frame alignment** | Arrows in item frames turned to match a mural elsewhere on the level. (Tomb of Annihilation) | S–M |
| **Vanishing bridge** | Use one block of a set and the whole set vanishes for a few seconds, or becomes a bridge. (Twilight Forest Dark Tower) | S–M |
| **Ice slide** | Push a NoAI marker mob along packed ice to a target block. | M |
| **Water board** | Levers move marked blocks so water flows to the right troughs. | M |
| **Boulder / Sokoban** | Buttons push crates one cell. It needs a reset lever and should be generated backwards from a solved state. | M |
| **Beam alignment** | Hit pairs of targets; end-crystal beams (vanilla-rendered) must all cross a centre point. | M |

## 4. Encounters and bosses

| Idea | Source | How | Effort |
|---|---|---|---|
| **Champions, capped** | Diablo, Greymerk | An occasional named elite with one or two affixes and better drops. Keep room counts capped: mob floods are the most-cited complaint about When Dungeons Arise. | S |
| **Sleeping guardian, steal or fight** | Mowzie's Frostmaw | A lair boss asleep on the hoard. Sneak in and take it; any noise wakes it. **LQ:** DEX check. | M |
| **Deliberate summon** | L_Ender's Cataclysm | The boss stays dormant until you place an offering on an altar, so nobody blunders into it. The offering sets the tier. | S |
| **Arena weapon** | Twilight Forest Ghast Trap | Kills in the arena charge a beacon; when it is full, a lever fires it at the boss. A way to win that needs no gear. | M |
| **Boss affixes** | Brutal Bosses | 1–3 rolled abilities named in the boss bar ("Gravemaw the Webbed"). | M |
| **Rematch** | Dungeon Now Loading | Re-summon a beaten boss at its altar; each rematch is stronger ("Recalled II") with a better rare-pool chance. | S–M |
| **Survival room** | Hades, Dungeon Now Loading | The doors seal and a boss bar counts down while waves come. The doors *always* reopen on a timer as a fallback, and the game says why they are shut. | S–M |
| **Patrol** | Dormans | One named mob walks the loop and never sleeps. The loop is what makes this work. | M |
| **False lair** | Tomb of Horrors | A weaker look-alike boss falls too easily ("This was too easy…"); the real lair is behind a secret wall in the same room. **LQ:** WIS spots the seam. | M |
| **The thing you should not touch** | Death Frost Doom | A sarcophagus with every warning on it. Disturb it and the level escalates: cleared rooms refill and a "Restless Dead" bar appears, with loot to match. Say what changed at once, and that leaving the level ends it. | M |
| **Rival factions** | Caves of Chaos, Temple of Elemental Evil, Sunless Citadel | Two monster factions on a level fight where they meet. One will bargain: "bring us X" and it stays neutral or opens a door. **LQ:** CHA to parley, and karma decides which side will deal. | M–L |
| **The prisoner** | Against the Giants, Goblin Punch | A caged villager in a lair. Freeing it gives a rumour, a map hint or a trade. **LQ:** karma. | M |

## 5. The long arc

| Idea | Source | How | Effort |
|---|---|---|---|
| **Seals for the deepest door** | DCSS runes, Zelda big key, CTM wools, Tomb of Annihilation cubes | 3–5 named relics from lairs, secrets and puzzles on earlier levels, set in item frames to open the final vault. It pairs with LegendQuest's criterion-name advancements. | M |
| **Dungeon clock** | The OSR "overloaded encounter die" | Every N seconds on a level, roll a table: a wandering band; *spoor* ("claws on stone to the east", with a sound); the dungeon's own torches gutter out; a local event (the Sunken Halls water rises); nothing. Fighting and searching speed the clock up. **LQ:** WIS tells you the spoor's direction. | M |
| **Darkness as a resource** | Darkest Dungeon, YUNG's Catacombs | Each theme gets a light budget, falling with depth; less light means better loot. | S–M |
| **Restocking** | OSR megadungeons | Cleared rooms refill after a real-time interval, with changed dressing, so a server's dungeon stays worth revisiting. | M |
| **Heroic re-entry** | Wynncraft's corrupted dungeons | After a clear, a found key reopens the dungeon as a harder version: the same layout with deeper tiers. | M–L |

## Traps other mods fell into

- **Digging round the puzzle.** Twilight Forest makes maze stone very hard to mine; Cataclysm makes it unbreakable. For us:
  - cancel breaks on puzzle, door and secret-wall blocks, with a line that names the intended way ("The ward holds. Find the lever.");
  - resend the block at once, so the client never shows a correction;
  - stop placing blocks in puzzle rooms, and send ender pearls back as the void does.
- **Spawners become farms** (Greymerk), so the dungeon is abandoned. Let spawners burn out, or stop once the room is clear.
- **Mob floods and server strain** (When Dungeons Arise, IDAS). Waking room by room, as we already do, plus a hard cap per room.
- **Stateful puzzles that can become unsolvable** (Hypixel's Boulder). Every one needs a reset lever and should be generated from a solved state backwards.
- **Arena doors that never reopen** (Minecraft Dungeons bug reports). Reopen on a timer as a fallback, and say why the doors are shut.
- **Lag when a chest opens** (When Dungeons Arise exploration maps). Never search for a structure at loot time.
- **The comment players make most often** is "seen it already". The cheapest fix is varying the skin per theme, not the logic: Valhelsia and Repurposed Structures exist for exactly that.

## Suggested order

1. **A first batch of small ones:**
   - level feelings;
   - the wider trap pool, with tells;
   - puzzle blessings;
   - take one of three;
   - foreshadowing windows;
   - one-way valves.
2. **Ice fill, teleport maze and ordered kills**: three Hypixel-proven puzzles that reuse the puzzle-room machinery.
3. **Cycle types on the planner**: the big structural piece. Valves, windows, patrols and false goals then fall out of it.
4. **Seals for the deepest door**, to give a whole run an arc.

## Sources

The full research reports, with links, came from three surveys. The main
references:

- Dormans' cyclic generation: Boris the Brave, "Dungeon generation in Unexplored" (boristhebrave.com, 2021).
- Brogue: the level generation wiki, and Anderoonies' reimplementation notes.
- Shattered Pixel Dungeon: the special rooms wiki.
- Dungeon Crawl Stone Soup: portal vaults on crawl.chaosforge.org.
- Hypixel Catacombs puzzle list: hypixelskyblock.minecraft.wiki.
- AD&D DMG Appendix A: 1eonline.info.
- Justin Alexander, "Xandering the Dungeon" (thealexandrian.net).
- Goblin Punch, "Dungeon Checklist".
- Necropraxis, "Overloading the Encounter Die".
- Wikis and pages for Twilight Forest, L_Ender's Cataclysm, Mowzie's Mobs, YUNG's, When Dungeons Arise, IDAS, Dungeon Now Loading, Wynncraft and Minecraft Dungeons.
