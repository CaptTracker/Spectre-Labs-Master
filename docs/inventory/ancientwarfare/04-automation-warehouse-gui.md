# Automation: warehouse system, containers and GUIs

> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

Packages covered (all under `automation/`):

| Package | Files | Lines |
|---|---|---|
| `tile/warehouse2/` | 19 (incl. `package-info`) | 2,642 |
| `container/` | 23 (incl. `package-info`) | 2,224 |
| `gui/` | 25 (incl. `package-info`) | 2,004 |
| Total | 67 | 6,870 |

Counts come from `wc -l` over the three directories. The jar has 28 classes in `tile/warehouse2` (18 top-level classes plus `package-info`, plus 9 inner/anonymous: the `WarehouseItemHandler`, the two `InterfaceFill/EmptyRequest` classes, the two `WarehouseStockFilter` classes, `EqualitySignType` and three anonymous `$1`), 27 in `container` (22 top-level classes plus `package-info`, plus 4 anonymous) and 85 in `gui` (24 top-level classes plus `package-info`, plus 60 anonymous/inner button, slot and listener classes). Top-level counts match the source file counts (jar counts include `package-info`).

I also read these out-of-slice classes because the slice cannot be understood without them: `core/container/ContainerBase`, `ContainerTileBase`, `ContainerCraftingRecipeMemory`, `core/gui/GuiContainerBase`, `core/network/NetworkHandler`, `PacketGui`, `core/inventory/ItemQuantityMap`, `ItemHashEntry`, `InventorySlotlessBasic`, `core/tile/CraftingRecipeMemory`, `TileUpdatable`, and in `automation/`: `tile/worksite/TileWorksiteBase`, `TileWorksiteBounded`, `TileWorksiteBoundedInventory`, `TileAutoCrafting`, `tile/TileMailbox`, `TileChunkLoaderDeluxe`, the six `BlockWarehouse*` classes and `init/AWAutomationBlocks`.

What this part is for: the "warehouse" is a player-built multiblock bulk item store. A control block (itself a bounded worksite) defines a box; storage blocks, interface blocks, stock viewer signs and stock linkers placed inside or next to the box attach to it. Items are stored as counts per item type (not per slot). Players browse and take items through the control block GUI or craft straight from the stock through the crafting station placed on top of the control block. Interface blocks act as the bridge to the rest of the automation system: they hold a 9-slot inventory and a filter list, and the warehouse fills them to a target quantity or empties surplus back into storage, so that hoppers, couriers and other mods can move items in and out. The rest of the slice is the container/GUI layer for every Automation screen (worksites, auto-crafting, mailbox, stirling generator, chunk loader).

## What it adds

This slice defines no blocks, items, entities, packets, commands or config options itself. The blocks and block items that carry the tiles are in `automation/block/` and `automation/init/AWAutomationBlocks` (other slice); they are listed in "How it works" for context.

### Tile entities

Registry names are the ones passed to `registerTile` in `init/AWAutomationBlocks.java:173-191` (namespace `ancientwarfareautomation`; `core/datafixes/TileIdFixer.java:21` shows the old `minecraft:` namespace for these ids).

| Registry id | Class | What it is | Porting note |
|---|---|---|---|
| `warehouse_control_tile` | `TileWarehouse` (extends abstract `TileWarehouseBase` -> `TileWorksiteBounded` -> `TileWorksiteBase`) | Control block: bounds, item cache, request processing, GUI | Is an NPC/torque-driven worksite; port together with the worksite base |
| `warehouse_storage_small_tile` | `TileWarehouseStorage` | Slotless store, 576 items (9*64) | One class, three block variants; capacity is items, not slots |
| `warehouse_storage_medium_tile` | `TileWarehouseStorageMedium` | 1,152 items (2x) | Block meta `size=medium` -> separate block |
| `warehouse_storage_large_tile` | `TileWarehouseStorageLarge` | 1,728 items (3x) | Block meta `size=large` -> separate block |
| `warehouse_interface_tile` | `TileWarehouseInterface` | 9-slot `ItemStackHandler` plus filter list; exposes item-handler capability on all sides | Capability moves to `RegisterCapabilitiesEvent` |
| `warehouse_crafting_tile` | `TileWarehouseCraftingStation` | Crafting table that draws from the warehouse directly below | Needs research-recipe crafting system |
| `warehouse_stock_viewer_tile` | `TileWarehouseStockViewer` | Sign showing live counts for up to 4 filters | Sign text rendered by a TESR; counts pushed with block updates |
| `warehouse_stock_linker_tile` | `TileWarehouseStockLinker` | Redstone comparator on warehouse counts (up to 4 filters) | Block state `lit`; redstone signal from tile |

Two more tiles are registered in the same method but are outside the warehouse set: `auto_crafting_tile` (`TileAutoCrafting`, `init/AWAutomationBlocks.java:194`) and `mailbox_tile` (`TileMailbox`, `init/AWAutomationBlocks.java:197`). Their GUIs are in the table below.

Abstract or helper classes in the slice (not registered): `TileControlled` (controller-lookup base for storage, interface, viewer, linker), `TileWarehouseBase`, interfaces `IControllerTile`, `IControlledTile`, `IWarehouseStorageTile`, value classes `WarehouseStorageFilter`, `WarehouseInterfaceFilter`, `TileWarehouseStockViewer.WarehouseStockFilter`, `TileWarehouseStockLinker.WarehouseStockFilter`, `WarehouseStorageMap`.

### Containers and GUIs

Registration: containers in `AncientWarfareAutomation.java:77-97`, screens in `BlockXxx.registerClient()` (warehouse blocks, mailbox, stirling, auto crafting, chunk loader) or `proxy/ClientProxyAutomation.java:87-99` (the rest). Ids are the `GUI_*` ints in `core/network/NetworkHandler.java:74-137`. All containers are created by reflection with the constructor `(EntityPlayer, int x, int y, int z)` (`NetworkHandler.getServerGuiElement`, line 200). Sync column: "NBT" = `PacketGui` through `ContainerBase.sendDataToClient/sendDataToServer` (compressed NBT, `core/network/PacketGui.java`); "vanilla" = standard slot sync / window properties.

| GUI id | Container (container/) | Screen (gui/) | Edits | Sync |
|---|---|---|---|---|
| 5 `GUI_WORKSITE_INVENTORY_SIDE_ADJUST` | `ContainerWorksiteInventorySideSelection` | `GuiWorksiteInventorySideSelection` | any `TileWorksiteBoundedInventory` (side-to-inventory map) | NBT: full map on open, per-change deltas both ways; "closeGUI" re-opens worksite GUI via `onBlockClicked` |
| 14 `GUI_WORKSITE_ANIMAL_CONTROL` | `ContainerWorksiteAnimalControl` | `GuiWorksiteAnimalControl` | `WorkSiteAnimalFarm` (max pigs/sheep/cows/chickens) | NBT: init packet, change polling in `detectAndSendChanges`, client sends 4 ints |
| 15 `GUI_WORKSITE_AUTO_CRAFT` | `ContainerWorksiteAutoCrafting` | `GuiWorksiteAutoCrafting` | `TileAutoCrafting` | vanilla slots (73 slots, indices in comments at container line 28) plus NBT keys `recipe`, `craft` |
| 16 `GUI_WORKSITE_FISH_CONTROL` | `ContainerWorksiteFishControl` | `GuiWorksiteFishControl` | `WorkSiteFishFarm` (harvest fish/ink flags) | NBT, same pattern as animal control |
| 17 `GUI_MAILBOX_INVENTORY` | `ContainerMailbox` | `GuiMailboxInventory`, plus sub-screens `GuiMailboxNameSelect` and `GuiMailboxInventorySideSetup` (opened client-side with `displayGuiScreen`, share the same container, no own id) | `TileMailbox` plus world data `MailboxData` | vanilla slots (18 receive + 18 send) plus NBT for names, sides, flags, name lists; server diff in `detectAndSendChanges` |
| 18 `GUI_WAREHOUSE_CONTROL` | `ContainerWarehouseControl` | `GuiWarehouseControl` | `TileWarehouse` | NBT: item-map change list, max storage, sort type/order; slot-click requests client to server |
| 19 `GUI_WAREHOUSE_STORAGE` | `ContainerWarehouseStorage` | `GuiWarehouseStorage` | `TileWarehouseStorage` (and Medium/Large) | NBT: change list of that tile's own map, filter list both ways, slot clicks |
| 20 `GUI_WAREHOUSE_STOCK` | `ContainerWarehouseStockViewer` | `GuiWarehouseStockViewer` | `TileWarehouseStockViewer` | NBT: filter list client to server; counts reach the client through the tile update packet, not the container |
| 21 `GUI_WAREHOUSE_OUTPUT` | `ContainerWarehouseInterface` | `GuiWarehouseInterface` | `TileWarehouseInterface` | vanilla slots (9 via `SlotItemHandler`) plus NBT filter list both ways |
| 22 `GUI_WAREHOUSE_CRAFTING` | `ContainerWarehouseCraftingStation` | `GuiWarehouseCraftingStation` | `TileWarehouseCraftingStation` plus the warehouse below | vanilla slots (crafting memory) plus NBT change list of the whole warehouse map plus `recipe` |
| 23 `GUI_CHUNK_LOADER_DELUXE` | `ContainerChunkLoaderDeluxe` | `GuiChunkLoaderDeluxe` | `TileChunkLoaderDeluxe` (5x5 chunk grid) | NBT: chunk list on open and on every change, "forced" toggle client to server |
| 24 `GUI_WORKSITE_QUARRY` | `ContainerWorksiteQuarry` | `GuiWorksiteQuarry` | `WorkSiteQuarry` inventory | vanilla slots only |
| 25 `GUI_WORKSITE_TREE_FARM` | `ContainerWorksiteTreeFarm` | `GuiWorksiteTreeFarm` | `WorkSiteTreeFarm` (main, plantable, misc inventories) | vanilla slots only |
| 26 `GUI_WORKSITE_ANIMAL_FARM` | `ContainerWorksiteAnimalFarm` | `GuiWorksiteAnimalFarm` | `WorkSiteAnimalFarm` (main, food, tool inventories) | vanilla slots only |
| 27 `GUI_WORKSITE_CROP_FARM` | `ContainerWorksiteCropFarm` | `GuiWorksiteCropFarm` | `WorkSiteCropFarm` (main, plantable, misc) | vanilla slots only |
| 29 `GUI_WORKSITE_FISH_FARM` | `ContainerWorksiteFishFarm` | `GuiWorksiteFishFarm` | `WorkSiteFishFarm` (main inventory only; raw-typed base) | vanilla slots only |
| 30 `GUI_WORKSITE_QUARRY_BOUNDS` | `ContainerWorksiteQuarryBounds` | `GuiWorksiteQuarryBounds` | `WorkSiteQuarry` (`height`) | NBT: height, "guiClosed" triggers `onBoundsAdjusted` |
| 31 `GUI_STIRLING_GENERATOR` | `ContainerStirlingGenerator` | `GuiStirlingGenerator` | `TileStirlingGenerator` (1 fuel slot) | vanilla window properties (`sendWindowProperty` ids 0-2 for energy, burn time, burn base) |
| 32 `GUI_WAREHOUSE_STOCK_LINKER` | `ContainerWarehouseStockLinker` | `GuiWarehouseStockLinker` | `TileWarehouseStockLinker` | NBT filter list client to server; counts via tile update packet |
| 46 `GUI_WORKSITE_BOUNDS` | `ContainerWorksiteBoundsAdjust` | `GuiWorksiteBoundsAdjust` | any `IBoundedSite` tile, including `TileWarehouse` | NBT: crop/tree "checkedMap" byte array to GUI via `sendDataToGui` (the `"gui"` key path), bounds and map back on close |
| 53 `GUI_WORKSITE_FRUIT_FARM` | `ContainerWorksiteFruitFarm` | `GuiWorksiteFruitFarm` | `WorkSiteFruitFarm` | vanilla slots only |

Counts: 22 container classes (21 registered plus abstract `ContainerWorksiteBase`), 24 screen classes (21 registered plus the two mailbox sub-screens plus abstract `GuiWorksiteBase`). GUI id 28 is unused. `GUI_VEHICLE_STATS` (52) is registered by `ClientProxyAutomation` but belongs to the vehicle module.

There are no GUIs for the hand-cranked generator, flywheels, torque distributors, windmill or water wheel: the only torque-related screen is the stirling generator. The "torque" information users see comes from the block tooltips and block-state rendering (not analysed here).

### Other kinds

| Kind | Entries |
|---|---|
| Event handlers | `WarehouseDebugger` (`TickEvent.WorldTickEvent`, END phase), registered at `AncientWarfareAutomation.java:100`. Debug-only inventory auditor, driven by `/awwarehouse startdebug x y z` (`automation/command/CommandWarehouse.java`, other slice). |
| Saved data | `WarehouseDebuggerData` (`WorldSavedData` named `AWWarehouseDebuggerData`). `readFromNBT` and `writeToNBT` are empty (`WarehouseDebuggerData.java:26-33`), so nothing persists; it is an in-memory debug map. |
| Capabilities provided | `TileWarehouseInterface` offers `ITEM_HANDLER_CAPABILITY` for every side (`TileWarehouseInterface.java:187-203`). `TileWarehouse` and `TileWarehouseStorage` offer none (they cannot be pulled from by hoppers). |
| Recipes and data (not in slice, for reference) | 8 `research_recipes/automation/warehouse_*.json` files, 6 blockstate files `warehouse_*.json`, manual page `registry/manual/*/warehouse.json`, lang keys. Not analysed in detail. |

## How it works

### 1. Multiblock model and controller discovery

Roles and bounds:

| Block / tile | Role | How it finds its controller |
|---|---|---|
| Control (`TileWarehouse`) | `IControllerTile`; defines bounds `bbMin..bbMax` (set by the player with the bounds GUI; `userAdjustableBlocks()` is false, `getBoundsMaxHeight()` is 3; width 5/9/16 by SIZE_MEDIUM/LARGE upgrade, inherited from `TileWorksiteBounded.getBoundsMaxWidth`) | n/a |
| Storage small/medium/large | `IControlledTile` plus `IWarehouseStorageTile` | `TileControlled.isValidController`: its position lies inside the controller bounds |
| Interface | `IControlledTile` | same bounds test |
| Stock viewer | `IControlledTile` | bounds expanded by 1 block horizontally (`TileWarehouseStockViewer.isValidController`), so a sign can sit on the outside wall |
| Stock linker | `IControlledTile` | no spatial rule: it is linked to a specific warehouse position stored in the item NBT (`warehousePosTag`, set by sneak-use on the control block in `ItemBlockWarehouseStockLinker.java:50-59`, copied to the tile in `BlockWarehouseStockLinker.onBlockPlacedBy`). `isValidController` accepts any `TileWarehouseBase` |
| Crafting station | not controlled; uses `world.getTile(pos.down(), TileWarehouse.class)` on every access (`TileWarehouseCraftingStation.getWarehouse`) | must be placed directly on top of the control block |

Lifecycle:

- `TileControlled.update()` (`TileControlled.java:23-31`): on the first tick, `loadController()` tries the saved `controllerPosition` (NBT long `controllerPosition`), otherwise `searchForController()` scans tile entities in a 33 x 9 x 33 box (`pos +/- 16, +/- 4, +/- 16`) for the first valid `IControllerTile` and calls `addControlledTile` on it.
- `TileWarehouseBase.updateWorksite` runs `scanForInitialTiles()` once (`init` flag): it lists tile entities inside the bounds extended by 1 horizontally and adds every orphan `IControlledTile` that accepts it.
- `addControlledTile` (`TileWarehouseBase.java:313`) dispatches by type to private `addStorageTile/addInterfaceTile/addStockViewer/addStockLinker`. Server only.
- Storage add: `setController(this)`, add to `storageTiles` and `storageMap`, then merge the tile's items into `cachedItemMap`.
- `TileControlled.invalidate()` removes itself from the controller; `removeStorageTile` subtracts the tile's items from the cache. `TileControlled.validate()` re-adds (chunk reload path).
- Changing bounds (`onBoundsAdjusted`) detaches every controlled tile, clears all sets and the cache, rescans, and resets the debugger counters.
- Control block removal: `TileWarehouse.invalidate()` clears the controller of every tile in bounds that pointed at it. It does not call `super.invalidate()` (`TileWarehouse.java:23`), which looks like an upstream defect (worksite base cleanup is skipped).
- What is saved on the controller: the base worksite data (bounds, upgrades, torque) plus `stockLinkerPositions` (long list) and `sortOrder`/`sortType` bytes. The item cache is not saved; it is rebuilt on load by tiles re-attaching (`tile.addItems(cachedItemMap)` in `addStorageTile`), and by `recountInventory()` if `shouldRecount` is set (nothing in the slice sets it to true; the field is only read, so that path is dead code).

### 2. Item storage model and filters

Key and map:

- `ItemHashEntry` (`core/inventory/ItemHashEntry.java`) is the map key: a copy of the stack with count 1 serialised to `NBTTagCompound` (`writeToNBT`), with `hashCode`/`equals` taken from that compound. It therefore distinguishes item id, damage (so each durability value is a separate key), full NBT and forge capability NBT.
- `ItemQuantityMap` is a `HashMap<ItemHashEntry, Integer>` with add/decrease/remove, `getItems()` (compacts to max-stack stacks), and NBT read/write as a list of `{item, quantity}` entries (`entryList`). `removeAll` unboxes `map.get(entry)` with no null check (`ItemQuantityMap.java:35-38`), so removing a tile whose items are not in the cache throws an NPE.
- Each storage tile owns an `InventorySlotlessBasic(totalSize)`: an `ItemQuantityMap` plus `currentSize`. Capacity is total item count (576/1152/1728), not slots or stack sizes. `getAvailableSpaceFor` ignores the filter. Saved under tag `inventory.itemMap.entryList`.
- The control block keeps `cachedItemMap`, the sum over all storage tiles (server-authoritative; also written on the client by the crafting container, see section 5).

Filters:

| Filter class | Owner | Fields | Meaning |
|---|---|---|---|
| `WarehouseStorageFilter` | storage tile (list, GUI limit 10) | item (count 1), derived `ItemHashEntry` | whitelist: a tile with at least one filter accepts only those exact item keys; no filters means accepts anything |
| `WarehouseInterfaceFilter` | interface tile (GUI limit 9) | item, quantity | target quantity to keep in the interface; matches with `InventoryTools.doItemStacksMatchRelaxed` (OreDictionary-aware, ignores durability for damageable items, checks NBT and capabilities, `InventoryTools.java:316`) |
| `TileWarehouseStockViewer.WarehouseStockFilter` | viewer (GUI limit 4) | item, live quantity | shows the warehouse count for the item |
| `TileWarehouseStockLinker.WarehouseStockFilter` | linker (GUI limit 4) | item, live quantity, `EqualitySignType` (=, >, <, >=, <=), compare value | linker is "on" if any filter's comparison is true; a compare value of 0 is always false (`TileWarehouseStockLinker.java` `equalToTarget` etc.) |

`WarehouseStorageMap` (one per control block) holds `unfilteredStorage`, `filteredStorage`, and `Map<ItemHashEntry, Set<IWarehouseStorageTile>> storageMap`. Routing:

- `getDestinations(stack)` returns the tiles registered for that item plus all unfiltered tiles (insert order: filtered first).
- `getDestinations()` returns all filtered plus all unfiltered tiles (extract order).
- Both return a freshly allocated `ArrayList` of a `HashSet`, so order is unspecified.
- Changing a tile's filters re-registers it but never moves items already stored.
- Manual GUI insertion bypasses the filter: `TileWarehouseStorage.handleSlotClick/tryAdd` inserts directly into that tile.

### 3. Requests and deposits: how items flow

Player GUI paths (server side, triggered from `ContainerWarehouseControl.handlePacketData`, key `slotClick`):

- Cursor holds an item and click is not shift: `TileWarehouse.tryAddItem` routes through `storageMap.getDestinations(stack)`, inserts into each tile until done, calls `changeCachedQuantity` and `updateViewers`, then sets the cursor stack and `updateHeldItem()`.
- Click on a displayed item: `tryGetItem` computes how much to take (left: one stack; right: half of min(stack, available); shift-right: one more than the cursor has; shift-left: moves to the player inventory via `InventoryTools.updateCursorItem`), then extracts from tiles in `getDestinations()` order and updates the cache.
- Shift-click on a player slot inside the container (`transferStackInSlot`) calls `TileWarehouseBase.tryAdd`.
- The client sends `{slotClick: {reqItem (item NBT), isShiftClick, isRightClick}}` built in `handleClientRequestSpecific`.

Interface paths (automation side), executed as worksite "actions":

- `TileWarehouseInterface.recalcRequests()` runs on every `ItemStackHandler.onContentsChanged` and on filter change. It builds `emptyRequests` (slot count of items that do not match any filter, or surplus above the summed quantity) and `fillRequests` (filter item with a missing amount), then calls `controller.onIterfaceInventoryChanged(this)`, which keeps the sets `interfacesToEmpty` and `interfacesToFill`.
- `TileWarehouseBase.getNextAction()` returns `EMPTY_ACTION` if any interface needs emptying, else `FILL_ACTION` if any needs filling, else nothing. Each action costs `WorksiteImplementation.getEnergyPerActivation(efficiency) / 4` torque.
- `TileWorksiteBase.update()` (server, not when redstone powered) calls `processAction`; success deducts energy, failure sets `workRetryDelay = 20`.
- `tryEmptyInterfaces`: first interface with a successful empty request wins this tick. It inserts the slot's stack into `getDestinations(stack)`, adds to the cache, extracts the moved amount from the interface slot.
- `tryFillInterfaces`: first interface with a successful fill request wins. For a request it loops over all storage tiles, takes `min(maxStackSize, found, remaining)` from the first tile that has the item, merges into the interface inventory (`InventoryTools.mergeItemStack`), and decrements the cache. At most one stack per tick per control block, so throughput is about 64 items per tick when powered, less for small stack sizes.
- Energy: `TileWorksiteBase` holds a torque cell and also accepts RF through the redstoneflux `Optional` interfaces (`IEnergyReceiver`). NPC workers add energy by walking to the site (`NpcAIPlayerOwnedWork` calls `site.addEnergyFromWorker`, `npc/ai/owned/NpcAIPlayerOwnedWork.java:99`) when `site.hasWork()` and `npc.canWorkAt(site.getWorkType())`. `TileWarehouseBase.getWorkType()` is `WorkType.CRAFTING`, so only crafting NPC workers (`NpcWorker.getWorkTypeFromEquipment`) power it.

Couriers and other automation:

- Couriers (`NpcCourier`, `NpcAIPlayerOwnedCourier`, `RoutingOrder`) have no warehouse-specific code (a grep of `npc/` for "warehouse" finds nothing). They use `IItemHandler` obtained with `te.getCapability(ITEM_HANDLER_CAPABILITY, side)` on the route point's tile (`NpcAIPlayerOwnedCourier.java:114-129`), so a courier can service a warehouse only through an interface block's 9-slot handler (or a mailbox).
- The control block and storage blocks are not reachable by hoppers or other mods' pipes.

### 4. What is saved and what is synced

| Data | Saved in chunk NBT | Synced to client |
|---|---|---|
| Control block: bounds, upgrades, torque | yes (worksite base) | worksite update packet (`TileUpdatable`) |
| Control block: sort type/order | yes | yes (`writeUpdateNBT`, `TileWarehouse.java:128`) |
| Control block: item cache | no (rebuilt) | only to open GUIs, as container change lists |
| Control block: stock linker positions | yes (`stockLinkerPositions`) | no |
| Storage tile: item map and filters | yes (`inventory`, `filterList`) | only to open `ContainerWarehouseStorage` viewers (change list); not in the tile update packet |
| Storage tile: controller position | yes (`controllerPosition`) | no |
| Interface: 9 inventory slots, filters | yes | slots by vanilla container sync; filters by NBT message |
| Crafting station: `CraftingRecipeMemory` (book slot, output slot, 3x3 matrix, recipe id) | yes | yes (`writeUpdateNBT` writes the same data) |
| Stock viewer and linker: filters with live quantities, owner | filters and owner yes | filters and counts in the tile update packet; block update fired on every warehouse change |

Container-side sync pattern shared by Control, Storage and Crafting containers (`synchItemMaps` in each):

1. The tile calls `viewer.onWarehouseInventoryUpdated()` / `onStorageInventoryUpdated()`, which only sets a `shouldUpdate` flag.
2. On the next `detectAndSendChanges`, the container copies the tile's full map into a scratch `ItemQuantityMap`, diffs it against the map it last sent, and sends one `changeList` NBT list containing `{item NBT, quantity}` for every changed or new key (a quantity of 0 removes the entry client-side via `putEntryFromNBT`).
3. The client container applies the list to its own `itemMap` and calls `refreshGui()`; the screen rebuilds all item slot widgets and re-sorts and filters (`GuiWarehouseControl.setupElements`).

### 5. Crafting station

`ContainerWarehouseCraftingStation` wraps core `ContainerCraftingRecipeMemory` (research-gated 3x3 recipe memory with a research book slot, `core/tile/CraftingRecipeMemory`). Items come from a synthetic `IItemHandlerModifiable` built per click by `TileWarehouse.getItemHandler()`:

- It takes `cachedItemMap.getItems()` (max-stack stacks) and appends 10 empty slots, then wraps them as `WarehouseItemHandler`. Insert and extract into these virtual slots call back into `tryAddItem/tryGetItem`, i.e. real storage tiles; `setStackInSlot` is implemented as an insert or extract of the difference.
- Crafting uses `AWCraftingManager.getRecipeInventoryMatch` over the combined handler plus reusable stacks, removes the resources, and inserts container items (buckets etc.) back or drops them.
- Client side: `handleChangeList` writes the received map into the client tile's `cachedItemMap` (`warehouse.clearItemCache(); addItemsToCache(itemMap)`), so the client-side `TileWarehouse` cache is overwritten by container data; `tryGetItemClient` then predicts removals.
- `pushCraftingMatrixToInventories` is the JEI recipe-transfer hook (`core/compat/jei/AWJEIPlugin.java:67-90` registers this container).
- `transferStackInSlot` (shift-click) keeps a running total so one shift-click cannot exceed one stack of output (`updateAndCheckCraftStackOrLessInTotal`).

### 6. Core container/GUI framework (context for every screen)

- `ContainerBase` (`core/container/ContainerBase.java`) extends vanilla `Container`. It adds the player inventory through `SlotItemHandler` over the player's `ITEM_HANDLER_CAPABILITY`, so slot order is hotbar 0-8 first, then main 9-35 (36 player slots counted in `playerSlots`). It provides `sendDataToClient/Server/Gui`, `sendInitData()` (called right after `FMLNetworkHandler.openGui` in `NetworkHandler.openGui`), `handlePacketData(NBTTagCompound)`, and `refreshGui()`.
- `ContainerTileBase<T>` resolves the tile from the position in the constructor, throws if null, and has `canInteractWith` at distance squared 64.
- Client creation: `NetworkHandler.getClientGuiElement` builds the screen by constructing the same container class on the client with the same coordinates and passing it to the screen constructor (`getClientGuiElement`, lines 214-228). The client container therefore reads the client-world tile entity in its constructor. Many containers rely on that tile's client copy being in sync; where it is not, they explicitly branch `if (!player.world.isRemote)` (e.g. `ContainerMailbox`, `ContainerChunkLoaderDeluxe`).
- Opening a GUI from the client goes through `PacketGui` with `openGui=true`; server opens it and then calls `sendInitData()`.
- `GuiContainerBase` extends vanilla `GuiContainer`, with its own widget tree in `core/gui/elements` (Button, Checkbox, Label, Text, NumberInput, ItemSlot, Composite, CompositeScrolled, CompositeItemSlots, ProgressBar and others), its own viewport/scissor handling and `initElements()` / `setupElements()` lifecycle; `refreshGui()` re-runs `setupElements`.
- The warehouse screens use virtual item slots (`ItemSlot` widgets in `CompositeItemSlots`) rather than real container slots; clicks are sent as NBT requests, so they bypass vanilla `slotClick`.

### 7. Worksite, mailbox, generator and chunk-loader screens

- Worksite inventory screens (`ContainerWorksiteBase` and subclasses): slots are `SlotItemHandler` over `ItemStackHandler`s on the tile (`mainInventory` 27 slots plus per-site `plantableInventory`, `miscInventory`, `foodInventory`, `toolInventory`). Positions are computed with label rows (`topLabel`, `frontLabel` ...); `GuiWorksiteBase` draws labels and three buttons (set sides, adjust bounds, alt controls). `transferStackInSlot` returns `EMPTY` after merging (the "wtf" comments reflect that the shift-click contract is not followed).
- Slot index conventions: tile slots first (index 0..n-1), then player slots; `tileEntitySlots` and `playerSlots` are used as offsets in `mergeItemStack`. `ContainerWorksiteAutoCrafting` hard-codes ranges from `craftMatrix.getSizeInventory()`.
- Side selection: `TileWorksiteBoundedInventory` maps four machine sides (`RelativeSide`) to named inventories (`sideInventories`) and answers `getCapability(ITEM_HANDLER_CAPABILITY, facing)` from the map; `ContainerWorksiteInventorySideSelection` edits that map.
- Bounds screens: `GuiWorksiteBoundsAdjust` (264 lines) lets the player move bounds along axes with buttons and, for farm worksites, toggle a per-block "checked" byte map; edits are held client-side and sent once on close (`guiClosed`). Warehouse uses the same screen (`GuiWarehouseControl` button opens id 46).
- Mailbox: `TileMailbox` holds two `ItemStackHandler(18)` (send, receive) and per-side lists `sendSides` and `receivedSides`; `getCapability` returns the receive handler on receive sides and the send handler on send sides. `ContainerMailbox` has the most elaborate server-to-client diff in the slice (names, sides, two flags, public and private name lists from `MailboxData`) and name add/delete messages. `autoExport` is stored and shown but `TileMailbox` has a `TODO` and no implementation. The container's change detection compares lists that are the same reference as the tile's (`sendSides = tileEntity.sendSides`), so list changes are never detected there; a missing null check on `tag` in the `receivedSides` branch could not be confirmed (unverified). The report's cited line 249 is the name-change branch, which creates the tag before use. Treat as upstream defects, not behaviour to port.
- Stirling generator: server polls three values and sends `listener.sendWindowProperty`; client container overrides `updateProgressBar` (ids 0-2). Energy is scaled to an int per mille.
- Chunk loader deluxe: server keeps a `Set<ChunkPos>`; client toggles with a "forced" message; the tile re-sends the whole list to all viewers on each change.

### 8. Things that will be slow or fragile at scale

| Issue | Where | Effect |
|---|---|---|
| Every lookup builds an `ItemHashEntry` (copy stack, serialise to NBT, hash the compound) | `ItemQuantityMap.getCount(ItemStack)`, `WarehouseStorageMap.getDestinations`, `TileWarehouseStorage.getQuantityStored` | Heavy allocation. `tryFillFromRequest` calls `getQuantityStored` once per storage tile, so cost is tiles x NBT serialisations per fill attempt |
| `WarehouseStorageMap.getOrCreateStorageSet` uses `computeIfAbsent` for lookups as well as registration | `WarehouseStorageMap.java:56-59` | Every distinct item ever routed leaves an empty `HashSet` in `storageMap` (unbounded growth) |
| `getDestinations()` allocates a list of all tiles per call, then each tile is asked in turn | `TileWarehouse.tryGetItem`, `decreaseCountOf`, `tryFillFromRequest` | O(storage tiles) per operation, linear scans, HashSet order so fill order is not stable |
| Container diff sends full NBT per changed key and rescans the full map on each flagged tick | `synchItemMaps` x3 | O(distinct items) per viewer per change; the first sync sends the entire inventory as one compressed NBT packet (can reach large sizes with many distinct NBT items) |
| `updateViewers()` called on every cache change, sometimes twice (`TileWarehouse.tryAddItem` calls `changeCachedQuantity` then `updateViewers`) | `TileWarehouseBase.changeCachedQuantity` | Each call also hits all stock viewers (`BlockTools.notifyBlockUpdate`, a full tile update packet to tracking players) and all stock linkers (`notifyNeighbors` plus block update), per item moved |
| Storage tile `markDirty` on each insert/extract | `TileWarehouseStorage` | Chunk saves rewrite the whole per-tile map (list of `{item NBT, qty}`) |
| `searchForController` scans a 33 x 9 x 33 area; the stock linker retries every 40 ticks until linked | `TileControlled`, `TileWarehouseStockLinker.updateTile` | Fine normally; wasted work for orphaned tiles |
| `TileWarehouse.getItemHandler()` builds a new `NonNullList` copy of every item (as stack-sized chunks) per crafting click | `TileWarehouse.java:158` | O(items) per slot click in the crafting station |
| Keys include damage and full NBT | `ItemHashEntry` | Damaged tools, enchanted books, named items each become their own key |
| Debugger | `WarehouseDebugger.tick` | Only active when `/awwarehouse startdebug` was used (data is empty otherwise) |
| Client prediction | `tryGetItemClient` mutates the client cache | Can drift until the next change list; corrected by the next diff |

## Dependencies

From other Ancient Warfare modules:

| Needs | Used for |
|---|---|
| Core: `ContainerBase`, `ContainerTileBase`, `ContainerCraftingRecipeMemory`, `GuiContainerBase`, GUI elements (`Button`, `Label`, `Text`, `NumberInput`, `Checkbox`, `ItemSlot`, `CompositeScrolled`, `CompositeItemSlots`, `ProgressBar`), `NetworkHandler`, `PacketGui` | whole container and GUI layer |
| Core: `InventoryTools` (merge, remove, insert, `ComparatorItemHashEntry` with `SortType`/`SortOrder`), `ItemQuantityMap`, `ItemHashEntry`, `InventorySlotlessBasic` | item model and sorting |
| Core: `TileUpdatable`, `CraftingRecipeMemory`, `AWCraftingManager`, `ICraftingRecipe`, `ItemResearchBook`, `SlotResearchCrafting`, `ResearchCraftingElement` | crafting station and auto crafting; research gating |
| Core: `IInteractableTile`, `IBlockBreakHandler`, `IOwnable`, `Owner`, `IWorker`, `ITorque`, `IWorkSite`, `WorldTools`, `BlockTools`, `NBTHelper`, `AWGameData`, `RelativeSide`, `BlockRotationHandler` | tile interaction, ownership, helpers, side mapping |
| Automation (other slice): `TileWorksiteBase/Bounded/BoundedInventory`, `WorkSite*` farm tiles, `TileAutoCrafting`, `TileMailbox`, `MailboxData`, `TileStirlingGenerator`, `TileChunkLoaderDeluxe`, `AWChunkLoader`, `BlockWarehouse*`, `ItemBlockWarehouseStockLinker`, TESRs `WarehouseStockViewerRenderer`, `WarehouseStockLinkerRenderer` | tiles that the containers edit, blocks, renderers |
| NPC: `NpcWorker`, `NpcAIPlayerOwnedWork`, `NpcAIPlayerOwnedCourier`, `RoutingOrder` (they call `IWorkSite`/item-handler capability; no compile-time reference from the slice) | power the warehouse and move items through interfaces |
| Vehicle: `GuiVehicleStats` is registered by the same client proxy (not this slice) | none for this slice |
| Structure: `StructurePluginAutomation` registers `WAREHOUSE_CONTROL` with the rotatable rule (outside slice) | templates that place warehouses |

Third-party mods: none required by the slice classes. JEI recipe transfer registers `ContainerWarehouseCraftingStation` in core (`AWJEIPlugin`). The worksite base, which the control block inherits, has `@Optional` RedstoneFlux interfaces (`IEnergyReceiver`/`IEnergyProvider`), so the warehouse can be powered by RF when `redstoneflux` is present. CodeChickenLib is used elsewhere in Automation (renderers), not in this slice.

Minecraft or Forge APIs that do not exist in 1.21.1:

- `net.minecraft.inventory.Container`, `ClickType`, `IContainerListener`, `sendWindowProperty`/`updateProgressBar`, `InventoryCrafting`, `InventoryCraftResult`, `InvWrapper`/`PlayerInvWrapper`/`CombinedInvWrapper` (the wrappers over `IInventory`), `player.inventory.getItemStack()/setItemStack`, `EntityPlayerMP.updateHeldItem()`.
- `NetworkRegistry.registerGuiHandler`, `FMLNetworkHandler.openGui`, `IGuiHandler`, SimpleNetworkWrapper-style `PacketBase`.
- `TileEntity` API: `hasCapability/getCapability(Capability, EnumFacing)`, `readFromNBT/writeToNBT`, `validate/invalidate`, `ITickable`, `getUpdatePacket/onDataPacket/getUpdateTag/handleUpdateTag`, `shouldRefresh`, `shouldRenderInPass`.
- `ForgeChunkManager.Ticket` (used by `TileWorksiteBounded`, `WorkSiteQuarry`, `AWChunkLoader`, `TileChunkLoaderSimple`, `TileChunkLoaderDeluxe`, core `IChunkLoaderTile`, `AncientWarfareAutomation` and `npc/tile/TileTownHall`; only the first two are worksite-related and all are outside this slice's files).
- `WorldSavedData`, `NBTTagCompound` string-key API, ItemStack NBT format (`new ItemStack(tag)`, damage values, `OreDictionary`).
- `Block` metadata and `IBlockState` meta mapping (`BlockWarehouseStorage.Size`), `getSubBlocks(CreativeTabs, ...)`, `ItemBlock` with `getMetadata`.
- `TickEvent.WorldTickEvent` (the 1.21 equivalents are level tick events; names not verified here).

## Porting notes (1.21.1 NeoForge)

Ranked hardest first. Effort scale: S under 300 lines, M 300-1,500, L 1,500-5,000, XL more than 5,000.

### 1. Replace the container/GUI framework: L (slice share about 4,200 lines; core framework is separate)

What: 22 containers and 24 screens built on `ContainerBase` + NBT `PacketGui` + a custom widget toolkit, with the GUI id registry and reflection construction.

Why hard: every one of them depends on the 1.12 `Container` slot model, ids and reflection, `IContainerListener`/window properties, `player.inventory` cursor, and hand-written widgets. In 1.21.1 this maps to `AbstractContainerMenu` with a `MenuType` per screen, `AbstractContainerScreen` for rendering, and menu opening with extra data (position) through NeoForge's menu-type extension (class and method names such as `IMenuTypeExtension` should be checked against the NeoForge 21.1 sources; unverified). The widget toolkit in `core/gui/elements` and `GuiContainerBase` (the `setupElements` rebuild-on-refresh model) has no vanilla counterpart.

Approach:

- Decide first whether to port the AW widget toolkit as a client-only library (probably in the core mod, since all five modules use it) or rewrite each screen on vanilla `Button`, `EditBox` and `ScrollPanel`-style widgets. The slice cannot move until this is decided.
- Replace `PacketGui`'s free-form NBT with typed payloads. Use `CustomPacketPayload` with a `StreamCodec` and register them through `RegisterPayloadHandlersEvent` (those names exist in NeoForge 21.1, per the porting table; verify in `docs/PORTING.md`).
- For simple screens (worksite inventories, stirling, interface) use vanilla data slots (`ContainerData` / `DataSlot`, exist in 1.21.1) for ints instead of `sendWindowProperty`.
- Keep the client menu constructed from `(containerId, Inventory, FriendlyByteBuf)` and look up the block entity by position, which is what the 1.12 code already does.

### 2. Warehouse item model and persistence (ItemHashEntry, ItemQuantityMap, storage tiles): M-L (about 1,200 lines in slice plus 350 in core)

What: key = serialised item NBT; value = int; capacity by total count; NBT save format with `ItemStack.writeToNBT`.

Why hard: item identity in 1.21.1 is item + data components (no damage meta, no `tag`, no capability NBT, no OreDictionary). `ItemStack` serialisation changed (`ItemStack.CODEC` and `OPTIONAL_CODEC` exist), and old saves cannot be read by the old format without a conversion step. Durability-based fragmentation (each damage value is a key) should be revisited, and filter matching "relaxed" semantics (OreDictionary) have to be re-expressed with item tags.

Approach:

- Define a `StackKey` record (item holder plus a hash of the components map) with a proper `equals/hashCode` that does not allocate NBT. Compare with `ItemStack.isSameItemSameComponents` (exists in 1.21.1) and hash with `ItemStack.hashItemAndComponents` (unverified; fall back to hashing item plus `getComponents()`).
- Save quantities as a list of `{stack codec, count}`; use `saveAdditional/loadAdditional` with `HolderLookup.Provider`.
- Keep per-tile capacity as a plain int total.
- Add a one-off reader for the legacy 1.12 tile NBT only if the owner decides existing worlds must migrate (open question).

### 3. Crafting station: M (279 lines container, 66 lines tile, plus dependence on research crafting in core)

What: virtual `IItemHandlerModifiable` over the cache, combined with the player inventory for JEI recipe transfer and shift-click crafting, through `ContainerCraftingRecipeMemory`, `AWCraftingManager` and the research system.

Why hard: relies on `InventoryCrafting`, `InventoryCraftResult`, `ClickType`/`slotClick` override, the `setAll`/`putStackInSlot` "opening" trick, and the recipe system. Recipes in 1.21.1 are `RecipeHolder<CraftingRecipe>`, matched with `CraftingInput`; the research-gated recipe format is a different mod-level design. JEI integration (`registerMultiRecipeTransferHandler`) would need rewriting against the 1.21 JEI API.

Approach: port after the core research and crafting memory classes; model the warehouse view as a menu-only data source (virtual `IItemHandler` still works in NeoForge) and keep the "take all resources then output" transaction on the server. Replace the client-side mutation of the tile cache with a menu-local map.

### 4. Warehouse sync protocol and scalability redesign: M (about 600 lines)

What: diff-based change lists per container; stock viewer and linker pushing block updates on every change.

Why: see the scalability table. Straight port keeps all the problems; the 1.21.1 payloads make a better design cheap.

Approach:

- One server-side per-warehouse listener with a coalesced dirty flag (flush at most once per N ticks).
- Send incremental typed payloads (list of key, delta or absolute count) and a full snapshot only on open.
- Only push stock viewer and linker updates when the value they show actually changes; the linker should compare its comparison result and only notify neighbours on change.
- Stop `computeIfAbsent` on lookups.
- Replace `HashSet` iteration order with a deterministic order.

### 5. Controller discovery and lifecycle: M (about 500 lines)

What: `TileControlled` plus `TileWarehouseBase`, with `validate/invalidate`, per-tile scan on first tick, controller position saved in NBT, controller cache rebuilt from tiles on load.

Why: 1.21 block entities use `onLoad`, `setRemoved`, `clearRemoved`, `saveAdditional/loadAdditional` and `BlockEntityTicker` in the block (not `ITickable`). The existing circular references (tile holds controller, controller holds tile sets, viewer sets in menus) must be torn down correctly on chunk unload, or they leak. The `TileWarehouse.invalidate` omission of `super.invalidate()` must not be copied.

Approach: use `onLoad` to attach, `setRemoved` to detach; keep `BlockPos`-based references where possible and resolve on demand; keep the position test for validity.

### 6. Capabilities and inventory APIs: S-M (about 300 lines touched)

What: `TileWarehouseInterface` capability, `TileMailbox` side lists, `TileWorksiteBoundedInventory` side mapping (other slice), `ItemStackHandler` usage everywhere, `SlotItemHandler`.

Port: 1.21.1 NeoForge still has `IItemHandler`, `IItemHandlerModifiable`, `ItemStackHandler`, `SlotItemHandler` and `CombinedInvWrapper`. Capability exposure changes from `getCapability(Capability, EnumFacing)` on the tile to block capabilities registered in `RegisterCapabilitiesEvent` (`Capabilities.ItemHandler.BLOCK` and `registerBlockEntity`; names believed correct for NeoForge 21.1, verify). The mailbox and interface providers become `(be, side) -> handler` lambdas. `ISidedInventory`/`IInventory` are not used by this slice except where vanilla requires it (`InventoryCrafting` in `CraftingRecipeMemory`, `TileEntityFurnace.isItemFuel` in the stirling container, which becomes the fuel-lookup API in 1.21; the exact method is not verified). Slot index conventions to preserve: tile slots first then 36 player slots with hotbar first; 1.21's `Inventory` ordering for `SlotItemHandler` over `player.getCapability(...)` is the same 0-8 hotbar, 9-35 main, but use `Inventory` slots directly for `Slot`.

### 7. Chunk loader deluxe and worksite chunk tickets: M

`ForgeChunkManager.Ticket` is gone. NeoForge has a ticket controller mechanism for forced chunks (`TicketController` and `RegisterTicketControllersEvent`, both (unverified)). The deluxe loader's 5x5 selection GUI and set of `ChunkPos` port easily; the tile ticket logic is in the worksite base and chunk loader tiles (other slice).

### 8. Mailbox GUI and `MailboxData`: M

`ContainerMailbox` (396 lines) is the most protocol-heavy container. `MailboxData` is a `WorldSavedData`, which becomes `SavedData` with a factory/codec. Rewrite the server-to-client diff as a single "mailbox state" payload sent on change, and drop the dead `autoExport` unless the owner wants it implemented.

### 9. Stock viewer / stock linker: S-M

Redstone output from `LIT` and `getEqualityHandle`; TESR/BlockEntityRenderer for text in the world; owner check by `Owner.isOwnerOrSameTeamOrFriend`. Port as-is once owner/team utilities exist. Note the compare-value-0-is-false rule must be kept to match old behaviour.

### 10. Worksite screens (13 of the 22 containers): M

Mostly mechanical once the menu pattern exists: slot grids from `ItemStackHandler`s with labels. `GuiWorksiteBoundsAdjust` (264 lines) holds the bounds editing logic and a "checked" byte map; keep protocol as a single "apply on close" payload.

### 11. Stirling generator menu: S

Replace window properties by `ContainerData` (3 ints). Fuel validity check uses `TileEntityFurnace.isItemFuel`; the 1.21.1 replacement is the furnace fuel data (not verified which method name).

### Cannot be ported as-is

- The warehouse is not "ported as-is" in any layer: item keys, sync protocol, and GUI toolkit change. Behaviour (bounds, multiblock, requests) can stay.
- Old saved worlds: ItemStack NBT, ids and damage values differ; tile NBT cannot be read without a conversion decision.
- `ForgeChunkManager.Ticket` and the GUI id/reflection handler have no equivalent.

## Candidates for capttrackerlib

Generic code another unrelated mod could reuse:

| Candidate | Why generic | Notes |
|---|---|---|
| `ItemQuantityMap` / `StackKey` (component-aware stack key with counts, NBT/codec round trip, `getItems()` compaction) | Any mod with bulk storage, stock tracking or recipe-availability needs a count-per-item map | `ItemStacks` already exists in the library; check overlap before adding |
| `InventorySlotlessBasic` (capped count store) | Simple slotless capacity container | tiny |
| Menu-to-client incremental "map sync" helper (diff a `Map<Key,Integer>` and emit a delta payload) | The change-list pattern appears in three containers; reusable for any virtual inventory screen | needs typed payload registration in `ModRegistrar` |
| Search/sort helpers behind `GuiWarehouseControl` (name+tooltip text index, `@modid` search, sort by name/quantity/damage) | Generic inventory-browser logic | `ComparatorItemHashEntry` lives in core `InventoryTools`; client-only |
| Virtual `IItemHandlerModifiable` over a count map (`WarehouseItemHandler` pattern) | Lets crafting/pipe code treat a count store as slots | depends on a callback interface for real insert/extract |
| Stock comparison filter (`EqualitySignType` plus compare) | Redstone-on-inventory-level logic | small |

Should stay inside Ancient Warfare:

- `TileControlled`/`TileWarehouseBase` multiblock attach logic: it is entangled with the worksite base, NPC energy and the bounds system.
- The crafting station (research-gated recipes, `ContainerCraftingRecipeMemory`).
- Mailbox, chunk loader and worksite side mapping screens (game-specific).
- The `GuiContainerBase` widget toolkit: generic in principle, but it belongs in the shared AW core, not the library, unless the owner wants a full GUI library.

## Open questions

1. Must existing 1.12.2 worlds (warehouse tile NBT, mailbox data) be loadable in the port, or is a clean start acceptable? This decides whether a legacy NBT reader for `ItemStack` data is needed.
2. Should the custom widget toolkit (`core/gui/elements`) be ported as a shared library or replaced by vanilla widgets? This changes the effort of all 24 screens.
3. Should control/storage blocks gain an item-handler capability (so hoppers and pipes can use the warehouse) or stay interface-only? The original exposes none.
4. Does `TileWarehouse.invalidate()` skipping `super.invalidate()` have a purpose, or is it an upstream bug? Not determined from the code.
5. `TileWarehouseBase.shouldRecount` is never set to true in the slice (nothing outside it was checked), and `autoExport` in `TileMailbox` is unimplemented. Should either be kept?
6. Filter semantics differ: interface filters use relaxed matching (OreDictionary, ignore durability) while the warehouse keys are exact. Which should the port use, tag-aware or exact component match?
7. I did not analyse the research recipes, blockstate models, manual page, lang strings, TESRs or the `GuiWorksiteBoundsAdjust` rendering in depth; they are listed but not enumerated. The recipe ingredient costs are unknown to this report.
8. Whether the GUI sizes (178 x 240 and similar, fixed-pixel layouts) must be preserved or may use new textures is a design decision; textures under `assets/ancientwarfare/textures/gui` were only listed (`guibackgroundlarge.png`, `guibuttons1.png`, `guibuttons2.png`).

## Verification

Checked by a second pass that did not rely on this report's lists.

Checked:
- File inventory: `find` over `automation/tile/warehouse2`, `container`, `gui`. Found 19 + 23 + 25 = 67 files (the report said 18 and 66). Line totals 2,642 / 2,224 / 2,004 = 6,870 match.
- GUI ids and registration: `core/network/NetworkHandler.java:74-137` against `registerContainer` (`AncientWarfareAutomation.java`, 21 entries) and `registerGui` (9 in `block/*.java`, 12 in `proxy/ClientProxyAutomation.java` excluding `GUI_VEHICLE_STATS`). The 21 container ids and 21 screen ids in the table match. Id 28 is unused.
- Tile registrations: `init/AWAutomationBlocks.java:173-191` (eight warehouse tiles), plus the auto-crafting and mailbox tiles at 194 and 197 (now noted).
- Command `/awwarehouse` in `automation/command/CommandWarehouse.java`.
- Capacities: small `9 * 64` (`TileWarehouseStorage.java:68`); medium `2x` and large `3x` the addition size.
- GUI filter limits: interface < 9, stock linker < 4, stock viewer < 4, storage < 10 (client GUI checks).
- Stock viewer bounds widened by 1 horizontally (`TileWarehouseStockViewer.java:73-75`).
- Controller search box 33 x 9 x 33 (`TileControlled.java:43-44`); stock linker retry cooldown 40 (`TileWarehouseStockLinker.java:169`).
- Crafting station finds the warehouse with `WorldTools.getTile(world, pos.down(), TileWarehouse.class)` (`TileWarehouseCraftingStation.java:26`).
- Interface capability returned for every facing (`TileWarehouseInterface.java:187-203`).
- Worksite action torque cost `getEnergyPerActivation / 4` (`TileWarehouseBase.java:240-241`).
- `WarehouseDebuggerData` read and write are empty (`WarehouseDebuggerData.java:26-33`).
- `shouldRecount` is set only in its own reset path (`TileWarehouseBase.java:36, 268-269`); nothing sets it true in the slice.
- `getAvailableSpaceFor` ignores filters (`TileWarehouseStorage.java:103-104`).
- Crafting station `getWorkType()` returns CRAFTING (`TileWarehouseBase.java:456-458`); NPC work calls `addEnergyFromWorker` (`NpcAIPlayerOwnedWork.java:99`).
- No `warehouse` string in `npc/` (grep empty). Courier handler lookup via `ITEM_HANDLER_CAPABILITY` (`NpcAIPlayerOwnedCourier.java:114-129`).
- `ItemHashEntry` keyed on NBT compound hash and equals (`ItemHashEntry.java:29-46`).
- `ContainerMailbox` shares list references with the tile (`sendSides` and `receivedSides` assignments).
- Mailbox `autoExport` has a TODO and no logic (`TileMailbox.java:30`).
- Warehouse GUI opens id 46 (`GuiWarehouseControl.java:96`).
- Resource counts: 6 warehouse blockstates in `resources/assets/ancientwarfare/blockstates/automation/`, 8 research recipe files in `research_recipes/automation/`.
- Modern API names: `Capabilities.ItemHandler.BLOCK`, `RegisterCapabilitiesEvent`, `onLoad`/`setRemoved`, `saveAdditional`/`loadAdditional`, `HolderLookup.Provider`, `ContainerData`/`DataSlot`, `SavedData`, `ItemStack.isSameItemSameComponents`, `CombinedInvWrapper`, `IItemHandler`, `ItemStackHandler`, `SlotItemHandler` are believed to exist in NeoForge/Minecraft 1.21.1. Not checked against sources (no network use; no NeoForge sources available in the slice).

Changed in the report:
- Scope table: tile/warehouse2 file count 18 to 19; total 66 to 67.
- Scope paragraph: class counts now separate package-info from top-level classes (18 tile, 22 container, 24 gui).
- Tile section: noted the auto-crafting and mailbox tile registrations (AWAutomationBlocks 194, 197) that sit outside the warehouse set.
- `TileWarehouse.invalidate` omission: line 27 corrected to 23 (the method starts there).
- `ItemQuantityMap.removeAll` NPE: line 21 corrected to 35-38 (unboxing at line 37).
- Mailbox null-check claim: marked unverified; the cited line 249 is not the receivedSides branch.
- ForgeChunkManager users list widened (it is used beyond the worksite base and chunk loader, including core `IChunkLoaderTile` and `npc/tile/TileTownHall`).
- `TicketController` and `RegisterTicketControllersEvent` marked (unverified).

Not settled:
- Whether `TileWarehouse` and `TileWarehouseStorage` expose no capabilities at all. The base `TileWorksiteBounded` was not checked for capability overrides, so "offer none" is unconfirmed.
- Whether the mailbox receivedSides branch has any null-check defect.
- Exact 1.21.1 names not confirmed against NeoForge sources (listed above). `RegisterPayloadHandlersEvent`, `IMenuTypeExtension`, `TicketController` and the furnace fuel method remain uncertain as stated in the report.
- The research recipe, blockstate and manual contents were counted, not read.
