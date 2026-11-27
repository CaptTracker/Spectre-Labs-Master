# Core screens (slice key: core-screens)

Ported from `core/gui/GuiResearchBook`, `GuiBackpack`, `GuiInfoTool`, `core/gui/research/*`, `core/gui/manual/*` (+ `elements`), `core/manual/*`, `core/gui/options/OptionsGuiFactory`, `ContainerResearchBook`, `ContainerManual`, `ContainerInfoTool`, and the manual content under `registry/manual`. Everything is written against the toolkit in `client.gui`. Nothing compiled or run; every Minecraft/NeoForge name was checked in `analysis/mc-sources`.

## Files (under `src/main/java/io/github/capttracker/ancientwarfare`)

- `client/screen/`: `GuiResearchStation`, `GuiResearchStationSelection` (queue picker), `GuiResearchBook`, `GuiBackpack`, `GuiInfoTool`, `ResearchScreenData` (package-private client lookups: goal registry, recipes, local research).
- `client/screen/manual/`: `GuiManual`, `Page`, `Paging`, `ElementWrapperFactory` (pagination), `IElementWrapperCreator`; `elements/`: `BaseElementWrapper`, `Text/Heading/Image/Item/TableOfContents` wrappers.
- `client/CoreScreensClient` (screens, manual parser + language, manual opener, config screen).
- `manual/`: `IContentElement`, `TextElement`, `HeadingElement`, `ImageElement`, `ItemElement`, `TableOfContentsElement`, `ManualContentRegistry` (+ nested `ManualContentParser`, name `manual_content`). No client imports.
- `menu/`: `ResearchBookMenu`, `InfoToolMenu`, `ManualMenu`, `ScreenOnlyMenu` (client-only empty menu).
- `init/AwCoreScreens`: `RESEARCH_BOOK_MENU`, `INFO_TOOL_MENU`, `load()`, `registerEvents(modBus)` (installs `InfoToolItem.setGuiOpener(InfoToolMenu::open)`).
- Assets: `assets/ancientwarfare/registry/manual/{en_us,de_de,zh_cn}/*.json` (26 files, byte copies, read by `RegistryLoader.CLIENT`), `textures/gui/manual.png` (copy), `textures/gui/manual/images/*.png` (49 images). Scripts: `analysis/port/core_screens_assets.py`, `core_screens_images.ps1`, `core_screens_lang.py`. Lang keys: `core-screens.lang.json`.

## Wiring needed in shared files

1. `AncientWarfareCore.java`, constructor, before `REG.register(modBus)`:
   ```java
   AwCoreScreens.load();
   AwCoreScreens.registerEvents(modBus);
   ```
   (import `init.AwCoreScreens`). Also still needed from earlier slices: `INFO_TOOL` / `MANUAL` as `InfoToolItem` / `ManualItem` (core-util notes, item 1).
2. `client/AwClient.java`: **delete the whole `registerScreens` method** (or its two registrations). `CoreScreensClient` registers the backpack, the engineering bench and the research bench; registering a menu type twice throws `IllegalStateException`. `PanelScreen` and `client/BackpackScreen` become unused and can be deleted; `PanelScreen` is still used by nothing else once the research bench stand-in from core-research-station wiring item 3 is NOT added (do not add it).
3. `menu/AwMenu.java`: the refresh listener from `core-gui-toolkit.md` (wiring item 1): `setRefreshListener(Runnable)` public final and `refreshGui()` protected final. `GuiContainerBase`, `ResearchStationMenu` and `GuiResearchStationSelection` call them.
4. `item/ResearchBookItem.java` (`use`): replace `listResearch(serverPlayer, researcher)` with `ResearchBookMenu.open(serverPlayer);` (the chat listing was a stand-in). Import `menu.ResearchBookMenu`.
5. `lang/en_us.json`: merge `core-screens.lang.json` (21 `guistrings.*` keys, most already present; `research.*` goal names, already present for 31 of them; the two `item.ancientwarfare.*` keys are only named in menu titles and already exist).
6. Config screen: nothing to add; `CoreScreensClient.onClientSetup` registers `IConfigScreenFactory` with `ConfigurationScreen::new` (the one line the spec mentioned) for the spec from `AWCoreStatics.register(modBus, container)`. If it is preferred in the mod constructor, use `container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new)` inside a client-only guard instead and delete it from `CoreScreensClient`.

## Behaviour and decisions

- **Sub-screens and slot hiding.** The original hid a menu's slots by moving them 10000 px away (`removeSlots`). `Slot.x/y` are final now, so the queue picker (`GuiResearchStationSelection`) and the manual use a client-only `ScreenOnlyMenu` (no slots) and read the bench's `ResearchStationMenu` through a reference. The bench menu's refresh listener is pointed at the picker in `init()`. The book shown over the bench gets its own client-only `ResearchBookMenu(-1, inventory)`, exactly like the original's `new ContainerResearchBook(...)`. Closing either goes back to the bench screen with `showScreen(parent)` (the bench menu stays open; `GuiContainerBase.removed` is skipped).
- **Manual** is client only: `ManualItem.setClientOpener` opens `GuiManual(new ManualMenu(inventory))`; `GuiManual.onClose` just calls `setScreen(null)` (a container screen would send a close packet for the player's inventory). The 1.12.2 `PacketManualReload` / `/awutils reloadmanual` path keeps working through `CoreMiscClient`; F3+T and language changes reload the manual too (`beginLoad` clears the tables).
- **Manual images**: 1.21.1 reads only PNG (`NativeImage.read` checks the PNG header) and requires lower-case resource paths, so the 49 jpg/JPG/png images were converted to lower-case PNG (with System.Drawing; the one binary change from "copied unchanged"). The JSON still says `/images/Ballista1.jpg`; `ImageElementWrapper.textureOf` maps any such path to `textures/gui/manual/images/ballista1.png`. This also fixes the zh_cn files, which name `.png` files that never existed.
- **Manual items**: resolved lazily and skipped if unknown (the files name `ancientwarfareautomation:*`, `ancientwarfarenpc:*` etc., which may not be registered). The 1.12.2 `data` value is dropped; `ItemElement.registerLegacyVariant("ancientwarfareautomation:worksite_upgrade", 3, "<new id>")` lets a module map variants (for the Automation and Vehicle slices: 33 worksite upgrade entries, 8 vehicle upgrade entries, `diamond_hammer` data 3 is a damage value and just shows the hammer).
- **Fixes of original bugs (local):** `TextElementWrapper` skipped `linesCurrent` lines when it placed none of them (one line of room left), losing a line; a heading as the last element of a category was dropped; research progress `time / total` divided by zero when both were 0. Kept as in the original: heading height counts its padding twice, `padding = min(0, ...)` in the image wrapper is always 0.
- **Research queue picker**: the 1.12.2 screen asked `ResearchTracker.getResearchableGoals(world, name)` (server data on a single-process client). The client only has the local player's research (`Research.localPlayerKnows`, `ResearchSyncPayload`), so the list is computed locally from the local player's finished research plus the bench's current goal and queue, and only when the book belongs to the local player. For another player's book nothing is offered. The server still validates every request (`ResearchStationMenu.changeQueue`). `PacketResearchUpdate` is gone (the bench menu's `enqueueResearch/dequeueResearch` replace it).
- **Research book** shows what the local player knows (`useResearchSystem` is read from the client's own config copy, like the original). Recipes come from the synced recipe manager (`AWCraftingManager.getResearchRecipes`, config-disabled outputs filtered), goals from the synced `ResearchGoal` registry. The recipe grid uses `ResearchRecipe.width()/height()/slots()`, which keeps the shapeless 3x3 row-major fill of the original; ingredients show the first accepted item with its count (no cycling through a tag's items).
- **Info tool**: the screen never lets a click move items (`slotClicked` override, plus the menu ignores `clicked`). Clicking a slot sends `printItem` (slot index) over the NBT channel; the server checks the slot and that an info tool is still in the opening hand, then calls `InfoToolItem.printItemInfo`.
- **Backpack**: `GuiBackpack` 178 x (114 + rows * 18). The hotbar-key guard of the original is already in `BackpackMenu.clicked`.
- **Not ported**: `GuiConfig`/`OptionsGuiFactory` (replaced as above), `Mouse.setCursorPosition` of the queue picker (the original moved the cursor back to where it was when switching screens), `GuiFileSelect` (toolkit slice).

## Unconfirmed API / runtime behaviour

- No name is unconfirmed in the sources. Runtime points: (a) `AwMenu.setRefreshListener` exists only after wiring item 3. (b) `ServerPlayer.openMenu(MenuProvider, Consumer<RegistryFriendlyByteBuf>)` with an empty writer for the book. (c) `RegisterClientReloadListenersEvent` fires before the first client reload, so the manual parser registered there is known when the reload runs (read from the event's place in the `Minecraft` constructor, not run). (d) Item rendering inside the foreground pass relies on `GuiGraphics.renderItem` restoring depth state, as `ItemSlot` of the toolkit does.

## Rework candidates

- Hand-placed pixel layouts of all screens; the manual could be a scrolling single page that follows the GUI scale.
- Research queue picker should get the researcher's finished research from the server (add `completedResearch` to `ResearchStationMenu`'s NBT channel) and cycle tag ingredients in the resource display.
- Manual search, bookmarks, and links from item tooltips; images could be a texture atlas (49 full-size PNGs, about 21 MB).
- The research book could show the owner's progress (known / unknown) instead of only what the viewing player knows.
- Replace the `ScreenOnlyMenu` workaround by a proper "hidden slots" flag in `AwMenu` (override `Slot.isActive`) so sub-screens can share the menu.
