/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (playtest fixes): the talk screen of an NPC.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.ProgressBar;
import io.github.capttracker.ancientwarfarenpc.menu.NpcMenuPayloads;
import io.github.capttracker.ancientwarfarenpc.menu.NpcTalkMenu;
import io.github.capttracker.ancientwarfarenpc.settlement.NpcMorale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.List;
import java.util.Locale;

/**
 * Talk to an NPC. Tab Talk: the name, a greeting, what the NPC thinks right now (3 to 6 lines) and what happened to it lately. Tab Morale (the commanders
 * of a player-owned NPC): the morale, the mood word and the factors with their points. A button leads on to the inventory screen of the NPC for its
 * commanders. The server sends the content every second; the screen only reads it ({@link NpcTalkMenu}).
 */
public class GuiNpcTalk extends GuiNpcBase<NpcTalkMenu> {
    private static final int AREA_Y = 24;
    private static final int TEXT_X = 6;

    private Button talkTab;
    private Button moraleTab;
    private Button skillsTab;
    private Button inventoryTab;
    private CompositeScrolled area;

    public GuiNpcTalk(NpcTalkMenu menu) {
        super(menu, 250, 200);
    }

    @Override
    public void initElements() {
        talkTab = new Button(6, 8, 50, 12, "guistrings.npc.talk.tab.talk") {
            @Override
            protected void onPressed() {
                getContainer().setTab(NpcTalkMenu.TAB_TALK);
            }
        };
        moraleTab = new Button(58, 8, 50, 12, "guistrings.npc.talk.tab.morale") {
            @Override
            protected void onPressed() {
                getContainer().setTab(NpcTalkMenu.TAB_MORALE);
            }
        };
        skillsTab = new Button(110, 8, 50, 12, "guistrings.npc.talk.tab.skills") {
            @Override
            protected void onPressed() {
                getContainer().setTab(NpcTalkMenu.TAB_SKILLS);
            }
        };
        inventoryTab = new Button(162, 8, 58, 12, "guistrings.npc.talk.tab.inventory") {
            @Override
            protected void onPressed() {
                requestOpen(NpcMenuPayloads.Request.INVENTORY);
            }
        };
        area = new CompositeScrolled(this, 4, AREA_Y, xSize - 8, ySize - AREA_Y - 4);
    }

    @Override
    public void setupElements() {
        NpcTalkMenu menu = getContainer();
        clearElements();
        addGuiElement(talkTab);
        if (menu.hasMorale) {
            addGuiElement(moraleTab);
        }
        if (menu.hasSkills) {
            addGuiElement(skillsTab);
        }
        if (menu.canEdit) {
            addGuiElement(inventoryTab);
        }
        talkTab.setEnabled(menu.tab != NpcTalkMenu.TAB_TALK);
        moraleTab.setEnabled(menu.tab != NpcTalkMenu.TAB_MORALE);
        skillsTab.setEnabled(menu.tab != NpcTalkMenu.TAB_SKILLS);
        addGuiElement(area);
        area.clearElements();
        int y = menu.tab == NpcTalkMenu.TAB_MORALE && menu.hasMorale ? buildMorale(menu)
                : menu.tab == NpcTalkMenu.TAB_SKILLS && menu.hasSkills ? buildSkills(menu) : buildTalk(menu);
        area.setAreaSize(y + 4);
        area.updateGuiPosition(guiLeft, guiTop);
    }

    /** Adds the text as lines that fit the area; returns the y below the last one. */
    private int addWrapped(int y, String text, int color, int indent) {
        int width = xSize - 8 - TEXT_X * 2 - 8 - indent;
        List<FormattedText> parts = Minecraft.getInstance().font.getSplitter().splitLines(text, width, Style.EMPTY);
        for (FormattedText part : parts) {
            area.addGuiElement(new Label(TEXT_X + indent, y, part.getString()).setColor(color));
            y += 10;
        }
        return y;
    }

    private int buildTalk(NpcTalkMenu menu) {
        int y = 6;
        y = addWrapped(y, menu.npcName.getString(), 0xFFFFD27F, 0);
        y += 2;
        if (menu.npc != null && io.github.capttracker.ancientwarfarenpc.client.MapShare.enabled()) {
            area.addGuiElement(new Button(TEXT_X, y, 100, 12, "guistrings.share.button") {
                @Override
                protected void onPressed() {
                    io.github.capttracker.ancientwarfarenpc.client.MapShare.share(menu.npcName.getString(), menu.npc.blockPosition());
                }
            });
            y += 16;
        }
        if (menu.greeting != null) {
            y = addWrapped(y, "\"" + menu.greeting.component().getString() + "\"", 0xFFDDDDDD, 0);
        }
        if (!menu.traits.isEmpty()) {
            y += 6;
            area.addGuiElement(new Label(TEXT_X, y, Component.translatable("guistrings.npc.talk.traits").getString()).setColor(0xFF9FD0FF));
            y += 12;
            for (String id : menu.traits) {
                io.github.capttracker.ancientwarfarenpc.trait.Trait trait = io.github.capttracker.ancientwarfarenpc.trait.Trait.byId(id);
                if (trait != null) {
                    y = addWrapped(y, Component.translatable("guistrings.npc.talk.trait_line", Component.translatable(trait.langKey()),
                            Component.translatable(trait.descriptionKey())).getString(), 0xFFFFE9B0, 0);
                    y += 2;
                }
            }
        }
        if (!menu.family.isEmpty()) {
            y += 6;
            area.addGuiElement(new Label(TEXT_X, y, Component.translatable("guistrings.npc.talk.family").getString()).setColor(0xFF9FD0FF));
            y += 12;
            for (var line : menu.family) {
                y = addWrapped(y, "- " + line.component().getString(), 0xFFFFC9D9, 0);
                y += 2;
            }
        }
        y += 6;
        area.addGuiElement(new Label(TEXT_X, y, Component.translatable("guistrings.npc.talk.thinking").getString()).setColor(0xFF9FD0FF));
        y += 12;
        for (var line : menu.lines) {
            y = addWrapped(y, "- " + line.component().getString(), 0xFFFFFFFF, 0);
            y += 2;
        }
        y += 6;
        area.addGuiElement(new Label(TEXT_X, y, Component.translatable("guistrings.npc.talk.lately").getString()).setColor(0xFF9FD0FF));
        y += 12;
        if (menu.events.isEmpty()) {
            y = addWrapped(y, Component.translatable("guistrings.npc.talk.nothing").getString(), 0xFFAAAAAA, 0);
        }
        for (NpcTalkMenu.Event event : menu.events) {
            String age = event.minutes() <= 0 ? Component.translatable("guistrings.npc.talk.just_now").getString()
                    : Component.translatable("guistrings.npc.talk.ago", event.minutes()).getString();
            y = addWrapped(y, "- " + event.line().component().getString() + " (" + age + ")", 0xFFBBBBBB, 0);
            y += 2;
        }
        return y;
    }

    private int buildMorale(NpcTalkMenu menu) {
        int y = 6;
        if (menu.moraleOff) {
            return addWrapped(y, Component.translatable("guistrings.settlement.morale.off").getString(), 0xFFAAAAAA, 0);
        }
        String mood = Component.translatable("guistrings.npc.mood." + NpcMorale.mood(menu.morale)).getString();
        area.addGuiElement(new Label(TEXT_X, y, Component.translatable("guistrings.npc.morale.value", menu.morale, mood).getString())
                .setColor(moodColor(menu.morale)));
        y += 12;
        area.addGuiElement(new Label(TEXT_X, y, Component.translatable("guistrings.npc.morale.speed", String.format(Locale.ROOT, "%.2f", menu.moraleFactor)).getString())
                .setColor(0xFFCCCCCC));
        y += 12;
        String base = Component.translatable(menu.moraleHasSettlement ? "guistrings.npc.morale.base_settlement" : "guistrings.npc.morale.base_none", menu.moraleBase).getString();
        y = addWrapped(y, base, 0xFFCCCCCC, 0);
        y += 6;
        for (NpcTalkMenu.Factor factor : menu.factors) {
            String sign = factor.delta() > 0 ? "+" + factor.delta() : Integer.toString(factor.delta());
            int color = factor.delta() > 0 ? 0xFF55FF55 : factor.delta() < 0 ? 0xFFFF7777 : 0xFF999999;
            area.addGuiElement(new Label(TEXT_X, y, sign).setColor(color));
            area.addGuiElement(new Label(TEXT_X + 28, y, Component.translatable("guistrings.npc.morale." + factor.key()).getString()).setColor(color));
            y += 11;
        }
        return y;
    }

    /** Every skill with its rank, a progress bar to the next rank and the experience; the main skill (the title of the NPC) first in gold. */
    private int buildSkills(NpcTalkMenu menu) {
        int y = 6;
        area.addGuiElement(new Label(TEXT_X, y, Component.translatable("guistrings.npc.skills.title").getString()).setColor(0xFF9FD0FF));
        y += 13;
        for (NpcTalkMenu.SkillRow row : menu.skills) {
            String name = Component.translatable("npc.skill." + row.id()).getString();
            String rank = Component.translatable("npc.skill.rank." + io.github.capttracker.ancientwarfarenpc.skill.Rank.byIndex(row.rank()).id()).getString();
            area.addGuiElement(new Label(TEXT_X, y, name + (row.main() ? " *" : "")).setColor(row.main() ? 0xFFFFD27F : 0xFFFFFFFF));
            area.addGuiElement(new Label(TEXT_X + 84, y, rank).setColor(row.rank() > 0 ? 0xFF55FF55 : 0xFFAAAAAA));
            ProgressBar bar = new ProgressBar(TEXT_X + 142, y - 1, 70, 10);
            bar.setProgress(row.percent() / 100.0F);
            area.addGuiElement(bar);
            y += 12;
            String detail = row.next() > 0 ? Component.translatable("guistrings.npc.skills.xp_next", row.xp(), row.next()).getString()
                    : Component.translatable("guistrings.npc.skills.xp_max", row.xp()).getString();
            area.addGuiElement(new Label(TEXT_X + 6, y, detail).setColor(0xFF999999));
            y += 12;
        }
        y += 4;
        return addWrapped(y, Component.translatable("guistrings.npc.skills.hint").getString(), 0xFFAAAAAA, 0);
    }

    private static int moodColor(int morale) {
        if (morale < 20) {
            return 0xFFFF5555;
        }
        if (morale < 40) {
            return 0xFFFFAA55;
        }
        if (morale < 60) {
            return 0xFFFFFFFF;
        }
        return 0xFF55FF55;
    }
}
