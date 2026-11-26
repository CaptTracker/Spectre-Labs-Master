/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 7): the skills of the player-owned NPCs.
 */
package io.github.capttracker.ancientwarfarenpc.skill;

import io.github.capttracker.ancientwarfare.api.IWorkSite.WorkType;
import io.github.capttracker.ancientwarfare.api.WorkAction;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * The skills an NPC learns by doing the work. Each has its own experience (saved per NPC, see {@link NpcSkills}) and a {@link Rank} made from it. The lang
 * key of a skill is {@code npc.skill.<id>}; the title of an NPC (its rank in the name) comes from the name templates.
 */
public enum Skill {
    MINING, WOODCUTTING, FARMING, FISHING, HUSBANDRY, CRAFTING, TRADING, HAULING, COMBAT, MEDICINE,
    /** Reserved for the builders of phase 9: no work gives experience for it yet. */
    BUILDING;

    public static final Skill[] ALL = values();

    /**
     * Ticks between two experience gains of this skill. Trading and hauling are paid per action that a player can repeat as fast as the NPC allows (a
     * trade back and forth, a courier route between two chests), so they are paced; the others are limited by the work itself.
     */
    public int xpCooldownTicks() {
        return this == TRADING || this == HAULING ? 100 : 0;
    }

    /** The lower case name: the key in the save data and in the lang keys. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String langKey() {
        return "npc.skill." + id();
    }

    /** The skill with the id, or null. */
    @Nullable
    public static Skill byId(String id) {
        for (Skill skill : ALL) {
            if (skill.id().equals(id)) {
                return skill;
            }
        }
        return null;
    }

    /**
     * The skill that a task at a staffed worksite trains: by what the worker does (mining, chopping, fishing, the animals), and for the actions that fit any
     * site (placing, collecting) by the kind of the site.
     */
    @Nullable
    public static Skill ofTask(WorkAction action, WorkType siteType) {
        return switch (action) {
            case MINE -> MINING;
            case CHOP -> WOODCUTTING;
            case TILL, PLANT, HARVEST, FERTILIZE -> FARMING;
            case FISH -> FISHING;
            case SHEAR, MILK, BREED, CULL -> HUSBANDRY;
            case SMELT, SAW, BAKE, SMITH, TAN -> CRAFTING;
            case PLACE, COLLECT -> ofWorkType(siteType);
        };
    }

    /** The skill of a kind of worksite (the sites of the older work orders, and the actions that fit any site). Null for none. */
    @Nullable
    public static Skill ofWorkType(WorkType type) {
        return switch (type) {
            case MINING -> MINING;
            case FORESTRY -> WOODCUTTING;
            case FARMING -> FARMING;
            case CRAFTING, RESEARCH, SMITHING, TANNING -> CRAFTING;
            case NONE -> null;
        };
    }

    /**
     * The main skill of an NPC of the full type ({@code worker.miner}, {@code combat.soldier}, ...), the one its title shows. Null for the types without a
     * skill (an innkeeper, a bard, a worker without a role, the faction NPCs).
     */
    @Nullable
    public static Skill mainOf(String fullType) {
        int dot = fullType.indexOf('.');
        String type = dot < 0 ? fullType : fullType.substring(0, dot);
        String sub = dot < 0 ? "" : fullType.substring(dot + 1);
        return switch (type) {
            case "worker" -> switch (sub) {
                case "farmer" -> FARMING;
                case "lumberjack" -> WOODCUTTING;
                case "miner" -> MINING;
                case "researcher", "craftsman", "smith", "tanner" -> CRAFTING;
                default -> null;
            };
            case "combat" -> switch (sub) {
                case "medic" -> MEDICINE;
                case "engineer" -> BUILDING;
                default -> COMBAT;
            };
            case "guard_captain" -> COMBAT;
            case "courier" -> HAULING;
            case "trader" -> TRADING;
            case "priest" -> MEDICINE;
            case "stable_hand" -> HUSBANDRY;
            default -> null;
        };
    }
}
