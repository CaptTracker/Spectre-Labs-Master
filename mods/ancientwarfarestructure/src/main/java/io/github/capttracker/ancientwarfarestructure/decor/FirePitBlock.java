/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/block/BlockFirePit.
 */
package io.github.capttracker.ancientwarfarestructure.decor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A decorative fire pit. Lit with flint and steel or a torch (the torch is not used up), put out with a water bucket
 * (the bucket is emptied, except in creative mode). Lit it gives light level 15 and smoke particles. It is 4/16 high,
 * has no collision box and needs a sturdy block below it when it is placed. Breaking it always drops the unlit pit.
 *
 * <p>The 1.12.2 block had the property {@code variant} (default, log, square); each variant is its own block now
 * ({@link FirePitVariant}). The {@code lit} state travels with the item as a block-state item component
 * ({@code minecraft:block_state}), so a lit pit can be picked from the creative tab and placed lit.
 *
 * <p>The original also ticked the block randomly and had a tick rate of 30, but never did anything with either; they
 * are not ported.
 */
public class FirePitBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);

    private final FirePitVariant variant;

    public FirePitBlock(Properties properties, FirePitVariant variant) {
        super(properties);
        this.variant = variant;
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    public FirePitVariant getVariant() {
        return variant;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** 1.12.2: canPlaceBlockAt, the block below must have a solid top face. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    // ---- items ----

    /** The block item of this pit in the given state, as the creative tab and the pick-block key give it. */
    public ItemStack stack(boolean lit) {
        ItemStack stack = new ItemStack(this);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(LIT, lit));
        return stack;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return stack(state.getValue(LIT));
    }

    // ---- interaction ----

    private static boolean isFireStarter(ItemStack stack) {
        return stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.TORCH);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        boolean lit = state.getValue(LIT);
        if (!lit && isFireStarter(stack)) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);
            if (stack.isDamageableItem()) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
            level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1f, 1f);
            return ItemInteractionResult.CONSUME;
        }
        if (lit && stack.is(Items.WATER_BUCKET)) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }
            level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL);
            if (!player.getAbilities().instabuild) {
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            }
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1f);
            // The original fell through to the default handling here, so the emptied bucket could also act on the
            // block; consuming the click is the obvious intent.
            return ItemInteractionResult.CONSUME;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    // ---- client effects ----

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        if (random.nextInt(10) == 0) {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS,
                    random.nextFloat(), random.nextFloat() * 0.7F + 0.3F, false);
        }
        for (int i = 0; i < 3; ++i) {
            double x = pos.getX() + 0.25D + random.nextDouble() * 0.5D;
            double y = pos.getY() + random.nextDouble() * 0.5D + 0.5D;
            double z = pos.getZ() + 0.25D + random.nextDouble() * 0.5D;
            level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }
}
