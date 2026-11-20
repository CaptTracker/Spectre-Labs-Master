/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the body that npc/gui/GuiNpcBard and GuiNpcFactionBard have in common (the two classes were identical).
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Checkbox;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Line;
import io.github.capttracker.ancientwarfare.client.gui.elements.NumberInput;
import io.github.capttracker.ancientwarfare.client.gui.elements.Text;
import io.github.capttracker.ancientwarfare.util.SongPlayData;
import io.github.capttracker.ancientwarfare.util.SongPlayData.SongEntry;
import io.github.capttracker.ancientwarfarenpc.menu.NpcMenuPayloads;
import io.github.capttracker.ancientwarfarenpc.menu.NpcSongMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * The song list editor of a singing NPC: the play on player entry and random boxes, the delays, and one block per tune with the sound name, its
 * length and volume and the buttons to move or delete it. Sends the list to the server when it closes and returns to the inventory screen.
 *
 * @param <M> the menu type
 */
public abstract class GuiNpcSongBase<M extends NpcSongMenu<?>> extends GuiNpcBase<M> {
    private CompositeScrolled area;

    protected GuiNpcSongBase(M menu) {
        super(menu);
    }

    @Override
    public void initElements() {
        area = new CompositeScrolled(this, 0, 0, xSize, ySize);
    }

    @Override
    public void setupElements() {
        clearElements();
        area.clearElements();
        addGuiElement(area);
        int totalHeight = 8;
        final SongPlayData data = getContainer().data;

        Checkbox playerEntry = new Checkbox(8, totalHeight, 16, 16, "guistrings.play_on_player_entry") {
            @Override
            public void onToggled() {
                data.setPlayOnPlayerEntry(checked());
            }
        };
        playerEntry.setChecked(data.getPlayOnPlayerEntry());
        area.addGuiElement(playerEntry);
        totalHeight += 16;

        Checkbox random = new Checkbox(8, totalHeight, 16, 16, "guistrings.random") {
            @Override
            public void onToggled() {
                data.setRandom(checked());
            }
        };
        random.setChecked(data.getIsRandom());
        area.addGuiElement(random);
        totalHeight += 16;

        NumberInput minDelay = new NumberInput(88, totalHeight, 55, data.getMinDelay(), this) {
            @Override
            public void onValueUpdated(float value) {
                data.setMinDelay((int) value);
            }
        };
        minDelay.setIntegerValue();
        area.addGuiElement(minDelay);
        area.addGuiElement(new Label(8, totalHeight + 1, "guistrings.min_delay"));
        totalHeight += 12;

        NumberInput maxDelay = new NumberInput(88, totalHeight, 55, data.getMaxDelay(), this) {
            @Override
            public void onValueUpdated(float value) {
                data.setMaxDelay((int) value);
            }
        };
        maxDelay.setIntegerValue();
        area.addGuiElement(maxDelay);
        area.addGuiElement(new Label(8, totalHeight + 1, "guistrings.max_delay"));
        totalHeight += 12;

        area.addGuiElement(new Line(0, totalHeight + 2, xSize, totalHeight + 2, 1, 0x000000ff));
        totalHeight += 5;

        totalHeight = addTuneEntries(data, totalHeight);

        Button newTuneButton = new Button(8, totalHeight, 120, 12, "guistrings.new_tune") {
            @Override
            protected void onPressed() {
                data.addNewEntry();
                refreshGui();
            }
        };
        area.addGuiElement(newTuneButton);
        totalHeight += 12;
        area.setAreaSize(totalHeight);
    }

    private int addTuneEntries(final SongPlayData data, int startHeight) {
        for (int i = 0; i < data.size(); i++) {
            startHeight = addTuneEntry(data.get(i), i, startHeight);
        }
        return startHeight;
    }

    private int addTuneEntry(final SongEntry entry, final int index, int startHeight) {
        int y = startHeight;
        Text input = new Text(8, startHeight, 120, entry.name(), this) {
            @Override
            public void onTextUpdated(String oldText, String newText) {
                super.onTextUpdated(oldText, newText);
                ResourceLocation id = ResourceLocation.tryParse(newText);
                entry.setSound(id == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null)); //TODO(port) from the original: test that this finds the sound event
            }
        };
        area.addGuiElement(input);
        startHeight += 12;

        area.addGuiElement(new Label(8, startHeight + 1, "guistrings.length"));
        NumberInput length = new NumberInput(88, startHeight, 60, entry.length(), this) {
            @Override
            public void onValueUpdated(float value) {
                entry.setLength(value);
            }
        };
        area.addGuiElement(length);
        startHeight += 12;

        area.addGuiElement(new Label(8, startHeight + 1, "guistrings.volume"));
        NumberInput volume = new NumberInput(88, startHeight, 60, entry.volume(), this) {
            @Override
            public void onValueUpdated(float value) {
                entry.setVolume((int) value);
            }
        };
        area.addGuiElement(volume);
        startHeight += 12;

        area.addGuiElement(new Button(160, y, 55, 12, "guistrings.up") {
            @Override
            protected void onPressed() {
                getContainer().data.decrementEntry(index);
                refreshGui();
            }
        });
        area.addGuiElement(new Button(160, y + 12, 55, 12, "guistrings.delete") {
            @Override
            protected void onPressed() {
                getContainer().data.deleteEntry(index);
                refreshGui();
            }
        });
        area.addGuiElement(new Button(160, y + 24, 55, 12, "guistrings.down") {
            @Override
            protected void onPressed() {
                getContainer().data.incrementEntry(index);
                refreshGui();
            }
        });

        area.addGuiElement(new Line(0, startHeight + 2, xSize, startHeight + 2, 1, 0x000000ff));
        startHeight += 5;
        return startHeight;
    }

    @Override
    protected boolean onGuiCloseRequested() {
        getContainer().sendTuneDataToServer();
        requestOpen(NpcMenuPayloads.Request.INVENTORY);
        return false;
    }
}
