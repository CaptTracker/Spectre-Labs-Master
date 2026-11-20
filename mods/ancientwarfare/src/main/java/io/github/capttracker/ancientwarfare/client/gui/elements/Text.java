/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/Text.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase.ActivationEvent;
import io.github.capttracker.ancientwarfare.client.gui.IWidgetSelection;
import io.github.capttracker.ancientwarfare.client.gui.Listener;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashSet;
import java.util.Set;

/*
 * Text input field
 * height = 12px
 *
 * Keyboard input arrives as two kinds of KEY_DOWN event: a key press (GLFW key code, no character)
 * and a typed character (key code -1). handleKeyInput(key, ch) gets both; special keys are matched
 * on the key code, anything else falls through to handleCharacter(ch).
 *
 * @author Shadowmage
 */
public class Text extends GuiElement {

    TextValidator textValidator = new TextValidator();
    IWidgetSelection selector;
    String text;
    int cursorIndex;

    public Text(int topLeftX, int topLeftY, int width, String defaultText, IWidgetSelection selector) {
        super(topLeftX, topLeftY, width, 12);
        if (defaultText == null) {
            defaultText = "";
        }
        this.text = defaultText;
        this.cursorIndex = text.length();
        this.selector = selector;
        this.addDefaultListeners();
        addAllowedChars();
    }

    protected void addAllowedChars() {
        this.textValidator.addValidChars(allowedChars);
        this.textValidator.addValidChars(allowedCharSymbols);
        this.textValidator.addValidChars(allowedNums);
        this.textValidator.addValidChars(allowedNumSymbols);
    }

    protected void addDefaultListeners() {
        this.addNewListener(new Listener(Listener.MOUSE_UP) {
            @Override
            public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                if (enabled && visible && isMouseOverElement(evt.mx, evt.my)) {
                    if (selected && evt.mButton == 1) {
                        String oldText = text;
                        setText("");
                        onTextUpdated(oldText, text);
                        return false;
                    }
                    setSelected(true);
                    selector.onWidgetSelected(Text.this);
                    cursorIndex = text.length();
                } else {
                    if (selected) {
                        selector.onWidgetDeselected(Text.this);
                    }
                    setSelected(false);
                }
                return true;
            }
        });

        this.addNewListener(new Listener(Listener.KEY_DOWN) {
            @Override
            public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                if (enabled && visible && selected) {
                    handleKeyInput(evt.key, evt.ch);
                }
                return true;
            }
        });
    }

    /*
     * Called directly after character input updates or text changes from
     * delete/backspace the new text has already been set.<br>
     * Any references to this.text will return the newtext old test is passed in
     * as a param so that it may be validated / reset to the previous text (cursor
     * position will need manual updating)<br>
     * Anonymous/Subclasses can override/implement this method for specialized
     * callback-type functionality.
     */
    public void onTextUpdated(String oldText, String newText) {

    }

    public void removeAllowedChars(char... chars) {
        for (char ch : chars) {
            this.textValidator.removeValidChar(ch);
        }
    }

    public void setAllowedChars(Set<Character> allowedChars) {
        this.textValidator = new TextValidator(allowedChars);
    }

    public void setAllowedChars(char[] chars) {
        this.textValidator = new TextValidator(chars);
    }

    /**
     * @param keyCode GLFW key code, or -1 for a typed character
     * @param ch      the typed character, or 0 for a key press
     */
    protected void handleKeyInput(int keyCode, char ch) {
        boolean handled = false;
        switch (keyCode) {
            case InputConstants.KEY_LEFT: {
                handled = true;
                cursorIndex--;
                if (cursorIndex < 0) {
                    cursorIndex = 0;
                }
            }
            break;
            case InputConstants.KEY_RIGHT: {
                handled = true;
                cursorIndex++;
                if (cursorIndex > text.length()) {
                    cursorIndex = text.length();
                }
            }
            break;
            case InputConstants.KEY_RETURN:
            case InputConstants.KEY_NUMPADENTER: {
                handled = true;
                onEnterPressed();
            }
            break;
            case InputConstants.KEY_BACKSPACE: {
                handled = true;
                handleBackspaceAction();
            }
            break;
            case InputConstants.KEY_DELETE: {
                handled = true;
                handleDeleteAction();
            }
            break;
            case InputConstants.KEY_HOME: {
                handled = true;
                cursorIndex = 0;
            }
            break;
            case InputConstants.KEY_END: {
                handled = true;
                cursorIndex = text.length();
            }
            break;
            case InputConstants.KEY_ESCAPE: {
                this.selected = false;
                this.selector.onWidgetDeselected(this);
            }
            break;
            default:
                break;
        }
        if (!handled && ch != 0) {
            handleCharacter(ch);
        }
    }

    /*
     * Anonymous classes should override this to handle enter-key pressed
     * inner classes can also override this for default enter-key pressed action
     */
    protected void onEnterPressed() {

    }

    protected void handleDeleteAction() {
        if (cursorIndex < text.length()) {
            String newText = text.substring(0, Math.max(0, cursorIndex)) + text.substring(cursorIndex + 1);
            String oldText = text;
            text = newText;
            onTextUpdated(oldText, newText);
        }
    }

    protected void handleBackspaceAction() {
        if (cursorIndex > 0) {
            cursorIndex = Math.min(cursorIndex, text.length());
            String newText = text.substring(0, cursorIndex - 1) + text.substring(cursorIndex);
            cursorIndex--;
            String oldText = text;
            setText(newText);
            onTextUpdated(oldText, newText);

        }
    }

    protected void handleCharacter(char ch) {
        if (isAllowedCharacter(ch))//is allowed character
        {
            cursorIndex = Math.max(0, Math.min(cursorIndex, text.length()));
            String newText = text.substring(0, cursorIndex) + ch + text.substring(cursorIndex);
            String oldText = text;
            text = newText;
            onTextUpdated(oldText, newText);
            cursorIndex++;
        }
    }

    protected static char[] allowedChars = new char[] {'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'};

    protected static char[] allowedCharSymbols = new char[] {' ', '!', '#', '$', '%', '^', '&', '*', '(', ')', '_', '-', '+', '=', '{', '}', '[', ']', ':', ';', '"', '\'', ',', '<', '.', '>', '/', '?', '@'};

    protected static char[] allowedNums = new char[] {'1', '2', '3', '4', '5', '6', '7', '8', '9', '0'};
    protected static char[] allowedNumSymbols = new char[] {'.', '-'};

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // lt-grey outline box, then the black input box inside it
        graphics.fill(renderX, renderY, renderX + width, renderY + height, 0xFFE6E6E6);
        graphics.fill(renderX + 1, renderY + 1, renderX + width - 1, renderY + height - 1, 0xFF000000);

        // (The original disabled colour 0xA9A9A9ff was RGBA written where ARGB is read; this is the intended dark grey.)
        graphics.drawString(font(), text, renderX + 2, renderY + 2, enabled ? 0xffffffff : 0xFFA9A9A9, false);

        if (selected) {
            int w = font().width(text.substring(0, Math.min(cursorIndex, text.length())));
            graphics.drawString(font(), "_", renderX + 2 + w, renderY + 3, 0xffff0000, false);
        }
    }

    public void setText(String text) {
        if (text == null) {
            text = "";
        }
        StringBuilder filtered = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            if (isAllowedCharacter(text.charAt(i))) {
                filtered.append(text.charAt(i));
            }
        }
        this.text = filtered.toString();
        if (cursorIndex > this.text.length()) {
            cursorIndex = this.text.length();
        }
    }

    public void addAllowedChar(char ch) {
        this.textValidator.addValidChar(ch);
    }

    protected boolean isAllowedCharacter(char ch) {
        return this.textValidator.isCharValid(ch);
    }

    public String getText() {
        return text;
    }

    public static class TextValidator {

        Set<Character> validChars = new HashSet<>();

        public TextValidator() {
        }

        public TextValidator(char[] chars) {
            for (char ch : chars) {
                validChars.add(ch);
            }
        }

        public TextValidator(Set<Character> chars) {
            validChars.addAll(chars);
        }

        public void addValidChar(char ch) {
            validChars.add(ch);
        }

        public void addValidChars(char[] chars) {
            for (char ch : chars) {
                validChars.add(ch);
            }
        }

        public boolean isCharValid(char ch) {
            return validChars.contains(ch);
        }

        public void removeValidChar(char ch) {
            validChars.remove(ch);
        }

        public void clearAllowedChars() {
            validChars.clear();
        }
    }

}
