/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/research/GuiResearchStation.
 */
package io.github.capttracker.ancientwarfare.client.screen;

import io.github.capttracker.ancientwarfare.block.AwDirection;
import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Checkbox;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.ProgressBar;
import io.github.capttracker.ancientwarfare.client.gui.elements.Tooltip;
import io.github.capttracker.ancientwarfare.menu.ResearchBookMenu;
import io.github.capttracker.ancientwarfare.menu.ResearchStationMenu;
import io.github.capttracker.ancientwarfare.research.ResearchGoal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The research bench: researcher, current goal and progress, the nine resources the next queued goal needs (shown as a
 * guide beside the real input slots), the adjacent-inventory options, and the buttons for the research queue and the
 * research book. The state comes from {@link ResearchStationMenu}, which the server keeps up to date.
 */
public class GuiResearchStation extends GuiContainerBase<ResearchStationMenu> {
    private Label researcherLabel;
    private Label researchGoalLabel;
    private ProgressBar bar;
    private Checkbox useAdjacentInventory;
    private Button info;
    private Button invDir;
    private Button invSide;

    private final ItemSlot[] layoutSlots = new ItemSlot[9];

    public GuiResearchStation(ResearchStationMenu menu) {
        super(menu, 178, 240);
    }

    @Override
    public void initElements() {
        String name = getContainer().researcherName == null ? "guistrings.research.no_researcher" : getContainer().researcherName;
        researcherLabel = new Label(8, 8, name);
        addGuiElement(researcherLabel);

        name = "guistrings.research.no_research";
        ResourceLocation researchName = getContainer().currentGoal;
        if (researchName != null && ResearchScreenData.goal(researchName) != null) {
            name = ResearchScreenData.name(researchName);
        }
        researchGoalLabel = new Label(80, 8 + 18 * 2, name);
        addGuiElement(researchGoalLabel);
        addGuiElement(new Label(8, 8 + 18 * 2, GuiRender.translate("guistrings.research.current_goal") + ":"));

        bar = new ProgressBar(70, 8 + 18 * 2 + 12, 178 - 70 - 8, 12);
        addGuiElement(bar);

        Button button = new Button(178 - 8 - 110, 8 + 12 + 4, 110, 12, "guistrings.research.research_queue") {
            @Override
            protected void onPressed() {
                showScreen(new GuiResearchStationSelection(GuiResearchStation.this));
            }
        };
        addGuiElement(button);
        info = new Button(30, 8 + 12 + 4, 24, 12, "guistrings.research.info") {
            @Override
            protected void onPressed() {
                // Client-only menu, like the original's new ContainerResearchBook(...): the bench's menu stays open underneath.
                showScreen(new GuiResearchBook(new ResearchBookMenu(-1, getContainer().player.getInventory()), GuiResearchStation.this));
            }
        };
        addGuiElement(info);
        if (getContainer().researcherName == null) {
            info.setEnabled(false);
        }

        int x;
        int y;
        for (int i = 0; i < layoutSlots.length; i++) {
            x = (i % 3) * 18 + 98;
            y = (i / 3) * 18 + 98;
            layoutSlots[i] = new ItemSlot(x, y, ItemStack.EMPTY, this);
            addGuiElement(layoutSlots[i]);
        }
        addGuiElement(new Label(8, 8 + 18 * 2 + 12 + 1, "guistrings.research.progress"));
        addGuiElement(new Label(8 + 18, 8 + 3 * 18 + 10 + 4 + 10, "guistrings.research.input"));
        addGuiElement(new Label(8 + 5 * 18, 8 + 3 * 18 + 10 + 4 + 10, "guistrings.research.needed"));

        useAdjacentInventory = new Checkbox(8, 8 + 3 * 18 + 6, 16, 16, "guistrings.research.adj_inv") {
            @Override
            public void onToggled() {
                getContainer().toggleUseAdjacentInventory();
                setChecked(getContainer().useAdjacentInventory);
            }
        };
        Tooltip tip = new Tooltip(50, 20);
        tip.addTooltipElement(new Label(0, 0, "guistrings.research.use_adjacent_inventory"));
        useAdjacentInventory.setTooltip(tip);
        addGuiElement(useAdjacentInventory);

        invDir = new Button(80, 8 + 3 * 18 + 6, 40, 16, AwDirection.getDirectionFor(getContainer().blockEntity().inventoryDirection).getTranslationKey()) {
            @Override
            protected void onPressed() {
                getContainer().onDirPressed();
                refreshGui();
            }
        };
        tip = new Tooltip(50, 20);
        tip.addTooltipElement(new Label(0, 0, "guistrings.research.invDir"));
        invDir.setTooltip(tip);
        addGuiElement(invDir);
        invSide = new Button(120, 8 + 3 * 18 + 6, 40, 16, AwDirection.getDirectionFor(getContainer().blockEntity().inventorySide).getTranslationKey()) {
            @Override
            protected void onPressed() {
                getContainer().onSidePressed();
                refreshGui();
            }
        };
        tip = new Tooltip(50, 20);
        tip.addTooltipElement(new Label(0, 0, "guistrings.research.invSide"));
        invSide.setTooltip(tip);
        addGuiElement(invSide);
    }

    @Override
    public void setupElements() {
        ResearchStationMenu menu = getContainer();
        String name = menu.researcherName == null ? "guistrings.research.no_researcher" : menu.researcherName;
        researcherLabel.setText(name);

        if (menu.researcherName == null) {
            cleanLayout();
            info.setEnabled(false);
        } else {
            info.setEnabled(true);
        }

        name = "guistrings.research.no_research";
        float progress = 0.f;
        ResourceLocation researchName = menu.currentGoal;
        if (researchName != null) {
            ResearchGoal goal = ResearchScreenData.goal(researchName);
            if (goal != null) {
                name = ResearchScreenData.name(researchName);

                float total = goal.time();
                float time = menu.progress;
                if (total == 0) {
                    total = time;
                }
                // 1.12.2 divided by zero here when both were 0
                progress = total == 0 ? 0.f : time / total;
            }
            cleanLayout();
        } else {
            List<ResourceLocation> queue = menu.queuedResearch;
            if (!queue.isEmpty()) {
                ResourceLocation first = queue.get(0);
                ResearchGoal goal = ResearchScreenData.goal(first);
                if (goal != null) {
                    name = ResearchScreenData.name(first);
                    List<ItemStack> resources = ResearchScreenData.resourcesForDisplay(goal);
                    for (int i = 0; i < layoutSlots.length; i++) {
                        layoutSlots[i].setItem(i >= resources.size() ? ItemStack.EMPTY : resources.get(i).copy());
                    }
                }
            } else {
                cleanLayout();
            }
        }
        bar.setProgress(progress);
        researchGoalLabel.setText(name);

        useAdjacentInventory.setChecked(menu.useAdjacentInventory);
        invDir.setText(AwDirection.getDirectionFor(menu.blockEntity().inventoryDirection).getTranslationKey());
        invSide.setText(AwDirection.getDirectionFor(menu.blockEntity().inventorySide).getTranslationKey());
    }

    private void cleanLayout() {
        for (ItemSlot layoutSlot : layoutSlots) {
            layoutSlot.setItem(ItemStack.EMPTY);
        }
    }
}
