/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/elements/NumberInput.
 */
package io.github.capttracker.ancientwarfare.client.gui.elements;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase.ActivationEvent;
import io.github.capttracker.ancientwarfare.client.gui.IWidgetSelection;
import io.github.capttracker.ancientwarfare.client.gui.Listener;

import java.util.Locale;

/** Text box for a float or int value; the mouse wheel changes it by {@code incrementAmount}. */
public class NumberInput extends Text {

    boolean allowDecimal = true;
    boolean allowNeg;
    boolean integerValue;
    float value;
    int decimalPlaces = 2;
    float incrementAmount = 1.f;

    public NumberInput(int topLeftX, int topLeftY, int width, float defaultText, IWidgetSelection selector) {
        super(topLeftX, topLeftY, width, format(2, defaultText), selector);
        this.value = defaultText;
        this.setAllowedChars(allowedNums);
        this.scrollInput = true;
    }

    /** Locale-independent number formatting (the original used the default locale, which breaks parsing where ',' is the decimal mark). */
    private static String format(int places, float val) {
        return String.format(Locale.ROOT, "%." + places + "f", val);
    }

    public int getIntegerValue() {
        return (int) value;
    }

    public float getFloatValue() {
        return value;
    }

    public NumberInput setAllowNegative() {
        this.allowNeg = true;
        return this;
    }

    public NumberInput setIntegerValue() {
        this.integerValue = true;
        this.decimalPlaces = 0;
        this.incrementAmount = 1.f;
        this.allowDecimal = false;
        this.text = format(decimalPlaces, value);
        return this;
    }

    public NumberInput setIncrementAmount(float amount) {
        this.incrementAmount = amount;
        return this;
    }

    public NumberInput setDecimalPlaces(int places) {
        places = places < 0 ? 0 : places;
        this.decimalPlaces = places;
        return this;
    }

    @Override
    protected void addAllowedChars() {
        this.textValidator.addValidChars(allowedNums);
        this.textValidator.addValidChars(allowedNumSymbols);
    }

    @Override
    protected void addDefaultListeners() {
        super.addDefaultListeners();

        this.addNewListener(new Listener(Listener.MOUSE_WHEEL) {
            @Override
            public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                if (isMouseOverElement(evt.mx, evt.my)) {
                    int d = evt.mw;
                    if (d < 0) {
                        setValue(value - incrementAmount);
                    } else if (d > 0) {
                        setValue(value + incrementAmount);
                    }
                }
                return true;
            }
        });
    }

    @Override
    public void setText(String text) {
        if (text == null || text.isEmpty()) {
            this.setValue(0);
            this.text = "";
            return;
        }
        try {
            float fl = Float.parseFloat(text);
            setValue(fl);
            this.text = text;
        }
        catch (NumberFormatException e) {
            this.setValue(0.f);
            this.text = format(decimalPlaces, 0.f);
        }
    }

    public NumberInput setValue(float val) {
        if (value == val) {
            return this;
        }
        if (!allowNeg && val < 0) {
            val = 0.f;
        }
        this.text = format(decimalPlaces, val);
        this.value = val;
        this.onValueUpdated(value);
        if (this.cursorIndex > this.text.length()) {
            this.cursorIndex = this.text.length();
        }
        return this;
    }

    @Override
    protected void handleCharacter(char ch) {
        boolean allowed = isAllowedCharacter(ch);
        if (ch == '.') {
            allowed = allowDecimal;
        } else if (ch == '-') {
            allowed = allowNeg && value > 0 && cursorIndex == 0;
        }
        if (allowed)//is allowed character
        {
            cursorIndex = Math.max(0, Math.min(cursorIndex, text.length()));
            text = text.substring(0, cursorIndex) + ch + text.substring(cursorIndex);
            cursorIndex++;
            setText(text);
        }
    }

    @Override
    protected void onEnterPressed() {
        this.setText(getText());
    }

    /*
     * anonymous classes should implement this for a callback for when value is updated/changed via user input (either manual, mouse,or keyboard)
     */
    public void onValueUpdated(float value) {

    }
}
