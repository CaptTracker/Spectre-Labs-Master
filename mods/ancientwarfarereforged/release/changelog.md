# Ancient Warfare Reforged 1.0.0 (NeoForge 1.21.1)

First release of Ancient Warfare 2, rebuilt for Minecraft 1.21.1 on NeoForge. All five modules come in one file: Core, Vehicles, Automation, NPCs and Structures.

**Requires:** Spectre Factions 1.0.0+ and Spectre Lib 1.1.0+.

## Ported from 1.12.2
- **Core:** research, the engineering bench, tools, the in-game manual and the JEI recipe category. With JEI, the "+" button fills the grid of the engineering bench, the auto crafting block and the warehouse crafting station (shift-"+" fills it with as many sets as you have, up to a stack).
- **Automation:** worksites, the warehouse and torque power.
- **Vehicles:** siege engines, boats and ammunition.
- **NPCs:** workers, soldiers, couriers, traders and faction NPCs.
- **Structures:** only the craftable gates (7 gate types). Coin stacks, fire pits and totems are not included, and neither are the structure templates, scanner, builder and world generation. Coins are plain currency items.

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
- Multiple seats with roles; switch seats with the number keys. Riders keep their seats when the world reloads.
- Horse-drawn carts and wagons you can hitch up. A rope is drawn from the cart to each hitched horse.
- **Ships with walkable decks:** a fishing boat, a cog and a war galley, with world wind, rowers and mounted turrets. The cog now has four rower seats.
- **NPC ship crews:** aim the command baton (Move mode) at a ship to send the selected NPCs aboard as rowers, gunners or passengers. Soldiers man the guns first: they aim, lead moving targets and fire at enemies in range (`ship_gunners_fire`, `ship_gunner_range`). Idle soldiers near a ship their owner is riding climb aboard on their own (`ship_auto_crew_soldiers`, `ship_auto_crew_range`). A Move order on land puts them back ashore.
- **NPC helmsmen:** an NPC ordered aboard a ship with a free helm takes the helm (`ship_helmsmen`). Give it a Move order at water and it sails there, slows down and stops near the spot, looking ahead to keep out of shallows and the shore, and it gives up and stops the ship if it can't get on (`ship_helm_stuck_seconds`). A Move order at another ship or boat makes it follow that vessel. Rowers add speed as they do with you at the helm. Take the helm back with seat key 1.
- **Boarding from the water:** ordered NPCs, and soldiers that answer their owner's ship, now swim out to a ship in open water and walk across the deck to their seat (`ship_board_range`).
- **Eating aboard:** a hungry crew member eats from the ship's storage, then from what it carries, without leaving its seat (`ship_crew_eat`).
- **Faction crews:** idle faction soldiers and archers climb aboard a ship whose helm is a player allied to their faction, man a gun or oar and fire at the faction's enemies. They leave when no allied helm is left (`ship_faction_crew`).
- **Cog oars:** the cog now has two oars a side that row while its rower seats are manned.

## Traders and new NPCs
- Market stalls run by your trader, with sell offers and buy orders.
- Prices depend on reputation: friendly factions get 10% off, allies 20% off, and hostile factions won't trade.
- Faction caravans travel with a trader, a horse, a wagon and two guards. A caravan's wagon starts empty: goods for your buy orders are made only when the stall pays for them, so killing a caravan drops just the coins it earned.
- **Trader wagon routes** load and unload between your storages.
- **New NPC types:** innkeeper, stable hand and guard captain.

## NPC personality
- Every NPC is born with a name, like "Guard Captain Rory", "Footsoldier Ren" or "Bill the Miner". Faction NPCs get names that fit their culture.
- **Talk to NPCs** to see what they're doing and thinking, and what happened to them recently.
- **Personal morale:** each NPC has its own morale from food, its bed, sleep, injuries, inn meals, friends, tools, idleness and fear. Its morale tab shows why, and morale sets how fast it works.
- NPCs swing their tools at a natural pace while working.
- **Workers pick the right tool for each job:** an axe for logs, a pickaxe for stone, a shovel for dirt, a hoe for tilling and shears for leaves and wool. They take it from their hand, a small tool belt or their pouch, or fetch one from your storage. They take their own tool back between jobs (`tool_switching` in the config).
- Hunger now counts once toward work speed, through the NPC's morale. The config keys `hungry_work_factor` and `starving_work_factor` are gone.

## Known issues
- Not yet play-tested in a running game: NPC helmsmen, swimming out to a ship, and the cog's oars.
- A helmsman only looks ahead over the water. A bay or an island between the ship and the destination is steered round as far as it can see, and it gives up if it makes no headway.
- A helmsman needs water at least 2 blocks deep at the spot you click. Faction NPCs can't take the helm, and they only crew a ship whose helm is held by an ally.

## Credits
Original Ancient Warfare 2 by shadowmage45, P3pp3rF1y and contributors. 1.21.1 port and rework by CaptTracker. Licensed GPL-3.0-or-later.
