/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/tile/TileTownHall.NpcDeathEntry (moved from the town hall to the settlement).
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** One dead NPC in the death list of a settlement: the spawner stack that brings it back and what to show about it. */
public class NpcDeathEntry {
    public ItemStack stackToSpawn;
    public String npcType;
    public String npcName;
    public String deathCause;
    public boolean resurrected;
    public boolean canRes;
    public boolean beingResurrected;
    public int[] pos;

    public NpcDeathEntry(CompoundTag tag, HolderLookup.Provider registries) {
        readFromNBT(tag, registries);
    }

    public NpcDeathEntry(ItemStack stackToSpawn, String npcType, String npcName, String deathCause, boolean canRes, BlockPos npcPos) {
        this.stackToSpawn = stackToSpawn;
        this.npcType = npcType;
        this.npcName = npcName;
        this.deathCause = deathCause;
        this.canRes = canRes;
        this.pos = new int[]{npcPos.getX(), npcPos.getY(), npcPos.getZ()};
    }

    public final void readFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        stackToSpawn = ItemStack.parseOptional(registries, tag.getCompound("spawnerStack"));
        npcType = tag.getString("npcType");
        npcName = tag.getString("npcName");
        deathCause = tag.getString("deathCause");
        resurrected = tag.getBoolean("resurrected");
        canRes = tag.getBoolean("canRes");
        pos = tag.getIntArray("pos");
    }

    public CompoundTag writeToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("spawnerStack", stackToSpawn.saveOptional(registries));
        tag.putString("npcType", npcType);
        tag.putString("npcName", npcName);
        tag.putString("deathCause", deathCause);
        tag.putBoolean("resurrected", resurrected);
        tag.putBoolean("canRes", canRes);
        tag.putIntArray("pos", pos);
        return tag;
    }
}
