# Rework phase 9, part 1: building design foundation

Not play-tested. `./gradlew build` passes. Only the foundation is here: the design format, the loader, player blueprints, the scanner item and the ghost helper. No planner, builder NPC or zone tool yet. Built-in design content is a separate part; one tiny example tent proves the loader.

## What changed
- **Design format and class** (`settlement/building/BuildingDesign`): immutable, parsed from JSON with strict validation. Placing works in all four directions (`blocks(origin, facing)`, `worksitePos`, `entranceOutside`, `rotatedSize`, `materialCounts`).
- **Loader** (`BuildingDesigns`): a datapack reload listener on `data/<namespace>/settlement_designs/**.json` (any namespace, so add-ons can ship designs). The design id is the file path, for example `ancientwarfarenpc:tent_example`. Bad designs are skipped with a `WARN` log line naming the file and the reason; soft problems (unknown style, unused palette char) are warned and the design is kept. API: `builtIn()`, `get(id)`, `availableTo(ownerUuid)`, `usableBy(design, ownerUuid)`.
- **Player blueprints** (`BlueprintStore`): one JSON file per blueprint in `<world>/ancientwarfarenpc/blueprints/<owner uuid>/<name>.json`, loaded at server start. Id: `ancientwarfarenpc:blueprint/<owner uuid without dashes>/<file name>`. `design.getOwner()` is the owner; built-in designs have owner `null`. API: `save`, `delete` (no UI for delete yet), `of(owner)`, `get(id)`, `count`.
- **Scanner** (`BlueprintScannerItem`, item `ancientwarfarenpc:blueprint_scanner`): click a block for corner 1, a block for corner 2, a block at the entrance (the front is the box side nearest that block; ties use the clicked face). A screen opens (`client/GuiBlueprintScanner`) for name, category, style and a worksite block (a cycle button listing the worksite blocks found in the box: blocks whose block entity is `IWorksiteTasks`). Sneak and use clears the selection; use again (block or air) after the third click reopens the screen. The selection lives in the item's custom data, so the client draws it (`client/ScannerRenderer`: corners, white box, green front bar). The server re-reads the held scanner when the screen answers (the screen sends only name, category, style and worksite). Capture (`BlueprintCapture`): block states only; entities ignored; block entities saved as empty blocks; air becomes `.`; fluids become `_` (leave as is). Beds and containers are counted automatically.
- **Sync:** `DesignNetwork`: the list of `DesignMeta` (id, name, category, style, size, front, beds, storage, worksite, tags, built-in flag, minimums) goes to a player on login, on a datapack reload and after a blueprint is saved (built-in designs plus that player's own blueprints only). The client keeps it in `client/DesignClientHooks.metas()`. `RequestPayload`/`DataPayload` send one design's full JSON on request (only if the player may use it).
- **Preview** (`client/DesignPreview`): `show(designId, origin, facing, 0xRRGGBB)` each tick while a plot is picked, `clear()` when done. Draws the box, a green bar on the front and, once the full design arrived, faint outlines of up to 3000 placed cells. `renderOutline(...)` draws a box from a size only.

## Design JSON format
```json
{
  "name": "Small Smithy",
  "category": "business",
  "style": "stone",
  "size": [5, 4, 5],
  "front": "south",
  "palette": {
    "S": "minecraft:stone_bricks",
    "L": "minecraft:oak_log[axis=y]",
    "D": "minecraft:oak_door[facing=south,half=lower]",
    "F": "ancientwarfareautomation:smithy"
  },
  "layers": [ ["SSSSS", "SSSSS", "SSSSS", "SSSSS", "SSSSS"], ["..."] ],
  "beds": 0,
  "storage": 1,
  "worksite": { "block": "ancientwarfareautomation:smithy", "pos": [2, 1, 1] },
  "tags": ["upgrade_of=ancientwarfarenpc:tent_example"],
  "min_level": 0,
  "min_population": 0
}
```
- `name` (string): shown to players. Missing: the id path (warning).
- `category`: `tent`, `house`, `storehouse`, `business`, `chapel`, `other`. Unknown or missing: `other` (warning).
- `style`: `timber`, `stone`, `desert`, `norse`, `any`. Unknown or missing: `any` (warning).
- `size`: `[x, y, z]`, each 1 to 64 (hard limit, rejected otherwise). x is east-west, y is up, z is north-south.
- `front`: `north`, `south`, `east` or `west`: the side of the entrance, in the authored frame. Default `south` (warning).
- `palette`: object, one character to one block state string (vanilla syntax: `modid:block[prop=value,...]`; missing properties take the default). A key is exactly one non-space character. Reserved characters: `.` clears the cell to air; `_` (and the space) leaves whatever block is there. Reserved characters may not be in the palette. Every block state must exist (an unknown block rejects the design). Block entity data (NBT) is not supported.
- `layers`: array of `size.y` layers, bottom to top. Each layer is an array of `size.z` rows (row 0 is the NORTH edge); each row is a string of exactly `size.x` characters (character 0 is the WEST edge). Every character must be `.`, `_`, a space or in the palette. A wrong count or an undefined character rejects the design and the log says which layer and row.
- `beds`, `storage` (optional ints): how many NPCs it houses and how many containers it has. Missing: counted from the blocks (bed foot parts; chests, barrels, shulker boxes).
- `worksite` (optional): `block` is the registered block id of the worksite block, `pos` is `[x, y, z]` in the authored frame, inside the box. The cell at `pos` should hold that block (a warning if not).
- `tags` (optional list of strings, lowercased). Used by the planner. Conventions: `housing_capacity` (counts for housing), `upgrade_of=<design id>` (replaces that design when the settlement grows; read with `design.tagValue("upgrade_of")`). Free form otherwise.
- `min_level`, `min_population` (optional ints, default 0): minimum settlement level and population for the planner to pick it. Nothing reads them yet.
- Rotation: the design is turned clockwise (seen from above) until `front` looks the requested way; block states are turned with `BlockState.rotate`. The placed box origin is its lowest north-west corner. Doors, beds and other multi-part blocks must list every part as its own cell (the builder places cells one by one; the builder must place both halves in the right order).
- Writing by hand or by script: copy `src/main/resources/data/ancientwarfarenpc/settlement_designs/tent_example.json`; check the server log for `Settlement design ... skipped` lines after `/reload`.

## Files
- New (`settlement/building/`): `BuildingDesign`, `DesignCategory`, `DesignStyle`, `DesignException`, `DesignMeta`, `BuildingDesigns`, `BlueprintStore`, `BlueprintCapture`, `BlueprintScannerItem`, `DesignNetwork`
- New (`settlement/building/client/`): `DesignClientHooks`, `GuiBlueprintScanner`, `ScannerRenderer`, `DesignPreview`
- New resources: item model `blueprint_scanner.json` (vanilla spyglass texture), recipe `blueprint_scanner.json` (settlement ledger, compass, ink sac), `data/ancientwarfarenpc/settlement_designs/tent_example.json`
- Edited: `init/AwNpcSettlements` (scanner item), `settlement/SettlementNetwork` (payload registration), `settlement/SettlementConfig`, `lang/en_us.json`

## Config keys (`ancientwarfarenpc-settlements.toml`, section `building_designs`)
`blueprint_max_width` (48), `blueprint_max_height` (32), `blueprint_max_per_player` (64).

## Deviations
- Ghost preview draws outlines, not real translucent block models.
- The scanner screen is a plain `Screen` opened by a payload, not a menu (it has no slots). The server trusts only the held scanner's selection.
- A player has no way to delete or rename a blueprint yet (`BlueprintStore.delete` exists; the planner screen should call it). The per-player cap is the limit until then.
- The worksite is picked from the worksite blocks found in the box (blocks with an `IWorksiteTasks` block entity), not by clicking the block.
- No recipe research pattern was found for settlement items, so the scanner uses a plain shapeless recipe. No loot tables or tags: there are no new blocks.
- The scanner copies any area the player can reach; there is no claim or permission check.
- Not run on a server: loader, scanning and rotation were checked by compiling and reading only.

# Rework phase 9, part 2: built-in designs

Not play-tested, never placed in a world. Content only: 22 JSON designs in `src/main/resources/data/ancientwarfarenpc/settlement_designs/`, produced by `analysis/port/gen_settlement_designs.py` (outside the repo source; rerun it to regenerate).

## Designs (id = file name)
- Tents (2 beds, category `tent`): `tent_canvas` (white wool, 7x5x6), `tent_hide` (brown wool, 5x4x5). Open 1-wide, 2-high flap flanked by fence posts, hanging lantern.
- Houses (category `house`, tag `housing_capacity`): `house_<style>_small` (2 beds, 7x7) and `house_<style>_medium` (4 beds, 9x9; norse 9x7) for styles `timber`, `stone`, `desert`, `norse`. Tag `upgrade_of`: small timber/desert upgrade `tent_canvas`, small stone/norse upgrade `tent_hide`, every medium upgrades the small of its style. Mediums need level 1, population 4.
- Storehouses: `storehouse_timber`, `storehouse_stone` (7 chests + 5 barrels, counted as 12 storage).
- Businesses (category `business`, worksite block inside): `sawmill`, `bakery`, `tannery` (timber), `smithy`, `smelter_house` (stone, with chimney), `inn` (4 guest beds, crafting table as its table), `stable` (barn plus fenced pen with a gate at the front), `market_stall` (open shelter with striped awning), `farmhouse` (house with a fenced garden of raised farmland behind it; beds 2).
- `chapel` (category `chapel`, tags `chapel`, `devout`; stone, ridge along z, pews, lectern altar, stained glass).
- Layer 0 is the standing level: the terrain is the floor, so interiors are cleared (`.`) and the surroundings are left (`_`). Doors are 2 high with posts, windows are glass panes at y=1, light is hanging lanterns on chains from the ridge (flat roofs: hung from the ceiling) and wall torches beside the door. All paths are at least 1 wide and 2 high.
- Block counts: tents 52 to 85; houses 150 to 281; most others 175 to 190; farmhouse 253, inn 271, chapel 256.

## Deviations
- `farmhouse` has no crop farm worksite: crop farms need a work area set by the player, which a design cannot store. The garden is decoration (farmland with `moisture=7` and ripe crops, no water).
- Tents have no door (an open flap framed by fence posts), because wool tents have no door block that looks right.
- The inn lists its 4 guest beds as `beds` and has no `housing_capacity` tag, so the planner should not count them as housing.
- The norse houses (steep roof) are over the 250 block target (220 and 281).
- Checked by starting `:ancientwarfarereforged:runServer`: the log shows `Loaded 23 settlement designs` (22 here plus the example tent) and no warnings or `skipped` lines. Not placed or walked in a world.

# Rework phase 9, part 3: building zones, the planner, the Building tab and the build job API

Not play-tested, never run in a world. `./gradlew build` passes. The builder NPC that walks and places blocks is not here (next part); this part ends at a clean job API.

## What changed
- **Zone tool** (`BuildingZoneItem`, item `ancientwarfarenpc:building_zone_tool`, shapeless recipe: settlement ledger, stick, string; vanilla golden hoe texture). It is its own item, not a mode of the marker or the baton: the marker is a block and the baton is for orders, while the tool needs two corner clicks and a sneak action like the scanner, so it follows the scanner. Use on a block inside the land of a settlement you manage for corner 1 (kept in the item's custom data), then on a second block: the rectangle (all heights) becomes a plot in the settlement record. Rules: all corners and the middle inside the land, each side from `plot_min_side` to `plot_max_side`, no overlap with another plot, at most `plots_max_per_settlement`. Sneak and use on a plot removes it (planned and queued builds on it are cancelled; a plot with a build under construction stays). Sneak and use elsewhere drops the first corner. While the tool is in a hand the owner's client draws the plots (green outline on the ground, a post at each corner), the footprints of the builds (yellow planned, orange queued, cyan building, grey done, red demolition) and the rectangle to the block looked at (`BuildingEvents` sends `BuildingNetwork.PlotsPayload` every half second to holders, `client/PlotRenderer` draws it).
- **Data in the settlement record** (`Settlement.getBuilding()`, class `SettlementBuilding`, saved under `building`): plots (`BuildingPlot`), the build queue in order (`BuildProject`), the planner settings (auto planning, auto approval, preferred style; null means the config default). Plots and projects have ids (UUID).
- **Planner** (`BuildingPlanner`, called once a second from `SettlementData.serverTick`): every 100 ticks `BuildJobs.maintain` (progress, finished builds, posted needs); every `planner_interval_ticks` (600) and only if auto planning is on, one new project at most, in this order: housing (members above beds, counting beds of builds in progress; tent while members are under `tent_below_population` or the storage holds less than `afford_fraction` of a house, else the house whose beds fit the shortage), a storehouse (storage slots used at or over `storage_full_fraction`), a business (a need with status "no site" and a hint that maps to a design tag: smithy, bakery, sawmill, tannery, ingot gives smelter; or a member with a worker role whose work type has no job on the job board, using the design whose worksite block has that work type, read from the block entity), a chapel (from `chapel_population` members with no priest member and no chapel build), an upgrade (a finished build that another design lists in `upgrade_of`, members at or over `tent_below_population`, storage holds `upgrade_afford_fraction` of the new one). A project is `PLANNED` (owner approves) or, with auto approval, `QUEUED`. Designs: built-in plus the founder's blueprints, filtered by the preferred style (designs of style `any` always fit; if nothing of the style exists the planner falls back to any style); `min_level` and `min_population` are honoured (see deviations). Plans stop while `max_pending_builds` builds are open.
- **Upgrade flow:** the better building is built first on its own spot; when it is done a `DEMOLISH` project for the old one is queued automatically (its blocks go back to the storage, top layer first) and, once taken down, both records vanish.
- **Site search** (`SitePicker`): every plot, all four turns, every position (every second position above 5000 candidates). Checks: inside the plot, `building_gap` blocks from other builds (planned and finished ones count), clear of the hall (2 blocks), storages, beds, worksites, markers and gathering points, no block entity in the box, no water. Ground: only the columns whose bottom cell is not "leave as is" count; the standing level is chosen so that no column needs more than `terrain_max_fill` (2) blocks of fill or `terrain_max_cut` (3) of cutting; fewer changes, a spot near the hall and a front that looks at the hall score better. Fill steps are stored in the project (dirt under the old top block, the top block itself on top; free unless `terrain_fill_costs_material`). Cutting needs no step of its own: cells of the design that are air become CLEAR steps.
- **Materials:** the design's blocks, one item each (`BuildMaterials`; door upper halves, bed heads and tall plant tops are free, double slabs cost 2, snow layers and candles by count, farmland is dirt, crops are their seed). Planks of any wood count as one material (`planks_any_wood`). The first `active_builds` approved builds of the queue post their remaining materials as settlement needs (`SettlementApi.postNeed`, id `build.<item>`, priority 45, lasts 1500 ticks, renewed when the amount grows or every 600 ticks, cleared when no longer wanted), so sawmills and couriers feed them.
- **Hall "Building" tab** (`client/screen/BuildingTabView`, `BuildingMenuSupport`, `TownHallMenu` data channel keys `buildOpen` and `buildAct`; the window is now 380 wide for the eighth tab): auto planning on or off, plans start at once or need approval, preferred style (any, timber, stone, desert, norse); the planner's last problem; the plots with Remove; the queue with state (planned, waiting for materials with the missing items, ready, under construction with progress %, done), reason, plot, position and front, and the buttons Approve, Approve all, Show (ghost), Up, Down, Cancel; a form to queue a build by hand: category filter, design, plot, front, position inside the plot (east and south offsets, right click moves by 5) and Queue build (a manual build starts queued). The ghost (`DesignPreview`) shows the form's design while the tab is open, or the project whose Show was pressed. The server sends the state only while the tab is open and re-checks everything (`SitePicker.check` for a manual build, the same as the planner).

## Build job API (for the builder NPC): `BuildJobs`
- `jobs(settlement)`: approved, unfinished projects in queue order (builds and demolitions). `all(settlement)` lists every project. `state(level, settlement, project)` gives `JobState` `PLANNED`, `WAITING_MATERIALS`, `READY`, `IN_PROGRESS`, `DONE`. `next(level, settlement)` gives the job to work on: one in progress, else the first ready one (a job short of materials is never given out).
- `nextStep(level, settlement, project)` gives the next `BuildStep` (`index`, `kind` FILL, PLACE, CLEAR or REMOVE, `pos`, `state`); steps already right in the world are skipped and marked. `perform(level, settlement, project, step)` does it (takes the material from the storage or gives it back, sets the block with flags 18 so nothing pops off, sets the owner of owned block entities, plays the sound, marks it done) and answers `PlaceResult` `PLACED`, `REMOVED`, `SKIPPED`, `NO_MATERIAL`, `UNLOADED`, `BLOCKED`, `INVALID`. A builder that wants to do the work itself uses `BuildMaterials.extract/insert/reqFor` and `markDone`.
- `remainingMaterials(project)`, `missing(level, settlement, project)`, `BuildMaterials.stock/count/missing`.
- When the last step is done the job finishes itself: its beds join the settlement (`addBed`), its chests, barrels and shulker boxes are linked as storage, worksites need nothing (the job board scans), and a replaced building is queued for demolition. `maintain` also finishes jobs whose steps are all done.
- Doors and beds are two steps each (lower then upper, foot then head); the builder places them in the order given. `Skill.BUILDING` XP is the builder's to give.

## Files
- New (`settlement/building/`): `BuildingPlot`, `BuildProject`, `BuildStep`, `SettlementBuilding`, `BuildMaterials`, `BuildJobs`, `SitePicker`, `BuildingPlanner`, `BuildingZoneItem`, `BuildingNetwork`, `ClientPlotView`, `BuildingEvents`, `BuildingMenuSupport`; `settlement/building/client/PlotRenderer`; `client/screen/BuildingTabView`
- New resources: item model `building_zone_tool.json`, recipe `building_zone_tool.json`
- Edited: `settlement/Settlement` (`getBuilding`, `markDirty`, save and load), `settlement/SettlementData` (calls the planner), `settlement/SettlementNetwork` (payload), `settlement/SettlementConfig`, `init/AwNpcSettlements` (item), `menu/TownHallMenu`, `client/screen/GuiTownHallInventory` (tab), `lang/en_us.json`

## Config keys (`ancientwarfarenpc-settlements.toml`, section `building_planner`)
`plot_min_side` (5), `plot_max_side` (48), `plots_max_per_settlement` (24), `planner_enabled` (true), `planner_interval_ticks` (600), `auto_plan_default` (true), `auto_approve_default` (false), `max_pending_builds` (3), `active_builds` (2), `level_population_step` (6), `respect_design_minimums` (true), `tent_below_population` (6), `afford_fraction` (0.5), `upgrade_afford_fraction` (1.0), `storage_full_fraction` (0.8), `chapel_population` (12), `terrain_max_fill` (2), `terrain_max_cut` (3), `terrain_fill_costs_material` (false), `building_gap` (1), `planks_any_wood` (true).

## Deviations
- There was no settlement level, so `min_level` is read as `members / level_population_step` (6 members per level).
- Planks of any wood pay for planks of one wood in a design (the block is still placed as written).
- The planner only uses the founder's blueprints (a faction co-owner's own blueprints can be queued by hand on the tab; the tab lists the viewer's designs). Blueprints cannot be deleted from the tab (`BlueprintStore.delete` still has no UI).
- The chapel is planned only when there is no priest member and no chapel build; a priest without a chapel does not trigger it. Chapels the player builds himself are not recognised.
- A business is planned for a need only for hints that map to a design tag (smithy, bakery, sawmill, tannery, smelter); roles without a design (farmer, lumberjack, miner, researcher) plan nothing. There is no design with a crop farm, so the farmhouse is never chosen for the farmer role.
- Demolition has no screen button; it only happens as part of an upgrade (the API can queue one: construct a `DEMOLISH` `BuildProject`).
- Cancelling a build under construction is not possible (it would leave a half built shell); a plot with a build under construction cannot be removed.
- Step progress is a bit set; if a datapack reload changes the number of steps of a design the progress of that build is dropped, and steps already right in the world are skipped by `nextStep`. A project whose design is gone is cancelled with a log warning.
- The planner does not build on ground it cannot see (unloaded chunks are refused with the reason "ground is not loaded"); the planning run happens again 30 seconds later.
- Trees above the footprint, leaves and plants are not cleared beyond the cells of the design (CLEAR steps).
- Not run on a server or in a world: the planner, the site search, the tab and the renderer were checked by compiling and reading only.

# Rework phase 9, part 4: builder NPCs

Not play-tested, never run in a world. `./gradlew build` passes. The builders walk the build queue of part 3 and place the designs block by block. Everything the builder does to the world goes through `BuildJobs`.

## What changed
- **The role.** New worker role `builder` (`NpcWorker.ROLES`, work type `WorkType.BUILDING` added to `IWorkSite.WorkType`, a spawner subtype with an iron hammer, skins like the craftsman, names, lang, the Role button of the hall). Its tool is a hammer (`HammerItem`) or any pickaxe, axe or shovel (`isToolForRole`); a builder without one fetches a tool from the storage like the other roles (`SettlementNeeds.toolKeyOfRole`: hammer). Skill: `Skill.BUILDING` (the title of the builder), XP `skill_xp_build` per block placed or taken down.
- **Job board.** `JobBoard` now also holds the build claims: `claimBuild` gives a builder the first approved build of the queue that is not blocked, whose plot exists and that has fewer than `builders_per_job` builders; `claimStep`, `releaseStep` and `isStepClaimedByOther` claim single steps (300 ticks, renewed while working), so two builders never place the same block. A build is not a worksite, so builders do not use `autoWorkTarget`: they use `NpcWorker.buildJobId`. The hall's job lines count the builder slots (`openSlots` and `filledSlots` for `BUILDING`). `NpcAIPlayerOwnedFindWorksite` claims the build for a builder every 40 ticks.
- **The goal** (`ai/owned/NpcAIBuilderWork`, priority 6 like the staffed work) and **the work** (`ai/owned/BuilderWork`, a state machine per tick):
  1. *Pick.* The open steps are looked at in order. A cell to clear that still holds a block comes first (over the whole build), then the fill steps and the design bottom layer up. A step whose material is not in the pouch, whose block cannot stand yet (`BlockState.canSurvive`, so a wall torch or a door waits for its wall) or that another builder claimed is left for later: the builder places the first block it can.
  2. *Materials.* Materials come only from the storage (owner decision). The builder walks to the nearest storage (the hall or a linked container) that holds what the next blocks need and takes it into its pouch (up to the free slots, 512 items); it then places from the pouch (`BuildJobs.perform(..., Carrier)`, a new overload: the pouch is the source, and what a broken block drops goes into the pouch). When the pouch is nearly full of things the build does not need, and when the builder leaves the job, it takes those to a storage. A courier bringing materials to a site chest was not needed: the planner's posted needs already make production and couriers fill the storage, and the builder is the last leg.
  3. *Missing material.* That block waits and the builder goes on with other blocks. With nothing at all to place it waits: its thoughts and census line say what is missing (`work.build_wait`), and the Building tab still shows the missing items of the job. After `builder_wait_release_ticks` it gives the job up, nobody gets that build for 1200 ticks and the builder claims another one.
  4. *Clearing.* The builder breaks the block with the tool that fits (the tool switching: `WorkTool.forTask`, belt, pouch, else it asks the fetch-tool goal), with the crack overlay, particles and the hit sound, at a speed from the hardness (`builder_break_ticks_per_hardness`); drops go to the pouch and later the storage. Only terrain and plants may be broken, only inside the plot, never a block with a block entity (chests, worksites, signs), the hall, markers, linked storages, beds, gathering points, worksites, or anything inside another build of the settlement (`BuildSafety`). A step that breaks these rules is skipped for good (the building differs a little there) and the builder says so (`build_skip`). Water is left alone.
  5. *Levelling.* The fill steps (earth under the building, free unless `terrain_fill_costs_material`) and the cut cells (the design's air cells, cleared first) were planned by `SitePicker` within `terrain_max_fill` and `terrain_max_cut`; the builder just does them.
  6. *Placing.* Within 4.5 blocks of the eyes, with a swing every `swing_interval_ticks` (block particles and the place sound), the blocks, door halves, beds and so on in the order of the steps with the rotated block states of the design. A builder that stands in the cell steps aside first. Time per block is `builder_place_ticks` at speed 1, scaled by the skill rank, the traits (lazy, hardworking, night owl; quick learner for the XP), morale and hunger (`advanceWorkTick`) and the tool tier (`getBaseWorkEffectiveness`).
  7. *High places.* No flying. The builder walks (vanilla navigation, then the routes over climbable blocks of `NpcRouteAssist`). When that finds no way it puts up a column of vanilla scaffolding beside the building (one or two blocks off the footprint, in the land of the settlement, on solid ground, nearest to the target, an existing column counts as nearer) up to the cell below the layer it works on, climbs it and walks onto the structure from there; `NpcRouteAssist.forgetFailure()` was added so the walk plans again once the column stands. The scaffolding is taken down from the top when the builder has nothing to place for a while, when it needs a column elsewhere, and when it leaves the job. The blocks are listed in `NpcWorker.scaffolds` (saved), so a builder that was unloaded still takes them down. Config: `builder_scaffolding`, `builder_scaffold_costs_material`, `builder_max_scaffold_height`.
  8. *Taking down.* `DEMOLISH` projects (tent upgrades) use the same loop: the builder removes the blocks top layer first (with the breaking time and the sound); the block items and the contents of containers go into the pouch and then the storage; chests, barrels and beds are also unlinked from the settlement (`BuildJobs.perform`, REMOVE).
- **When a job finishes** (`BuildJobs.finish`, already there): beds join the settlement (members get a bed through the normal bed assignment), chests, barrels and shulker boxes become linked storage, and the placed worksite block belongs to the settlement owner (`setOwner` at placement), so the job board takes it up at its next refresh. New: a chapel (design category `chapel` or tag `chapel` or `devout`) registers with `TraitHooks.registerDevoutSite` (`ChapelSites`, registered in `AwNpcSettlements.registerEvents`; it reads the finished builds of the settlement, so it also works after a restart). Every builder on the job thinks "We finished building the (name)" (`built`).
- **Safety.** Unloaded chunks: steps in unloaded chunks are not picked and `BuildJobs.perform` answers `UNLOADED`; the builder pauses. A removed plot: `SettlementBuilding.removePlot` now cancels the builds on it, including one under construction (the old rule refused to remove such a plot), and `BuildJobs.maintain` also cancels open builds whose plot is gone; placed blocks stay and the builders let go of the job on their own.
- **Text.** Census (`guistrings.census.task.work.build_*`), talk screen (`npc.thought.now.work.build_*`, `job.building`, `nojob.no_build`, `nojob.build_busy`) and events (`got_build`, `built`, `build_wait`, `build_wait_over`, `build_skip`), all with three variants in `en_us.json`. Jade shows the census task of the builder through the existing NPC tooltip.

## Files
- New: `ai/owned/NpcAIBuilderWork`, `ai/owned/BuilderWork`, `settlement/building/BuildSafety`, `settlement/building/ChapelSites`.
- Edited: `ancientwarfare` `IWorkSite` (work type `BUILDING`), `entity/NpcWorker`, `entity/NpcThinking`, `ai/owned/NpcAIPlayerOwnedFindWorksite`, `ai/path/NpcRouteAssist` (`forgetFailure`), `settlement/JobBoard`, `settlement/SettlementConfig`, `settlement/SettlementNeeds`, `settlement/building/BuildJobs` (`Carrier`, the pouch overload of `perform`, `isSatisfied`, unlinking on removal, orphan check), `settlement/building/BuildProject` (`firstOpenStep` public), `settlement/building/SettlementBuilding` (`removePlot`), `skill/Skill`, `init/AwNpcs`, `init/AwNpcSettlements`, `lang/en_us.json`, `registry/skin_pack.json`.
- No new items or blocks, so no models, recipes, loot tables or tags (the builder holds an existing hammer, scaffolding is the vanilla block).

## Config keys (`ancientwarfarenpc-settlements.toml`, section `builders`)
`builders_per_job` (3), `builder_place_ticks` (12), `builder_break_ticks_per_hardness` (30), `builder_scaffolding` (true), `builder_scaffold_costs_material` (false), `builder_max_scaffold_height` (24), `builder_wait_release_ticks` (600), `skill_xp_build` (3).

## Deviations
- Materials are carried by the builder (no site chest, no courier leg). Placing needs only a distance of 4.5 blocks to the cell, not a line of sight, so a builder can place a block into a cell behind a wall it has just built.
- Scaffolding: by default the builder brings its own (free, always taken down again); with `builder_scaffold_costs_material` the blocks come from the storage directly (not carried) and go back to it. If the builder dies or the scaffolding cannot be reached it stays (it is a normal vanilla block). A roof that is out of reach even from a column is left for later (the step pauses for 200 ticks) until the layer below is complete enough to walk on.
- Steps are not done strictly bottom-up: a missing material or an unsupported block lets the builder place other blocks first, as asked. Cells that must be cleared are cleared before anything else is placed, but a second builder may place blocks while the first still clears.
- Only blocks the builder may break are broken (terrain, plants, trees with leaves nearby; a log without leaves counts as a building). Anything else in the way is skipped, so a building on such a spot has a hole.
- The hall's job list got a line for the builder (8 roles now); the room of that tab was not checked, so the last lines may sit lower than before.
- The planner does not know about builders: if there is no builder, queued builds wait. The owner sets the role of a worker to Builder on the hall screen (or spawns a builder). There is no planner note for a missing builder.
- Taking down a building unlinks its beds, so their owners are homeless until the new house is done.
- Not run on a server or in a world: the pick, the walk, the scaffolding, the fetch and the deposit were checked by compiling and reading only.

## Final check

Reviewed the whole phase 9 diff (designs, planner, builders) for item loss or duplication, griefing, rotation, client classes on the server, per-tick cost, old saves and packet size. `./gradlew build` passes; `:ancientwarfarereforged:runServer` reached `Done (` in 3.4 s with "Loaded 23 settlement designs" (22 built-in plus the example tent), no skipped design, no warning from the mods and no `eula.txt` created. The server was stopped with `./gradlew --stop`.

### Fixed
- **Medium Norse house had a lone bed head.** The carpet of the table corner overwrote a bed foot, so the build would have placed a head with no foot (the bed beside it would have been removed by the game). The generator now keeps the carpet out of the bed row (`gen_settlement_designs.py`); only `house_norse_medium.json` changed. Every bed, door and wall torch of all designs was checked by script (bed foot and head pair up in the facing, door halves match, torches have a support).
- **Free blocks in blueprints.** The scanner now saves blocks that have no item (fire, portals, potted plants and the like) as "leave as is", because a builder would place them for free. Fluids were already left out.
- **Refund lost on take-down.** A door, bed or tall plant has its item on the lower half or foot; a take-down removes top blocks first, so the free half came first and the game removed the paying half with it, and the step of the paying half then found only air and gave nothing back. The free half now returns the item of the pair (once: the other step finds air and is skipped).
- **Chest taken down refilled itself.** The contents of a container being taken down were put into the settlement storage, which could be that same chest, and then dropped as items. The chest and bed are now unlinked from the settlement before the contents are moved.
- **Break rules re-checked at the moment of work.** The planner rules (inside the plot, only terrain and plants, never a block entity, hall, marker, storage, bed, worksite or another build) were checked when a step was picked but not again when the builder broke the block seconds later. They are checked again in `BuilderWork.work`, and a step that now breaks a rule is skipped.
- **Design data packet.** A design that would not fit the 1 MiB packet limit as JSON is no longer sent for the ghost preview (the config caps keep scanned blueprints far below that).

### Checked and found fine
- **Materials:** a block is placed only after the material was taken from the pouch (which is filled from storage); a take-down returns exactly the item cost of the blocks it removes (double slab 2, layers and candles by count, second halves nothing); free blocks cost nothing on both sides. Chest contents are never scanned (block states only).
- **Grief:** plots must lie inside the boundary and be made by someone who may manage the settlement; the site search keeps clear of other builds, the hall, beds, storages and worksites and refuses a box with a block entity in it; the Building tab actions need `mayManage` (menu `stillValid`).
- **Rotation:** the turn is clockwise from the front to the facing; positions and block states turn together (`BlockState.rotate`), so stairs, doors, beds and wall torches keep their meaning.
- **Server:** the client classes are only reached from payload handlers and client event subscribers; the dedicated server started cleanly. Old saves load: the `building` tag of a settlement and the `scaffolds` list of a worker are optional.
- **Cost:** the planner runs once per interval per settlement and the builder looks at the steps every 40 ticks (at most 4000 steps).

### Known limits left as they are
- A design cell that already holds the same block (stone on a stone hillside) is skipped without cost, and a later take-down returns the item for it. Rare and small; fixing it would need a per-step record of what the builder placed.
- A scanned blueprint also copies blocks of other players' builds when the scanner player can reach them (as noted in the deviations above).
- Not play-tested in a world: planner, Building tab, scanner screen, the builder walk, scaffolding, fetch and deposit, and take-down.

## Take-down refund fix (after the final check)

- `BuildProject` now saves a `paid` bit set: a step is marked paid only when its block was placed with materials taken from storage. A cell that already held the wanted block (skipped for free) is not paid.
- Taking a building down (`REMOVE` steps of a demolition) refunds the design item only for paid cells (the free half of a door or bed counts with its paid half). Unpaid cells break like mined terrain and give their normal drops. This closes the free-item gap noted in the final check.
- Builds finished before this change have no paid bits, so their take-down gives normal drops instead of design items. No released world has such builds.
