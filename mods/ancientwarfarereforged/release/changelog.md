# Ancient Warfare Reforged 1.0.0 (NeoForge 1.21.1)

First release of Ancient Warfare 2, rebuilt for Minecraft 1.21.1 on NeoForge. All five modules come in one file: Core, Vehicles, Automation, NPCs and Structures.

**Requires:** Spectre Factions 1.0.0+ and Spectre Lib 1.1.0+.

## Ported from 1.12.2
- **Core:** research, the engineering bench, tools, the in-game manual and the JEI recipe category.
- **Automation:** worksites, the warehouse and torque power.
- **Vehicles:** siege engines, boats and ammunition.
- **NPCs:** workers, soldiers, couriers, traders and faction NPCs.
- **Structures:** only the craftable building pieces (7 gate types, coin stacks, fire pits and totems). The structure templates, scanner, builder and world generation aren't included.

## Factions
- NPC teams now run on the Spectre Factions mod instead of Ancient Warfare's old team system.
- Faction relations are hostile, neutral, friendly or allied. NPCs leave neutral factions alone and help friendly and allied ones in a fight.
- Guards have modes: Passive, Defend, Guard and Aggressive.
- Ancient Warfare's NPC factions (Vikings, desert, Romans and more) are real factions with reputation.

## Settlements (replaces town hall upkeep)
- Mark out your land with boundary markers instead of relying on a town hall's range.
- A settlement ledger and linked storage feed your people.
- Hunger is gentler: NPCs slow down when hungry, eat early and starve only late.
- **Town hall screen:** tabs for overview, members, jobs, guards and morale.
- **Daily life:**
  - A job board with worker roles.
  - Beds and sleep.
  - Gathering at the campfire.
  - Rain shelter and a night watch.
- **Defence:** an automatic alarm when hostiles enter your land (bells ring), guards rally and patrol the border, and a guard captain buffs your defenders.
- **Couriers** haul goods around the settlement on their own.
- **Settlement morale** from nine factors raises or lowers work speed by up to 20%.

## Living worksites
- Workers do the work themselves: they walk to the job, swing their tool, break blocks and carry the drops back. They walk and climb ladders to reach the job instead of flying.
- Farms work on natural terrain, no flat-field setup needed.
- **Lumberjacks** fell whole trees and replant. Player-built log structures are left alone.
- **Quarry:** four mine styles (open quarry, shaft, branch and strip mining), with ladder and torch slots. Miners climb down and back out by ladder.
- **Fishers:** shore fishers, and boat fishers who sail out from a fishing dock.
- **New worksites:** Apiary, Smelter, Market Stall, Inn and Stable, plus a staffed animal farm.
- `machines_work_alone` in the config brings back the old worksites that work without NPCs.

## Vehicles
- Mouse steering with a chase camera, and smoother controls.
- Multiple seats with roles; switch seats with the number keys.
- Horse-drawn carts and wagons you can hitch up.
- **Ships with walkable decks:** a fishing boat, a cog and a war galley, with world wind, rowers and mounted turrets.

## Traders and new NPCs
- Market stalls run by your trader, with sell offers and buy orders.
- Prices depend on reputation: friendly factions get 10% off, allies 20% off, and hostile factions won't trade.
- Faction caravans travel with a trader, a horse, a wagon and two guards.
- **Trader wagon routes** load and unload between your storages.
- **New NPC types:** innkeeper, stable hand and guard captain.

## NPC personality
- Every NPC is born with a name, like "Guard Captain Rory", "Footsoldier Ren" or "Bill the Miner". Faction NPCs get names that fit their culture.
- **Talk to NPCs** to see what they're doing and thinking, and what happened to them recently.
- **Personal morale:** each NPC has its own morale from food, its bed, sleep, injuries, inn meals, friends, tools, idleness and fear. Its morale tab shows why, and morale sets how fast it works.
- NPCs swing their tools at a natural pace while working.

## Known issues
- No JEI "move items into the grid" button for the engineering bench yet.
- NPCs can't crew ships yet.
- Hitch ropes aren't drawn, and vehicle seat assignments reset when the world reloads.
- NPCs hold their role's tool and don't switch tools for each task.
- Hunger counts twice toward a worker's speed.
- Killing a caravan after placing a buy order with it can give free goods (it costs reputation).

## Credits
Original Ancient Warfare 2 by shadowmage45, P3pp3rF1y and contributors. 1.21.1 port and rework by CaptTracker. Licensed GPL-3.0-or-later.
