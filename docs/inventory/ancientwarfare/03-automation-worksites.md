# Automation: module entry, worksites, chunk loaders, registries, config
> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

Packages covered (all under `automation/`): the four files directly in `automation/` (`AncientWarfareAutomation`, `AWAutomationTab`, `AutomationInputHandler`, `package-info`), `tile/worksite/` (with `cropfarm/`, `fruitfarm/`, `treefarm/`), the three files directly in `tile/` (`TileMailbox`, `TileChunkLoaderSimple`, `TileChunkLoaderDeluxe`), `item/`, `registry/`, `gamedata/`, `chunkloader/`, `command/`, `config/`, `datafixes/`, `compat/` and `compat/agricraft/`, `init/`, `proxy/`.

Counted with a script: **80 files, 7,164 lines** (includes about 20 one-line `package-info.java` files). Largest files: `WorkSiteAnimalFarm` 525, `TileWorksiteBase` 402, `MailboxData` 400, `WorkSiteTreeFarm` 316, `TileWorksiteFarm` 298, `WorkSiteQuarry` 285.

Resource files analysed: `src/main/resources/assets/ancientwarfare/registry/` (38 JSON files feeding the crop, fruit and tree registries (3 `crop_farm`, 3 `tree_farm`, 32 compat)) plus the 49 research-recipe JSONs under `research_recipes/automation/` and 9 component recipes under `recipes/automation/` for context.

What this part is for: it is the entry point of the Automation module and the "worksite" half of it. A worksite is a block with a block entity that works on a rectangular area (quarry, tree farm, crop farm, fruit farm, animal farm, fish farm) or on an inventory (auto-crafting table). Worksites are not driven by NPC animation. They run their own work loop in the tile tick and pay for each action with "torque" energy. NPC workers (module `npc`) only stand next to the block and add energy to it; players can do the same with a hand crank. Torque cables, generators and flywheels, the warehouse network, and GUIs/containers/renderers are in other slices. This slice also owns the registries that tell farms which blocks are crops, soils, fruits, saplings and trees (hard-coded defaults plus JSON data, including 32 third-party-mod compat files), the two chunk loader blocks, the cross-world mailbox system, the module config, and the module's registration events.

## What it adds

### Module entry and registration timeline

`AncientWarfareAutomation` (`automation/AncientWarfareAutomation.java:53`) is a `@Mod` with `modid = ancientwarfareautomation`, `dependencies = "required-after:ancientwarfare;after:redstoneflux;after:buildcraftcore"`. Sided proxy: client `automation.proxy.ClientProxyAutomation`, server `core.proxy.CommonProxy`.

| Phase | What happens | Evidence |
|---|---|---|
| Class load | `TAB = new AWAutomationTab()` creative tab, `LOG` created | line 63-65 |
| `RegistryEvent.Register<Block>` | `AWAutomationBlocks.register` creates 26 blocks and registers 36 tile entity classes with `GameRegistry.registerTileEntity` | `init/AWAutomationBlocks.java:152-246` |
| `RegistryEvent.Register<Item>` | `registerItemBlocks` creates 26 ItemBlocks; `AWAutomationItems.register` creates the one standalone item | `init/AWAutomationBlocks.java:118-149`, `init/AWAutomationItems.java:23` |
| `preInit` | `RFProxy.loadInstance()`; `new AWAutomationStatics("AncientWarfareAutomation")` (loads config); `proxy.preInit()`; 21 `NetworkHandler.registerContainer` calls; registers `this` and a `WarehouseDebugger` on the Forge bus; `ForgeChunkManager.setForcedChunkLoadingCallback(this, AWChunkLoader.INSTANCE)`; 7 `RegistryLoader.registerParser` calls; `CompatLoader.registerCompat(new AgricraftCompat())` | lines 70-113 |
| `init` | `proxy.init()` (client: registers the F8 key binding), `statics.save()` | lines 116-120 |
| `serverStart` | registers `/awwarehouse` | lines 123-125 |
| Later (core) | `RegistryLoader.load()` and `CompatLoader.init()` run from the Core mod, which is when the JSON registries are actually read and `AgricraftCompat.init()` runs | `core/AncientWarfareCore.java:93` (`RegistryLoader.load()`) and `core/AncientWarfareCore.java:97` (`CompatLoader.init()`) |

### BuildCraft: is it actually used?

No. A search of all Java source, `build.gradle` and `gradle.properties` finds "buildcraft" only in the `dependencies` string above. There is no BuildCraft import, no `@Optional` annotation and no Gradle dependency for it. The only leftover is the unused constant pair `mjToTorque` / `torqueToMj` in `config/AWAutomationStatics.java:79,82` (no references anywhere). `after:buildcraftcore` only affects load order. RedstoneFlux, by contrast, is really used: `@Optional.Interface` on `TileWorksiteBase` (`IEnergyProvider`, `IEnergyReceiver`), `TileTorqueBase`, `TileTorqueSidedCell`, and `proxy/RFProxy*` (see How it works). Stats confirm: `cofh/redstoneflux/api` is referenced by 3 automation classes (`STATS` third-party table).

### Blocks (master list for the Automation module: 26 blocks, each with one ItemBlock)

All registered in `init/AWAutomationBlocks.java` with the namespace `ancientwarfareautomation`. Stats category "block" for automation = 26, matching. Classes marked * are implemented in other slices' packages (`block/` is not part of this slice) but listed here because this slice owns the master list.

| Registry name | Block class / ItemBlock | What it is | Variants / state | Porting note |
|---|---|---|---|---|
| `quarry` | `BlockWorksiteBase` / `ItemBlockWorksiteStatic` | Quarry worksite | blockstate `facing` (4-way) + `active` bool, both derived from the tile in `getActualState`; meta always 0 (`BlockWorksiteBase.java`) | One block, two properties; facing and active must become real blockstate properties or be read from the block entity. |
| `tree_farm` | `BlockWorksiteBase` / `ItemBlockWorksiteStatic` | Tree farm | same | Tile id is `forestry_tile` (historic). |
| `crop_farm` | same | Crop farm | same | |
| `fruit_farm` | same | Fruit farm | same | |
| `animal_farm` | same | Animal farm | same | |
| `fish_farm` | same | Fish farm | same | |
| `warehouse_control` | `BlockWorksiteBase` / `ItemBlockWorksiteStatic` | Warehouse controller (tile `TileWarehouse`, other slice) | same | Uses the same block class as worksites, so it is placed with a 5x5 default bounds area. |
| `warehouse_storage`* | `BlockWarehouseStorage` / `ItemBlockWarehouseStorage` | Warehouse storage | property `size` (3 values: small, medium, large via meta 0-2); 3 tile classes | 3 metadata variants: split into 3 blocks (or one block with a property). |
| `warehouse_interface`* | `BlockWarehouseInterface` / plain `ItemBlock` | Warehouse interface | none | |
| `warehouse_crafting`* | `BlockWarehouseCraftingStation` / plain `ItemBlock` | Warehouse crafting station | none | |
| `warehouse_stock_viewer`* | `BlockWarehouseStockViewer` / `ItemBlockOwnedRotatable` | Stock viewer | facing | |
| `warehouse_stock_linker`* | `BlockWarehouseStockLinker` / `ItemBlockWarehouseStockLinker` | Stock linker | facing + `lit` bool; warehouse position kept in item NBT `warehousePosTag` | State kept in item NBT: needs a data component. |
| `auto_crafting`* | `BlockAutoCrafting` / `ItemBlockRotatableMetaTile` | Auto-crafting table | unlisted horizontal facing from tile; non-opaque | Extended (unlisted) state: needs block entity renderer data or a real property. |
| `mailbox` | `BlockMailbox` / `ItemBlockOwnedRotatable` | Mailbox | facing stored in meta (`FACING` ordinal) | |
| `flywheel_controller`* | `BlockFlywheelController` / `ItemBlockTorqueTile` | Flywheel controller | property `TIER` (light, medium, heavy via meta 0-2) | 3 variants: split into 3 blocks. |
| `flywheel_storage`* | `BlockFlywheelStorage` / `ItemBlockMeta` | Flywheel storage | `TIER` 0-2 | 3 variants. |
| `torque_junction`* | `BlockTorqueJunction` / `ItemBlockTorqueTile` | Torque conduit/junction | `TIER` 0-2, unlisted connection props | 3 variants. |
| `torque_shaft`* | `BlockTorqueTransportShaft` / `ItemBlockTorqueTile` | Torque shaft | `TIER` 0-2, 6-way rotation, unlisted has_previous/has_next | 3 variants. |
| `torque_distributor`* | `BlockTorqueDistributor` / `ItemBlockTorqueTile` | Torque distributor | `TIER` 0-2 | 3 variants. |
| `stirling_generator`* | `BlockStirlingGenerator` / `ItemBlockTorqueTile` | Stirling generator | facing | |
| `waterwheel_generator`* | `BlockWaterwheelGenerator` / `ItemBlockTorqueTile` | Waterwheel generator | facing + `valid_setup` | |
| `hand_cranked_generator`* | `BlockHandCrankedGenerator` / `ItemBlockTorqueTile` | Hand crank | facing | |
| `windmill_blade`* | `BlockWindmillBlade` / plain `ItemBlock` | Windmill blade | unlisted `formed` | |
| `windmill_generator`* | `BlockWindmillGenerator` / `ItemBlockTorqueTile` | Windmill generator | facing | |
| `chunk_loader_simple` | `BlockChunkLoaderSimple` / plain `ItemBlock` | 3x3 chunk loader | none | See Chunk loaders. |
| `chunk_loader_deluxe` | `BlockChunkLoaderDeluxe` / plain `ItemBlock` | Configurable chunk loader | none | |

The object-holder field names in `AWAutomationBlocks` (`QUARRY`, `TREE_FARM`, ... `WINDMILL_GENERATOR`) are 1:1 with the table. `BlockOreProcessor` (in `block/`) and `TileOreProcessor` exist but are never registered: dead code, a half-written ore-processing worksite with a "TODO implement GUI" and `getNextAction()` returning `null` (`tile/worksite/TileOreProcessor.java:20-60`). Do not port it.

Common properties: every block uses `BlockBaseAutomation`, which sets the creative tab and a custom state mapper to `ancientwarfare:automation/<regname>` blockstate JSONs. Worksites have `Material.WOOD`, hardness 2, flammability 20, fire spread 5. Right click requires being the owner, same team or a friend (`Owner.isOwnerOrSameTeamOrFriend`, `BlockWorksiteBase.onBlockActivated`).

### Items

| Registry name | Class | What it is | Porting note |
|---|---|---|---|
| `worksite_upgrade` | `item/ItemWorksiteUpgrade` (extends core `ItemMulti`) | One item with 11 metadata variants; right click on a worksite applies the upgrade | 11 metadata variants: split into 11 items. Model names `automation/worksite_upgrade#variant=...` |

The 11 variants, by enum ordinal in `core/upgrade/WorksiteUpgrade.java` (order is persisted, "DO NOT EVER CHANGE ENUM ORDERING"):

| Meta | Enum | Variant name | Valid on | Efficiency bonus | Replaces / blocked by |
|---|---|---|---|---|---|
| 0 | `SIZE_MEDIUM` | `bounds_medium` | bounded non-quarry worksites | none | - |
| 1 | `SIZE_LARGE` | `bounds_large` | same | none | overrides and is exclusive to 0 (`{0}`/`{0}`) |
| 2 | `QUARRY_MEDIUM` | `quarry_medium` | quarry | none | - |
| 3 | `QUARRY_LARGE` | `quarry_large` | quarry | none | overrides and excludes 2 |
| 4 | `ENCHANTED_TOOLS_1` | `enchanted_tools_1` | all | +0.05, fortune 1 | - |
| 5 | `ENCHANTED_TOOLS_2` | `enchanted_tools_2` | all | +0.10, fortune 2 | overrides and excludes 4 |
| 6 | `TOOL_QUALITY_1` | `quality_tools_1` | all | +0.05 | - |
| 7 | `TOOL_QUALITY_2` | `quality_tools_2` | all | +0.15 | overrides and excludes 6 |
| 8 | `TOOL_QUALITY_3` | `quality_tools_3` | all | +0.25 | overrides and excludes 6 and 7 |
| 9 | `BASIC_CHUNK_LOADER` | `chunkloader_basic` | bounded worksites | none | - |
| 10 | `QUARRY_CHUNK_LOADER` | `chunkloader_quarry` | quarry | none | - |

ItemBlock helpers in this slice (not separate registry entries; they are the ItemBlocks in the blocks table): `ItemBlockWorksiteStatic` sets default bounds, owner and facing when placed; `ItemBlockTorqueTile` sets owner and facing, and appends meta to the unlocalised name; `ItemBlockWarehouseStorage` (meta as sub-type); `ItemBlockWarehouseStockLinker` (shift-right-click a warehouse controller to store its position in NBT, 5 block range via `RayTraceUtils.getPlayerTarget`).

Stats reconciliation: automation `item` category = 5 classes, matching the 5 classes in `item/`. Registered items: 1 standalone item + 26 ItemBlocks = 27 item registry entries.

### Tile entities (36 registered ids, `GameRegistry.registerTileEntity(class, ancientwarfareautomation:<id>)`)

Stats show 49 tile-entity classes in automation; the difference (13) is abstract bases and intermediate classes (`TileWorksiteBase`, `TileWorksiteBounded`, `TileWorksiteBoundedInventory`, `TileWorksiteFarm`, `TileTorqueBase`, `TileTorqueSidedCell` and similar) plus the unregistered `TileOreProcessor`.

| Tile id | Class | Slice | What it is |
|---|---|---|---|
| `quarry_tile` | `tile/worksite/WorkSiteQuarry` | this | Quarry |
| `forestry_tile` | `tile/worksite/treefarm/WorkSiteTreeFarm` | this | Tree farm |
| `crop_farm_tile` | `tile/worksite/cropfarm/WorkSiteCropFarm` | this | Crop farm |
| `fruit_farm_tile` | `tile/worksite/fruitfarm/WorkSiteFruitFarm` | this | Fruit farm |
| `animal_farm_tile` | `tile/worksite/WorkSiteAnimalFarm` | this | Animal farm |
| `fish_farm_tile` | `tile/worksite/WorkSiteFishFarm` | this | Fish farm |
| `auto_crafting_tile` | `tile/worksite/TileAutoCrafting` | this | Auto-crafting table |
| `mailbox_tile` | `tile/TileMailbox` | this | Mailbox |
| `chunk_loader_simple_tile` | `tile/TileChunkLoaderSimple` | this | Simple chunk loader |
| `chunk_loader_deluxe_tile` | `tile/TileChunkLoaderDeluxe` | this | Deluxe chunk loader |
| `warehouse_control_tile` | `TileWarehouse` | warehouse | Warehouse controller |
| `warehouse_storage_small_tile` | `TileWarehouseStorage` | warehouse | Small storage |
| `warehouse_storage_medium_tile` | `TileWarehouseStorageMedium` | warehouse | Medium storage |
| `warehouse_storage_large_tile` | `TileWarehouseStorageLarge` | warehouse | Large storage |
| `warehouse_interface_tile` | `TileWarehouseInterface` | warehouse | Interface |
| `warehouse_crafting_tile` | `TileWarehouseCraftingStation` | warehouse | Crafting station |
| `warehouse_stock_viewer_tile` | `TileWarehouseStockViewer` | warehouse | Stock viewer |
| `warehouse_stock_linker_tile` | `TileWarehouseStockLinker` | warehouse | Stock linker |
| `flywheel_light_tile` / `flywheel_medium_tile` / `flywheel_large_tile` | `TileFlywheelControllerLight/Medium/Heavy` | torque | Flywheel controllers (note the heavy tier id is `flywheel_large_tile`) |
| `flywheel_storage_tile` | `TileFlywheelStorage` | torque | Flywheel storage |
| `torque_junction_{light,medium,heavy}_tile` | `TileConduitLight/Medium/Heavy` | torque | Junction (3) |
| `torque_shaft_{light,medium,heavy}_tile` | `TileTorqueShaftLight/Medium/Heavy` | torque | Shaft (3) |
| `torque_distributor_{light,medium,heavy}_tile` | `TileDistributorLight/Medium/Heavy` | torque | Distributor (3) |
| `stirling_generator_tile` | `TileStirlingGenerator` | torque | Generator |
| `waterwheel_generator_tile` | `TileWaterwheelGenerator` | torque | Generator |
| `hand_cranked_generator_tile` | `TileHandCrankedGenerator` | torque | Generator |
| `windmill_blade_tile` | `TileWindmillBlade` | torque | Windmill blade |
| `windmill_generator_tile` | `TileWindmillController` | torque | Windmill controller |

Count: 10 (this slice) + 8 warehouse + 18 torque = 36. A tiered block uses one tile class per tier (3 classes for 3 tiers), so the tile class is selected by block metadata.

### Commands

| Name | Class | What it does | Porting note |
|---|---|---|---|
| `/awwarehouse startdebug <x> <y> <z>` / `stopdebug [x] [y] [z]` | `command/CommandWarehouse` (`CommandBase`) | Starts/stops `WarehouseDebugger` tracking on a warehouse at a position. Coordinates are parsed with relative-coordinate support (y clamped 0..256, x/z +-30,000,000). No permission level override, so default OP level applies. | Brigadier via `RegisterCommandsEvent`. y limit 0..256 is wrong for 1.21 (-64..320). Debug aid only; low priority. |

### Config options (24)

`AWAutomationStatics extends core ModConfiguration`; file `config/ancientwarfare/AncientWarfareAutomation.cfg` (`ModConfiguration.java:84`). Categories: `01_shared_settings` (general), `02_server_settings`, `03_client_settings`. Class comment says "TODO config annotations".

| Key | Category | Default | Type | Meaning |
|---|---|---|---|---|
| `rf_energy_conversion_factor` | shared | 10.0 | double | RF per torque; <= 0 disables RF entirely (sets both factors to 0) |
| `energy_network_update_frequency` | shared | 5 | int | Min ticks between energy sync packets (used by torque slice) |
| `enable_server_energy_network` | server | true | bool | Toggle energy sync packets |
| `mailbox_travel_time_per_block` | server | 10 | int | Ticks per block of distance for mail; <= 0 is instant |
| `mailbox_travel_time_per_dimension` | server | 100 | int | Ticks for cross-dimension mail |
| `low_quality_tile_energy_drain` | server | 0.99990 | double | Torque loss factor, light tier |
| `med_quality_tile_energy_drain` | server | 0.99995 | double | medium |
| `high_quality_tile_energy_drain` | server | 0.99999 | double | heavy |
| `low_quality_tile_energy_transfer` | server | 32 | double | Max output per tick, light |
| `med_quality_tile_energy_transfer` | server | 192 | double | medium |
| `high_quality_tile_energy_transfer` | server | 1024 | double | heavy |
| `low_quality_conduit_energy_max` | server | 32 | double | Conduit buffer, light |
| `med_quality_conduit_energy_max` | server | 192 | double | medium |
| `high_quality_conduit_energy_max` | server | 1024 | double | heavy |
| `low_quality_storage_energy_max` | server | 9600 | double | Flywheel storage capacity, light |
| `med_quality_storage_energy_max` | server | 14400 | double | medium |
| `high_quality_storage_energy_max` | server | 24000 | double | heavy |
| `stirling_generator_output_factor` | server | 1.0 | double | Output multiplier |
| `waterwheel_generator_output_factor` | server | 1.0 | double | Output multiplier |
| `hand_cranked_generator_output_factor` | server | 1.0 | double | Output multiplier; also multiplies energy added by NPC workers and by players cranking a worksite (`TileWorksiteBase.java:258,263`) |
| `windmill_per_size_output_factor` | server | 1.0 | double | Windmill multiplier |
| `animal_farm_pickups` | server | 11 item ids (leather, beef, cooked_beef, chicken, cooked_chicken, feather, egg, wool, cooked_porkchop, porkchop, `harderwildlife:rawleather`) | string list | Items animal farms pick up from the ground; format `modid:itemid[:damage]` |
| `enable_client_energy_animations` | client | true | bool | Torque animation toggle |
| `render_work_bounds` | client | true | bool (as `Property`) | Show worksite bounds box; toggled in game by key F8 |

Unused fields in the class: `conduitRenderID`, `fishFarmRescanTicks`, `animalFarmRescanTicks` (hard-coded 200 in the tiles instead), `mjToTorque`, `torqueToMj`. `onConfigChanged` compares the event mod id with `AncientWarfareCore.MOD_ID`, not the automation id, so the automation file is saved only when core's config changes (`AncientWarfareAutomation.java:128-131`). Also relevant: core option `energy_per_work_unit` (default 50) sets how much torque a worker or player crank produces and how much each action costs (`core/config/AWCoreStatics.java:40,58`).

### Containers and GUIs

None defined in this slice (stats: automation `container` 22 and `gui_screen` 24 are in packages owned by other slices). The module entry registers 21 container ids with `NetworkHandler.registerContainer`, and `ClientProxyAutomation.preInit` registers 13 GUI classes client side (`proxy/ClientProxyAutomation.java:87-99`, including `GuiVehicleStats`). This is the list a planner needs to wire menus:

| GUI id constant (`NetworkHandler`) | Container registered | Opened from |
|---|---|---|
| `GUI_WORKSITE_QUARRY`, `GUI_WORKSITE_QUARRY_BOUNDS` | `ContainerWorksiteQuarry`, `ContainerWorksiteQuarryBounds` | `WorkSiteQuarry.onBlockClicked` |
| `GUI_WORKSITE_TREE_FARM`, `GUI_WORKSITE_CROP_FARM`, `GUI_WORKSITE_FRUIT_FARM` | `ContainerWorksiteTreeFarm/CropFarm/FruitFarm` | each farm `onBlockClicked` |
| `GUI_WORKSITE_ANIMAL_FARM`, `GUI_WORKSITE_ANIMAL_CONTROL` | `ContainerWorksiteAnimalFarm`, `ContainerWorksiteAnimalControl` | main / `openAltGui` |
| `GUI_WORKSITE_FISH_FARM`, `GUI_WORKSITE_FISH_CONTROL` | `ContainerWorksiteFishFarm`, `ContainerWorksiteFishControl` | main / `openAltGui` |
| `GUI_WORKSITE_AUTO_CRAFT` | `ContainerWorksiteAutoCrafting` | `BlockAutoCrafting.onBlockActivated` |
| `GUI_WORKSITE_INVENTORY_SIDE_ADJUST`, `GUI_WORKSITE_BOUNDS` | `ContainerWorksiteInventorySideSelection`, `ContainerWorksiteBoundsAdjust` | from worksite GUIs |
| `GUI_MAILBOX_INVENTORY` | `ContainerMailbox` | `BlockMailbox.onBlockActivated` |
| `GUI_CHUNK_LOADER_DELUXE` | `ContainerChunkLoaderDeluxe` | `TileChunkLoaderDeluxe.onBlockClicked` |
| `GUI_STIRLING_GENERATOR` | `ContainerStirlingGenerator` | torque slice |
| `GUI_WAREHOUSE_CONTROL/STORAGE/OUTPUT/CRAFTING/STOCK/STOCK_LINKER` | `ContainerWarehouse*` (6) | warehouse slice |

`GUI_VEHICLE_STATS` is registered client-only from the automation proxy (`GuiVehicleStats` from the vehicle module): a cross-module oddity.

### Network packets

None defined here. Worksite state reaches clients via `TileUpdatable.writeUpdateNBT/handleUpdateNBT` (core) and GUI containers.

### Key bindings

| Name | Class | Default | What it does |
|---|---|---|---|
| `keybind.awAutomation.toggle_workbounds_render` (category `keybind.category.awAutomation`) | `AutomationInputHandler` | F8, `KeyConflictContext.IN_GAME` | Flips `render_work_bounds` between "true" and "false" and saves the config. Uses core `InputHandler.registerCallBack`. |

### Recipes and other data

| Item | Where | Note |
|---|---|---|
| Research recipes | `research_recipes/automation/**` (49 JSON, custom type `ancientwarfare:research_recipe`, each names a `research`) | 7 worksites + 11 upgrades + 20 torque blocks + 11 other (warehouse, mailbox, chunk loaders). Needs a custom `RecipeType`/`RecipeSerializer` in 1.21 and the research system from core/npc. |
| Component recipes | `recipes/automation/*.json` (9: wooden/iron/steel x gear/shaft/bearings), type `forge:ore_shaped`, result `ancientwarfare:component` with metadata | Component item is core's; ore dictionary (`#INGOTIRON`) becomes tags. |
| Registry JSON | `registry/crop_farm/*.json` (3), `registry/tree_farm/*.json` (3) and `registry/compat/<mod>/*.json` (32; the compat folder also holds 3 `entity_spawn_nbt` files that belong to other slices) | Details under Registries. |
| Blockstates | `blockstates/automation/*.json` (17 incl. `worksite_upgrade.json`, `component.json`) | Forge blockstate "forge_marker 1" format, not vanilla; rewrite as vanilla blockstates/models. |
| Creative tab | `AWAutomationTab` (`tabs.automation`, icon `AWCoreItems.IRON_HAMMER`) | Items sorted by `SortItemsFirstComparator` in a fixed order (tree, crop, fruit, animal, fish, quarry, auto-craft, warehouse parts, mailbox, torque, flywheel storage, windmill blade, chunk loaders). Becomes `CreativeModeTab` with an explicit display-items list. |

### Saved data and capabilities

| Name | Class | Notes |
|---|---|---|
| `AWMailboxData` (map-storage id `"AW" + simple class name`) | `gamedata/MailboxData extends WorldSavedData` | Global (overworld map storage via `AWGameData.getData`, `core/gamedata/AWGameData.java`), not per dimension. |
| Item handler capability | worksite tiles, `TileMailbox`, `TileOreProcessor` | `CapabilityItemHandler.ITEM_HANDLER_CAPABILITY`, side-mapped. |
| RF energy interface | `TileWorksiteBase` | via `@Optional.Interface` (RedstoneFlux), not a Forge capability. |

Stats: automation `world_saved_data` = 2 (MailboxData in this slice, the other in the warehouse slice).

### Event handlers

| Handler | Event | What it does | Notes |
|---|---|---|---|
| `AWAutomationBlocks.register` / `registerItemBlocks` | `RegistryEvent.Register<Block>` / `<Item>` | Registration | `@Mod.EventBusSubscriber` |
| `AWAutomationItems.register` | `RegistryEvent.Register<Item>` | Registration | |
| `AncientWarfareAutomation.onConfigChanged` | `ConfigChangedEvent.OnConfigChangedEvent` | Save config | compares with the wrong mod id (see Config) |
| `MailboxData.serverTick` | `TickEvent.ServerTickEvent` (END phase) | Advances mail delivery timers by 1 | Registers itself on `MinecraftForge.EVENT_BUS` in its constructor |
| `ClientProxyAutomation.onPreTextureStitch` | `TextureStitchEvent.Pre` | Registers 28 model sprites for TESRs/baked models | Client; renderer slice |
| `WorkSiteAnimalFarm.onLivingDrops` (static) | `LivingDropsEvent` | Cancels drops of animals currently being culled | **Never registered on any event bus**: the method carries `@SubscribeEvent` (`WorkSiteAnimalFarm.java:438`), but the class is not `@EventBusSubscriber` and no `register(...)` call exists (searched all source). Probably dead code. See Open questions. |
| `WarehouseDebugger` | (warehouse slice) | registered here | |

Stats list 8 automation `@SubscribeEvent` methods (3 Register, ConfigChanged, ServerTick, TextureStitch.Pre, WorldTick, LivingDrops); the WorldTick one belongs to the warehouse debugger (other slice).

### Entities, sounds, AI tasks, renderers

None defined in this slice. `ClientProxyAutomation` binds 14 TESRs (`WorksiteRenderer` for `TileWorksiteBase` and `TileWarehouseBase`, torque and flywheel animation renderers, warehouse stock viewer/linker renderers); classes belong to the render-blocks slice. `WorksiteRenderer` draws the bounds box when `render_work_bounds` is on.

## How it works

### 1. The worksite work loop (`tile/worksite/TileWorksiteBase`)

Hierarchy: `TileWorksiteBase` (energy, owner, upgrades, facing, work loop) -> `TileWorksiteBounded` (work bounds, chunk-load ticket) -> `TileWorksiteBoundedInventory` (27-slot main inventory, side mapping) -> `TileWorksiteFarm` (plantable + misc inventories, target map, bonemeal) -> the crop, fruit and tree farms. Quarry, animal farm and fish farm extend `TileWorksiteBoundedInventory` directly. `TileAutoCrafting` extends `TileWorksiteBase` directly.

`update()` (final, server side only, `TileWorksiteBase.java:158-192`):
1. Return if client, or if `world.getStrongPower(pos) != 0` (a redstone signal switches the worksite off).
2. A 20-tick retry delay (`workRetryDelay`) gates work.
3. `getNextAction()` returns an `Optional<IWorksiteAction>` (priority list per subclass). An action only has `getEnergyConsumed(efficiencyBonusFactor)`.
4. If stored torque >= action cost, `processAction(action)` runs the real work on the server thread. On success the cost is subtracted from the `TorqueCell`; on failure the retry delay is set to 20.
5. Every 60 ticks `active` (= stored torque > 0) is re-checked and the block is re-sent to clients so the `active` blockstate property updates.
6. `updateWorksite()` (subclass hook) runs every tick for incremental scanning.

Energy model: `TorqueCell(32, 0, energyPerWorkUnit * 3, 1)` (`TileWorksiteBase.java:61`): max input 32 per tick, no output, capacity three work units (150 with the default 50). Sources of energy: adjacent torque network (any `ITorqueTile` neighbour pushes in; the worksite accepts input on all sides), RF through `receiveEnergy` converted by `rfToTorque`, NPC workers via `IWorkSite.addEnergyFromWorker(IWorker)` (adds `energyPerWorkUnit * worker.getWorkEffectiveness(workType) * hand_cranked_generator_output`), and players via `addEnergyFromPlayer`. NPC side (`npc/ai/owned/NpcAIPlayerOwnedWork.java:74-99`): the worker walks to the site, checks `site.hasWork()` (true while the cell is not full and no redstone power), then calls `addEnergyFromWorker`. So **NPCs never perform the world action; they only fill the energy buffer.** `WorkType` (`MINING`, `FARMING`, `FORESTRY`, `CRAFTING`, `RESEARCH`, `NONE`, from core `IWorkSite`) tells NPC types which sites they may crank; the string names are persisted in NPC XP data.

Costs: `IWorkSite.WorksiteImplementation.getEnergyPerActivation(eff)` is `energyPerWorkUnit * 1.f - eff` (`core/interfaces/IWorkSite.java`). Because of operator precedence the efficiency bonus is *subtracted* from 50 (so the largest total bonus 0.65 saves 0.65 energy, about 1.3 percent), not applied as a percentage. Upgrade "efficiency" is therefore almost cosmetic; keep or fix is an owner decision. Divisors: crop farm actions cost `/5` (plant, harvest, fertilize, till), tree chop trunk `/2`, chop leaf `/10`, all other actions full cost.

Upgrades: stored in `EnumSet<WorksiteUpgrade>`, serialised as an int array of ordinals (`upgrades`), with a fallback reader that accepts a string tag list (comment: "template parser reads int-arrays as a tag list", i.e. structure templates can carry worksites). `onBlockBroken` drops upgrade items. Fortune: `ENCHANTED_TOOLS_2` = 2, `_1` = 1, else 0 (`getFortune`). Applying an upgrade: `ItemWorksiteUpgrade.onItemRightClick` raytraces the block, checks `getValidUpgrades()`, rejects duplicates and `exclusive` conflicts, drops and removes overridden upgrades, then `addUpgrade`.

Sync: owner, upgrades, orientation, active flag go in `writeUpdateNBT` (core `TileUpdatable`); the work bounds go in the bounded layer; targets and queues are server-only.

Interfaces from core that worksites implement: `IWorkSite`, `IBoundedSite`, `IChunkLoaderTile`, `IInteractableTile`, `IOwnable`, `IRotatableTile`, `ITickable`, `IBlockBreakHandler`.

### 2. Bounds, area handling and side configuration

`TileWorksiteBounded`: `bbMin`/`bbMax` (block positions; `BlockPos.ORIGIN` means unset) are saved as longs (`bbMin`, `bbMax`) and synced. On placement `ItemBlockWorksiteStatic.placeBlockAt` calls `setBounds(pos1, pos2)` with a 5x5 area in front of the player: `pos1 = pos + facing + rotateYCCW*2`, `pos2 = pos + 4*facing + rotateY*4` (`item/ItemBlockWorksiteStatic.java:28-29`). Max width is 5, 9 (`SIZE_MEDIUM`) or 16 (`SIZE_LARGE`) for normal worksites; the quarry has 16/32/64 with `QUARRY_MEDIUM/LARGE`. Height is 1 layer for farms (the plane at the min Y), 4 for the fish farm, `pos.y - 1` blocks down for the quarry. Bounds are edited through GUI containers (`ContainerWorksiteBoundsAdjust`, `ContainerWorksiteQuarryBounds`), which call `setBounds` then `onBoundsAdjusted()`/`onPostBoundsAdjusted()`; farms revalidate their queues with `validateCollection` (drops positions outside bounds or not marked as a target).

Farm target map: `TileWorksiteFarm.targetMap` is a 16x16 byte array (1 = work this column), indexed `(z - minZ) * 16 + (x - minX)` (`TileWorksiteFarm.java:153-156`); this is why the maximum width is 16. Set to all-1 when bounds are first set. Saved as `targetMap`. Quarry, fish and animal farms return `userAdjustableBlocks() == false`.

Inventory layout (`TileWorksiteBoundedInventory`): `mainInventory` (27 slots) is the output/input buffer. Side mapping: each of the 4 horizontal sides of the machine (`RelativeSide`: front/left/right/back etc, relative to facing) is mapped to one logical inventory or `NONE`; default is TOP of the machine -> `mainInventory` accessible from `BOTTOM` side label (`setSideInventory(TOP, mainInventory, BOTTOM)`). Farms additionally: `FRONT` -> plantable inventory (3 slots; insert only accepts plantables), `TOP` -> misc inventory (3 slots; bonemeal, and shears for the tree farm). Animal farm: `FRONT` -> food (3 slots: wheat seeds, wheat, carrot, potato, beetroot), `TOP` -> tools (3 slots: bucket, shears). Mapping is saved as `inventorySideMappings` (list of int pairs of ordinals) and edited via `ContainerWorksiteInventorySideSelection`. Exposed as an `ITEM_HANDLER_CAPABILITY` for the facing that maps to an inventory. Quarry and fish farm only have the main inventory.

Item pickup: farms pick up `EntityItem`s in bounds every 20 ticks into plantable then main inventory (`TileWorksiteFarm.pickupItems`). Animal farm picks up items from `animal_farm_pickups` every 128 ticks.

### 3. Fake player use

`AWFakePlayer.get(world)` (`core/entity/AWFakePlayer.java`) is a cached singleton `FakePlayer` created from the first server world it sees (not per dimension; cleared on world unload). Used for:
- `ItemDye.applyBonemeal(stack, world, pos, fakePlayer, hand)` (crop, fruit and tree farm fertilising).
- `BlockTools.placeItemBlock` / `placeItemBlockRightClick` (planting saplings, seeds, mushrooms; sets held item and calls `stack.onItemUse` or `useItemRightClick`).
- `BlockTools.canBreakBlock` posts `BlockEvent.BreakEvent` with the fake player when the core option `fireBlockBreakEvents` is on (used by `breakBlockNoDrops`, i.e. all harvesting).
- `EntityAnimal.setInLove(fakePlayer)` when breeding.
The fake player never owns the worksite's player; the worksite owner is not used for protection checks (open question).

### 4. Crop farm (`cropfarm/WorkSiteCropFarm`, `WorkType.FARMING`)

Incremental scan (`TileWorksiteFarm.updateWorksite`): when the position queue is empty and bounds exist, it enqueues every target column (at Y = bounds min Y); each tick one position is polled and `scanBlockPosition` is called.

`WorkSiteCropFarm.scanBlockPosition` (`:55-77`): if the block is replaceable and the block below is tillable (`CropFarmRegistry.isTillable`), queue the below position to till; else if the block below is a registered soil, queue for planting. If not air, look up the crop handler `CropFarmRegistry.getCrop(state)`, add `getPositionsToHarvest` to the harvest queue, and add to the fertilise queue if `canBeFertilized`.

Priority: harvest, then fertilise (needs bonemeal, 1 per use from the misc inventory), plant (needs a plantable), till. Till: `world.setBlockState(pos, tilledState)` and hoe sound, requires the block above to be replaceable. Plant: `BlockTools.placeItemBlock(copy, pos, UP)` through the fake player, consumes one item on success. Harvest: `ICrop.harvest` writes drops to a combined (plantable + main) inventory, only if all drops fit.

`ICrop` handlers, tried most recently registered first (`registerCrop` inserts at index 0):

| Class | Rule | Used for |
|---|---|---|
| `CropDefault` | Block is `IGrowable` and `!canGrow` (i.e. fully grown) means harvest; breaks the block with no drops, takes one `IPlantable` drop and re-places it on the same spot (replant), rest to inventory. Plantable if item is `IPlantable`, an `ItemBlock`/`ItemBlockSpecial` of an `IPlantable` block | Wheat, carrots, potatoes, beetroots and any `IGrowable` crop. Fallback handler. |
| `CropGourd` (extends break-only) | matches `Material.GOURD` | Melon and pumpkin blocks |
| `CropStem` | Harvest positions: gourd-material blocks on the 4 horizontal neighbours of a stem; stem itself is fertilisable while it can grow; `harvest` returns false by itself | Melon/pumpkin stems |
| `CropBreakOnly` | Break and collect drops, no replant; data matcher for state and item | Mushrooms (from JSON) |
| `CropKeepBottom` | Harvest everything above the bottom block, scanning up to 10 blocks; only when the block above matches | Sugar cane, cactus |
| `CropMatureDefined` | Mature when block-state property matches (e.g. `age=3`), then default harvest | Nether wart, mod crops |
| `CropTall` | Multi-block crop of fixed height from JSON; harvests all `height` blocks | Immersive Engineering hemp (2) |
| `CropAgricraftCrop` | AgriCraft; see Compat | |

Assumptions that changed since 1.12: crops identified by `IGrowable.canGrow`, `Material.GOURD`, `IPlantable`, `Blocks.DYE` bonemeal metadata, `Items.DYE` with damage 15 (`isBonemeal`, `TileWorksiteFarm.java:158`), pre-flattening block names in JSON (`minecraft:reeds`, `minecraft:grass`, `minecraft:grass_path`), `Block.getDrops(NonNullList, ...)`, `block.isReplaceable`.

### 5. Fruit farm (`fruitfarm/WorkSiteFruitFarm`, `WorkType.FARMING`)

Scans each target column from the bounds layer up 7 blocks (`MAX_HEIGHT_SEARCH`). For each position: if `FruitFarmRegistry.getPickable(state).isRipe(state)`, queue pick; if any item in the plantable inventory maps to an `IFruit` whose `canPlant` is true there, queue plant; if the block is `IGrowable` and can grow, queue bonemeal. Priority pick, plant, bonemeal; each costs one full work unit. Bonemeal is stored in the misc inventory.

`IFruit` types: `FruitCocoa` (hard-coded: matches `Blocks.COCOA`, ripe at `AGE == 2`, plant on air next to a jungle log using `BlockOldLog.VARIANT`, item = `Items.DYE` meta brown, pick resets age to 0 and removes one cocoa bean from the drop = "replant"), `FruitBreakOnly` (break and take drops), `FruitPicked` (ripe property matcher; on pick set property to a "new" value; drops from `getDrops` using the deprecated list-returning overload because of Pam's Harvestcraft), `FruitPickedDrop` (fixed drop from JSON), `FruitPickedRemoveOne` (drops minus one of the first stack; a berry that regrows). `NO_FRUIT` is the null object.

There is no vanilla apple or sweet berry support: the only vanilla fruit is cocoa; every other fruit comes from the compat JSON.

### 6. Tree farm (`treefarm/WorkSiteTreeFarm`, `WorkType.FORESTRY`)

Scan (`scanBlockPosition`): if the position is replaceable and the block below is a registered tree soil (or the position below is replaceable and the one above is soil, for "hanging" trees), queue for planting; otherwise, if the block is a bonemealable `IGrowable` sapling queue fertilise; otherwise, if the block is non-air and both chop queues are empty, scan a whole tree from this block (`addTreeBlocks`) within a 31x31 horizontal window (`MAX_DISTANCE_TO_INITIAL_CUT_POSITION = 15`). Only one tree is queued at a time. Chop queues are persisted as long lists (`targetList`, `targetLeafList`), all other queues are rebuilt by scanning.

Priority: shear leaves (if shears in misc inventory), chop leaves (cost /10), chop trunk (/2), plant, bonemeal. Without shears, leaves go to the "chop" queue and are broken for drops; with shears they go to the shear queue (via `IShearable.onSheared`, then `setBlockToAir`, `world.setBlockToAir(position)`). Extra drops per block come from `IBlockExtraDrop` (only `ChorusFlowerDrop`: chorus flower block item). Shear drops and chop drops go into plantable inventory first (saplings are kept) then main.

Tree detection (`ITreeScanner`, first match by trunk state wins, last registered first):
- `DefaultTreeScanner`: breadth-first flood fill from the clicked trunk using a pluggable neighbour pattern (`ALL_AROUND`, `CONNECTED_AROUND`, `CONNECTED_UP_OR_LEVEL` = 4 horizontal + up, `CONNECTED_DOWN_OR_LEVEL`, `ALL_UP_OR_LEVEL`), constrained to the horizontal window, adding trunk blocks and leaf blocks. A leaf is accepted if its horizontal distance to the growing trunk bounding box is <= `maxLeafDistance`. Trunk bounding box grows with trunk blocks within distance 1. Default constructor: any block with `Material.WOOD` (except the tree farm block itself) is a trunk and `Material.LEAVES` is a leaf, `CONNECTED_UP_OR_LEVEL`, leaf distance 5 (`TreeFarmRegistry.java`, `DEFAULT_TREE_SCANNER`). That means planks, crafting tables and chests (wood material) count as trunks in 1.12.
- `ChorusScanner`: walks the branches of a chorus plant (ignoring DOWN), builds a `Branch` tree; a branch is harvested only when mature (flower age 5 at the end, or no flower); chorus flower returns itself as an extra drop.

Saplings (`TreeFarmRegistry`): hard-coded predicate matchers for `ItemBlock` of `BlockSapling` and `BlockMushroom` (planted by placing), plus JSON `saplings` entries, each with an optional `right_click` flag (plant via `useItemRightClick` instead of block placement, for items like Forestry saplings). Soils: `registry/tree_farm/tree_soil_blocks.json` holds the vanilla soils (`minecraft:dirt`, `minecraft:grass`, `minecraft:mycelium`, `minecraft:end_stone`) plus mod files. Vanilla saplings are hard-coded; `registry/tree_farm/saplings.json` adds only `minecraft:chorus_flower`. The default tree scanner is hard-coded at `registry/TreeFarmRegistry.java:38-40` (trunk = any `Material.WOOD` block except the farm, leaves = `Material.LEAVES`), and `registry/tree_farm/tree_scanners.json` adds oak log/leaves scanner entries.

### 7. Animal farm (`WorkSiteAnimalFarm`, `WorkType.FARMING`)

State: per-species caps `maxPigCount`, `maxCowCount`, `maxChickenCount`, `maxSheepCount` (default 6 each, saved, edited in `ContainerWorksiteAnimalControl`), resource counters, lists of `EntityPair` (entity ids), cull ids, shear ids, `cowsToMilk`.

Every 200 ticks `rescan()`: gets all `EntityAnimal` in bounds, buckets by `EntityCow` (includes mooshrooms), `EntityChicken`, `EntitySheep`, `EntityPig`; for each species finds pairs where both `getGrowingAge() == 0` and not `isInLove()`; counts adult animals and if above the cap marks the excess for culling. Sheep unsheared adults are queued; cows count adults for milking. Note `sheepToShear` is never cleared between rescans, so ids can accumulate (they are validated when used).

Actions in priority order: shear, milk, breed sheep (2 wheat), breed cows (2 wheat), breed chickens (2 seeds), breed pigs (2 carrots, or potatoes, or beetroot), cull. All cost one work unit. Breeding calls `setInLove(fakePlayer)` on the pair (vanilla then handles the baby). Milking has a random chance that depends on cow count and consumes a bucket to produce `Items.MILK_BUCKET` in the main inventory (the bucket-filling check `rand.nextInt(n*4) > n*3` is a 1/4-ish chance at best). Shearing uses `sheep.onSheared(shears, world, pos, fortune)` and the shears are not damaged. Culling: `animal.captureDrops = true; attackEntityFrom(GENERIC, health + 1)` and collects `capturedDrops`, adding `rand.nextInt(fortune)` extra items per stack (possible `IllegalArgumentException` is avoided since only used when fortune > 0). Dropped items near the farm that match `animal_farm_pickups` are collected every 128 ticks; the matching condition has an operator precedence bug (`a && b || c`, `WorkSiteAnimalFarm.java:460`), so any stack whose damage equals the configured damage matches regardless of item.

Inventories: food (3), tools (3), main (27, output). Fast food/tool checks by item identity.

### 8. Fish farm (`WorkSiteFishFarm`, `WorkType.FARMING`)

Counts the water columns in bounds every 200 ticks (`Material.WATER` from the top of the 4-high column down until a non-water block; the success chance divides by the constant `MAX_WATER = 1280`, which is larger than the 16x16x4 = 1024 blocks the bounds can hold, so even a full tank never reaches 100 percent). Each action: probability `waterCount / 1280`; if successful pick fish or ink (50/50 when both enabled; `harvestFish`/`harvestInk` from the alt GUI `ContainerWorksiteFishControl`). Fish: loot table `LootTableList.GAMEPLAY_FISHING` generated with `withLuck(fortune)` and `generateLootForPools`. Ink: `Items.DYE` meta 0 plus up to fortune extra. Works only if the main inventory can hold everything. Bounds are forced to the 4 layers below the machine (`onBoundsSet`: max Y = `pos.y - 1`, min Y = `pos.y - 5`).

### 9. Quarry (`WorkSiteQuarry`, `WorkType.MINING`)

Bounds are a column from the machine down to `pos.y - 1 - ...`: `height = pos.y - 1` (bedrock level), so it digs to Y=1 in a 1.12 world. It mines in chunk-sized slabs: `current` is the cursor (`BlockPos`), `incrementPosition` walks x then z within the current chunk, then goes down one layer, and when the bottom is reached `moveToStartOfNextChunk()` (unforces the old chunk and forces the next one when a chunk loader upgrade is installed). `canHarvest` skips air and liquids, enforces tool tier (`Block.getHarvestLevel(state)`): blocks with harvest level >= 2 need `TOOL_QUALITY_1` (level 2), `_2` (3) or `_3` (unlimited), otherwise skipped (base level counts as stone = 1); unbreakable blocks (hardness < 0) are skipped. Mining inserts all drops into the main inventory only if they all fit (else the action fails and retries after 20 ticks). `finished` flag saved with `current`, `validate`, `init`, `height`. Adding an upgrade resets the cursor to the start of the bounds. Valid upgrades: enchanted tools 1/2, quarry medium/large, tool quality 1-3, quarry chunk loader (not the basic one).

### 10. Auto-crafting table (`TileAutoCrafting`, `WorkType.CRAFTING`)

Holds a recipe in a `CraftingRecipeMemory` (core `crafting` package): a stored 3x3 matrix, matched with `AWCraftingManager` against `resourceInventory` (18 slots) using 1.12 `IRecipe`/`InventoryCrafting`; results go to `outputInventory` (9 slots; capability facing DOWN = output, any other side = input; no capability from `null`). The action is available when the output can hold the result; it takes the ingredients, puts crafted item and remaining items (containers like buckets) into output (or back into the resource inventory when the remainder was consumed as an ingredient). The recipe is saved and synced to the client. Reusable stacks logic (`getReusableStacks`) keeps tools in the matrix.

### 11. Registries (`registry/`)

Three static registries, none is a Forge registry; they are plain Java collections filled partly in static initialisers (hard-coded vanilla knowledge) and partly by JSON through the `RegistryLoader` (core). `RegistryLoader` walks `assets/ancientwarfare/registry/**.json` in the jar (or a `config/ancientwarfare/registry` override folder first; override files win by name), picks a parser by file base name, or by a top-level `"type"` field, skips files whose `"mod"` is not loaded or whose `"disabled"` is true, supports `"load_after"` (named parser dependency ordering), and logs errors. Seven parsers are registered by this module:

| Parser name (file base name) | Class | Fills | Entry fields |
|---|---|---|---|
| `tillable_blocks` | `CropFarmRegistry.TillableParser` | `tillable_mapping`: tillable state to tilled state; tilled states are also added to soils | `tillable`, `tilled` (blockstate matchers) |
| `crop_blocks` | `CropFarmRegistry.CropParser` | crop handlers | `crop`, `type` in `tall`, `keep_bottom`, `mature_defined`, `break_only` (anything else falls back to the default crop), `properties.height`, `mature` property, `item` |
| `soil_blocks` | `CropFarmRegistry.SoilParser` | soils | `soils[]` with `name` (+ properties) |
| `fruit_blocks` | `FruitFarmRegistry.FruitParser` | fruits | `type` in `picked_remove_one`, `picked`, `picked_drop`, `break_only`; `fruit`, `ripe`, `new`, `drop` |
| `saplings` | `TreeFarmRegistry.PlantableParser` | saplings | `sapling` (item matcher), optional `right_click` |
| `tree_soil_blocks` | `TreeFarmRegistry.SoilParser` | tree soils | `soils[]` |
| `tree_scanners` | `TreeFarmRegistry.TreeScannerParser` | tree detection | `type` default, `trunk`/`trunks`, `leaf`/`leaves`, `max_leaf_distance`, `next_block_search` (`all_up_or_level`, `all_around`, `connected_around`, `connected_down_or_level`, `connected_up_or_level`) |

Matching helpers (`JsonHelper`, `BlockStateMatcher`, `ItemStackMatcher`, `PropertyStateMatcher`, `PropertyState`) are core classes. Scanners with the same trunk matcher (compared by `hashCode`) are merged by adding leaf matchers.

Hard-coded in code (no JSON): default crop `CropDefault`, `CropGourd`, `CropStem`; fruit `FruitCocoa`; saplings `BlockSapling` and `BlockMushroom` item-blocks; tree default scanner on wood/leaves materials; `ChorusScanner`; `ChorusFlowerDrop`.

JSON in RES (only files used by these parsers; 38 files, entry totals include the base files):

| Parser | Files | Entries | Notes |
|---|---|---|---|
| `crop_blocks` | 4 (base + growthcraft, immersiveengineering, natura) | 8 | Base: reeds (`keep_bottom`), cactus (`keep_bottom`), nether_wart (`mature_defined` age 3), red/brown mushroom (`break_only`). Types: 2 mature_defined, 1 tall, 3 break_only, 2 keep_bottom. |
| `soil_blocks` | 3 (base, growthcraft, rustic) | 4 | Base: `minecraft:sand`, `minecraft:soul_sand` |
| `tillable_blocks` | 3 (base, biomesoplenty, randomthings) | 19 | Base: grass, dirt, grass_path to farmland |
| `fruit_blocks` | 8 (biomesoplenty, growthcraft, harvestcraft, natura, oreberries, oreshrubs, plants2, rustic) | 78 | 36 `picked_remove_one`, 37 `picked_drop`, 4 `break_only`, 1 `picked` |
| `saplings` | 6 (bop, forestry, growthcraft, plants2, rustic, twilightforest) | 10 | |
| `tree_soil_blocks` | 4 (bop, natura, plants2, tconstruct) | 6 | |
| `tree_scanners` | 7 (bop, forestry, natura, plants2, rustic, tconstruct, twilightforest) | 78 | search modes: 57 `connected_up_or_level`, 8 `all_around`, 8 `all_up_or_level`, 4 `connected_around`, 1 `connected_down_or_level` |

Of the 38 parser files, 6 are vanilla (`crop_farm/` 3, `tree_farm/` 3; their entries are the base files counted in the totals above) and 32 are mod compat gated by `"mod"`. A copy-paste error: `compat/rustic/tree_scanners.json` declares `"mod": "natura"`, so it loads only when Natura is present, not Rustic. Mods covered (all 1.12-era): Biomes O' Plenty, Forestry, Growthcraft, Pam's Harvestcraft, Immersive Engineering, Natura, Ore Berries (Tinkers), Ore Shrubs, Plants 2, Random Things, Rustic, Tinkers' Construct, Twilight Forest. The `registry/compat/` folder also holds three `entity_spawn_nbt.json` files (ebwizardry, grimoireofgaia, iceandfire) that belong to the NPC/structure side, not this slice.

Correction: `registry/tree_farm/` does ship vanilla files (`tree_soil_blocks.json`, `saplings.json`, `tree_scanners.json`, see section 6). The non-compat folders are `crop_farm/` and `tree_farm/`, 3 files each.

### 12. Chunk loaders (`chunkloader/`, `tile/TileChunkLoader*`, `TileWorksiteBounded`)

All use `ForgeChunkManager` (1.12 ticket system) with `Type.NORMAL` tickets owned by the Automation mod instance.
- `AWChunkLoader` (a `LoadingCallback`, singleton): on world load, for each non-player ticket whose mod id starts with `ancientwarfare` and whose mod data contains `tilePosition`, finds the tile at that position implementing `IChunkLoaderTile` and calls `setTicket(ticket)` to re-attach it. `writeDataToTicket` writes the position long.
- `TileChunkLoaderSimple`: on `onBlockPlacedBy` requests a ticket and forces the 3x3 chunks around the block; `invalidate` and `breakBlock` release it. Chunk forcing is not saved in the tile itself; it relies on the ticket persistence of the chunk manager.
- `TileChunkLoaderDeluxe` (also `IInteractableTile`): forces its own chunk plus a user-selected set `ccipSet` (toggled in `ContainerChunkLoaderDeluxe`/GUI, saved as NBT list `chunkList` of x/z ints); viewers (open containers) are notified on change.
- Worksites: `TileWorksiteBounded` can hold one ticket if the `BASIC_CHUNK_LOADER` (or `QUARRY_CHUNK_LOADER`) upgrade is installed; forces the machine's chunk and all chunks overlapping the work bounds. The quarry overrides this to force only the chunk currently being mined and unforces finished chunks.
- Other user: `npc/tile/TileTownHall` also uses `ForgeChunkManager` (outside this slice).
Config in forge's `forgeChunkLoading.cfg` (ticket limits) is not touched by this mod.

### 13. Mailbox (`TileMailbox`, `gamedata/MailboxData`)

Mailboxes are named endpoints. Public boxes live in a shared `MailboxSet("public")`; private boxes in a map from owner player **name** (string) to a set. Each `MailboxEntry` holds a list of `DeliverableItem` (stack, origin dimension and x/y/z, `deliveryTime` counter in server ticks, though a comment says "millis") and a transient list of receiver tiles.

Flow each tick on the server (`TileMailbox.update`): if the box has a name, it registers itself as a receiver in its `MailboxEntry` (`addMailboxReceiver`); if it also has a destination name, `trySendItems` moves the first non-empty stack from `sendInventory` (18 slots) into the destination entry as a `DeliverableItem`. `MailboxData.serverTick` (END phase) calls `onTick(1)` which increments every item's `deliveryTime` and, for each registered receiver, merges the first deliverable item (the one with `deliveryTime >= distance * mailbox_travel_time_per_block`, or `mailbox_travel_time_per_dimension` when the dimension differs) into the box's `receivedInventory` (18 slots), then clears the receiver list. `TileMailbox.update` also calls `getDeliverableItems` and ignores the result (dead call). Delivery is thus one stack per box per tick, with a distance delay. `deleteMailbox` refuses if items are pending. Private mail uses `getOwner().getName()`; toggling private/public clears the names.

Persistence: NBT `publicBoxes`, `privateBoxes` (list of `mailboxList`, `ownerName`), each entry `name`, `itemList`. Client sync: none except GUI. Capability: `ITEM_HANDLER_CAPABILITY` for the sides in `sendSides` (default UP) and `receivedSides` (default DOWN); both saved. `autoExport` is stored but unused ("TODO"). `MailboxData` is registered on the global Forge bus in its constructor, so each loaded instance subscribes (and is never unsubscribed): potential duplicate ticks if the storage is reloaded.

### 14. RF compat (`proxy/RFProxy*`)

`RFProxy.loadInstance()` picks `RFProxyActual` by reflection when `redstoneflux` is loaded, else a no-op instance. `RFProxyActual.transferPower` pushes torque out of a generator into a neighbouring `IEnergyReceiver` at `torqueToRf` (default 10 RF per torque), and `isRFTile` checks `IEnergyConnection`. `TileWorksiteBase` itself accepts RF through `@Optional.Method`-stripped methods. The RF API is the only third-party energy API in use.

### 15. Data fixers (`datafixes/ItemMapDataWalker`)

An `IDataWalker` that, for the given tile ids (the three warehouse storage tiles), walks the NBT path `inventory/itemMap/entryList` and runs the vanilla `ITEM_INSTANCE` fixer on each `item` tag, so item ids in stored stacks get upgraded when Minecraft's own datafixer changes. It is registered in core (`core/datafixes/AWDataFixes.java`, version 11) as `registerWalker(BLOCK_ENTITY, ...)`; this slice only holds the class.

### 16. AgriCraft compat (`compat/agricraft`)

`AgricraftCompat` (an `ICompat`, `getModId() == "agricraft"`) registers `CropAgricraftCrop` into the crop registry when AgriCraft is loaded. `CropAgricraftCrop` matches `AgriBlocks.getInstance().CROP`, uses the `TileEntityCrop` to decide maturity (`isMature`), fertility (`isFertile`), and harvests by `crop.getDrops(...)` then `setGrowthStage(0)` (no breaking, no replant). Compiled against `curse.maven:agricraft` and `infinitylib` (`build.gradle:74-75`). Stats: `com/infinityraider/agricraft` referenced by 1 automation class.

## Dependencies

From other Ancient Warfare modules (named classes; stats say automation uses 149 core classes, 1 vehicle class):
- Core: `ContainerBase` (77 uses), `BlockRotationHandler` and `IRotatableBlock/Tile` (62), `InventoryTools` (50), `Button` and GUI elements, `ITorque`/`ITorqueTile` (37), `NetworkHandler` (35, GUI ids and container registration), `IWorkSite`/`IWorker`/`IBoundedSite`/`IChunkLoaderTile`/`IInteractableTile`/`IOwnable`, `WorldTools` (28), `BlockTools`, `EntityTools`, `AWFakePlayer`, `Owner`, `TileUpdatable`/`TileOwned`, `WorksiteUpgrade`, `ItemMulti`, `ItemBlockBase`, `ItemBlockOwnedRotatable`/`RotatableMetaTile`/`ItemBlockMeta`, `BlockBase`, `ModConfiguration`, `AWCoreStatics` (`energyPerWorkUnit`, `fireBlockBreakEvents`), `RegistryLoader`, `IRegistryDataParser`, `JsonHelper` and the matchers, `CompatLoader`/`ICompat`, `AWGameData`, `AWCraftingManager`/`CraftingRecipeMemory`, `AWCoreItems.IRON_HAMMER`, `InputHandler`, `ClientProxyBase`, `CommonProxyBase`, `SortItemsFirstComparator`, `ItemWrapper`, `Trig`, `CoreProperties`.
- NPC: `NpcAIPlayerOwnedWork` and `NpcWorker` call `IWorkSite.addEnergyFromWorker` (the worksite does not reference npc classes).
- Structure: `structure` uses `DefaultTreeScanner`, `ITree`, `ITreeScanner` from this slice, plus torque blocks (stats: structure -> automation 3 classes).
- Vehicle: client proxy registers `GuiVehicleStats` (a cross-module oddity).
- Reverse: core references automation classes in `AWDataFixes` (`ItemMapDataWalker`), `WorksiteUpgrade.unlocalizedName` uses `AWAutomationItems`, and warehouse crafting and auto crafting container hooks.

Third-party mods: RedstoneFlux (`cofh.redstoneflux.api`: `IEnergyProvider`, `IEnergyReceiver`, `IEnergyConnection`; optional); AgriCraft and InfinityLib (optional); BuildCraft: only in the dependency string; CodeChickenLib (rendering; the torque/render slices; 15+ classes); FTBLib in core; JEI in core. The compat JSON targets Biomes O' Plenty, Forestry, Growthcraft, Pam's Harvestcraft, Immersive Engineering, Natura, Ore Berries, Ore Shrubs, Plants 2, Random Things, Rustic, Tinkers' Construct, Twilight Forest, Harder Wildlife (item id in the pickup list).

Minecraft/Forge APIs that no longer exist in 1.21.1:
- `ForgeChunkManager` tickets and `LoadingCallback`; `GameRegistry.registerTileEntity`; `ITickable`; `TileEntity`/`NBTTagCompound` and 1.12 NBT API; `WorldSavedData` with `MapStorage`; `IDataWalker`/`DataFixer` mod fixes; `CommandBase`; `@Mod` annotation/`@SidedProxy`/`@Instance`/`FMLPreInitializationEvent`; `@ObjectHolder` injection; `@Optional.Interface`; Forge `Configuration` and `ConfigChangedEvent`; `Material` (`WOOD`, `LEAVES`, `GOURD`, `WATER`, `isLiquid`); `Block.getHarvestLevel`; `IPlantable`/`EnumPlantType`; `IGrowable.canGrow(world,pos,state,isClient)`; `Items.DYE` and damage values, `Blocks.LOG`/`BlockOldLog`/`BlockPlanks.EnumType` metadata, `Blocks.TALLGRASS`-style ids and every pre-flattening name in the JSON; `ItemDye.applyBonemeal`; `EntityAnimal.getGrowingAge`; `Entity.captureDrops`/`capturedDrops` field access; `LootTableList.GAMEPLAY_FISHING` and `LootContext.Builder.withLuck`; `ForgeEventFactory`-style `BlockEvent.BreakEvent` with the old constructor; item metadata/`getSubBlocks`/`ItemMulti`; `IExtendedBlockState`/unlisted properties; forge blockstate "forge_marker" JSON; TESR binding via `ClientRegistry`; ore dictionary.

## Porting notes (1.21.1 NeoForge)

Ranked hardest first. Where I name a class and am not sure it exists under that name in 1.21.1, it is marked "(unverified)".

1. **Registry/crop/tree/fruit knowledge is built on 1.12 metadata and materials (L).** What: the whole `registry/` package, 38 JSON files, and `Crop*`, `Fruit*`, `Tree*` classes assume pre-flattening block names with variant properties, `Material`, `IGrowable.canGrow` as a maturity test, `Items.DYE` meta and `IPlantable`. Why hard: every detection rule is a correctness assumption about vanilla behaviour that changed (wheat/carrot/potato maturity is `CropBlock.isMaxAge`; melon/pumpkin gourds are named blocks, not a material; mushrooms and saplings are `BlockItem`s of different classes; logs/leaves are block tags; hoeing is an item ability; sugar cane/cactus/bamboo/nether wart (`NetherWartBlock`, age 3) need explicit rules). 32 of 38 JSON files target mods that mostly have no 1.21.1 build, and the matcher format (name + property map) would need to be redefined. Approach: define a datapack-driven format (reload listener or custom datapack registry, via `AddReloadListenerEvent` or `DataPackRegistryEvent.NewRegistry`; unverified exact usage) with block/item tags as the primary matching tool (vanilla `BlockTags.LOGS`, `BlockTags.LEAVES`, `ItemTags.SAPLINGS`, `BlockTags.CROPS` and friends, tag names unverified), keep the handler type set (`default`, `keep_bottom`, `tall`, `mature_defined`, `break_only`, `picked*`), and rewrite the default JSON for vanilla 1.21.1 (including new fruits such as sweet berries and glow berries, bamboo, kelp if wanted). Drop all compat JSON until the target mod exists on 1.21.1. Effort L.

2. **Worksite engine and tile entity migration (L).** `TileWorksiteBase`..`TileWorksiteFarm` (about 1,100 lines) plus 8 concrete tiles. Why: `ITickable` to `BlockEntityTicker` (server only), `NBTTagCompound` to `CompoundTag` with `HolderLookup.Provider` in `saveAdditional`/`loadAdditional` (1.21 signatures; unverified detail), `TileUpdatable` sync via `getUpdateTag`/`getUpdatePacket`, capability lookup moves from `hasCapability/getCapability` to `RegisterCapabilitiesEvent` (the side-mapped inventories become `registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> ...)`), facing/active properties must be real blockstate properties, owner/team checks use scoreboard `PlayerTeam`, `ItemStackHandler` serialisation takes a `HolderLookup.Provider` in 1.21. Approach: keep the action/priority-queue design (it is clean and testable), port the base class once, then each worksite by mechanical translation. Keep upgrade ordinal persistence compatibility off the table (no worlds migrate): serialise upgrades as strings. Effort L.

3. **Energy: torque cells, RF and NPC cranking (M, depends on the torque slice).** The worksite energy cell, `RFProxy`, `IEnergyReceiver` and the `rf_energy_conversion_factor` option. Approach: RedstoneFlux is gone; use NeoForge's FE capability (`Capabilities.EnergyStorage.BLOCK`, `IEnergyStorage`; names believed correct, unverified) with a receive-only storage that converts to torque at the configured ratio. Keep `IWorkSite.addEnergyFromWorker` as the NPC contract. Decide whether to keep the separate torque energy unit at all. Fix `getEnergyPerActivation` precedence while there (see Open questions). Delete `RFProxy`/`RFProxyActual`. Effort M.

4. **Fake player, harvesting and drops API (M-L).** `harvestBlock`, `breakBlockNoDrops`, `Crop*.harvest`, `Fruit*.pick`, quarry `harvestBlock`, tree `shear`, all call `Block.getDrops(NonNullList...)` and fortune ints. In 1.21 drops come from loot tables with `LootParams` (tool item, origin, optional entity, block entity), fortune is an enchantment looked up from a registry (not an int), so to give fortune the worksite must hold a virtual tool stack with the enchantment applied or pass a tool in `LootParams`. Use `Block.getDrops(state, serverLevel, pos, blockEntity, entity, tool)` (believed correct, unverified) and `ServerLevel.destroyBlock`/`removeBlock` plus a `BlockEvent.BreakEvent` through a fake player (`FakePlayerFactory`, unverified). `AWFakePlayer` caching the first world is a bug to fix by caching per `ServerLevel`. Planting via `BlockItem.useOn` with `UseOnContext` is available through the fake player. Effort M, one shared helper class.

5. **Animal farm (M).** 525 lines of 1.12 entity API: `EntityAnimal` to `Animal` (`getAge`/`isBaby`, `setInLove(Player)`, `isInLove`), `EntitySheep` shearing to the shearing interfaces (vanilla `Shearable` and NeoForge shear item ability; unverified), milk via `Items.MILK_BUCKET` and `Cow`, `ItemStack` identity comparisons, new species (goat, sniffer, llama, etc. are out of scope today). Culling relies on `captureDrops`/`capturedDrops` and a `LivingDropsEvent` handler that was never registered; redesign: kill via `hurt` and collect through `LivingDropsEvent` for a tracked id set, or roll the entity's loot table directly. Replace entity-id lists with UUIDs. Make the per-species caps data-driven by entity type so modded animals could be added. Effort M.

6. **Chunk loaders (M).** `ForgeChunkManager` tickets are replaced in NeoForge by a ticket controller: a `TicketController` registered through `RegisterTicketControllersEvent` and forced/unforced with `forceChunk` on the controller (names believed correct, unverified), with validation callbacks that re-establish tickets by block position on load. Design questions: the controller must be re-validated after restart, the deluxe loader's selectable chunk set and the quarry's "move the forced chunk" behaviour map directly, but forced chunks no longer need an in-world ticket object, so `AWChunkLoader` shrinks to a validation callback. Also consider a configurable server cap. Effort M.

7. **Quarry (M).** Tier test `getHarvestLevel >= 2` -> vanilla tier tags (`BlockTags.NEEDS_IRON_TOOL`, `NEEDS_DIAMOND_TOOL`, `INCORRECT_FOR_*` in 1.21; tag names unverified) or ask a virtual pickaxe `isCorrectToolForDrops`; `Material.isLiquid` -> `!state.getFluidState().isEmpty()`; hardness `state.getDestroySpeed(level, pos) >= 0`; build height is now -64 to 320, so `height = pos.y - 1` should become `pos.y - minBuildHeight` and the 3 saved ints need the new origin. Chunk-slab iteration logic ports unchanged. Effort M.

8. **Fish farm loot (M).** The 1.21 fishing loot table has conditions that need a fishing hook entity and a tool (rod) in the loot context (believed; unverified), so the 1.12 shortcut `generateLootForPools` with only luck will not work; either build a loot context with a dummy `FishingHook`/rod stack or define the farm's own loot table under `data/` (best: data-driven and tweakable). Water counting uses `Material.WATER` -> `FluidTags.WATER`. Effort S-M.

9. **Mailbox and saved data (M).** `WorldSavedData` -> `SavedData` with a `SavedData.Factory` through `DimensionDataStorage` (`ServerLevel.getDataStorage().computeIfAbsent`; believed, unverified); load/save signature takes `HolderLookup.Provider` and `ItemStack` NBT uses codecs (`ItemStack.CODEC`/`parseOptional`; unverified). Remove the self-registering Forge bus subscriber (use `ServerTickEvent`), key private boxes by player UUID, store the name for display. Deliveries one-stack-per-tick and name lookups remain. Side-mapped `Capabilities.ItemHandler.BLOCK` on `TileMailbox`. Effort M.

10. **Data format and registration plumbing (M).** 26 blocks, 27 items and 36 block entity types become `DeferredRegister` entries (`DeferredRegister.Blocks`/`Items`, `DeferredRegister<BlockEntityType<?>>`; believed correct) with `BlockEntityType.Builder`. Tiered blocks: flywheel controller, flywheel storage, torque junction/shaft/distributor each have 3 tiers by metadata: split into 3 blocks each (15 blocks) or one block with a `tier` property (the tile class is chosen by meta today, so 3 BE types remain either way). Warehouse storage: 3 sizes. Worksite upgrade: 11 items (`ItemMulti` is gone). Creative tab ordering via `CreativeModeTab.Builder.displayItems`. 17 forge-marker blockstate JSONs need rewriting. The research recipe type needs a custom `RecipeType`/serializer; ore-dictionary ingredients -> tags. Effort M for this slice; larger overall.

11. **Config (S).** 24 options -> `ModConfigSpec` (NeoForge) with `COMMON`, `SERVER`, `CLIENT` specs; the F8 key toggling a client option becomes a `KeyMapping` (registered with `RegisterKeyMappingsEvent`) that sets the spec value. Remove the five dead fields. Effort S.

12. **Command, datafixer, small items (S).** `/awwarehouse` to Brigadier (`RegisterCommandsEvent`) with a permission level. The datafixer walker (`ItemMapDataWalker`) and all 1.12 world migration are not portable (see below). `AWAutomationTab`, key binding, `ItemBlockWorksiteStatic` default-bounds logic (to `BlockItem` override or `setPlacedBy`). Effort S.

13. **AgriCraft compat (S-M, may be moot).** Against a different AgriCraft API (the 1.21.1 version of the mod, if one exists, is unverified). Isolate behind a service and skip until needed. Effort S.

### Cannot be ported as is

- **Migration of 1.12 worlds**: block ids, tile ids (`forestry_tile` etc.), metadata, NBT layouts, `ItemMapDataWalker` and the mod's data fixer version 11 are tied to the 1.12 flattening-era data format; a 1.12 world cannot load in 1.21.1. Offer a standalone converter only if there is demand.
- **Hard-coded 1.12 vanilla assumptions** in `CropDefault`, `FruitCocoa`, `CropStem`, `DefaultTreeScanner` default materials and the quarry tool tiers: replaced by tags and data.
- **RedstoneFlux** (`@Optional.Interface`, `RFProxy`): replaced by FE capabilities.
- **BuildCraft dependency string**: delete it; there is no BuildCraft code.
- **ForgeChunkManager tickets**: replaced by NeoForge's ticket controller approach.
- **The 32 compat JSON files** (Forestry, Natura, Rustic, Growthcraft, Harvestcraft, Plants2, Twilight Forest, etc.): written for 1.12 block names and mods; keep as reference only.
- **Unregistered dead code**: `TileOreProcessor`, `BlockOreProcessor`, the animal farm `LivingDropsEvent` handler.

## Candidates for capttrackerlib

Generic code that another, unrelated mod could reuse:
- **Data-driven block/item matcher and registry-loader framework** (`RegistryLoader`, `IRegistryDataParser`, `JsonHelper`, `BlockStateMatcher`, `ItemStackMatcher`, `PropertyStateMatcher`, all in core, with the `load_after`, `mod` and `disabled` flags and override-folder behaviour): any mod with data-defined compat lists benefits, but in 1.21 most of it is covered by datapacks and tags, so only a thin "matcher JSON" utility is worth sharing.
- **Incremental scan queue + priority action pattern** (`TileWorksiteBase` action/energy loop, `TileWorksiteFarm` per-tick queue): generic "machine that works over an area slowly" base. It needs a decoupling from `TorqueCell` first.
- **Per-dimension fake player helper** (`AWFakePlayer` + `BlockTools.placeItemBlock/breakBlockNoDrops/canBreakBlock`): any automation mod needs a safe fake-player block place/break with event firing. Rewrite to cache per `ServerLevel`.
- **Mailbox-style delayed item delivery saved data** (`MailboxData`): small, self-contained, reusable as "named endpoint item transfer with distance delay".
- **Side-mapped inventory config** (`TileWorksiteBoundedInventory` side mapping with `RelativeSide`): generic machine IO configuration, though the same code exists in the core `BlockRotationHandler` family (core slice).
- **Tree/trunk flood-fill scanner** (`DefaultTreeScanner` with pluggable neighbour patterns, `HorizontalAABB`, `ChorusScanner`) and `ItemStacks`/inventory helpers (`InventoryTools.canInventoryHold/insertOrDropItems/mergeItemStack`, which belong with the existing `ItemStacks` in capttrackerlib).
- **Chunk-loader ticket helper** (the owner-position callback in `AWChunkLoader`): any mod with machine-bound chunk loading needs the same restore-by-position pattern.

Looks generic but should stay inside Ancient Warfare: the concrete worksites and their priorities, the animal farm species logic, the quarry chunk-slab iterator (tied to this block's upgrade design), `WorksiteUpgrade` (persisted by ordinal, coupled to NPC XP work types), `CropFarmRegistry`/`FruitFarmRegistry`/`TreeFarmRegistry` as Ancient-Warfare-specific content (the matcher base can be shared, the registries should not), `IWorkSite`/`IWorker` (NPC-coupled), and the whole torque unit system.

## Open questions

1. **Efficiency precedence bug.** `getEnergyPerActivation` evaluates `energyPerWorkUnit * 1.f - eff`, so upgrades reduce cost by under 1.3 percent. Was the intent `energyPerWorkUnit * (1 - eff)` (up to 65 percent cheaper)? Decision needed before port, because it changes balance for every worksite.
2. **Animal farm cull drops.** The `LivingDropsEvent` handler in `WorkSiteAnimalFarm` is never registered, and `Entity.captureDrops` handling in 1.12 (does `onDeath` clear `capturedDrops` first?) could not be checked from the available source. Behaviour in the original may therefore be: drops spawn normally and are picked up later by `animal_farm_pickups`. Needs a test in a 1.12.2 environment, or accept a redesign.
3. **Vanilla tree soil list.** The 1.12 vanilla soils are in `registry/tree_farm/tree_soil_blocks.json` (dirt, grass, mycelium, end_stone), and vanilla saplings are hard-coded plus `chorus_flower` in JSON. Podzol, coarse dirt and the 1.21 equivalents (rooted dirt, mud for mangrove, etc.) are not in the source, so the 1.21.1 list must be decided. Decide whether `mycelium` and `end_stone` carry over.
4. **Animal farm pickup condition bug** (`a && b || c`, line 460): fix or keep? Same for the unbounded `sheepToShear` list.
5. **Which of the 26 blocks and 36 tile ids keep their registry names** in the new mod? `forestry_tile` (tree farm), `flywheel_large_tile` (heavy tier) and `warehouse_storage_small_tile` (class `TileWarehouseStorage`) have inconsistent historic names; since worlds do not migrate, they can be renamed cleanly (suggest `tree_farm`, `flywheel_controller_heavy`, `warehouse_storage_small`).
6. **Mailbox ownership key**: player name string today. UUID in the port? Does mail need to survive a rename? Also whether mailboxes should become per-dimension data or stay global.
7. **Worksite protection**: worksites break and place blocks through a fake player that is not the worksite owner. Should the port check ownership/claim mods (BreakEvent already fires with the fake player only if `fireBlockBreakEvents`)? Also the 5x5 default bounds in `ItemBlockWorksiteStatic` has two `TODO`s about overlap validation with other worksites.
8. **Scope of compat in the new mod**: all 13 mod integrations (and AgriCraft) are 1.12 mods. Which, if any, have 1.21.1 versions worth supporting? I cannot tell from the source. Also the config-file override folder (loader called from `core/AncientWarfareCore.java:93`) (`config/ancientwarfare/registry`) feature: keep (as datapack overrides) or drop?
9. **Research recipe system and component items** are referenced by 49 recipe JSONs but live in other slices; the planner needs a decision on whether the research gate is kept.
10. **Tools not available / not verified:** I did not run the mod or any Minecraft 1.12 decompile; 1.12 behaviours that are described from memory rather than from this source (fishing loot behaviour, `captureDrops`) are labelled as such. All 1.21.1 class names are from memory and not checked against NeoForge sources: those marked "(unverified)" must be confirmed during implementation.

## Verification

Checked against `SRC` and `RES` directly, not against the first draft.

**Checked and confirmed**
- Scope: 80 files in the listed directories, 7,164 lines (line total recomputed from the files).
- Blocks: 26 `register` calls in `init/AWAutomationBlocks.java` (lines 152-240), 26 ItemBlocks in `registerItemBlocks` (lines 118-148). ItemBlock classes match the table.
- Standalone item: one (`worksite_upgrade`, `item/ItemWorksiteUpgrade` extends `ItemMulti`), 11 enum variants in `core/upgrade/WorksiteUpgrade.java`.
- Tile entities: 36 `registerTile` calls (lines 156-241), ids match the table, per-slice split 10 + 8 warehouse + 18 torque.
- Config: 24 options (23 `config.get` plus `enable_client_energy_animations` via `getBoolean`, line 162). Defaults checked: `energy_network_update_frequency` 5, `mailbox_travel_time_per_block` 10, `per_dimension` 100, `torqueToRf` 10, `energy_per_work_unit` 50 (core), `animal_farm_pickups` 11 entries.
- Commands: one (`command/CommandWarehouse`), y range 0..256 as stated.
- Containers: 21 `registerContainer` calls; parsers: 7 `registerParser` calls.
- BuildCraft: the only occurrence in the upstream tree is the `@Mod` dependency string. Confirmed unused.
- Efficiency bug: `IWorkSite.java:78` is `energyPerWorkUnit * 1.f - efficiencyBonusFactor`. Confirmed.
- Worksite loop: redstone check, 20-tick retry, `TorqueCell(32, 0, energyPerWorkUnit * 3, 1)`, hand-crank output at `TileWorksiteBase.java:258,263`, crop divisors /5 and tree leaf /10.
- Animal farm: caps default 6, rescan 200 ticks, pickups every 128 ticks, pickup condition precedence bug at line 460.
- Fish farm `MAX_WATER = 1280`. Bonemeal check = `Items.DYE` damage 15 (`TileWorksiteFarm.java:158`).
- Quarry `height = pos.getY() - 1` (line 244). Core `RegistryLoader.load()` at `AncientWarfareCore.java:93`, `CompatLoader.init()` at line 97.
- Tree default scanner: `Material.WOOD` trunk and `Material.LEAVES` leaves (`TreeFarmRegistry.java:38-40`). `compat/rustic/tree_scanners.json` declares `"mod": "natura"` (confirmed).
- Research recipes: 49 under `research_recipes/automation`, 9 component recipes outside research. Both match.
- Source files: 80 in slice, confirmed.
- Blockstates, creative tab and `BlockWorksiteBase` `Material.WOOD` (line 34): confirmed.

**Changed** (17 edits, all applied by a count-checked script)
- Line references corrected: AWAutomationBlocks (285-375 to 152-246; registerItemBlocks 251 to 118-149), AWAutomationItems (403 to 23), AncientWarfareCore RegistryLoader call (now traced at 93), mjToTorque/torqueToMj (63,65 to 79,82), ItemBlockWorksiteStatic setBounds (23-24 to 28-29).
- GUI count: 13 client `registerGui` calls, not 14.
- Registry JSON count: 38 parser files (crop_farm 3, tree_farm 3, compat 32), not 35. Compat folder holds 35 files including 3 `entity_spawn_nbt` files for other slices.
- Tree farm: the report said no vanilla soil or sapling JSON exists. Wrong. `registry/tree_farm/` has `tree_soil_blocks.json` (dirt, grass, mycelium, end_stone), `saplings.json` (chorus_flower), `tree_scanners.json` (oak entries). Section 6, section 11 evidence, porting note and open question 3 rewritten.
- LivingDrops handler: the method has `@SubscribeEvent` but the class is never registered, so the note now says so.

**Not settled**
- Per-entry counts for the vanilla `tree_farm/` base files inside the tree_soil/saplings/tree_scanners rows were not reconciled. The compat entry counts for those rows were not re-counted.
- Slope of a few numeric claims (hardness 2, flammability 20, 60-tick active re-check, Stats category totals for container/gui) were not checked against source.
- `package-info` count: report says "about 20". Not counted exactly.
- Modern NeoForge 1.21.1 names: no NeoForge sources available offline. Names marked "(unverified)" remain unverified. Unmarked names I believe exist (`LivingDropsEvent`, `BlockEntityType.Builder`, `Capabilities.ItemHandler.BLOCK`, `ModConfigSpec`, `RegisterKeyMappingsEvent`, `RegisterCommandsEvent`, `ItemStack.CODEC`) were not checked against a 1.21.1 jar.
- Stats-category reconciliation (container 22, gui 24) relies on the STATS file and was not re-derived.
