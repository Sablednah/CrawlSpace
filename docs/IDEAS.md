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

**Portcullis trap** (Sable; S). The same portcullis, open, over a doorway
or a corridor. It slams down once you have walked through, with a clang and
dust, sealing the way back. It is the "blocked retreat" cycle in a single
block.

- **What is behind it:**
  - an ambush, where the room wakes as the bars fall;
  - a survival room: hold out until the timer runs out;
  - nothing at all: just a longer way round.
- **The way out is always named:** "The portcullis has fallen behind you.
  A lever must raise it from somewhere ahead." A winch lever further on
  raises it again, and once the room is clear a timer raises it anyway.
- **Being under it as it falls** pushes you forward, never traps you
  inside a block.
- **In multiplayer** it splits the party. With LQ, a STR check heaves it up
  so the others can roll under.
- **Spotting it:** score marks on the floor where the bars land. With LQ, a
  WIS notice sees it before you cross, as with plates.

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

## 2b. Objective rooms, after Warhammer Quest (2026-10-09)

This comes from the Warhammer Quest Adventure Book (Games Workshop, 1995),
which Sable supplied. Its five objective rooms (Fighting Pit, Firechasm,
Fountain of Light, Tomb Chamber, Idol Chamber) each carry six different
objectives. That is 30 adventures from 5 set pieces, and it is what
CrawlSpace's objective rooms want to be: **a small number of grand rooms,
each with a list of jobs that can be done in it.** Both lists are data.

### How the book's rules carry over

- **It is always deep.** The objective card is shuffled in with six dungeon
  cards and six more go on top, so it can never be found early. For us it
  goes in the far half of the level's graph from the entry.
- **It has its own horde.** Entering it does not wake an ordinary room; it
  rolls on an Objective Room Monster Table, a bigger mixed group.
- **Finishing the objective opens the way on.** It is never a door that
  was there all along:
  - a secret door behind the statue;
  - a trapdoor in the plinth;
  - a tapestry burnt away;
  - the ceiling collapsing to daylight.

  For us that is the stairs down, so **the objective is what gates the next
  level**, rather than "find the exit".
- **Unfinished means pressure.** While the gate stays open, the tomb stays
  unbroken or the idol stays standing, more monsters keep coming. Some
  objectives have a clock (1D6 or 2D6 turns): a boss bar counting down.
- **Something is carried in.** A casket from the first room, a gem, a ring,
  a staff, a barrel. Carrying the barrel slows you, and drinking from it
  helps but leaves too little to finish the job. A vanilla item with
  `custom_data` (see the portcullis key) is all this needs.
- **Finishing changes the way back** (the "altered return" cycle):
  - the room collapses with two turns to get out;
  - a new door opens onto 1D6+2 fresh rooms to fight through to the surface;
  - smashing the idol makes every monster nearby flee, so the walk out is safe.
- **Some jobs need a particular hero.** "Only the Dwarf can throw the ring";
  "only the Wizard can seal the gate". With LQ that is a race or class, and
  without it anyone can do it.
- **Some checks are a party sum.** Overturning the idol needs everyone's
  1D6 plus Strength to reach 24; the battle of wills needs everyone's dice
  to total 13. With LQ that is a group check. Without it, it is a number of
  players, or the same work done slowly alone.
- **Each room has a line to read aloud at its door:** "A chill breeze blows
  out from this room, and you can just make out a set of stairs leading up to
  a stone slab". We send it to chat as you reach the doorway, so the room
  announces itself before you step in.

### The five rooms, and jobs for each

**Fighting Pit.** A sunken arena under one hanging lantern.
- Each player who drops in brings a champion up beside them, at most three.
  When the first champion dies, they all vanish. Treasure lies on the pit
  floor, and the trapdoor at the bottom is the way down.
- A guardian that stays in the pit unless it is shot at, so shooting from
  the rim is a choice with a cost.
- A gate in the pit spews monsters until it is sealed. Sealing is an
  attempt every few seconds, and a bad roll undoes it.
- Prisoners in the cells beneath, freed for a reward.

**Firechasm.** Lava, an old rope bridge, and a dragon statue on the far side.
- The bridge is the hazard: `Crumbles` planks, and **LQ:** DEX or Athletics
  to cross without slipping. A slip burns you, drops something you carry, or
  leaves you swinging back to the side you started on.
- **Throw the relic into the fire.** The room starts to collapse, a secret
  door opens behind the dragon, and a boss-bar countdown runs.
- **Destroy the bridge behind you.** Waves keep arriving on the near side
  until its blocks are broken.
- **Return the dragon's eye.** Set a gem in the statue's socket (an item
  frame) and a trapdoor in the plinth opens on the hoard.
- **Stop the engineer.** A mob on the far side runs for a lever, and you
  have until the countdown ends to reach him.

**Fountain of Light.** A glowing pool: water, light blocks, gargoyle heads.
- **Pour in what you carried** and the water flows again, which opens the door.
- **Cleanse it.** Remove the corrupted blocks in the basin. Each costs the
  player who takes it health, and nobody may take two.
- **Drink once.** Usually a full heal; rarely it burns you from inside. The
  odds are posted beside it.
- **Look once.** It shows something: the direction of the next level's lair,
  or of the key room.
- **Fill a bottle and carry it out.** Leaning on a gargoyle as you fill it
  slides a door open.

**Tomb Chamber.** Steps up to a stone slab.
- **Lay relics on the lid**, gathered on the way. Each attempt may summon a
  wave; once a try summons nothing, the spirit is laid.
- **Lift the lid.** The boss rises, unless a party check (**LQ:** WIS)
  pins it first.
- **The key is in the tomb, and the chest is in the first room.** You walk
  back through the cleared level to open it. This is Sable's portcullis and
  key run in reverse, and rooms already cleared stay quiet on the way back.
- **Break the tomb**, a block with a lot of health. While it stands, events
  keep coming.

**Idol Chamber.** Steps up to an idol at the far end.
- **Rescue the captive before the rite.** A chance every minute that it is
  too late, heard as screams down the corridors, and the room turns to
  vengeance.
- **Overturn the idol** as a party (**LQ:** a summed STR check). Smash it
  and every monster on the level flees.
- **Kill the shaman.** He keeps away from you and casts from range.
- **Stop the summoning.** Kill the channeller before the timer ends, or he
  becomes the boss.
- **Wrest the sword from its hand** (**LQ:** STR). Failing, the idol strikes
  you.

### Smaller things from the same book

- **Fleeing has a price.** Its Escaping table makes leaving mid-adventure a
  gamble: lost gold, wounds, wandering in a circle back to your friends. That
  suits a "flee" lever or a recall item.
- **Monster tables are mixed groups**, such as a Minotaur with orcs and
  archers, or bats, spiders and rats together. They read as warbands rather
  than singletons.

## 2c. Farming, replay and per-player loot (Sable's notes, 2026-10-09)

Preventing farming and offering replay (regenerating, resetting, heroic
re-entry) pull against each other, so decide them together. A rule that
holds both: **a reward is once per player per run**, and a run ends when the
dungeon or level is regenerated. Replay then pays out again, and standing
still does not.

- **Per-player chests.** CrawlSpace already places chests with a loot table
  rather than rolling their contents (`Site`, `setBlockEntityLootTable`), so
  an unopened chest is still undecided. **Lootr** would convert them with no
  change on our side. Lootr has to be on the client as well, though, so it
  can only be an optional compatibility layer, never the design. The
  vanilla-first way is to do it ourselves:
  1. Opening a dungeon chest is caught on the server.
  2. A per-player inventory is rolled from the loot table on that player's
     first open and stored.
  3. A normal chest screen opens on it, with the lid animation sent as a
     block event.

  The client sees an ordinary chest, and every player gets their own
  contents.
- **The vanilla vault block** already gives loot once per player (it
  remembers who it has rewarded). It needs a key, and the key item is
  configurable, so it suits a lair's hoard, where the key comes from the
  boss or the key room.
- **Trial spawners instead of spawners.**
  - The trial spawner is vanilla and vanilla clients render it.
  - It spawns a fixed wave scaled by the number of players nearby, ejects
    loot when the wave is cleared, then goes dormant for a configurable
    cooldown. That cooldown can be long enough to make it a one-off.
  - It cannot be picked up with silk touch, so it cannot be carried home as
    a farm.
  - Its mobs and loot come from its config, which is datapack data, so each
    theme can give it its own config.
  - The ominous variant gives a harder second tier.
  - Waking on approach is how it behaves already.

## 2d. Notes on the above

- **The portcullis trap is a valve**: a one-way edge in the cycle sense.
  The keyed portcullis is a lock.
- **Objective rooms gate the stairs down on every level, and the bottom one
  is the finale.** In Warhammer Quest they read as finales only because they
  are the way out. The deepest level's objective room is the reason to reach
  the bottom: the biggest, double-height set piece. Earlier levels' objective
  rooms are smaller versions that open the way on.

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
