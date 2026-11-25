# Automation: torque energy system and block classes
> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

Packages covered: `automation/tile/torque/` (26 files, 2,615 lines: 22 classes in the package, 2 in `multiblock/`, 2 `package-info.java`) and `automation/block/` (28 files, 2,543 lines: 27 classes plus `package-info.java`). Total 54 files, 5,158 lines (counted with `wc -l`). Context read from `core/interfaces/ITorque.java`, `core/block/BlockRotationHandler.java`, `core/util/BlockFinder.java`, `automation/init/AWAutomationBlocks.java`, `automation/config/AWAutomationStatics.java`, `automation/proxy/RFProxy*.java`, `automation/item/ItemBlockTorqueTile.java`, the torque renderers (only to see how animation state is consumed), and the research recipes under `RES/assets/ancientwarfare/research_recipes/automation/torque/`.

This part of the mod is the "torque" power grid and every block class of the Automation module. Torque is the mod's own energy unit. Generators (hand crank, stirling engine, waterwheel, windmill) produce it, tiered shafts, junctions and distributors move it, flywheels store it, and worksites consume it (`IWorkSite extends ITorqueTile`, `core/interfaces/IWorkSite.java:15`). The block package also holds the non-torque Automation blocks: the worksite shell, the warehouse family, the auto crafting table, the mailbox and the chunk loaders. Their tile entities live in other slices; only the block classes are covered here.

Note on ownership: `AWAutomationBlocks` (the registration list, `automation/init`) is outside this slice but is the only place that defines registry names, so the tables below use it.

## What it adds

### Blocks

26 blocks are registered under the modid `ancientwarfareautomation` (`init/AWAutomationBlocks.java`, `register(RegistryEvent.Register<Block>)`). `BlockOreProcessor` is a 27th block class that is never registered (see Open questions).

| Registry name | Class | What it is | Variants and porting note |
|---|---|---|---|
| `torque_shaft` | `BlockTorqueTransportShaft` | Straight 6-way shaft; bounding box stretches along its facing axis | 3 tiers by metadata (`TorqueTier` LIGHT/MEDIUM/HEAVY): split into 3 blocks. Facing is in the tile only. Rotating model needs a block entity renderer |
| `torque_junction` | `BlockTorqueJunction` | Conduit: one output side, five input sides, connection-sensitive bounding box | 3 tiers: 3 blocks. Connection flags are unlisted properties computed from the tile |
| `torque_distributor` | `BlockTorqueDistributor` | One input (back), five outputs | 3 tiers: 3 blocks |
| `flywheel_controller` | `BlockFlywheelController` | Input/output block that sits on top of a flywheel stack | 3 tiers: 3 blocks. 4-way facing in tile, `powered` flag in tile |
| `flywheel_storage` | `BlockFlywheelStorage` | Multiblock storage cell (1x1xh or 3x3xh cube) | 3 tiers: 3 blocks. All multiblock state is in the tile |
| `hand_cranked_generator` | `BlockHandCrankedGenerator` | Player or worker cranked generator | 1 block, 4-way facing in tile. Implements `IWorkSite` so NPC workers can crank it |
| `stirling_generator` | `BlockStirlingGenerator` | Fuel-burning generator, 1-slot GUI | 1 block. GUI id `GUI_STIRLING_GENERATOR` |
| `waterwheel_generator` | `BlockWaterwheelGenerator` | Generator that needs a water channel in front | 1 block. Real blockstate property `valid_setup` (boolean) |
| `windmill_generator` | `BlockWindmillGenerator` | Output block placed behind the windmill blade plane | 1 block |
| `windmill_blade` | `BlockWindmillBlade` | Building block of a square vertical blade plane | 1 block. Flammable (fire spread 60, flammability 20). All state in tile |
| `chunk_loader_simple` | `BlockChunkLoaderSimple` | Chunk-ticket loader | 1 block. Uses Forge chunk tickets, which need replacing |
| `chunk_loader_deluxe` | `BlockChunkLoaderDeluxe` | Chunk loader with GUI (`GUI_CHUNK_LOADER_DELUXE`) | 1 block |
| `mailbox` | `BlockMailbox` | Item mailbox with GUI | 1 block. Metadata is the horizontal facing: becomes a `FACING` property |
| `auto_crafting` | `BlockAutoCrafting` | Auto crafter, worksite style | 1 block. Facing read from the tile through `IRotatableTile` |
| `warehouse_control` | `BlockWorksiteBase` (factory `TileWarehouse::new`) | Warehouse control block | 1 block |
| `warehouse_storage` | `BlockWarehouseStorage` | Storage block with a size property | 3 sizes by metadata (`Size` SMALL/MEDIUM/LARGE): 3 blocks, each with its own tile class |
| `warehouse_interface` | `BlockWarehouseInterface` | Output interface, GUI | 1 block |
| `warehouse_crafting` | `BlockWarehouseCraftingStation` | Crafting station, GUI | 1 block |
| `warehouse_stock_viewer` | `BlockWarehouseStockViewer` | Wall-mounted stock display | 1 block. `facing` from metadata, 4 hard-coded AABBs, no collision |
| `warehouse_stock_linker` | `BlockWarehouseStockLinker` | Wall-mounted redstone output tied to a warehouse | 1 block. Properties `facing`, `lit`. Emits power 15. Linked position travels in item NBT (`WAREHOUSE_POS_TAG`) |
| `quarry` | `BlockWorksiteBase` (`WorkSiteQuarry`) | Quarry worksite | 1 block |
| `tree_farm` | `BlockWorksiteBase` (`WorkSiteTreeFarm`) | Tree farm worksite | 1 block |
| `crop_farm` | `BlockWorksiteBase` (`WorkSiteCropFarm`) | Crop farm worksite | 1 block |
| `fruit_farm` | `BlockWorksiteBase` (`WorkSiteFruitFarm`) | Fruit farm worksite | 1 block |
| `animal_farm` | `BlockWorksiteBase` (`WorkSiteAnimalFarm`) | Animal farm worksite | 1 block |
| `fish_farm` | `BlockWorksiteBase` (`WorkSiteFishFarm`) | Fish farm worksite | 1 block |

Support classes with no registry entry: `BlockBaseAutomation` (creative tab, client model registration), `BlockTorqueBase` (shared torque behaviour), `BlockTorqueTransport` (tier property, bounding box), `BlockTorqueTransportSided` (six connection properties), `BlockTorqueGenerator` (4-way facing, `invertFacing=false`), `TorqueTier` (enum, not a block).

**Variant count.** 26 registered names. Flattened: 5 tiered torque blocks x 3 tiers = 15, `warehouse_storage` x 3 sizes = 3, and 20 single-variant blocks, giving **38 separate blocks**. Only 6 registry names carry metadata variants (shaft, junction, distributor, flywheel controller, flywheel storage, warehouse storage); every other block has one item. Within the torque part alone, 5 registry names (shaft, junction, distributor, flywheel controller, flywheel storage) become 15 blocks. Properties that stay as real block-state properties: `facing` (mailbox, stock viewer, stock linker, and worksite shells where it is derived from the tile), `lit` (stock linker), `valid_setup` (waterwheel), `active` (worksite shell, derived from the tile).

### Tile entities

18 concrete tile entity classes (plus 6 abstract bases) are defined in `automation/tile/torque`. Ids are registered with `GameRegistry.registerTileEntity(class, ancientwarfareautomation:<id>)` (`AWAutomationBlocks.registerTile`).

| Registered id | Class | What it is | Porting note |
|---|---|---|---|
| `torque_shaft_light_tile`, `_medium_tile`, `_heavy_tile` | `TileTorqueShaftLight/Medium/Heavy` (abstract `TileTorqueShaft`) | Single-cell conduit; chains of same-class shafts behave like one rotating axle | One block entity type per tier block, or one type with a tier field. Linked-list neighbour cache must be rebuilt |
| `torque_junction_light_tile`, `_medium_tile`, `_heavy_tile` | `TileConduitLight/Medium/Heavy` (concrete; extends `TileTorqueSidedCell` directly) | 6 per-side cells, one output side | Same |
| `torque_distributor_light_tile`, `_medium_tile`, `_heavy_tile` | `TileDistributorLight/Medium/Heavy` (abstract `TileDistributor`) | Splits input among up to 5 outputs | Same |
| `flywheel_light_tile`, `flywheel_medium_tile`, `flywheel_large_tile` | `TileFlywheelControllerLight/Medium/Heavy` (abstract `TileFlywheelController`) | Controller; the heavy tier is registered as `flywheel_large_tile` | Odd id matters only for world migration |
| `flywheel_storage_tile` | `TileFlywheelStorage` (`multiblock/`) | Multiblock part; the control tile holds the stored energy | Reads the tier from block metadata; must read it from the block instead |
| `hand_cranked_generator_tile` | `TileHandCrankedGenerator` | Input cell plus output cell, owner | Implements `IWorkSite`, `IOwnable`; cranked by hammer or NPC |
| `stirling_generator_tile` | `TileStirlingGenerator` | Fuel slot, burn time | Item-handler capability, GUI. Uses `TileEntityFurnace.getItemBurnTime` |
| `waterwheel_generator_tile` | `TileWaterwheelGenerator` | Validates the water channel every 20 ticks | `Material.WATER` tests must become fluid checks |
| `windmill_generator_tile` | `TileWindmillController` | Single cell fed from the blade control block behind it | |
| `windmill_blade_tile` | `TileWindmillBlade` (`multiblock/`) | Multiblock member and, for the centre block, controller | |

Abstract bases: `TileTorqueBase`, `TileTorqueSingleCell`, `TileTorqueSidedCell`, `TileTorqueShaft`, `TileDistributor`, `TileFlywheelController`.

Reconciliation with STATS: `jar_stats.txt` lists 26 top-level classes in `automation/tile/torque` and 28 in `automation/block`. Source gives 26 files and 28 files, and each count includes one `package-info`. Real classes: 24 in torque (18 concrete + 6 abstract) and 27 in block. The block-side tile classes named in `createTileEntity` but owned by other slices are not counted: `TileMailbox`, `TileAutoCrafting`, `TileChunkLoaderSimple/Deluxe`, `TileWarehouse*`, `TileOreProcessor`, and the `WorkSite*` classes.

### Items

None defined here. All torque ItemBlocks use `ItemBlockTorqueTile` (`automation/item`), `flywheel_storage` uses `ItemBlockMeta` (core). The 26 ItemBlocks are registered in `AWAutomationBlocks.registerItemBlocks`. Shaft crafting components (`ItemComponent.WOODEN/IRON/STEEL_TORQUE_SHAFT`, `core/init/AWCoreItems.java:77-79`) belong to Core.

### Containers and GUIs

None defined here. Block classes register client GUIs through `NetworkHandler.registerGui`: `GUI_STIRLING_GENERATOR`, `GUI_MAILBOX_INVENTORY`, `GUI_WAREHOUSE_CRAFTING`, `GUI_WAREHOUSE_OUTPUT`, `GUI_WAREHOUSE_STORAGE`, `GUI_WAREHOUSE_STOCK_LINKER`, `GUI_WAREHOUSE_STOCK`, `GUI_WORKSITE_AUTO_CRAFT`, `GUI_CHUNK_LOADER_DELUXE`. Container and Gui classes are in other slices.

### Network packets

None defined. All sync uses the Core `PacketBlockEvent` through `NetworkHandler.sendToAllTrackingChunk` (`TileTorqueBase.sendDataToClient`, `TileFlywheelStorage.sendDataToClient`).

### Config options

Defined in `automation/config/AWAutomationStatics.java` (another slice), consumed here. Listed so one config class can be planned; they are not counted in this slice's totals.

| Config key | Default | Used by |
|---|---|---|
| `rf_energy_conversion_factor` | 10 RF per torque; 0 or less disables RF | `TileTorqueBase`, `RFProxyActual`, `TileWorksiteBase` |
| `energy_network_update_frequency` | 5 ticks | `TileTorqueBase.serverNetworkUpdate`, `TileFlywheelStorage` |
| `enable_server_energy_network` | true | only `TileFlywheelStorage.serverNetworkUpdate` |
| `low/med/high_quality_tile_energy_drain` | 0.9999 / 0.99995 / 0.99999 (per-tick multiplier) | tier efficiency |
| `low/med/high_quality_tile_energy_transfer` | 32 / 192 / 1024 per tick | tier cell size and rate |
| `low/med/high_quality_storage_energy_max` | 9600 / 14400 / 24000 per flywheel block | `TileFlywheelStorage.setAsController` |
| `stirling_generator_output_factor` | 1 per burning tick | `TileStirlingGenerator` |
| `waterwheel_generator_output_factor` | 1 per tick | `TileWaterwheelGenerator` |
| `hand_cranked_generator_output_factor` | 1 | `TileHandCrankedGenerator`, `TileWorksiteBase` |
| `windmill_per_size_output_factor` | 1 per blade-plane height per tick | `TileWindmillBlade` |

Non-configurable constants: `rpmToRpt` = 360/60/20 = 0.3 degrees per tick per RPM, `low_quality_rpm` = 100, `low_rpt` = 30 degrees per tick. `low/med/high_quality_conduit_energy_max` (config keys) and `enable_energy_client_updates` (config key `enable_client_energy_animations`) exist in the statics; no code in SRC reads them.

### Recipes and other data

20 research recipes under `RES/assets/ancientwarfare/research_recipes/automation/torque/` (type `ancientwarfare:research_recipe`, gated by a research name; `windmill_blade.json` needs research `the_wheel`, pattern `SCS/CSC/SCS`, wool and `#STICKWOOD`): `flywheel_controller_{light,medium,heavy}`, `flywheel_storage_{light,medium,heavy}`, `torque_distributor_{...}`, `torque_junction_{...}`, `torque_shaft_{...}`, `hand_cranked_generator`, `stirling_generator`, `waterwheel_generator`, `windmill_blade`, `windmill_generator`. Models are 12 `.m2f` files under `assets/ancientwarfare/models/automation/` (`cube`, `flywheel_controller`, `flywheel_large`, `flywheel_small`, `hand_engine`, `sterling_engine`, `torque_conduit`, `torque_shaft`, `waterwheel`, `windmill_10`, `windmill_blade`, `windmill_control`) and 14 files under `models/block/automation/` (11 `.obj`, 3 `.json`). There are no blockstate JSONs for the torque blocks; they are rendered by a code-driven bakery (`IBakeryProvider`). Blockstate JSONs exist for the non-torque blocks in `assets/ancientwarfare/blockstates/automation/` (18 files: 16 for blocks, e.g. `mailbox.json`, `warehouse_storage.json` keyed on `size`, plus `component.json` and `worksite_upgrade.json`).

### Renderers and models

Wiring lives in the block classes (`registerClient`, `getBakery`, `getExtendedState`) but the renderers (26 files, 1,612 lines in `automation/render`) are outside this slice. Per block: `TorqueShaftRenderer`, `TorqueJunctionRenderer`, `TorqueDistributorRenderer`, `FlywheelControllerRenderer`, `FlywheelStorageRenderer`, `HandCrankedGeneratorRenderer`, `StirlingGeneratorRenderer`, `WaterwheelGeneratorRenderer`, `WindmillGeneratorRenderer`, `WindmillBladeRenderer`; tile renderers are bound in `proxy/ClientProxyAutomation.java:108-114`.

### Entities, commands, key bindings, sounds, saved data, event handlers, AI tasks

None in this slice.

## How it works

### 1. The torque model

`ITorque` (`core/interfaces/ITorque.java`) is a holder for three types.

- `ITorqueTile`: 10 methods keyed by `EnumFacing`: `getMaxTorque`, `getTorqueStored`, `addTorque`, `drainTorque`, `getMaxTorqueOutput`, `getMaxTorqueInput`, `canOutputTorque`, `canInputTorque`, `useOutputRotation`, `getClientOutputRotation(side, partialTicks)`. The contract says tiles push their own power and never pull.
- `TorqueCell`: a double-valued buffer with `maxInput`, `maxOutput`, `maxEnergy`, `efficiency`. `addEnergy` clamps to `min(maxInput, maxEnergy - energy)`, `drainEnergy` clamps to `min(maxOutput, energy)`. Both throw on NaN.
- `SidedTorqueCell` extends it and asks its owner `canInputTorque(dir)` / `canOutputTorque(dir)` before accepting or releasing anything.

Energy is plain `double`. Conversion constants: `torqueToRf = 10` (configurable), and 1:1 `mjToTorque`/`torqueToMj` (unused in this slice). `after:buildcraftcore` is still in the `@Mod` dependency string (`AncientWarfareAutomation.java:53`) but no BuildCraft code is referenced here.

### 2. Tiers

`TorqueTier` has LIGHT=0, MEDIUM=1, HEAVY=2, used as block metadata and as the blockstate property `tier`. Each tier is a trivial final subclass returning two config values (`getEfficiency`, `getMaxTransfer`), so the 12 tiered tile classes carry no logic of their own.

| Tier | Transfer per tick and per-cell buffer | Efficiency multiplier per tick | Flywheel energy per block | Loss of stored energy |
|---|---|---|---|---|
| LIGHT | 32 | 0.99990 | 9,600 | 0.01 percent per tick (about 0.2 percent per second) |
| MEDIUM | 192 | 0.99995 | 14,400 | 0.005 percent per tick |
| HEAVY | 1,024 | 0.99999 | 24,000 | 0.001 percent per tick |

Loss is `TileTorqueBase.applyPowerDrain(cell)`: `energy = energy * efficiency`, returning the negative delta. There is no per-hop transfer loss; loss happens only through stored energy decaying every tick.

### 3. Tick and transfer algorithm

All torque tiles are `ITickable`; logic runs on the server only. `TileTorqueBase` (333 lines) is the template.

1. Neighbour caches. `getTorqueCache()` lazily builds `ITorqueTile[6]` for sides where `canInputTorque || canOutputTorque` (`WorldTools.getTile(world, pos.offset(dir), ITorqueTile.class)`, skipped for unloaded chunks). A parallel `rfCache` holds RF neighbours. Both are cleared in `validate()`, `invalidate()`, `setPrimaryFacing`, and when the block gets `neighborChanged` or `onNeighborChange` (`BlockTorqueBase` forwards to `TileTorqueBase.onNeighborTileChanged`).
2. Push. `transferPowerTo(side)` takes the neighbour; if it is an `ITorqueTile` that `canInputTorque(side.getOpposite())`, it runs `drainTorque(side, neighbour.addTorque(opposite, getMaxTorqueOutput(side)))`: it offers the full output rate and drains only what the neighbour accepted. If the neighbour is not an `ITorqueTile`, it falls back to RF (section 5).
3. Statistics. `torqueIn`, `torqueOut`, `torqueLoss`, `prevEnergy` are display-only; right-clicking with an empty hand sends `guistrings.automation.torque.values` to the player.

Because transfer is push-based in tile tick order, a downstream tile that ticks before its source gets the energy one tick later. A cell buffer equals one tick of the tier's transfer rate.

**Cell layouts per class**

| Class | Cells | Output side | Input side(s) | Per-tick logic |
|---|---|---|---|---|
| `TileTorqueSingleCell` (base for shaft, windmill controller, generators, flywheel controller) | 1 | `orientation` | opposite of `orientation` | drain loss, push to front, update `clientDestEnergyState` |
| `TileTorqueShaft` | 1 | front | back | as single cell, plus the `prev`/`next` chain used for animation; chain links only same-class shafts |
| `TileTorqueSidedCell` (junction) | 6, one per side | `orientation` | every other side | `balanceStorage` moves all input energy into the output cell proportionally, loss on every cell, then push |
| `TileDistributor` | 6 | every side except the back | back (`orientation.getOpposite()`) | `balanceStorage` splits input among connected output cells by free capacity; `transferPower` pushes out of each non-input side |
| `TileFlywheelController` | input cell plus output cell plus the storage block below | front | back | section 4 |
| `TileHandCrankedGenerator` | input cell (in 32, cap 150) and output cell (out 32, cap 32) | front | none (UP side accepts crank energy via `addTorque`) | moves at most 2 per tick from input to output; loss on both |
| `TileStirlingGenerator` | 1 (in 0, out 4, cap 1600, medium efficiency) | front | none | burns fuel, +1 per burning tick |
| `TileWaterwheelGenerator` | 1 (in 0, out 4, cap 32, low efficiency) | front; the wheel itself is on the back side | none | +1 per tick while `validSetup` |
| `TileWindmillController` | 1 (32/32/32, low) | front | none | takes and zeroes the energy stored on the blade control tile behind it |

Behaviour worth preserving or fixing deliberately:

- `SidedTorqueCell` energy stays attached to the world side, not to the block orientation, so rotating a conduit (`rotateBlock`) turns an old output buffer into an input buffer (documented in the class comment, `ITorque.java`).
- `TileFlywheelController.getMaxTorqueOutput` returns 0 while the block is powered by redstone: redstone is the output switch. The tile checks `world.getStrongPower(pos) > 0` in `onNeighborTileChanged` and syncs it with block event 7.
- Generators add output with `setEnergy(energy + output)`. `TorqueCell.setEnergy` clamps to `maxEnergy`, so generators cannot overfill.
- `TileHandCrankedGenerator.addTorque` accepts energy from side UP or `null` only, which is how hammer and worker cranking enter; `canInputTorque` is false, so conduits never push into it.

### 4. Storage: flywheel multiblock

`BlockFlywheelStorage` and `TileFlywheelStorage` (279 lines).

- Shape validation (`validateSetup`): `BlockFinder` flood-fills all connected blocks of the same block and same metadata (so same tier) with radius 20, then requires the bounding box to be completely filled, width equal to length, and width 1 or 3. Height may be anything the flood fill reaches. The control tile is the base centre (the base block itself for 1-wide stacks). Invalid setups clear every member's `controllerPos`.
- Capacity: `maxEnergyStored = width * width * height * perBlockEnergy(tier)`. Example: 3x3x10 heavy = 90 x 24,000 = 2,160,000.
- Loss: on the control tile each tick, `torqueLoss = storedEnergy * (1 - eff) * 0.1`.
- Grid connection: the controller block must sit directly above a storage block. `TileFlywheelController.getControlledFlywheel` looks one block down, follows `controllerPos` to the control tile, and reads and writes its public fields `storedEnergy`, `maxEnergyStored`, `torqueLoss`, `rotation`. `balancePower` order: input to output, then storage to output, then input to storage.
- Updates: `blockPlaced` (from `onBlockPlacedBy`) and `blockBroken` (called after `breakBlock` so neighbours see the block gone) trigger `validateSetup` on neighbour tiles.
- Animation: speed is `maxRpm (100) * percentFull`; the controller renders the stack rotation by reading `storage.rotation`.
- Tier source: `getBlockMetadata()` selects efficiency and per-block capacity (switches in `getEfficiency` and `setAsController`). This must be rewritten when metadata disappears.
- Render bounds are hard-coded: `(x-1, y-1, z-1)` to `(x+2, y+setHeight, z+2)`. The block renders in solid and translucent layers.

### 5. Redstone Flux interop (exact behaviour)

- Library: `cofh:RedstoneFlux:1.12-2.0.2.3` (`gradle.properties` `rf_version`, `build.gradle:69` `deobfCompile`). Not shaded; soft dependency declared as `after:redstoneflux`.
- Interfaces: `TileTorqueBase` carries `@Optional.Interface(iface="cofh.redstoneflux.api.IEnergyReceiver", modid="redstoneflux", striprefs=true)` and four `@Optional.Method` implementations. `TileWorksiteBase` does the same for `IEnergyProvider` and `IEnergyReceiver` (outside this slice); `IWorkSite extends ITorqueTile`, so worksites connect to conduits as torque tiles, and RF is only an extra path.
- Receiving RF: `receiveEnergy(from, maxReceive, simulate)` returns 0 if `!canInputTorque(from)`. When simulating it returns `min(maxReceive, torqueToRf * getMaxTorqueInput(from))`. Otherwise it calls `addTorque(from, maxReceive * rfToTorque)` and returns the amount added scaled back to RF. `getEnergyStored` and `getMaxEnergyStored` multiply torque by `torqueToRf`. `canConnectEnergy` = `canOutputTorque || canInputTorque`. The torque tiles implement `IEnergyReceiver` only, never a provider.
- Sending RF: `RFProxyActual.transferPower` (loaded reflectively, only when `Loader.isModLoaded("redstoneflux")`, from `AncientWarfareAutomation.java:71`): if the neighbour is an `IEnergyReceiver`, call `receiveEnergy(from.getOpposite(), (int)(getMaxTorqueOutput * torqueToRf), false)` and drain the generator by `received * rfToTorque`. `isRFTile` = `instanceof IEnergyConnection`.
- Rate: 1 torque = 10 RF, set by `rf_energy_conversion_factor`; zero or negative zeroes both factors and disables RF. A heavy conduit's 1,024 torque per tick is 10,240 RF per tick.
- Visual connections: `TileTorqueSidedCell.buildConnections` marks a side connected when its neighbour is an RF tile and not a torque tile, so conduits visibly attach to RF machines.

Natural 1.21.1 replacement: NeoForge's built-in energy capability, `net.neoforged.neoforge.energy.IEnergyStorage` exposed through `Capabilities.EnergyStorage.BLOCK` and registered in `RegisterCapabilitiesEvent` (class names believed correct for 1.21.1; unverified here). Each torque block entity exposes an adapter converting at the configured ratio, and `transferPowerTo` queries `level.getCapability(...)` on the neighbour instead of using `RFProxy`. Redstone Flux itself has no 1.21.1 release; FE replaced it.

### 6. Generators

- Hand-cranked (`TileHandCrankedGenerator`, 286 lines): implements `IWorkSite`, so an NPC worker with work type CRAFTING cranks it (`addEnergyFromWorker` adds `energyPerWorkUnit (50) * effectiveness * hand_cranked_generator_output`). Players crank it with `ItemHammer` (`core/item/ItemHammer.java:159` calls `addEnergyFromPlayer`). `hasWork()` is true while the input cell is not full. Upgrade methods are no-ops. The owner is stored through `Owner`. The crank animation is a separate channel keyed to side UP.
- Stirling (`TileStirlingGenerator`, 115 lines): one-slot `ItemStackHandler` accepting only items with `TileEntityFurnace.getItemBurnTime > 0`, exposed through the item-handler capability on every side, contents dropped through `IBlockBreakHandler`. Output 1.0 per burning tick; a new fuel item is consumed only when burn time is 0 and the cell is below max. NBT: `burnTicks`, `burnTicksBase`, `inventory`.
- Waterwheel (`TileWaterwheelGenerator`, 152 lines): every 20 ticks the server runs `validateBlocks`. It classifies cells as air (1), water (2) or other (0) in the 2-wide, 3-tall channel in front of the wheel and requires water flowing down one side with air on the other, or water below; rotation direction (+1, -1, 0) comes from the `BlockLiquid.LEVEL` of the two lower cells. `validSetup` and `rotationDirection` are sent in the update NBT and the client spins the wheel at 20 RPM. Output is a flat 1.0 per tick whenever valid, independent of flow. Render bounding box is 7x7x7.
- Windmill: section 7.

### 7. Windmill multiblock

`BlockWindmillBlade` has no state of its own; `TileWindmillBlade` (310 lines) does everything.

- Shape: blades of the same block form a flat square in a vertical plane. Valid when the height is odd and at least 5, one horizontal extent is 1, the other equals the height, and the box is full (`validateSetup`). A code comment says "<= 17", but no upper bound is enforced beyond the flood-fill radius of 20 in `BlockFinder`. The control tile is the centre cell. `windmillDirection` is WEST if the X size is 1, otherwise NORTH (axis only).
- Output: the control tile sets `energy = windmillSize * windmill_per_size_output` every server tick. `TileWindmillController`, which must be directly behind the control blade, takes that energy and zeroes it. A size-5 windmill therefore yields 5 torque per tick, size 9 yields 9, capped by the controller cell at 32 per tick. There is no wind, weather, altitude or obstruction check.
- Teardown: breaking the control tile clears every member of the square; breaking another member makes the control tile revalidate; breaking an unformed blade asks neighbours to revalidate.
- Client: the update NBT carries `controlPos`, `isControl`, `size`, `direction`. Only the control tile renders (`shouldRenderInPass` is false for members) and its render bounding box grows with size.

### 8. Client animation state sync

Animation state is derived from a small integer, not from stored energy.

1. Server: every `energyMinNetworkUpdateFrequency` (5) ticks `serverNetworkSynch` computes `percent = max(storedPercent, outputPercent)` as an int 0-100. If it changed, `sendSideRotation(side, percent)` sends a `PacketBlockEvent` (`type` = side ordinal, `data` = percent) to everyone tracking the chunk.
2. Client: `receiveClientEvent(a, b)` (reached through `Block.eventReceived`, which `BlockTorqueBase` forwards to `WorldTools.sendClientEventToTile`) calls `handleClientRotationData`. The tile stores `clientDestEnergyState` and restarts a 5-tick lerp.
3. Each client tick `clientNetworkUpdate` lerps `clientEnergyState` toward the target, and `updateRotation` does `rotation -= low_rpt * clientEnergyState * 0.01` (degrees converted to radians) keeping `lastRotationDiff` for partial ticks. `getClientOutputRotation(side, delta)` returns `rotation - lastRotationDiff * (1 - delta)`.
4. Shaft chains: only the head shaft (`prev() == null`) sends and rotates. It averages the stored energy of the chain, uses the last shaft's output, and copies its `rotation` onto every `next` shaft each tick, so the axle spins as one piece.
5. `useOutputRotation` and `getClientOutputRotation` let a neighbour's renderer read this tile's output rotation for the matching input stub, so connected machines spin in phase (consumed in `automation/render`, not read in depth).
6. Late joiners: `writeUpdateNBT` and `handleUpdateNBT` carry `orientation`, `clientEnergy`, `powered`, `validSetup` and the multiblock fields.
7. Flywheel storage (event type 1) and the hand-crank input (side UP) have their own percent channels.

Render input is passed through unlisted blockstate properties declared in `render/property/AutomationProperties.java`: `ACTIVE`, `DYNAMIC`, `ROTATIONS[6]` (a float per side), `USE_INPUT`, `INPUT_ROTATION`, `IS_CONTROL`, `HEIGHT`, `WIDTH`, `ROTATION` (14 property objects counting the six rotation entries), and `CONNECTIONS[6]` (`connection_<SIDE>`) in `BlockTorqueTransportSided`, which adds 6 more. `UNLISTED_FACING`, `FLYWHEEL_ROTATION`, `HAS_PREVIOUS` and `HAS_NEXT` were not found in that file (location unverified). `TIER` in the same file is a real block-state property. None of it is real block state; a port should treat it as block entity renderer input.

### 9. Block-class conventions

- Base: `BlockBaseAutomation` sets the Automation creative tab, calls `proxy.addClientRegister(this)` and a state mapper that points at `assets/ancientwarfare/automation/<regname>` (Core modid for assets, Automation modid for registry ids).
- Rotation: `IRotatableBlock` has `getRotationType()` (FOUR_WAY, SIX_WAY, NONE) and `invertFacing()`. `ItemBlockTorqueTile.placeBlockAt` writes the facing into the tile through `BlockRotationHandler.getFaceForPlacement`: the player's horizontal facing, or the clicked face if the block is SIX_WAY and the face is on the Y axis, then inverted if `invertFacing()`. Shaft, junction, distributor are SIX_WAY; generators, controllers, mailbox are FOUR_WAY. Torque facing is never in the blockstate, only in the tile (NBT `orientation`).
- `BlockTorqueBase.rotateBlock` (wrench): FOUR_WAY blocks rotate around Y only, SIX_WAY around any axis.
- Items: `getSubBlocks` returns meta 0, 1, 2; `damageDropped` returns meta; item names are `<unlocalized>.<meta>`.
- Shape: `BlockTorqueTransport.getBoundingBox` builds a 0.1875-0.8125 core and stretches each axis to the block edge when the tile reports a connection on that side (`getConnections`); the shaft stretches along its facing axis.
- Worksite shell: `BlockWorksiteBase` has real `facing` and `active` properties but metadata 0 only; `getActualState` reads both from the tile. It is built per worksite with a `Supplier<TileEntity>`. Right-click works only if the player is owner, same team, or friend (`IOwnable`, `IWorkSite.getOwner().isOwnerOrSameTeamOrFriend`). Wrench rotation is about Y only.
- Redstone: `BlockWarehouseStockLinker` is the only emitter (weak power 15; strong power on its facing side) driven by the tile's `getEqualityHandle`. `lit` is set by `setActiveState`. The linked warehouse position travels in item NBT written by `ItemBlockWarehouseStockLinker` and is restored in `onBlockPlacedBy`.
- Chunk loaders: `BlockChunkLoaderSimple` requests a chunk ticket on placement (`TileChunkLoaderSimple.setupInitialTicket`) and releases it in `breakBlock`.

## Dependencies

**Other Ancient Warfare modules (Core):** `core.block.BlockBase`, `BlockRotationHandler` (62 references from Automation per STATS), `BlockTools`, `CoreProperties.UNLISTED_FACING / UNLISTED_HORIZONTAL_FACING`, `core.tile.TileUpdatable` (update NBT mechanism), `core.network.NetworkHandler` and `PacketBlockEvent`, `core.util.WorldTools`, `BlockFinder`, `InventoryTools`, `Trig`, `core.interfaces.IWorkSite`, `IWorker`, `IInteractableTile`, `IBlockBreakHandler`, `ITorque`, `Owner`, `AWCoreStatics.energyPerWorkUnit`, `core.item.ItemBlockBase`, `ItemBlockMeta`, `ItemHammer`; `core.tile.TileResearchStation` is also an `ITorqueTile`.

**Within Automation but outside this slice:** `AWAutomationStatics`, `AncientWarfareAutomation` (proxy, tab, log), `RFProxy`/`RFProxyActual`, `ItemBlockTorqueTile`, all `render/*` bakery models, `ContainerStirlingGenerator`, `GuiStirlingGenerator`, `TileWorksiteBase`, `WorkSite*`, `TileWarehouse*`, `TileMailbox`, `TileAutoCrafting`, `TileChunkLoader*`, `WorksiteUpgrade`.

**Other modules referencing this slice:** Structures references `BlockFlywheelController`, `BlockFlywheelStorage`, `BlockHandCrankedGenerator`, `BlockTorqueDistributor`, `BlockTorqueGenerator` (STATS line 300 header says 3 classes but names five targets, one each). NPC workers drive generators and worksites through `IWorkSite`/`IWorker`.

**Third-party mods:** Redstone Flux (optional, `@Optional` plus reflective proxy). CodeChickenLib bakery classes (`CCBakeryModel`, `ModelRegistryHelper`, `IBakery`, `IBakeryProvider`, `BlockStateKeyGenerator`) are imported directly by every animated block class. Dependency string also names `buildcraftcore` but nothing here uses it.

**Minecraft and Forge APIs that no longer exist in 1.21.1:** block metadata (`getStateFromMeta`, `getMetaFromState`, `damageDropped`, `getSubBlocks`), `IExtendedBlockState`/`IUnlistedProperty`, `ModelLoader.setCustomStateMapper`, `ModelBakery.registerBlockKeyGenerator`, `isOpaqueCube/isNormalCube/isFullCube/isSideSolid/getBlockFaceShape`, `Material`, `ITickable`, `readFromNBT/writeToNBT`, `getRenderBoundingBox`, `shouldRenderInPass`, `hasFastRenderer`, `GameRegistry.registerTileEntity`, `@ObjectHolder` with `InjectionTools.nullValue()`, `RegistryEvent.Register`, `receiveClientEvent` with `eventReceived`, the `Capability` objects (`CapabilityItemHandler.ITEM_HANDLER_CAPABILITY`), `ForgeChunkManager` tickets, `@SideOnly`, `Loader.isModLoaded`, `@Optional`, `TileEntityFurnace.getItemBurnTime`, `EnumFacing`, `BlockLiquid.LEVEL`, `IStringSerializable`.

## Porting notes (1.21.1 NeoForge)

Ranked hardest first.

1. **Animated machines and their rendering (flywheel storage, windmill, waterwheel, shafts, conduits, stirling, hand crank)** - Effort: XL.
   The 1.12 code renders through a CodeChickenLib bakery with about 14 unlisted properties (plus 6 connection properties) and `.m2f` models, recomputing rotation per frame as float properties on a baked model. None of that exists in 1.21.1, and most of each tiered block class (`registerClient`) is this glue. Approach: one block entity per type plus a client-only block entity renderer reading the interpolated rotation from the block entity and drawing static baked models with a separate rotating part. `.m2f` is a custom format and cannot be loaded; the OBJ files in `models/block/automation/` or re-exported JSON models are the starting point. Static parts become normal block models with datagen blockstates. The renderer classes themselves (1,612 lines) sit in another slice, so this item covers only what this slice's blocks need. Owner decision: full animation or static models first.

2. **Torque energy network** - Effort: L (about 2,000 lines of tile logic: `TileTorqueBase` 333, `SingleCell` 179, `SidedCell` 285, `Shaft` 127, `Distributor` 74, `FlywheelController` 215, generators about 550, `ITorque` 190).
   The logic ports well: plain doubles, minimal Minecraft coupling (neighbour lookup, tick, NBT). Work items: neighbour cache invalidation via block `neighborChanged` plus block entity load events; `EnumFacing` becomes `Direction`; `TorqueCell` and `SidedTorqueCell` stay as POJOs; NBT moves to the 1.21 `saveAdditional`/`loadAdditional` pair, which takes a registry-lookup argument (exact signature unverified); client sync moves from `PacketBlockEvent` to the block entity update packet (`getUpdateTag`/`getUpdatePacket`) or block events. Keep the algorithm and constants unchanged because balance depends on tick order and loss factors. Replace the `input.getClass() == this.getClass()` shaft chain test with a tier or block comparison.

3. **Redstone Flux to NeoForge FE** - Effort: S.
   Delete `RFProxy`, `RFProxyActual`, the `@Optional` annotations and `Loader` checks. Register an energy block capability on each torque block entity type; the adapter implements `IEnergyStorage` as `TileTorqueBase.receiveEnergy` does, with `extractEnergy` returning 0 and `canReceive` from `canInputTorque`. For output, ask the neighbour for its energy capability in `transferPowerTo` when it is not a torque tile. Keep the 10:1 ratio and its config key (rename to FE). `canConnectEnergy` disappears (a null capability replaces it), so conduit connection logic must test capability presence.

4. **Variant flattening (metadata to separate blocks)** - Effort: M.
   38 blocks versus 26 registry names. Suggested names: `torque_shaft_light|medium|heavy`, same for junction, distributor, flywheel controller, flywheel storage, plus `warehouse_storage_small|medium|large`, all sharing one class parameterised by `TorqueTier` or `Size`. `TileFlywheelStorage` tier switches and `BlockFinder`'s `getMetaFromState` comparison become block-identity checks. `getSubBlocks` becomes creative tab `displayItems`. If conduits should show connections in the blockstate (vanilla pipe style), add six boolean properties; the current game never exposes them. Note the heavy flywheel tile id `flywheel_large_tile` when planning migration.

5. **Multiblock validators (flywheel stack, windmill plane, waterwheel channel)** - Effort: M.
   Logic ports as is. Rewrite `BlockFinder` without metadata and without its O(n^2) `List.contains` and unbounded recursion (a radius-20 flood fill recurses thousands deep and can overflow the stack; use iterative BFS with a `HashSet`). Replace `Material.WATER` checks with fluid-state checks. Move validation to the place and remove block hooks (names changed in 1.21; exact names unverified). The waterwheel's 20-tick poll can stay.

6. **Stirling generator, hand crank and NPC integration** - Effort: S to M.
   Item handler capability via NeoForge's `ItemStackHandler` and `RegisterCapabilitiesEvent`; menu and screen replace `ContainerStirlingGenerator` and `GuiStirlingGenerator`; fuel time comes from the NeoForge fuel mechanism (exact method unverified). `IWorkSite extends ITorqueTile` is what lets NPC workers drive generators and worksites; it must be ported with the NPC module, or the hand crank is player-only (`ItemHammer.addEnergyFromPlayer`) at first.

7. **Worksite shell block and its seven registrations** - Effort: S.
   One class with a block entity supplier; `EntityBlock`-style creation. `active` and `facing` become real properties that the block entity updates. Keep the owner/team gate.

8. **Chunk loaders** - Effort: M (block side is small).
   `ForgeChunkManager` tickets were removed; NeoForge has a ticket controller mechanism (`TicketController`, believed present; unverified). Tile logic belongs to another slice.

9. **Mailbox, warehouse family, auto crafting, stock linker and viewer** - Effort: S to M (about 900 lines of block code).
   Hard-coded AABB maps become `VoxelShape` maps keyed by `Direction`; opacity flags become `noOcclusion()` and shape methods; `rotateBlock` becomes the block rotation hook or a wrench item; redstone emission uses the signal methods; `onBlockActivated` becomes the 1.21 use methods (exact names unverified).

10. **Saved data compatibility** - Effort: S.
    Tile NBT keys in use, if a migration is wanted: `orientation`, `torqueEnergy`, `clientEnergy`, `energyList` (6 compounds with `energy`), `torqueEnergyIn`, `powered`, `inputEnergy`, `clientInputEnergy`, `burnTicks`, `burnTicksBase`, `inventory`, `controllerPos` (long), `isControl`, `setWidth`, `setHeight`, `maxEnergy`, `storedEnergy`, `controlPos`, `direction`, `size`, `validSetup`, `rotationDirection`, owner tags.

**Cannot be ported as is:** the bakery and unlisted-property rendering path (use block entity renderers and models); `.m2f` models (convert or replace); Redstone Flux interfaces (use the FE capability); block metadata (use separate blocks); Forge chunk tickets (use NeoForge tickets); `Material` checks (use fluid tags and states).

## Candidates for capttrackerlib

Generic and reusable by an unrelated mod:

- **Tick-capped energy buffer and side-aware buffer** (`TorqueCell`, `SidedTorqueCell`, about 150 lines): unit-free, no Minecraft dependence beyond the facing enum and NBT. Any mod wanting an internal energy model next to FE can use it, ideally with a built-in `IEnergyStorage` adapter.
- **FE bridge helpers**: "wrap an internal unit at ratio X as `IEnergyStorage`" and "push energy to neighbouring energy capabilities" (what `transferPowerTo` plus `RFProxy` do). Every generator or machine mod needs these.
- **Iterative multiblock finder** (flood fill, box fill, bounding box, with block predicates instead of block plus meta): reusable for tanks, silos, towers.
- **Controller-member multiblock block entity pattern** (`controllerPos`, `isControl`, revalidate neighbours on place and break, sync to client), used by the flywheel and windmill.
- **Neighbour-capability cache** (array of 6 neighbours invalidated on neighbour change).
- **Facing-from-placement helper** (FOUR_WAY / SIX_WAY / invert semantics of `BlockRotationHandler.getFaceForPlacement`), only if the lib lacks an equivalent.

Looks generic but should stay inside Ancient Warfare:

- Tier constants and the torque unit: tuned against `energyPerWorkUnit = 50` and NPC worker rates.
- Windmill, waterwheel, flywheel and hand-crank rules (flat output, no wind model).
- `BlockWorksiteBase` owner and team gating: depends on AW's `Owner`, teams and friend list.
- `IWorkSite extends ITorqueTile`: couples NPC work to energy and belongs with the NPC and Automation port.

## Open questions

1. `BlockOreProcessor` extends `BlockWorksiteBase` and creates `TileOreProcessor`, but is not registered in `AWAutomationBlocks` and is not used by any other class (searched SRC). Confirm it is abandoned and should not be ported.
2. Torque or FE internally: keep torque (preserves the 32/192/1024 balance, `energyPerWorkUnit`, NPC rates) with FE only at the edges, or convert everything to FE. This decides whether `ITorque` stays in Core or moves.
3. Is migration of 1.12.2 worlds and items wanted? If not, tile ids (`flywheel_large_tile`, `torque_*_tile`) and the meta-tier item scheme can be cleaned up freely.
4. Animation scope: full animated rendering through block entity renderers, or static models first. The `.m2f` assets cannot be used directly; the owner must decide whether to re-export them.
5. Transfer order: throughput through a conduit chain depends on tile tick order. Preserve that, or replace it with a proper graph transfer that changes balance?
6. Behaviours I did not resolve: (a) the windmill maximum size is not enforced in code despite a comment saying 17; (b) `enable_server_energy_network` is honoured only by `TileFlywheelStorage`, and `enable_energy_client_updates` (key `enable_client_energy_animations`) and the three `*_quality_conduit_energy_max` options are never read anywhere in SRC; (c) `TileFlywheelController.balancePower` adds `storage.torqueLoss` again after `applyDrainToStorage` already added it, which looks like a double count of a display-only statistic; (d) `TileWindmillBlade.setEnergy(int)` takes an int while `energy` is a double. I did not read the renderers in depth, so how each uses `ROTATIONS` is inferred from `TorqueAnimationRenderer` and `BaseTorqueRenderer` only.
7. The exact class-by-class match of STATS to source was checked by count only (26 and 28 files including the `package-info` entries), not name by name; inner and anonymous classes (for example the fuel handler in `TileStirlingGenerator`) were not enumerated.
8. The chunk loader block classes are here but their tiles and ticket logic belong to another slice: confirm which slice's plan covers the chunk ticket replacement. Confirm that the `buildcraftcore` dependency string is a leftover and no MJ support is wanted.

## Verification

Second-pass check of this slice (automation torque energy system and block classes), done from source greps and reads of the listed files.

**Enumerations re-derived**
- Blocks: `init/AWAutomationBlocks.register` has 26 `registry.register(new ...)` calls with the names and classes in the table. `BlockOreProcessor` is not registered, and no file other than its own mentions it. Confirmed dead code.
- ItemBlocks: `registerItemBlocks` registers 26 items. Shafts, junctions, distributors, flywheel controllers, both generators types listed and the windmill generator use `ItemBlockTorqueTile`; flywheel storage uses `ItemBlockMeta`. Confirmed.
- Tile entities: 18 concrete classes are registered with the ids in the table (3 shafts, 3 junctions, 3 distributors, 3 flywheel controllers including `flywheel_large_tile`, flywheel storage, hand crank, stirling, waterwheel, windmill blade, windmill controller). Six abstract bases confirmed. `tile/torque` holds 22 top-level classes, 2 in `multiblock/`, and 2 `package-info`, so 24 real classes.
- Block package: 26 block classes plus the `TorqueTier` enum, plus `package-info`.
- GUI ids referenced from block classes: 9, matching the report's list.
- Renderers: `automation/render` has 26 files plus `package-info`, 1,612 lines.
- Research recipes: 20 files.
- Blockstate JSONs: 18 files (16 for blocks, plus `component.json` and `worksite_upgrade.json`). The report said 17; corrected.
- Models: 12 `.m2f` files (the report listed 8); 14 files under `models/block/automation/`. Corrected.
- Items, containers, packets, entities, AI tasks: none defined in this slice. Confirmed.
- Config keys: all read keys match the report, except the conduit and client-animation keys, which are corrected.

**Claims checked against source**
1. `TorqueTier` LIGHT=0, MEDIUM=1, HEAVY=2, with lowercase `getName()`.
2. Efficiency 0.99990/0.99995/0.99999, transfer 32/192/1024, flywheel storage 9600/14400/24000 (`AWAutomationStatics`).
3. `torqueToRf = 10`; zero or negative disables RF.
4. RF proxy is loaded only when `Loader.isModLoaded("redstoneflux")`, via `Class.forName` in `RFProxy.loadInstance`, called from `AncientWarfareAutomation.java`.
5. `@Optional` set: `TileTorqueBase` has one interface and four methods; `TileWorksiteBase` has IEnergyProvider and IEnergyReceiver.
6. `energy_network_update_frequency` default 5; `enable_server_energy_network` is read only in `TileFlywheelStorage`.
7. Hand crank cells (input 32 in, cap 150; output 32 out, cap 32), stirling (0/4/1600, medium), waterwheel (0/4/32, low).
8. Hand crank adds `energyPerWorkUnit * effectiveness * output`; `energyPerWorkUnit = 50` in `AWCoreStatics`.
9. `ItemHammer` calls `addEnergyFromPlayer` at line 159.
10. `IWorkSite extends ITorqueTile, IBlockBreakHandler, IOwnable`; `TileResearchStation` also implements `ITorqueTile`.
11. Shaft chain links only same-class shafts (`input.getClass() == this.getClass()`).
12. Flywheel controller returns 0 output while powered.
13. Windmill controller zeroes the blade's energy; the `<= 17` comment exists.
14. `BlockFinder` has `maxRadius = 20` and a recursive `getConnectedBlocks`.
15. Windmill blade flammability 20, fire spread 60.
16. Stock linker: weak power 15, strong power on the facing side, `lit` property.
17. Waterwheel `valid_setup` property; warehouse storage `size` property backed by a `Size` enum.
18. Client sync interval `energyMinNetworkUpdateFrequency = 5`; `rpmToRpt`, `low_quality_rpm = 100`, `low_rpt`.
19. `after:buildcraftcore` at `AncientWarfareAutomation.java:53`.

**Corrected in this pass**
- Torque flattening: 5 registry names become 15 blocks (the report said 10 names become 20).
- Junction tile classes are concrete and extend `TileTorqueSidedCell` directly, not through an abstract class.
- Model file lists and blockstate count.
- Conduit max keys and `enable_energy_client_updates` (key `enable_client_energy_animations`): no code in SRC reads them.
- Unlisted properties: `UNLISTED_FACING`, `FLYWHEEL_ROTATION`, `HAS_PREVIOUS` and `HAS_NEXT` were not found in `AutomationProperties.java`; the count is 14 in that file plus 6 connection properties.
- STATS line 300 says 3 classes but lists five names; noted in Dependencies.

**Modern API names (NeoForge 1.21.1)**
- No 1.21.1 jar is in the tree, so none of the 1.21.1 names were checked against a binary. These stay as the report's claims, and all of them should be read as unverified: `net.neoforged.neoforge.energy.IEnergyStorage`, `Capabilities.EnergyStorage.BLOCK`, `RegisterCapabilitiesEvent`, `saveAdditional`/`loadAdditional` with a registry-lookup argument, `TicketController`, `getUpdateTag`/`getUpdatePacket`, `noOcclusion()`, `displayItems`.
- The 1.12 APIs the report says are gone (`ITickable`, `IExtendedBlockState`, `IUnlistedProperty`, `ForgeChunkManager`, `Loader.isModLoaded`, `@Optional`, `GameRegistry.registerTileEntity`, `receiveClientEvent`) all appear in this source, so they are correct as 1.12 names.

**Could not settle**
- Open question 6(c), the flywheel `torqueLoss` double count: `TileFlywheelController` line 77 adds `storage.torqueLoss` after `applyDrainToStorage`, but the full call path was not traced.
- Waterwheel 20-tick period: `validateBlocks()` is called around line 40; the timing test was not traced.
- Windmill controller cell capacity `max` was not traced to its config value.
- `build.gradle:69` and the RF version in `gradle.properties` were not checked (outside SRC).
- Renderer internals, the stirling fuel handler, and `TileWorksiteBase` belong to other slices and were not read.
- STATS counts were checked by file count, not class by class.
