/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiTownHallInventory (reworked for settlements; the death list screen is now a tab).
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Line;
import io.github.capttracker.ancientwarfare.client.gui.elements.Text;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.ancientwarfarenpc.menu.TownHallMenu;
import io.github.capttracker.ancientwarfarenpc.settlement.NpcDeathEntry;
import io.github.capttracker.ancientwarfarenpc.settlement.NpcMorale;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The town hall screen, with tabs: Overview (name, owner, members, food in NPC-days, alerts, alarm), Members (with Dismiss), Storage (with Unlink),
 * Deaths (the resurrection list) and Inventory (the hall's own 27 slots). The tabs are buttons at the top; the content is drawn in a scrolled area,
 * and the slots only show on the inventory tab. Everything the screen changes is a request to the server, which checks it.
 */
public class GuiTownHallInventory extends GuiContainerBase<TownHallMenu> {
    private enum Tab {
        OVERVIEW("guistrings.settlement.tab.overview"),
        CENSUS("guistrings.settlement.tab.census"),
        MEMBERS("guistrings.settlement.tab.members"),
        STORAGE("guistrings.settlement.tab.storage"),
        DEATHS("guistrings.npc.death_list"),
        GUARD("guistrings.settlement.tab.guard"),
        INVENTORY("guistrings.settlement.tab.inventory");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private static final int AREA_Y = 24;

    private Tab current = Tab.OVERVIEW;
    private Tab built = null;
    private final Button[] tabButtons = new Button[Tab.values().length];
    private CompositeScrolled area;
    private final CensusView census = new CensusView();
    private boolean censusAsked = false;
    private boolean areaShown = false;

    // overview elements (made once per visit of the tab)
    private Text nameBox;
    private String lastServerName = null;
    private Label ownerLabel;
    private Label membersLabel;
    private Label foodLabel;
    private final List<Label> jobLabels = new ArrayList<>();
    private Button alarmButton;
    private Label moraleLabel;
    private final List<Label> moraleLines = new ArrayList<>();
    private List<String> builtAlerts = new ArrayList<>();

    public GuiTownHallInventory(TownHallMenu menu) {
        super(menu, 350, 3 * 18 + 4 * 18 + 8 + 8 + 4 + 8 + 16);
        menu.setInventoryVisible(false);
    }

    @Override
    public void initElements() {
        int x = 6;
        for (Tab tab : Tab.values()) {
            Button button = new Button(x, 8, 44, 12, tab.key) {
                @Override
                protected void onPressed() {
                    select(tab);
                }
            };
            tabButtons[tab.ordinal()] = button;
            addGuiElement(button);
            x += 46;
        }
        area = new CompositeScrolled(this, 4, AREA_Y, xSize - 8, ySize - AREA_Y - 4);
        nameBox = new Text(50, 4, 120, "", this);
    }

    private void select(Tab tab) {
        current = tab;
        census.invalidate();
        getContainer().setInventoryVisible(tab == Tab.INVENTORY);
        refreshGui();
    }

    @Override
    public void setupElements() {
        for (Tab tab : Tab.values()) {
            tabButtons[tab.ordinal()].setEnabled(tab != current);
        }
        getContainer().setInventoryVisible(current == Tab.INVENTORY);
        // the server only sends the member snapshot while the Census tab is open
        if ((current == Tab.CENSUS) != censusAsked) {
            censusAsked = current == Tab.CENSUS;
            getContainer().setCensusOpen(censusAsked);
        }
        boolean wantArea = current != Tab.INVENTORY;
        if (wantArea && !areaShown) {
            addGuiElement(area);
            areaShown = true;
        } else if (!wantArea && areaShown) {
            removeGuiElement(area);
            areaShown = false;
        }
        if (!wantArea) {
            built = current;
            return;
        }
        if (current == Tab.OVERVIEW && built == Tab.OVERVIEW && builtAlerts.equals(getContainer().getAlerts())) {
            updateOverview();
            return;
        }
        if (current == Tab.CENSUS && built == Tab.CENSUS && !census.needsBuild(getContainer())) {
            return;
        }
        area.clearElements();
        switch (current) {
            case OVERVIEW -> buildOverview();
            case CENSUS -> area.setAreaSize(census.build(area, getContainer(), this::refreshGui));
            case MEMBERS -> buildMembers();
            case STORAGE -> buildStorage();
            case DEATHS -> buildDeaths();
            case GUARD -> buildGuard();
            default -> {
            }
        }
        built = current;
        area.updateGuiPosition(guiLeft, guiTop);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String fit(String text, int width) {
        return Minecraft.getInstance().font.plainSubstrByWidth(text, width);
    }

    // ---- overview ----

    private void buildOverview() {
        TownHallMenu menu = getContainer();
        area.addGuiElement(new Label(6, 6, tr("guistrings.settlement.name")));
        area.addGuiElement(nameBox);
        area.addGuiElement(new Button(176, 4, 50, 12, "guistrings.settlement.rename") {
            @Override
            protected void onPressed() {
                String text = nameBox.getText().trim();
                if (!text.isEmpty()) {
                    getContainer().rename(text);
                }
            }
        });
        ownerLabel = new Label(6, 24, "");
        membersLabel = new Label(6, 38, "");
        foodLabel = new Label(6, 52, "");
        area.addGuiElement(ownerLabel);
        area.addGuiElement(membersLabel);
        area.addGuiElement(foodLabel);
        alarmButton = new Button(6, 66, 110, 12, "guistrings.settlement.alarm_off") {
            @Override
            protected void onPressed() {
                getContainer().setAlarm(!getContainer().isAlarm());
            }
        };
        area.addGuiElement(alarmButton);
        moraleLabel = new Label(6, 82, "");
        area.addGuiElement(moraleLabel);
        moraleLines.clear();
        int y = 94;
        for (int i = 0; i < MORALE_LINES; i++) {
            Label line = new Label(12, y, "");
            moraleLines.add(line);
            area.addGuiElement(line);
            y += 10;
        }
        y += 6;
        area.addGuiElement(new Label(6, y, tr("guistrings.settlement.jobs")));
        y += 12;
        jobLabels.clear();
        for (int i = 0; i < NpcWorker.ROLES.size(); i++) {
            Label label = new Label(12, y, "");
            jobLabels.add(label);
            area.addGuiElement(label);
            y += 12;
        }
        y += 4;
        area.addGuiElement(new Label(6, y, tr("guistrings.settlement.alerts")));
        y += 12;
        builtAlerts = new ArrayList<>(menu.getAlerts());
        if (builtAlerts.isEmpty()) {
            area.addGuiElement(new Label(12, y, tr("guistrings.settlement.no_alerts")).setColor(0xFFAAAAAA));
            y += 12;
        }
        for (String alert : builtAlerts) {
            String key = "guistrings.settlement.alert." + alert;
            area.addGuiElement(new Label(12, y, "! " + (Component.translatableWithFallback(key, alert).getString())).setColor(0xFFFF8855));
            y += 12;
        }
        area.setAreaSize(y + 4);
        lastServerName = null;
        updateOverview();
    }

    private void updateOverview() {
        TownHallMenu menu = getContainer();
        String serverName = menu.getTownName();
        if (lastServerName == null || !lastServerName.equals(serverName)) {
            lastServerName = serverName;
            if (!nameBox.selected()) {
                nameBox.setText(serverName);
            }
        }
        ownerLabel.setText(tr(menu.isOwnerFaction() ? "guistrings.settlement.owner_faction" : "guistrings.settlement.owner", menu.getOwnerName()));
        long homeless = menu.getMembers().stream().filter(TownHallMenu.MemberView::homeless).count();
        membersLabel.setText(homeless > 0 ? tr("guistrings.settlement.member_count_homeless", menu.getMembers().size(), homeless)
                : tr("guistrings.settlement.member_count", menu.getMembers().size()));
        double days = (double) menu.getFoodTicks() / Math.max(1, SettlementConfig.npcDayFoodTicks);
        foodLabel.setText(tr("guistrings.settlement.food", String.format(Locale.ROOT, "%.1f", days)));
        List<TownHallMenu.RoleJobs> jobs = menu.getRoleJobs();
        for (int i = 0; i < jobLabels.size(); i++) {
            if (i < jobs.size()) {
                TownHallMenu.RoleJobs j = jobs.get(i);
                jobLabels.get(i).setText(tr("guistrings.settlement.job_line", tr("guistrings.settlement.role." + j.role()), j.open(), j.filled(), j.idle()));
            }
        }
        alarmButton.setText(menu.isAlarm() ? "guistrings.settlement.alarm_on" : "guistrings.settlement.alarm_off");
        updateMorale();
    }

    /** How many lines the morale breakdown has: one for each factor of {@code SettlementMorale}. */
    private static final int MORALE_LINES = 9;

    private void updateMorale() {
        TownHallMenu menu = getContainer();
        if (!menu.isMoraleOn()) {
            moraleLabel.setText(tr("guistrings.settlement.morale.off"));
            for (Label line : moraleLines) {
                line.setText("");
            }
            return;
        }
        moraleLabel.setText(tr("guistrings.settlement.morale", menu.getMorale(), String.format(Locale.ROOT, "%.2f", menu.getMoraleFactor())));
        List<TownHallMenu.MoraleLine> lines = menu.getMoraleLines();
        for (int i = 0; i < moraleLines.size(); i++) {
            Label label = moraleLines.get(i);
            if (i < lines.size()) {
                TownHallMenu.MoraleLine line = lines.get(i);
                label.setText(tr("guistrings.settlement.morale_line", line.points(), line.max(), tr("guistrings.settlement.morale." + line.key())));
                label.setColor(line.points() >= line.max() ? 0xFF55FF55 : line.points() > 0 ? 0xFFFFFF55 : 0xFFFF8855);
            } else {
                label.setText("");
            }
        }
    }

    // ---- members ----

    private void buildMembers() {
        int y = 6;
        List<TownHallMenu.MemberView> members = getContainer().getMembers();
        if (members.isEmpty()) {
            area.addGuiElement(new Label(6, y, tr("guistrings.settlement.no_members")).setColor(0xFFAAAAAA));
            y += 14;
        }
        for (TownHallMenu.MemberView member : members) {
            String type = tr("entity.ancientwarfarenpc." + member.type() + ".name");
            String title = member.name().isEmpty() ? io.github.capttracker.ancientwarfarenpc.entity.NpcNames.display(member.type(), member.given()).getString() : member.name() + " (" + type + ")";
            area.addGuiElement(new Label(6, y + 2, fit(title, 74)));
            if (member.type().startsWith("worker")) {
                String role = member.type().length() > 7 ? member.type().substring(7) : "";
                String roleText = role.isEmpty() ? tr("guistrings.settlement.role.none") : tr("guistrings.settlement.role." + role);
                area.addGuiElement(new Button(82, y, 50, 12, fit(roleText, 44)) {
                    @Override
                    protected void onPressed() {
                        getContainer().cycleRole(member.id());
                    }
                });
            }
            area.addGuiElement(new Label(136, y + 2, tr("guistrings.settlement.status." + member.status().name().toLowerCase(Locale.ROOT)))
                    .setColor(statusColor(member.status())));
            area.addGuiElement(new Label(176, y + 2, tr("guistrings.settlement.distance", member.distance())));
            if (member.morale() >= 0) {
                String mood = NpcMorale.mood(member.morale());
                area.addGuiElement(new Label(208, y + 2, tr("guistrings.settlement.member_morale", tr("guistrings.npc.mood." + mood), member.morale()))
                        .setColor(moraleColor(member.morale())));
            }
            area.addGuiElement(new Button(302, y, 40, 12, "guistrings.settlement.dismiss") {
                @Override
                protected void onPressed() {
                    getContainer().dismiss(member.id());
                }
            });
            y += 16;
            if (member.homeless()) {
                area.addGuiElement(new Label(12, y - 3, tr("guistrings.settlement.homeless")).setColor(0xFFFFAA55));
                y += 8;
            }
        }
        area.setAreaSize(y + 4);
    }

    private static int moraleColor(int morale) {
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

    private static int statusColor(io.github.capttracker.ancientwarfarenpc.settlement.MemberStatus status) {
        return switch (status) {
            case WORKING -> 0xFF55FF55;
            case HUNGRY -> 0xFFFFFF55;
            case STARVING -> 0xFFFF5555;
            case SLEEPING -> 0xFF8899FF;
            case IDLE -> 0xFFCCCCCC;
        };
    }

    // ---- guard ----

    private void buildGuard() {
        TownHallMenu menu = getContainer();
        TownHallMenu.GuardView g = menu.getGuard();
        int y = 6;
        String captain = g.captain().isEmpty() ? tr("guistrings.settlement.guard.no_captain") : tr("guistrings.settlement.guard.captain", g.captain());
        area.addGuiElement(new Label(6, y + 2, captain).setColor(g.captain().isEmpty() ? 0xFFFFAA55 : 0xFF55FF55));
        y += 18;
        area.addGuiElement(new Button(6, y, 120, 12, g.patrol() ? "guistrings.settlement.guard.patrol_on" : "guistrings.settlement.guard.patrol_off") {
            @Override
            protected void onPressed() {
                getContainer().setPatrol(!getContainer().getGuard().patrol());
            }
        });
        y += 18;
        area.addGuiElement(new Label(6, y + 2, tr("guistrings.settlement.guard.night_watch", g.nightWatchPercent())));
        area.addGuiElement(new Button(190, y, 20, 12, "-") {
            @Override
            protected void onPressed() {
                getContainer().setNightWatch(Math.max(0, getContainer().getGuard().nightWatchPercent() - 10));
            }
        });
        area.addGuiElement(new Button(212, y, 20, 12, "+") {
            @Override
            protected void onPressed() {
                getContainer().setNightWatch(Math.min(100, getContainer().getGuard().nightWatchPercent() + 10));
            }
        });
        y += 18;
        area.addGuiElement(new Button(6, y, 120, 12, g.autoAlarm() ? "guistrings.settlement.guard.auto_on" : "guistrings.settlement.guard.auto_off") {
            @Override
            protected void onPressed() {
                getContainer().setAutoAlarm(!getContainer().getGuard().autoAlarm());
            }
        });
        y += 18;
        area.addGuiElement(new Label(6, y + 2, tr("guistrings.settlement.guard.threshold", g.threshold())));
        area.addGuiElement(new Button(190, y, 20, 12, "-") {
            @Override
            protected void onPressed() {
                getContainer().setAlarmThreshold(Math.max(1, getContainer().getGuard().threshold() - 1));
            }
        });
        area.addGuiElement(new Button(212, y, 20, 12, "+") {
            @Override
            protected void onPressed() {
                getContainer().setAlarmThreshold(getContainer().getGuard().threshold() + 1);
            }
        });
        y += 18;
        area.addGuiElement(new Label(6, y + 2, tr("guistrings.settlement.guard.clear_time", g.clearSeconds())));
        area.addGuiElement(new Button(190, y, 20, 12, "-") {
            @Override
            protected void onPressed() {
                getContainer().setAlarmClearSeconds(Math.max(5, getContainer().getGuard().clearSeconds() - 10));
            }
        });
        area.addGuiElement(new Button(212, y, 20, 12, "+") {
            @Override
            protected void onPressed() {
                getContainer().setAlarmClearSeconds(getContainer().getGuard().clearSeconds() + 10);
            }
        });
        y += 18;
        String route = g.waypoints() >= 2 ? tr("guistrings.settlement.guard.route_waypoints", g.waypoints()) : g.waypoints() == 1
                ? tr("guistrings.settlement.guard.route_one") : tr("guistrings.settlement.guard.route_border");
        area.addGuiElement(new Label(6, y + 2, route));
        area.addGuiElement(new Button(190, y, 42, 12, "guistrings.settlement.guard.clear_route") {
            @Override
            protected void onPressed() {
                getContainer().clearRoute();
            }
        });
        y += 18;
        area.addGuiElement(new Label(6, y + 2, tr(g.overridden() ? "guistrings.settlement.guard.overridden" : "guistrings.settlement.guard.defaults"))
                .setColor(0xFFAAAAAA));
        area.addGuiElement(new Button(190, y, 42, 12, "guistrings.settlement.guard.reset") {
            @Override
            protected void onPressed() {
                getContainer().resetGuard();
            }
        });
        y += 18;
        area.addGuiElement(new Label(6, y, tr("guistrings.settlement.guard.hint")).setColor(0xFFAAAAAA));
        y += 12;
        area.setAreaSize(y + 4);
    }

    // ---- storage ----

    private void buildStorage() {
        int y = 6;
        area.addGuiElement(new Label(6, y + 2, tr("guistrings.settlement.hall_storage")));
        y += 16;
        List<TownHallMenu.StorageView> storages = getContainer().getStorages();
        if (storages.isEmpty()) {
            area.addGuiElement(new Label(6, y, tr("guistrings.settlement.no_storage")).setColor(0xFFAAAAAA));
            y += 14;
        }
        for (TownHallMenu.StorageView storage : storages) {
            String block = storage.blockKey().isEmpty() ? tr("guistrings.settlement.unloaded") : tr(storage.blockKey());
            String where = block + " " + storage.pos().getX() + ", " + storage.pos().getY() + ", " + storage.pos().getZ();
            area.addGuiElement(new Label(6, y + 2, fit(where, 140)));
            double days = (double) storage.foodTicks() / Math.max(1, SettlementConfig.npcDayFoodTicks);
            area.addGuiElement(new Label(150, y + 2, tr("guistrings.settlement.food_short", String.format(Locale.ROOT, "%.1f", days))).setColor(0xFFCCCCCC));
            area.addGuiElement(new Button(190, y, 42, 12, "guistrings.settlement.unlink") {
                @Override
                protected void onPressed() {
                    getContainer().unlink(storage.pos());
                }
            });
            y += 16;
        }
        area.setAreaSize(y + 4);
    }

    // ---- deaths ----

    private void buildDeaths() {
        int y = 6;
        area.addGuiElement(new Button(6, y, 55, 12, "guistrings.npc.clear_death_list") {
            @Override
            protected void onPressed() {
                getContainer().clearList();
            }
        });
        y += 18;
        for (NpcDeathEntry entry : getContainer().getDeathList()) {
            area.addGuiElement(new Label(6, y, tr("guistrings.npc.npc_name") + ": " + entry.npcName));
            y += 12;
            area.addGuiElement(new Label(6, y, tr("guistrings.npc.npc_type") + ": " + tr("entity.ancientwarfarenpc." + entry.npcType + ".name")));
            y += 12;
            area.addGuiElement(new Label(6, y, tr("guistrings.npc.death_cause", entry.deathCause)));
            y += 12;
            if (entry.pos != null && entry.pos.length == 3) {
                area.addGuiElement(new Label(6, y, tr("guistrings.npc.death_position", entry.pos[0], entry.pos[1], entry.pos[2])));
                y += 12;
            }
            area.addGuiElement(new Label(6, y, tr("guistrings.npc.can_res", entry.canRes)));
            y += 12;
            if (entry.canRes) {
                area.addGuiElement(new Label(6, y, tr("guistrings.npc.resurrected", entry.resurrected)));
                y += 12;
            }
            area.addGuiElement(new Line(0, y - 1, xSize - 8, y - 1, 1, 0x000000ff));
            y += 4;
        }
        area.setAreaSize(y);
    }

    @Override
    protected boolean onGuiCloseRequested() {
        getContainer().setInventoryVisible(true);
        return super.onGuiCloseRequested();
    }
}
