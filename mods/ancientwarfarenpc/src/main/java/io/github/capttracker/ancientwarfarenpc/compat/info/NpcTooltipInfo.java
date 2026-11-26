/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 6): the look-at tooltip lines of an NPC, shared by the Jade and WTHIT plugins. No class of either mod is used here.
 */
package io.github.capttracker.ancientwarfarenpc.compat.info;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.entity.NpcThinking;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.settlement.NpcMorale;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the look-at tooltip says of an NPC: the role with the owner or faction, the mood word (owned NPCs), the task in short words and the health. The
 * server writes the text parts ({@link #write}) into the data of the provider, the client turns them into 2 to 4 lines ({@link #lines}). The name itself
 * is the display name of the entity (the nameplate text), which both mods show as the title.
 */
public final class NpcTooltipInfo {
    /** The key of our data in the data tag of the mods. */
    public static final String KEY = "awnpc";

    private NpcTooltipInfo() {}

    /** Server side: writes the role, the owner or faction, the mood and the task of the NPC into the tag. */
    public static void write(CompoundTag tag, NpcBase npc) {
        CompoundTag data = new CompoundTag();
        data.putString("role", npc.getNpcFullType());
        if (npc instanceof NpcFaction faction) {
            data.putString("faction", capitalize(faction.getFaction()));
        } else {
            String owner = npc.getOwner().getName();
            if (!owner.isEmpty()) {
                data.putString("owner", owner);
            }
        }
        if (npc instanceof NpcPlayerOwned owned && SettlementConfig.moraleEnabled) {
            data.putString("mood", NpcMorale.mood(owned.getPersonalMorale()));
        }
        data.putString("task", NpcThinking.task(npc));
        tag.put(KEY, data);
    }

    /** Client side: the lines for the data the server sent (none if it sent nothing), health from the entity. */
    public static List<Component> lines(CompoundTag tag, LivingEntity entity) {
        List<Component> lines = new ArrayList<>(4);
        if (tag.contains(KEY)) {
            CompoundTag data = tag.getCompound(KEY);
            Component role = Component.translatable("entity.ancientwarfarenpc." + data.getString("role") + ".name");
            if (data.contains("owner")) {
                lines.add(Component.translatable("tooltip.ancientwarfarenpc.npc.owned", role, data.getString("owner")));
            } else if (data.contains("faction")) {
                lines.add(Component.translatable("tooltip.ancientwarfarenpc.npc.faction", role, data.getString("faction")));
            } else {
                lines.add(role);
            }
            if (data.contains("mood")) {
                lines.add(Component.translatable("tooltip.ancientwarfarenpc.npc.mood", Component.translatable("guistrings.npc.mood." + data.getString("mood"))));
            }
            lines.add(Component.translatable("tooltip.ancientwarfarenpc.npc.task", taskText(data.getString("task"))));
        }
        lines.add(Component.translatable("tooltip.ancientwarfarenpc.npc.health", Math.max(0, (int) Math.ceil(entity.getHealth())),
                (int) Math.ceil(entity.getMaxHealth())));
        return lines;
    }

    /** A task key without text in the language file shows as "Working". */
    private static Component taskText(String task) {
        String key = "guistrings.census.task." + task;
        return Component.translatable(Language.getInstance().has(key) ? key : "guistrings.census.task.work.generic");
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
