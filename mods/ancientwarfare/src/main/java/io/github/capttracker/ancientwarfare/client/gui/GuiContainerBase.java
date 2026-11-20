/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/GuiContainerBase.
 */
package io.github.capttracker.ancientwarfare.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.capttracker.ancientwarfare.client.gui.elements.GuiElement;
import io.github.capttracker.ancientwarfare.client.gui.elements.Tooltip;
import io.github.capttracker.ancientwarfare.menu.AwMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen base for every Ancient Warfare GUI, carrying the retained-mode widget toolkit
 * ({@link GuiElement}, {@link Listener}, {@link ActivationEvent}) on top of vanilla's
 * {@link AbstractContainerScreen}.
 * <p>
 * The screen flow is the original's: {@link #initElements()} runs once, {@link #setupElements()}
 * runs every time the screen is (re)initialised, and {@link #refreshGui()} (called by the menu when
 * server data arrives) schedules another setup on the next tick. Vanilla input callbacks are turned
 * into one {@link ActivationEvent} per input and offered to every widget.
 * <p>
 * Porting notes for screens:
 * <ul>
 * <li>{@code xSize}/{@code ySize}/{@code guiLeft}/{@code guiTop} exist as in 1.12.2 and are copied to and
 * from vanilla's {@code imageWidth}/{@code imageHeight}/{@code leftPos}/{@code topPos}.</li>
 * <li>Widgets draw with a {@link GuiGraphics}; {@code render(graphics, mouseX, mouseY, partialTick)}.</li>
 * <li>{@code getContainer().setGui(parent)} becomes {@code parent.bindMenu()}.</li>
 * <li>{@code fontRenderer.getStringWidth(s)} becomes {@code font.width(s)}.</li>
 * <li>The menu is registered with a screen factory such as {@code (menu, inventory, title) -> new MyGui(menu)}.</li>
 * </ul>
 *
 * @param <T> the menu type
 */
public abstract class GuiContainerBase<T extends AwMenu> extends AbstractContainerScreen<T> implements IContainerGuiCallback, ITooltipRenderer, IWidgetSelection {

    private GuiElement selectedWidget = null;
    protected boolean shouldCloseOnVanillaKeys = true;
    private float partialRenderTick = 0.f;
    private boolean initDone = false;
    private boolean shouldUpdate = false;
    private final List<GuiElement> elements = new ArrayList<>();

    private Tooltip elementTooltip;
    private int elementTooltipX;
    private int elementTooltipY;

    private ItemStack tooltipStack = ItemStack.EMPTY;
    private int tooltipX;
    private int tooltipY;

    private ResourceLocation backgroundTexture;

    protected Player player;

    /** Window size, as in 1.12.2. Copied to {@code imageWidth}/{@code imageHeight} when the screen is initialised. */
    protected int xSize;
    protected int ySize;
    /** Top left corner of the window, as in 1.12.2 (mirrors {@code leftPos}/{@code topPos}). */
    protected int guiLeft;
    protected int guiTop;

    protected GuiContainerBase(T menu, int xSize, int ySize) {
        super(menu, menu.player.getInventory(), Component.empty());
        this.xSize = xSize;
        this.ySize = ySize;
        this.imageWidth = xSize;
        this.imageHeight = ySize;
        this.player = menu.player;
        this.backgroundTexture = GuiElement.backgroundTextureLocation;
        bindMenu();
    }

    public GuiContainerBase(T menu) {
        this(menu, 256, 240);
    }

    /**
     * Makes this screen the one the menu talks to: data sent to the screen and refresh requests come
     * here. Done on construction and on every init, so a screen that returns from a sub-screen takes the
     * menu back. (1.12.2: {@code container.setGui(gui)}.)
     */
    public void bindMenu() {
        menu.setScreenListener(this::handlePacketData);
        menu.setRefreshListener(this::refreshGui);
    }

    /** Replaces the background texture (a 256x256 texture whose top left 256x240 is drawn quartered). */
    public final void setTexture(String name) {
        this.backgroundTexture = ResourceLocation.parse(name);
    }

    public final void setTexture(ResourceLocation texture) {
        this.backgroundTexture = texture;
    }

    public final T getContainer() {
        return menu;
    }

    @Override
    public void handleItemStackTooltipRender(ItemStack itemStack, int mouseX, int mouseY) {
        this.tooltipStack = itemStack;
        this.tooltipX = mouseX;
        this.tooltipY = mouseY;
    }

    @Override
    public void handleElementTooltipRender(Tooltip tooltip, int mouseX, int mouseY) {
        this.elementTooltip = tooltip;
        elementTooltipX = mouseX;
        elementTooltipY = mouseY;
    }

    protected void clearElements() {
        if (this.selectedWidget != null && this.elements.contains(selectedWidget)) {
            this.selectedWidget = null;
        }
        this.elements.clear();
    }

    protected void addGuiElement(GuiElement element) {
        this.elements.add(element);
    }

    protected void removeGuiElement(GuiElement element) {
        this.elements.remove(element);
    }

    // ---- input: vanilla callbacks to ActivationEvents ----

    private void dispatchMouse(ActivationEvent evt) {
        for (GuiElement element : new ArrayList<>(this.elements)) {
            element.handleMouseInput(evt);
        }
    }

    private void dispatchKey(ActivationEvent evt) {
        for (GuiElement element : new ArrayList<>(this.elements)) {
            element.handleKeyboardInput(evt);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        syncGeometry();
        // vanilla first (slot clicks), then the widgets, as in the original's handleMouseInput
        super.mouseClicked(mouseX, mouseY, button);
        dispatchMouse(ActivationEvent.mouse(Listener.MOUSE_DOWN, button, true, Mth.floor(mouseX), Mth.floor(mouseY), 0));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        syncGeometry();
        super.mouseReleased(mouseX, mouseY, button);
        dispatchMouse(ActivationEvent.mouse(Listener.MOUSE_UP, button, false, Mth.floor(mouseX), Mth.floor(mouseY), 0));
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        dispatchMouse(ActivationEvent.mouse(Listener.MOUSE_MOVED, -1, false, Mth.floor(mouseX), Mth.floor(mouseY), 0));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // The original wheel delta was LWJGL2's 120 per notch; widgets scale it themselves.
        int wheel = (int) Math.round(scrollY * 120.0);
        if (wheel != 0) {
            dispatchMouse(ActivationEvent.mouse(Listener.MOUSE_WHEEL, -1, false, Mth.floor(mouseX), Mth.floor(mouseY), wheel));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        dispatchKey(ActivationEvent.key(Listener.KEY_DOWN, keyCode, '\0', true, modifiers));
        if (selectedWidget != null) {
            // a selected text box swallows every key, including the inventory key
            return true;
        }
        if (!shouldCloseOnVanillaKeys) {
            boolean isExitCommand = keyCode == InputConstants.KEY_ESCAPE || this.minecraft.options.keyInventory.matches(keyCode, scanCode);
            if (isExitCommand) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        dispatchKey(ActivationEvent.key(Listener.KEY_UP, keyCode, '\0', false, modifiers));
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        // key code -1: a typed character, not a key press
        dispatchKey(ActivationEvent.key(Listener.KEY_DOWN, InputConstants.UNKNOWN.getValue(), codePoint, true, modifiers));
        if (selectedWidget != null) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    /** 1.12.2 name for {@code Screen.hasShiftDown()}. */
    protected static boolean isShiftKeyDown() {
        return Screen.hasShiftDown();
    }

    /** 1.12.2 name for {@code Screen.hasControlDown()}. */
    protected static boolean isCtrlKeyDown() {
        return Screen.hasControlDown();
    }

    // ---- closing ----

    protected final void closeGui() {
        onClose();
    }

    private boolean switchingScreens = false;

    /**
     * Shows another screen (usually a sub-screen sharing this screen's menu, or this screen's parent)
     * without closing the menu. 1.12.2: {@code Minecraft.displayGuiScreen(screen)} while keeping the
     * same container. Vanilla calls {@code menu.removed(player)} whenever a container screen is
     * replaced; that call is skipped here because the menu stays open.
     */
    protected final void showScreen(Screen next) {
        this.switchingScreens = true;
        try {
            this.minecraft.setScreen(next);
        } finally {
            this.switchingScreens = false;
        }
    }

    @Override
    public void removed() {
        if (switchingScreens) {
            return;
        }
        super.removed();
    }

    /**
     * Vanilla routes Escape and the inventory key here. The original called its overridable
     * {@link #onGuiCloseRequested()} first, so this does too.
     */
    @Override
    public void onClose() {
        if (onGuiCloseRequested()) {
            super.onClose();
        }
    }

    /*
     * over-rideable method for onGuiClosed w/ proper functionality -- (is called before the container disappears)
     * derived classes can override this to handle custom onClosed functionality (e.g. call container to send data to server/
     * finalize settings)
     *
     * @return true if the GUI should closed -- false if another GUI has been opened/the GUI should not closed
     */
    protected boolean onGuiCloseRequested() {
        return true;
    }

    // ---- lifecycle ----

    private void syncGeometry() {
        this.imageWidth = xSize;
        this.imageHeight = ySize;
    }

    @Override
    protected void init() {
        syncGeometry();
        super.init();
        this.guiLeft = this.leftPos;
        this.guiTop = this.topPos;
        bindMenu();
        if (!initDone) {
            initElements();
            initDone = true;
        }
        this.setupElements();
        for (GuiElement element : this.elements) {
            element.updateGuiPosition(guiLeft, guiTop);
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.shouldUpdate) {
            this.init();
            this.shouldUpdate = false;
        }
    }

    /*
     * call this method to enforce a re-initialization of gui at the start of next game tick (not render tick)<br>
     * setupElements() will then be called<br>
     * and all elements will have their position updated
     */
    @Override
    public void refreshGui() {
        this.shouldUpdate = true;
    }

    /*
     * Sub-classes should implement this method to add initial gui elements.<br>
     * Only called a single time, shortly after construction,
     * but before any rendering or other update methods are called
     */
    public abstract void initElements();

    /*
     * sub-classes should implement this method to setup/change any elements that need adjusting when the gui is initialized<br>
     * any elements that are positioned outside of the gui-window space will need their positions updated by calling element.setPosition(xPos, yPos)
     * as they reference internal position relative to the guiLeft / guiTop values from this gui (which are passed in and updated directly after setupElements() is called)<br>
     * Always called at least once, directly after {@link #initElements()}
     */
    public abstract void setupElements();

    /*
     * sub-classes should override this method to handle any expected packet data
     */
    @Override
    public void handlePacketData(CompoundTag data) {

    }

    // ---- drawing ----

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiRender.resetColor(graphics);
        if (backgroundTexture != null) {
            GuiRender.quartered(graphics, backgroundTexture, 256, 256, 0, 0, 256, 240, leftPos, topPos, imageWidth, imageHeight);
        }
        for (Slot slot : this.menu.slots) {
            if (slot.isActive()) {
                graphics.blit(GuiElement.widgetTexture1, slot.x - 1 + leftPos, slot.y - 1 + topPos, 152, 120, 18, 18, 256, 256);
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        partialRenderTick = partialTicks;
        syncGeometry();
        super.render(graphics, mouseX, mouseY, partialTicks);
        renderTooltip(graphics, mouseX, mouseY);
        // deferred tooltips go on top of everything else
        graphics.flush();
        if (!tooltipStack.isEmpty()) {
            graphics.renderTooltip(this.font, tooltipStack, tooltipX, tooltipY);
            tooltipStack = ItemStack.EMPTY;
        }
        if (elementTooltip != null) {
            elementTooltip.renderTooltip(graphics, elementTooltipX, elementTooltipY, partialRenderTick);
            elementTooltip = null;
        }
    }

    /**
     * The widgets are drawn here, in the container screen's foreground pass (after the slots, before the
     * carried item). Vanilla's title and inventory labels are not drawn; screens place their own Labels.
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        RenderSystem.disableDepthTest();
        graphics.pose().pushPose();
        // the pass is translated by (leftPos, topPos); widgets use absolute coordinates
        graphics.pose().translate(-leftPos, -topPos, 0);
        long time = System.currentTimeMillis();
        for (GuiElement element : this.elements) {
            element.render(graphics, mouseX, mouseY, partialRenderTick);
            element.postRender(mouseX, mouseY, partialRenderTick, time, this);
        }
        graphics.pose().popPose();
    }

    // ---- widget selection ----

    @Override
    public void onWidgetSelected(GuiElement element) {
        this.selectedWidget = element;
        element.setSelected(true);
    }

    @Override
    public void onWidgetDeselected(GuiElement element) {
        if (selectedWidget == element) {
            selectedWidget = null;
        }
        element.setSelected(false);
    }

    public void onCompositeCleared(List<GuiElement> compositeElements) {
        if (compositeElements.contains(selectedWidget)) {
            selectedWidget = null;
        }
    }

    /*
     * Action event for gui-widgets.  A single event is sent to all element / listeners for each input event (key up/down, mouse up/down/move/wheel)
     *
     * @author Shadowmage
     */
    public static class ActivationEvent {
        /*
         * the type of event, one of the Listener constants (KEY_UP, KEY_DOWN, MOUSE_UP, MOUSE_DOWN, MOUSE_WHEEL, MOUSE_MOVED)
         */
        public final int type;

        /*
         * what key was pressed? A GLFW key code (see InputConstants.KEY_*). -1 for a typed-character event.
         */
        public int key;

        /*
         * the mouse button number.
         * -1 = none
         * 0 = LMB
         * 1 = RMB
         * 2+= ?
         */
        public int mButton;
        /*
         * the state of the button or key.  true for pressed, false for released
         */
        public boolean state;
        /*
         * the input char, for typed-character events (0 for key presses)
         */
        public char ch;
        /*
         * modifier bits of a key event (GLFW_MOD_*), 0 for mouse events
         */
        public int modifiers;
        /*
         * mouse x position
         */
        public int mx;
        /*
         * mouse y position
         */
        public int my;
        /*
         * mouse-wheel delta movement (120 per notch, like LWJGL2)
         */
        public int mw;

        private ActivationEvent(int type, int button, boolean state, int mx, int my, int mw) {
            this.type = type;
            this.mButton = button;
            this.state = state;
            this.mx = mx;
            this.my = my;
            this.mw = mw;
        }

        private ActivationEvent(int type, int key, char character, boolean state, int modifiers) {
            this.type = type;
            this.key = key;
            this.ch = character;
            this.state = state;
            this.modifiers = modifiers;
        }

        static ActivationEvent mouse(int type, int button, boolean state, int mx, int my, int mw) {
            return new ActivationEvent(type, button, state, mx, my, mw);
        }

        static ActivationEvent key(int type, int key, char character, boolean state, int modifiers) {
            return new ActivationEvent(type, key, character, state, modifiers);
        }
    }

    /*
     * Push a new scissors-test viewport. Viewports nest: a new one is cut down to fit inside the one
     * below it (GuiGraphics keeps the stack). Every push must be matched by a popViewport.
     */
    public static void pushViewport(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.enableScissor(x, y, x + w, y + h);
    }

    /*
     * pop a scissors-test viewport off of the stack
     */
    public static void popViewport(GuiGraphics graphics) {
        graphics.disableScissor();
    }

}
