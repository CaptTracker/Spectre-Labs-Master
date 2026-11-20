package io.github.capttracker.spectrefactions.client;

import io.github.capttracker.spectrefactions.api.FactionType;
import io.github.capttracker.spectrefactions.api.GuardMode;
import io.github.capttracker.spectrefactions.api.Rank;
import io.github.capttracker.spectrefactions.api.Relation;
import io.github.capttracker.spectrefactions.api.Treaty;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.FactionInfo;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.InviteInfo;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.MemberInfo;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.ProposalInfo;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.RelationInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** The faction screen: My faction, Factions and Proposals tabs. Rebuilt whenever a new snapshot arrives. */
public class FactionsScreen extends Screen {
    private static final int PANEL_W = 360;
    private static final int PANEL_H = 240;
    private static final int ROW_H = 22;
    private static final List<ChatFormatting> COLORS = new ArrayList<>();
    static {
        for (ChatFormatting c : ChatFormatting.values()) {
            if (c.isColor()) {
                COLORS.add(c);
            }
        }
    }

    private static int tab = 0;

    private final ClientFactionsView view = ClientFactionsView.INSTANCE;
    private long seenRevision = -1;
    private int scroll = 0;
    private int left, top, listTop, listBottom, panelBottom;
    private int maxRows = 1;
    private int totalRows;
    private int confirm = 0; // 1 = leave armed, 2 = disband armed

    // form state kept across rebuilds
    private String nameText = "";
    private String tagText = "";
    private String inviteText = "";
    private ChatFormatting colorChoice = ChatFormatting.GOLD;

    private final List<Row> rows = new ArrayList<>();

    private record Btn(Component label, int width, Runnable action, boolean active) {}

    private record Row(Component text, List<Btn> buttons) {}

    public FactionsScreen() {
        super(Component.translatable("gui.spectrefactions.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        seenRevision = view.revision();
        left = (width - PANEL_W) / 2;
        int panelH = Math.min(height - 12, PANEL_H);
        top = Math.max(6, (height - panelH) / 2);
        panelBottom = top + panelH;

        String[] tabs = {"my_faction", "factions", "proposals"};
        int tw = PANEL_W / 3;
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Button b = Button.builder(Component.translatable("gui.spectrefactions.tab." + tabs[i]), x -> {
                tab = idx;
                scroll = 0;
                confirm = 0;
                rebuildWidgets();
            }).bounds(left + i * tw, top, tw - 2, 20).build();
            b.active = tab != i;
            addRenderableWidget(b);
        }

        int y = top + 26;
        rows.clear();
        switch (tab) {
            case 0 -> y = buildMyFaction(y);
            case 1 -> buildFactions();
            default -> buildProposals();
        }
        listTop = y;
        listBottom = panelBottom - 14;
        maxRows = Math.max(1, (listBottom - listTop) / ROW_H);
        totalRows = rows.size();
        scroll = Math.max(0, Math.min(scroll, totalRows - maxRows));

        for (int i = 0; i < maxRows && scroll + i < rows.size(); i++) {
            Row r = rows.get(scroll + i);
            int x = left + PANEL_W;
            int ry = listTop + i * ROW_H;
            for (int k = r.buttons().size() - 1; k >= 0; k--) {
                Btn b = r.buttons().get(k);
                x -= b.width();
                Button w = Button.builder(b.label(), btn -> b.action().run()).bounds(x, ry, b.width(), 20).build();
                w.active = b.active();
                addRenderableWidget(w);
                x -= 3;
            }
        }
    }

    // ---- tabs ----

    private int buildMyFaction(int y) {
        Optional<FactionInfo> mine = view.myFaction();
        if (mine.isEmpty()) {
            return buildCreateForm(y);
        }
        FactionInfo f = mine.get();
        Rank rank = view.myRank().orElse(Rank.MEMBER);
        boolean leader = rank == Rank.LEADER;

        y += 12; // header line is drawn in render()
        if (leader) {
            EditBox name = box(left, y, 100, "gui.spectrefactions.field.name", f.name(), 24);
            EditBox tag = box(left + 156, y, 40, "gui.spectrefactions.field.tag", f.tag(), 5);
            addRenderableWidget(name);
            addRenderableWidget(tag);
            addRenderableWidget(Button.builder(Component.translatable("gui.spectrefactions.rename"), b -> {
                if (!name.getValue().isBlank()) {
                    FactionsClient.sendAction("set_name", name.getValue().trim());
                }
            }).bounds(left + 102, y, 50, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.spectrefactions.set"), b -> {
                if (!tag.getValue().isBlank()) {
                    FactionsClient.sendAction("set_tag", tag.getValue().trim());
                }
            }).bounds(left + 198, y, 34, 20).build());
            colorChoice = f.color();
            addRenderableWidget(colorButton(left + 236, y, 124, b -> {
                cycleColor();
                FactionsClient.sendAction("set_color", colorChoice.getName());
            }));
            y += 24;
        }
        if (view.canInvite()) {
            EditBox inv = box(left, y, 150, "gui.spectrefactions.field.invite", inviteText, 16);
            inv.setResponder(s -> inviteText = s);
            addRenderableWidget(inv);
            addRenderableWidget(Button.builder(Component.translatable("gui.spectrefactions.invite"), b -> {
                if (!inviteText.isBlank()) {
                    FactionsClient.sendAction("invite", inviteText.trim());
                    inviteText = "";
                    inv.setValue("");
                }
            }).bounds(left + 154, y, 50, 20).build());
        }
        if (leader) {
            addRenderableWidget(Button.builder(Component.translatable(f.open() ? "gui.spectrefactions.open_yes" : "gui.spectrefactions.open_no"),
                    b -> FactionsClient.sendAction("set_open", String.valueOf(!f.open()))).bounds(left + 208, y, 72, 20).build());
        }
        addRenderableWidget(guardButton(leader ? left + 284 : left + 208, y, leader ? 76 : 120, leader));
        y += 24;
        if (leader) {
            addRenderableWidget(Button.builder(Component.translatable(confirm == 2 ? "gui.spectrefactions.confirm" : "gui.spectrefactions.disband"), b -> {
                if (confirm == 2) {
                    confirm = 0;
                    FactionsClient.sendAction("disband");
                } else {
                    confirm = 2;
                    rebuildWidgets();
                }
            }).bounds(left + PANEL_W - 142, y, 68, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable(confirm == 1 ? "gui.spectrefactions.confirm" : "gui.spectrefactions.leave"), b -> {
            if (confirm == 1) {
                confirm = 0;
                FactionsClient.sendAction("leave");
            } else {
                confirm = 1;
                rebuildWidgets();
            }
        }).bounds(left + PANEL_W - 70, y, 70, 20).build());
        y += 26;

        UUID me = view.localPlayerId().orElse(null);
        for (MemberInfo m : f.members()) {
            List<Btn> btns = new ArrayList<>();
            boolean self = m.id().equals(me);
            if (leader && !self && m.rank() != Rank.LEADER) {
                btns.add(new Btn(Component.translatable("gui.spectrefactions.lead"), 36, () -> FactionsClient.sendAction("transfer", m.name()), true));
            }
            if (leader && !self && m.rank() == Rank.MEMBER) {
                btns.add(new Btn(Component.translatable("gui.spectrefactions.promote"), 52, () -> FactionsClient.sendAction("promote", m.name()), true));
            }
            if (leader && !self && m.rank() == Rank.OFFICER) {
                btns.add(new Btn(Component.translatable("gui.spectrefactions.demote"), 52, () -> FactionsClient.sendAction("demote", m.name()), true));
            }
            if (!self && view.canKick(m)) {
                btns.add(new Btn(Component.translatable("gui.spectrefactions.kick"), 36, () -> FactionsClient.sendAction("kick", m.name()), true));
            }
            MutableComponent t = Component.literal(m.name()).withStyle(self ? ChatFormatting.WHITE : ChatFormatting.GRAY)
                    .append(Component.literal("  ").append(Component.translatable("rank.spectrefactions." + m.rank().name().toLowerCase(Locale.ROOT)))
                            .withStyle(ChatFormatting.DARK_GRAY));
            rows.add(new Row(t, btns));
        }
        return y;
    }

    private int buildCreateForm(int y) {
        y += 12;
        EditBox name = box(left, y, 130, "gui.spectrefactions.field.name", nameText, 24);
        name.setResponder(s -> nameText = s);
        EditBox tag = box(left + 134, y, 44, "gui.spectrefactions.field.tag", tagText, 5);
        tag.setResponder(s -> tagText = s);
        addRenderableWidget(name);
        addRenderableWidget(tag);
        addRenderableWidget(colorButton(left + 182, y, 100, b -> {
            cycleColor();
            rebuildWidgets();
        }));
        addRenderableWidget(Button.builder(Component.translatable("gui.spectrefactions.create"), b -> {
            if (!nameText.isBlank() && !tagText.isBlank()) {
                FactionsClient.sendAction("create", nameText.trim(), tagText.trim(), colorChoice.getName());
                nameText = "";
                tagText = "";
            }
        }).bounds(left + 286, y, 74, 20).build());
        y += 24;
        addRenderableWidget(guardButton(left, y, 150, true));
        y += 28;
        for (InviteInfo inv : view.invites()) {
            String fname = view.faction(inv.factionId()).map(FactionInfo::name).orElse("?");
            rows.add(new Row(Component.translatable("gui.spectrefactions.invited_by", fname, inv.ticksLeft() / 20),
                    List.of(new Btn(Component.translatable("gui.spectrefactions.join"), 44,
                            () -> FactionsClient.sendAction("join", inv.factionId().toString()), true))));
        }
        return y;
    }

    private void buildFactions() {
        FactionInfo mine = view.myFaction().orElse(null);
        for (FactionInfo f : view.factions()) {
            RelationInfo rel = view.relationWithFaction(f.id()).orElse(null);
            boolean isMine = mine != null && mine.id().equals(f.id());
            Relation relation = rel == null ? Relation.NEUTRAL : rel.relation();
            int rep = rel == null ? 0 : rel.reputation();
            MutableComponent t = Component.literal("[" + f.tag() + "] ").withStyle(f.color())
                    .append(Component.literal(f.name()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" (" + f.members().size() + ") ").withStyle(ChatFormatting.DARK_GRAY));
            if (isMine) {
                t.append(Component.translatable("gui.spectrefactions.yours").withStyle(ChatFormatting.YELLOW));
            } else {
                t.append(Component.translatable(relation.translationKey()).withStyle(relation.color()))
                        .append(Component.literal(" " + rep).withStyle(relation.color()));
                if (rel != null && rel.war()) {
                    t.append(Component.literal(" ").append(Component.translatable("gui.spectrefactions.at_war").withStyle(ChatFormatting.RED)));
                } else if (rel != null && rel.treaty() != Treaty.NONE) {
                    t.append(Component.literal(" ").append(Component.translatable("treaty.spectrefactions." + rel.treaty().name().toLowerCase(Locale.ROOT))
                            .withStyle(ChatFormatting.DARK_AQUA)));
                }
            }
            List<Btn> btns = new ArrayList<>();
            String id = f.id().toString();
            if (!isMine && view.canJoin(f)) {
                btns.add(new Btn(Component.translatable("gui.spectrefactions.join"), 34, () -> FactionsClient.sendAction("join", id), true));
            }
            if (!isMine && mine != null && f.type() == FactionType.PLAYER && view.canDiplomacy()) {
                boolean war = rel != null && rel.war();
                Treaty tr = rel == null ? Treaty.NONE : rel.treaty();
                if (!war) {
                    btns.add(new Btn(Component.translatable("gui.spectrefactions.war"), 30, () -> FactionsClient.sendAction("war", id), true));
                }
                if (war || tr == Treaty.NONE) {
                    btns.add(new Btn(Component.translatable("gui.spectrefactions.peace"), 38, () -> FactionsClient.sendAction("peace", id), true));
                }
                if (!war && tr != Treaty.ALLIANCE) {
                    btns.add(new Btn(Component.translatable("gui.spectrefactions.ally"), 30, () -> FactionsClient.sendAction("ally", id), true));
                }
                if (tr != Treaty.NONE) {
                    btns.add(new Btn(Component.translatable("gui.spectrefactions.break_treaty"), 44, () -> FactionsClient.sendAction("breaktreaty", id), true));
                }
            }
            rows.add(new Row(t, btns));
        }
    }

    private void buildProposals() {
        boolean dip = view.canDiplomacy();
        for (ProposalInfo p : view.incomingProposals()) {
            String from = view.faction(p.from()).map(FactionInfo::name).orElse("?");
            String id = p.from().toString();
            rows.add(new Row(Component.translatable("gui.spectrefactions.incoming", from,
                    Component.translatable("proposal.spectrefactions." + p.type().name().toLowerCase(Locale.ROOT))),
                    List.of(new Btn(Component.translatable("gui.spectrefactions.accept"), 46, () -> FactionsClient.sendAction("accept", id), dip),
                            new Btn(Component.translatable("gui.spectrefactions.deny"), 36, () -> FactionsClient.sendAction("deny", id), dip))));
        }
        for (ProposalInfo p : view.outgoingProposals()) {
            String to = view.faction(p.to()).map(FactionInfo::name).orElse("?");
            String id = p.to().toString();
            rows.add(new Row(Component.translatable("gui.spectrefactions.outgoing", to,
                    Component.translatable("proposal.spectrefactions." + p.type().name().toLowerCase(Locale.ROOT))),
                    List.of(new Btn(Component.translatable("gui.spectrefactions.cancel"), 46, () -> FactionsClient.sendAction("cancel", id), dip))));
        }
    }

    // ---- widgets ----

    private EditBox box(int x, int y, int w, String hintKey, String value, int max) {
        EditBox b = new EditBox(font, x, y, w, 20, Component.translatable(hintKey));
        b.setMaxLength(max);
        b.setHint(Component.translatable(hintKey).withStyle(ChatFormatting.DARK_GRAY));
        b.setValue(value);
        return b;
    }

    private void cycleColor() {
        int i = COLORS.indexOf(colorChoice);
        colorChoice = COLORS.get((i + 1) % COLORS.size());
    }

    private Button colorButton(int x, int y, int w, Button.OnPress press) {
        Component label = Component.translatable("gui.spectrefactions.colour").append(": ")
                .append(Component.literal(colorChoice.getName()).withStyle(colorChoice));
        return Button.builder(label, press).bounds(x, y, w, 20).build();
    }

    private Button guardButton(int x, int y, int w, boolean enabled) {
        GuardMode mode = view.myGuardMode();
        GuardMode next = GuardMode.values()[(mode.ordinal() + 1) % GuardMode.values().length];
        Button b = Button.builder(Component.translatable("gui.spectrefactions.guard_mode", Component.translatable(mode.translationKey())),
                x0 -> FactionsClient.sendAction("guard", next.name().toLowerCase(Locale.ROOT))).bounds(x, y, w, 20).build();
        b.active = enabled;
        return b;
    }

    // ---- frame ----

    @Override
    public void tick() {
        if (view.revision() != seenRevision) {
            confirm = 0;
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (totalRows > maxRows) {
            int ns = Math.max(0, Math.min(totalRows - maxRows, scroll - (int) Math.signum(sy)));
            if (ns != scroll) {
                scroll = ns;
                rebuildWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        g.fill(left - 4, top - 4, left + PANEL_W + 4, panelBottom + 4, 0xB0000000);
        if (tab == 0) {
            Optional<FactionInfo> mine = view.myFaction();
            if (mine.isPresent()) {
                FactionInfo f = mine.get();
                Component head = Component.literal("[" + f.tag() + "] ").withStyle(f.color())
                        .append(Component.literal(f.name()).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal("  " + f.members().size() + " ").withStyle(ChatFormatting.GRAY))
                        .append(Component.translatable("gui.spectrefactions.members").withStyle(ChatFormatting.GRAY));
                g.drawString(font, head, left, top + 26, 0xFFFFFF);
            } else {
                g.drawString(font, Component.translatable("gui.spectrefactions.no_faction"), left, top + 26, 0xAAAAAA);
            }
        }
        for (int i = 0; i < maxRows && scroll + i < rows.size(); i++) {
            Row r = rows.get(scroll + i);
            int btnW = 0;
            for (Btn b : r.buttons()) {
                btnW += b.width() + 3;
            }
            var clipped = Language.getInstance().getVisualOrder(font.substrByWidth(r.text(), PANEL_W - btnW - 6));
            g.drawString(font, clipped, left, listTop + i * ROW_H + 6, 0xFFFFFF);
        }
        if (rows.isEmpty()) {
            String key = tab == 0 ? (view.myFaction().isPresent() ? "" : "gui.spectrefactions.no_invites")
                    : tab == 1 ? "gui.spectrefactions.no_factions" : "gui.spectrefactions.no_proposals";
            if (!key.isEmpty()) {
                g.drawString(font, Component.translatable(key), left, listTop + 4, 0x888888);
            }
        }
        if (totalRows > maxRows) {
            g.drawString(font, Component.translatable("gui.spectrefactions.scroll", scroll + 1, Math.min(totalRows, scroll + maxRows), totalRows),
                    left, listBottom + 4, 0x888888);
        }
        super.render(g, mx, my, pt);
    }
}
