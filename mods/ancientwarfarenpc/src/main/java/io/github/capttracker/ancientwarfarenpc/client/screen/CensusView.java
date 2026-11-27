/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 6): the Census tab of the town hall screen.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase.ActivationEvent;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.ITooltipRenderer;
import io.github.capttracker.ancientwarfare.client.gui.Listener;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.GuiElement;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Tooltip;
import io.github.capttracker.ancientwarfarenpc.entity.NpcNames;
import io.github.capttracker.ancientwarfarenpc.menu.TownHallMenu;
import io.github.capttracker.ancientwarfarenpc.settlement.Census;
import io.github.capttracker.ancientwarfarenpc.settlement.family.FamilyAge;
import io.github.capttracker.ancientwarfarenpc.settlement.NpcMorale;
import io.github.capttracker.ancientwarfarenpc.settlement.client.CensusHighlight;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * The Census tab: one row for each member (name, role, mood, where, what it does, problem icons), sorted and filtered on the client from the snapshot the
 * server sends while the tab is open. The list scrolls (the area is a {@link CompositeScrolled}); a row is one element that draws all its columns, so 100
 * and more members stay cheap. A click on a row highlights the NPC ({@link CensusHighlight}); the icons have tooltips.
 */
final class CensusView {
    private enum Sort {
        NAME, ROLE, MOOD, PROBLEMS;

        Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** The row text is drawn at this scale to fit the columns into the window. */
    private static final float TEXT_SCALE = 0.75F;
    private static final int ROW_X = 2;
    private static final int ROW_WIDTH = 326;
    private static final int ROW_HEIGHT = 12;
    private static final int ICON_SIZE = 8;
    private static final int ICONS_WIDTH = Census.FLAGS.length * ICON_SIZE;
    // columns in text units (before the scale)
    private static final int COL_NAME = 2;
    private static final int COL_ROLE = 72;
    private static final int COL_MOOD = 126;
    private static final int COL_WHERE = 194;
    private static final int COL_TASK = 244;
    private static final int COL_END = (int) ((ROW_WIDTH - ICONS_WIDTH - 2) / TEXT_SCALE);
    private static final int[] FLAG_COLORS = {0xFFFF5555, 0xFFFFFF55, 0xFF8899FF, 0xFFFFAA55, 0xFFFF55FF, 0xFFFF7777, 0xFF55FFFF, 0xFFFFFFFF};
    private static final String[] FLAG_LETTERS = {"S", "H", "B", "T", "J", "+", "?", "!"};

    private Sort sort = Sort.NAME;
    /** 0 = all, 1 = problems only, otherwise the role in {@link #filterRole}. */
    private int filterMode = 0;
    private String filterRole = "";
    /** What the last build was made from, so an unchanged snapshot does not rebuild the list (and reset the tooltips). */
    private List<Census.Entry> builtFrom = null;
    private Sort builtSort = null;
    private int builtFilter = -1;
    private String builtRole = null;

    /** Forces the next {@link #needsBuild} to say yes (the tab was just opened). */
    void invalidate() {
        builtFrom = null;
    }

    boolean needsBuild(TownHallMenu menu) {
        return builtFrom != menu.getCensus() || builtSort != sort || builtFilter != filterMode || !filterRole.equals(builtRole);
    }

    /** Fills the area. @return the height of the content */
    int build(CompositeScrolled area, TownHallMenu menu, Runnable changed) {
        List<Census.Entry> all = menu.getCensus();
        builtFrom = all;
        TreeSet<String> roles = new TreeSet<>();
        for (Census.Entry entry : all) {
            roles.add(entry.type());
        }
        if (filterMode > 1 && !roles.contains(filterRole)) {
            filterMode = 0;
            filterRole = "";
        }
        builtSort = sort;
        builtFilter = filterMode;
        builtRole = filterRole;

        List<Census.Entry> shown = new ArrayList<>();
        for (Census.Entry entry : all) {
            if (filterMode == 0 || filterMode == 1 && entry.hasProblem() || filterMode > 1 && entry.type().equals(filterRole)) {
                shown.add(entry);
            }
        }
        BlockPos hall = menu.getHallPos();
        shown.sort(comparator(sort));

        int y = 4;
        area.addGuiElement(new Button(4, y, 80, 12, tr("guistrings.census.sort", tr("guistrings.census.sort." + sort.name().toLowerCase(Locale.ROOT)))) {
            @Override
            protected void onPressed() {
                sort = sort.next();
                changed.run();
            }
        });
        String filterText = filterMode == 0 ? tr("guistrings.census.filter.all") : filterMode == 1 ? tr("guistrings.census.filter.problems") : roleName(filterRole);
        area.addGuiElement(new Button(88, y, 100, 12, tr("guistrings.census.filter", filterText)) {
            @Override
            protected void onPressed() {
                // all -> problems only -> each role that is present -> all
                List<String> order = new ArrayList<>(roles);
                if (filterMode == 0) {
                    filterMode = 1;
                } else if (filterMode == 1) {
                    filterMode = order.isEmpty() ? 0 : 2;
                    filterRole = order.isEmpty() ? "" : order.get(0);
                } else {
                    int index = order.indexOf(filterRole) + 1;
                    if (index >= order.size()) {
                        filterMode = 0;
                        filterRole = "";
                    } else {
                        filterRole = order.get(index);
                    }
                }
                changed.run();
            }
        });
        area.addGuiElement(new Label(194, y + 2, tr("guistrings.census.count", shown.size(), all.size())).setColor(0xFFAAAAAA));
        y += 18;
        area.addGuiElement(new Header(ROW_X, y));
        y += 11;
        if (shown.isEmpty()) {
            area.addGuiElement(new Label(6, y + 2, tr("guistrings.census.empty")).setColor(0xFFAAAAAA));
            y += 14;
        }
        for (Census.Entry entry : shown) {
            area.addGuiElement(new Row(ROW_X, y, entry, hall));
            y += ROW_HEIGHT;
        }
        return y + 4;
    }

    private static Comparator<Census.Entry> comparator(Sort sort) {
        Comparator<Census.Entry> byName = Comparator.comparing((Census.Entry e) -> displayName(e).toLowerCase(Locale.ROOT)).thenComparing(Census.Entry::id);
        return switch (sort) {
            case NAME -> byName;
            case ROLE -> Comparator.comparing((Census.Entry e) -> roleName(e.type()).toLowerCase(Locale.ROOT)).thenComparing(byName);
            // the unhappiest first; members that have no morale yet go last
            case MOOD -> Comparator.comparingInt((Census.Entry e) -> e.morale() < 0 ? Integer.MAX_VALUE : e.morale()).thenComparing(byName);
            case PROBLEMS -> Comparator.comparingInt((Census.Entry e) -> -Integer.bitCount(e.flags())).thenComparing(byName);
        };
    }

    // ---- texts ----

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String roleName(String type) {
        if (type.startsWith("worker")) {
            String role = type.length() > 7 ? type.substring(7) : "";
            return role.isEmpty() ? tr("guistrings.settlement.role.none") : Component.translatableWithFallback("guistrings.settlement.role." + role, role).getString();
        }
        return tr("entity.ancientwarfarenpc." + type + ".name");
    }

    private static String displayName(Census.Entry entry) {
        if (!entry.name().isEmpty()) {
            return entry.name();
        }
        if (!entry.surname().isEmpty() && !entry.given().isEmpty()) {
            return NpcNames.full(entry.given(), entry.surname()).getString(); // the full name "Bill Carter" (rework phase 10)
        }
        if (entry.isChild()) {
            return NpcNames.child(entry.given()).getString(); // a child has no title (rework phase 10)
        }
        return NpcNames.display(entry.type(), entry.given(), entry.rank()).getString();
    }

    /** The names of the traits of a mask, joined with commas; empty for none. */
    private static String traitsText(int mask) {
        StringBuilder text = new StringBuilder();
        for (io.github.capttracker.ancientwarfarenpc.trait.Trait trait : io.github.capttracker.ancientwarfarenpc.trait.NpcTraits.fromMask(mask)) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(tr(trait.langKey()));
        }
        return text.toString();
    }

    private static String moodWord(int morale) {
        return tr("guistrings.npc.mood." + NpcMorale.mood(morale));
    }

    private static int moodColor(int morale) {
        if (morale < 0) {
            return 0xFFAAAAAA;
        }
        if (morale < 20) {
            return 0xFFFF5555;
        }
        if (morale < 40) {
            return 0xFFFFAA55;
        }
        if (morale < 60) {
            return 0xFFCCCCCC;
        }
        return 0xFF55FF55;
    }

    private static String taskText(String task) {
        return Component.translatableWithFallback("guistrings.census.task." + task, task).getString();
    }

    /** Horizontal distance from the hall. */
    private static int distance(BlockPos pos, BlockPos hall) {
        double dx = pos.getX() - hall.getX();
        double dz = pos.getZ() - hall.getZ();
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    private static String direction(BlockPos pos, BlockPos hall) {
        return tr("guistrings.census.dir." + CensusHighlight.direction(pos.getX() - hall.getX(), pos.getZ() - hall.getZ()));
    }

    private static String fit(String text, int width) {
        return GuiRender.font().plainSubstrByWidth(text, width);
    }

    // ---- elements ----

    /** The column titles. */
    private static final class Header extends GuiElement {
        Header(int x, int y) {
            super(x, y, ROW_WIDTH, 10);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.pose().pushPose();
            graphics.pose().translate(renderX, renderY, 0.0F);
            graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
            String[] titles = {"name", "role", "mood", "where", "task"};
            int[] columns = {COL_NAME, COL_ROLE, COL_MOOD, COL_WHERE, COL_TASK};
            for (int i = 0; i < titles.length; i++) {
                graphics.drawString(GuiRender.font(), tr("guistrings.census.col." + titles[i]), columns[i], 1, 0xFF9999AA, false);
            }
            graphics.drawString(GuiRender.font(), tr("guistrings.census.col.flags"), (int) ((ROW_WIDTH - ICONS_WIDTH) / TEXT_SCALE), 1, 0xFF9999AA, false);
            graphics.pose().popPose();
            graphics.fill(renderX, renderY + 9, renderX + ROW_WIDTH, renderY + 10, 0x60FFFFFF);
        }
    }

    /** One member. */
    private static final class Row extends GuiElement {
        private final Census.Entry entry;
        private final BlockPos hall;
        private final String name;
        private final String role;
        private final String mood;
        private final String where;
        private final String task;
        private final int moodColor;
        private String tipText = null;
        private Tooltip tip = null;
        private long tipSince = -1;

        Row(int x, int y, Census.Entry entry, BlockPos hall) {
            super(x, y, ROW_WIDTH, ROW_HEIGHT);
            this.entry = entry;
            this.hall = hall;
            this.name = fit(displayName(entry), COL_ROLE - COL_NAME - 3);
            this.role = fit(entry.isChild() ? tr("guistrings.census.child_short", entry.childYears(), entry.childDays()) : roleName(entry.type()), COL_MOOD - COL_ROLE - 3);
            this.mood = entry.morale() < 0 ? "-" : fit(moodWord(entry.morale()) + " " + entry.morale(), COL_WHERE - COL_MOOD - 3);
            this.moodColor = moodColor(entry.morale());
            String place = !entry.loaded() ? tr("guistrings.census.unloaded") : distance(entry.pos(), hall) < 4 ? tr("guistrings.census.here")
                    : tr("guistrings.census.where", distance(entry.pos(), hall), direction(entry.pos(), hall));
            this.where = fit(place, COL_TASK - COL_WHERE - 3);
            this.task = fit(taskText(entry.task()), COL_END - COL_TASK);
            addNewListener(new Listener(Listener.MOUSE_UP) {
                @Override
                public boolean onEvent(GuiElement widget, ActivationEvent evt) {
                    if (evt.mButton == 0 && isMouseOverElement(evt.mx, evt.my)) {
                        GuiRender.playClickSound();
                        CensusHighlight.start(entry.id(), displayName(entry), entry.pos(), entry.loaded(), hall);
                    }
                    return true;
                }
            });
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!visible) {
                return;
            }
            if (isMouseOverElement(mouseX, mouseY)) {
                graphics.fill(renderX, renderY, renderX + width, renderY + height, 0x30FFFFFF);
            }
            graphics.pose().pushPose();
            graphics.pose().translate(renderX, renderY, 0.0F);
            graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1.0F);
            int ty = 3;
            graphics.drawString(GuiRender.font(), name, COL_NAME, ty, 0xFFEEEEEE, false);
            graphics.drawString(GuiRender.font(), role, COL_ROLE, ty, 0xFFBBBBBB, false);
            graphics.drawString(GuiRender.font(), mood, COL_MOOD, ty, moodColor, false);
            graphics.drawString(GuiRender.font(), where, COL_WHERE, ty, entry.loaded() ? 0xFFCCCCCC : 0xFF777777, false);
            graphics.drawString(GuiRender.font(), task, COL_TASK, ty, entry.loaded() ? 0xFFCCCCCC : 0xFF888888, false);
            graphics.pose().popPose();
            int x = ROW_WIDTH - ICONS_WIDTH;
            for (int i = 0; i < Census.FLAGS.length; i++) {
                if ((entry.flags() & Census.FLAGS[i]) != 0) {
                    int ix = renderX + x + i * ICON_SIZE;
                    graphics.fill(ix, renderY + 2, ix + ICON_SIZE - 1, renderY + 2 + ICON_SIZE, 0xC0000000);
                    graphics.drawString(GuiRender.font(), FLAG_LETTERS[i], ix + 1, renderY + 3, FLAG_COLORS[i], false);
                }
            }
        }

        /** The tooltip text for a position inside the row, or null. */
        private String tooltipAt(int mouseX) {
            int rel = mouseX - renderX;
            int iconStart = ROW_WIDTH - ICONS_WIDTH;
            if (rel >= iconStart) {
                int index = (rel - iconStart) / ICON_SIZE;
                if (index >= 0 && index < Census.FLAGS.length && (entry.flags() & Census.FLAGS[index]) != 0) {
                    return tr("guistrings.census.flag." + Census.FLAG_NAMES[index]);
                }
                return null;
            }
            int col = (int) (rel / TEXT_SCALE);
            if (col >= COL_WHERE && col < COL_TASK) {
                BlockPos p = entry.pos();
                String line = tr(entry.loaded() ? "guistrings.census.where.tooltip" : "guistrings.census.where.tooltip_unloaded", p.getX(), p.getY(), p.getZ(),
                        distance(p, hall), direction(p, hall));
                return line + "\n" + tr("guistrings.census.click_hint");
            }
            if (col < COL_ROLE) {
                String traitLine = traitsText(entry.traits());
                return displayName(entry) + (entry.isChild() ? ", " + tr("guistrings.census.child") + ", " + FamilyAge.text(entry.childYears(), entry.childDays()).getString() : " (" + roleName(entry.type()) + ")") + (traitLine.isEmpty() ? "" : "\n" + tr("guistrings.census.traits", traitLine))
                        + "\n" + tr("guistrings.census.click_hint");
            }
            return null;
        }

        @Override
        public void postRender(int mouseX, int mouseY, float partialTick, long tick, ITooltipRenderer rend) {
            if (!isMouseOverElement(mouseX, mouseY)) {
                tipText = null;
                tip = null;
                tipSince = -1;
                return;
            }
            String text = tooltipAt(mouseX);
            if (text == null) {
                tipText = null;
                tip = null;
                return;
            }
            if (!text.equals(tipText)) {
                tipText = text;
                tipSince = tick;
                String[] lines = text.split("\n");
                int w = 0;
                for (String line : lines) {
                    w = Math.max(w, GuiRender.font().width(line));
                }
                tip = new Tooltip(w, lines.length * 10 - 2);
                for (int i = 0; i < lines.length; i++) {
                    tip.addTooltipElement(new Label(0, i * 10, lines[i]));
                }
            }
            if (tip != null && tick - tipSince > 300) {
                rend.handleElementTooltipRender(tip, mouseX, mouseY);
            }
        }
    }
}
