# automation-base: module entry, registries, config, items, chunk loaders, mailbox data, worksite base classes

Slice key `automation-base`, module `ancientwarfareautomation`. Ported from `automation/*.java`, `init`, `config`, `registry`,
`gamedata`, `chunkloader`, `command`, `item`, `compat`, `proxy`, `tile/*.java` and the abstract bases of `tile/worksite`
(report 03). Nothing was compiled or run. Every Minecraft / NeoForge name was checked against `analysis/mc-sources`
(see "Unconfirmed API" for what rests on other slices only).

## What was ported

Java, package `io.github.capttracker.ancientwarfareautomation`:

`init`
- `AwAutomationBase`: the master list. 38 blocks, 11 upgrade items, 34 block entity types, the `warehouse_pos` data
  component, the creative tab; `load()`, `registerEvents(modBus)`; capability registration for the worksites, the mailbox
  and (through the torque slice's `TorqueCapabilities`) the torque tiles; ticket controller, parsers, command and tick listeners;
  fire odds of the worksite blocks.
- `WarehouseSize` (SMALL, MEDIUM, LARGE; the metadata of the warehouse storage block). `TorqueTier` is the torque slice's
  (`block.torque.TorqueTier`).

`config`
- `AWAutomationStatics`: two `ModConfigSpec`s (common: shared and server options, client: render options), values copied into
  the original static field names.

`block.entity.worksite` (base classes; 1.12.2 name in brackets)
- `WorksiteBlockEntity` (TileWorksiteBase): work loop, torque cell, upgrades, owner, facing and active in the block state.
- `BoundedWorksiteBlockEntity` (TileWorksiteBounded): work bounds, chunk loading through tickets.
- `InventoryWorksiteBlockEntity` (TileWorksiteBoundedInventory): 27 slot main inventory, side mapped inventories.
- `FarmWorksiteBlockEntity` (TileWorksiteFarm): target map, scan queue, plantable and misc inventories, harvest, fertilise.
- `IWorksiteAction`, `WorksiteProperties` (FACING and ACTIVE), `WorksiteTools` (drops with fortune, bone meal, replaceable),
  `TorqueEnergyStorage` (Forge Energy face of a torque tile; replaces RFProxy and the RF methods).

`chunkloader`: `AWChunkLoader` (ticket controller), `ChunkTicketSet` (the chunks one block entity forced),
`ChunkLoaderSimpleBlockEntity` (TileChunkLoaderSimple), `ChunkLoaderDeluxeBlockEntity` (TileChunkLoaderDeluxe).

`gamedata`: `MailboxData` (saved data), `MailboxBlockEntity` (TileMailbox; it lives here and not in `block.entity` because that
package was not on this slice's write list).

`registry`: `CropFarmRegistry`, `FruitFarmRegistry`, `TreeFarmRegistry` with the 7 parsers.

`item`: `WorksiteBlockItem` (ItemBlockWorksiteStatic), `WorksiteUpgradeItem` (ItemWorksiteUpgrade, now one item per upgrade),
`WarehouseStockLinkerBlockItem` (ItemBlockWarehouseStockLinker).

`command`: `WarehouseCommand` (CommandWarehouse, Brigadier).

`client`: `AutomationInputHandler` (F8 key, config screen entry).

Assets and data (scripts `analysis/port/automation_assets.py`, `automation_component_recipes.py`, `research_recipes_convert.py
--module automation`; all safe to re-run), under `src/main/resources`:
- `assets/ancientwarfareautomation/textures/block/*` (92 files: the cube textures and their `.mcmeta` animations) and `textures/item/upgrade_*` (11).
  Other files in `textures/block/automation/` and `models/block/automation/` are the torque slice's machine models.
- vanilla blockstates, block models and item models for the 18 non-machine blocks: 6 worksites (active/inactive rear),
  warehouse control, mailbox, warehouse crafting, interface, 3 storages, 2 chunk loaders, auto crafting (the Blockbench model),
  stock viewer, stock linker (lit/unlit); plus the 11 upgrade item models.
- `data/ancientwarfareautomation/loot_table/blocks/*` for all 38 blocks (each drops itself; the torque slice wrote the same
  files for its 20).
- `data/minecraft/tags/block/mineable/axe.json` and `pickaxe.json` for all 38 blocks (wood blocks axe, stone blocks pickaxe).
- `data/ancientwarfareautomation/recipe/research/**`: 49 research recipes (converted, 0 skipped; 20 are the torque ones),
  `recipe/*.json`: the 9 plain component recipes (shaped, results are Core's items).
- `data/ancientwarfareautomation/registry/crop_farm/*` and `tree_farm/*`: the 6 vanilla registry files rewritten for 1.21.1.
- `port-notes/automation-base.lang.json`: 54 keys.

## Class contract with the other slices

`AwAutomationBase` references these classes. Torque ones exist (taken as written); the others are what the next slices must
write (or list their names, only `AwAutomationBase` changes then).

| Class | Package (under `...ancientwarfareautomation`) | Required shape |
|---|---|---|
| `WorksiteBlock` | `block` | `(BlockBehaviour.Properties, Supplier<? extends BlockEntityType<?>>)`; quarry, 5 farms, warehouse control. Extends `BaseBlock`, implements `EntityBlock` and `IRotatableBlock` (FOUR_WAY, `invertFacing` true). Declares `WorksiteProperties.FACING` and `WorksiteProperties.ACTIVE` (the same instances), `getStateForPlacement` with `BlockRotationHandler.getFaceForPlacement(context, this)`, `getTicker` returns `WorksiteBlockEntity.ticker(level)`. Right-click check: the original `BlockWorksiteBase.onBlockActivated` (owner, same team or friend, `IInteractableTile`), `BaseBlock.useItemOn` has none. |
| `AutoCraftingBlock` | `block` | `(Properties)`; same properties and ticker (its block entity is a `WorksiteBlockEntity`). |
| `WarehouseStorageBlock` | `block` | `(Properties, WarehouseSize)`. |
| `WarehouseInterfaceBlock`, `WarehouseCraftingStationBlock` | `block` | `(Properties)`; no properties. |
| `WarehouseStockViewerBlock` | `block` | `(Properties)`; `BlockStateProperties.HORIZONTAL_FACING`; item is `OwnedBlockItem` (owner and facing from `IOwnable` / `IRotatableTile` of the block entity). |
| `WarehouseStockLinkerBlock` | `block` | `(Properties)`; `HORIZONTAL_FACING` and `BlockStateProperties.LIT`; reads the warehouse from the stack with `WarehouseStockLinkerBlockItem.getWarehousePos(stack)`. |
| `MailboxBlock` | `block` | `(Properties)`; `HORIZONTAL_FACING`; `IRotatableBlock`; `getTicker` returns `MailboxBlockEntity.ticker(level)`; right-click opens the mailbox menu. |
| `ChunkLoaderSimpleBlock`, `ChunkLoaderDeluxeBlock` | `block` | `(Properties)`; extend `BaseBlock` (its `onRemove` calls `onBlockBroken`, which releases the tickets); `setPlacedBy` calls `ChunkLoaderSimpleBlockEntity.setupInitialTicket()` on the server. |
| `QuarryBlockEntity`, `AnimalFarmBlockEntity`, `FishFarmBlockEntity` | `block.entity.worksite` | `(BlockPos, BlockState)`, pass `AwAutomationBase.<X>_BE.get()`; extend `InventoryWorksiteBlockEntity`. |
| `CropFarmBlockEntity`, `FruitFarmBlockEntity`, `TreeFarmBlockEntity` | `block.entity.worksite.cropfarm` / `.fruitfarm` / `.treefarm` | same; extend `FarmWorksiteBlockEntity`. |
| `AutoCraftingBlockEntity` | `block.entity.worksite` | same; extends `WorksiteBlockEntity`; registers its own item handler capability. |
| `WarehouseBlockEntity` | `block.entity.warehouse` | `(BlockPos, BlockState)`; extends `BoundedWorksiteBlockEntity` (it gets torque, energy and upgrades; the capabilities are registered here). |
| `WarehouseStorageBlockEntity` (one type, three sizes) and `WarehouseInterfaceBlockEntity`, `WarehouseCraftingStationBlockEntity`, `WarehouseStockViewerBlockEntity`, `WarehouseStockLinkerBlockEntity` | `block.entity.warehouse` | `(BlockPos, BlockState)`. The warehouse slice registers their capabilities itself. |
| `ChunkLoaderDeluxeMenu` | `menu` | `(int containerId, Inventory, ChunkLoaderDeluxeBlockEntity)`; implements `ChunkLoaderDeluxeBlockEntity.Viewer` (`addViewer` / `removeViewer`); the block entity writes the block position when opening it. |
| concrete crop / fruit / tree handlers | `block.entity.worksite.cropfarm` / `.fruitfarm` / `.treefarm` | see "Registry classes" below. |

Registry classes the concrete-worksite slice must write, with the constructors the registries call (1.12.2 signatures with
`IBlockState` -> `BlockState`, `World` -> `Level`, `IBlockAccess` -> `BlockGetter`):
- `cropfarm`: `ICrop` (`getPositionsToHarvest(Level, BlockPos, BlockState)`, `canBeFertilized(BlockState, Level, BlockPos)`,
  `harvest(Level, BlockState, BlockPos, int fortune, IItemHandler)`, `matches(BlockState)`, default `matches(ItemStack)`, default
  `isPlantable(ItemStack)`), `CropDefault()`, `CropStem()`, `CropGourd()` (extends `CropBreakOnly`), `CropBreakOnly(BlockStateMatcher, ItemStackMatcher)`,
  `CropKeepBottom(BlockStateMatcher)`, `CropMatureDefined(BlockStateMatcher, PropertyStateMatcher)`, `CropTall(BlockStateMatcher, int)`.
- `fruitfarm`: `IFruit` (`matches(BlockState)`, default `matches(ItemStack)`, `isRipe(BlockState)`, `pick(Level, BlockState, BlockPos, int, IItemHandler)`,
  `isPlantable()`, default `canPlant(Level, BlockPos, BlockState)`, default `plant(Level, BlockPos)`), `FruitCocoa()`,
  `FruitBreakOnly(BlockStateMatcher)`, `FruitPicked(BlockStateMatcher, PropertyStateMatcher, PropertyState<?, ?>)`, `FruitPickedDrop(..., ItemStack)`,
  `FruitPickedRemoveOne(BlockStateMatcher, PropertyStateMatcher, PropertyState<?, ?>)`.
- `treefarm`: `ITreeScanner` (`scanTree(Level, BlockPos, BlockPos, BlockPos)`, `matches(BlockState)`), `ISapling` (`matches(ItemStack)`, `isRightClick()`),
  `Sapling(Predicate<ItemStack>, boolean)`, `IBlockExtraDrop` (`matches(BlockState)`, `NonNullList<ItemStack> getDrops(BlockGetter, BlockPos, BlockState, int)`),
  `ChorusFlowerDrop()`, `ChorusScanner()`, `DefaultTreeScanner(Predicate<BlockState>, Predicate<BlockState>)` and
  `(Predicate, Predicate, INextPositionGetter, int)` with `getTrunkMatcher()`, `addLeafMatcher(Predicate<BlockState>)`, the `INextPositionGetter` interface and its five
  constants `ALL_AROUND`, `CONNECTED_AROUND`, `CONNECTED_UP_OR_LEVEL`, `CONNECTED_DOWN_OR_LEVEL`, `ALL_UP_OR_LEVEL`. The registry's default tree scanner uses the
  tags `minecraft:logs` and `minecraft:leaves` (1.12.2: `Material.WOOD` and `Material.LEAVES`).

What the concrete worksites get from the base classes (public API): `WorksiteBlockEntity.ticker(Level)`, `getNextAction` /
`processAction` / `updateWorksite` to implement, `addUpgrade` hook, `getFortune()`, `isActive()`; bounds: `getWorkBoundsMin/Max`, `setBounds`,
`isInBounds`, `validateCollection`, `onBoundsSet`, `getRenderBounds()`; chunk loading: `chunkLoadWorkBounds()` (override, the quarry
forces only its current chunk), `forceChunk(ChunkPos)`, `unforceChunk(ChunkPos)`, `hasChunkLoaderUpgrade()`, `setupInitialTicket()`;
inventories: `mainInventory`, `setSideInventory`, `setInventorySideMappings`, `getItemHandler(Direction)`, `openAltGui(Player)`;
farms: `plantableInventory`, `miscInventory`, `harvestBlock(pos)`, `fertilize(pos)`, `tryPlace`, `canReplace`, `isFarmable`, `isBonemeal`,
`plantableCount`, `bonemealCount`, `countResources()`, `getTargetMap()` / `setTargetBlocks(byte[])`; `WorksiteTools.getDrops/getFortuneTool/applyBonemeal/canReplace`.

## Wiring needed in shared files

1. `AncientWarfareAutomation.java` (mod constructor, before `REG.register(modBus)`; the constructor already has `modBus` and `container`):
   ```java
   AwAutomationBase.load();
   AwAutomationBase.registerEvents(modBus);
   AWAutomationStatics.register(modBus, container);
   ```
   imports `io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase` and `...config.AWAutomationStatics`. The
   stub's TODO comment goes.
2. `assets/ancientwarfareautomation/lang/en_us.json`: merge `automation-base.lang.json` and `automation-torque.lang.json`; remove the template key
   `block.ancientwarfareautomation.example_block`.
3. Core, `upgrade/WorksiteUpgrade.unlocalizedName()` builds `item.ancientwarfare.worksite_upgrade.<name>` (keys that Core's own lang file
   has). The items are `item.ancientwarfareautomation.worksite_upgrade_<variant>`; if the upgrade list in the worksite screens should
   show them, change it to `AwAutomationBase.UPGRADE_ITEMS.get(this).get().getDescriptionId()` (Automation depends on Core, so Core cannot
   call that; use a supplier registered by Automation) or keep the Core keys.
4. Warehouse slice: call `WarehouseCommand.setDebugger(...)` from its setup so `/awwarehouse` works.
5. Client: nothing in `AwClient`; `AutomationInputHandler` registers itself (`@EventBusSubscriber`).
6. `AwNetwork`: nothing (this slice defines no payloads).
7. `build.gradle` / `neoforge.mods.toml`: nothing (the Core dependency exists; BuildCraft and RedstoneFlux are not dependencies any more).
8. `docs/PORT-STATUS.md`: Automation base slice done, not built.

## Registry names

Blocks (38): `tree_farm`, `crop_farm`, `fruit_farm`, `animal_farm`, `fish_farm`, `quarry`, `auto_crafting`, `warehouse_control`,
`warehouse_interface`, `warehouse_crafting`, `warehouse_storage_{small,medium,large}`, `warehouse_stock_viewer`,
`warehouse_stock_linker`, `mailbox`, `flywheel_controller_{light,medium,heavy}`, `torque_junction_{...}`, `torque_shaft_{...}`,
`torque_distributor_{...}`, `stirling_generator`, `waterwheel_generator`, `hand_cranked_generator`, `windmill_generator`,
`flywheel_storage_{...}`, `windmill_blade`, `chunk_loader_simple`, `chunk_loader_deluxe` (creative tab order of 1.12.2).
Items: one block item each, plus `worksite_upgrade_{bounds_medium, bounds_large, quarry_medium, quarry_large, enchanted_tools_1, enchanted_tools_2,
quality_tools_1, quality_tools_2, quality_tools_3, chunkloader_basic, chunkloader_quarry}` (the 1.12.2 model variants; enum order
SIZE_MEDIUM ... QUARRY_CHUNK_LOADER). Data component `warehouse_pos`. Block entity types: the block names above for worksites, warehouse,
mailbox, chunk loaders and generators; `warehouse_storage` and `flywheel_storage` shared by the three blocks; `torque_shaft_<tier>`,
`torque_junction_<tier>`, `torque_distributor_<tier>`, `flywheel_controller_<tier>`; `windmill_blade`. The 1.12.2 `*_tile` ids and
`forestry_tile` / `flywheel_large_tile` oddities are gone (worlds do not migrate).

## Decisions and small changes from the original

- Facing and `active` of the worksites are block state properties (`WorksiteProperties`); the tile entity's `orientation` tag is not
  saved. Upgrades are saved by name (the 1.12.2 int array and the template parser's string list are still read).
- Energy: Redstone Flux is replaced by `Capabilities.EnergyStorage.BLOCK` (`TorqueEnergyStorage`), receive only, 10 FE per torque unit
  (`rf_energy_conversion_factor` keeps its name and default; <= 0 switches it off). `RFProxy` is gone; generators push with
  `TorqueEnergyStorage.transferPower`. The torque slice has its own FE adapter with the same maths (unify later).
- Chunk loading: `ForgeChunkManager` tickets are a NeoForge `TicketController` (`AWChunkLoader.CONTROLLER`). A block position owns its
  forced chunks; `ChunkTicketSet` remembers them in the block entity NBT (`forcedChunks`) so they can be released exactly, also after a restart.
  `AWChunkLoader.validateTickets` drops the tickets of positions without a chunk loading block entity when a level loads. Tickets are not
  requested when a block entity loads (NeoForge saves and re-applies them), only on placement, upgrade install, bounds change and deluxe toggles.
- Mailbox: private boxes are keyed by the owner's UUID (the player name is kept for display), the origin dimension is a string id,
  `MailboxData.onServerTick` is registered once (the original registered every instance in its constructor), `autoExport` is saved now.
  The API takes the `Owner` (null = public box). The dead `getDeliverableItems` call in the tile update is dropped.
- Worksite upgrade item: used on a block (`useOn`), not creative-consuming, PASS on non-worksites (the original swung the arm on any block).
- `WorksiteBlockItem`: the default 5 x 5 bounds are set on the server only; the two overlap TODOs of the original are still open.
- `FarmWorksiteBlockEntity`: items are picked up every 20 game ticks (the original used the day time), resource counts are refreshed after
  bone meal use, `isFarmable` default looks at `BushBlock`, `SugarCaneBlock`, `CactusBlock` (no `IPlantable` in 1.21).
- Deluxe chunk loader: removing the loader's own chunk from the selection no longer unforces it (the original unforced it, a bug).
- Config: five dead fields dropped (`conduitRenderID`, `fishFarmRescanTicks`, `animalFarmRescanTicks`, `mjToTorque`, `torqueToMj`); value ranges
  are new (the spec needs them); drain factors are limited to 0..1. `animal_farm_pickups` takes item ids or `#tags`; the 1.12.2 entry `minecraft:wool`
  (any colour) became `#minecraft:wool`. The animal farm port must read it that way. The original saved the config only when Core's config changed (wrong mod id): gone.
- Registry data rewritten for 1.21.1: sugar cane (was `reeds`), `grass_block`, `dirt_path`, red sand, one scanner per wood type with flattened
  block names; tree soils are the flattened 1.12.2 ones (dirt, coarse dirt, podzol, grass block, mycelium, end stone). Cherry, mangrove and azalea trees use the default
  tag scanner.

## Not ported (and why)

- AgriCraft compat (`compat/agricraft`, `CropAgricraftCrop`): no 1.21.1 AgriCraft API known. BuildCraft: the dependency string was the only trace.
- The 32 compat registry JSON files (Biomes O' Plenty, Forestry, Growthcraft, Pam's Harvestcraft, Immersive Engineering, Natura, Ore Berries, Ore Shrubs,
  Plants 2, Random Things, Rustic, Tinkers' Construct, Twilight Forest): written for 1.12.2 block names. The parsers keep the `mod` gate, so files can be added back per mod.
  `compat/rustic/tree_scanners.json` declared `"mod": "natura"` (copy paste error), moot now.
- `ItemMapDataWalker` and the data fixer (1.12 world migration), `TileOreProcessor` / `BlockOreProcessor` (dead code), `proxy/*`, `SidedProxy`, the `ConfigChangedEvent` handler,
  `AWAutomationTab` as a class (a registrar tab), the 21 `NetworkHandler.registerContainer` calls (menu types: GUI slice), `ItemBlockTorqueTile`, `ItemBlockWarehouseStorage`
  (plain block items now), `ItemMulti`.
- Client proxy: the 28 model sprites stitched for the renderers and the renderer bindings (render slices), `GuiVehicleStats` registration (vehicle module). `getRenderBoundingBox`
  has no 1.21.1 block entity hook; `BoundedWorksiteBlockEntity.getRenderBounds()` is there for the renderer.
- Light opacity of the torque transport blocks (`setLightOpacity(1)`): no block property for it in 1.21.1.
- Models and item models of the torque blocks (torque slice), JEI, machine `.obj` models.

## Unconfirmed API / things to check at the first build

- Every NeoForge and Minecraft name used was found in the sources. Nothing rests on another slice's usage alone.
- Generic inference: `REG.blockEntity(name, X::new, supplier varargs)` with `suppliers(...)` returning a raw-array cast; `AwAutomationBase.block(...)` with method reference constructors
  for `BiFunction<? super B, Item.Properties, ? extends BlockItem>`; `registerBlockEntity` lambdas on generic helper methods. If javac cannot infer, add type witnesses.
- Static initialisation order in `AwAutomationBase`: lambdas use qualified names (`AwAutomationBase.X_BE`) to avoid illegal forward references.
- `TicketController.forceChunk` loads the chunk when a ticket is new (`level.getChunk`); it is called from game logic (placement, upgrade, GUI), never from `onLoad`.
- `AWChunkLoader.validateTickets` calls `level.getBlockEntity(pos)` while the server prepares levels (NeoForge documents block state checks there; the chunk is loaded then).
- The `BlockPos.CODEC` / `BlockPos.STREAM_CODEC` data component registration (`ByteBuf` codec used where a `RegistryFriendlyByteBuf` codec is expected: `? super` in the registrar).
- `ModConfigSpec` list option with `defineListAllowEmpty(String, List, Supplier, Predicate)`.

## Rework candidates (for the playability pass)

- Worksite efficiency upgrades (`getEnergyPerActivation` subtracts the bonus, report open question 1), animal farm pickup condition bug and unbounded `sheepToShear` (animal farm slice).
- Worksite protection: the fake player is not the owner; `fire_block_break_events` posts break events with the fake player only.
- 5 x 5 default bounds overlap checks; a bounds adjust screen reuse for the warehouse.
- Chunk loader limits (a server cap), moving the quarry's forced chunk without releasing all (works through `forceChunk` / `unforceChunk` now).
- Mailbox: one stack per box per tick, names per owner UUID, no sync to clients except the menu; per-dimension data could replace the global set.
- Vanilla coverage of the farm registries: sweet berries, glow berries, bamboo, kelp, azalea and mangrove propagule as saplings, coarse dirt / rooted dirt tillable, mud as tree soil.
- Research recipe `warehouse_stock_linker` needs `ancientwarfarenpc:routing_order` (guarded with a `neoforge:mod_loaded` condition); the NPC port must use that item name or the file changes.
- Research recipes `enchanted_tools_1/2` use a `neoforge:components` ingredient with `minecraft:stored_enchantments` (Fortune I / II), non-strict.
- Per-tier block entity types for the torque tiles (the torque slice keeps three classes) could collapse to one type per kind like the warehouse storage.
