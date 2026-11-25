
## Hunger once, caravan cargo, per-task tools

### 1. Hunger counts once
- `NpcPlayerOwned.getWorkFactor()` is now only the morale factor. Hunger stays in the personal morale (`NpcMorale`, Morale tab). The separate factor did nothing else (starvation damage is a different path), so it was removed.
- Config keys removed: `hunger.hungry_work_factor`, `hunger.starving_work_factor` (and the `SettlementConfig` fields). Existing config files keep the unknown keys harmlessly. Hungry NPCs still walk slower and do not heal.
- Not play-tested.

### 2. Caravan buy-order exploit
- A caravan wagon now starts empty (`CaravanStock.stockCargo` and the spawner's `tailorCargo` are gone). `CaravanState` saves `supply` (items its trades and, with `caravan_tailor_buy_orders`, its faction/land lists can give), `supplied` (count per item this visit, capped at 2 stacks per item) and `virtualCargo`.
- `CaravanLogic.fillBuyOrders`: for each fillable buy order the goods are made in a scratch inventory, the stall does its normal all-or-nothing `fillBuyOrder` against it, the coins move into the wagon (checked to fit beforehand) and the leftover scratch goods vanish. Goods never exist as loot before they are paid for.
- Killing the leader drops only what the wagon holds: the coins it earned. A caravan saved before the change (`virtualCargo` false) drops only coins and its old stocked goods are removed. Reputation penalty and guards logic are unchanged.
- Config: `caravan_tailor_buy_orders` keeps its name; its comment now says goods are made at the trade.
- Deviations: the goods a faction takes in exchange (trade inputs) are no longer in the wagon, so they cannot be sold to buy orders; supply per item per visit is capped at two stacks instead of by what was stocked. Faction trader stock and the player trade view are unaffected. Not play-tested.

### 3. Per-task tool switching
- New `ai/owned/WorkTool` (PICKAXE, AXE, SHOVEL, HOE, SHEARS): tools recognised by `ItemTags.PICKAXES/AXES/SHOVELS/HOES` (plus item abilities, shears by `ShearsItem`), blocks by `BlockTags.MINEABLE_WITH_*`; leaves, wool, cobweb, vine want shears; TILL wants a hoe; SHEAR wants shears; tasks with no wanted tool (planting, crops, collecting) use the role tool.
- `NpcWorker` has a 4-slot saved `toolBelt` (dropped on death or repack, no screen). `StaffedWork.chooseTool` runs each working tick before the swing: hand, then belt, then pouch (best break speed on the block); the old hand item goes into the belt (from the pouch) or the same slot. Between tasks the worker takes its role tool back. `hasMatchingTool()` counts a role tool in the belt, so the role-tool speed factor, morale and fetch goal are not confused by a swapped tool.
- If none is found, `setWantedTool` makes `NpcAIPlayerOwnedFetchTool` fetch one from settlement storage into the belt (retry every 30 s, no "no tool" thought for secondary tools); meanwhile the worker keeps working with what it holds (break speed follows the held tool via the existing `rate`).
- Config: `jobs.tool_switching` (default true).
- Not play-tested: the visible swaps, shears on animals (the site uses its own shears), fetch behaviour with real storages.

## Vehicles: saved seats and hitch ropes

What changed
- Seats are saved: `VehicleBase` writes a `seats` list (seat index + rider UUID) in its saved data. After a load the riders re-attach one by one; `addPassenger` gives each one the seat it had (matched by UUID), and anyone without a saved seat gets the first free seat of the usual order, skipping seats still kept for returning riders. The reservations are dropped 100 ticks (5 s) after the load; a vehicle saved again before that keeps the entries of riders that had not returned. The synced `seat_data` is unchanged.
- Hitch ropes are drawn: `client/render/HitchRopeRenderer` (called from `VehicleRenderer`) draws a vanilla-leash style strip (24 segments, small sag, light from both ends) from the cart's drawbar front (`hitchOffset`, 0.6 blocks up, offset sideways for a pair) to the rear of each hitched horse. The horses were already synced (`hitch_data`, `getHitchedIds`), so nothing new is sent. `CartHitch.lateralAt(index, count)` is a new static helper so the client can place a pair.
- Files: `entity/VehicleBase.java`, `entity/hitch/CartHitch.java`, `client/render/VehicleRenderer.java`, `client/render/HitchRopeRenderer.java`.
- Config keys: none. New text: none.

Deviations / limits
- A rider that returns later than 5 s after the vehicle loads (for example a player logging back in long after) gets a free seat in the usual order, not the saved one, unless that one is still free and unreserved (then they take the usual-order seat).
- The rope sag is a fixed parabola (up to 0.3 blocks), not a physical sag; the rope uses the leash render type and colour.

Not play-tested
- Compiled only; no client run. Rope height and ends, seat restore after a real save and reload, and a pair of horses were not looked at in game.

## NPC ship crews (rowers, gunners, passengers)

What changed
- Owned NPCs can crew the ships (fishing boat, cog, war galley). The helm stays with the player: NPCs never take the DRIVER seat of a ship (`NpcAIFindVehicle` now skips ships, so siege engineers do not try to "pilot" one).
- Ordering aboard: the command baton in Move mode aimed at a ship (a click on a hull part counts, `NpcCommand.handleServerCommand` turns it into the ship) sends the selected NPCs to the nearest point of the hull; within 3 blocks of the hull they board the first free seat of their role order (`NpcPlayerOwned.getShipSeatPreference`: soldiers and siege engineers GUNNER, ROWER, PASSENGER; every other owned NPC ROWER, PASSENGER). Boarding takes exactly that role (`VehicleBase.boardStrict`, no fall back to the helm). The baton shows an action bar message with the number ordered and the free seats. The order ends once aboard, when there is no seat, or after 30 s without getting there.
- Getting off: a Move or attack-area order, or a Move order at the ship they sit in, puts the NPC on the nearest dry land to the target within 7 blocks of its seat (`ShipCrew.disembark`), else on the deck beside its seat; the order then runs as usual. When the ship sinks the vehicle throws everyone off (existing behaviour).
- Idle soldiers (`NpcCombat` with no order, target, patrol orders or alarm) near a ship their owner is riding walk to it and take a free gunner, else rower, seat (`NpcAIShipAutoCrew`). They stay until ordered off.
- Duty (`NpcAIShipCrew`, on every owned NPC, top priority, holds the move/look flags so work, wander and patrol goals leave a crew member in its seat). Rowers and passengers just sit (each occupied ROWER seat adds speed through the ship's rower power). A soldier in a gunner seat picks a target and aims and fires its mount through the same server calls as a player's HardpointAim/HardpointFire payloads (`ShipHelper.aimHardpoint`, `fireHardpoint`): so reload, wind-up, the ship's ammo and the arc limits apply. Targets: its own attack target, else the nearest living entity it is hostile to by its owner's faction relation (`isHostileTowards`) that it may target with line of sight (`AIHelper.isTarget`), within `ship_gunner_range` and reachable by the mount (arc, elevation limits, shot speed of the loaded ammo). It leads moving targets, switches ammo type when the loaded one runs out, and holds fire when the shot would run over the ship's own deck more than 3 blocks.
- Vehicles module: `ShipHelper.solveHardpoint` (server side aim solver with the client's launch maths), `ShipHardpoint.isReady`, `VehicleAmmoHelper.ensureAmmoAvailable` (server safe ammo switch; the old `setNextAmmo` sends a client payload), `VehicleBase.boardStrict`, gunners face their mount. The cog got 4 ROWER seats (beside the mast) and `rowPower` 0.4 (a tenth of the top speed per rower; its model draws no oars). The war galley already had 8 rower seats; the fishing boat has none.
- Files: NPC `ai/vehicle/{ShipCrew, NpcAIShipCrew, NpcAIShipAutoCrew, NpcAIFindVehicle}`, `ai/owned/NpcAIPlayerOwnedFollowCommand`, `entity/{NpcPlayerOwned, NpcCombat}`, `entity/vehicle/NpcSiegeEngineer`, `npccommand/NpcCommand`, `config/AWNPCStatics`, `lang/en_us.json`; Vehicles `entity/VehicleBase`, `entity/ship/{ShipHelper, ShipHardpoint}`, `entity/types/VehicleTypeCog`, `helpers/VehicleAmmoHelper`.
- Config (`ancientwarfarenpc-common.toml`, `01_ship_crew_settings`): `ship_gunners_fire` (true), `ship_gunner_range` (60), `ship_auto_crew_soldiers` (true), `ship_auto_crew_range` (24).
- Text: `message.ancientwarfarenpc.ship.boarding`, `message.ancientwarfarenpc.ship.not_enough_seats`; the baton mode name now reads "Move/Guard/Mount/Board ship".

Deviations / limits
- NPC helmsmen (an NPC sailing a ship, apart from the dock's fishing worker) are not done: it needs route planning for open water beyond the dock autopilot. Rowers only add speed while the player (or the dock autopilot) sets the sail and oar trim.
- NPCs cannot walk or swim out to a ship (the pathfinder does not see the hull parts): the ship must lie within 3 blocks of the shore or a dock, otherwise the order times out. Faction NPCs do not crew ships.
- NPC gunners use the ship's own ammo stock whatever `ownedSoldiersUseAmmo` says (only owned siege engineers firing as the first crew get the unlimited-ammo rule of the option). A ship with no ammo: the gunners sit idle. A crew member does not leave its seat to eat, so a hungry rower stays hungry until ordered off.
- The over-the-deck check is a straight-line heuristic; friendly fire from a missile that flies over players on another ship is not prevented.
- Noticed, not changed: `ShipGunnerClient` clamps the launch angle that `Trig.getLaunchAngleToHit` returns wrapped to 0..360, so a flat shot below the horizon (for example 357 degrees) is clamped to the upper elevation limit for a human gunner.

Not play-tested
- Compiled only (`./gradlew build`); the dedicated server was not started (no eula.txt may be created). Boarding from a dock, seat positions of the cog's new rowers, disembarking onto land, the aim and fire of an NPC gunner at a real target, the targeting of hostile faction NPCs and the auto crew were not seen in game.


## JEI recipe transfer ("+" button) for the Ancient Warfare crafting grids

What changed: JEI's "+" / shift-"+" button now fills the crafting grid of the engineering bench, the auto crafting block and the warehouse crafting station, for the engineering bench (research) recipe category and for vanilla crafting recipes (as the original did). The client handler checks the recipe (research book present, own research known, grid contents fit the inventories, ingredients available) and highlights missing ingredient slots; on click it sends `TransferRecipePayload` (container id, recipe name, shift flag) to the server. The server (`RecipeTransfer.execute`) checks the open menu is an `ICraftingContainer` with that id, checks research for the crafter in the book slot, returns the grid contents to the inventories, takes one item type per ingredient (storage first, then player inventory) into the grid and selects the recipe (`setRecipe(recipe, true)`). Shift = the largest multiple of the ingredient counts possible (capped at the stack size). Nothing changes if the recipe cannot be supplied.

Files: ancientwarfare: `crafting/RecipeTransfer.java` (new, no JEI types), `network/TransferRecipePayload.java` (new, registered in `AwNetwork`), `compat/jei/AwRecipeTransferHandler.java` (new), `compat/jei/AwJeiPlugin.java` (transfer handlers, bench as crafting catalyst), `crafting/CraftingMemoryController.java` (`setRecipe` now makes the next `serverRefresh` return true so the menu recomputes the result slot), `lang/en_us.json` (inventory.full, missing, invalid). ancientwarfareautomation: `compat/jei/AutomationJeiPlugin.java` (new), `build.gradle` (compileOnly JEI API + maven repo).

Config keys: none.

Deviations: the original matched ingredients with the grid item JEI displayed; the port picks any inventory item the ingredient accepts (one type per cell). Research of a book bound to another player is only checked on the server (the player sees an action bar message instead of a red button). The original registered separate shaped and shapeless categories; the port has one research category.

Not play-tested: nothing was run in game (build only). Untested: shift-"+" multiples with research recipes that have counted ingredients, automation menus, JEI absent at runtime (no JEI class is referenced outside compat/jei).


## Final check (review pass)

- Reviewed the full diff for crashes, client or JEI classes on the server, item loss or duplication (caravan fills, JEI transfer), old-save NBT and null riders. Nothing found in the caravan and JEI paths (the scratch inventory is discarded, the coins are paid from the stall and moved into the wagon only after the fit check; the transfer plans from a copy and extracts at most what it plans). Old saves: the missing `seats`, `toolBelt` and `virtualCargo` tags load as empty or false. No client or JEI class is referenced outside `client` and `compat/jei`.
- Fixed: an idle soldier ordered off a ship by a Move order walked back and boarded the same ship at once while its owner stayed aboard (the order clears on arrival, the auto-crew goal then saw an idle soldier). `NpcPlayerOwned` now remembers the ship it was ordered off (`refusedShip`, not saved, forgotten when the owner leaves the ship) and `NpcAIShipAutoCrew` skips it.
- Fixed: `CartHitch.java` had mixed line endings (the added lines were LF in a CRLF file); all lines are CRLF again.
- Built: `./gradlew build` passes. `./gradlew :ancientwarfarereforged:runServer` reached `Done (` with no errors from the mods (the first start rewrote the NPC config files with the new keys). No eula.txt was created.
- Not play-tested: everything above is compile and server start only.
