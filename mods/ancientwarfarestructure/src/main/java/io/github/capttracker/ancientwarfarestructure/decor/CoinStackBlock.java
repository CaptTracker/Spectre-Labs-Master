/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/block/BlockCoinStack (and the block-placing half of npc/item/ItemCoin).
 */
package io.github.capttracker.ancientwarfarestructure.decor;

import io.github.capttracker.ancientwarfarestructure.init.AwDecor;
import io.github.capttracker.lib.util.ItemStacks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * A pile of coins. {@link #SIZE} counts groups of 8 coins (1 to 8, so 8 to 64 coins); in 1.12.2 it was a custom
 * property with the values 8, 16, ... 64 and the item NBT {@code size}. Right-click with the same metal's coin adds 8
 * coins (when the player holds 8 or more), right-click with anything else takes 8 coins. Breaking the block drops
 * the coins themselves.
 *
 * <p>The coin items belong to the NPC module; they are looked up through {@link CoinMetal#coinItem()}. When they are
 * missing the pile gives (and drops) coin stack items instead, so no coins are lost.
 */
public class CoinStackBlock extends Block {
    /** Number of 8-coin groups in the pile. */
    public static final IntegerProperty SIZE = IntegerProperty.create("size", 1, 8);
    public static final int COINS_PER_STEP = 8;

    // 1.12.2 box heights for 8, 16, ... 64 coins (40 and 48 coins share one height in the original).
    private static final double[] HEIGHTS = {0.10D, 0.25D, 0.37D, 0.57D, 0.81D, 0.81D, 0.95D, 1.00D};
    private static final VoxelShape[] SHAPES = new VoxelShape[HEIGHTS.length];

    static {
        for (int i = 0; i < HEIGHTS.length; i++) {
            SHAPES[i] = Shapes.box(0D, 0D, 0D, 1D, HEIGHTS[i], 1D);
        }
    }

    private final CoinMetal metal;

    public CoinStackBlock(Properties properties, CoinMetal metal) {
        super(properties);
        this.metal = metal;
        registerDefaultState(stateDefinition.any().setValue(SIZE, 1));
    }

    public CoinMetal getMetal() {
        return metal;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIZE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(SIZE) - 1];
    }

    // ---- items and drops ----

    /** The block item holding a pile of the given size ({@code groups} of 8 coins), as the recipes and creative tab give it. */
    public ItemStack stackItem(int groups) {
        ItemStack stack = new ItemStack(this);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(SIZE, Math.max(1, Math.min(8, groups))));
        return stack;
    }

    /**
     * {@code count} coins of this block's metal as stacks of at most their max stack size. Without the NPC coin item
     * the same value is returned as coin stack items (the count is rounded up to whole groups of 8).
     */
    public List<ItemStack> coins(int count) {
        List<ItemStack> stacks = new ArrayList<>();
        Item coin = metal.coinItem().orElse(null);
        if (coin == null) {
            int groups = (count + COINS_PER_STEP - 1) / COINS_PER_STEP;
            while (groups > 0) {
                int step = Math.min(8, groups);
                stacks.add(stackItem(step));
                groups -= step;
            }
            return stacks;
        }
        int remaining = count;
        while (remaining > 0) {
            ItemStack stack = new ItemStack(coin);
            int amount = Math.min(remaining, Math.max(1, stack.getMaxStackSize()));
            stack.setCount(amount);
            stacks.add(stack);
            remaining -= amount;
        }
        return stacks;
    }

    @Override
    protected List<net.minecraft.world.item.ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        return coins(state.getValue(SIZE) * COINS_PER_STEP);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return stackItem(state.getValue(SIZE));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BlockItemStateProperties properties = stack.get(DataComponents.BLOCK_STATE);
        Integer groups = properties == null ? null : properties.get(SIZE);
        tooltip.add(Component.literal((groups == null ? 1 : groups) * COINS_PER_STEP + " ").append(Component.translatable("guistrings.structure.coins")));
    }

    // ---- interaction ----

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (metal.isCoin(stack)) {
            addToStack(level, pos, state, player, stack);
        } else {
            removeFromStack(level, pos, state, player);
        }
        return ItemInteractionResult.CONSUME;
    }

    private void removeFromStack(Level level, BlockPos pos, BlockState state, Player player) {
        int size = state.getValue(SIZE);
        for (ItemStack coins : coins(COINS_PER_STEP)) {
            ItemStacks.giveOrDrop(player, coins);
        }
        if (size > 1) {
            level.setBlock(pos, state.setValue(SIZE, size - 1), Block.UPDATE_ALL);
        } else {
            level.removeBlock(pos, false);
        }
        playInteractSound(level, pos);
    }

    private void addToStack(Level level, BlockPos pos, BlockState state, Player player, ItemStack stack) {
        if (stack.getCount() >= COINS_PER_STEP) {
            int size = state.getValue(SIZE);
            if (size < 8) {
                level.setBlock(pos, state.setValue(SIZE, size + 1), Block.UPDATE_ALL);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(COINS_PER_STEP);
                }
            }
        }
        // The original plays the sound even when the pile is already full.
        playInteractSound(level, pos);
    }

    private static void playInteractSound(Level level, BlockPos pos) {
        level.playSound(null, pos, AwDecor.COIN_STACK_INTERACT.get(), SoundSource.PLAYERS, 0.5f, 1f);
    }

    /**
     * Right-clicking with 8 or more coins puts a new pile of 8 coins on the clicked block (1.12.2:
     * {@code ItemCoin.onItemUse}). The NPC module's coin items call this from {@code useOn}.
     *
     * @return FAIL with fewer than 8 coins or when the pile does not fit, otherwise the usual sided success
     */
    public static InteractionResult placeFromCoins(UseOnContext context, CoinMetal metal) {
        ItemStack stack = context.getItemInHand();
        if (stack.getCount() < COINS_PER_STEP) {
            return InteractionResult.FAIL;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.getBlockState(pos).canBeReplaced()) {
            pos = pos.relative(context.getClickedFace());
        }
        BlockState newState = AwDecor.coinStack(metal).get().defaultBlockState();
        Player player = context.getPlayer();
        CollisionContext collision = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        if (!level.isInWorldBounds(pos) || !level.getBlockState(pos).canBeReplaced() || !level.isUnobstructed(newState, pos, collision)) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, newState, Block.UPDATE_ALL);
            // 1.12.2 takes the coins even in creative mode.
            stack.shrink(COINS_PER_STEP);
            playInteractSound(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
