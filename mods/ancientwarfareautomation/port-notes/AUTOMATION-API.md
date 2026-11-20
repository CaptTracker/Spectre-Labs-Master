# Ancient Warfare Automation: ported API

Written by the port workflow. What each finished slice offers to code that builds on it.

## automation-torque

Ported the whole torque grid: TileTorqueBase and every subclass, per-side cells, tiered 32/192/1024 transfer, losses, flywheel multiblock, the four generators with iterative BlockPos-set validators, and the 20 blocks (tiers flattened, facing as a block-state property). Animation syncs through block entity update packets; Redstone Flux became an FE capability. Copied textures and OBJ models, plus blockstates, placeholder models, loot tables, 20 converted research recipes and lang. Nothing compiled; the registry entries and renderers still have to be wired.

**API**

- block.torque.TorqueTier (LIGHT/MEDIUM/HEAVY; getEfficiency, getMaxTransfer, getStorageEnergyMax)
- block.torque.BlockTorqueBase.defaultProperties(), getFacing(BlockState), withFacing(BlockState, Direction); block classes take (Properties, [TorqueTier], Supplier<BlockEntityType<?>>)
- block.torque.BlockTorqueTransportShaft / BlockTorqueJunction / BlockTorqueDistributor / BlockFlywheelController / BlockFlywheelStorage / BlockHandCrankedGenerator / BlockStirlingGenerator / BlockWaterwheelGenerator (VALID_SETUP) / BlockWindmillGenerator / BlockWindmillBlade
- block.torque.IBlockEntityTypeProvider.typeOf(BlockState) and ITieredBlock.getTier()
- block.entity.torque.TileTorqueBase: getTorqueCache, getEnergyStorage(side), getPrimaryFacing/setPrimaryFacing, update(), getRenderBounds(), onNeighborTileChanged()
- block.entity.torque.TileTorqueSidedCell.getConnections()/isConnected(side); TileTorqueShaft.hasPrevious()/hasNext()
- block.entity.torque.TileHandCrankedGenerator (IWorkSite: addEnergyFromPlayer/addEnergyFromWorker, getCrankRotation)
- block.entity.torque.TileStirlingGenerator.setMenuFactory(MenuFactory), getFuelHandler(), getBurnTime(), getBurnTimeBase(), getFuelBurnTime(stack)
- block.entity.torque.TileWaterwheelGenerator (wheelRotation, isValidSetup, getRotationDirection)
- block.entity.torque.TileFlywheelController (getFlywheelRotation, isPowered)
- block.entity.torque.multiblock.TileFlywheelStorage (controllerPos, isControl, storedEnergy, maxEnergyStored, getRotation, blockPlaced/blockBroken)
- block.entity.torque.multiblock.TileWindmillBlade (isControl, getWindmillSize, getDirection, getRotation, getEnergy/setEnergy)
- block.entity.torque.multiblock.MultiblockScan (floodFill, cross, isBoxFilled, boxPositions)
- block.entity.torque.TorqueCapabilities.register(event, type) and registerStirling(event, type)
- block.entity.torque.TorqueNetwork.sendEvent(blockEntity, type, data)

**Not ported**

- CodeChickenLib bakery rendering: registerClient, getBakery, getExtendedState, key generators, AutomationProperties unlisted properties, connection properties (renderer slice)
- .m2f models (OBJ copies provided instead)
- BlockBaseAutomation, ItemBlockTorqueTile, RFProxy/RFProxyActual and @Optional RF interfaces (registrar, block placement hooks and FE capability replace them)
- Metadata hooks (getSubBlocks, damageDropped, getMetaFromState) and render/opacity overrides (noOcclusion plus a support shape for the flywheel controller)
- Stirling generator menu and screen (only the opening hook exists)
- Validation of blades/flywheel blocks placed by anything that does not call setPlacedBy (same as the original onBlockPlacedBy)

## automation-base

Ported the Automation module base, uncompiled: 25 Java files (4,454 lines). AwAutomationBase registers 38 blocks, 11 upgrade items and 34 block entity types. It uses the torque slice's real classes and assumes names for the warehouse and concrete worksite classes. The worksite base classes use block-state facing/active, NeoForge energy capability and ticket-based chunk loading. Also: config specs, mailbox saved data, registries, items, command, F8 key, converted assets, loot tables, tags, recipes and registry JSON. Notes are in port-notes/automation-base.md and .lang.json.

**API**

- io.github.capttracker.ancientwarfareautomation.init.AwAutomationBase: every block, item and block entity holder, UPGRADE_ITEMS, WAREHOUSE_STORAGES, WAREHOUSE_POS, TAB, upgradeItemName(); load(), registerEvents(modBus)
- ...init.WarehouseSize: SMALL, MEDIUM, LARGE, the constructor argument of WarehouseStorageBlock
- ...block.entity.worksite.WorksiteBlockEntity (was TileWorksiteBase): ticker(Level), getNextAction, processAction, updateWorksite, getFortune, isActive, getEnergyStorage(side)
- ...block.entity.worksite.BoundedWorksiteBlockEntity (TileWorksiteBounded): bounds, isInBounds, validateCollection, onBoundsSet, chunkLoadWorkBounds, forceChunk, unforceChunk, getRenderBounds
- ...block.entity.worksite.InventoryWorksiteBlockEntity (TileWorksiteBoundedInventory): mainInventory, setSideInventory, setInventorySideMappings, getItemHandler(Direction), openAltGui
- ...block.entity.worksite.FarmWorksiteBlockEntity (TileWorksiteFarm): plantableInventory, miscInventory, harvestBlock, fertilize, tryPlace, target map, counts
- ...block.entity.worksite.WorksiteProperties: FACING and ACTIVE, which worksite blocks must declare
- ...block.entity.worksite.WorksiteTools and TorqueEnergyStorage (forTile, transferPower)
- ...config.AWAutomationStatics: static option fields with the original names, register(modBus, container), toggleRenderWorkBounds()
- ...gamedata.MailboxData (get, onServerTick, addMailbox, deleteMailbox, getPublicBoxNames, getPrivateBoxNames) and MailboxBlockEntity (ticker(Level), getItemHandler(side), send/received inventories)
- ...chunkloader.ChunkLoaderSimpleBlockEntity (setupInitialTicket, releaseTicket) and ChunkLoaderDeluxeBlockEntity (addOrRemoveChunk, getForcedChunks, Viewer)
- ...chunkloader.AWChunkLoader.CONTROLLER and ChunkTicketSet
- ...registry.CropFarmRegistry, FruitFarmRegistry and TreeFarmRegistry: static lookups plus registerCrop(ICrop) for compat
- ...item.WorksiteUpgradeItem.getStack(WorksiteUpgrade), WorksiteBlockItem and WarehouseStockLinkerBlockItem.getWarehousePos(stack)
- ...command.WarehouseCommand.setDebugger(IWarehouseDebugger)

**Not ported**

- AgriCraft compat (CropAgricraftCrop, AgricraftCompat): dropped; BuildCraft was only a dependency string
- RFProxy and RFProxyActual: replaced by the Forge Energy capability (TorqueEnergyStorage); torque tiles use the torque slice's own adapter
- The 32 compat registry JSON files for 1.12.2 mods: not ported; the vanilla registry files are rewritten for 1.21.1 block names
- ItemMapDataWalker, the data fixer and 1.12 world migration; dead TileOreProcessor and BlockOreProcessor; the proxy classes, SidedProxy and the config-changed handler
- The 21 registerContainer calls (menu types belong to the GUI slice) and the client proxy's texture stitching and renderer bindings (render slices)
- Torque block models, item models and obj/m2f machine models (torque slice); BlockEntity getRenderBoundingBox has no 1.21.1 hook, getRenderBounds() is provided for the renderer
- setLightOpacity(1) of the torque transport blocks (no 1.21.1 block property)

## automation-warehouse

Ported the warehouse multiblock, uncompiled: control block entity (item cache, storage routing, interface fill/empty actions, crafting item handler), controlled-block lifecycle, storage (3 sizes, one type), interface, crafting station, stock viewer and linker, filters, debugger, six blocks including the mailbox block (mailbox block entity and data came with the base slice). Menu opening goes through factory hooks for the GUI slice. Notes and lang JSON written. No new assets were needed.

**API**

- block.entity.warehouse.WarehouseBlockEntity: handleSlotClick, tryAdd, getItems, getCountOf, changeCachedQuantity, getItemHandler, addViewer, addCraftingViewer (Viewer), getSortType/Order, getMaxStorage
- block.entity.warehouse.WarehouseMenus: setControlFactory, setStorageFactory, setInterfaceFactory, setCraftingStationFactory, setStockViewerFactory, setStockLinkerFactory, setMailboxFactory, open*
- block.entity.warehouse.WarehouseStorageBlockEntity: getFilters/setFilters, handleSlotClick, addViewer (Viewer), getStorageAdditionSize, addItems
- block.entity.warehouse.WarehouseInterfaceBlockEntity: getInventory, getFilters/setFilters, addViewer (Viewer), recalcRequests
- block.entity.warehouse.WarehouseStockViewerBlockEntity and WarehouseStockLinkerBlockEntity: getFilters/setFilters, WarehouseStockFilter, EqualitySignType, addViewer (Viewer), getEqualityHandle
- block.entity.warehouse.WarehouseCraftingStationBlockEntity: craftingRecipeMemory, getWarehouse()
- block.entity.warehouse.WarehouseSetup.registerEvents(IEventBus)
- block.entity.warehouse.WarehouseDebugger.COMMAND
- block.entity.warehouse.ControlledBlockEntity.ticker(Level), IControlledTile, IControllerTile
- block.entity.warehouse.WarehouseStorageFilter and WarehouseInterfaceFilter (INBTSerializable, getFilterItem/Quantity)
- block.warehouse: WarehouseStorageBlock(Properties, WarehouseSize), WarehouseInterfaceBlock, WarehouseCraftingStationBlock, WarehouseStockViewerBlock, WarehouseStockLinkerBlock (setActiveState, LIT), MailboxBlock

**Not ported**

- Menus and screens (ContainerWarehouse*, GuiWarehouse*, ContainerMailbox): GUI slice, hooks provided in WarehouseMenus
- Block entity renderers for stock viewer and linker: client slice
- Ore dictionary matching in interface filters (relaxed match is now same item and components, damage ignored)
- Block event forwarding (eventReceived/sendClientEventToTile) in the storage, viewer and linker blocks: no tile uses it
- WarehouseDebuggerData as SavedData (it never persisted; now in memory)
- IWarehouseStorageTile interface (single implementer, dropped); TileWarehouseBase merged into WarehouseBlockEntity
- Old *_tile ids / tile id fixer, JEI recipe transfer for the crafting station
- Mailbox block entity and MailboxData: already ported by the base slice, reviewed against TileMailbox, unchanged

## automation-worksites

Ported all concrete worksites, uncompiled: 37 Java files (3,952 lines) plus notes and lang json. These are the quarry, crop, fruit, tree, animal and fish farms and the auto crafting table, with their handler classes, the shared worksite block and the auto crafting block. Two known bugs are fixed: efficiency upgrades now scale cost (WorksiteActions), and the animal farm drop handler is registered through WorksiteEvents. Other small bugs are fixed and listed in the notes. Crops, fruit, trees, quarry tiers and fishing loot use 1.21 blocks, tags and loot tables. Menus open through a new WorksiteMenus hook the GUI slice must fill.

**API**

- block.worksite.WorksiteBlock(Properties, Supplier<? extends BlockEntityType<?>>) and block.worksite.AutoCraftingBlock(Properties)
- block.entity.worksite.QuarryBlockEntity, AnimalFarmBlockEntity, FishFarmBlockEntity, AutoCraftingBlockEntity, each with a public (BlockPos, BlockState) constructor
- block.entity.worksite.cropfarm.CropFarmBlockEntity, fruitfarm.FruitFarmBlockEntity, treefarm.TreeFarmBlockEntity, each with a public (BlockPos, BlockState) constructor
- block.entity.worksite.WorksiteMenus.setFactory(Kind, Factory) and open(Player, Kind, BlockEntity)
- block.entity.worksite.WorksiteEvents.registerEvents(IEventBus modBus)
- block.entity.worksite.WorksiteActions.energyPerActivation(double), full(), fraction(double)
- AnimalFarmBlockEntity.foodInventory, toolInventory, maxPigCount/maxCowCount/maxChickenCount/maxSheepCount, setMaxCounts(int,int,int,int)
- FishFarmBlockEntity.harvestFish(), harvestInk(), setHarvest(boolean, boolean)
- AutoCraftingBlockEntity.craftingRecipeMemory, outputInventory, resourceInventory, getItemHandler(Direction), tryCraftItem()
- QuarryBlockEntity.height (public int)
- cropfarm.ICrop, CropDefault.isPlantableItem(ItemStack) and isPlantableBlock(Block)
- fruitfarm.IFruit, FruitCocoa
- treefarm.ITreeScanner, ITree, DefaultTreeScanner (INextPositionGetter, ALL_AROUND ... ALL_UP_OR_LEVEL, addLeafMatcher), ChorusScanner, ISapling, Sapling, IBlockExtraDrop

**Not ported**

- TileOreProcessor and BlockOreProcessor (dead code, as instructed)
- AgriCraft crop compat and the 32 mod compat registry files (not ported by the base slice either)
- ItemBlockWorksiteStatic and ItemBlockRotatableMetaTile (the base slice has WorksiteBlockItem and OwnedBlockItem)
- BlockBaseAutomation creative tab and state mapper (the registrar and JSON assets do this)
- Syncing the auto crafting recipe memory through the block entity update tag (the menu syncs it)
- Renderers, menus and screens for the worksites (render and GUI slices)

## automation-render

Ported the Automation rendering, uncompiled: 11 Java files (1,167 lines) with 27 block entity renderers. The 11 OBJ files are split by script into 59 part OBJs and 115 tier JSON models, plus new static-body blockstates, block models and item models for the 20 torque blocks. Moving parts use PoseStack with the original pivots; the flywheel storage and unformed windmill cube are drawn entirely by renderers. Work-bounds outline and the merged warehouse sign renderer are ported. No shared-file wiring is needed.

**API**

- io.github.capttracker.ancientwarfareautomation.client.AutomationRenderClient (self-registering client subscriber)
- io.github.capttracker.ancientwarfareautomation.client.render.AutomationModels.Family.get(part, tier) / allIds() / clearCache()
- io.github.capttracker.ancientwarfareautomation.client.render.PartRenderer.draw / drawTranslucent / faceFront / rotateAbout / rotateAboutCenter / axisOf
- io.github.capttracker.ancientwarfareautomation.client.render.TorqueShaftRenderer
- io.github.capttracker.ancientwarfareautomation.client.render.TorqueSidedCellRenderer(context, family)
- io.github.capttracker.ancientwarfareautomation.client.render.FlywheelControllerRenderer
- io.github.capttracker.ancientwarfareautomation.client.render.FlywheelStorageRenderer
- io.github.capttracker.ancientwarfareautomation.client.render.GeneratorRenderers.HandCranked / Stirling / Waterwheel / Windmill
- io.github.capttracker.ancientwarfareautomation.client.render.WindmillBladeRenderer
- io.github.capttracker.ancientwarfareautomation.client.render.WorksiteRenderer
- io.github.capttracker.ancientwarfareautomation.client.render.WarehouseSignRenderer.forViewer / forLinker

**Not ported**

- CodeChickenLib bakery classes: BaseBakery, RotatableBlockRenderer, AnimatedBlockRenderer, BaseTorqueRenderer, TorqueTieredRenderer, BaseAnimationRenderer and its subclasses, ITESRRenderer, AutomationProperties, sprite registration on TextureStitchEvent
- The 12 .m2f model files (unreferenced 1.7 format)
- Translucent flywheel windows on the item model (an item model cannot do alpha 0.25; the windows are left out)
- Chunk-mesh rendering of the flywheel storage static parts and the unformed windmill cube (drawn by the renderer instead; rework candidate)

## automation-gui

Ported all 22 containers (25 menu-package files) and 24 screens onto Core's toolkit, plus ResearchCraftingElement and AwAutomationMenus (21 MenuTypes, hook registration, server-side openers). Menus open from the server with the block position. Screens that switch menus send a request and the server opens the next one. The NBT protocol is kept. Server handling is split by side and validates client input. Warehouse change lists are batched. Mailbox, fish-control and stirling bugs are fixed locally. Nothing compiled. Notes and lang JSON written.

**API**

- init.AwAutomationMenus: 21 MenuType holders (QUARRY ... WAREHOUSE_CRAFTING_STATION), load(), registerEvents(modBus), openInventorySides(ServerPlayer, InventoryWorksiteBlockEntity), openBounds(ServerPlayer, BlockEntity), openQuarryBounds(ServerPlayer, QuarryBlockEntity)
- menu.AutomationMenu<T>: first-state push, detectChanges(), isRequestAllowed(); base of every Automation menu
- menu.WorksiteMenuBase<T extends InventoryWorksiteBlockEntity> with QuarryMenu, TreeFarmMenu, AnimalFarmMenu, CropFarmMenu, FishFarmMenu, FruitFarmMenu (guiHeight, label fields, requestOpenSides/Bounds/Alt)
- menu.AnimalControlMenu, FishControlMenu, QuarryBoundsMenu, BoundsAdjustMenu, InventorySideMenu (second screens; sendSettingsToServer, onClose)
- menu.AutoCraftingMenu and menu.WarehouseCraftingStationMenu (ICraftingContainer, CraftingMemoryController)
- menu.WarehouseControlMenu, WarehouseStorageMenu, WarehouseInterfaceMenu, WarehouseStockViewerMenu, WarehouseStockLinkerMenu (Viewer implementations)
- menu.MailboxMenu (setSlotsVisible, updateSides, handleNameAdd/Delete/Selection)
- menu.StirlingGeneratorMenu and menu.ChunkLoaderDeluxeMenu (Viewer of ChunkLoaderDeluxeBlockEntity)
- menu.ItemMapSync: changeList diff and apply for virtual item maps
- client.AutomationScreensClient: registers the 21 screens
- client.screen.GuiWorksiteBase<M> and ResearchCraftingElement(gui, CraftingMemoryController, x, y)

**Not ported**

- Mouse.getX/Mouse.setCursorPosition around the mailbox sub screens (LWJGL2); the pointer is not moved
- JEI recipe transfer for the two crafting menus (ICraftingContainer is implemented, no JEI hook)
- Client-side prediction of warehouse contents on crafting (tryGetItemClient); the result is computed on the server only
- setAll/putStackInSlot opening hacks of the crafting containers (Core's CraftingMemoryController replaces them)
- GUI_VEHICLE_STATS (vehicle module)
- Mailbox autoExport has no function (as in the original)

