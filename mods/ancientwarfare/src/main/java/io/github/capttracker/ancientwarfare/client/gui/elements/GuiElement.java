/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/GuiElement.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase.ActivationEvent;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.ITooltipRenderer;
import io.github.capttracker.ancientwarfare.client.gui.Listener;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Base GUI element class. Subclasses add their own listeners in their constructor to handle default
 * behaviour such as playing the click sound, toggling state or changing highlighting.
 * <p>
 * Differences from the 1.12.2 class: {@link #render} receives the {@link GuiGraphics} to draw with,
 * and lang lookups use {@link GuiRender#translate} instead of I18n.format.
 *
 * @author Shadowmage (original)
 */
public abstract class GuiElement {

    private final List<Listener> actionListeners = new ArrayList<>();

    protected boolean mouseInterface;
    protected boolean keyboardInterface;

    protected boolean enabled;

    protected boolean visible;

    protected boolean selected;//isFocused -- for text-input lines / etc
    protected boolean scrollInput = false;//if should intercept scroll input, mostly used inside compositescrolled containers, really should only be true for number input widget
    protected boolean renderTooltip = true;
    protected Tooltip tooltip;

    /** Time the mouse started hovering, or -1. (Original started at 0, which showed the first tooltip without the delay.) */
    protected long hoverStart = -1;
    protected int topLeftX;

    protected int topLeftY;
    protected int renderX;

    protected int renderY;
    protected int width;

    protected int height;

    // The texture files keep their original (already lower-case) names.
    public static final ResourceLocation backgroundTextureLocation = Ids.of(AncientWarfareCore.MOD_ID, "textures/gui/guibackgroundlarge.png");

    public static final ResourceLocation widgetTexture1 = Ids.of(AncientWarfareCore.MOD_ID, "textures/gui/guibuttons1.png");
    public static final ResourceLocation widgetTexture2 = Ids.of(AncientWarfareCore.MOD_ID, "textures/gui/guibuttons2.png");

    public GuiElement(int topLeftX, int topLeftY) {
        this.topLeftX = topLeftX;
        this.topLeftY = topLeftY;
        this.enabled = true;
        this.visible = true;
    }

    public GuiElement(int topLeftX, int topLeftY, int width, int height) {
        this(topLeftX, topLeftY);
        this.width = width;
        this.height = height;
    }

    /** The game font (the original's {@code Minecraft.fontRenderer}). */
    protected static Font font() {
        return GuiRender.font();
    }

    /** Looks up a lang key; text that is not a key is returned unchanged. */
    protected static String translate(String keyOrText) {
        return GuiRender.translate(keyOrText);
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setScrollable(boolean handleScroll) {
        this.scrollInput = handleScroll;
    }

    /*
     * called to update the internal positioning of this element.
     * needs to be called anytime the parent gui layout is changed (resized / etc)
     */
    public void updateGuiPosition(int guiLeft, int guiTop) {
        renderX = topLeftX + guiLeft;
        renderY = topLeftY + guiTop;
    }

    public void setRenderPosition(int topLeftX, int topLeftY) {
        this.topLeftX = topLeftX;
        this.topLeftY = topLeftY;
    }

    /*
     * called from GUI to process mouse interface.
     * all functionality should be implemented via ActionListeners
     * even default functionality (e.g. play sound on click, toggle state, etc)
     */
    public final void handleMouseInput(ActivationEvent evt) {
        if (mouseInterface && visible && enabled && !actionListeners.isEmpty()) {
            for (Listener o : this.actionListeners) {
                //   bitwise check of types, if it returns !=0 at least 1 type bit was shared, so should execute
                if ((o.type & evt.type) != 0) {
                    if (!o.onEvent(this, evt)) {
                        break;
                    }
                }
            }
        }
    }

    /*
     * called from GUI to process keyboard interface
     * all functionality should be implemented via ActionListeners
     * including default functionality.
     * <p/>
     * will only fire events if this element is currently selected.
     * selection is currently handled manually --
     * must call element.setSelected() and element.clearSelected()
     * currently multiple elements may be selected concurrently and all
     * will receive the keyboard input events
     */
    public final void handleKeyboardInput(ActivationEvent evt) {
        if (keyboardInterface && visible && enabled && !actionListeners.isEmpty()) {
            for (Listener o : this.actionListeners) {
                //   bitwise check of types, if it returns !=0 at least 1 type bit was shared, so should execute
                if ((o.type & evt.type) != 0) {
                    if (!o.onEvent(this, evt)) {
                        break;
                    }
                }
            }
        }
    }

    /*
     * add a new event listener to this element
     * if the element is not set to receive those event types
     * --auto-flag the element to receive those events
     *
     * @param listener the new listener to add
     */
    public final void addNewListener(Listener listener) {
        this.actionListeners.add(listener);
        if ((listener.type & Listener.MOUSE_TYPES) != 0) {
            this.mouseInterface = true;
        }
        if ((listener.type & Listener.KEY_TYPES) != 0) {
            this.keyboardInterface = true;
        }
    }

    public final boolean isMouseOverElement(int mouseX, int mouseY) {
        return mouseX >= renderX && mouseX < renderX + width && mouseY >= renderY && mouseY < renderY + height;
    }

    /** Called by the screen (or the parent composite) to draw this element. Coordinates are absolute GUI coordinates. */
    public abstract void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /*
     * checks for tooltip-rendering for this gui-element.
     */
    public void postRender(int mouseX, int mouseY, float partialTick, long tick, ITooltipRenderer rend) {
        if (tooltip == null) {
            return;
        }
        if (isMouseOverElement(mouseX, mouseY)) {
            if (hoverStart == -1) {
                hoverStart = tick;
            } else if (tick - hoverStart > 500)//0.5 seconds
            {
                rend.handleElementTooltipRender(tooltip, mouseX, mouseY);
            }
        } else {
            hoverStart = -1;
        }
    }

    public void setTooltip(Tooltip tip) {
        this.tooltip = tip;
    }

    public void addTooltip(String text) {
        addTooltip(text, 40);
    }

    public void addTooltip(String text, int maxWidthInChars) {
        // wrap around \n characters (a literal backslash followed by 'n' in the lang text)
        List<String> textLines = Arrays.asList(translate(text).split("\\\\n"));

        // perform additional word-wrapping to maxWidthInChars, if required
        List<String> textLinesWrapped = new ArrayList<>();
        for (String textLine : textLines) {
            if (textLine.length() > maxWidthInChars) {
                // line is too long, perform additional splitting
                textLinesWrapped.addAll(splitWords(textLine, maxWidthInChars));
            } else {
                textLinesWrapped.add(textLine);
            }
        }
        if (textLinesWrapped.isEmpty()) {
            textLinesWrapped.add("");
        }

        int width = font().width(textLinesWrapped.get(0));
        int height = 0;

        // pre-calculate the width and height (necessary for multiline tooltips)
        for (int i = 0; i < textLinesWrapped.size(); i++) {
            int lineWidth = font().width(textLinesWrapped.get(i));
            if (lineWidth > width) {
                width = lineWidth;
            }
            height = addLineSpacing(height, i, textLinesWrapped.size());
        }

        if (tooltip == null) {
            tooltip = new Tooltip(width, height);
        }

        height = 0;
        for (int i = 0; i < textLinesWrapped.size(); i++) {
            tooltip.addTooltipElement(new Label(0, height, textLinesWrapped.get(i)));
            height = addLineSpacing(height, i, textLinesWrapped.size());
        }
        this.renderTooltip = true;
    }

    // for multi-line tooltip formatting
    private static int addLineSpacing(int height, int lineNumber, int totalLines) {
        if (lineNumber + 1 == totalLines) {
            // no line spacing for the last line
            height += 8;
        } else {
            height += 10;
        }
        return height;
    }

    /** Word wrap by character count (copy of the original TextUtils.split, kept private so the toolkit stands alone). */
    private static List<String> splitWords(String text, int maxLineLength) {
        StringTokenizer tok = new StringTokenizer(text, " ");
        StringBuilder output = new StringBuilder(text.length());
        int lineLen = 0;
        while (tok.hasMoreTokens()) {
            String word = tok.nextToken();

            while (word.length() > maxLineLength) {
                int cut = Math.max(1, maxLineLength - lineLen);
                output.append(word, 0, cut).append('\n');
                word = word.substring(cut);
                lineLen = 0;
            }

            if (lineLen + word.length() > maxLineLength) {
                output.append('\n');
                lineLen = 0;
            }
            output.append(word).append(' ');

            lineLen += word.length() + 1;
        }
        return Arrays.asList(output.toString().split("\n"));
    }

    /**
     * Looks for a tooltip lang entry for the key: first the wildcard form (last segment replaced by
     * "*", for example {@code guistrings.inventory.direction.*.tooltip}), then {@code <key>.tooltip}.
     * The more specific one wins. A found tooltip replaces any earlier one (the original appended
     * lines every time a Label's text was set again).
     */
    public void setTooltipIfFound(String name) {
        if (name == null) {
            return;
        }
        String tooltipFound = null;
        String tooltipCheck;
        int endIndex = name.lastIndexOf(".");
        if (endIndex > 0) {
            // replace the last .segment of this key with ".*" and see if it has a tooltip
            tooltipCheck = name.substring(0, endIndex) + ".*.tooltip";
            if (GuiRender.hasTranslation(tooltipCheck)) {
                tooltipFound = tooltipCheck;
            }
        }
        // even if there is a wildcard tooltip found, we still want to check for a more specific one
        tooltipCheck = name + ".tooltip";
        if (GuiRender.hasTranslation(tooltipCheck)) {
            tooltipFound = tooltipCheck;
        }

        if (tooltipFound != null) {
            tooltip = null;
            addTooltip(tooltipFound);
        }
    }

    public boolean selected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

}
