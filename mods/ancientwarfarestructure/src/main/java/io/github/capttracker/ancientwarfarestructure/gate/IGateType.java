/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/gates/IGateType.
 */
package io.github.capttracker.ancientwarfarestructure.gate;

import io.github.capttracker.ancientwarfarestructure.entity.EntityGate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * One kind of gate: its numbers, its look and how it moves its proxy blocks. Differences from 1.12.2:
 * {@code setCollisionBoundingBox} (which swapped the entity's bounding box by reflection) is {@link #getEntityBox}, the
 * box that hits and picks see; collision itself is the proxy blocks' shapes, which the {@code onGate*} callbacks set.
 */
public interface IGateType {

    /** The global ID, which determines the render type and model and was the item metadata (0, 1, 4, 5, 8, 9, 12). */
    int getGlobalID();

    /** The original type name ("gateBasicWood"); it is also the key of the item name in the 1.12.2 lang file. */
    String getDisplayName();

    /** The registry name of the spawner item of this type ("gate_basic_wood"). */
    String getRegistryName();

    /** The texture that the gate is drawn with. */
    ResourceLocation getTexture();

    /** The speed at which the gate opens or closes: blocks per tick, degrees per tick for the drawbridge. */
    float getMoveSpeed();

    int getMaxHealth();

    /**
     * The box of the gate entity (hits, picking, area queries of the battering ram): the whole volume while closed,
     * the top half of the top row while open, for the drawbridge the part that is out of the wall.
     */
    AABB getEntityBox(EntityGate gate);

    /** The gate starts to open from its closed state: the proxies stop colliding (server only). */
    void onGateStartOpen(EntityGate gate);

    void onGateFinishOpen(EntityGate gate);

    void onGateStartClose(EntityGate gate);

    /** The gate is closed: every cell of it holds a closed proxy (server only). */
    void onGateFinishClose(EntityGate gate);

    void setInitialBounds(EntityGate gate, BlockPos pos1, BlockPos pos2);

    /**
     * A callback from the spawning item for validation of a chosen pair of spawning points. This is where the gate can
     * reject a starting position/setup if the points are not placed correctly.
     */
    boolean arePointsValidPair(BlockPos pos1, BlockPos pos2);

    boolean canActivate(EntityGate gate, boolean open);

    boolean canSoldierActivate();

    /** 0 draws the solid wall, anything else the bars. */
    int getModelType();

    /** The spawner item that places this type (and that repacking drops). */
    ItemStack getConstructingItem();
}
