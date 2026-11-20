/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/item/ItemBlockTotemPart (the variant was item NBT; there is one item per
 * variant now).
 */
package io.github.capttracker.ancientwarfarestructure.decor.item;

import io.github.capttracker.ancientwarfarestructure.decor.TotemPartBlock;
import io.github.capttracker.ancientwarfarestructure.decor.TotemVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Places a totem block of one variant. Before placing it checks that every extra block of a multi-block variant
 * has room (1.12.2: {@code canPlaceBlockOnSide} asked {@code Variant.canPlace}).
 */
public class TotemPartItem extends BlockItem {
    private final TotemVariant variant;

    public TotemPartItem(TotemPartBlock block, Properties properties, TotemVariant variant) {
        super(block, properties);
        this.variant = variant;
    }

    public TotemVariant getVariant() {
        return variant;
    }

    @Nullable
    @Override
    protected BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = getBlock().getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        state = state.setValue(TotemPartBlock.VARIANT, variant);
        return canPlace(context, state) ? state : null;
    }

    @Override
    protected boolean canPlace(BlockPlaceContext context, BlockState state) {
        if (!super.canPlace(context, state)) {
            return false;
        }
        Level level = context.getLevel();
        TotemPartBlock block = (TotemPartBlock) getBlock();
        BlockPos pos = context.getClickedPos();
        for (BlockPos extra : variant.additionalPositions(pos, state.getValue(TotemPartBlock.FACING))) {
            if (!block.mayPlacePartAt(level, extra, context.getPlayer())) {
                return false;
            }
        }
        return true;
    }

    /** Names the item by its variant, like the 1.12.2 {@code tile.totem_part.<variant>.name} keys. */
    @Override
    public String getDescriptionId() {
        return super.getDescriptionId() + "." + variant.getSerializedName();
    }
}
