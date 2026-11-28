# Known-issue fixes

Developer notes for the fixes made after the 1.1.0 release, one section per area. Existing worlds keep loading: no ids, NBT keys or config keys were renamed; the new NBT key (`handBuilt` on a household) reads as `false` when missing. Not play-tested: `./gradlew build` passes, nothing was run in a world.

## Settlements and families (ancientwarfarenpc)

### 1. Soldiers equip gear themselves
- New goal `ai/owned/NpcAIPlayerOwnedEquipGear`, added to `NpcCombat` at priority 7 (above the schedule and patrol goals, below food, commands, alarm and combat).
- A combat NPC of a settlement checks the linked storages (hall inventory and containers) every 5 seconds. If a storage holds a better weapon or better armour piece than it carries, it walks to the nearest such storage, takes the best item for each slot, puts it on and puts the old item back into the storage (the swap is undone for an item when the storage cannot take the old one).
- Weapon kind is kept: the kind comes from the main hand. Sword (`#minecraft:swords`) stays a sword, an axe (the medic, whose subtype comes from an axe in the hand) stays an axe, a bow stays a bow. An empty hand takes the best sword, the same as the weapon need counts it. A baton, hammer or other item is left alone. Swords and axes are compared by the attack damage of the main-hand modifiers, bows by remaining durability and enchantments; armour by armour value plus half the toughness in that slot. Worn items count up to half less.
- Does not start while the NPC is alarmed, fighting, riding, hungry, following a command or holding orders, so existing orders are never overridden. After a visit the settlement recounts its needs (`SettlementNeeds.recount`), so the weapon and armour needs clear as soon as the gear is worn (the needs flag of a soldier without a weapon clears by itself).
- Config (`ancientwarfarenpc-settlements.toml`, section `demand`): `soldiers_equip_from_storage` (true). The comment of `demand_armour` was updated.
- Lang: `npc.thought.event.got_weapon.1..3`, `got_armour.1..3`.
- Deviation: archers do not take arrows. The arrows an NPC archer shoots are not taken from an inventory (`RangeAttackHelper`), so there is nothing to stock.

### 2. Families without the daily schedule
- `FamilyLife.tick` already ran without the schedule, but the bed scan and the claiming of homes lived in `SettlementLife.tick`, which returned at once when `schedule_enabled` was off. `SettlementLife.tick` now runs when the schedule or the families are on: it finds and checks beds and lets the households claim homes; handing out free beds and the gathering points still need the schedule.
- Couples need shared social time, which came only from the schedule goal (idling at a gathering point). With the schedule off, two free members count as meeting while both are loaded, awake and calm (not alarmed, fighting, riding, no orders) and within `couple_social_radius` of each other (`FamilyLife.isFreeToMeet`).
- No new config keys.

### 3. Hand-built family houses
- New `settlement/building/RoomScanner`: flood fill (6 neighbours) over open cells, where open means no collision shape and no fluid. Walls, doors, panes, stairs, slabs and furniture all block. The fill must end within `hand_built_room_max_cells` cells (so an open roof or a gap in a wall fails: the room is always roofed), may not reach an unloaded chunk, and at least one door block must touch the room.
- `FamilyHomes.best` now takes the level. Beds that are not in a finished build of the planner are grouped by room. A room with two or more beds that no household claimed and no outside member sleeps in is offered as a house (kind 0, same rank as a planner house), not as loose beds. A pair of loose beds in such a room is no longer offered twice.
- `Household.homeHandBuilt` (saved as `handBuilt`, only written when true). The daily `reconsider` keeps a household in a hand-built house, moves tent and loose-bed households into one, and gives the home back if the room is no longer enclosed (wall or door taken down) or holds fewer than two of the home beds.
- Config (section `demand`, next to the other new keys): `hand_built_family_houses` (true), `hand_built_room_max_cells` (600, shared with the chapel check).

### 4. Farmhouse with a working farm (and two small shells)
- Design format: the optional `worksite` object takes an `area`: `{"min": [x, y, z], "max": [x, y, z]}`, inclusive corners in the authored frame relative to the design origin, inside the design box. `BuildingDesign.Area`, `BuildingDesign.workArea(origin, facing)` (turned with the design, sorted). Written by `toJson`. The rest of the format is described in `mods/ancientwarfarenpc/port-notes/REWORK-PHASE9.md`.
- `BuildJobs.finish` calls `applyWorkArea`: when a build finishes, the area is set on the worksite block entity through `IBoundedSite.setBounds` plus the two bounds hooks the bounds screen calls. A worksite block that is gone or not loaded is left alone (logged).
- `farmhouse.json` now has the crop farm block (`ancientwarfareautomation:crop_farm`) at the end of the garden path and the garden (7 x 4 columns of farmland) as its area. Minimum lowered to level 0, population 3 so a young settlement can get one. Because the design now has a farming worksite, `BuildingPlanner.tryBusiness` plans it for the reason `role.farmer` when a farmer role exists and the job board has no farming site.
- New shells with the same mechanism: `lumber_camp.json` (tree farm block in a small hut, fenced plot as the area; the plot is left as it is, the farm plants the saplings) and `mine_entrance.json` (walled yard, quarry block, whole yard as the area). They plan for `role.lumberjack` and `role.miner` the same way.
- Generator: `analysis/port/gen_settlement_designs.py` (outside the repo) got an `area` argument for `emit`, the farmhouse change and the two new functions; `GEN_OUT` can point the output elsewhere. Only the three JSON files were copied into resources (the other designs regenerate byte for byte).
- Deviations and open points: the three designs need the worksite block item in the storage like the sawmill and the smithy do (they cannot be crafted by NPCs), so a planned build can wait for it ("Missing" on the Building tab). The farmland of the garden has no water source (the crops stand on it already). The quarry has the usual rules of the quarry (needs ladders, digs down to the bottom of the world unless limited); the mine entrance was not run, only checked by reading the quarry code.

### 5. Player-built chapels
- A hand-built chapel is an enclosed room (as in item 3) of the settlement that touches both a bell and a lectern. The lectern is the altar, the bell is the call; both are vanilla blocks, so any building becomes a chapel by putting them inside.
- `ChapelSites.refresh` (called with the planner maintenance, every 5 seconds) scans the loaded chunks of the land for bells and lecterns (at most 8 bell rooms per run), caches the room boxes per settlement (not saved; the first refresh after a restart finds them). `isNearChapel` (the devout trait) also accepts a place near such a room (within the trait radius of its box). `ChapelSites.hasHandBuilt` stops `BuildingPlanner.tryChapel` from planning a chapel. The cache is dropped when a settlement is disbanded.
- Config: `hand_built_chapels` (true).

### 6. Blueprints: delete and rename
- Building tab, New build form: when the selected design is one of the player's own blueprints (marked `*`), a row shows a name box with Rename, and Delete (asks "Delete for good?" on the first click). The typed name survives a rebuild of the tab.
- Server (`BuildingMenuSupport`, requests `renameBlueprint` and `deleteBlueprint`): the blueprint must exist and be owned by the player; names lose control characters, are cut at 40 characters and may not be empty. Rename (`BlueprintStore.rename`) keeps the id and file name, so plans that use the blueprint keep working, and rewrites the file. Delete is refused while any build that is not finished, in any settlement, uses the blueprint (the planner would cancel it when its design vanished). Finished builds of a deleted blueprint stay in the record; they just have no design any more (the Building tab shows the id path as name). The design list is sent to the player again after each change.
- Lang keys under `guistrings.settlement.build.` (`rename`, `delete`, `delete_confirm`, `renamed`, `deleted`, `error.not_your_blueprint`, `error.bad_name`, `error.blueprint_in_use`).

### 7. Builder notice
- `BuildingMenuSupport.noBuilder(settlement)`: some build or take-down is queued or under construction and no member has the role `worker.builder`. The Building tab then shows "No builder: assign a worker the Builder role" (orange, under the settings row) and the hall Overview puts the same line first in its Needs (a need line with status `TownHallMenu.NOTICE_STATUS`; it takes one of the 8 lines). The planner and the posted material needs are unchanged.
- Lang: `guistrings.settlement.build.no_builder`.

## Files
- New: `ai/owned/NpcAIPlayerOwnedEquipGear.java`, `settlement/building/RoomScanner.java`, resources `settlement_designs/lumber_camp.json`, `settlement_designs/mine_entrance.json`.
- Edited (ancientwarfarenpc): `entity/NpcCombat`, `settlement/SettlementLife`, `settlement/SettlementConfig`, `settlement/SettlementNeeds`, `settlement/SettlementData`, `settlement/family/FamilyLife`, `settlement/family/FamilyHomes`, `settlement/family/Household`, `settlement/building/BuildingDesign`, `settlement/building/BuildJobs`, `settlement/building/BuildingPlanner`, `settlement/building/ChapelSites`, `settlement/building/BlueprintStore`, `settlement/building/BuildingMenuSupport`, `menu/TownHallMenu`, `client/screen/BuildingTabView`, `client/screen/GuiTownHallInventory`, `lang/en_us.json`, resource `settlement_designs/farmhouse.json`.
- Outside the repo: `analysis/port/gen_settlement_designs.py`.

## Ship helmsman routing and grappling lines (Vehicles, NPCs)

### Changed
- Helmsman routing: a baton Move order at water now plans a water route to the clicked spot before sailing (`ShipRoutePlanner.planBetween`, clearance from the hull width through `SeaDocks.clearanceOf`, the same planner the sea trade uses). The waypoints are stored as the sail route and followed by `ShipSteering`, which still looks ahead and steers round what the plan did not know about. The plan is made once for each destination (`NpcAIShipHelm.planRoute`). When there is no route (blocked, too far for the planner, over unloaded chunks) the helmsman falls back to the old look-ahead and the owner gets a chat message (`message.ancientwarfarenpc.ship.no_route`).
- The 2-block depth rule is unchanged. A Move order on water that is shallower than that now tells the owner why (`message.ancientwarfarenpc.ship.too_shallow`) and leaves the crew where it is; before, the order was handled like a land order and put the helmsman ashore.
- `NpcPlayerOwned.setSailOrder` clears the old sail route; new `NpcPlayerOwned.tellOwner`; `ShipCrew.isWaterNear`.
- Grappling lines survive a reload: both ships write the line into their own save data (`ship.lines` list in the `ship` tag: both ship UUIDs, holder UUID, anchor cells, phase, held timer, rest length). On load the ships keep it as pending; as soon as both ships are loaded either one makes a new hook (`GrappleHook.restore`) and the other drops its copy. A line that was still flying resumes as pulling. The holder check is suspended for 10 s after a restore so the thrower can load. If the other ship does not come back within `grapple_reattach_ticks` loaded ticks of the waiting ship, the line is dropped. Cutting all lines clears pending ones too. Old saves have no `lines` entry and load as before.

### Files
- Vehicles: `entity/ship/GrappleHook`, `entity/ship/ShipHelper`, `entity/VehicleSettings`, `config/AWVehicleStatics`.
- NPCs: `ai/vehicle/NpcAIShipHelm`, `ai/vehicle/ShipCrew`, `entity/NpcPlayerOwned`, `lang/en_us.json`.

### Config
- `ancientwarfarevehicle-common.toml`: `grapple_reattach_ticks` (default 1200, range 20 to 100000).

### Deviations and limits
- The hook entity is still not saved (its entity type is `noSave`); the line is saved on the ships and the hook is rebuilt, which gives the same result without a second source of truth.
- If one ship of a line unloads while the other stays loaded, the live hook is released as before; the unloaded ship's saved copy can bring the line back when it returns within the timeout.
- Not play-tested, only built.
- Left as documented limits, unchanged: NPCs crossing decks with a leap, gunners and archers not boarding, sea routes running only in loaded chunks, faction NPCs not taking a player ship's helm.

## Siege towers and mantlets

What changed (vehicles: `entity/siege/SiegeHelper`, `entity/VehicleBase`, `helpers/VehicleMoveHelper`, client `model/ModelSiegeTower` and `render/VehicleModels`, `en_us.json`; NPCs: `ai/siege/{SiegeJob, SiegeDuty, SiegeOps, NpcAISiegeDuty}`, `entity/NpcCombat`):

1. **Bridge height.** When the bridge is lowered (key, NPC job, or the auto bridge) the tower looks at what is under the last plank row in front of it. If the top of that surface lies from 3 blocks below to 1 block above the top platform and has two free blocks over it, the bridge ends level with it: the planks step down or up row by row (the first row on the surface lies at its height, the rows before it step towards it), and the model's bridge tilts to match. Otherwise it stays level with the platform as before. The planks are still walkable part entities, never blocks. The middle column is sampled first, then the sides, so a crenellated wall top gives the height of the middle column. The shape is two new synced values on the vehicle and is saved with the bridge (new NBT `bridgeDelta`, `bridgeSupport`; missing means level, so old saves load). The NPC siege planner now accepts towers whose platform is 1 block below to 3 blocks above the wall top (it was 1 below to 2 above).
2. **Siege jobs survive a reload.** Every NPC with a siege duty saves it (NPC NBT `SiegeDuty`: role, stage, slot and a copy of the job: vehicle, kind, target wall, approach side, phase, route and waypoint index, the timeout counters). After a load the first crew member whose goal runs brings the job back into the in-memory registry and the others join it, as long as the NPC still has the attack order at that target. A job that was planning plans again from where its vehicle stands; one that was on its way follows the saved route. A restored duty waits up to 10 seconds for its vehicle to load before it gives up. Existing timeouts keep counting from the saved values. Jobs that were done or failed are not restored. Old saves have no tag and behave as before.
3. **Roof riders.** Nothing holds a tower still because of what stands on its roof any more. Living things and items standing on the roof (and on the lowered bridge) are carried with the tower, turning round its middle like a ship deck; the local player is carried by his own client, the server only resets its floating check. A carried entity whose new place would be inside a block stays where it is; the tower itself still stops dead at blocks. The bridge still cannot be raised with somebody on it. The message key `bridge.roof_busy` was removed (no longer used).
4. **Mantlet collision.** The shield (3 blocks wide, 0.55 blocks in front of the middle) is now tested against blocks on its own with seven small boxes across its width, from 0.65 blocks above the ground (the mantlet still climbs 0.6) to the top. If a move or a turn would put the shield into a block, the mantlet keeps the new place with the old heading if that is free (slides), otherwise it stays as it was and stops. It still fits through gaps 3 blocks wide when lined up. Server side only (the client follows the server).

Config keys: none added.

Deviations and limits:
- The bridge shape is chosen once when it is lowered. The planks are flat boxes, so a drop of 3 over a short ramp is a staircase of planks (steps of up to 1.5 blocks), not a smooth slope; the model draws one tilted plank, so it only approximates the planks. Walking up a step of 1 block (wall top 1 above the platform with the wall touching the first plank row) needs a jump.
- Only the bridge height rule was widened in the NPC planner; a wall with a parapet at the target is still not handled.
- The tower's roof carry is done on the server for mobs, NPCs and items and on the client for the local player only (as for ships); other players are carried by their own clients.
- The mantlet's shield is not a collision box, it is a check after the move, so entities cannot be pushed by it and it does not block other entities.
- Left as-is by request: siege crews do not open gates, faction NPCs do not use siege engines (raids come later).
- Not play-tested, only built.

## Final check of this round

Reviewed the whole uncommitted diff, fixed what was found, built, and started the dedicated server once.

### Checked and found sound
- **Old worlds:** every new NBT tag is optional and read with a default: household `handBuilt` (false), ship `lines` (none), vehicle `bridgeDelta` and `bridgeSupport` (level bridge), NPC `SiegeDuty` (no duty), design `worksite.area` (none). Nothing was renamed.
- **Client classes on the server:** the new client imports are only in `client/screen/BuildingTabView`. The server classes use no client packages.
- **Blueprint actions:** `renameBlueprint` and `deleteBlueprint` look the blueprint up by id in the sender's own set, so the id is never used as a path before it is known to be the player's. Delete is refused while any unfinished build uses it. The soldier gear swap takes one item, puts the old one in the storage first, and undoes the swap when the storage cannot take it.
- **Griefing:** hand-built houses start from beds the settlement already counts (inside its land); hand-built chapels only take bells and lecterns inside the land (`Settlement.contains`). Rooms elsewhere are never looked at.

### Changed in the check
- `FamilyHomes`: one search scans at most 12 rooms (each a flood fill of at most `hand_built_room_max_cells` cells); the rest is left to the next search.
- `ChapelSites.refresh`: the chunk walk runs at most every 20 seconds for each settlement (it ran with every 5-second maintenance), and it no longer copies the block entity map of each chunk.
- `NpcAIPlayerOwnedEquipGear.swap`: when the undo cannot put the item back either, it is dropped at the soldier instead of being lost.
- `BuildingMenuSupport.renameBlueprint`: a blueprint name from the client is cut to 160 characters before it is cleaned.
- `FamilyLife.isFreeToMeet`: sleeping members do not count as meeting (schedule off).

### Server start
`./gradlew :ancientwarfarereforged:runServer` reached "Done" in about 3 seconds with no mod errors or exceptions; 25 settlement designs loaded. The config files were rewritten with the new keys. Nothing was played in a world: gameplay of the fixes is still untested.

### Docs
- `mods/ancientwarfarereforged/release/changelog.md`: new top section "1.2.0 (in progress)", Known issues list trimmed to what remains (and the families line in the Families section). Mod versions were not changed.
