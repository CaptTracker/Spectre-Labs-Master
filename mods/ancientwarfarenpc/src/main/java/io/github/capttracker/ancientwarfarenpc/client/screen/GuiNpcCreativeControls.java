/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiNpcCreativeControls.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Checkbox;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.NumberInput;
import io.github.capttracker.ancientwarfare.client.gui.elements.Text;
import io.github.capttracker.ancientwarfarenpc.menu.NpcCreativeControlsMenu;
import io.github.capttracker.ancientwarfarenpc.menu.NpcMenuPayloads;

/**
 * The creative controls of an NPC (1.12.2: {@code GuiNpcCreativeControls}): owner name, skin, health, damage and armor overrides, wander and custom
 * equipment. Closing it sends the changes and returns to the inventory screen.
 */
public class GuiNpcCreativeControls extends GuiNpcBase<NpcCreativeControlsMenu> {
    private Text ownerNameInput;
    private NumberInput attackDamageOverrideInput;
    private NumberInput armorValueOverrideInput;
    private NumberInput maxHealthOverrideInput;
    private Checkbox wanderCheckbox;
    private Checkbox customEquipmentCheckbox;
    private Button skinButton;
    private boolean hasChanged = false;

    public GuiNpcCreativeControls(NpcCreativeControlsMenu menu) {
        // height: 8 + five rows of 12 + the checkbox row of 16 + 8
        super(menu, 256, 92);
    }

    @Override
    public void initElements() {
        int totalHeight = 8;
        addGuiElement(new Label(8, totalHeight + 1, "guistrings.npc.owner_name"));
        ownerNameInput = new Text(100, totalHeight, 256 - 16 - 100, "", this) {
            @Override
            public void onTextUpdated(String oldText, String newText) {
                getContainer().ownerName = newText;
                hasChanged = true;
            }
        };
        addGuiElement(ownerNameInput);
        totalHeight += 12;

        addGuiElement(new Label(8, totalHeight + 1, "guistrings.npc.custom_texture"));
        skinButton = new Button(100, totalHeight, 256 - 16 - 100, 12, getContainer().skinSettings.getDescription().getString()) {
            @Override
            protected void onPressed() {
                showScreen(new GuiSkinSelection(GuiNpcCreativeControls.this, getContainer(), getContainer().npc));
            }
        };
        addGuiElement(skinButton);
        totalHeight += 12;

        addGuiElement(new Label(8, totalHeight + 1, "guistrings.npc.health_override"));
        maxHealthOverrideInput = new NumberInput(120, totalHeight, 60, 0, this) {
            @Override
            public void onValueUpdated(float value) {
                getContainer().maxHealth = (int) value;
                hasChanged = true;
            }
        };
        addGuiElement(maxHealthOverrideInput);
        maxHealthOverrideInput.setIntegerValue().setAllowNegative();
        totalHeight += 12;

        addGuiElement(new Label(8, totalHeight + 1, "guistrings.npc.damage_override"));
        attackDamageOverrideInput = new NumberInput(120, totalHeight, 60, 0, this) {
            @Override
            public void onValueUpdated(float value) {
                getContainer().attackDamage = (int) value;
                hasChanged = true;
            }
        };
        addGuiElement(attackDamageOverrideInput);
        attackDamageOverrideInput.setIntegerValue().setAllowNegative();
        totalHeight += 12;

        addGuiElement(new Label(8, totalHeight + 1, "guistrings.npc.armor_override"));
        armorValueOverrideInput = new NumberInput(120, totalHeight, 60, 0, this) {
            @Override
            public void onValueUpdated(float value) {
                getContainer().armorValue = (int) value;
                hasChanged = true;
            }
        };
        addGuiElement(armorValueOverrideInput);
        armorValueOverrideInput.setIntegerValue().setAllowNegative();
        totalHeight += 12;

        wanderCheckbox = new Checkbox(8, totalHeight, 16, 16, "guistrings.npc.allow_wander") {
            @Override
            public void onToggled() {
                getContainer().wander = checked();
                hasChanged = true;
            }
        };
        addGuiElement(wanderCheckbox);

        if (getContainer().isFactionNpc()) {
            customEquipmentCheckbox = new Checkbox(100, totalHeight, 16, 16, "guistrings.npc.custom_equipment") {
                @Override
                public void onToggled() {
                    getContainer().hasCustomEquipment = checked();
                    hasChanged = true;
                }
            };
            customEquipmentCheckbox.addTooltip("guistrings.npc.custom_equipment.tooltip");
            addGuiElement(customEquipmentCheckbox);
        }
    }

    @Override
    public void setupElements() {
        ownerNameInput.setText(getContainer().ownerName);
        attackDamageOverrideInput.setValue(getContainer().attackDamage);
        armorValueOverrideInput.setValue(getContainer().armorValue);
        maxHealthOverrideInput.setValue(getContainer().maxHealth);
        wanderCheckbox.setChecked(getContainer().wander);
        if (getContainer().isFactionNpc()) {
            customEquipmentCheckbox.setChecked(getContainer().hasCustomEquipment);
        }
        skinButton.setText(getContainer().skinSettings.getDescription().getString());
    }

    @Override
    protected boolean onGuiCloseRequested() {
        // if changes were made while the screen was open, send these to the server
        if (hasChanged) {
            getContainer().sendChangesToServer();
        }
        // force the opening of the normal screen (whatever that may be for the NPC) when the advanced controls are closed
        requestOpen(NpcMenuPayloads.Request.INVENTORY);
        return false;
    }
}
