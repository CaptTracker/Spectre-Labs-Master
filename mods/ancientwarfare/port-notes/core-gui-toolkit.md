# Core GUI widget toolkit (slice key: core-gui-toolkit)

Ported from `core/gui/GuiContainerBase`, `Listener`, `TextureData`, `GuiSelectFromList`, `GuiFileSelect`, `core/gui/elements/*` (24 classes) and the toolkit interfaces in `core/interfaces`. Nothing compiled or run. All names below were checked against `analysis/mc-sources`.

## Files (all under `src/main/java/io/github/capttracker/ancientwarfare/client/gui`)

- `GuiContainerBase<T extends AwMenu>` (extends `AbstractContainerScreen<T>`; holds nested `ActivationEvent`, static `pushViewport/popViewport(GuiGraphics, ...)`)
- `GuiRender` (new; drawing and text helpers: `quartered`, `region`, `fillRgba`, `tint`, `translate`, unicode font helpers, click sound)
- `Listener`, `TextureData`
- `IContainerGuiCallback`, `IScrollableCallback`, `ITabCallback`, `ITooltipRenderer`, `IWidgetSelection` (the original lives in `core/interfaces`; now in `client.gui`)
- `GuiSelectFromList<T>`, `GuiFileSelect`
- `elements/`: `GuiElement`, `Composite`, `CompositeScrolled`, `CompositeTabbed`, `CompositeItemSlots`, `Button`, `Checkbox`, `ClickableArea`, `ImageButton`, `ItemSlot`, `ItemToggleButton`, `Label`, `Line`, `NumberInput`, `ProgressBar`, `Rectangle`, `Scrollbar`, `Tab`, `Text`, `TextButton`, `TexturedButton`, `TexturedRectangle`, `TexturedRectangleLive`, `Tooltip`

Resources: `assets/ancientwarfare/textures/gui/guibackgroundlarge.png`, `guibuttons1.png`, `guibuttons2.png` (binary copies; `manual.png` is left to the manual slice). Script: `analysis/port/core_gui_toolkit.py`.

No `GuiToolkitClient` class is needed.

## Wiring needed in shared files

1. `menu/AwMenu.java` (not owned by this slice). `GuiContainerBase.bindMenu()` calls `menu.setRefreshListener(...)`. Add:
   ```java
   @Nullable
   private Runnable refreshListener;

   /** Client only: the screen registers here to be told to rebuild itself (1.12.2: ContainerBase.gui.refreshGui()). */
   public final void setRefreshListener(@Nullable Runnable listener) {
       this.refreshListener = listener;
   }

   /** Client only: asks the open screen to re-run its setup on its next tick. Safe to call with no screen. */
   protected final void refreshGui() {
       if (refreshListener != null && player.level().isClientSide) {
           refreshListener.run();
       }
   }
   ```
   Menus call `refreshGui()` wherever the 1.12.2 container called `gui.refreshGui()`.
2. Screen registration (`client/AwClient.java`, one line per menu): `event.register(MENU.get(), (menu, inv, title) -> new MyGui(menu));`. Screens take only the menu, and `GuiContainerBase` takes the player from `menu.player`.
3. Lang: merge `core-gui-toolkit.lang.json` (9 `guistrings.*.tooltip` keys read by `GuiElement.setTooltipIfFound`) into `en_us.json`.
4. `BackpackScreen`/`PanelScreen` can stay until the Core screens slice replaces them.

## How a screen is written now

- `class MyGui extends GuiContainerBase<MyMenu>`; `super(menu, xSize, ySize)` or `super(menu)` for 256x240. `xSize/ySize/guiLeft/guiTop` still exist; they are copied to and from vanilla `imageWidth/imageHeight/leftPos/topPos` on every init.
- `initElements()` once, `setupElements()` on every init and after `refreshGui()` (next tick), widgets get `updateGuiPosition(guiLeft, guiTop)` afterwards. Unchanged from the original.
- `GuiElement.render(GuiGraphics, mouseX, mouseY, partialTick)` replaces `render(mouseX, mouseY, partialTick)`. Coordinates are absolute GUI coordinates as before.
- `getContainer().setGui(parent)` becomes `parent.bindMenu()`. `Minecraft.displayGuiScreen(screen)` for a screen sharing the menu becomes `showScreen(screen)` (keeps the menu open; skips vanilla's `menu.removed`). Returning to a parent: `parent.bindMenu(); showScreen(parent);`.
- Menu data for the screen: `AwMenu.sendDataToScreen` arrives in `handlePacketData(CompoundTag)`.
- `closeGui()`, `onGuiCloseRequested()`, `shouldCloseOnVanillaKeys`, `setTexture(String)`, `addGuiElement/removeGuiElement/clearElements`, `onCompositeCleared`, `handleItemStackTooltipRender/handleElementTooltipRender` are as in the original.
- Fonts: `font.width(s)` for `fontRenderer.getStringWidth(s)`; `Screen.hasShiftDown()` via `isShiftKeyDown()`/`isCtrlKeyDown()` helpers.
- Key codes in `ActivationEvent.key` are GLFW (`InputConstants.KEY_*`, not LWJGL2 `Keyboard.KEY_*`). Keyboard events are split in two: a key press (`key` = GLFW code, `ch` = 0) and a typed character (`key` = -1, `ch` = the character). `Text.handleKeyInput(key, ch)` receives both. Screens with their own `Listener(KEY_DOWN)` must expect that split.
- Mouse wheel: `evt.mw` is 120 per notch like LWJGL2, so widget arithmetic is unchanged.
- Colours stay `0xRRGGBBAA` in `Rectangle`, `Line` (via `GuiRender.fillRgba`); text colours are `0xAARRGGBB` as in vanilla.

## Differences from the original (all local)

- Tooltip hover timer starts at -1 (the original started at 0, so the first tooltip appeared with no delay).
- `Button` hover text colour: original `0xa0a0a0ff` was RGBA written where ARGB is read (it drew translucent blue-white); now grey `0xFFA0A0A0`. Same for `Text` disabled colour `0xA9A9A9ff` -> `0xFFA9A9A9`.
- `ImageButton` tint: the original passed 0-255 ints to a 0-1 colour call, so `Color.GRAY` drew white; the tint is now applied as 0-1 (grey draws grey, `Color.CYAN` still cyan).
- `Tooltip` screen-edge check uses GUI units (the original compared to framebuffer pixels). Tooltip is drawn at z 400 over items.
- `NumberInput` formats with `Locale.ROOT`.
- `Scrollbar.getPercentScrolled` avoids 0/0 (NaN) when the content fits.
- `Text` edit helpers clamp the cursor; `Line` draws diagonals as a run of small squares (1.21.1 has no line primitive in `GuiGraphics`).
- `CompositeScrolled.postRender` only forwards tooltips while the mouse is inside the area.
- `GuiFileSelect`: Back at the filesystem root no longer prints a stack trace. `GuiSelectFromList`: a null `entry` gives an empty filter text instead of calling the display-name function with null.
- `setTooltipIfFound` replaces an earlier tooltip instead of appending to it.
- Event lists are copied before dispatch so a widget may rebuild its parent while handling a click.
- `TextButton`'s private unicode `FontRenderer` is the vanilla `minecraft:uniform` font via `GuiRender.unicode(...)`.
- `renderQuarteredTexture` is reproduced by `GuiRender.quartered` using integer halves and `GuiGraphics.blit`; render sizes beyond twice the region stretch instead of bleeding into neighbouring texture.
- `ItemSlot`: item and decorations through `GuiGraphics.renderItem/renderItemDecorations`; item count drawn by the original layout (counts over 99 at half size); highlight uses `RenderType.guiOverlay()` so it shows above the item.

## Not ported

- `GuiBackpack` (replaced by the existing `client/BackpackScreen`), `GuiInfoTool` (info tool slice), `GuiResearchBook` (research/manual screens slice).
- Sub-folders `manual`, `research`, `crafting`, `options`: other slices.

## API names not confirmed beyond reading the sources

- None unconfirmed. Points relying on runtime behaviour rather than a signature: vanilla calls `Screen.mouseMoved` before `mouseDragged` while a button is held (read in `MouseHandler`), so scrollbar dragging gets `MOUSE_MOVED`; `Style.withFont(Minecraft.UNIFORM_FONT)` assumes the `minecraft:uniform` font definition is present in the resource pack (it is in vanilla 1.21.1).

## Rework candidates

- Replace hand-placed pixel layouts with a simple layout helper; screens are hard to read at GUI scale 1 and above 3.
- `Text` could wrap a vanilla `EditBox` for selection, clipboard (Ctrl+V/C), IME and a proper cursor blink; it currently has no paste.
- Text colours and the grey/white panel textures do not follow the vanilla 1.21 look; consider a resource-pack-friendly theme.
- `Line` as small squares is crude; a `fill`-based rotated quad or a `RenderType.lines` call would be cleaner.
- Tooltips use `Label` trees and a custom box; vanilla `renderComponentTooltip` would give correct wrapping and theming.
- `refreshGui()` re-inits the whole screen; most uses only need a list rebuilt.
- Widgets drawn in the foreground pass sit under menu slot items if they overlap them (depth ordering); screens that overlay panels on real slots need a z offset.
