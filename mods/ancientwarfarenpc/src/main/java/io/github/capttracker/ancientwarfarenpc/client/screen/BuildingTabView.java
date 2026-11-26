/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): the Building tab of the town hall screen.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfarenpc.menu.TownHallMenu;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildingDesign;
import io.github.capttracker.ancientwarfarenpc.settlement.building.BuildProject;
import io.github.capttracker.ancientwarfarenpc.settlement.building.DesignCategory;
import io.github.capttracker.ancientwarfarenpc.settlement.building.DesignMeta;
import io.github.capttracker.ancientwarfarenpc.settlement.building.DesignStyle;
import io.github.capttracker.ancientwarfarenpc.settlement.building.client.DesignClientHooks;
import io.github.capttracker.ancientwarfarenpc.settlement.building.client.DesignPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The Building tab: the planner settings (auto planning, approval, preferred style), the plots with Remove, the build queue (state, progress, missing
 * materials; Approve, Cancel, Up, Down and Show for the ghost) and a form to queue a design by hand (design, plot, turn and position in the plot; the ghost
 * of the design is drawn in the world while the tab is open). Everything is a request to the server ({@link TownHallMenu#sendBuildAction}).
 */
final class BuildingTabView {
    private static final String P = "guistrings.settlement.build.";
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFAAAAAA;
    private static final int YELLOW = 0xFFFFFF55;
    private static final int ORANGE = 0xFFFFAA55;
    private static final int CYAN = 0xFF55FFFF;
    private static final int GREEN = 0xFF55FF55;

    private record PlotLine(UUID id, int number, int x1, int z1, int x2, int z2) {
        int width() {
            return x2 - x1 + 1;
        }

        int depth() {
            return z2 - z1 + 1;
        }
    }

    // the form of the new build (kept while the screen is open)
    private int category = -1;
    private int designIndex;
    private int plotIndex;
    private int rot = 2;
    private int dx;
    private int dz;
    private boolean previewOn = true;
    @Nullable
    private UUID shownProject;

    @Nullable
    private CompoundTag builtFrom;
    private List<PlotLine> plots = List.of();
    private final List<CompoundTag> projectTags = new ArrayList<>();

    /** Forces the next {@link #needsBuild} to say yes (the tab was just opened). */
    void invalidate() {
        builtFrom = null;
    }

    boolean needsBuild(TownHallMenu menu) {
        return builtFrom != menu.getBuildState();
    }

    /** The tab is left or the screen is closed: the ghost goes away. */
    void close() {
        DesignPreview.clear();
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String fit(String text, int width) {
        return Minecraft.getInstance().font.plainSubstrByWidth(text, width);
    }

    private static Direction facing(int rot) {
        return FACINGS[Math.floorMod(rot, FACINGS.length)];
    }

    private void send(CompoundTag action) {
        // set by build(): the menu to send through
        if (menu != null) {
            menu.sendBuildAction(action);
        }
    }

    @Nullable
    private TownHallMenu menu;
    private Runnable changed = () -> {};

    private void local() {
        builtFrom = null;
        changed.run();
    }

    // ---------------------------------------------------------------- the form's data

    private List<DesignMeta> options() {
        List<DesignMeta> list = new ArrayList<>();
        for (DesignMeta meta : DesignClientHooks.metas()) {
            if (category < 0 || meta.category().ordinal() == category) {
                list.add(meta);
            }
        }
        list.sort(Comparator.comparingInt((DesignMeta m) -> m.category().ordinal()).thenComparing(DesignMeta::name));
        return list;
    }

    @Nullable
    private DesignMeta selectedDesign(List<DesignMeta> options) {
        if (options.isEmpty()) {
            return null;
        }
        designIndex = Math.floorMod(designIndex, options.size());
        return options.get(designIndex);
    }

    @Nullable
    private PlotLine selectedPlot() {
        if (plots.isEmpty()) {
            return null;
        }
        plotIndex = Math.floorMod(plotIndex, plots.size());
        return plots.get(plotIndex);
    }

    /** The most the position may be moved east and south so the design still lies in the plot. */
    private int[] maxOffsets(@Nullable DesignMeta meta, @Nullable PlotLine plot) {
        if (meta == null || plot == null) {
            return new int[]{0, 0};
        }
        int[] size = BuildingDesign.rotatedSize(meta.sizeX(), meta.sizeY(), meta.sizeZ(), meta.front(), facing(rot));
        return new int[]{Math.max(0, plot.width() - size[0]), Math.max(0, plot.depth() - size[2])};
    }

    // ---------------------------------------------------------------- build

    /** Fills the area. @return the height of the content */
    int build(CompositeScrolled area, TownHallMenu hall, Runnable refresh) {
        this.menu = hall;
        this.changed = refresh;
        CompoundTag state = hall.getBuildState();
        builtFrom = state;
        if (state.isEmpty()) {
            area.addGuiElement(new Label(6, 6, tr(P + "loading")).setColor(GREY));
            return 24;
        }
        plots = readPlots(state);
        projectTags.clear();
        ListTag projectList = state.getList("projects", Tag.TAG_COMPOUND);
        for (int i = 0; i < projectList.size(); i++) {
            projectTags.add(projectList.getCompound(i));
        }

        int y = 4;
        boolean auto = state.getBoolean("auto");
        boolean approve = state.getBoolean("approve");
        int style = state.getInt("style");
        area.addGuiElement(new Button(4, y, 104, 12, tr(P + (auto ? "auto_on" : "auto_off"))) {
            @Override
            protected void onPressed() {
                CompoundTag action = new CompoundTag();
                action.putBoolean("auto", !auto);
                send(action);
            }
        });
        area.addGuiElement(new Button(112, y, 118, 12, tr(P + (approve ? "approve_auto" : "approve_manual"))) {
            @Override
            protected void onPressed() {
                CompoundTag action = new CompoundTag();
                action.putBoolean("approve", !approve);
                send(action);
            }
        });
        String styleName = style < 0 ? tr("design.ancientwarfarenpc.style.any") : tr(DesignStyle.byOrdinal(style).translationKey());
        area.addGuiElement(new Button(234, y, 110, 12, tr(P + "style", styleName)) {
            @Override
            protected void onPressed() {
                CompoundTag action = new CompoundTag();
                action.putInt("style", style >= DesignStyle.ANY.ordinal() - 1 ? -1 : style + 1);
                send(action);
            }
        });
        y += 16;
        String note = state.getString("note");
        if (!note.isEmpty() && auto) {
            String arg = state.getString("noteArg");
            String text;
            if (note.equals("no_site") && arg.contains("|")) {
                String[] parts = arg.split("\\|", 2);
                text = tr(P + "note.no_site", parts[0], tr(P + "error." + parts[1]));
            } else {
                text = tr(P + "note." + note);
            }
            area.addGuiElement(new Label(6, y, fit(text, 360)).setColor(ORANGE));
            y += 12;
        }
        y += 2;

        // plots
        area.addGuiElement(new Label(6, y, tr(P + "plots")).setColor(YELLOW));
        y += 12;
        if (plots.isEmpty()) {
            area.addGuiElement(new Label(12, y, fit(tr(P + "no_plots"), 350)).setColor(GREY));
            y += 12;
        }
        for (PlotLine plot : plots) {
            area.addGuiElement(new Label(12, y + 2, tr(P + "plot_line", plot.number(), plot.x1(), plot.z1(), plot.x2(), plot.z2(), plot.width(), plot.depth())));
            area.addGuiElement(new Button(300, y, 44, 12, tr(P + "remove")) {
                @Override
                protected void onPressed() {
                    CompoundTag action = new CompoundTag();
                    action.putUUID("removePlot", plot.id());
                    send(action);
                }
            });
            y += 15;
        }
        y += 4;

        // queue
        area.addGuiElement(new Label(6, y + 2, tr(P + "queue")).setColor(YELLOW));
        boolean anyPlanned = projectTags.stream().anyMatch(p -> p.getInt("status") == BuildProject.Status.PLANNED.ordinal());
        if (anyPlanned) {
            area.addGuiElement(new Button(160, y, 90, 12, tr(P + "approve_all")) {
                @Override
                protected void onPressed() {
                    CompoundTag action = new CompoundTag();
                    action.putBoolean("approveAll", true);
                    send(action);
                }
            });
        }
        y += 15;
        if (projectTags.isEmpty()) {
            area.addGuiElement(new Label(12, y, tr(P + "queue_empty")).setColor(GREY));
            y += 12;
        }
        int number = 0;
        for (CompoundTag p : projectTags) {
            number++;
            y = addProject(area, p, number, y);
        }
        y += 6;

        // new build
        y = addForm(area, y);
        return y + 6;
    }

    private static List<PlotLine> readPlots(CompoundTag state) {
        List<PlotLine> list = new ArrayList<>();
        ListTag plotList = state.getList("plots", Tag.TAG_COMPOUND);
        for (int i = 0; i < plotList.size(); i++) {
            CompoundTag p = plotList.getCompound(i);
            if (p.hasUUID("id")) {
                list.add(new PlotLine(p.getUUID("id"), p.getInt("n"), p.getInt("x1"), p.getInt("z1"), p.getInt("x2"), p.getInt("z2")));
            }
        }
        return list;
    }

    private int addProject(CompositeScrolled area, CompoundTag p, int number, int y) {
        UUID id = p.getUUID("id");
        int status = p.getInt("status");
        boolean demolish = p.getInt("kind") == BuildProject.Kind.DEMOLISH.ordinal();
        boolean waiting = p.getBoolean("waiting");
        int progress = p.getInt("progress");
        BuildProject.Status st = BuildProject.Status.values()[Math.max(0, Math.min(status, BuildProject.Status.values().length - 1))];

        String statusText;
        int color;
        switch (st) {
            case PLANNED -> {
                statusText = tr(P + "status.planned");
                color = YELLOW;
            }
            case QUEUED -> {
                statusText = demolish ? tr(P + "status.demolish_queued") : waiting ? tr(P + "status.waiting") : tr(P + "status.ready");
                color = waiting ? ORANGE : WHITE;
            }
            case BUILDING -> {
                statusText = demolish ? tr(P + "status.demolishing", progress) : waiting ? tr(P + "status.waiting_progress", progress) : tr(P + "status.building", progress);
                color = waiting ? ORANGE : CYAN;
            }
            default -> {
                statusText = tr(P + "status.done");
                color = GREEN;
            }
        }
        String category = tr(DesignCategory.byOrdinal(p.getInt("cat")).translationKey());
        String title = (demolish ? tr(P + "demolish_prefix") + " " : "") + p.getString("name") + " (" + category + ")";
        area.addGuiElement(new Label(12, y + 2, fit("" + number + ". " + title + " - " + statusText, 186)).setColor(color));
        boolean open = st != BuildProject.Status.DONE;
        if (st == BuildProject.Status.PLANNED) {
            area.addGuiElement(new Button(200, y, 46, 12, tr(P + "approve")) {
                @Override
                protected void onPressed() {
                    CompoundTag action = new CompoundTag();
                    action.putUUID("approveOne", id);
                    send(action);
                }
            });
        }
        area.addGuiElement(new Button(248, y, 34, 12, tr(P + (id.equals(shownProject) ? "hide" : "show"))) {
            @Override
            protected void onPressed() {
                shownProject = id.equals(shownProject) ? null : id;
                local();
            }
        });
        if (open) {
            area.addGuiElement(new Button(284, y, 14, 12, "^") {
                @Override
                protected void onPressed() {
                    CompoundTag action = new CompoundTag();
                    action.putUUID("up", id);
                    send(action);
                }
            });
            area.addGuiElement(new Button(300, y, 14, 12, "v") {
                @Override
                protected void onPressed() {
                    CompoundTag action = new CompoundTag();
                    action.putUUID("down", id);
                    send(action);
                }
            });
        }
        if (st == BuildProject.Status.PLANNED || st == BuildProject.Status.QUEUED) {
            area.addGuiElement(new Button(316, y, 14, 12, "X") {
                @Override
                protected void onPressed() {
                    CompoundTag action = new CompoundTag();
                    action.putUUID("cancel", id);
                    send(action);
                }
            });
        }
        y += 13;
        ListTag missing = p.getList("missing", Tag.TAG_COMPOUND);
        if (!missing.isEmpty() && open && !demolish) {
            StringBuilder list = new StringBuilder();
            for (int i = 0; i < missing.size(); i++) {
                CompoundTag m = missing.getCompound(i);
                if (i > 0) {
                    list.append(", ");
                }
                list.append(m.getInt("count")).append(' ').append(tr(m.getString("label")));
            }
            int more = p.getInt("missingMore");
            if (more > 0) {
                list.append(" (+").append(more).append(')');
            }
            area.addGuiElement(new Label(18, y, fit(tr(P + "missing", list.toString()), 330)).setColor(ORANGE));
            y += 10;
        }
        String reason = p.getString("reason");
        int dot = reason.indexOf('.');
        String reasonKey = dot < 0 ? reason : reason.substring(0, dot);
        String reasonArg = dot < 0 ? "" : reason.substring(dot + 1);
        String where = tr(P + "where", p.getInt("plot"), p.getInt("x"), p.getInt("y"), p.getInt("z"), tr("direction.ancientwarfarenpc." + p.getString("facing")));
        area.addGuiElement(new Label(18, y, fit(where + " - " + tr(P + "reason." + reasonKey, reasonArg), 330)).setColor(GREY));
        return y + 14;
    }

    private int addForm(CompositeScrolled area, int y) {
        area.addGuiElement(new Label(6, y + 2, tr(P + "new_build")).setColor(YELLOW));
        y += 15;
        List<DesignMeta> options = options();
        DesignMeta meta = selectedDesign(options);
        PlotLine plot = selectedPlot();
        int[] max = maxOffsets(meta, plot);
        dx = Math.max(0, Math.min(max[0], dx));
        dz = Math.max(0, Math.min(max[1], dz));

        String categoryName = category < 0 ? tr(P + "all") : tr(DesignCategory.byOrdinal(category).translationKey());
        area.addGuiElement(new Button(12, y, 110, 12, tr(P + "category", categoryName)) {
            @Override
            protected void onPressed() {
                category = category >= DesignCategory.OTHER.ordinal() ? -1 : category + 1;
                designIndex = 0;
                local();
            }
        });
        area.addGuiElement(new Button(126, y, 14, 12, "<") {
            @Override
            protected void onPressed() {
                designIndex--;
                dx = 0;
                dz = 0;
                local();
            }
        });
        String designName = meta == null ? tr(P + "no_designs") : meta.name() + (meta.builtIn() ? "" : " *");
        area.addGuiElement(new Button(142, y, 140, 12, fit(designName, 134)) {
            @Override
            protected void onPressed() {
                designIndex++;
                dx = 0;
                dz = 0;
                local();
            }
        });
        area.addGuiElement(new Button(284, y, 14, 12, ">") {
            @Override
            protected void onPressed() {
                designIndex++;
                dx = 0;
                dz = 0;
                local();
            }
        });
        y += 15;
        if (meta != null) {
            int[] size = BuildingDesign.rotatedSize(meta.sizeX(), meta.sizeY(), meta.sizeZ(), meta.front(), facing(rot));
            area.addGuiElement(new Label(18, y, fit(tr(P + "design_info", size[0], size[1], size[2], meta.beds(), meta.storage(), tr(meta.style().translationKey()),
                    tr(meta.category().translationKey())), 330)).setColor(GREY));
            y += 12;
        }
        String plotText = plot == null ? tr(P + "no_plots_short") : tr(P + "plot", plot.number());
        area.addGuiElement(new Button(12, y, 80, 12, plotText) {
            @Override
            protected void onPressed() {
                plotIndex++;
                dx = 0;
                dz = 0;
                local();
            }
        });
        area.addGuiElement(new Button(96, y, 100, 12, tr(P + "front", tr("direction.ancientwarfarenpc." + facing(rot).getName()))) {
            @Override
            protected void onPressed() {
                rot = (rot + 1) & 3;
                local();
            }
        });
        area.addGuiElement(new Button(200, y, 84, 12, tr(P + (previewOn ? "preview_on" : "preview_off"))) {
            @Override
            protected void onPressed() {
                previewOn = !previewOn;
                if (previewOn) {
                    shownProject = null;
                }
                local();
            }
        });
        y += 15;
        area.addGuiElement(new Label(12, y + 2, tr(P + "offset_x", dx)));
        area.addGuiElement(new Button(96, y, 14, 12, "-") {
            @Override
            protected void onPressed(int mButton) {
                dx = Math.max(0, dx - (mButton == 1 ? 5 : 1));
                shownProject = null;
                local();
            }
        });
        area.addGuiElement(new Button(112, y, 14, 12, "+") {
            @Override
            protected void onPressed(int mButton) {
                dx = Math.min(max[0], dx + (mButton == 1 ? 5 : 1));
                shownProject = null;
                local();
            }
        });
        area.addGuiElement(new Label(150, y + 2, tr(P + "offset_z", dz)));
        area.addGuiElement(new Button(234, y, 14, 12, "-") {
            @Override
            protected void onPressed(int mButton) {
                dz = Math.max(0, dz - (mButton == 1 ? 5 : 1));
                shownProject = null;
                local();
            }
        });
        area.addGuiElement(new Button(250, y, 14, 12, "+") {
            @Override
            protected void onPressed(int mButton) {
                dz = Math.min(max[1], dz + (mButton == 1 ? 5 : 1));
                shownProject = null;
                local();
            }
        });
        y += 15;
        Button queue = new Button(12, y, 110, 12, tr(P + "queue_it")) {
            @Override
            protected void onPressed() {
                DesignMeta chosen = selectedDesign(options());
                PlotLine chosenPlot = selectedPlot();
                if (chosen == null || chosenPlot == null) {
                    return;
                }
                CompoundTag form = new CompoundTag();
                form.putString("design", chosen.id().toString());
                form.putUUID("plot", chosenPlot.id());
                form.putInt("rot", rot);
                form.putInt("dx", dx);
                form.putInt("dz", dz);
                CompoundTag action = new CompoundTag();
                action.put("queue", form);
                send(action);
            }
        };
        queue.setEnabled(meta != null && plot != null);
        area.addGuiElement(queue);
        area.addGuiElement(new Label(126, y + 2, tr(P + "offset_hint")).setColor(GREY));
        y += 16;
        return y;
    }

    // ---------------------------------------------------------------- the ghost

    /** Every few ticks while the tab is open: draws the ghost of the form (or of the project whose Show was pressed) in the world. */
    void tickPreview(TownHallMenu hall) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (shownProject != null) {
            for (CompoundTag p : projectTags) {
                if (p.hasUUID("id") && p.getUUID("id").equals(shownProject)) {
                    ResourceLocation id = ResourceLocation.tryParse(p.getString("design"));
                    Direction facing = Direction.byName(p.getString("facing"));
                    if (id != null && facing != null) {
                        DesignPreview.show(id, new BlockPos(p.getInt("x"), p.getInt("y"), p.getInt("z")), facing, 0xFFFF55);
                        return;
                    }
                }
            }
            shownProject = null;
        }
        if (!previewOn) {
            DesignPreview.clear();
            return;
        }
        List<DesignMeta> options = options();
        DesignMeta meta = selectedDesign(options);
        PlotLine plot = selectedPlot();
        if (meta == null || plot == null) {
            DesignPreview.clear();
            return;
        }
        Direction facing = facing(rot);
        int[] size = BuildingDesign.rotatedSize(meta.sizeX(), meta.sizeY(), meta.sizeZ(), meta.front(), facing);
        int x = plot.x1() + dx;
        int z = plot.z1() + dz;
        int cx = x + size[0] / 2;
        int cz = z + size[2] / 2;
        int y = mc.level.hasChunk(cx >> 4, cz >> 4) ? mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) : hall.getHallPos().getY();
        DesignPreview.show(meta.id(), new BlockPos(x, y, z), facing, 0x55FF55);
    }
}
