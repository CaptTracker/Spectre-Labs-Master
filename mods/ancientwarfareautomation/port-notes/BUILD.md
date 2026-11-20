# Automation build notes (first compile)

Result: `:ancientwarfareautomation:compileJava` and `:ancientwarfareautomation:build` pass (338 classes, 0 errors, 0 warnings
after the `bus =` cleanup below). The ported source needed no API fixes; the only compile blocker was two missing classes.

## Wired

- `AncientWarfareAutomation` constructor: `AwAutomationBase.load()` + `registerEvents(modBus)`, `AwAutomationMenus.load()` +
  `registerEvents(modBus)`, `AWAutomationStatics.register(modBus, container)`, then `REG.register(modBus)`. TODO comment removed.
- `AwAutomationBase`: imports repointed to the real packages (`block.warehouse.*` for the six warehouse/mailbox blocks,
  `block.worksite.*` for `WorksiteBlock` and `AutoCraftingBlock`, `block.chunkloader.*` for the chunk loaders).
  `registerEvents` now also calls `WarehouseSetup.registerEvents(modBus)` (interface item handler, debugger listeners,
  `/awwarehouse` hookup) and `WorksiteEvents.registerEvents(modBus)` (auto crafting item handler, animal farm drops).
- The torque registrations and capabilities (`TorqueCapabilities.register` x16, `registerStirling`) were already in `AwAutomationBase`.
  Torque menu hook (`TileStirlingGenerator.setMenuFactory`), worksite and warehouse menu hooks are set in
  `AwAutomationMenus.registerEvents`.
- Menu types: 21 in `AwAutomationMenus` (the only place menus are registered, no duplicates). Screens: `AutomationScreensClient`.
  Renderers and part models: `AutomationRenderClient`. Key mapping and config screen: `AutomationInputHandler`.
  All three are `@EventBusSubscriber(value = Dist.CLIENT)` and pick up the mod bus from the event type (the deprecated
  `bus = Bus.MOD` argument was removed).
- Lang: all six `*.lang.json` merged into `en_us.json` (111 keys, no conflicts; `automation-render.lang.json` is empty), template key
  `block.ancientwarfareautomation.example_block` removed, `itemGroup.ancientwarfareautomation` kept. No `.sounds.json` fragments
  exist for this module, so `sounds.json` was not touched (the module has none).
- No duplicated creative tab, registration name or payload: one tab (`AwAutomationBase.TAB`), registry names grepped and unique per
  registry, no payloads in this module. Every block has a blockstate, item model, loot table and lang key (checked by script).

## Written / changed

- New `block/chunkloader/ChunkLoaderSimpleBlock` and `ChunkLoaderDeluxeBlock`: the base slice's contract listed them but no slice wrote
  them. They extend Core's `BaseBlock` (so `onRemove` releases the tickets and right-click reaches `IInteractableTile`), implement
  `EntityBlock`, and `setPlacedBy` calls `setupInitialTicket()` server side (original `BlockChunkLoaderSimple`).

## TODO(port) left

- None added by this step. Items listed as "not ported" in the slice notes stand (AgriCraft compat, 32 compat registry files, JEI,
  Core `IWorkSite.WorksiteImplementation.getEnergyPerActivation` formula, Core `WorksiteUpgrade` lang keys vs the module's upgrade items).

## Runtime-only risks (not caught by the compiler)

- Static init order in `AwAutomationBase`: the block lambdas reference `*_BE` holders by qualified name and are lazy, so they are
  fine; the warehouse storage block entity type is built from `WAREHOUSE_STORAGES.values()` which is filled in a static block that
  precedes it. `TAB` icon reads `AncientWarfareCore.HAMMERS` lazily inside the supplier (needs Core loaded first, it is a dependency).
- Capability registration: the three torque `TorqueCapabilities.register` calls plus the worksite ones all run in
  `RegisterCapabilitiesEvent`; a block entity type registered for the same capability twice throws there. Stirling is registered for
  torque/energy and item handler separately (different capabilities), the auto crafting table gets its item handler only from
  `WorksiteEvents`. The `WarehouseBlockEntity` is registered as a worksite in `AwAutomationBase` and its own capabilities in
  `WarehouseSetup`; check that these do not overlap on first server start.
- The menu hooks (`WorksiteMenus`, `WarehouseMenus`, `TileStirlingGenerator`) are set in `AwAutomationMenus.registerEvents`, called from
  the constructor; right-clicking before then is impossible, but if that call were ever removed the blocks silently do nothing.
- `AWChunkLoader.validateTickets` reads block entities while levels are prepared; `TicketController` registration is on the mod bus
  (`AWChunkLoader::register`).
- Registry parsers are registered in `registerEvents` with Core's `RegistryLoader.SERVER` (a data pack reload listener added by Core's
  `AwCoreMisc`); the crop/tree farm registry JSON is read on every data reload, so the farms have no crops/trees until a world loads.
- Client-only classes: nothing outside `client/` references `net.minecraft.client` or Core's client package. The menus do not
  reference screens; screens are bound by `AutomationScreensClient` only.
- Not run: `runServer`/`runClient` (not allowed in this step). Placeholder/OBJ models and `neoforge:obj` loader use are untested.
