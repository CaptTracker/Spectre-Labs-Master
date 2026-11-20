/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/gui/GuiChunkLoaderDeluxe.
 */
package io.github.capttracker.ancientwarfareautomation.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfareautomation.menu.ChunkLoaderDeluxeMenu;
import net.minecraft.world.level.ChunkPos;

/** The deluxe chunk loader: a 5 x 5 grid of the chunks around it, an X marks the loaded ones, a click toggles one. */
public class GuiChunkLoaderDeluxe extends GuiContainerBase<ChunkLoaderDeluxeMenu> {
    public GuiChunkLoaderDeluxe(ChunkLoaderDeluxeMenu menu) {
        super(menu, 76, 76);
    }

    @Override
    public void initElements() {
        // everything is built in setupElements, it depends on the set the server sends
    }

    @Override
    public void setupElements() {
        clearElements();
        ChunkPos center = new ChunkPos(getContainer().blockEntity().getBlockPos());

        for (int x = center.x - 2, xPos = 0; x <= center.x + 2; x++, xPos++) {
            for (int z = center.z - 2, yPos = 0; z <= center.z + 2; z++, yPos++) {
                if (x == center.x && z == center.z) {
                    continue; // the centre button is added below, it is always forced
                }
                ChunkPos ccip = new ChunkPos(x, z);
                addGuiElement(new ButtonChunk(xPos, yPos, getContainer().ccipSet.contains(ccip)) {
                    @Override
                    protected void onPressed() {
                        getContainer().force(ccip);
                    }
                });
            }
        }

        addGuiElement(new ButtonChunk(2, 2, true));
    }

    private static class ButtonChunk extends Button {
        ButtonChunk(int xPos, int yPos, boolean forced) {
            super(8 + xPos * 12, 8 + yPos * 12, 12, 12, forced ? "X" : "");
        }
    }
}
