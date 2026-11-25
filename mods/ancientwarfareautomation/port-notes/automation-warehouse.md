# automation-warehouse: warehouse multiblock, stock viewer / linker, crafting station, mailbox block

Slice key `automation-warehouse`, module `ancientwarfareautomation`. Ported from `automation/tile/warehouse2/**`, the
six `BlockWarehouse*` / `BlockMailbox` classes and the mailbox use of the base slice (report 04, sections 1 to 5 and 9).
Nothing was compiled or run. Every Minecraft / NeoForge name used was looked up in `analysis/mc-sources`.

## What was ported

Java, package `io.github.capttracker.ancientwarfareautomation` (1.12.2 name in brackets):

`block.entity.warehouse` (15 classes + 2 helpers)
- `WarehouseBlockEntity` (TileWarehouseBase + TileWarehouse, merged: the base had one subclass). Extends
  `BoundedWorksiteBlockEntity`. Item cache (`ItemQuantityMap`), `WarehouseStorageMap` routing, interface fill/empty
  actions (quarter energy, one stack per action), crafting station item handler (`getItemHandler()`), player click
  handling (`handleSlotClick`), sort type/order, stock linker positions, viewers, bounds height 3, work type CRAFTING.
- `ControlledBlockEntity` (TileControlled): controller lookup (saved position, then 33 x 9 x 33 scan), detach in
  `setRemoved`, server-only ticker, update tag hooks with registry access (`writeSyncData` / `readSyncData`).
- `WarehouseStorageBlockEntity` (TileWarehouseStorage / Medium / Large + IWarehouseStorageTile): one class for the three
  sizes (size read from `WarehouseStorageBlock`), 576 / 1,152 / 1,728 items, filters, slot clicks.
- `WarehouseInterfaceBlockEntity` (TileWarehouseInterface): `ItemStackHandler(9)`, filters, `recalcRequests()`,
  `InterfaceFillRequest` / `InterfaceEmptyRequest` records.
- `WarehouseCraftingStationBlockEntity` (TileWarehouseCraftingStation): Core's `CraftingRecipeMemory`, warehouse below.
- `WarehouseStockViewerBlockEntity` (TileWarehouseStockViewer), `WarehouseStockLinkerBlockEntity`
  (TileWarehouseStockLinker, with `EqualitySignType` and its `WarehouseStockFilter`).
- `WarehouseStorageFilter`, `WarehouseInterfaceFilter`, `WarehouseStorageMap`, `IControllerTile`, `IControlledTile`.
- `WarehouseDebugger` + `WarehouseDebuggerData` (in memory, level tick listener, `/awwarehouse` hookup).
- `WarehouseMenus`: the seven menu hooks (see below), `WarehouseSetup`: capability and event registration,
  `WarehouseScan`: loaded-chunks-only block entity scan (package private).

`block.warehouse` (6 classes): `WarehouseStorageBlock(Properties, WarehouseSize)`, `WarehouseInterfaceBlock`,
`WarehouseCraftingStationBlock`, `WarehouseStockViewerBlock`, `WarehouseStockLinkerBlock` (facing + `lit`, redstone),
`MailboxBlock`.

Mailbox: `MailboxBlockEntity` and `MailboxData` were already ported by the base slice (package `gamedata`). Checked
against `TileMailbox`: same fields, same save format, tick and send logic, capability sides; nothing to change. This
slice adds the missing `MailboxBlock` (placement facing, ticker, opens the menu through `WarehouseMenus.openMailbox`).

Assets and data: nothing new. The base slice already converted blockstates, models, item models, loot tables, tags and
the 8 warehouse research recipes (`warehouse_*`, plus `warehouse_stock_linker`). The blockstate property names match
the blocks (`facing`, `lit`).

`port-notes/automation-warehouse.lang.json`: 16 keys (block names, linker tooltip, `warehouse_set`, `equality.*`;
the same block name keys are in `automation-base.lang.json`, the file may be merged with duplicates dropped).
Script: `analysis/port/automation_warehouse_lang.py`.

## Wiring needed in shared files

1. `init/AwAutomationBase.java`, imports: the six block classes live in `block.warehouse` (this slice's target package),
   not `block`. Change
   `...ancientwarfareautomation.block.MailboxBlock`, `.block.WarehouseCraftingStationBlock`, `.block.WarehouseInterfaceBlock`,
   `.block.WarehouseStockLinkerBlock`, `.block.WarehouseStockViewerBlock`, `.block.WarehouseStorageBlock`
   to `...ancientwarfareautomation.block.warehouse.<same name>`. Constructors are as the base slice's contract asked:
   `(Properties)`, and `(Properties, WarehouseSize)` for the storage.
2. `init/AwAutomationBase.registerEvents(IEventBus modBus)`: add
   `WarehouseSetup.registerEvents(modBus);` (import `...block.entity.warehouse.WarehouseSetup`). It registers the
   interface block's item handler capability, the debugger's level tick and server stopped listeners and calls
   `WarehouseCommand.setDebugger(WarehouseDebugger.COMMAND)` (so `/awwarehouse startdebug|stopdebug x y z` works; this
   closes item 4 of the base notes).
3. GUI slice: hand the menu constructors to `WarehouseMenus` during setup (until then right-clicking the blocks does
   nothing). Each factory is `(int containerId, Inventory, BlockEntity) -> AbstractContainerMenu`:
   `setControlFactory` (WarehouseBlockEntity), `setStorageFactory` (WarehouseStorageBlockEntity), `setInterfaceFactory`
   (WarehouseInterfaceBlockEntity), `setCraftingStationFactory` (WarehouseCraftingStationBlockEntity),
   `setStockViewerFactory`, `setStockLinkerFactory`, `setMailboxFactory` (`gamedata.MailboxBlockEntity`).
   The menu is opened with the block position as extra data and `sendInitData()` is called on it right after.
   Menus register with the block entities through nested `Viewer` interfaces: `WarehouseBlockEntity.Viewer`
   (`addViewer` for the control menu, `addCraftingViewer` for the crafting menu), `WarehouseStorageBlockEntity.Viewer`,
   `WarehouseInterfaceBlockEntity.Viewer`, `WarehouseStockViewerBlockEntity.Viewer`, `WarehouseStockLinkerBlockEntity.Viewer`.
   Server-side add/remove viewer calls are ignored on the client (as in the original).
4. `lang/en_us.json`: merge `automation-warehouse.lang.json` (only the `equality.*` keys are new).
5. Client slice: block entity renderers for the stock viewer and linker (1.12.2 `WarehouseStockViewerRenderer` /
   `WarehouseStockLinkerRenderer`; they read `getFilters()` and `WarehouseStockFilter.getQuantity()`, synced in the update
   tag), and the work bounds renderer for the warehouse control block (`getRenderBounds()` of the bounded worksite base).
6. Control block: it is a `WorksiteBlock` (base slice contract); its ticker must be `WorksiteBlockEntity.ticker(level)`
   and the right-click goes through `IInteractableTile` (`WarehouseBlockEntity.onBlockClicked` opens the control menu).
7. Optional, Core: `UpdatableBlockEntity` has no registry access in its `writeUpdateNBT` / `handleUpdateNBT`, so item
   stacks cannot be sent through them. This slice overrides `getUpdateTag` / `handleUpdateTag` / `onDataPacket` itself
   (`ControlledBlockEntity`, `WarehouseCraftingStationBlockEntity`); a registry-aware hook in Core would let the two share it.
8. `docs/PORT-STATUS.md`: automation-warehouse slice done, not built.

## Design decisions and changes from the original

- **One class per role, no tile-per-size.** Storage small / medium / large are three blocks and one block entity type; the
  size comes from the block. `IWarehouseStorageTile` is dropped (single implementer), `TileWarehouseBase` merged into
  `WarehouseBlockEntity`.
- **Item identity** is Core's `ItemHashEntry` (item + data components), counts in `ItemQuantityMap`, saving through
  `ItemStack.saveOptional` / `parseOptional`. Interface filters match with Core's relaxed comparison (same item and
  components, damage ignored); 1.12.2 also matched ore dictionary equals (no 1.21.1 equivalent; tags would be the
  replacement if wanted, open question 6 of report 04). Storage filters stay exact.
- **Lifecycle.** `invalidate` becomes `setRemoved` (runs on break and on chunk unload). `validate` is not ported (it could
  only re-attach what `invalidate` had just cleared). `onLoad` is not used: the first server tick links, like the original.
  `getController()` ignores a controller that was removed since (a reloaded controller is a new object).
- **Chunk safety.** Scans use `WarehouseScan` (loaded chunks only); Core's `WorldTools.getTileEntitiesInArea` loads chunks.
  `level.isLoaded(pos)` guards every position lookup (controller position, linker positions, warehouse position).
- **Cursor stack.** `player.inventory.getItemStack()/setItemStack/updateHeldItem` became
  `containerMenu.getCarried() / setCarried(...) / broadcastChanges()`.
- **Client work.** The original ticked controlled blocks on both sides; here only the server ticks. The control block
  keeps `addViewer` ignoring the client, as before.
- **Client sync.** Control block: sort type and order in the update tag. Stock viewer and linker: filters with live
  quantities (the linker's compare values and signs too) in the update tag, the server pushes a block update on every
  warehouse change (as before, see rework candidates). Crafting station: recipe memory in the update tag. Storage and
  interface: nothing in the tag; open menus are told through the `Viewer` callbacks.

### Known bugs fixed (obvious and local)

- `TileWarehouse.invalidate()` did not call `super.invalidate()`; `WarehouseBlockEntity.setRemoved()` does. It also
  detaches the blocks it knows (sets) instead of scanning its bounds, so stock viewers placed one block outside the
  bounds are detached too.
- `TileWarehouseStockLinker` forgot its warehouse position on restart (only the saved controller position survived);
  `warehousePos` is saved now. It also changed the block state on the client side; the whole tick is server only now.
- `TileWarehouseStockLinker.recountFilters` reset the filters' sign and compare value when there was no controller
  (every detach wiped the player's settings); only the quantity is reset now.
- `WarehouseItemHandler.insertItem` called `setCount` on the shared `ItemStack.EMPTY` for the empty crafting slots;
  empty slots are skipped now.
- Sort type and order were not marked dirty when changed (so saved only by chance); `setChanged()` is called. Viewer filters
  of the stock viewer likewise.
- Out of range ordinals in saved sort / sign bytes fall back to the defaults instead of throwing.
- `ItemQuantityMap.removeAll` NPE is already fixed in Core.
- `TileWarehouseBase.shouldRecount` is still never set by the original code; kept with a public `requestRecount()`
  (nothing calls it) so the path is not dead.

## Not ported

- Menus and screens (`ContainerWarehouse*`, `GuiWarehouse*`, `ContainerMailbox` ...): GUI slice. The hooks above are the
  contact surface.
- Block entity renderers (`WarehouseStockViewerRenderer`, `WarehouseStockLinkerRenderer`): client slice.
- `eventReceived` / `sendClientEventToTile` forwarding in the storage, viewer and linker blocks: none of the block entities
  handles block events, so `triggerEvent` is not forwarded.
- `WarehouseDebuggerData` as saved data: its `readFromNBT` / `writeToNBT` were empty, so it is an in-memory map now.
- Registry ids `*_tile` and the old tile id fixer (worlds are not migrated). JEI recipe transfer for the crafting station.
- Ore dictionary matching in interface filters (see above).

## Unconfirmed API / things to check at the first build

- Every Minecraft and NeoForge name was found in the sources. Nothing rests on another slice's usage alone, except the
  Core and base classes listed under "Depends on".
- Depends on (read, taken as written): Core `ItemHashEntry`, `ItemQuantityMap`, `InventorySlotlessBasic`
  (`readFromNBT(registries, tag)`), `CraftingRecipeMemory` (`save` / `load` / `dropInventory(level, pos)`),
  `InventoryTools` (`mergeItemStack`, `updateCursorItem`, `doItemStacksMatchRelaxed`, `getCountOf`, `SortType`, `SortOrder`),
  `NBTHelper.writeSerializablesTo` / `deserializeListFrom` (registry-aware), `BlockTools`, `OwnedBlockItem`,
  `UpdatableBlockEntity`; base `BoundedWorksiteBlockEntity` (`onBoundsSet`, `onBoundsAdjusted`), `WorksiteBlockEntity`
  (`getNextAction`, `processAction`, `updateWorksite`, `isActive`), `AwAutomationBase` holders, `WarehouseCommand`.
- Generic inference: `ControlledBlockEntity.ticker(level)` returned from `getTicker` (same pattern as
  `MailboxBlockEntity.ticker`); `NeoForge.EVENT_BUS.addListener(WarehouseDebugger::onLevelTick)`.
- `AwMenu.sendInitData()` is called right after `openMenu` (the GUI slice's menus decide what it sends).
- A static nested class of `WarehouseStockLinkerBlockEntity` (`WarehouseStockFilter`) has private comparison methods that the
  enclosing class calls (legal Java, listed in case a linter complains).

## Rework candidates (for the playability pass; none fixed here)

Scalability (report 04, section 8), all still present:
- `ItemHashEntry` hashing on every lookup (`getCount(ItemStack)`, `getDestinations`, `getQuantityStored`); `tryFillFromRequest`
  calls `getQuantityStored` once per storage block per attempt.
- `WarehouseStorageMap.getOrCreateStorageSet` uses `computeIfAbsent` on lookups: every item ever routed leaves an empty set.
- `getDestinations()` allocates a list per operation; the fill order is hash set order (not stable).
- `updateViewers()` per cache change (twice in `tryAddItem`): a block update packet per stock viewer and linker per item moved;
  the linker should notify neighbours only when its comparison result changes.
- Menu diffing rescans the whole map per change; the first sync sends everything (GUI slice: coalesce, deltas).
- Storage blocks `setChanged` on every insert / extract; the whole map is rewritten on chunk save.
- `getItemHandler()` copies every item per crafting click; the client prediction `tryGetItemClient` mutates the cache.
- Keys include damage and every component: damaged tools, enchanted books and named items are separate entries.
- `searchForController` scans 33 x 9 x 33 per orphan; the linker retries every 40 ticks.
- A lookup of `ItemStack` keys in the debugger uses identity maps (as the original).

Design:
- Interface filters could use item tags (or the item + damage-less comparison) instead of the removed ore dictionary.
- The control block and the storage blocks have no item handler capability (hoppers cannot use them); report 04, question 3.
- The stock viewer's owner check and the linker's compare-value-0-is-false rule are kept as they were.
- The crafting station only finds a warehouse directly below it, looked up on every access.
- A controlled block that loses its controller (chunk unload of the control block, bounds change) never searches again until
  the control block scans (`init` stays true), as in the original; a periodic retry like the linker's would be more robust.
- `Owner` sync: the stock viewer and linker do not send the owner to clients (as before).
- Mailbox: report 04 section 8 and the base notes (autoExport, one stack per box per tick) still apply.
