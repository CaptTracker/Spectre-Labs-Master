/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building designs.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.capttracker.ancientwarfare.api.IWorksiteTasks;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads a box of the world into the design format. Only block states are saved: entities are ignored and block entities (chests, furnaces and
 * so on) are saved as an empty block. Air becomes the "clear" character; fluids become "leave as is".
 */
public final class BlueprintCapture {
    /** Characters for the palette: letters, digits, symbols and Latin-1 letters (never the reserved characters or the space). */
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#$%&*+-=@^!/:;<>?|~"
            + "ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕ"
            + "ÖØÙÚÛÜÝÞßàáâãäåæçèéêë"
            + "ìíîïðñòóôõöøùúûüýþÿ";

    private BlueprintCapture() {}

    /** What the scanner screen shows about a box: the bed and container counts and the worksite blocks found in it. */
    public record Info(int beds, int storage, List<String> worksiteBlocks) {}

    public static Info inspect(ServerLevel level, BlockPos min, BlockPos max) {
        int beds = 0;
        int storage = 0;
        Set<String> worksites = new LinkedHashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                    && state.getValue(net.minecraft.world.level.block.BedBlock.PART) == net.minecraft.world.level.block.state.properties.BedPart.FOOT) {
                beds++;
            }
            if (state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock || state.getBlock() instanceof net.minecraft.world.level.block.BarrelBlock
                    || state.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock) {
                storage++;
            }
            if (level.getBlockEntity(pos) instanceof IWorksiteTasks) {
                worksites.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            }
        }
        return new Info(beds, storage, new ArrayList<>(worksites));
    }

    /** Whether every chunk of the box is loaded. */
    public static boolean isLoaded(ServerLevel level, BlockPos min, BlockPos max) {
        return level.hasChunksAt(min, max);
    }

    /**
     * Captures the box into design JSON. {@code front} is the side of the entrance (horizontal). {@code worksiteBlock} is the id of the worksite
     * block to mark (the first block of that kind in the box), or null for none.
     */
    public static JsonObject capture(ServerLevel level, BlockPos min, BlockPos max, Direction front, String name, DesignCategory category,
                                     DesignStyle style, @Nullable ResourceLocation worksiteBlock) throws DesignException {
        int sx = max.getX() - min.getX() + 1;
        int sy = max.getY() - min.getY() + 1;
        int sz = max.getZ() - min.getZ() + 1;
        if (sx < 1 || sy < 1 || sz < 1 || sx > BuildingDesign.MAX_DIMENSION || sy > BuildingDesign.MAX_DIMENSION || sz > BuildingDesign.MAX_DIMENSION) {
            throw new DesignException("the area " + sx + "x" + sy + "x" + sz + " is too big");
        }
        Map<String, Character> palette = new LinkedHashMap<>();
        StringBuilder[][] rows = new StringBuilder[sy][sz];
        int[] worksitePos = null;
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                StringBuilder row = new StringBuilder(sx);
                rows[y][z] = row;
                for (int x = 0; x < sx; x++) {
                    BlockState state = level.getBlockState(min.offset(x, y, z));
                    char c;
                    if (state.isAir()) {
                        c = BuildingDesign.AIR;
                    } else if (state.getBlock() instanceof LiquidBlock || BuildMaterials.itemFor(state) == Items.AIR) {
                        // fluids, and blocks that have no item (fire, portals, potted plants): a builder would place them for free, so they are not copied
                        c = BuildingDesign.KEEP;
                    } else {
                        String key = BlockStateParser.serialize(state);
                        Character known = palette.get(key);
                        if (known == null) {
                            if (palette.size() >= ALPHABET.length()) {
                                throw new DesignException("the build has more than " + ALPHABET.length() + " different block states");
                            }
                            known = ALPHABET.charAt(palette.size());
                            palette.put(key, known);
                        }
                        c = known;
                        if (worksitePos == null && worksiteBlock != null && BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(worksiteBlock)) {
                            worksitePos = new int[]{x, y, z};
                        }
                    }
                    row.append(c);
                }
            }
        }
        if (worksiteBlock != null && worksitePos == null) {
            throw new DesignException("no " + worksiteBlock + " in the area");
        }
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("category", category.id());
        json.addProperty("style", style.id());
        JsonArray size = new JsonArray();
        size.add(sx);
        size.add(sy);
        size.add(sz);
        json.add("size", size);
        json.addProperty("front", front.getName());
        JsonObject paletteJson = new JsonObject();
        for (Map.Entry<String, Character> entry : palette.entrySet()) {
            paletteJson.addProperty(String.valueOf(entry.getValue()), entry.getKey());
        }
        json.add("palette", paletteJson);
        JsonArray layers = new JsonArray();
        for (int y = 0; y < sy; y++) {
            JsonArray layer = new JsonArray();
            for (int z = 0; z < sz; z++) {
                layer.add(rows[y][z].toString());
            }
            layers.add(layer);
        }
        json.add("layers", layers);
        if (worksitePos != null) {
            JsonObject ws = new JsonObject();
            ws.addProperty("block", worksiteBlock.toString());
            JsonArray pos = new JsonArray();
            pos.add(worksitePos[0]);
            pos.add(worksitePos[1]);
            pos.add(worksitePos[2]);
            ws.add("pos", pos);
            json.add("worksite", ws);
        }
        return json;
    }
}
