/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: the 1.12.2 worksite block derived its FACING and ACTIVE properties from the tile entity in
 * getActualState (BlockWorksiteBase); they are real block state properties now.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The block state properties every worksite block (the {@code WorksiteBlock} class and {@code AutoCraftingBlock})
 * must declare in {@code createBlockStateDefinition}, because {@link WorksiteBlockEntity} reads and writes them:
 * <ul>
 *   <li>{@link #FACING}: the vanilla horizontal facing. The block's front is the side the placer looks at
 *   (1.12.2: {@code invertFacing() == true}); it is set by {@code getStateForPlacement} through
 *   {@code BlockRotationHandler.getFaceForPlacement(context, this)}.</li>
 *   <li>{@link #ACTIVE}: true while the worksite has stored torque; the rear texture is animated while it is true.</li>
 * </ul>
 * Property instances are compared by identity in block states, so the block must use these constants, not its own.
 */
public final class WorksiteProperties {
    private WorksiteProperties() {}

    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
}
