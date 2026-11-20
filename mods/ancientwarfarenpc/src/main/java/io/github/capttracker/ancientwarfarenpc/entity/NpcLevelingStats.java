/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/NpcLevelingStats.
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.registry.NpcDefault;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;

/**
 * Experience and level of an NPC: a "generic" xp and level that grow with every kind of experience, plus one entry per full
 * type ({@code worker.miner}, {@code combat.soldier}, ...). The XP needed for level n is {@code (n + 3)^2}.
 */
public class NpcLevelingStats {

    private final Map<String, ExperienceEntry> experienceMap = new HashMap<>();
    private int xp;//'generic' xp, always incremented for all xp-types
    private int level;
    private final NpcBase npc;

    public NpcLevelingStats(NpcBase npc) {
        this.npc = npc;
    }

    public int getExperience() {
        String type = npc.getNpcFullType();
        ExperienceEntry entry = experienceMap.get(type);
        return entry == null ? 0 : entry.xp;
    }

    public int getLevel() {
        String type = npc.getNpcFullType();
        ExperienceEntry entry = experienceMap.get(type);
        return entry == null ? 0 : entry.level;
    }

    public int getBaseExperience() {
        return xp;
    }

    public int getBaseLevel() {
        return level;
    }

    private int getMaxLevel() {
        return npc instanceof NpcCombat ? AWNPCStatics.maxNpcCombatLevel : AWNPCStatics.maxNpcWorkerLevel;
    }

    public void addExperience(int xpGained) {
        if (npc.level().isClientSide) {
            return;
        }
        String type = npc.getNpcFullType();
        ExperienceEntry entry = experienceMap.computeIfAbsent(type, t -> new ExperienceEntry());
        entry.xp += xpGained;
        while (entry.level < getMaxLevel() && entry.xp >= getXPToLevel(entry.level + 1)) {
            entry.xp -= getXPToLevel(entry.level + 1);
            entry.level++;
            onSubLevelGained(entry.level);
        }
        this.xp += xpGained;
        while (level < getMaxLevel() && this.xp >= getXPToLevel(level)) {
            this.xp -= getXPToLevel(level);
            onBaseLevelGained(level + 1);
        }
    }

    private void onBaseLevelGained(int newLevel) {
        level = newLevel;
        if (newLevel <= getMaxLevel()) {
            if (npc.getMaxHealthOverride() <= 0) {
                NpcDefault npcDefault = npc.getNpcDefault();
                double health = (npcDefault == null ? 20.0D : npcDefault.getBaseHealth()) + newLevel;
                npc.setBaseMaxHealth(health);
            }
            npc.updateDamageFromLevel();
        }
    }

    private void onSubLevelGained(int newLevel) {
        npc.updateDamageFromLevel();
    }

    private int getXPToLevel(int level) {
        return (level + 3) * (level + 3);
    }

    public CompoundTag writeToNBT(CompoundTag tag) {
        tag.putInt("xp", xp);
        tag.putInt("level", level);
        ListTag entryList = new ListTag();
        for (Map.Entry<String, ExperienceEntry> entry : experienceMap.entrySet()) {
            CompoundTag xpTag = new CompoundTag();
            xpTag.putString("type", entry.getKey());
            xpTag.putInt("xp", entry.getValue().xp);
            xpTag.putInt("level", entry.getValue().level);
            entryList.add(xpTag);
        }
        tag.put("entryList", entryList);
        return tag;
    }

    public void readFromNBT(CompoundTag tag) {
        experienceMap.clear();
        xp = tag.getInt("xp");
        level = tag.getInt("level");
        ListTag entryList = tag.getList("entryList", Tag.TAG_COMPOUND);
        for (int i = 0; i < entryList.size(); i++) {
            CompoundTag xpTag = entryList.getCompound(i);
            ExperienceEntry entry = new ExperienceEntry();
            entry.xp = xpTag.getInt("xp");
            entry.level = xpTag.getInt("level");
            experienceMap.put(xpTag.getString("type"), entry);
        }
    }

    private static final class ExperienceEntry {
        int xp;
        int level;
    }
}
