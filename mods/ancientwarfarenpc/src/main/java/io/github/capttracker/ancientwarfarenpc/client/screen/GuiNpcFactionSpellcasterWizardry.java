/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiNpcFactionSpellcasterWizardry.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Text;
import io.github.capttracker.ancientwarfarenpc.menu.NpcMenuPayloads;
import io.github.capttracker.ancientwarfarenpc.menu.NpcSpellcasterMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The spell editor of a spellcaster NPC (1.12.2: {@code GuiNpcFactionSpellcasterWizardry}): the preset buttons, a search box over the list of
 * spells, the list of spells to add (click to assign) and the list of assigned spells (click to remove).
 *
 * <p>Changes from 1.12.2: the preset buttons are the ones the server reports as enabled for the faction (the original read the NPC defaults on the
 * client and, for a disabled preset, added the previous button a second time); a click on a preset asks the server to apply it.
 */
public class GuiNpcFactionSpellcasterWizardry extends GuiNpcBase<NpcSpellcasterMenu> {
    private Text spellFilterInput;
    private CompositeScrolled selectionArea;
    private CompositeScrolled assignedSpellsArea;
    private boolean hasChanged = false;

    public GuiNpcFactionSpellcasterWizardry(NpcSpellcasterMenu menu) {
        super(menu, 295, 240);
    }

    @Override
    public void initElements() {
        spellFilterInput = new Text(8, 18 + 36, 116, "", this) {
            @Override
            protected void handleKeyInput(int keyCode, char ch) {
                super.handleKeyInput(keyCode, ch);
                refreshGui();
            }
        };
        selectionArea = new CompositeScrolled(this, 0, 70, 143, 168);
        assignedSpellsArea = new CompositeScrolled(this, 143, 70, 143, 168);
    }

    private class SpellButton extends Button {
        private final ResourceLocation spell;
        private final boolean add;

        private SpellButton(int topLeftX, int topLeftY, ResourceLocation spell, boolean add) {
            super(topLeftX, topLeftY, 115, 12, getContainer().getSpellName(spell));
            this.spell = spell;
            this.add = add;
        }

        @Override
        protected void onPressed() {
            hasChanged = true;
            if (add) {
                getContainer().addSpell(spell);
            } else {
                getContainer().removeSpell(spell);
            }
            getContainer().sendChangesToServer();
            refreshGui();
        }
    }

    @Override
    public void setupElements() {
        clearElements();
        addGuiElement(new Button(xSize - 55 - 8, 8, 55, 12, "guistrings.done") {
            @Override
            protected void onPressed() {
                closeGui();
            }
        });
        addGuiElement(new Label(10, 43, "guistrings.npc.search"));
        addGuiElement(new Label(147, 55, "guistrings.npc.current_spells"));
        addGuiElement(spellFilterInput);
        addGuiElement(selectionArea);
        addGuiElement(assignedSpellsArea);

        // the presets the faction has enabled, two columns
        int shown = 0;
        for (int preset : getContainer().getAvailablePresets()) {
            if (preset < 1 || preset > 5) {
                continue;
            }
            String name = Component.translatable("entity.ancientwarfarenpc." + getContainer().getFactionNpcFullType() + "." + preset + ".name").getString();
            int x = shown < 2 ? 8 : 8 + 112;
            int y = 8 + 15 * (shown < 2 ? shown : shown - 2);
            addGuiElement(new Button(x, y, 100, 12, name) {
                @Override
                protected void onPressed() {
                    hasChanged = true;
                    getContainer().applyPreset(preset);
                }
            });
            shown++;
        }

        // every spell that can be assigned, filtered and sorted by name; clicking one assigns it
        selectionArea.clearElements();
        int selectionAreaHeight = 8;
        String filter = spellFilterInput.getText().toLowerCase(Locale.ROOT);
        List<ResourceLocation> castable = new ArrayList<>(getContainer().getAllSpells());
        castable.removeIf(spell -> !getContainer().getSpellName(spell).toLowerCase(Locale.ROOT).contains(filter));
        castable.sort(Comparator.comparing(spell -> getContainer().getSpellName(spell).toLowerCase(Locale.ROOT)));
        for (ResourceLocation spell : castable) {
            selectionArea.addGuiElement(new SpellButton(8, selectionAreaHeight, spell, true));
            selectionAreaHeight += 12;
        }
        selectionArea.setAreaSize(selectionAreaHeight + 8);

        // the assigned spells; clicking one removes it
        assignedSpellsArea.clearElements();
        int assignedSpellsAreaHeight = 8;
        List<ResourceLocation> assigned = new ArrayList<>(getContainer().getAssignedSpells());
        assigned.sort(Comparator.comparing(spell -> getContainer().getSpellName(spell).toLowerCase(Locale.ROOT)));
        for (ResourceLocation spell : assigned) {
            assignedSpellsArea.addGuiElement(new SpellButton(8, assignedSpellsAreaHeight, spell, false));
            assignedSpellsAreaHeight += 12;
        }
        assignedSpellsArea.setAreaSize(assignedSpellsAreaHeight + 8);
    }

    @Override
    protected boolean onGuiCloseRequested() {
        // if changes were made while the screen was open, send these to the server
        if (hasChanged) {
            getContainer().sendChangesToServer();
        }
        // force the opening of the normal screen (whatever that may be for the NPC) when this one is closed
        requestOpen(NpcMenuPayloads.Request.INVENTORY);
        return false;
    }
}
