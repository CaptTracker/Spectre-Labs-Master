/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/WorldTools.
 */
package io.github.capttracker.ancientwarfare.util;

import io.github.capttracker.ancientwarfare.gamedata.AWGameData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Block entity, capability and per-dimension helpers.
 *
 * <p>Replaced by vanilla or NeoForge calls (use them directly in new code):
 * item handler of a tile, {@code level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)} (see
 * {@link #getItemHandlerFromTile}); saved data, {@code ServerLevel#getDataStorage}.
 *
 * <p>Not ported here: {@code clickInteractableTileWithHand}, which belongs with the {@code IInteractableTile}
 * interface of the interfaces slice.
 */
public final class WorldTools {
    private WorldTools() {}

    /** SERVER ONLY. Loads the chunks it needs, as the original did. */
    public static List<BlockEntity> getTileEntitiesInArea(Level world, int x1, int y1, int z1, int x2, int y2, int z2) {
        if (world instanceof ServerLevel) {
            List<BlockEntity> tileEntities = new ArrayList<>();
            BlockPos min = new BlockPos(x1, y1, z1);
            BlockPos max = new BlockPos(x2, y2, z2);
            for (int x = (x1 >> 4); x <= (x2 >> 4); x++) {
                for (int z = (z1 >> 4); z <= (z2 >> 4); z++) {
                    addValidTilesInChunkArea(world, min, max, tileEntities, x, z);
                }
            }
            return tileEntities;
        }
        return Collections.emptyList();
    }

    private static void addValidTilesInChunkArea(Level world, BlockPos min, BlockPos max, List<BlockEntity> tileEntities, int x, int z) {
        LevelChunk chunk = world.getChunk(x, z);
        for (BlockEntity tile : chunk.getBlockEntities().values()) {
            if (!tile.isRemoved() && isTileInArea(min, max, tile)) {
                tileEntities.add(tile);
            }
        }
    }

    private static boolean isTileInArea(BlockPos min, BlockPos max, BlockEntity tile) {
        BlockPos tilePos = tile.getBlockPos();
        return tilePos.getX() >= min.getX() && tilePos.getY() >= min.getY() && tilePos.getZ() >= min.getZ()
                && tilePos.getX() <= max.getX() && tilePos.getY() <= max.getY() && tilePos.getZ() <= max.getZ();
    }

    /** The item handler the block at the position exposes on the given side (null = unsided), if any. */
    public static Optional<IItemHandler> getItemHandlerFromTile(Level world, BlockPos pos, @Nullable Direction side) {
        return Optional.ofNullable(world.getCapability(Capabilities.ItemHandler.BLOCK, pos, side));
    }

    /** Sends a block entity event ({@code BlockEntity#triggerEvent}) to the block entity, if there is one. */
    public static boolean sendClientEventToTile(BlockGetter world, BlockPos pos, int id, int param) {
        return getTile(world, pos).map(t -> t.triggerEvent(id, param)).orElse(false);
    }

    public static Optional<BlockEntity> getTile(BlockGetter world, BlockPos pos) {
        return getTile(world, pos, BlockEntity.class);
    }

    public static <T> Optional<T> getTile(@Nullable BlockGetter world, @Nullable BlockPos pos, Class<T> teClass) {
        if (world == null || pos == null) {
            return Optional.empty();
        }

        BlockEntity te = world.getBlockEntity(pos);

        if (teClass.isInstance(te)) {
            return Optional.of(teClass.cast(te));
        }

        return Optional.empty();
    }

    private static final Map<ResourceKey<Level>, Predicate<Level>> DIMENSION_DAY_TIMES = new HashMap<>();

    /** Lets a dimension (for example Twilight Forest) define its own idea of "daytime". */
    public static void registerDimensionDaytimeLogic(ResourceKey<Level> dimension, Predicate<Level> isDayTime) {
        DIMENSION_DAY_TIMES.put(dimension, isDayTime);
    }

    public static boolean isDaytimeInDimension(Level world) {
        return DIMENSION_DAY_TIMES.getOrDefault(world.dimension(), Level::isDay).test(world);
    }

    public static boolean hasItemHandler(Level world, BlockPos pos) {
        return world.getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null;
    }

    /**
     * Saved data of the given factory and name: per dimension if {@code perWorldStorage}, otherwise one copy
     * saved with the overworld. Empty on the client. Prefer {@link AWGameData} in new code.
     */
    public static <T extends SavedData> Optional<T> getWorldSavedData(Level world, SavedData.Factory<T> factory, String name, boolean perWorldStorage) {
        if (!(world instanceof ServerLevel level)) {
            return Optional.empty();
        }
        return Optional.of(perWorldStorage
                ? AWGameData.INSTANCE.getPerLevelData(level, factory, name)
                : AWGameData.INSTANCE.getData(level.getServer(), factory, name));
    }

    /**
     * Changes the biome at the position. Biomes are stored per 4x4x4 cell in 1.21, so the whole column cell
     * (4x4 blocks, all heights) changes, not a single block column as in 1.12.2. Clients are sent the new biomes.
     */
    public static void changeBiome(ServerLevel world, BlockPos pos, Holder<Biome> biome) {
        ChunkAccess chunk = world.getChunk(pos);
        int quartX = QuartPos.fromBlock(pos.getX());
        int quartZ = QuartPos.fromBlock(pos.getZ());
        chunk.fillBiomesFromNoise(
                (x, y, z, sampler) -> x == quartX && z == quartZ ? biome : chunk.getNoiseBiome(x, y, z),
                world.getChunkSource().randomState().sampler());
        chunk.setUnsaved(true);
        world.getChunkSource().chunkMap.resendBiomesForChunks(List.of(chunk));
    }
}
