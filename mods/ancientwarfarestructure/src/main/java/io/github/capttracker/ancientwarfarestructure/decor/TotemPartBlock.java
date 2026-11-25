/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/block/BlockTotemPart.
 */
package io.github.capttracker.ancientwarfarestructure.decor;

import io.github.capttracker.ancientwarfare.block.BaseBlock;
import io.github.capttracker.ancientwarfarestructure.decor.entity.TotemPartBlockEntity;
import io.github.capttracker.ancientwarfarestructure.init.AwDecor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * One block for all 16 totem parts: the facing, the variant and the visible flag are block-state properties (in
 * 1.12.2 facing and visible were metadata and the variant lived in the tile entity). Multi-block variants fill the
 * space around the main block with invisible or side parts when placed ({@link #setPlacedBy}); breaking any part
 * removes the whole totem and drops the main block's variant once (see {@link TotemPartBlockEntity}).
 */
public class TotemPartBlock extends BaseBlock implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** False for filler blocks that only occupy space; they are not rendered. */
    public static final BooleanProperty VISIBLE = BooleanProperty.create("visible");
    public static final EnumProperty<TotemVariant> VARIANT = EnumProperty.create("variant", TotemVariant.class);

    public TotemPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(VISIBLE, true).setValue(VARIANT, TotemVariant.BASE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VISIBLE, VARIANT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TotemPartBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The variant is set by TotemPartItem, which knows which part it places.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection()).setValue(VISIBLE, true);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) {
            return;
        }
        TotemVariant variant = state.getValue(VARIANT);
        if (level.getBlockEntity(pos) instanceof TotemPartBlockEntity main) {
            main.setMainVariant(variant);
        }
        for (TotemVariant.Part part : variant.additionalParts(pos, state.getValue(FACING))) {
            BlockState partState = defaultBlockState()
                    .setValue(VARIANT, part.variant())
                    .setValue(FACING, part.facing())
                    .setValue(VISIBLE, part.visible());
            level.setBlock(part.pos(), partState, Block.UPDATE_ALL);
            if (level.getBlockEntity(part.pos()) instanceof TotemPartBlockEntity partEntity) {
                partEntity.setMain(pos, variant);
            }
        }
    }

    /** Whether an extra part of a multi-block variant may be placed at {@code pos} (1.12.2: World.mayPlace). */
    public boolean mayPlacePartAt(Level level, BlockPos pos, @Nullable net.minecraft.world.entity.Entity placer) {
        if (!level.isInWorldBounds(pos) || !level.getBlockState(pos).canBeReplaced()) {
            return false;
        }
        CollisionContext collision = placer == null ? CollisionContext.empty() : CollisionContext.of(placer);
        return level.isUnobstructed(defaultBlockState(), pos, collision);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        // The main block's variant is on the block entity, so this cannot be a plain loot table (1.12.2: harvestBlock).
        BlockEntity entity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        TotemVariant variant = entity instanceof TotemPartBlockEntity totem ? totem.getDropVariant() : state.getValue(VARIANT);
        return List.of(new ItemStack(AwDecor.totemItem(variant).get()));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        TotemVariant variant = level.getBlockEntity(pos) instanceof TotemPartBlockEntity totem
                ? totem.getDropVariant() : state.getValue(VARIANT);
        return new ItemStack(AwDecor.totemItem(variant).get());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(VISIBLE) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(VARIANT).getShape(state.getValue(FACING));
    }
}
