/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): the data and the requests of the Building tab of the town hall.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

/**
 * Server side of the Building tab of the town hall menu: {@link #toTag} writes what the tab shows (settings, plots, the build queue with state, progress and
 * missing materials) and {@link #handle} applies what the owner asked for. The menu has checked that the player may manage the hall. Nothing in a request
 * is trusted: ids are looked up, numbers are clamped, and a manual build goes through the same site check as the planner.
 */
public final class BuildingMenuSupport {
    /** The most finished builds listed. */
    private static final int MAX_DONE_SHOWN = 10;
    private static final int MAX_MISSING_SHOWN = 4;
    private static final int MAX_OPEN_PROJECTS = 40;
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private BuildingMenuSupport() {}

    // ---------------------------------------------------------------- to the screen

    public static CompoundTag toTag(ServerLevel level, Settlement settlement) {
        SettlementBuilding building = settlement.getBuilding();
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("auto", building.isAutoPlan());
        tag.putBoolean("approve", building.isAutoApprove());
        tag.putInt("style", building.getPreferredStyle());
        tag.putString("note", building.getNote());
        tag.putString("noteArg", building.getNoteArg());
        ListTag plots = new ListTag();
        for (BuildingPlot plot : building.getPlots()) {
            CompoundTag p = new CompoundTag();
            p.putUUID("id", plot.getId());
            p.putInt("n", plot.getNumber());
            p.putInt("x1", plot.getMinX());
            p.putInt("z1", plot.getMinZ());
            p.putInt("x2", plot.getMaxX());
            p.putInt("z2", plot.getMaxZ());
            p.putInt("y", plot.getY());
            plots.add(p);
        }
        tag.put("plots", plots);

        List<ItemStack> stock = BuildMaterials.stock(level, settlement);
        ListTag projects = new ListTag();
        List<BuildProject> all = building.getProjects();
        int doneSkip = Math.max(0, (int) all.stream().filter(BuildProject::isDone).count() - MAX_DONE_SHOWN);
        for (BuildProject project : all) {
            if (project.isDone()) {
                if (doneSkip > 0) {
                    doneSkip--;
                    continue;
                }
                if (project.getKind() == BuildProject.Kind.DEMOLISH) {
                    continue;
                }
            }
            BuildingDesign design = BuildJobs.designOf(project);
            CompoundTag p = new CompoundTag();
            p.putUUID("id", project.getId());
            p.putString("design", project.getDesignId().toString());
            p.putString("name", design == null ? project.getDesignId().getPath() : design.getName());
            p.putInt("cat", design == null ? DesignCategory.OTHER.ordinal() : design.getCategory().ordinal());
            p.putInt("beds", design == null ? 0 : design.getBeds());
            p.putInt("kind", project.getKind().ordinal());
            p.putInt("status", project.getStatus().ordinal());
            p.putInt("progress", project.getProgress());
            p.putString("reason", project.getReason());
            int plotNumber = project.getPlotId() == null ? 0 : building.plot(project.getPlotId()).map(BuildingPlot::getNumber).orElse(0);
            p.putInt("plot", plotNumber);
            p.putInt("x", project.getOrigin().getX());
            p.putInt("y", project.getOrigin().getY());
            p.putInt("z", project.getOrigin().getZ());
            p.putString("facing", project.getFacing().getName());
            ListTag missingTag = new ListTag();
            boolean waiting = false;
            if (design != null && project.isOpen() && project.getKind() == BuildProject.Kind.BUILD) {
                List<BuildMaterials.Req> missing = BuildMaterials.missing(BuildMaterials.remaining(project, design), stock);
                waiting = !missing.isEmpty();
                for (int i = 0; i < Math.min(missing.size(), MAX_MISSING_SHOWN); i++) {
                    CompoundTag m = new CompoundTag();
                    m.putString("label", missing.get(i).label());
                    m.putInt("count", missing.get(i).count());
                    missingTag.add(m);
                }
                p.putInt("missingMore", Math.max(0, missing.size() - MAX_MISSING_SHOWN));
            }
            p.put("missing", missingTag);
            p.putBoolean("waiting", waiting);
            projects.add(p);
        }
        tag.put("projects", projects);
        return tag;
    }

    // ---------------------------------------------------------------- from the screen

    /** Applies one request of the screen. Unknown or invalid parts are ignored; a refused manual build tells the player why. */
    public static void handle(ServerLevel level, Settlement settlement, ServerPlayer player, CompoundTag action) {
        SettlementBuilding building = settlement.getBuilding();
        if (action.contains("auto", Tag.TAG_BYTE)) {
            building.setAutoPlan(action.getBoolean("auto"));
        }
        if (action.contains("approve", Tag.TAG_BYTE)) {
            building.setAutoApprove(action.getBoolean("approve"));
            if (building.isAutoApprove()) {
                for (BuildProject project : List.copyOf(building.getProjects())) {
                    building.approve(project.getId());
                }
            }
        }
        if (action.contains("style", Tag.TAG_INT)) {
            building.setPreferredStyle(action.getInt("style"));
        }
        if (action.hasUUID("approveOne")) {
            building.approve(action.getUUID("approveOne"));
        }
        if (action.contains("approveAll")) {
            for (BuildProject project : List.copyOf(building.getProjects())) {
                building.approve(project.getId());
            }
        }
        if (action.hasUUID("cancel")) {
            building.cancel(action.getUUID("cancel"));
        }
        if (action.hasUUID("up")) {
            building.move(action.getUUID("up"), -1);
        }
        if (action.hasUUID("down")) {
            building.move(action.getUUID("down"), 1);
        }
        if (action.hasUUID("removePlot")) {
            if (!building.removePlot(action.getUUID("removePlot"))) {
                player.displayClientMessage(Component.translatable("guistrings.settlement.build.error.plot_busy"), true);
            }
        }
        if (action.contains("queue", Tag.TAG_COMPOUND)) {
            queue(level, settlement, player, action.getCompound("queue"));
        }
    }

    private static void queue(ServerLevel level, Settlement settlement, Player player, CompoundTag tag) {
        SettlementBuilding building = settlement.getBuilding();
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("design"));
        BuildingDesign design = id == null ? null : BuildingDesigns.get(id);
        if (design == null || !(BuildingDesigns.usableBy(design, player.getUUID()) || BuildingDesigns.usableBy(design, settlement.getFounderId()))) {
            player.displayClientMessage(Component.translatable("guistrings.settlement.build.error.no_design"), true);
            return;
        }
        UUID plotId = tag.hasUUID("plot") ? tag.getUUID("plot") : null;
        BuildingPlot plot = plotId == null ? null : building.plot(plotId).orElse(null);
        if (plot == null) {
            player.displayClientMessage(Component.translatable("guistrings.settlement.build.error.no_plot"), true);
            return;
        }
        int open = 0;
        for (BuildProject project : building.getProjects()) {
            if (project.isOpen()) {
                open++;
            }
        }
        if (open >= MAX_OPEN_PROJECTS) {
            player.displayClientMessage(Component.translatable("guistrings.settlement.build.error.queue_full"), true);
            return;
        }
        Direction facing = FACINGS[Math.floorMod(tag.getInt("rot"), FACINGS.length)];
        int dx = Math.max(0, Math.min(1000, tag.getInt("dx")));
        int dz = Math.max(0, Math.min(1000, tag.getInt("dz")));
        SitePicker.Result result = SitePicker.check(level, settlement, design, plot, facing, plot.getMinX() + dx, plot.getMinZ() + dz);
        if (result.site() == null) {
            player.displayClientMessage(Component.translatable("guistrings.settlement.build.error." + result.error()), true);
            return;
        }
        SitePicker.Site site = result.site();
        building.addProject(new BuildProject(UUID.randomUUID(), design.getId(), plot.getId(), site.origin(), facing, design.rotatedSize(facing), BuildProject.Kind.BUILD,
                BuildProject.Status.QUEUED, "manual", null, level.getGameTime(), site.fills()));
        player.displayClientMessage(Component.translatable("guistrings.settlement.build.queued", design.getName()), true);
    }
}
