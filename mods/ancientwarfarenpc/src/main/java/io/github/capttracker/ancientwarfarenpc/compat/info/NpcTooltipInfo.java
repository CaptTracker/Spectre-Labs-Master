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
 * server writes the text parts ({@link #write}) into the data of the provider, the client turns them into 2 to 6 lines ({@link #lines}). The name itself
 * is the display name of the entity (the nameplate text), which both mods show as the title; the full name with the surname, the partner and the number of
 * children of an owned NPC are lines of their own (rework phase 10).
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
        if (npc instanceof NpcPlayerOwned owned) {
            if (owned.isChild()) {
                data.putInt("childYears", io.github.capttracker.ancientwarfarenpc.settlement.family.FamilyAge.years(owned.getChildAgeTicks())); // "Tom Carter, child, 1 year 40 days"
                data.putInt("childDays", io.github.capttracker.ancientwarfarenpc.settlement.family.FamilyAge.days(owned.getChildAgeTicks()));
            }
            if ((!owned.getSurname().isEmpty() || owned.isChild()) && !owned.getGivenName().isEmpty() && owned.getCustomName() == null) {
                data.putString("given", owned.getGivenName());
                data.putString("surname", owned.getSurname()); // the full name "Bill Carter" (rework phase 10)
            }
            if (SettlementConfig.familiesEnabled) {
                owned.getSettlement().ifPresent(settlement -> {
                    io.github.capttracker.ancientwarfarenpc.settlement.family.FamilyApi.getPartner(settlement, owned.getUUID()).ifPresent(partner -> {
                        data.putString("partnerGiven", settlement.nameOf(partner));
                        data.putString("partnerSurname", settlement.surnameOf(partner));
                    });
                    int children = 0;
                    for (java.util.UUID child : settlement.getFamilies().childrenOf(owned.getUUID())) {
                        if (settlement.isMember(child)) {
                            children++;
                        }
                    }
                    if (children > 0) {
                        data.putInt("children", children);
                    }
                });
            }
        }
        data.putString("task", NpcThinking.task(npc));
        io.github.capttracker.ancientwarfarenpc.skill.Skill main = npc.getMainSkill();
        if (main != null) {
            data.putString("skill", main.id());
            data.putInt("rank", npc.getSkills().getRank(main)); // the true rank, also when the name does not show it
        }
        if (npc.supportsTraits() && SettlementConfig.traitsEnabled && npc.getTraitMask() != 0) {
            data.putInt("traits", npc.getTraitMask());
        }
        tag.put(KEY, data);
    }

    /** Client side: the lines for the data the server sent (none if it sent nothing), health from the entity. */
    public static List<Component> lines(CompoundTag tag, LivingEntity entity) {
        List<Component> lines = new ArrayList<>(4);
        if (tag.contains(KEY)) {
            CompoundTag data = tag.getCompound(KEY);
            Component role = data.contains("childYears") ? Component.translatable("guistrings.census.child") : Component.translatable("entity.ancientwarfarenpc." + data.getString("role") + ".name");
            if (data.contains("owner")) {
                lines.add(Component.translatable("tooltip.ancientwarfarenpc.npc.owned", role, data.getString("owner")));
            } else if (data.contains("faction")) {
                lines.add(Component.translatable("tooltip.ancientwarfarenpc.npc.faction", role, data.getString("faction")));
            } else {
                lines.add(role);
            }
            if (data.contains("childYears") && data.contains("given")) {
                lines.add(Component.translatable("tooltip.ancientwarfarenpc.npc.child", io.github.capttracker.ancientwarfarenpc.entity.NpcNames.full(data.getString("given"), data.getString("surname")),
                        io.github.capttracker.ancientwarfarenpc.settlement.family.FamilyAge.text(data.getInt("childYears"), data.getInt("childDays"))));
            } else if (data.contains("surname")) {
                lines.add(Component.translatable("tooltip.ancientwarfarenpc.npc.fullname", io.github.capttracker.ancientwarfarenpc.entity.NpcNames.full(data.getString("given"), data.getString("surname"))));
            }
            if (data.contains("mood")) {
                lines.add(Component.translatable("tooltip.ancientwarfarenpc.npc.mood", Component.translatable("guistrings.npc.mood." + data.getString("mood"))));
            }
            if (data.contains("partnerGiven")) {
                lines.add(Component.translatable("tooltip.ancientwarfarenpc.npc.partner",
                        io.github.capttracker.ancientwarfarenpc.entity.NpcNames.full(data.getString("partnerGiven"), data.getString("partnerSurname"))));
            }
            if (data.getInt("children") > 0) {
                lines.add(Component.translatable("tooltip.ancientwarfarenpc.npc.children", data.getInt("children")));
            }
            io.github.capttracker.ancientwarfarenpc.skill.Skill skill = io.github.capttracker.ancientwarfarenpc.skill.Skill.byId(data.getString("skill"));
            if (skill != null) {
                lines.add(Component.translatable("tooltip.ancientwarfarenpc.npc.skill", Component.translatable(io.github.capttracker.ancientwarfarenpc.skill.Rank.byIndex(data.getInt("rank")).langKey()),
                        Component.translatable(skill.langKey())));
            }
            if (data.getInt("traits") != 0) {
                net.minecraft.network.chat.MutableComponent names = Component.empty();
                for (io.github.capttracker.ancientwarfarenpc.trait.Trait trait : io.github.capttracker.ancientwarfarenpc.trait.NpcTraits.fromMask(data.getInt("traits"))) {
                    if (!names.getSiblings().isEmpty()) {
                        names.append(", ");
                    }
                    names.append(Component.translatable(trait.langKey()));
                }
                lines.add(Component.translatable("tooltip.ancientwarfarenpc.npc.traits", names));
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
