/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): the building state of a settlement.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a settlement keeps for building (rework phase 9): the plots, the build queue (planned, running and finished builds in queue order) and the
 * planner settings. Part of the settlement record ({@link Settlement#getBuilding()}) and saved with it. Server side only.
 */
public final class SettlementBuilding {
    private final Settlement settlement;
    private final List<BuildingPlot> plots = new ArrayList<>();
    private final List<BuildProject> projects = new ArrayList<>();
    private int nextPlotNumber = 1;
    /** The auto planning switch of this settlement; null means the config default. */
    @Nullable
    private Boolean autoPlan;
    /** Whether plans start without the owner's approval; null means the config default. */
    @Nullable
    private Boolean autoApprove;
    /** The preferred style: the ordinal of a {@link DesignStyle}, or -1 for any. */
    private int preferredStyle = -1;

    // not saved
    long plannerNext;
    long maintenanceNext;
    String note = "";
    String noteArg = "";
    /** The material needs posted to the settlement: id to {count, game time of the post}. */
    final Map<String, long[]> postedNeeds = new HashMap<>();

    public SettlementBuilding(Settlement settlement) {
        this.settlement = settlement;
    }

    private void dirty() {
        settlement.markDirty();
    }

    // ---------------------------------------------------------------- plots

    public List<BuildingPlot> getPlots() {
        return Collections.unmodifiableList(plots);
    }

    public Optional<BuildingPlot> plot(UUID id) {
        for (BuildingPlot plot : plots) {
            if (plot.getId().equals(id)) {
                return Optional.of(plot);
            }
        }
        return Optional.empty();
    }

    /** The plot that holds the column, if any. */
    public Optional<BuildingPlot> plotAt(int x, int z) {
        for (BuildingPlot plot : plots) {
            if (plot.contains(x, z)) {
                return Optional.of(plot);
            }
        }
        return Optional.empty();
    }

    /** Adds a plot. The caller has checked the rules (inside the land, size, overlap, count); see {@code BuildingZoneItem}. */
    public BuildingPlot addPlot(int x1, int z1, int x2, int z2, int y) {
        BuildingPlot plot = new BuildingPlot(UUID.randomUUID(), nextPlotNumber++, x1, z1, x2, z2, y);
        plots.add(plot);
        dirty();
        return plot;
    }

    /**
     * Removes a plot. Builds that are planned, queued or under construction on it are cancelled (rework phase 9, builders: what the builders already placed
     * stays in the world; the builders let go of the job by themselves).
     * @return false if the plot is not known
     */
    public boolean removePlot(UUID id) {
        BuildingPlot plot = plot(id).orElse(null);
        if (plot == null) {
            return false;
        }
        projects.removeIf(project -> id.equals(project.getPlotId()) && project.getStatus() != BuildProject.Status.DONE);
        plots.remove(plot);
        dirty();
        return true;
    }

    // ---------------------------------------------------------------- projects

    public List<BuildProject> getProjects() {
        return Collections.unmodifiableList(projects);
    }

    public Optional<BuildProject> project(UUID id) {
        for (BuildProject project : projects) {
            if (project.getId().equals(id)) {
                return Optional.of(project);
            }
        }
        return Optional.empty();
    }

    public void addProject(BuildProject project) {
        projects.add(project);
        dirty();
    }

    boolean removeProject(UUID id) {
        boolean removed = projects.removeIf(project -> project.getId().equals(id));
        if (removed) {
            dirty();
        }
        return removed;
    }

    /** Moves an open (not finished) project one place earlier ({@code -1}) or later ({@code +1}) in the queue, past the other open ones. */
    public boolean move(UUID id, int direction) {
        int index = -1;
        for (int i = 0; i < projects.size(); i++) {
            if (projects.get(i).getId().equals(id)) {
                index = i;
            }
        }
        if (index < 0 || !projects.get(index).isOpen()) {
            return false;
        }
        int step = direction < 0 ? -1 : 1;
        int other = index + step;
        while (other >= 0 && other < projects.size() && !projects.get(other).isOpen()) {
            other += step;
        }
        if (other < 0 || other >= projects.size()) {
            return false;
        }
        BuildProject moved = projects.remove(index);
        projects.add(other, moved);
        dirty();
        return true;
    }

    /** The owner approved a planned project: it joins the queue. */
    public boolean approve(UUID id) {
        BuildProject project = project(id).orElse(null);
        if (project == null || project.getStatus() != BuildProject.Status.PLANNED) {
            return false;
        }
        project.setStatus(BuildProject.Status.QUEUED);
        dirty();
        return true;
    }

    /** Cancels a project that is planned or queued (one under construction or finished is not cancelled this way). */
    public boolean cancel(UUID id) {
        BuildProject project = project(id).orElse(null);
        if (project == null || project.getStatus() == BuildProject.Status.BUILDING || project.getStatus() == BuildProject.Status.DONE) {
            return false;
        }
        return removeProject(id);
    }

    void changed() {
        dirty();
    }

    // ---------------------------------------------------------------- settings

    public boolean isAutoPlan() {
        return autoPlan != null ? autoPlan : SettlementConfig.autoPlanDefault;
    }

    public void setAutoPlan(boolean on) {
        autoPlan = on;
        plannerNext = 0L;
        dirty();
    }

    public boolean isAutoApprove() {
        return autoApprove != null ? autoApprove : SettlementConfig.autoApproveDefault;
    }

    public void setAutoApprove(boolean on) {
        autoApprove = on;
        dirty();
    }

    /** The ordinal of the preferred {@link DesignStyle}, or -1 for any. */
    public int getPreferredStyle() {
        return preferredStyle;
    }

    public void setPreferredStyle(int style) {
        preferredStyle = style < 0 || style >= DesignStyle.ANY.ordinal() ? -1 : style;
        plannerNext = 0L;
        dirty();
    }

    /** What the planner could not do at its last run: a lang key suffix ({@code no_plots}, {@code queue_full}, {@code no_site} ...), empty if nothing. */
    public String getNote() {
        return note;
    }

    public String getNoteArg() {
        return noteArg;
    }

    // ---------------------------------------------------------------- saving

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag plotList = new ListTag();
        for (BuildingPlot plot : plots) {
            plotList.add(plot.save());
        }
        tag.put("plots", plotList);
        ListTag projectList = new ListTag();
        for (BuildProject project : projects) {
            projectList.add(project.save());
        }
        tag.put("projects", projectList);
        tag.putInt("nextPlot", nextPlotNumber);
        if (autoPlan != null) {
            tag.putBoolean("autoPlan", autoPlan);
        }
        if (autoApprove != null) {
            tag.putBoolean("autoApprove", autoApprove);
        }
        tag.putInt("style", preferredStyle);
        return tag;
    }

    public void load(CompoundTag tag) {
        plots.clear();
        projects.clear();
        ListTag plotList = tag.getList("plots", Tag.TAG_COMPOUND);
        for (int i = 0; i < plotList.size(); i++) {
            BuildingPlot plot = BuildingPlot.load(plotList.getCompound(i));
            if (plot != null) {
                plots.add(plot);
            }
        }
        ListTag projectList = tag.getList("projects", Tag.TAG_COMPOUND);
        for (int i = 0; i < projectList.size(); i++) {
            BuildProject project = BuildProject.load(projectList.getCompound(i));
            if (project != null) {
                projects.add(project);
            }
        }
        nextPlotNumber = Math.max(1, tag.getInt("nextPlot"));
        for (BuildingPlot plot : plots) {
            nextPlotNumber = Math.max(nextPlotNumber, plot.getNumber() + 1);
        }
        autoPlan = tag.contains("autoPlan") ? tag.getBoolean("autoPlan") : null;
        autoApprove = tag.contains("autoApprove") ? tag.getBoolean("autoApprove") : null;
        preferredStyle = tag.contains("style") ? tag.getInt("style") : -1;
        if (preferredStyle >= DesignStyle.ANY.ordinal() || preferredStyle < -1) {
            preferredStyle = -1;
        }
    }
}
