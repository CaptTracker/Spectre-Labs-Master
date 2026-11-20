/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/item/ItemBlockFirePit.
 */
package io.github.capttracker.ancientwarfarestructure.decor.item;

import io.github.capttracker.ancientwarfarestructure.decor.FirePitBlock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;

/**
 * Block item of a fire pit. Its name ends in {@code .lit} or {@code .unlit} (lang keys
 * {@code block.ancientwarfarestructure.fire_pit.lit} and so on), as the 1.12.2 NBT-driven name did.
 */
public class FirePitItem extends BlockItem {
    public FirePitItem(FirePitBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public String getDescriptionId(ItemStack stack) {
        BlockItemStateProperties properties = stack.get(DataComponents.BLOCK_STATE);
        Boolean lit = properties == null ? null : properties.get(FirePitBlock.LIT);
        return getDescriptionId() + (Boolean.TRUE.equals(lit) ? ".lit" : ".unlit");
    }
}
