/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 7): the experience of one NPC in each skill.
 */
package io.github.capttracker.ancientwarfarenpc.skill;

import net.minecraft.nbt.CompoundTag;

/**
 * The experience of one NPC in each {@link Skill}, saved as one compound {@code skills} with an int per skill id. Missing data (an NPC from an older save)
 * reads as no experience. Server side the NPC owns one and writes to it; the talk screen gets a copy of the numbers with its state.
 */
public final class NpcSkills {
    private final int[] xp = new int[Skill.ALL.length];

    public int getXp(Skill skill) {
        return xp[skill.ordinal()];
    }

    public int getRank(Skill skill) {
        return Rank.indexOf(xp[skill.ordinal()]);
    }

    /**
     * Adds experience.
     *
     * @return the new rank if the rank went up, else -1
     */
    public int add(Skill skill, int amount) {
        if (amount <= 0) {
            return -1;
        }
        int before = getRank(skill);
        long sum = (long) xp[skill.ordinal()] + amount;
        xp[skill.ordinal()] = (int) Math.min(sum, 1_000_000_000L);
        int after = getRank(skill);
        return after > before ? after : -1;
    }

    /** Progress inside the rank of this experience, 0 to 1 (1 at Master). */
    public static double progress(int xp) {
        int rank = Rank.indexOf(xp);
        if (rank >= Rank.MAX) {
            return 1.0D;
        }
        int from = Rank.ALL[rank].minXp();
        int to = Rank.ALL[rank + 1].minXp();
        return to <= from ? 1.0D : Math.max(0.0D, Math.min(1.0D, (xp - from) / (double) (to - from)));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (Skill skill : Skill.ALL) {
            if (xp[skill.ordinal()] > 0) {
                tag.putInt(skill.id(), xp[skill.ordinal()]);
            }
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        for (Skill skill : Skill.ALL) {
            xp[skill.ordinal()] = Math.max(0, tag.getInt(skill.id()));
        }
    }
}
