# Ancient Warfare Reforged

**Ancient Warfare 2, rebuilt for Minecraft 1.21.1 on NeoForge, with a big immersion rework.**

Found a settlement, hire workers who really do the work, defend your land with soldiers and guard captains, trade with faction caravans, and take siege engines and warships into battle.

This one file holds all five Ancient Warfare modules: **Core, Vehicles, Automation, NPCs and Structures**.

> Unofficial port. Ancient Warfare 2 was created by shadowmage45, P3pp3rF1y and contributors. This port is not affiliated with or endorsed by them.

## Requirements
- Minecraft 1.21.1 with NeoForge
- Spectre Factions (required)
- Spectre Lib (required)
- JEI (optional)
- Jade or WTHIT (optional, for look-at tooltips on NPCs and worksites)
- Ice and Fire, Weather2 (with CoroUtil), Serene Seasons (with GlitchCore), Farmer's Delight, Dynamic Trees, JourneyMap and Xaero's Minimap (all optional, see Optional mod support)

## Settlements
- Mark out your land with **boundary markers**, with no town hall range limit.
- A **settlement ledger** and linked storage keep your people fed.
- Hunger is forgiving: NPCs slow down when hungry and only starve after a long time.
- **Daily life:** a job board with worker roles, beds and sleep, campfire gatherings, rain shelter and a night watch.
- **Defence:** an alarm sounds and bells ring when hostiles enter your land. Guards rally and patrol the border, and a guard captain leads them.
- **Logistics:** couriers haul goods between storages on their own.
- **Morale:** settlement morale and each NPC's own mood speed up or slow down their work.
- **Census tab:** the town hall lists every member with its role, mood, location, task and problems (hungry, no bed, no tool and more). Sort, filter, and click a row to make that NPC glow and see where it is.
- **Build your own village:** mark **building zones** with the Building Zone Tool and the settlement plans houses, storehouses, workshops and a chapel on its own, with 22 built-in designs in timber, stone, desert and Norse styles. Tents grow into houses as the settlement grows.
- **Builder NPCs** put each building up block by block, with materials taken from your settlement storage (nothing is free). The hall's **Building** tab shows the queue, what is missing and lets you approve, reorder or queue builds by hand.
- **Blueprint Scanner:** save any build of yours as a blueprint the settlement can use.

## Workers who really work
- Workers walk to the job, swing their tools, break blocks and carry the drops home. They use ladders to get around.
- Farms work on natural, uneven terrain.
- **Lumberjacks** fell whole trees and replant saplings.
- **Mining:** quarries in four styles (open pit, shaft, branch and strip), with miners who climb ladders down and back out.
- **Fishing:** shore fishers, and boat fishers who sail out from a fishing dock.
- **Worksites:** Apiary, Smelter, Market Stall, Inn, Stable and a staffed animal farm.
- **Production chains:** Sawmill, Bakery, Smithy and Tannery, with a recipe screen ("keep N in storage"), smith and tanner workers, and a settlement needs list that tells them what to make.

## NPCs with personality
- Every NPC has a name, like "Guard Captain Rory" or "Bill the Miner".
- **Talk to them** to see what they're doing and what's on their mind.
- **Each NPC has its own morale:** food, a bed, sleep, injuries, friends and fear all count.
- Workers switch to the right tool for each block.
- **Skills and ranks:** NPCs learn mining, woodcutting, farming, fishing, husbandry, crafting, trading, hauling, combat and medicine by doing the work. They rise from Novice to Master, work faster and fight better, and the rank shows in their name, like "Apprentice Miner Bill".
- **Traits:** every NPC has one or two quirks, like lazy, hardworking, brave, cowardly, glutton, night owl, quick learner, clumsy or tough. They change how an NPC works, eats, fights and feels, and the talk screen lists them.
- **Families:** NPCs have surnames, fall in love (any two adults can pair up), share a home, have children and grieve when a loved one dies. Children play around the settlement, grow up in 2 in-game years (192 days by default, set in the config) and become workers.
- **New NPC types:** trader, innkeeper, stable hand and guard captain.
- **Jade and WTHIT (optional):** look at an NPC to see its role, mood, task and health, or at a worksite to see its workers, status and settlement.

## Factions and trade
- NPC teams run on **Spectre Factions**. Faction relations are hostile, neutral, friendly or allied, and NPCs help their friends in a fight.
- Ancient Warfare's NPC factions, like Vikings, desert nomads and Romans, are real factions with reputation.
- Market stalls have sell offers and buy orders, and prices depend on reputation.
- Faction caravans travel the land with a trader, a wagon and guards.
- With JEI, the "+" button fills the crafting grid of the engineering bench, auto crafting block and warehouse crafting station.

## Vehicles and ships
- Siege engines with **mouse steering** and a chase camera.
- Horse-drawn carts and wagons.
- **Ships with walkable decks:** a fishing boat, a cog and a war galley, driven by wind and rowers and armed with mounted turrets.
- **NPC ship crews:** send your NPCs aboard with the command baton, even out in open water: they swim out to the ship. They row, ride as passengers, eat from the ship's stores, and soldiers fire the turrets at your enemies. Allied faction soldiers join a friend's ship too.
- **NPC helmsmen:** an NPC can take the helm and sail to a spot you point at over the water, or follow another vessel. You can take the helm back at any time.
- **Siege towers and mantlets:** push a tower to a wall, climb its ladder and lower the drop bridge onto the wall top; hide archers behind a mantlet that stops arrows from the front. Soldiers and archers you send at a wall crew your siege engines on their own.
- Hitched horses are roped to their cart, and riders keep their seats after a reload.
- **Sea trade:** put a **Sea Route Chart** in a trader's orders slot and they sail your cog from dock to dock, unloading and loading cargo at each stop. Factions send their own trade ships to your docks.
- **Naval combat:** the war galley (and, weakly, the cog) ram other ships, **grappling hooks** pull two ships together so crews can fight across the decks, a stance toggle makes your helmsman ram or flee, and a sunk ship's cargo floats up in crates. A faction trade ship left with no crew can be taken as a prize.

## Structures
Only the craftable gates are included: 7 gate types. There are no coin stacks, fire pits or totems, and the original structure templates, builder and world generation are **not** included.

## Optional mod support
Nothing below is needed. Each part only works when the other mod is installed, and each has an `enabled` switch in the config.
- **Ice and Fire:** its dragons and monsters count as threats. Dragons raise the alarm from far away, civilians run from them, archers and siege crews shoot them first, and unbrave melee soldiers keep their distance. Dragonbone and dragonsteel gear works for soldiers.
- **Weather2:** settlements warn of severe storms, tornadoes and sandstorms. NPCs go indoors, outdoor work pauses and ship helmsmen head for a dock.
- **Serene Seasons:** farms only sow what is fertile in the current season, winter makes NPCs eat more and the cold hurts the morale of NPCs without a home, and the hall shows the season.
- **Farmer's Delight:** farms plant its crops and rice, knives count as tools, hearty meals give NPCs their effects and the innkeeper cooks in a kitchen room you build.
- **Dynamic Trees:** the lumberjacks of a tree farm cut its trees down so that they really fall, collect the drops and plant its seeds.
- **Xaero's Minimap:** a Share waypoint button posts a clickable waypoint to your own chat.
- **JourneyMap:** your settlements show on the map with a waypoint and their border.
- **Mo' Bends and Dynamic Surroundings:** NPCs work with them as with vanilla mobs; nothing to set up.

## Good to know
- Back up your worlds. This is a first release.
- Config files: `ancientwarfarenpc-settlements.toml` and `ancientwarfareautomation-worksites.toml`. Set `machines_work_alone = true` to bring back worksites that run without NPCs.
- Known issues are listed below.

## Controls
All keys can be rebound in **Options > Controls**. Vehicle keys only act while you ride a vehicle, and the item keys only while you hold an Ancient Warfare item, so they don't clash with your normal keys.

**Driving (every vehicle)**
| Key | Action |
|---|---|
| W / S | Throttle forward / reverse |
| A / D | Steer (with mouse steering on, A/D turn the hull of vehicles that have a turret) |
| C | Switch between mouse steering (the vehicle turns where you look) and classic key steering |
| 1 to 9 | Switch seats on vehicles with more than one seat. On a ship, 1 takes the helm back from an NPC helmsman |

**Weapons (siege engines and ship gun seats)**
| Key | Action |
|---|---|
| Space | Fire |
| R / F | Aim up / down |
| Z / X | Turn the turret left / right |
| T / G | Previous / next ammo |
| V | Open the ammo selection |

**Siege tower**
| Key | Action |
|---|---|
| W / S in a ladder seat | Climb up / down |
| B | Lower or raise the drop bridge (driver, ladder or platform seat) |

**Items and worksites**
| Key | Action |
|---|---|
| Z, X, C, V, B | Alt-use keys 1 to 5 for Ancient Warfare items (for example, alt key 1 adds a dock to a Sea Route Chart) |
| F8 | Show or hide worksite work areas |

**With the mouse**
| Action | Result |
|---|---|
| Right-click a vehicle | Board it |
| Sneak + right-click a vehicle | Open its inventory (ships: the Stance passive / aggressive button) |
| Right-click a cart with a lead while leading a horse | Hitch the horse (sneak + right-click with a lead unhitches) |
| Use a Grappling Hook from your ship at another ship | Throw a line (sneak-use the hook to let go) |
| Right-click an NPC you don't own, or sneak + right-click your own with an empty hand | Talk to it |
| Command baton, attack mode at a wall or gate | Start a siege with your towers, mantlets and rams |

## Commands
| Command | Who | What |
|---|---|---|
| `/awsettlement birth`, `age`, `growup`, `families` | Operators | Force a birth, age a child, grow a child up now, list households |
| `/awcaravan [faction]` | Operators | Call a faction caravan to your settlement by land |
| `/awcaravan sea [faction]` | Operators | Call a faction trade ship |
| `/awresearch add/remove <player> <goal>`, `fill <player>`, `clear <player>` | Operators | Give or take research |
| `/awnpcdebug` | Operators | Toggle the NPC AI debug display |

## Known issues
**Settlements and production**
- A recipe switched off is still made when the settlement needs it. Set `demand_enables_recipes = false` for a hard off.
- Nothing makes copper gear, netherite upgrades, saddles or bundles.
- Archers don't take arrows from storage: NPC archers shoot without using up arrows, so only bows are handed out.
- A hand-built house or chapel is found by its walls: an enclosed room with a door (a roof is implied by the size limit, `hand_built_room_max_cells`). Open or very large buildings are not recognised. There are no schools.

**Building**
- The farmhouse, the lumber camp and the mine entrance need their worksite block (crop farm, tree farm, quarry) in the storage, like the sawmill and the smithy. A planned build waits for it. The farmhouse garden has no water source, and the quarry of the mine entrance follows the normal quarry rules (ladders, digs down).
- Deleting a blueprint leaves the finished builds of it in the settlement record without a design.

**Ships**
- A helmsman plans a water route only over loaded chunks and legs the route planner covers; with no route it looks ahead as before and tells you. The spot you click needs water at least 2 blocks deep.
- Faction NPCs can't take the helm, and they only crew a ship whose helm is held by an ally.
- NPCs cross between grappled decks with a short leap. Gunners and archers don't board.
- Sea routes and trade ships only run near a player (in loaded chunks), and legs longer than 256 blocks aren't planned.

**Sieges**
- The drop bridge is a staircase of flat planks: it reaches a wall top from 3 blocks below to 1 above the platform, and stepping up 1 block needs a jump. After a siege the bridge stays down.
- NPC siege crews plan routes over open ground only and don't open gates.
- A mantlet's shield only blocks other entities by being in the way of the mantlet itself; it does not push them.
- Faction NPCs don't use siege engines yet (there are no faction raids).

**Art**
- New blocks and items reuse vanilla or existing textures (sawmill, bakery, smithy, tannery, grappling hook, crates, scanner, siege engines). Census problem icons are coloured letters.

## License and credits
GPL-3.0-or-later, the same as the original. Source code: https://github.com/CaptTracker/Spectre-Labs-Master

The original mod is by shadowmage45, P3pp3rF1y and contributors. Textures, models and sounds come from the original Ancient Warfare 2 and keep its credits. The 1.21.1 port and rework are by CaptTracker.
