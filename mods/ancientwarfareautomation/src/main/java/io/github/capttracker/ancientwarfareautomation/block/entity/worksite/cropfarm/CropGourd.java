/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/cropfarm/CropGourd.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Melons and pumpkins: broken when found. 1.12.2 matched {@code Material.GOURD}, which also held the carved pumpkin
 * and the jack o'lantern (they were all {@code BlockPumpkin}); the materials are gone, so the four blocks are listed.
 */
public class CropGourd extends CropBreakOnly {
    /** True for the blocks of the former gourd material. */
    static boolean isGourd(BlockState state) {
        return state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN) || state.is(Blocks.CARVED_PUMPKIN) || state.is(Blocks.JACK_O_LANTERN);
    }

    @Override
    public boolean matches(BlockState state) {
        return isGourd(state);
    }
}
