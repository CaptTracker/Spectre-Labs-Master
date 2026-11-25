# Ancient Warfare rework roadmap (stage 4)

Goal (owner, 2026-11-19): a more immersive mod, with these parts reworked or added:
- the whole colony system, starting with upkeep that is no longer limited by town hall range;
- NPCs that are more autonomous and smarter, plus new types such as traders;
- worksites that are less bland: farms on natural terrain, several mine styles, fishers using boats;
- more vehicles and better controls: mouse steering, bigger ships, horses and carts.

This plan comes from four code readings, one each for colony/upkeep, NPC AI, worksites and vehicles. Sizes run S < M < L < XL. Nothing here is built yet. Choices still open are marked **Q**.

## Why it feels the way it does today
- **Town halls.** A hall only reaches NPCs inside a box of ±100 blocks across and ±50 up and down, and only NPCs in loaded chunks. Each NPC keeps one fixed upkeep block. There is no settlement record anywhere; "the colony" is just whichever NPCs heard the last broadcast, which goes out every 100 ticks (`TownHallBlockEntity.broadcast`).
- **Hunger.** Hunger is a cliff. An NPC only fetches food at exactly 0, and stops working until it has some. It walks to its single upkeep block, and if that chest is short it stands idle (`NpcAIPlayerOwnedGetFood`, `NpcAIPlayerOwnedWork`).
- **Work and NPC behaviour.**
  - Work is driven by order items, or by a scan of 80×40×80 blocks every 200 ticks for the nearest worksite.
  - Day and night is just awake or asleep.
  - Couriers and traders walk fixed lists of waypoints.
  - A worker's job is decided by the tool in its hand.
- **Worksites.** These are torque-powered machines that edit blocks inside a rectangle.
  - Farms only work a single flat layer at the bottom of their box.
  - The quarry has one pattern: dig the whole column below it.
  - The fish farm is a dice roll on the vanilla fishing loot table, weighted by how much water is nearby. There is no boat and no treasure.
- **Vehicles.**
  - A vehicle is one entity with one seat.
  - Steering is A/D only; the mouse only aims the turret.
  - Steering input reaches the server only when a key is released or once per second.
  - Boats are 2.7 × 1.4 rafts with one rider.
  - The cart has no animal to pull it.

## Phases

Each phase builds and can be played on its own.

### Phase 1: Settlements and humane upkeep (the keystone, M to L)
- **Settlement record.** A `Settlement` saved data keyed by id holds the owner (a player or a spectrefactions faction), its core hall, its claimed area, its member NPCs, the storages linked to it, and its alerts. NPCs store the settlement id instead of a town hall position. Membership survives distance and unloaded chunks.
- **Hall and area.** The town hall becomes the settlement's core and its screen. Its area is a radius that upgrades or extra marker blocks can grow (**Q1**). It shows how many NPCs it has, the food in stock, NPCs who are hungry or homeless, the death and resurrection list, and the alarm.
- **Soft hunger.** NPCs eat early, below about 25%, from the nearest settlement storage that has food. Hungry NPCs work slower rather than stopping. Starvation damage comes only after a long grace period, and is a config option (**Q2**). Upkeep orders become optional overrides.
- **Factions.** A settlement can belong to a faction, and its members share it. Guard modes and relations apply to its residents.
- **Old worlds.** No 1.21 worlds exist yet, so there is a clean break with no migration from the old `townHall` tags.

### Phase 2: Autonomous NPC life (L)
- **Job board.** Each settlement has a job board that its worksites post to. Workers claim jobs that match their role and skill, with the load balanced between them. Orders stay as a manual override.
- **Real jobs.** The job becomes a stored role on the NPC, like a villager profession, instead of following the tool in its hand. The tool still gives a bonus.
- **Daily schedule.** NPCs work, eat, socialise at a gathering point (the market or a bard), and sleep in assigned beds. The schedule is driven by one `NpcAIScheduleGoal` over the existing goals.
- **Auto-hauling.** Couriers move worksite output to storage and bring inputs to sites without being told. Routing orders stay.
- **Threat response.**
  - Civilians flee to shelter.
  - Armed workers join a militia when the alarm sounds.
  - Guards patrol by themselves.
  - Soldiers rally and hold a line.
  - Faction guard modes drive all of this, so faction NPCs can now be peaceful too.
- **Pathing.** NPCs plan longer routes and know how to board boats.

### Phase 3: Living worksites (L)
- **Natural-terrain farms.** Farms scan each column for its surface, so slopes and natural ground get tilled and planted without flattening. This applies to the crop, fruit and tree farms.
- **Mine styles.** The quarry becomes a pattern you pick: open quarry (today's), a vertical shaft with ladders, a branch mine with side tunnels and torches, or a strip mine at a chosen Y level that follows ores.
- **Visible work.** Breaking animations, particles and item pops show when a site acts. If worksites become staffed (**Q3**), an NPC is actually seen doing the work.
- **New worksites.** An apiary, an orchard, a stonemason or smelter, a herder pen, and a market stall that ties into traders.

### Phase 4: Vehicles and controls (M to L)
- **Mouse and helm controls.**
  - The mouse steers: the vehicle turns towards where the camera points, and W/S is throttle.
  - Input is analog and is sent every tick.
  - There is a chase or helm camera.
  - The aim reticle stays.
- **Seats.** A vehicle gets a list of seats with roles (helmsman, gunner, passenger), so several riders and NPC crews can share one.
- **Horse-drawn carts and wagons.** You leash a horse or donkey to the cart and it follows the horse's hitch point. Storage carries over from the chest cart. NPC traders can lead them.
- **Bigger ships.** These are large entities with a walkable deck, several seats, and turrets mounted on hardpoints (**Q4**). Examples are a cargo cog, a war galley with rowers, and a small fishing boat.
- **Fisher boats.** These pair with phase 3. A dock worksite owns a boat, an NPC sails it to open water, and uses a real bite timer with treasure.
- **Models.** New vehicles use Blockbench or JSON-style models. The existing 19 models stay as they are.

### Phase 5: Traders and caravans (M to L)
- **Settlement traders.** Traders buy and sell, with prices and access set by faction reputation.
- **Caravans.** Travelling caravans with wagons move between settlements and NPC faction camps. Stock depends on the biome and the faction.
- **New NPC types.** An innkeeper, a stable hand, a guard captain, and a builder. The builder stays parked until structures come back.

## Owner decisions (2026-11-19)
- **Q1, land:** **placed boundary markers** decide the settlement's land.
- **Q2, hunger:** hungry NPCs **slow down, and only starve after a long grace period**. Starvation can be turned off in config.
- **Q3, worksites:** worksites are **staffed by NPCs who visibly do the work**. Torque becomes an optional speed boost.
- **Q4, ships:** big ships are **large entities with a walkable deck**, from fixed designs.

## Phase 1 spec

All of this lives in `ancientwarfarenpc`, package `settlement`.

### Settlements
- **Founding.** Placing a town hall founds a settlement for the placer's actor: their spectrefactions faction, or the player alone. The settlement is named after the owner by default, and can be renamed in the hall screen.
- **Data.** A `SettlementData` saved data, keyed by UUID, holds:
  - the owner `ActorId` and the owner player's UUID, for AW's `Owner`;
  - the dimension and hall position;
  - the marker positions;
  - the member NPC UUIDs, with each one's last known position and status;
  - the linked storage positions;
  - the death list, which moves here from the hall;
  - the alarm state.

### Boundary markers
- **The block.** A new `settlement_marker` block is a post with a banner look, tinted with the faction colour where it can be. Placing one within `markerMaxDistance` (128) of a hall of the same owner, in the same dimension, links it to the nearest such hall.
- **The area.** The land is the polygon of the markers, taken in angle order around the hall and tested in XZ only, over the full height. With fewer than 3 markers the area is a radius of `defaultRadius` (24) around the hall. The hall must be inside the polygon; a marker that would leave it outside is refused, with a message.
- **Showing it.** Holding a marker or the ledger draws the border with particles on the client.
- **Removal.** Breaking a marker removes it from the settlement. Breaking the hall disbands the settlement, and its markers drop as items.

### Members
- **Joining.** An owned NPC joins a settlement in any of these ways:
  - it is spawned inside the area;
  - it is ordered there with the command baton;
  - it finds itself inside the area of a settlement with the same owner actor and has none yet.
- **Leaving.** It leaves when it is dismissed in the hall screen, or when it dies (it goes onto the death list).
- **No range limit.** Membership is stored on the NPC as the settlement UUID and in the settlement's member set. Distance and unloaded chunks no longer matter.
- **What replaces the broadcast.** The old range broadcast is removed. The alarm and death reports go through the settlement.

### Storage
- **The ledger.** A new `settlement_ledger` item does two things. Used on a container inside the area, it links or unlinks that container as settlement storage, with a chat message. Used on the hall, it opens the screen.
- **What counts as storage.** The hall's own inventory always counts. Upkeep orders still override everything, for a single NPC.

### Hunger
- **The meter.** Food is a meter from 0 to `maxFood`, where `maxFood` is today's full value.
- **Eating.**
  - An NPC under 25% goes to eat at the nearest linked storage that has food, measured by path distance or else straight-line distance. It eats there until it is above 90%.
  - If none of the storages has food, the settlement raises a "no food" alert.
- **Below 25%.** Work speed is multiplied by `hungryWorkFactor` (0.6). Movement speed drops by 15%. Natural healing stops.
- **At 0.** The NPC moves slower again (`starvingWorkFactor` 0.3). After `starvationGraceTicks` (3 in-game days) it takes 1 damage every 30 seconds, but only if `starvationEnabled` is on (the default).
- **Remove the cliff.** Workers no longer stop at 0. The "food == 0" checks in `NpcAIPlayerOwnedWork`, `IdleWhenHungry`, healing and the trader are replaced by the hunger multipliers.

### Hall screen
The hall screen gets these tabs:
- **Overview:** name and rename, owner, member count, food in storage (counted in NPC-days), alerts, and the alarm toggle.
- **Members:** each member's name, type, status (working, hungry, starving, sleeping, idle) and distance, with a Dismiss button.
- **Storage:** the linked containers, with an Unlink button.
- **Deaths:** the existing resurrection list.

### Factions
- **Owner.** The settlement's owner actor follows its founder: if the founder joins or leaves a faction, the settlement follows, unless the faction disbands, in which case it reverts to the founder.
- **Who can use it.** Members of the owning faction count as owners for the hall, ledger and markers.
- **Guard modes.** Guard modes already apply through the NPC actors.
- **API.** `FactionsApi` gets nothing new. The settlement only stores `ActorId`.

### Config
All the numbers above go in the NPC server config.

## Phase 2 spec: autonomous NPC life

Phase 2 lives in `ancientwarfarenpc`. The numbers go in `ancientwarfarenpc-settlements.toml`.

### Roles and the job board
- **Role.** Each worker has a stored `role`: farmer, lumberjack, miner, researcher, craftsman, and so on. It no longer comes from the tool the worker holds.
  - It starts from the spawner subtype, or from the tool at the time the role is first set.
  - The Members tab gets a Role cycle button.
- **Tools.**
  - A matching tool gives full speed.
  - With no matching tool the worker works at `noToolWorkFactor` (0.5) speed.
  - A worker with no matching tool fetches one from linked storage on its own.
- **Job board.** The settlement keeps a job board. Each worksite inside the settlement's land posts a job with:
  - the role it needs;
  - its worker slots (`maxWorkersPerSite`, default 2);
  - whether it currently has work.
- **Claiming jobs.**
  - A worker with no work order claims a job that matches its role, has work and has a free slot. It picks the job with the fewest workers, nearest first.
  - It keeps the claim while it eats or sleeps.
  - It gives the claim up when the site has had no work for 60 seconds, or when the site is removed.
  - This replaces the 80×40×80 scan in `NpcAIPlayerOwnedFindWorksite`.
- **Overrides.** Work orders still override all of this.
- **Hall screen.** The Overview tab shows open and filled jobs, and the number of idle workers in each role.

### Daily schedule
`NpcAIScheduleGoal` drives the NPC's activity from the time of day:

| Activity | Ticks |
|---|---|
| wake, eat | 0 to 1000 |
| work | 1000 to 11000 |
| socialise | 11000 to 12500 |
| sleep | 12500 to 23500 |

Hunger, alarms and orders still take priority over the schedule.

- **Beds.** The settlement assigns beds inside its land to members.
  - An NPC without a bed sleeps by the hall and shows as homeless in the Members tab.
  - Breaking a bed frees it.
- **Socialising.** NPCs gather at a gathering point: the hall, any campfire or bell inside the land, or a bard. They idle around it and turn to face each other. A small ambient chatter sound is optional.
- **Rain.** In rain, NPCs that aren't working go and stand under a roof inside the land.
- **Night watch.** Combat NPCs work in shifts: `nightWatchFraction` (0.5) of them stay awake at night and patrol.

### Threat response
- **Raising the alarm.** The settlement scans its land every second, in loaded chunks only, for HOSTILE actors and hostile mobs.
  - The alarm starts automatically when the threat passes `autoAlarmThreshold` (1 entity), if `autoAlarm` is on.
  - It ends 30 seconds after the threat is clear.
  - The manual alarm toggle still works.
- **During an alarm:**
  - **Civilians** (workers, couriers, traders, bards, priests) flee to shelter: the hall, or the nearest roofed spot inside the land. Medics heal the wounded.
  - **Combat NPCs with no orders** rally to the threat and fight it. They respect guard mode: PASSIVE NPCs only defend themselves.
- **Patrols.** Outside alarms, idle combat NPCs patrol the settlement border (marker to marker) and visit the hall, when the owner's guard mode is GUARD or AGGRESSIVE.
- **Faction NPCs.** Faction NPCs use their NPC faction's guard mode from `FactionsApi.getGuardMode`, instead of always being AGGRESSIVE.

### Auto-hauling couriers
A courier with no routing order runs logistics for its settlement:
- It moves the output inventories of worksites inside the land into linked storage.
- It brings worksites the inputs they are short of from linked storage: seeds, saplings, bone meal, fuel and tools.
- It carries food to storages that are low, if any storage has spare.

Worksites say which slots are input and which are output through a small `IWorksiteLogistics` view, implemented by the existing worksite block entities. Routing orders still override.

### Pathing
- **Path range.** The navigator's path range goes up (`maxPathRange` 64) for long trips inside a settlement.
- **Long trips.** Trips further than that go in hops, through intermediate waypoints.

## Phase 3 spec: living worksites

Phase 3 lives in `ancientwarfareautomation`, with the NPC side in `ancientwarfarenpc` and small interfaces in Core. Config numbers go in the Automation server config.

### Staffed work
Worksites no longer edit blocks by themselves. A worksite becomes a job post plus storage that hands out **work tasks**. Each task is a target position, an action (`TILL`, `PLANT`, `HARVEST`, `CHOP`, `MINE`, `PLACE`, `SHEAR`, `MILK`, `BREED`, `CULL`, `FISH`, `COLLECT`, `SMELT`) and a duration.

**What the worker does:**
1. It claims a task through the phase 2 job board.
2. It walks within reach of the target.
3. It faces the target and swings its tool. For breaking actions the block's crack overlay advances, using `Level.destroyBlockProgress`. The duration comes from the block's hardness, the tool, the worker's skill and its hunger and tool factors.
4. It completes the task. The worksite then does what the old `processAction` did, for that one target.
5. Particles and the block sounds play.

**Drops and torque:**
- The worker keeps drops in a carry pouch of 9 slots. It takes them to the worksite inventory when the pouch is full or at the end of its shift.
- Torque is optional. A powered site speeds its workers up to ×`torqueMaxBoost` (2.0).

**Fallback:** config `machinesWorkAlone` (default false) restores the old unstaffed behaviour for automation-only players.

**Expected count:** the Core `IWorkSite`/`IWorksiteTasks` view lists the open tasks, so the job board shows how many workers a site can usefully take.

### Farms on natural terrain
- **Height range.** Farm bounds get a vertical range: 4 above and 4 below the site, which can be configured in the site screen.
- **Surface scan.** For each column the farm finds the surface: the topmost solid block that can be farmed. Slopes, steps and natural ground all work.
- **Crop farm.**
  - It tills grass and dirt on the surface, plants on farmland, and harvests ripe crops.
  - Unwatered farmland is allowed and behaves as vanilla does.
  - It never tills under a block that isn't air.
- **Tree farm.**
  - It finds natural trees inside the area, including big oak and 2×2 trees, through the existing scanners.
  - Workers chop from the bottom up, climbing where needed or reaching up to 4 blocks.
  - Saplings are replanted where the trunk stood, and leaves decay naturally.
- **Fruit farm.** It works on natural terrain the same way.
- **Fish farm becomes a shore fisher.**
  - The worker stands at the water's edge, casts a bobber (a cosmetic entity) and waits a vanilla-like bite time, then reels in.
  - Loot comes from the vanilla fishing loot table. Treasure is allowed when the water is open water (the vanilla 5×4×5 check), and the luck of the worker's rod counts.
  - Boat fishing comes in phase 4.

### Mine styles
The quarry gets a **Mode** setting in its screen. Every mode leaves its tunnels behind.

| Mode | What it digs |
|---|---|
| Open quarry | Today's pattern, with workers digging from the edge. |
| Shaft | A 3×3 shaft straight down, with ladders on one wall and a torch every 8 blocks, until bedrock or the Y level set. |
| Branch mine | A 3-wide by 2-high main tunnel at the chosen Y, with 1×2 side branches every 3 blocks, each `branchLength` (16) long, and torches every 8 blocks. |
| Strip mine | A 1×2 tunnel at the chosen Y. Any ore exposed in the walls is followed and mined out as a vein. |

**For every mode:**
- Ores and stone go into the site inventory. A filter in the screen can void cobblestone, dirt and gravel.
- Workers get lit tunnels and floor access.
- Lava and water exposed in a tunnel are blocked off with cobblestone.
- Each mode keeps its own saved cursor.

### New and improved worksites
- **Apiary.**
  - It manages beehives and bee nests inside its area.
  - Workers harvest honey with bottles and honeycomb with shears, with a campfire under the hive or a smoker item so the bees stay calm.
  - They plant flowers if fewer than 4 are nearby.
- **Smelter or kiln.**
  - It works the furnaces, smokers and blast furnaces linked inside its area, or its own internal fire.
  - Workers load ore and fuel from the site inventory and collect the output.
  - It is linked into courier logistics as an `IWorksiteLogistics`.
- **Animal farm (herding).**
  - Workers also milk cows, shear sheep, collect eggs and feed animals visibly.
  - The species limits become configurable for each site.

## Phase 4 spec: vehicles and controls

Phase 4 lives in `ancientwarfarevehicle`. The fishing dock is in `ancientwarfareautomation` and the crew AI in `ancientwarfarenpc`.

### First: worksite task-list cost (left over from phase 3)
Sites rebuild `listTasks()` far too often. They should cache the task list and rebuild it only on a block change inside the area, or every `taskRescanTicks` (40). Workers should read from the cache.

### Controls
- **Analog input.** `VehicleInputData` carries analog throttle and steer floats, plus the aim, and is sent every tick while it changes.
- **Steering modes.** A new client option `steerMode`, toggled with the C key, which already toggles mouse aim:
  - **Mouse steer** (the default): the vehicle turns towards where the camera points, and W/S is throttle. On turreted vehicles the mouse aims and A/D turns the hull.
  - **Classic:** keys only, as today.
- **Camera.** The rider gets a chase camera behind and above the vehicle, at a distance set for the type. The camera turns smoothly, the mouse wheel zooms, and F5 still cycles views.
- **Client prediction.** The rider's own vehicle is moved on the client too, and the server corrects it if it drifts more than 0.5 blocks.

### Seats
- **Seat list.** `VehicleType` gets a list of seats, each with an offset and a role:
  - `DRIVER`: steers;
  - `GUNNER`: aims and fires turret N;
  - `PASSENGER`;
  - `ROWER`: adds speed on galleys.
- **Using seats.** `canAddPassenger` and `positionRider` use the list. Riders can switch seats with the number keys. NPC crew (`IVehicleCrew`) take seats by role.

### Horse-drawn carts
- **New types:**
  - `cart`: 2 seats and 27 slots, pulled by one horse.
  - `wagon`: 4 seats and 54 slots, pulled by one or two horses. It has a covered model.
- **Hitching.**
  - Use a lead on a cart while you are leading a horse, donkey or mule to hitch it.
  - Sneak-use to unhitch.
- **Driving.**
  - The cart follows the horse's hitch point, with a spring and a turning limit.
  - When a player rides the horse, the player drives.
  - When a player sits in the cart's driver seat, the cart steers the horse through its movement.
- **NPCs.** NPCs can lead hitched horses, so trader caravans can use carts in phase 5.
- **Rules:** a cart with no horse can't move except by a slow push, and it breaks with a player attack the way a boat does.

### Ships
**The framework.** A large ship is one `VehicleBase` entity with a hull made of several collision parts (NeoForge `PartEntity`, laid out along the hull and turned with it), so players can stand and walk on the deck.
- **Movement:**
  - The ship moves anything standing on its deck by its own movement and rotation each tick.
  - It floats in water depth 2 or more.
  - It runs aground in shallow water.
  - Big ships turn slowly and build speed slowly.
- **Wind and rowing.** Sails give speed that depends on wind. Wind is one direction per world that drifts slowly; the HUD shows it. Rowers add speed with no wind.

**The ships:**

| Ship | Size (W × L blocks) | Seats | Storage | Turrets | Speed |
|---|---|---|---|---|---|
| Fishing boat | 2 × 4 | driver, 1 passenger | 9 slots | none | small, quick |
| Cog (cargo) | 5 × 10 | driver, 4 passengers | 54 slots | 1 ballista hardpoint | sails only |
| War galley | 5 × 14 | driver, 8 rowers, 4 passengers | none | 2 cannon or ballista hardpoints, 1 bow ram | oars and sails |

**Turrets and damage:**
- Turrets are mounted on hardpoints, and a GUNNER seat controls each one.
- Ships have health. When it runs out they sink: they slowly go down and drop their cargo.

### Fishing dock (Automation)
A **dock** worksite placed at the water's edge owns one fishing boat.
- **The trip.** A fisher worker boards the boat, sails out up to `dockRange` (48) to open water, fishes from the boat with the phase 3 bobber logic and treasure rules, and returns.
- **Unloading.** The catch is unloaded into the dock's inventory.
- **No boat.** If the boat is lost, the dock asks the courier for a new one, or the player places one.
- **How it moves.** The boat is steered by waypoints over water. The boat entity is driven directly; it does not use vanilla boat input.

### Models
New vehicles use Java `LayerDefinition` and `ModelPart` models in the vanilla style, with textures made from existing AW and vanilla planks, wool and iron. The old 19 Techne models stay as they are.

## Phase 5 spec: traders, caravans and new NPC types

Phase 5 is mostly in `ancientwarfarenpc`. The market stall and stable worksites go in `ancientwarfareautomation`. AW's coin items are the currency.

### Market stall and pricing
- **The stall.** A new **market stall** worksite is staffed by a trader NPC (trader role) during work hours.
  - The owner sets an offer list in the stall's screen: an item, an amount, and a price in coins.
  - Stock comes from linked settlement storage, so couriers keep it stocked.
  - Players right-click the trader at the stall to buy.
  - Coins go into the stall's inventory, and couriers haul them to storage.
- **Buy orders.** The stall can also post "buy" orders, which caravans fill.
- **Prices.** Prices scale with the buyer's relation to the seller's actor. The same scale also applies to faction traders (`NpcFactionTrader`).

  | Relation | Prices |
  |---|---|
  | HOSTILE | refuses to trade |
  | NEUTRAL | ×1.0 |
  | FRIENDLY | ×`friendlyDiscount` (0.9) |
  | ALLIED | ×`alliedDiscount` (0.8) |

- **Reputation.** Each trade adds `tradeReputation` (+2, at most 20 per in-game day per pair).

### Caravans
- **When they come.** NPC faction caravans spawn every `caravanIntervalDays` (2). Each one has a 50% chance of going to each player settlement whose owner is NEUTRAL or better with that faction.
- **Who comes.** A trader leading a horse and wagon, with 2 guards. They spawn 64 to 96 blocks from the hall, on loaded ground near a player.
- **The trip.** They travel to the hall and stay half a day.
  - They sell stock that depends on the faction and the biome, using the existing `FactionTradeList` data.
  - They buy from the settlement's stall buy orders, paying coins.
  - Then they leave, and despawn once they are out of range.
- **Danger.** Attacking a caravan costs reputation, as for any NPC. A killed caravan drops its wagon cargo.
- **Player trade routes.** An owned trader with a hitched wagon can follow a trade order route between settlements, loading and unloading at linked storages at each stop. It uses the phase 4 hitch API.

### Morale
- **The value.** Each settlement has a **morale** value from 0 to 100, recalculated every 30 seconds.
- **What raises it:**
  - food stocked, and the variety of food;
  - beds per member;
  - a gathering point;
  - an inn with an innkeeper;
  - a bard performing;
  - no recent deaths;
  - no active threat.
- **Work speed.** Morale multiplies work speed from ×0.8 (at 0) to ×1.2 (at 100).
- **Hall screen.** The Overview tab shows morale and lists what is raising or lowering it.

### New NPC types
Each is an owned NPC type, made with a spawner recipe and research in the existing pattern, with its own skin.

- **Innkeeper.**
  - Staffs an **inn**, a new worksite block that needs beds and a table (crafting table or barrel) in its area.
  - Serves food and drink from storage to NPCs at the gathering time, which raises morale.
  - Sells meals to players.
  - Rents inn beds to visiting caravan NPCs, for coins.
- **Stable hand.**
  - Staffs a **stable** worksite: feeds and breeds horses, donkeys and mules, and tames wild ones brought in.
  - Keeps horses ready and hitches them to carts and wagons for the settlement's trade routes.
- **Guard captain.**
  - At most one per settlement.
  - Soldiers within 24 blocks get Strength I and Resistance I while the captain is in combat.
  - The captain sets the patrol route (the border markers, or waypoints set with the command baton).
  - Rallies soldiers into a line facing the threat during an alarm.
  - The hall gets a **Guard** tab with patrol on or off, the night-watch fraction and the auto-alarm settings. These are stored per settlement and override the config defaults.
- **Builder.** Stays parked until structures come back.

## Open questions for the owner
- **Q1.** Should a settlement claim land automatically as a radius around the hall, or with marker or flag blocks the player places?
- **Q2.** Can NPCs starve to death, or does hunger only slow them down?
- **Q3.** Should worksites stay machines with an optional worker, or be staffed by NPCs who visibly do the work?
- **Q4.** Should big ships be large entities with a walkable deck and seats (feasible, L), or true block contraptions you build (Create-style, XL and fragile)?
- Later questions: should mines leave tunnels behind or tidy up; should torque survive the rework; should worksites keep chunk loading or run only near players.
