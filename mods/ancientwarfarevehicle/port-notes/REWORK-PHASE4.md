# Rework phase 4: vehicles and controls

## Stage: task-list cache, controls, seats

Code: Automation `WorksiteBlockEntity` / `WorksiteEvents` / `AWAutomationStatics` (task cache), Core `WorksiteRegistry.forEach`; Vehicles `input/VehicleInputData`, `helpers/VehicleMoveHelper`,
`entity/VehicleBase`, `entity/SeatRole`, `entity/VehicleSeat`, `entity/types/VehicleType`, `client/VehicleInputClient`, `client/VehicleCameraClient`, `network/*`; NPC `NpcAIFindVehicle`, `NpcAIMountVehicle`.

### Worksite task-list cost (Automation)
- `WorksiteBlockEntity.visibleTasks()` keeps the (cooldown filtered) list of `listTasks()` in a cache. It is rebuilt when marked dirty or after `task_rescan_ticks` (server config, default 40, 5..1200).
- Dirty on: a `BlockEvent.NeighborNotifyEvent` for a block inside the site's area (`taskAreaContains`: the bounds one block wider, the vertical range for farms, all heights for the others; 16 blocks round a site with no bounds;
  `WorksiteEvents.onNeighborNotify` walks `WorksiteRegistry.forEach`, which makes no snapshot), a task completed or given back, a target's cooldown ending or starting, new bounds or vertical range, and the quarry's `invalidatePlan`.
  `tasksDirty` is cleared after `listTasks()` returns, so a site that calls `setChanged` while listing does not keep itself dirty.
- Deviation: `NeighborNotifyEvent` only fires for changes that notify neighbours (flag 1, which players, workers and growth all use). Changes made with flags that skip it, and changes in the site's own inventory or scan sets,
  show up at the next `task_rescan_ticks` rebuild (up to 2 s). Entity-based sites (animal farm) rely on the timer alone.

### Controls
- `VehicleInputData` is now `(forward, turn, power, rotation, throttle, steer, aimYaw, aimPitch)`: the bytes stay, `throttle` and `steer` are floats in -1..1 (steer +1 is right), the aim is the rider's camera yaw and pitch.
  The old four-byte constructor still exists (analog = digital). Wire format changed, `VehicleNetwork.VERSION` is "2".
- Sending: every tick (client tick Pre, so the input is in place before the vehicle ticks) the client builds the input, applies it to its own vehicle and sends it when the movement part changed (`movementDiffers`), plus once a second.
  The camera angles alone do not trigger a send. This replaces the key-release tracking.
- Server and prediction: `VehicleMoveHelper.handleInputData(VehicleInputData)` turns the analog values into the old direction byte and a magnitude (`forwardMag`, `turnMag`); the magnitudes scale the target speed and the target turn rate in
  `applyForwardInput` / `applyTurnInput`. The AI's digital setters set the magnitudes to 1.
- Steering mode: client option `mouse_steer` (default true), toggled with the C key, which now also sets `enable_mouse_aim` to the same value (classic = keys only, as before). A chat-free action bar message names the mode.
  - Mouse steer: for a vehicle with no turret yaw (`!canAimRotate`) that is drivable, the steer value is the angle between the camera yaw and the vehicle heading (`180 - yaw`) over 35 degrees, with a 2 degree dead zone, while W/S is held or
    the vehicle still moves; A/D override it. The rider's view is not dragged along by the vehicle's turn (`riderViewFollows`). On turreted vehicles the mouse aims (existing mouse aim) and A/D turn the hull.
  - W/S and A/D ramp from 0 to 1 over 4 ticks, a released key drops to 0 at once.
- Camera (`VehicleCameraClient`): `CalculateDetachedCameraDistanceEvent` sets the distance to the type's `getCameraDistance()` (default `max(4, 2.5 + 1.5 * max(width, height))`, field `cameraDistance` to override) times a zoom, eased at 12% per frame;
  the mouse wheel zooms 0.5..3 in third person (cancelled so the hotbar does not scroll; first person still scrolls the hotbar); mounting in third person tilts the view down to 14 degrees. F5 is untouched.
  Deviation: no extra "follow" easing of the yaw beyond what exists (the vehicle's own yaw change is added to the view each tick in classic mode; the vehicle follows the view in mouse mode). A recenter-behind-the-vehicle easing would fight the turret aim and was left out.
- Prediction: `VehicleMoveHelper.isPredicted()` (client, ground and water vehicles, the local player is the driver). The predicted vehicle runs `stepKinematics()` (the same code as the server, with collisions: `noPhysics` is off for it) each tick and keeps its last 12 states.
  A `VehicleMove` from the server is compared with the nearest of those 12 states (the server's position is a few ticks in the past): more than 0.5 blocks (or 15 degrees of yaw) off snaps the client to the server, a smaller gap above 0.05 eases a fifth of it out.
  Deviation from "server corrects beyond 0.5": the compare is against the lagged history, not the current client position, otherwise a fast vehicle would be corrected at every latency spike. The server stays authoritative; it is not changed.

### Seats
- `IVehicleType.getSeats()`: `VehicleSeat(role, forward, horizontal, vertical, turret)`, roles `DRIVER`, `GUNNER`, `PASSENGER`, `ROWER`. `VehicleType` builds the list lazily: seat 0 is a DRIVER seat from the existing rider offsets, so all 21 types keep
  their old single rider with no change (the driver also aims and fires, as before). `addSeat(role, forward, horizontal, vertical)` adds more in a type's constructor. Added: chest cart 1 passenger, transport boat 2 passengers, ballista boat and catapult boat 1 passenger each.
  Their positions are estimates from the models' sizes and were not looked at in game.
- `VehicleBase`: the rider of each seat is in a synced string `seat_data` ("12,-1,45", entity ids). `canAddPassenger` needs a free seat the entity may take; `positionRider` uses the rider's seat; `getControllingPassenger()` is the occupant of seat 0 (the old "first passenger" rule only while a client has no seat data yet).
  Helpers: `occupantAt`, `seatOf`, `hasFreeSeat(roles...)`, `findSeatFor`, `boardAs(entity, role)`, `switchSeat`, `isWeaponCrew`, `getFiringCrew`, `getRowerCount`. Seats are looked up through the passenger list (not the level) so a loading vehicle with riders works.
  Seats are not saved: on a reload the riders get seats in their saved order (a rider that had switched seat may come back in another one).
- Using a vehicle: a player with a free seat boards (driver first, then gunner, passenger, rower); with none, using the vehicle makes an NPC driver get off, as before. Crew NPCs (`IVehicleCrew`) only take DRIVER or GUNNER seats; `getPreferredSeatRole()` (default DRIVER) is passed to `boardAs`.
- Authority: `VehicleInput` (movement) needs the DRIVER; aim, fire, ammo and the ammo screen need driver or gunner (`Authority.CREW`); `SeatSwitch` (new payload `seat_switch`, seat index) needs any rider and a free, existing seat. The fire/aim/ammo key callbacks only act for weapon crew.
  Ammo and missile shooter use `getFiringCrew()` (driver, else the first gunner).
- Number keys 1 to 9 switch seats on a vehicle with 2 or more seats (the clicks are eaten in the client tick so the hotbar slot does not change; with one seat nothing is eaten).
- Rowers: each occupied ROWER seat adds 15% of the top speed to a water vehicle (`rowerFactor`). No type has rower seats yet.
- NPC crew: `NpcAIFindVehicle` looks for a vehicle with a free DRIVER seat; `NpcAIMountVehicle` boards by role. No NPC AI for a gunner seat exists: the second crew seat is available to the framework but only the driver AI aims and fires.
- Not play-tested (no client run): prediction, the camera, the seat keys and seat positions were compiled only; `:ancientwarfarestructure:runServer` reaches `Done (`.

## Stage: horse-drawn carts

Code: `entity/hitch/CartHitch`, `entity/hitch/HitchGoal`, `entity/types/VehicleTypeHorseDrawn` / `VehicleTypeCart` / `VehicleTypeWagon`, `VehicleBase` (hitch field, synced `hitch_data`, lead use, attack breaking, save/unload),
`VehicleMoveHelper.stepHorseDrawn`, client `ModelCart` / `ModelWagon` (generated by `analysis/port/vehicle_cart_models.py`, which also writes the textures), `VehicleModels`.

- Types 21 `cart` (2 seats: driver + 1 passenger, 27 slots, 1 horse, 4/5 wood per level in the recipe) and 22 `wagon` (4 seats: driver + 3 passengers, 54 slots, 1 or 2 horses, covered cloth model). Spawner items `cart_spawner` / `wagon_spawner`, five material levels
  each like the chest cart, research recipes `recipe/research/vehicles/{cart,wagon}_level_N.json` (research `ancientwarfare:trade`, the cart costs wood of the level, wooden chests, a lead and an equipment bay; the wagon more). Tooltip line `horse`.
- Hitching: use a lead (held in hand, not used up) on the cart while leading a tamed horse, donkey or mule (the animal's own lead is taken). Sneak-use with a lead unhitches the horse hitched last and gives a lead back. The horse must be within 12 blocks and the rope must be
  less than 60% stretched. Sneak-use without a lead still opens the inventory, use without a lead boards.
- Hitch is saved as the horse UUIDs (`hitch` list) and restored when the horse is found in the level again; a horse not found for 10 s while its last place is loaded is dropped (lead falls at the cart). An unloaded cart takes the goal off its horses.
- Moving: the server moves a hitched cart (`stepHorseDrawn`): spring (50% of the gap to the drawbar target per tick, 0.9 blocks/tick cap), turns towards the horse's hitch point at a rate that grows with speed (2 + 45*speed degrees/tick), never more than `maxHitchAngle` (70, wagon 60)
  off the horse's heading (the horse is slowed to 60% each tick while it is over). The rope snaps at 6 blocks (cart stuck while the horse walks on): the horse is unhitched and the lead drops.
- Driving: a ridden horse is the lead horse and the rider drives (the hitch goal is idle). A player in the driver seat steers the lead horse with W/S/A/D (or mouse steer): throttle/steer become a `MoveControl` target 3 blocks ahead of the horse's yaw + 55*steer; S is a brake (a horse
  does not back a cart up). A leader (`setLeader`) or a destination (`leadTo`) is used when nobody drives. The second horse of the wagon trails its slot beside the lead horse.
- Client: not predicted (the cart follows the horse, which the client cannot know), the normal `VehicleMove` interpolation is used. Drive input uses the existing analog input and mouse steering; the camera is the type's `cameraDistance` (wagon 8).
- No horse: movement input pushes the cart at 20% of the top speed and 40% of the turn rate (`CartHitch.PUSH_FACTOR`).
- Breaking: a player's blow adds damage*10 to a boat-like counter (heals 1/tick); above 40 the cart is packed into its item (inventories drop). Creative breaks it at once and drops no item. Other damage works as before.
- NPC API (`CartHitch`, reached as `vehicle.hitch`): `isHitchable`, `hitch(horse[, requireTamed])`, `unhitch(horse, dropLead)`, `horses()`, `primary()`, `setLeader(entity, distance)`, `leadTo(pos, speed)`, `isLeadingToDestination()`, `cartOf(horse)`, `snap`. For phase 5 caravans: spawn
  the cart, hitch the trader's pack horse with `requireTamed` false, `setLeader(trader, 3)`. `setLeader` and `leadTo` are not saved, whoever leads sets them again after a load. No NPC AI uses them yet.

Deviations / not done
- Carts are not drivable by NPC crew (`pilotableBySoldiers` false); NPCs lead the horse instead.
- Seat positions and shaft geometry of both models are estimates; the models, the hitch behaviour, the mouse steering of a cart and the spring were compiled and loaded by a dedicated server only, never seen in a client.
- The rope is not drawn; the shafts hang from a pivot that lowers them to the ground while no horse is hitched.
- Textures come from the plank area of the chest cart textures of each level (wood at level 1, plate at level 5), a generated woven cloth for the wagon cover; vanilla texture files were not available locally.
- One lead item in hand is needed to hitch (it is not used up), in addition to the lead on the animal.

## Stage: ships

Code: `entity/ship/{ShipSpec, ShipPart, ShipHelper, ShipHardpoint}`, `wind/{WindData, Wind, WindEvents}`, `entity/types/VehicleTypeShip` / `VehicleTypeFishingBoat` / `VehicleTypeCog` / `VehicleTypeWarGalley`, `VehicleBase` (ship hooks), `VehicleMoveHelper.stepShip`,
`VehicleFiringHelper.fireFromHardpoint`, payloads `WindSync`, `HardpointAim`, `HardpointFire` (`VehicleNetwork.VERSION` is "3"), client `ShipHud`, `ShipGunnerClient`, `ModelFishingBoat` / `ModelCog` / `ModelWarGalley`
(generated with the textures, item icons, item models and research recipes by `analysis/port/vehicle_ship_models.py`; edit the script, not the Java).

### Framework
- A ship is a `VehicleBase` of its own entity type `ship` (`AwVehicles.SHIP`, tracking range 10 chunks). The type matters: the vanilla client adds an entity to its level (and registers its `PartEntity` parts) before the spawn data that names the vehicle type arrives, so
  the parts must exist from the constructor. Every ship entity therefore carries `ShipSpec.MAX_PARTS` (64) `ShipPart`s; the cells of its design configure the first ones, the rest are inactive (no collision, not pickable, sitting in the ship's middle).
  `setId` gives part `i` the id `shipId + 1 + i` on both sides (the ender dragon's trick), so the interact packet for a clicked part finds it. Parts are never ticked, saved or tracked; use and hurt of a part go to the ship.
- Design (`ShipSpec`): a grid of 1 x 1 hull cells, a row per block of length, bow first (`rows` = width per row). Each cell is a part with an axis aligned box from the keel up: `DECK` (the top of the hull, walkable), `RAIL` (edge cells of rows 3+ wide that are not gangway rows, and the
  bow row: 0.9 higher) and `MAST` (the cell of a mast, 3 blocks over the deck). As the ship turns, `updateParts()` moves every cell round the middle, so the boxes follow the hull (a rotated cell is approximated by its box, up to 0.3 blocks of overhang at 45 degrees).
  Sizes: fishing boat 2 x 4 (rows 1,2,2,2), cog 5 x 10, war galley 5 x 14. The box of the entity itself is a 2.5 block cube in the middle and not solid (`canBeCollidedWith` is false for ships).
- Moving (`ShipHelper.step`, server): the ship's own movement, not `Entity.move`. It floats where every cell has at least `MIN_DEPTH` (2) blocks of water and no block is in the hull's body; a move or turn that would put more cells on shallow water or in blocks than now is refused (the move is tried on one axis
  alone first), so it runs aground in shallows (flag `aground`, "Aground!" in the HUD, speed 35% while it counts as aground) and can back out. Height: the average water surface under the middle, bow, stern and sides less the draft, plus a small swell, eased 20% a tick; over no water it falls to the ground.
  Speed and turning are slow: acceleration 0.0022 to 0.006 blocks/tick per tick, a turn rate that is reached at 5 to 15% of the top rate a tick, and a turn rate of 30% of the top at a standstill.
- Controls: W and S set the sail and oar setting `trim` (-0.5 to 1) by 2.5% a tick (S 1.6 times faster); a player helmsman's setting stays when he lets go of the key (the ship sails on), for anyone else it follows the key and falls to 0. The top speed at trim 1 is `baseForwardSpeed` times
  `drivePower + sailPower * windStrength * sailEfficiency + rowPower * rowers / rowerSeats`. Not predicted on the client (the normal `VehicleMove` interpolation). The rider's view is not turned with a ship (the camera is the rider's own, also in classic steering).
- Deck: the server and the client move what stands on the hull by the ship's movement and turn each tick, round the ship's middle (positions only, the view of a player is not turned): the server mobs and items, the client the local player (a player's position is his client's to decide, as in vanilla).
  A mob's facing is turned with the ship. For the server's floating check (a player over water on a deck 1.5 blocks up counts as flying and is kicked after 80 ticks on a server with allow-flight off) the counter `aboveGroundTickCount` of `ServerGamePacketListenerImpl` is reset by reflection for a player on a deck; if the field cannot be reached a warning is logged once.
- Seats: the helm is seat 0; gunner seats come next (turret = hardpoint index), then passengers, then rowers. Players board in the usual role order (driver, gunner, passenger, rower) and dismount onto the deck beside the seat. The number keys reach seats 1 to 9 only (the galley has 15), the other rowers' benches are filled by boarding.
- Sinking: health at or below 0 does not kill: `startSinking()` throws off the riders, the ship goes down 0.012 to 0.05 blocks a tick for up to 200 ticks (or until it is on the bottom), then it breaks up (`kill()`), which drops every inventory (cargo, ammo, upgrades, armor) at that place. A sinking ship takes no damage.
- Ram: the war galley hurts the first living thing, vehicle or ship part in a 1.8 block box at its bow while it moves faster than 0.05 blocks/tick: `ramDamage * speed / topSpeed` (35 at top speed, at most 1.5 times that) with the battering damage type, a knock-back, 15 ticks of cooldown and 40% of the speed lost.

### Wind
- `WindData` (saved with the overworld, `ancientwarfarevehicle_wind`): one direction (the way it blows to, as a vehicle yaw) and a strength of 0.25 to 1, both drifting by a random walk of their rate: the direction turns by at most 0.0075 degrees a tick (a full turn takes 40 minutes at the most), the strength wanders round 0.6.
  `WindEvents` ticks it, saves it every minute and sends `WindSync` to everyone every 5 seconds and to a player who joins, respawns or changes dimension (the overworld's wind is used everywhere). The client extrapolates the drift between packets (`Wind`).
- Sail efficiency: `0.15 + 0.85 * (1 + cos(angle between the ship's heading and the wind direction)) / 2`: all of the wind from astern, 57% on a beam reach, 15% into the wind. The sails of the models are braced towards the wind (0.45 of the relative angle, up to 50 degrees).
- HUD (`ShipHud`, shown while riding a ship or standing on its deck, with the vehicle overlay option): a compass whose arrow is the wind relative to the ship (up is the bow), the strength in knots (strength x 20), the heading, sail setting, speed, hull, "Aground!" or "Sinking!", and the state of the gunner's turret mount.

### Turret mounts
- A hardpoint (`ShipSpec.Hardpoint`: weapon, place, arc, elevation limits) has its own state (`ShipHardpoint`: yaw from the bow, pitch, destination, reload, wind-up, recoil). The weapon's ammo, ammo count, launch speed, scatter and missile are those of the existing helpers (`VehicleAmmoHelper`, `VehicleFiringHelper.fireFromHardpoint`
  uses `getMissile2` and `decreaseCurrentAmmo` like the single turret of the other types); the ammo is selected with the usual keys by the driver or a gunner. Reload is `currentReloadTicks`, the wind-up 3 ticks for a ballista and 10 for a cannon (with the fuse sound).
- A gunner's client (`ShipGunnerClient`) aims the mount of his seat with the camera whatever the mouse aim setting is: yaw towards the point he looks at, the elevation that lands the shot there, within the arc; sent as `HardpointAim` when it changed by 0.4 degrees; the server sends the destination on to the tracking clients, and every side turns the mount at 4 (ballista) or 2.5 (cannon)
  degrees a tick. The fire key sends `HardpointFire` (the server checks that the sender sits in a gunner seat of that mount). The driver cannot fire; with no gunner a mount stays still.
- Missiles do not hit the launching ship's own parts (`MissileBase.canHitEntity`, `checkProximity`).
- Cog: 1 ballista on a swivel (360 degrees) at the bow, gunner seat behind it. War galley: 2 cannons at the bow on the sides (arc 70 degrees either side of the beam), a gunner seat for each.

### Ships
| Ship | Size | Seats | Storage | Turrets | Top speed | Power |
|---|---|---|---|---|---|---|
| fishing boat (23) | 2 x 4 | driver, 1 passenger | 9 | none | 0.30 b/tick (6 m/s) | helmsman 0.5, lug sail 0.55 |
| cog (24) | 5 x 10 | helm, 1 gunner, 4 passengers | 54 | 1 ballista | 0.24 | sail 1.8 (none without wind) |
| war galley (25) | 5 x 14 | helm, 2 gunners, 4 passengers, 8 rowers | none | 2 cannons, bow ram | 0.26 | sail 0.9, rowers 0.9 |
- Health 60 / 300 / 400 (times the material factor). Items `fishing_boat_spawner` / `cog_spawner` / `war_galley_spawner`, five material levels each, research recipes `recipe/research/vehicles/<name>_level_N.json` (fishing boat and cog: research `seafaring`, wood of the level, string, wool, equipment bay (the cog also chests,
  a turret unit); galley: research `naval_warfare`, wood, string, wool, iron ingots, 2 turret units, 2 powder cases, 2 equipment bays). Tooltips `ship`, `sails`, `rowers`, `hardpoints`. Models are `LayerDefinition` box models with texture atlases made from the plank area of the chest cart textures (plain wood at level 1, plate at level 5), wool and iron grey; the model parts
  are the sails (braced to the wind), 8 oars that swing with the speed (galley) and the turret mounts (yaw, pitch, recoil). Soldiers do not pilot ships (`pilotableBySoldiers` false).

### Tested
- Built; a dedicated server loads the types, recipes, lang and models and reached `Done (`. A temporary server-side self test (removed again) put ships in a pool: the parts register (`getPartEntities`), the ship floats at the surface, a mob on the deck keeps its place in the ship's frame through a long turn,
  speed and turning build up slowly, a sandbar stops the ship, and a lethal hit makes the ship sink for about 200 ticks and drop its cargo (5 diamonds) when it breaks up.
- Not run in a client: the walk on the deck (collision with the parts and the carry on the client), the models, the HUD, the seat positions, the gunner aim, the ram and the wind in play. The fly-check reset was not tested on a dedicated server with a player.

### Deviations / not done
- Hull cells are 1 x 1 boxes: a turned ship's walkable area is an approximation of its outline, the rails are the edge cells and cannot be walked on, and a player climbs from the water only at the gangway rows and the open bow/stern rows (the deck is 0.45 to 0.55 blocks over the water, so a jump is needed).
- Shallow water only stops a ship (and makes it slower once aground); it does not damage or slow it by depth, and the ship does not sit on the bottom. No waves, roll or pitch; the swell is a 3 cm bob.
- No NPC rowers, helms or gunners (`roleOrder` still gives NPC crew only the driver and gunner seats, and ships are not soldier-piloted); the fishing dock and its waypoint sailing are the next stage.
- No config option for the wind (the numbers above are constants in `WindData`); the wind of the overworld is used in every dimension.
- Seats are not saved across a reload (as before); `trim` and the turret states are.

## Stage: fishing dock

Code: Core `api/IBoatWorksite`; Vehicle `ShipHelper.setPilot/pilotInput` (autopilot) and `VehicleMoveHelper.stepShip`; Automation `DockBlockEntity`, `DockRoute`, `FishingSupport` (bobber and loot code of the shore fisher, moved out of `FishFarmBlockEntity`), `DockMenu`, `GuiWorksiteDock`,
block/item `dock` (research `fishing`, recipe `recipe/research/worksite/dock.json`), config `dock_range` (48) and `dock_trip_catches` (5); NPC `ai/owned/BoatTrip` and the dock branch of `StaffedWork`. Textures are made from the fish farm's by `analysis/port/automation_dock_assets.py`.
Automation now depends on the Vehicle module (build.gradle and mods.toml).

- The dock is a staffed `FARMING` worksite (the shore fisher's role) with a 27 slot inventory. It keeps the id of one fishing boat. With no boat it takes a fishing boat spawner out of its inventory and spawns it at the berth (the deep water block nearest to it, 6 blocks at most);
  with no boat and none stocked, `getLogisticsNeeds()` asks the couriers for one. A sneaking player with an empty hand who uses the dock links the nearest unlinked fishing boat within 24 blocks. A boat missing for 5 s while its last place is loaded is lost (broken, sunk).
- It lists one FISH task at a time (a spot of open water, by the bobber's 5x4x5 test, 8 to 48 blocks away, rescanned every 10 s), so one worker uses the boat.
- The worker (`StaffedWork` + `BoatTrip`) walks to the boat, boards as driver, sails to the spot along `planRoute` (breadth first search over 1x1 cells of water 2+ deep with a clear 3x3, then corner cutting), fishes from the boat with the shore fisher's bobber, and after
  `dock_trip_catches` fish (or a nearly full pouch, or no more tasks) sails to the berth, steps onto the dock and empties the pouch into the dock inventory. The boat is driven by the ship autopilot (`ShipHelper.setPilot`, refreshed each tick, expires after 2 s), not by vanilla boat input.
- An empty boat away from its berth sails home by itself (the dock's autopilot). A route that cannot be found or a boat stuck for 5 s gives the spot a cooldown.

Deviations / not verified:
- Never run in a client or with a real worker: boarding, the autopilot's braking, route following, bobber from the boat and the seat position were only compiled; the server loads (`Done (`).
- Ending the goal while aboard (hunger, night) teleports the worker to the landing on top of the dock instead of sailing home; the dock then recalls the boat.
- Corners of the route use a 3x3 clearance, a 4 block hull turning near shore can still run aground (the spot then goes on cooldown).
- Only one worker per dock; the passenger seat is unused. No fishing rod durability or xp (as the shore fisher).
- The dock has no bounds screen, only the side screen.
