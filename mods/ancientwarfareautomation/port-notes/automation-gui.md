# automation-gui: Automation menus and screens

Slice key `automation-gui`, module `ancientwarfareautomation`. Ported from `automation/container/**` (22 classes) and
`automation/gui/**` (24 classes), report 04 (sections 6, 7 and the GUI table). Nothing was compiled or run. Every Minecraft and
NeoForge name was looked up in `analysis/mc-sources`; the Core toolkit classes were read, not assumed.

## What was ported

Package `io.github.capttracker.ancientwarfareautomation` (1.12.2 name in brackets).

`menu` (all extend `AwMenu` through `AutomationMenu` / `BlockEntityMenu`; every menu has the two constructors
`(int, Inventory, RegistryFriendlyByteBuf)` for the client and `(int, Inventory, <block entity>)` for the server):
- Base and helpers (new): `AutomationMenu<T>` (first-state push, `detectChanges()`, `isRequestAllowed()`), `ItemMapSync`
  (the warehouse change-list protocol), `WarehouseRequests` (slot-click and filter messages, validation; package private).
- Worksites: `WorksiteMenuBase<T>` (ContainerWorksiteBase), `QuarryMenu`, `TreeFarmMenu`, `AnimalFarmMenu`, `CropFarmMenu`,
  `FishFarmMenu`, `FruitFarmMenu`; second screens `AnimalControlMenu`, `FishControlMenu`, `QuarryBoundsMenu`,
  `BoundsAdjustMenu` (ContainerWorksiteBoundsAdjust), `InventorySideMenu` (ContainerWorksiteInventorySideSelection);
  `AutoCraftingMenu`.
- Other blocks: `StirlingGeneratorMenu`, `ChunkLoaderDeluxeMenu` (the name the base slice's block entity already uses), `MailboxMenu`.
- Warehouse: `WarehouseControlMenu`, `WarehouseStorageMenu`, `WarehouseInterfaceMenu`, `WarehouseStockViewerMenu`,
  `WarehouseStockLinkerMenu`, `WarehouseCraftingStationMenu`.

`init.AwAutomationMenus`: the 21 `MenuType` holders (registry names `worksite_quarry`, `worksite_tree_farm`, `worksite_animal_farm`,
`worksite_crop_farm`, `worksite_fish_farm`, `worksite_fruit_farm`, `worksite_animal_control`, `worksite_fish_control`,
`worksite_quarry_bounds`, `worksite_bounds`, `worksite_inventory_sides`, `worksite_auto_crafting`, `stirling_generator`,
`chunk_loader_deluxe`, `mailbox`, `warehouse_control`, `warehouse_storage`, `warehouse_interface`, `warehouse_stock_viewer`,
`warehouse_stock_linker`, `warehouse_crafting_station`), `load()`, `registerEvents(modBus)` (hands the constructors to the open hooks) and
the server side openers `openInventorySides`, `openBounds`, `openQuarryBounds`. The class comment lists the 1.12.2 GUI id of each holder.

`client.screen` (all extend `GuiContainerBase`, built on the Core toolkit): `GuiWorksiteBase<M>`, `GuiWorksiteQuarry`,
`GuiWorksiteTreeFarm`, `GuiWorksiteAnimalFarm`, `GuiWorksiteCropFarm`, `GuiWorksiteFishFarm`, `GuiWorksiteFruitFarm`,
`GuiWorksiteAnimalControl`, `GuiWorksiteFishControl`, `GuiWorksiteQuarryBounds`, `GuiWorksiteBoundsAdjust`,
`GuiWorksiteInventorySideSelection`, `GuiWorksiteAutoCrafting`, `GuiStirlingGenerator`, `GuiChunkLoaderDeluxe`, `GuiMailboxInventory`,
`GuiMailboxInventorySideSetup`, `GuiMailboxNameSelect`, `GuiWarehouseControl`, `GuiWarehouseStorage`, `GuiWarehouseInterface`,
`GuiWarehouseStockViewer`, `GuiWarehouseStockLinker`, `GuiWarehouseCraftingStation`, plus `ResearchCraftingElement` (the recipe arrows and book
icon that the original shared between the two crafting screens).

`client.AutomationScreensClient`: registers the 21 screens (`@EventBusSubscriber(value = Dist.CLIENT, bus = MOD)`).

Assets: none needed. The three GUI textures (`guibackgroundlarge`, `guibuttons1`, `guibuttons2`) are Core's. The lang keys are in
`automation-gui.lang.json` (49 keys, script `analysis/port/automation_gui_lang.py`).

## Wiring needed in shared files

1. `AncientWarfareAutomation.java` (mod constructor, before `REG.register(modBus)`):
   ```java
   AwAutomationMenus.load();
   AwAutomationMenus.registerEvents(modBus);   // import ...init.AwAutomationMenus
   ```
   `registerEvents` sets the `WorksiteMenus` factories (nine kinds), the seven `WarehouseMenus` factories and
   `TileStirlingGenerator.setMenuFactory`; until it runs, right-clicking those blocks does nothing. Nothing else has to be added to the block
   entities: the hooks they call already exist. `ChunkLoaderDeluxeBlockEntity` opens `ChunkLoaderDeluxeMenu` directly.
2. Core `menu/AwMenu.java` (the change already requested by `core-gui-toolkit.md`): `refreshGui()` (protected final) and `setRefreshListener(Runnable)`.
   These menus call `refreshGui()` everywhere the original called `gui.refreshGui()`, and `GuiContainerBase.bindMenu()` calls `setRefreshListener`.
   Nothing in this slice compiles without it.
3. `lang/en_us.json` of the module (`assets/ancientwarfareautomation/lang/en_us.json`): merge `automation-gui.lang.json`. The warehouse lang json holds the
   `equality.*` keys the stock linker screen shows; `guistrings.automation.animal_control` and `fish_control` are in the worksites json. Note that the
   `guistrings.*` keys are looked up by name wherever they are defined, they do not have to be in the module's own file.
4. The `block` classes `ChunkLoaderSimpleBlock` and `ChunkLoaderDeluxeBlock` imported by `AwAutomationBase` are not on disk yet (base slice contract). The deluxe block's right click
   must reach `IInteractableTile.onBlockClicked` (it does through `BaseBlock`), which opens `ChunkLoaderDeluxeMenu`.
5. `docs/PORT-STATUS.md`: automation-gui slice done, not built.

## How the menus work (differences from the original)

**Opening.** `WorksiteMenus.open`, `WarehouseMenus.open*` and the stirling/chunk loader block entities open the menu with the block position as extra
data; the client constructor resolves the block entity with `BlockEntityMenu.resolve`. Screens that need to open another menu (side screen, bounds
screen, second screens, the way back) send a request over the NBT channel and the **server** opens the menu (`AwAutomationMenus.open*`,
`WorksiteMenus.open`). The original screen called `NetworkHandler.openGui` itself. A screen that does this returns `false` from
`onGuiCloseRequested` so it does not send a container close of its own: the server's close and open packet replaces it (a client close sent after the server's open would
close the new menu, `handleContainerClose` closes whatever is open).

**First state.** Several openers do not call `sendInitData()` after `openMenu`. `AutomationMenu` therefore sends the starting state itself on the
first `broadcastChanges()` unless an opener already called `sendInitData()` (the warehouse hooks do). The server work of the original
`detectAndSendChanges` is `detectChanges()`.

**Both directions of `handlePacketData` are split by side.** The original handled one key set on both sides (the server applied whatever a client sent, for
example the whole `changeList` or all mailbox name lists). Now the server handles only what the screen sends, and the client only what the server sends. Every server
branch first checks `isRequestAllowed()` (server copy, block entity still there and within 8 blocks).

**Validation of what a client sends** (the original used it as it came):
- Item stacks are parsed with `ItemStack.parseOptional` (unknown item gives empty), counts clamped to 1..max stack; filter items are cut to one item.
- Lists are cut: storage filters 10, interface 9, stock viewer 4, stock linker 4. Interface quantity 0..100,000, linker compare value 0..100,000,000, stock
  quantities are reset (the block entity counts them), the equality sign index is range checked by the block entity's `setEqualitySignType`.
- Animal limits 0..1000. Quarry height 1..distance to the world bottom. Bounds: same y as now, x and z extent at most `getBoundsMaxWidth()`, still within that distance of the
  block, only for a block entity that already has bounds; target map exactly 256 entries of 0 or 1.
- Side screen: the machine side must be one the worksite has, the inventory one it owns or none (the original indexed the enum with any number).
- Chunk loader: only chunks of the 5 x 5 grid around the block, never the centre chunk.
- Mailbox: only keys the screen sends; sides must be valid ordinals; names 1..64 characters without control characters, an empty name means "no name".
- Warehouse click: an empty click with nothing on the cursor and no item is dropped before it reaches the block entity.

**Warehouse change lists** (`ItemMapSync`): same format (`changeList`, `{item, quantity}`, 0 = removed) but sent in packets of at most 48 entries (a client-bound custom payload is limited to 1 MiB,
the first sync of a big warehouse is not). The server-side copy drops entries that reach 0 (the original kept them with 0).

**Crafting menus.** The original `ContainerCraftingRecipeMemory` is Core's `CraftingMemoryController` now (as in the engineering bench menu). The auto crafting table shows the result but cannot
give it out; its "Craft" button sends `craft` and the server calls `tryCraftItem()`. The warehouse crafting table takes the ingredients through the warehouse's virtual item handler, the template items
are used up if the warehouse cannot supply the recipe (same as the bench port). The result is computed on the server only (no client prediction of the warehouse content, the original's `tryGetItemClient` path).
`setAll` / `putStackInSlot` "opening" hacks are not needed (the server decides the recipe). Both implement `ICraftingContainer`.

**Stirling generator.** The three values go over the NBT channel (`energy` as a double, `burnTime`, `burnTimeBase`), not through window properties: `ClientboundContainerSetDataPacket` carries shorts and a modded fuel can burn longer than 32,767 ticks.

**Mailbox.** The sub screens share the menu; the slots are hidden with `Slot.isActive()` (the original moved them 10,000 pixels up). `MailboxMenu` keeps its own copy of the side lists, so changes of the block entity's lists are noticed
(the original compared the tile's list with itself); the server also invalidates the block's capabilities when the sides change.

**Stock viewer and linker.** The client menu rebuilds its filter list from the block entity when it differs from it (the block entity's update packet carries the live counts and calls the menu). The original
viewer re-read the list on every setup and the linker never did (its counts never moved).

### Fixed bugs of the original (obvious and local)

- Mailbox `detectAndSendChanges`: change detection compared a list with itself, and the received-sides branch could throw a NullPointerException (no tag created). Both fixed.
- Fish control: the change check never stored the new values and resent every tick; now it does.
- Warehouse sort order checkbox started with the "Desc." label even when the order was ascending; the label follows the state now.
- Warehouse control: the sort values are sent with the first sync, not only when the storage size happened to change.
- Warehouse and storage menus kept zero entries in their sent map forever.
- The bounds screen's server side took any min and max; see validation.

## Not ported

- `Mouse.getX/setCursorPosition` around the mailbox sub screens (LWJGL2); the pointer is not moved.
- JEI recipe transfer for the two crafting menus (`ICraftingContainer` is implemented, the hook exists in Core).
- The "wtf" return of the worksite, stirling and mailbox `transferStackInSlot`: kept as it was (merge and report empty, mailbox no shift click).
- `GUI_VEHICLE_STATS` (vehicle module).

## Unconfirmed API / things to check at the first build

- `AwMenu.refreshGui()` and `setRefreshListener(Runnable)` do not exist yet (wiring item 2).
- `GuiContainerBase` calls `Slot.isActive()` in `renderBg`; vanilla's `AbstractContainerScreen.render`/`findSlot` also honour it (read in the sources), so the hidden mailbox slots neither draw nor take clicks.
- Method references `IMenuTypeExtension.create(QuarryMenu::new)`: each menu has two three-argument constructors; the reference resolves by the buffer parameter type.
- `Item.TooltipContext.of(Minecraft.getInstance().level)` is given a nullable level (the signature allows it).
- Generic inference of `event.<Menu, Screen>register(...)` follows Core's `CoreScreensClient`.
- `ItemMapSync` and the menus rely on `ItemQuantityMap.putEntryFromNBT`, `writeEntryToNBT` and `getTotalItemCount` of Core as written.
- Everything the earlier slices expose (`WorksiteMenus`, `WarehouseMenus`, the `Viewer` interfaces, `FarmWorksiteBlockEntity.getTargetMap`, `QuarryBlockEntity.height`, `AnimalFarmBlockEntity.setMaxCounts`, `MailboxData`) is used as written there.

## Rework candidates

- Filter lists are sent as full item NBT; a filter holding a big item (a full shulker box) can push a client-to-server payload over the 32 KiB limit of custom payloads. A filter should send the item id and a trimmed component patch, or the filter should be chosen server side from the cursor stack.
- The warehouse control screen resends nothing but the diff; a search-and-sort index on the server or an item registry id table would shrink the first sync.
- The three warehouse menus rescan the whole map on every flagged tick (`ItemMapSync.sendChanges`); a per-listener dirty set from the block entities would avoid it (report 04, section 8).
- The bounds screen holds the 16 x 16 target map but the farms' areas are bounded by upgrades; the map could be sent in a smaller form.
- The warehouse interface quantity cap (100,000) and the animal cap (1000) are guesses at "large enough"; no original limit existed.
- A shared "back to the worksite" request (side screen, second screens) could be one payload type instead of per menu keys.
- Mailbox `autoExport` is still saved and shown but has no function (as in the original, report 04 section 7).
