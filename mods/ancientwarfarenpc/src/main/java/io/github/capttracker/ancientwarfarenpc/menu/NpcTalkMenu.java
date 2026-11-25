/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (playtest fixes): talking to an NPC, and the morale tab of an NPC.
 */
package io.github.capttracker.ancientwarfarenpc.menu;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcMind;
import io.github.capttracker.ancientwarfarenpc.entity.NpcMind.Line;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.entity.NpcThinking;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcMenus;
import io.github.capttracker.ancientwarfarenpc.settlement.NpcMorale;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The talk screen of an NPC, with the tabs Talk (the name, a greeting, what the NPC thinks right now and what happened to it lately) and Morale (the
 * personal morale of a player-owned NPC with the factors; for the commanders of the NPC only). Anyone who is not an enemy of the NPC may talk to it.
 * The menu has no slots. The server makes everything from the live state of the NPC ({@link NpcThinking}, {@link NpcMorale}) and sends it when the menu
 * opens and every second after that (only when something changed on the screen); the client sends nothing.
 */
public class NpcTalkMenu extends NpcMenuBase<NpcBase> {
    public static final int TAB_TALK = 0;
    public static final int TAB_MORALE = 1;
    private static final String STATE_TAG = "talk";
    private static final int REFRESH_TICKS = 20;

    /** A thing that happened lately with its age in minutes. */
    public record Event(Line line, int minutes) {}

    /** A factor of the morale. */
    public record Factor(String key, int delta) {}

    // server
    private final int initialTab;
    private int refreshTimer = 0;

    // client: what the last state said
    public Component npcName = Component.empty();
    @Nullable
    public Line greeting;
    public final List<Line> lines = new ArrayList<>();
    public final List<Event> events = new ArrayList<>();
    public boolean canEdit = false;
    public boolean hasMorale = false;
    public boolean moraleOff = false;
    public int morale = 50;
    public int moraleBase = 50;
    public double moraleFactor = 1.0D;
    public boolean moraleHasSettlement = false;
    public final List<Factor> factors = new ArrayList<>();
    public int tab = TAB_TALK;
    @Nullable
    private CompoundTag lastState;

    /** Client side: the entity id of the NPC comes from the buffer written by {@link NpcMenuOpener}. */
    public NpcTalkMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, find(inventory.player, buf.readVarInt(), NpcBase.class), TAB_TALK);
    }

    public NpcTalkMenu(int containerId, Inventory inventory, @Nullable NpcBase npc, int initialTab) {
        super(AwNpcMenus.TALK_MENU.get(), containerId, inventory, npc);
        this.initialTab = initialTab;
    }

    /** Whether this NPC has a morale tab for this player: a player-owned NPC and one of its commanders. */
    public static boolean hasMoraleTab(Player who, NpcBase npc) {
        return npc instanceof NpcPlayerOwned && mayEdit(who, npc);
    }

    /** Anyone who is not an enemy of the NPC, and the editors of it. */
    @Override
    protected boolean mayUse(Player who) {
        return npc != null && (mayEdit(who, npc) || !npc.isHostileTowards(who));
    }

    @Override
    protected void sendInitialState() {
        sendState(true);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!isClient() && ++refreshTimer >= REFRESH_TICKS) {
            refreshTimer = 0;
            sendState(false);
        }
    }

    private void sendState(boolean withTab) {
        if (npc == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        var registries = registries();
        CompoundTag state = new CompoundTag();
        state.putString("name", Component.Serializer.toJson(npc.getName(), registries));
        state.put("greeting", NpcThinking.greeting(npc, player).save(registries));
        ListTag lineList = new ListTag();
        for (Line line : NpcThinking.describe(npc)) {
            lineList.add(line.save(registries));
        }
        state.put("lines", lineList);
        ListTag eventList = new ListTag();
        List<NpcMind.Thought> thoughts = npc.getMind().thoughts();
        for (int i = thoughts.size() - 1; i >= 0; i--) {
            CompoundTag event = new CompoundTag();
            event.put("line", thoughts.get(i).line().save(registries));
            event.putInt("min", (int) Math.max(0L, (level.getGameTime() - thoughts.get(i).time()) / 1200L));
            eventList.add(event);
        }
        state.put("events", eventList);
        boolean editor = mayEdit(player, npc);
        state.putBoolean("canEdit", editor);
        if (npc instanceof NpcPlayerOwned owned && editor) {
            CompoundTag morale = new CompoundTag();
            if (!SettlementConfig.moraleEnabled) {
                morale.putBoolean("off", true);
            } else {
                if (owned.getMoraleFactors().isEmpty()) {
                    owned.recomputeMorale(level);
                }
                morale.putInt("value", owned.getPersonalMorale());
                morale.putInt("base", owned.getMoraleBase());
                morale.putDouble("factor", SettlementConfig.moraleWorkFactor(owned.getPersonalMorale()));
                morale.putBoolean("settlement", owned.isMoraleSettlementBased());
                ListTag factorList = new ListTag();
                for (NpcMorale.Factor factor : owned.getMoraleFactors()) {
                    CompoundTag f = new CompoundTag();
                    f.putString("key", factor.key());
                    f.putInt("delta", factor.delta());
                    factorList.add(f);
                }
                morale.put("factors", factorList);
            }
            state.put("morale", morale);
        }
        CompoundTag tag = new CompoundTag();
        tag.put(STATE_TAG, state);
        if (withTab) {
            tag.putInt("tab", initialTab);
        }
        sendDataToClient(tag);
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (!isClient()) {
            return; // the client asks for nothing
        }
        if (tag.contains("tab", Tag.TAG_INT)) {
            tab = tag.getInt("tab");
        }
        if (!tag.contains(STATE_TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag state = tag.getCompound(STATE_TAG);
        boolean changed = !state.equals(lastState);
        lastState = state;
        if (changed) {
            readState(state);
        }
        if (changed || tag.contains("tab", Tag.TAG_INT)) {
            refreshGui();
        }
    }

    private void readState(CompoundTag state) {
        var registries = registries();
        try {
            npcName = Component.Serializer.fromJson(state.getString("name"), registries);
        } catch (RuntimeException e) {
            npcName = Component.empty();
        }
        greeting = Line.load(state.getCompound("greeting"), registries);
        lines.clear();
        ListTag lineList = state.getList("lines", Tag.TAG_COMPOUND);
        for (int i = 0; i < lineList.size(); i++) {
            lines.add(Line.load(lineList.getCompound(i), registries));
        }
        events.clear();
        ListTag eventList = state.getList("events", Tag.TAG_COMPOUND);
        for (int i = 0; i < eventList.size(); i++) {
            CompoundTag event = eventList.getCompound(i);
            events.add(new Event(Line.load(event.getCompound("line"), registries), event.getInt("min")));
        }
        canEdit = state.getBoolean("canEdit");
        hasMorale = state.contains("morale", Tag.TAG_COMPOUND);
        factors.clear();
        if (hasMorale) {
            CompoundTag morale = state.getCompound("morale");
            moraleOff = morale.getBoolean("off");
            this.morale = morale.getInt("value");
            moraleBase = morale.getInt("base");
            moraleFactor = morale.getDouble("factor");
            moraleHasSettlement = morale.getBoolean("settlement");
            ListTag factorList = morale.getList("factors", Tag.TAG_COMPOUND);
            for (int i = 0; i < factorList.size(); i++) {
                CompoundTag f = factorList.getCompound(i);
                factors.add(new Factor(f.getString("key"), f.getInt("delta")));
            }
        }
        if (!hasMorale && tab == TAB_MORALE) {
            tab = TAB_TALK;
        }
    }

    /** Client side: switches the tab (the screen rebuilds itself). */
    public void setTab(int tab) {
        this.tab = tab;
        refreshGui();
    }
}
