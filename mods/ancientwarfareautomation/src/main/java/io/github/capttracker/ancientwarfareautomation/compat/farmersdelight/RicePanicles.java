/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): the rice panicles of Farmer's Delight, as the crop farm harvests them. No class of the other mod is used.
 */
package io.github.capttracker.ancientwarfareautomation.compat.farmersdelight;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.cropfarm.CropDefault;
import io.github.capttracker.ancientwarfareautomation.config.AWAutomationStatics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The ripe panicles on a rice plant. They are a plain age-based crop, so the default handler finds them; this one differs in two things: the farm cuts them
 * with a knife (the loot table gives rice for a knife and only the panicle item without one), and nothing is replanted from the drops because the rice plant
 * stays in the water and grows new panicles by itself.
 */
public final class RicePanicles extends CropDefault {
    /** The block of the panicles. */
    public static final ResourceLocation BLOCK_ID = ResourceLocation.parse("farmersdelight:rice_panicles");
    private static final String[] KNIVES = {"farmersdelight:iron_knife", "farmersdelight:flint_knife"};

    @Override
    public boolean matches(BlockState state) {
        return AWAutomationStatics.farmersDelightEnabled && BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(BLOCK_ID);
    }

    /** Never the fallback for an item: only the blocks are matched. */
    @Override
    public boolean matches(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isPlantable(ItemStack stack) {
        return false;
    }

    @Override
    protected boolean replantsDrops() {
        return false;
    }

    @Override
    protected List<ItemStack> getDrops(ServerLevel level, BlockPos pos, BlockState state, int fortune) {
        for (String id : KNIVES) {
            Item knife = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).orElse(null);
            if (knife != null) {
                return Block.getDrops(state, level, pos, level.getBlockEntity(pos), null, new ItemStack(knife));
            }
        }
        return super.getDrops(level, pos, state, fortune);
    }
}
