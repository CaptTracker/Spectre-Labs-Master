# Rework phase 3: living worksites

## Stage: staffed work

Code: Core `api/WorkTask`, `api/WorkAction`, `api/IWorksiteTasks`; Automation `WorksiteBlockEntity` (task bookkeeping and the converted update loop), `FarmWorksiteBlockEntity`
(`getDepositInventory`), `CropFarmBlockEntity` (the converted site), `AWAutomationStatics` (server config `ancientwarfareautomation-worksites.toml`);
NPC `ai/owned/StaffedWork` (the work session), `NpcAIStaffedWork` (job-board goal), `NpcAIWorkerDeposit`, `NpcWorker` (pouch), `settlement/JobBoard` (slots).

### How it works
- A worksite block entity implements `IWorksiteTasks` next to `IWorkSite`. `isStaffed()` is `supportsStaffedWork() && !machinesWorkAlone`. Not-yet-converted sites return false
  and keep the machine loop (workers crank torque through `NpcAIPlayerOwnedWorkRandom`, as in phase 2); `machinesWorkAlone` (default false) does the same for converted ones.
- A staffed site skips `getNextAction`/`processAction`. It lists tasks (`listTasks`, best first), reserves one per worker (`claimTask`, refreshed every 20 ticks with `keepTask`,
  freed after `task_claim_ticks` 200 without a refresh, so a dead worker frees it), and does `performTask(task, drops)` for the one target on `completeTask`.
  Unreachable targets go on a `task_fail_cooldown_ticks` (600) cooldown. Nothing is saved: after a restart every task is open and the scan rebuilds the list.
- `hasWork()` of a staffed site is "has any task" (and not powered off by redstone); the job board's slots for a staffed site are
  `min(maxWorkersPerSite, workers + open tasks)` (at least 1 while it has work), so the board and the hall Overview show how many workers a site can usefully take.
- The worker: claims the job on the board (phase 2), `claimTask`, walks within reach (`npcActionRange` blocks, at least 2, measured to the block centre) of the target through the normal `NpcAI` movement,
  faces it, swings every 6 ticks (particles of the block, its hit sound) and fills a progress counter by `toolSpeed * skill * torqueBoost` per tick that `advanceWorkTick()`
  allows (hunger factor times tool factor). `toolSpeed` is the held tool's destroy speed on the block, 1..8, for breaking actions only; `skill` is `getWorkEffectiveness`
  (floor 0.1). Done at `baseTicks` (breaking actions: 30 ticks per hardness, min 10, from `WorkTask.breakTicks`); breaking actions advance `destroyBlockProgress` and clear it.
  Then the site does the action; the worker gets `npcXpFromWork` per completed task and an item pickup sound when the pouch grew.
- **Carry pouch**: 9 slots on `NpcWorker` (`pouch`, `pouchSite` in the NBT), dropped on death and on repack. It is the drop target of `performTask`. With fewer than 3 free
  slots the worker walks to the site and empties the pouch into `getDepositInventory()` before the next task; `NpcAIWorkerDeposit` (priority 5, before the schedule goal) does the
  same when the shift is over (not work time, has to go home, or no job any more). A full site inventory: retry after 10 s while working on, 20 s in the deposit goal. If the site
  is gone the items are dropped on the ground. Seeds in the pouch go back to the plantable inventory first through the farm's combined deposit inventory.
- **Torque boost**: a site with stored torque speeds its workers by `1 + (torque_max_boost - 1) * min(1, stored / cost of one action)`. Each completed task uses the
  action cost (a work unit / `taskEnergyDivisor()`, 5 for the crop farm) out of the stored torque. Workers no longer crank staffed sites.
- **Work orders** still override: an entry of a work order that is a staffed site is worked through the same `StaffedWork` (no task or time up moves to the next entry).

### Crop farm (first converted site)
- Tasks per target: HARVEST (15 ticks), FERTILIZE (20, needs bone meal), PLANT (20, needs a plantable item; at most as many tasks as plantable items), TILL (20), in that priority,
  at most 64 per action. The old iterator methods became per-target (`harvestAt`, `fertilizeAt`, `plantAt`, `tillAt`) shared by the machine loop and `performTask`.
- Still the flat single layer: natural-terrain scanning, the vertical range and the Y-surface scan are the next stage.

### Deviations and choices
- Added `WorkAction.FERTILIZE`: the spec's action list has no bone meal action, and the crop farm has four kinds of work.
- Config is a new SERVER spec `ancientwarfareautomation-worksites.toml` (`machines_work_alone`, `torque_max_boost`, `task_claim_ticks`, `task_fail_cooldown_ticks`); the existing
  automation spec is COMMON and cannot hold per-world numbers cleanly. Fields are baked on config load (`AWAutomationStatics.bakeServer`).
- The task is claimed from the site after the job board claim (two levels), not a separate board entry per task. The board only counts open tasks.
- HARVEST uses a fixed short duration instead of block hardness (crops have hardness 0, stems would otherwise be instant); the crack overlay still follows the progress.
- A task whose completion fails (inventory full, target changed) is dropped from the site's queue as in the old loop; the incremental scan lists it again later.
- No new blocks, items or lang keys, so no blockstate/model/texture/loot/recipe work in this stage.
- Not play-tested with a real worker; `:ancientwarfare`, `:ancientwarfareautomation`, `:ancientwarfarenpc` build and `:ancientwarfarestructure:runServer` reaches `Done (`.

## Stage: farms on natural terrain

Code: `BoundedWorksiteBlockEntity` (vertical range), `FarmWorksiteBlockEntity` (surface scan), crop/tree/fruit/fish farm block entities, `entity/WorkBobberEntity`,
`init/AwAutomationEntities`, `client/render/WorkBobberRenderer`, `BoundsAdjustMenu` + `GuiWorksiteBoundsAdjust` (Y-/Y+), Core `IWorksiteTasks` (new default methods
`taskStarted`, `isTaskReady`, `completeTask(..., actor)`), NPC `StaffedWork` and `NpcWorker` (climbing).

- **Vertical range**: farms work `site Y - range .. site Y + range`, range 0..4 (default 4), saved as `yRange`, synced, set with Y-/Y+ in the bounds screen, drawn in the work area box.
  The bounds' own Y is no longer used by farms.
- **Surface scan**: per target column `findSurface` takes the topmost block that `isGround` accepts (crop farm: tillable or soil; tree farm: tree soil) with usable space above; the
  scan position is the block above it. A column whose first non-air block is a fluid has no surface. Slopes work.
- **Crop farm**: tills the surface, plants, harvests. Unwatered farmland is allowed. Tilling needs air above, or vegetation (replaceable, not solid) which the worker clears first
  (deviation from "air only": natural ground is covered in grass, and vanilla would refuse to hoe under it). Never under anything else; never under water.
- **Tree farm**: the oak scanner now searches `all_up_or_level` (leaf distance 4) so big oak branches are found; 2x2 trees were already covered. Chop tasks are only the lowest layer of the
  queued tree, so it comes down bottom up. Leaves are left to decay (shears still give SHEAR tasks). Workers out of reach of a target climb: `StaffedWork.climb` rises the worker beside the
  target without gravity or collision (flag `NpcWorker.workClimbing`, no fall damage for 10 s afterwards). The foot of every chopped trunk is remembered (`replantSpots`, saved) and
  replanted first; elsewhere saplings are only planted with no log or sapling within 2 blocks (also in machine mode), so a natural area is not carpeted.
- **Fruit farm**: scans the whole vertical range of each column (fruit grows above ground, so there is no surface); tasks HARVEST (pick), PLANT, FERTILIZE.
- **Shore fisher**: FISH tasks on source water blocks with air above and a standable shore block beside, in the old 4-layer area. The worker stands within 3 blocks, casts a `WorkBobberEntity`
  (cosmetic, not saved, self-removes 5 s after the site stops refreshing it), waits for the vanilla bite (100-600 ticks minus rod lure, 20-80 approach, 20-40 bite, 6-tick reaction),
  reels in. Loot is the `gameplay/fishing` table with the worker's rod (or a plain one), luck of the sea plus the luck attribute. Treasure: the table's open water condition needs a
  `FishingHook`, so a loose, never-spawned hook stands in as `this` only when the bobber's port of the vanilla 5x4x5 test says open water. Rod damage and xp orbs are not done.
  The machine loop of the old farm remains for `machinesWorkAlone`.
- Added `AwAutomationEntities` with a raw `DeferredRegister` because `ModRegistrar` has no entity registry (the NPC module does the same).
- Not play-tested: climbing, the bobber visuals and the worker's real fishing were only compiled; `:ancientwarfarestructure:runServer` reaches `Done (`.

## Stage: mine styles

Code: `worksite/mine/` (`MineMode`, `MineOp`, `MineCell`, `MineSite`, `MinePattern`, `OpenQuarryPattern`, `SlicePattern`, `ShaftPattern`, `TunnelPattern`), `QuarryBlockEntity`,
`QuarryBoundsMenu` + `GuiWorksiteQuarryBounds` (the mine screen), config `branch_length` (server, 16), NPC `StaffedWork.climb`, 11 lang keys.

- **Strategy**: `QuarryBlockEntity` implements `MineSite` and holds one `MinePattern` per mode, each with its own saved cursor (`patterns.<mode>` in the NBT; an old save's `current`/`finished`
  become the open quarry's). A pattern only plans `MineOp`s (SEAL, VEIN, DIG, LADDER, TORCH_FLOOR, TORCH_WALL) from the world; the quarry turns them into MINE and PLACE tasks (cached 5 ticks)
  and applies them in `performTask`. The machine loop (`machines_work_alone`) does the first planned op into the inventory.
- **Open quarry**: the old cursor, tasks are the block at the cursor and the next diggable ones of its slab. Cursor scanning over air is budgeted (8192 steps per plan).
- **Shaft**: 3x3 at the centre of the work area from the top layer to the chosen Y (clamped to the world bottom: bedrock is skipped). Ladder in the middle of the north row against its wall, wall torch on the south wall every 8 layers.
- **Branch / strip**: a 1x1 access shaft with a ladder at the west end of the area (x = min + 1), then a tunnel east along x to the area's edge. Branch: main tunnel 3 wide x 2 high at the chosen Y, side branches every third column
  (not the first) to both sides, `branch_length` long but never out of the work area, floor torches every 8 blocks. Strip: 1x2; ores (`c:ores`) touching an open tunnel cell are mined and
  the vein is followed through a saved queue of neighbouring ores (max 256). Tunnels are left in place.
- **Work area limits**: tunnels and branches stay inside the work area (so with a 16 wide area a branch is at most 7 long); the shaft stands at the area's centre column.
- **Digging order**: a cell is offered only if it touches open space (or is the first one left), so workers dig from the frontier and never target an unreachable pocket.
- **Placing**: ladders and torches are taken from the quarry inventory and only offered while it holds some (otherwise that ladder/torch is skipped for good once the cursor moves on, a deviation: no waiting for supplies).
  Plugs use cobblestone from the inventory, or are free when there is none (the void filter would otherwise starve them). Fluids in a planned cell, and fluids touching an open cell, get a cobblestone plug; a plugged cell
  is remembered and never dug again, so a tunnel into a lava lake ends at the plug.
- **Screen**: the quarry's "Height" button became "Mine" and opens the former height screen, now with height (open quarry), Y level, mode button (cycles) and three void checkboxes (cobblestone/cobbled deepslate, dirt/coarse dirt, gravel).
  Mine settings are sent as they change and validated on the server (mode ordinal, Y clamped to world bottom+1 .. quarry Y-3, default quarry Y-20). Closing only revalidates the bounds if the height changed.
- **Workers**: `StaffedWork.climb` lets MINE/PLACE workers descend (noPhysics, like the tree climb) to the target's level from up to 12 blocks away, so they get down shafts and pits.
- Not play-tested: worker descent in shafts, tunnel pathing, the ladder/torch placement and vein following were only compiled; `:ancientwarfarestructure:runServer` reaches `Done (`.
- Known gaps: the access shaft has no torches; the ladder is missing where its wall was dug (branch main tunnel level is fine, strip too); no lava damage avoidance for the worker; chunk loading follows the first planned op only.

## Stage: new and improved worksites

Code: `ApiaryBlockEntity`, `SmelterBlockEntity`, converted `AnimalFarmBlockEntity`; menus `ApiaryMenu`, `SmelterMenu` (+ `GuiWorksiteApiary`, `GuiWorksiteSmelter`);
registered in `AwAutomationBase` (blocks, block entities, capabilities, flammability), `AwAutomationMenus`, `WorksiteMenus.Kind`, client screens and renderers.
Assets: blockstates/models/loot/item models and textures (`apiary_*` tinted from the fruit farm, `smelter_*` from the quarry, generated with a pure-Python PNG tint), lang, axe tag,
research recipes (`apiary`: research `ancientwarfare:farming`, honeycomb; `smelter`: `ancientwarfare:engineering`, furnace).

### Apiary
- Work type FARMING (farmer role, so the job board and hall roles need no change). Work area: bounds plus vertical range (default 4). Inventories: main (output), flowers (front), tools (glass bottles, shears, a Smoker block).
- Tasks: COLLECT (bottle -> honey bottle) and SHEAR (3 honeycombs, shears lose durability) on hives (`#minecraft:beehives`) at honey level 5, alternating when both tools exist; PLANT a small flower while fewer than 4 grow in the area.
- Calm: a hive with bees is only harvested when `CampfireBlock.isSmokeyPos` (vanilla: lit campfire below within 5 blocks) or a Smoker item is in the tool inventory (reusable, not consumed). A hive without bees needs no smoke.
  Deviation: uncalm hives with bees are skipped instead of harvested with angry bees. Bees stay in the hive.
- Machine fallback (`machinesWorkAlone`) does the first task per torque action.

### Smelter
- Work type CRAFTING (craftsman role). Links every furnace, smoker and blast furnace in its area (scan every 5 s, at most 64). Inventories: main (output), input (front, 9 slots), fuel (3 slots).
- Tasks: SMELT loads a furnace with input matching its recipe type (smelting/smoking/blasting) and fuel when its fuel slot is empty; COLLECT takes the result slot (furnace xp is not collected).
- Internal fire: a worker loads up to 16 items (SMELTING recipes), the fire burns fuel from the fuel inventory by itself, a COLLECT task takes the output. Redstone pauses it like every worksite.
- Logistics: through `InventoryWorksiteBlockEntity` it is an `IWorksiteLogistics`: output is the main inventory; needs are coal/charcoal (fuel, 16) and `c:raw_materials`/`c:ores` (input, 64).

### Animal farm
- Converted: SHEAR, MILK, COLLECT (eggs and other `animal_farm_pickups` items lying in the area), BREED (the worker feeds both animals: item particles and eating sound, then love mode) and CULL, in that priority.
  Targets are the animals themselves; a task keeps its position while the animal only wanders (<4 blocks) so claims survive a rescan. The rescan stays at 200 ticks.
- Milking: a cow can be milked once per 3000 ticks per bucket instead of the machine's chance rule. The passive item pickup only runs in machine mode.
- Species limits per site (pigs, cows, chickens, sheep) already existed in the second screen and are kept.

### Fix
- `InventoryWorksiteBlockEntity.getDepositInventory()` now returns the main inventory. The shore fisher (and any inventory site that did not override it) had `null`, so its workers could never empty their pouch.

## Stage: verify

Fixes found by review (none play-tested):
- `StaffedWork.reset(null)` now looks up the site it claimed at and releases the claim (goal stop, new orders); before, the claim stayed until it expired after 200 ticks.
- `NpcWorker.crackPos` remembers the block with a crack overlay; `NpcWorker.remove` resets it to -1 so a worker that dies or unloads mid-break leaves no cracked block.
- `OpenQuarryPattern` plugs fluid cells of the slab that touch open space (SEAL tasks, saved `sealed` set, the cursor skips plugs); only the tunnel modes did this before.
- `findSurface`, the fish, Apiary and Smelter scans skip columns in unloaded chunks instead of loading them.
- `FishFarmBlockEntity.keepTask` ends the claim when the worker's bobber is gone (water removed, entity lost) so the worker casts again; `WorkBobberEntity` removes itself if it was never set up.
