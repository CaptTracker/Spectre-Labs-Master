/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/research/GuiResearchStationSelection.
 */
package io.github.capttracker.ancientwarfare.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Tooltip;
import io.github.capttracker.ancientwarfare.menu.ResearchStationMenu;
import io.github.capttracker.ancientwarfare.menu.ScreenOnlyMenu;
import io.github.capttracker.ancientwarfare.research.ResearchGoal;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The research queue picker, shown over the research bench screen: the queued research on the left (with a button to take
 * a goal out) and the research that can be queued now on the right (with a button to add it). Changes are requests to the
 * server through the bench's menu, which sends the new queue back.
 *
 * <p>The screen has its own empty menu so the bench's slots are not drawn or clickable over it (the original moved them
 * off screen); the bench's menu is only the source of data and the target of requests. Closing returns to the bench screen.
 *
 * <p>"Can be queued now" is worked out here from the book owner's finished research (sent by the bench's menu) plus the
 * bench's current goal and queue, so it works for any player's book.
 */
public class GuiResearchStationSelection extends GuiContainerBase<ScreenOnlyMenu> {
    private final GuiResearchStation parent;
    private final ResearchStationMenu station;

    private CompositeScrolled queueArea;
    private CompositeScrolled selectionArea;

    public GuiResearchStationSelection(GuiResearchStation parent) {
        super(new ScreenOnlyMenu(parent.getContainer().player.getInventory()), 400, 240);
        this.parent = parent;
        this.station = parent.getContainer();
    }

    @Override
    protected void init() {
        super.init();
        // the bench's menu now talks to this screen (1.12.2: container.setGui)
        station.setRefreshListener(this::refreshGui);
    }

    @Override
    public void initElements() {
        queueArea = new CompositeScrolled(this, 0, 40, 200, 200);
        addGuiElement(queueArea);

        selectionArea = new CompositeScrolled(this, 200, 40, 200, 200);
        addGuiElement(selectionArea);

        addGuiElement(new Label(8, 8, "guistrings.research.queued_research"));
        addGuiElement(new Label(240 + 8, 8, "guistrings.research.learnable_research"));
    }

    @Override
    public void setupElements() {
        selectionArea.clearElements();
        queueArea.clearElements();
        ResourceLocation research = station.currentGoal;

        int totalHeight = 8;

        if (research != null) {
            totalHeight = addQueuedGoal(totalHeight, research, false);
        }

        for (ResourceLocation goal : station.queuedResearch) {
            totalHeight = addQueuedGoal(totalHeight, goal, true);
        }

        queueArea.setAreaSize(totalHeight + 8);

        totalHeight = 8;

        for (ResourceLocation goal : researchableGoals()) {
            totalHeight = addSelectableGoal(totalHeight, goal);
        }

        selectionArea.setAreaSize(totalHeight + 8);
    }

    /** Goals not known, queued or in progress whose dependencies all are (1.12.2: ResearchTracker.getResearchableGoals). */
    private List<ResourceLocation> researchableGoals() {
        List<ResourceLocation> ret = new ArrayList<>();
        Registry<ResearchGoal> goals = ResearchScreenData.goals();
        if (goals == null || station.researcherName == null) {
            return ret;
        }
        Set<ResourceLocation> total = new HashSet<>(station.finishedResearch);
        total.addAll(station.queuedResearch);
        if (station.currentGoal != null) {
            total.add(station.currentGoal);
        }
        for (ResourceLocation id : goals.keySet()) {
            if (!total.contains(id) && ResearchGoal.canResearch(goals, id, total)) {
                ret.add(id);
            }
        }
        ret.sort(Comparator.comparing(ResearchScreenData::name));
        return ret;
    }

    private int addQueuedGoal(int totalHeight, ResourceLocation id, boolean removeButton) {
        ResearchGoal goal = ResearchScreenData.goal(id);
        if (goal == null) {
            return totalHeight;
        }
        String name = ResearchScreenData.name(id);
        if (!removeButton) {
            name = name + " (" + Component.translatable("guistrings.research.current_goal").getString() + ")";
        }

        Label label = new Label(8, totalHeight + 1, name);
        queueArea.addGuiElement(label);

        Tooltip goalTooltip = createGoalTooltip(label, goal);

        if (removeButton) {
            GoalButton button = new GoalButton(200 - 8 - 12 - 12, totalHeight, 12, 12, id, false);
            button.setTooltip(goalTooltip);
            queueArea.addGuiElement(button);
        }
        return totalHeight + 12;
    }

    private int addSelectableGoal(int totalHeight, ResourceLocation id) {
        ResearchGoal goal = ResearchScreenData.goal(id);
        if (goal == null) {
            return totalHeight;
        }

        Label label = new Label(8, totalHeight + 1, ResearchScreenData.name(id));
        selectionArea.addGuiElement(label);

        GoalButton button = new GoalButton(200 - 8 - 12 - 12, totalHeight, 12, 12, id, true);
        button.setTooltip(createGoalTooltip(label, goal));
        selectionArea.addGuiElement(button);
        return totalHeight + 12;
    }

    private Tooltip createGoalTooltip(Label label, ResearchGoal goal) {
        Tooltip tooltip = new Tooltip(110, 75);
        tooltip.addTooltipElement(new Label(0, 0, Component.translatable("guistrings.research.research_time", goal.time()).getString()));
        tooltip.addTooltipElement(new Label(0, 10, "guistrings.research.resources_needed"));
        int x = 0;
        int y = 0;
        for (ItemStack stack : ResearchScreenData.resourcesForDisplay(goal)) {
            tooltip.addTooltipElement(new ItemSlot(x * 18, y * 18 + 20, stack, this));
            x++;
            if (x > 2) {
                x = 0;
                y++;
            }
        }
        label.setTooltip(tooltip);
        return tooltip;
    }

    /** Back to the bench screen; the bench's menu stays open. */
    @Override
    protected boolean onGuiCloseRequested() {
        parent.bindMenu();
        parent.refreshGui();
        showScreen(parent);
        return false;
    }

    private class GoalButton extends Button {
        private final ResourceLocation goal;
        private final boolean add;

        GoalButton(int topLeftX, int topLeftY, int width, int height, ResourceLocation goal, boolean add) {
            super(topLeftX, topLeftY, width, height, add ? "+" : "-");
            this.goal = goal;
            this.add = add;
        }

        @Override
        protected void onPressed() {
            if (add) {
                station.enqueueResearch(goal);
            } else {
                station.dequeueResearch(goal);
            }
        }
    }
}
