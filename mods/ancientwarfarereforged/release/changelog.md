# Ancient Warfare Reforged 1.0.0 (NeoForge 1.21.1)

First release of Ancient Warfare 2, rebuilt for Minecraft 1.21.1 on NeoForge. All five modules come in one file: Core, Vehicles, Automation, NPCs and Structures.

**Requires:** Spectre Factions 1.0.0+ and Spectre Lib 1.1.0+.

**Optional:** JEI, Jade or WTHIT (neither Jade nor WTHIT is needed; the look-at tooltips only appear if you install one).

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
- **Town hall screen:** tabs for overview, census, members, jobs, guards and morale.
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

## Production chains
- **Four new production worksites,** each worked by an NPC and stocked and emptied by couriers: the **Sawmill** (logs to planks, 6 per log by default, and optionally slabs, stairs, sticks, fences and doors), the **Bakery** (bread, cake, pumpkin pie and cookies, with fuel), the **Smithy** (iron, gold and diamond tools, weapons and armour, leather armour, and repair of worn gear) and the **Tannery** (rotten flesh and rabbit hide into leather; flesh needs water nearby).
- **New worker roles:** the smith and the tanner. The craftsman works the sawmill and the bakery.
- **Recipe screen:** every recipe has an on/off switch and a "keep N in storage" number, with the stock and the site's status shown.
- **The settlement tells production what it needs:** tools for workers without one, swords and armour for soldiers, food, and a reserve of planks, stone and glass. Sites make what is needed first, couriers bring those inputs first, and the hall Overview lists the needs and who is making them. A worker waiting for a tool says which site is making it.
- **Master smiths** sometimes save an ingot. Workers now take the best tool in storage, not the first.
- **Config:** the `production` section of `ancientwarfareautomation-worksites.toml`, `demand_enables_recipes`, and the `demand` section of `ancientwarfarenpc-settlements.toml`.

## Settlement expansion and builders
Your settlement can now plan and build its own houses and workshops. (This is a new settlement builder. The old Ancient Warfare structure templates and scanner are still not included.)
- **Building zones:** hold the **Building Zone Tool** inside your land and click two corners to make a plot. Sneak and use on a plot removes it. Builders only build inside plots, and while you hold the tool you see your plots, the buildings and the rectangle you are marking.
- **The planner:** every 30 seconds each settlement looks at what it lacks and plans at most one building: housing for homeless people (a tent while the settlement is small or poor, otherwise a house in your preferred style), a storehouse when the storage is nearly full, a workshop when a need or a worker role has no site (smithy, bakery, sawmill, tannery, smelter and more), a chapel for a bigger settlement without a priest, and upgrades from tent to house. The better building is built first, then the old one is taken down and its blocks go back to storage.
- **Building tab:** the town hall has a new Building tab. Switch auto planning on or off, choose automatic or manual approval and the preferred style, list and remove plots, and watch the queue: each build shows its state, progress and missing materials, with Approve, Show, Up, Down and Cancel. You can also queue any design by hand, pick the plot, the turn and the position, and see a ghost of it in the world.
- **Designs:** 22 built-in designs ship with the mod: canvas and hide tents, small and medium houses in timber, stone, desert and Norse styles, storehouses, a sawmill, bakery, tannery, smithy, smelter house, farmhouse, inn, stable, market stall and a chapel. Workshops come with their worksite block already inside. Server owners can add their own in a datapack folder (`data/<namespace>/settlement_designs/`).
- **Blueprint Scanner:** click two corners of one of your builds and then the entrance, and name it, pick its category and style and mark its worksite block. The build is saved as your own blueprint and the planner and the Building tab can use it. Only blocks are saved: chests are copied empty, and fire, portals and other blocks that have no item are left out. Air inside the box is saved as "clear this", so builders level the site.
- **Builders:** a new worker role. Set a worker to Builder on the hall's Role button, or spawn one with an iron hammer. Builders clear the site, level the ground and place the building block by block, bottom up, up to three builders per job. They walk and climb (no flying) and put up scaffolding for high places, then take it down again.
- **Materials come from settlement storage.** There is no free building. A builder walks to a storage that holds what the next blocks need, carries it and places it. The planner posts the materials as settlement needs, so sawmills and couriers supply them. When a material runs out the builder places other blocks first, tells you what is missing and after a while gives the job up until the storage is restocked.
- Finished buildings count straight away: beds and chests join the settlement, the worksite block belongs to you, and a chapel makes devout NPCs happier. Builders earn Building skill experience, and skill, traits, morale and hunger change their speed.
- **Config:** the sections `building_designs`, `building_planner` and `builders` of `ancientwarfarenpc-settlements.toml`.

## Families and children
- **Surnames:** every NPC you own now has a family name from its culture, like "Bill Carter". The full name shows on the talk screen, the census and the Jade/WTHIT tooltip. The nameplate keeps its style, and a name tag still wins.
- **Couples:** two adult members who spend time together at the gathering point can become partners, with a thought, a message to you and hearts. There is no gender rule: any two adults can pair up. Sociable NPCs bond faster and loners slower. Partners share a surname and gather at the same spot. When one dies, the survivor grieves and may find a new partner after a few days.
- **Homes:** a couple claims two beds, a free house first, then a tent, then two loose beds. If none is free, the planner builds a house for them. Home life, a partner nearby and children around add morale, and grief takes it away for 3 days.
- **Children:** a couple with a home, good morale, a free bed and enough food in storage may have a child. A child is half size, takes the family surname, gets traits from both parents and a small head start in a parent's best skill. Children have no role and take no orders. They play near home, follow their parents or other children, run from hostiles, sleep in the family bed and eat half as much as an adult.
- **Growing up:** a child becomes a working adult after **2 in-game years. A year is 96 days by default (the Serene Seasons year), so 192 days to adulthood.** It keeps its name, traits, skills and bed, and can later become a partner. The talk screen, the census and the Jade/WTHIT tooltip show its age in years and days.
- **Population:** children count toward population, need beds and food (half a member's share), and losing a family member hurts morale.
- **Config:** the `families` section of `ancientwarfarenpc-settlements.toml`: `families_enabled`, `year_length_days` (96), `years_to_adulthood` (2.0), `couple_min_population`, `couple_min_morale`, `couple_bond_seconds`, `couple_chance_per_day`, `couple_social_radius`, `couple_sociable_bonus`, `couple_loner_factor`, `partners_share_surname`, `widow_repartner_days`, `grief_days`, `grief_morale`, `partner_morale`, `partner_nearby_morale`, `partner_nearby_radius`, `family_home_morale`, `children_near_morale`, `children_near_radius`, `child_min_morale`, `child_min_food_days`, `max_children_per_household` (3), `birth_cooldown_days` (8), `birth_chance_per_day` (0.25), `child_hunger_factor` (0.5), `child_skill_head_start_xp`, `child_scale_start` (0.5), `child_play_radius`, `family_debug_fast_growth`.
- **Operators:** `/awsettlement birth`, `age`, `growup` and `families` (permission level 2) force a birth, age a child, let it grow up now and list the households.
- Families only work while the daily schedule is on (`schedule_enabled`).

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

## Sea trade and naval combat
- **Sea trade routes:** the new **Sea Route Chart** lists the docks a cargo ship sails to. Sneak-use it on one of your docks (or press the alt key 1 while looking at one) to add a stop; use it anywhere else to open the editor, where you set the wait at each dock and what is unloaded and loaded there. Put the chart in an owned trader's orders slot: the trader finds your cog within 32 blocks, takes the helm (or a seat beside your helmsman), plans each leg over the water, moors at each dock, unloads and loads between the ship's hold and the dock (and the settlement's linked storage), waits, and sails on, round and round (`sea_trade` settings).
- A stop that cannot be reached three times is skipped and you are told once. A dock that is not loaded makes the ship wait nearby. If the ship sinks, the chart is kept and the trader looks for another ship.
- **Faction trade ships:** factions now also send a cog to a settlement with a dock on deep water. It appears out at sea beyond view with a helmsman, rowers, guards and a trader, sails to the dock, trades for a while with the usual trade view and market stall buy orders, then sails off and vanishes. Killing the trader drops only the coins it earned and costs you standing. Operators can call one with `/awcaravan sea [faction]` (`sea_caravan` settings).
- **Ramming:** the war galley rams hard, the cog has a weak ram and the fishing boat has none. Damage grows with how fast the bow closes in on the target; the rammer takes a quarter of it back.
- **Grappling hook:** a new craftable item. Aim it at another ship from your ship to pull the two side by side and cross the decks. Cut the line by hitting the hook, with shears, a sword or an axe, or sneak-use the hook item to let go. NPC soldiers throw hooks at hostile ships and fight across the decks; crews defend their own deck.
- **Stance:** a "Stance: passive / aggressive" button in a ship's inventory screen. An aggressive helmsman rams hostile ships; a passive one whose ship is hit sails for your nearest dock.
- **Sinking:** when a ship sinks, half of its hold floats up as crates that bob for five minutes. Touch one with a ship or your body to collect it, or hit it to break it open.
- **Prizes:** a faction trade ship with no crew left alive is yours to take by using it (your standing with that faction drops). An abandoned one vanishes after a day.
- Needs matching client and server versions (new network message).

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

## NPC skills and traits
- **Skills:** your NPCs learn by doing. Each one has 11 skills (mining, woodcutting, farming, fishing, husbandry, crafting, trading, hauling, combat, medicine and building), and each skill has its own experience. Experience comes from the work: finished tasks at a worksite, hits, arrows and kills, trades, courier deliveries, medic heals, priest resurrections and feeding horses. NPCs from older worlds start at zero.
- **Ranks:** Novice, Apprentice, Journeyman, Expert and Master. A new rank shows up as a thought, lifts the NPC's morale for half a day and plays a sound with a few particles.
- **What ranks do:** a higher rank works faster (x0.9 at Novice to x1.4 at Master), has a small chance of one extra ore, crop or log, and a shorter wait for a fish. Fighters hit harder, take less damage from creatures and shoot straighter. Medics heal more.
- **Names:** the rank of an NPC's main skill is part of its name, like "Apprentice Miner Bill". A name tag still wins. The census and the Jade/WTHIT tooltip show the rank too.
- **Skills tab:** talk to an NPC to see every skill with its rank, a progress bar and its experience.
- **Traits:** every NPC is born with one or two traits out of 17: lazy, hardworking, brave, cowardly, glutton, frugal, devout, night owl, early riser, sociable, loner, quick learner, clumsy, strong, tough, green thumb and keen eye. Opposites never go together. Traits change work speed, hunger, fear, daily schedule, learning, fighting and morale, and the morale tab names the points a trait adds. NPCs from older worlds get their traits the first time they load.
- **Brave NPCs** never run from hostiles, and a brave worker with a sword or axe fights instead of hiding in an alarm. **Cowardly NPCs** spot danger from farther away and run earlier.
- **Night owls** and **early risers** shift their whole day, and night owls are picked for the night watch more often.
- The talk screen lists an NPC's traits with a line about each, and the census row and the Jade/WTHIT tooltip show them.
- Both are tuned in `ancientwarfarenpc-settlements.toml` (sections `skills` and `traits`). `skill_xp_multiplier` scales all experience (0 switches it off), `skill_rank_in_name` hides the ranks in names, and `traits_enabled` switches traits off.

## Quality of life
- **Census tab** on the town hall screen: one row for every member of the settlement with its role, mood, distance and direction from the hall (or "unloaded"), what it is doing, and problem icons (starving, hungry, no bed, no tool, no job, badly hurt, no path, alarm). Sort by name, role, mood or problems, and filter by problems or a single role. Click a row to make that NPC glow for 10 seconds with a particle beam leading to it (only you see it); an NPC that is unloaded or out of view gets its last known position in chat. The list refreshes every 2 seconds while the tab is open (`census_refresh_ticks`).
- **Jade and WTHIT support (optional):** look at an NPC to see its role, owner or faction, mood, current task and health. Look at a worksite (farm, quarry, tree farm, fishing, apiary, smelter, dock, market stall, inn, stable) to see workers out of slots, its status (working, idle, waiting for workers, tools or ladders, storage full, needs a bed and a table) and the settlement it belongs to. Each tooltip can be switched off in the Jade or WTHIT config screen.

## Known issues
- Not yet play-tested in a running game: couples, homes, births, children, growing up and the family morale factors (compiled and read only).
- Not yet play-tested in a running game: NPC skills, ranks and traits (the effects, the Skills tab and the rank in names).
- Not yet play-tested in a running game: the settlement planner, the Building tab, the Blueprint Scanner and builders (scaffolding, fetching from storage and taking down a building were compiled and checked by reading only).
- Not yet play-tested in a running game: NPC helmsmen, swimming out to a ship, and the cog's oars.
- A helmsman only looks ahead over the water. A bay or an island between the ship and the destination is steered round as far as it can see, and it gives up if it makes no headway.
- A helmsman needs water at least 2 blocks deep at the spot you click. Faction NPCs can't take the helm, and they only crew a ship whose helm is held by an ally.

## Credits
Original Ancient Warfare 2 by shadowmage45, P3pp3rF1y and contributors. 1.21.1 port and rework by CaptTracker. Licensed GPL-3.0-or-later.
