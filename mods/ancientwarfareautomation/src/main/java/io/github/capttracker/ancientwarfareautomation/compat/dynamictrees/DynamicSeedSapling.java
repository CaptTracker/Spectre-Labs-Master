/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (optional integration): the seeds of Dynamic Trees as plantables of the tree farm.
 */
package io.github.capttracker.ancientwarfareautomation.compat.dynamictrees;

import com.dtteam.dynamictrees.item.Seed;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.treefarm.ISapling;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * A seed of Dynamic Trees. The farm plants it with the planting call of the mod itself ({@code Seed.doPlanting}), which puts the sapling of the species
 * where the seed belongs and checks the soil and the room for it; the farm takes the seed from its inventory when that returns true.
 */
final class DynamicSeedSapling implements ISapling {
    @Override
    public boolean matches(ItemStack stack) {
        return AWAutomationStatics.dynamicTreesEnabled && stack.getItem() instanceof Seed seed && seed.getSpecies().isValid();
    }

    @Override
    public boolean isRightClick() {
        return false;
    }

    @Override
    public boolean plantsItself() {
        return true;
    }

    @Override
    public boolean plant(ServerLevel level, BlockPos pos, ItemStack stack) {
        return stack.getItem() instanceof Seed seed && seed.doPlanting(level, pos, null, stack.copy());
    }
}
