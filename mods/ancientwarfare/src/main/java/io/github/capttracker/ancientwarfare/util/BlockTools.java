/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/BlockTools.
 */
package io.github.capttracker.ancientwarfare.util;

import com.google.common.collect.AbstractIterator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.capttracker.ancientwarfare.config.AWCoreStatics;
import io.github.capttracker.ancientwarfare.util.parsing.PropertyState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Block position, rotation and block-state helpers, and breaking/placing blocks on behalf of automation.
 *
 * <p>Replaced by vanilla (use directly): the per-property rotator table of 1.12.2 ({@code rotateFacing})
 * is now {@link BlockState#rotate(Rotation)}, which every vanilla property type already handles;
 * {@code getMetaFromState} based comparisons are plain {@link BlockState} equality;
 * {@code BlockPos.betweenClosed} walks a box.
 */
public final class BlockTools {
    private BlockTools() {}

    /**
     * Test for blocks that structure scans skip when looking for the ground (grass, leaves, ...).
     * The Structures slice installs its own with {@link #setSkippableTest}; by default nothing is skipped.
     */
    private static volatile Predicate<BlockState> skippableTest = state -> false;

    /** Hook for the Structures slice (original: {@code AWStructureStatics.isSkippable}). */
    public static void setSkippableTest(Predicate<BlockState> test) {
        skippableTest = test;
    }

    // ---- rotation and bounds ----

    public static BlockPos rotateHorizontal(BlockPos offset, int turns) {
        BlockPos.MutableBlockPos ret = new BlockPos.MutableBlockPos(offset.getX(), offset.getY(), offset.getZ());
        for (int i = 0; i < turns; i++) {
            ret.set(-ret.getZ(), ret.getY(), ret.getX());
        }
        return ret.immutable();
    }

    /** Rotate a float X offset (0 to 1) within a block. */
    public static float rotateFloatX(float x, float z, int turns) {
        float x1 = x;
        float z1 = z;
        for (int i = 0; i < turns; i++) {
            z = x1;
            x = 1.f - z1;
            x1 = x;
            z1 = z;
        }
        return x;
    }

    public static float rotateFloatZ(float x, float z, int turns) {
        float x1 = x;
        float z1 = z;
        for (int i = 0; i < turns; i++) {
            z = x1;
            x = 1.f - z1;
            x1 = x;
            z1 = z;
        }
        return z;
    }

    public static BlockPos getAverageOf(BlockPos... positions) {
        float x = 0;
        float y = 0;
        float z = 0;
        int count = 0;
        for (BlockPos pos : positions) {
            x += pos.getX();
            y += pos.getY();
            z += pos.getZ();
            count++;
        }
        if (count > 0) {
            x /= count;
            y /= count;
            z /= count;
        }
        return new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
    }

    /**
     * The block the player is looking at within 5 blocks, or the position of an entity the player's eyes are
     * inside of (the original's odd entity test, kept as it was).
     *
     * @param offset return the position next to the block, on the face that was hit
     * @return null if nothing is in range
     */
    @Nullable
    public static BlockPos getBlockClickedOn(Player player, Level world, boolean offset) {
        double reachLength = 5.0D;
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = start.add(look.x * reachLength, look.y * reachLength, look.z * reachLength);
        BlockHitResult hit = world.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return null;
        }

        AABB searchBox = player.getBoundingBox().expandTowards(look.x * reachLength, look.y * reachLength, look.z * reachLength).inflate(1.0D);
        for (Entity testEntity : world.getEntities(player, searchBox)) {
            if (testEntity.isPickable()) {
                float bbExpansionSize = testEntity.getPickRadius();
                if (testEntity.getBoundingBox().inflate(bbExpansionSize).contains(start)) {
                    return testEntity.blockPosition();
                }
            }
        }
        return offset ? hit.getBlockPos().relative(hit.getDirection()) : hit.getBlockPos();
    }

    public static BlockPos rotateAroundOrigin(BlockPos pos, int turns) {
        for (int i = 0; i < turns; i++) {
            pos = rotateAroundOrigin(pos);
        }
        return pos;
    }

    /** Rotate a position around (0,0,0) in 90 degree clockwise steps. */
    private static BlockPos rotateAroundOrigin(BlockPos pos) {
        return new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
    }

    /** Whether TEST lies somewhere in the box bounded by pos1 (minimum) and pos2 (maximum). */
    public static boolean isPositionWithinBounds(BlockPos test, BlockPos pos1, BlockPos pos2) {
        return isPositionWithinHorizontalBounds(test, pos1, pos2)
                && test.getY() >= pos1.getY() && test.getY() <= pos2.getY();
    }

    public static boolean isPositionWithinHorizontalBounds(BlockPos test, BlockPos minPos, BlockPos maxPos) {
        return test.getX() >= minPos.getX() && test.getX() <= maxPos.getX()
                && test.getZ() >= minPos.getZ() && test.getZ() <= maxPos.getZ();
    }

    /** A new position holding the minimum coordinates of the two. */
    public static BlockPos getMin(BlockPos pos1, BlockPos pos2) {
        return new BlockPos(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()));
    }

    /** A new position holding the maximum coordinates of the two. */
    public static BlockPos getMax(BlockPos pos1, BlockPos pos2) {
        return new BlockPos(Math.max(pos1.getX(), pos2.getX()), Math.max(pos1.getY(), pos2.getY()), Math.max(pos1.getZ(), pos2.getZ()));
    }

    /** Rotates a block position in an area by the number of turns. Used by templates to get a relative position. */
    public static BlockPos rotateInArea(BlockPos pos, int xSize, int zSize, int turns) {
        int xSize1 = xSize;
        int zSize1 = zSize;
        int x = pos.getX();
        int z = pos.getZ();
        int x1 = x;
        int z1 = z;
        int positiveTurns = turns > 0 ? turns : 4 + turns;
        for (int i = 0; i < positiveTurns; i++) {
            x = zSize - 1 - z1;
            z = x1;
            x1 = x;
            z1 = z;
            xSize = zSize1;
            zSize = xSize1;
            xSize1 = xSize;
            zSize1 = zSize;
        }
        return new BlockPos(x, pos.getY(), z);
    }

    // ---- breaking and placing ----

    public static boolean breakBlockAndDrop(Level world, BlockPos pos) {
        return breakBlock(world, pos, 0, true);
    }

    /**
     * Removes the block, optionally dropping its loot as if broken with a tool of the given Fortune level
     * (the drop ignores the harvest-tool requirement, as the original did). Server only.
     *
     * @return true if the block was removed
     */
    public static boolean breakBlock(Level world, BlockPos pos, int fortune, boolean doDrop) {
        if (!(world instanceof ServerLevel level)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) {
            return false;
        }
        if (doDrop) {
            if (!canBreakBlock(level, pos, state)) {
                return false;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            Block.dropResources(state, level, pos, blockEntity, null, getFortuneTool(level, fortune));
        }
        return level.removeBlock(pos, false);
    }

    private static ItemStack getFortuneTool(ServerLevel level, int fortune) {
        if (fortune <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Enchantments.FORTUNE)
                .ifPresent(holder -> tool.enchant(holder, fortune));
        return tool;
    }

    /** Posts the break event for the fake player, if the config says so. @return false if it was cancelled */
    private static boolean canBreakBlock(ServerLevel world, BlockPos pos, BlockState state) {
        return !AWCoreStatics.fireBlockBreakEvents
                || !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world, pos, state, FakePlayers.get(world))).isCanceled();
    }

    public static boolean breakBlockNoDrops(Level world, BlockPos pos, BlockState state) {
        if (!(world instanceof ServerLevel level)) {
            return false;
        }
        if (!canBreakBlock(level, pos, state) || !level.removeBlock(pos, false)) {
            return false;
        }
        level.levelEvent(2001, pos, Block.getId(state));
        return true;
    }

    /** Uses the stack as a fake player standing at the position, looking straight down (for seeds, buckets, ...). */
    public static boolean placeItemBlockRightClick(ItemStack stack, Level world, BlockPos pos) {
        if (!(world instanceof ServerLevel level)) {
            return false;
        }
        Player owner = FakePlayers.get(level);
        owner.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        owner.setXRot(90F);

        // sidedSuccess is CONSUME on the server in 1.21, so "did something" is consumesAction, not == SUCCESS.
        return stack.use(level, owner, InteractionHand.MAIN_HAND).getResult().consumesAction();
    }

    /** Places the item against the block at {@code pos + opposite(face)}, clicked on the given face. */
    public static boolean placeItemBlock(ItemStack stack, Level world, BlockPos pos, Direction face) {
        if (!(world instanceof ServerLevel level)) {
            return false;
        }
        Direction direction = face.getOpposite();
        Player owner = FakePlayers.get(level);
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos clicked = pos.relative(direction);
        Vec3 hitLocation = new Vec3(clicked.getX() + 0.25D, clicked.getY() + 0.25D, clicked.getZ() + 0.25D);
        UseOnContext context = new UseOnContext(level, owner, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(hitLocation, face, clicked, false));
        InteractionResult result = stack.useOn(context);
        return result.consumesAction();
    }

    public static void notifyBlockUpdate(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        world.sendBlockUpdated(pos, state, state, 3);
    }

    public static void notifyBlockUpdate(BlockEntity tile) {
        if (tile.getLevel() != null) {
            notifyBlockUpdate(tile.getLevel(), tile.getBlockPos());
        }
    }

    public static void notifyNeighbors(BlockEntity tile) {
        Level world = tile.getLevel();
        if (world == null) {
            return;
        }
        BlockPos pos = tile.getBlockPos();
        world.updateNeighborsAt(pos, world.getBlockState(pos).getBlock());
    }

    // ---- block state <-> text ----

    /** {@code {"name": "modid:block", "properties": {"prop": "value"}}}; the format {@code JsonHelper} reads. */
    public static JsonElement serializeToJson(BlockState state) {
        JsonObject serializedState = new JsonObject();
        serializedState.addProperty("name", BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());

        JsonObject serializedProps = new JsonObject();
        for (Map.Entry<Property<?>, Comparable<?>> prop : state.getValues().entrySet()) {
            serializedProps.addProperty(prop.getKey().getName(), serializeValue(prop.getKey(), prop.getValue()));
        }
        if (!serializedProps.entrySet().isEmpty()) {
            serializedState.add("properties", serializedProps);
        }
        return serializedState;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Comparable<T>> String serializeValue(Property<T> property, Comparable<?> value) {
        return property.getName((T) value);
    }

    /** Horizontal facing from an index into {@code Direction.values()}; vertical becomes north (was meta based). */
    public static Direction getHorizontalFacing(Direction facing) {
        return facing.getAxis() != Direction.Axis.Y ? facing : Direction.NORTH;
    }

    /** Adds one property value to the object being built (a block state, a matcher, ...). */
    public interface AddPropertyFunction<T> {
        T apply(T obj, Property<?> property, Comparable<?> value);
    }

    /**
     * Builds something from a block name and property strings: {@code init} creates it from the block, then every
     * property is parsed and handed to {@code addProperty}.
     *
     * @throws MissingResourceException if the block lacks a property or the value does not parse, or the block
     *                                  does not exist although its mod is loaded
     */
    public static <T> T getBlockState(Tuple<String, Map<String, String>> blockProps, Function<Block, T> init, AddPropertyFunction<T> addProperty) {
        String registryName = blockProps.getA();
        Map<String, String> properties = blockProps.getB();

        Block block = RegistryTools.getBlock(registryName);

        T ret = init.apply(block);
        StateDefinition<Block, BlockState> stateContainer = block.getStateDefinition();

        for (Map.Entry<String, String> prop : properties.entrySet()) {
            Property<?> property = stateContainer.getProperty(prop.getKey());

            if (property == null) {
                throw new MissingResourceException("Block \"" + BuiltInRegistries.BLOCK.getKey(block) + "\" doesn't have \"" + prop.getKey() + "\" property",
                        Property.class.getName(), prop.getKey());
            }

            ret = addProperty.apply(ret, property, getPropertyState(block, stateContainer, prop.getKey(), prop.getValue()).getValue());
        }

        return ret;
    }

    /** {@link AddPropertyFunction} for plain block states. */
    public static BlockState updateProperty(BlockState state, Property<?> property, Comparable<?> value) {
        return setUnchecked(state, property, value);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState setUnchecked(BlockState state, Property property, Comparable value) {
        return state.setValue(property, value);
    }

    /** The default state of the block with the named properties set. */
    public static BlockState getBlockState(String registryName, Map<String, String> properties) {
        return getBlockState(new Tuple<>(registryName, properties), Block::defaultBlockState, BlockTools::updateProperty);
    }

    @SuppressWarnings({"rawtypes", "java:S3740"})
    public static PropertyState getPropertyState(Block block, StateDefinition<Block, BlockState> stateContainer, String propName, String propValue) {
        Property<?> property = stateContainer.getProperty(propName);
        if (property == null) {
            throw new MissingResourceException("Block \"" + BuiltInRegistries.BLOCK.getKey(block) + "\" doesn't have \"" + propName + "\" property",
                    Property.class.getName(), propName);
        }
        return getPropertyState(property, propName, propValue);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>, V extends T> PropertyState<T, V> getPropertyState(Property<T> property, String propName, String propValue) {
        Optional<T> value = property.getValue(propValue);
        if (value.isEmpty()) {
            throw new MissingResourceException("Invalid value \"" + propValue + "\" for property \"" + propName + "\"", Property.class.getName(), propName);
        }
        return new PropertyState<>(property, (V) value.get());
    }

    /** Rotates every horizontal property of the state by the number of clockwise quarter turns. */
    public static BlockState rotateFacing(BlockState state, int turns) {
        return state.rotate(rotationForTurns(turns));
    }

    public static Rotation rotationForTurns(int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    // ---- terrain ----

    /**
     * The y of the top block of the column that is not air (and, if {@code skippables}, not skippable), or one
     * below the lowest block of the level if there is none (1.12.2: -1).
     */
    public static int getTopFilledHeight(Level world, int x, int z, boolean skippables, int maxY) {
        return getTopFilledHeight(world.getChunk(x >> 4, z >> 4), x, z, skippables, maxY);
    }

    public static int getTopFilledHeight(Level world, int x, int z, boolean skippables) {
        return getTopFilledHeight(world.getChunk(x >> 4, z >> 4), x, z, skippables);
    }

    public static int getTopFilledHeight(ChunkAccess chunk, int x, int z, boolean skippables) {
        return getTopFilledHeight(chunk, x, z, skippables, chunk.getHighestSectionPosition() + 15);
    }

    private static int getTopFilledHeight(ChunkAccess chunk, int x, int z, boolean skippables, int maxY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = Math.min(maxY, chunk.getMaxBuildHeight() - 1); y >= chunk.getMinBuildHeight(); y--) {
            BlockState state = chunk.getBlockState(pos.set(x, y, z));
            if (state.isAir() || (skippables && skippableTest.test(state))) {
                continue;
            }
            return y;
        }
        return chunk.getMinBuildHeight() - 1;
    }

    /** All positions of the box, starting at the top-north-west corner, X fastest, then Z, then downwards. */
    public static Iterable<BlockPos> getAllInBoxTopDown(BlockPos from, BlockPos to) {
        return getAllInBoxTopDown(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()),
                Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()), Math.max(from.getZ(), to.getZ()));
    }

    public static Iterable<BlockPos> getAllInBoxTopDown(final int x1, final int y1, final int z1, final int x2, final int y2, final int z2) {
        return () -> new AbstractIterator<BlockPos>() {
            private boolean first = true;
            private int lastPosX;
            private int lastPosY;
            private int lastPosZ;

            @Override
            protected BlockPos computeNext() {
                if (first) {
                    first = false;
                    lastPosX = x1;
                    lastPosY = y2;
                    lastPosZ = z1;
                    return new BlockPos(x1, y2, z1);
                } else if (lastPosX == x2 && lastPosY == y1 && lastPosZ == z2) {
                    return endOfData();
                } else {
                    if (lastPosX < x2) {
                        ++lastPosX;
                    } else if (lastPosZ < z2) {
                        lastPosX = x1;
                        ++lastPosZ;
                    } else if (lastPosY > y1) {
                        lastPosX = x1;
                        lastPosZ = z1;
                        --lastPosY;
                    }

                    return new BlockPos(lastPosX, lastPosY, lastPosZ);
                }
            }
        };
    }
}
