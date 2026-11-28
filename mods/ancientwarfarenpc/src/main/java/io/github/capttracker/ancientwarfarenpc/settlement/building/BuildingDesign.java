/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building designs.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A building design: a box of blocks that a builder can place, rotated to any of the four horizontal directions. See
 * {@code port-notes/REWORK-PHASE9.md} for the JSON format. A design is immutable once parsed.
 *
 * <p>Frame of a design (the "authored frame"): x grows east, z grows south, y grows up. Row 0 of a layer is the north edge, column 0 of a row is
 * the west edge, layer 0 is the bottom. {@link #getFront()} is the side of the entrance in that frame. Placing with a {@code facing} turns the
 * design clockwise (seen from above) until its front looks that way, and turns the block states with it ({@link BlockState#rotate}).
 */
public final class BuildingDesign {
    /** The hardest limit of any side of a design, whatever the config says. */
    public static final int MAX_DIMENSION = 64;
    /** Palette character: the block must be cleared to air. */
    public static final char AIR = '.';
    /** Palette character: leave whatever is there (the space counts as this too). */
    public static final char KEEP = '_';

    /**
     * The work area of a worksite block, as two corners (inclusive) in the authored frame, relative to the design origin (the lowest north-west corner).
     * The corners are inside the design box and sorted (min first).
     */
    public record Area(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    /** The worksite block inside a design and where it stands (authored frame), and optionally the work area to set on it when the build is finished. */
    public record Worksite(ResourceLocation block, int x, int y, int z, @Nullable Area area) {}

    /** One block of a placed design. {@code state} is air for a cell that must be cleared. */
    public record Placement(BlockPos pos, BlockState state) {}

    private final ResourceLocation id;
    private final String name;
    private final DesignCategory category;
    private final DesignStyle style;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final Direction front;
    private final Map<Character, String> paletteSpec;
    private final Map<Character, BlockState> palette;
    private final char[] cells;
    private final int beds;
    private final int storage;
    @Nullable
    private final Worksite worksite;
    private final List<String> tags;
    private final int minLevel;
    private final int minPopulation;
    @Nullable
    private final UUID owner;
    private final List<String> warnings;
    @Nullable
    private Map<BlockState, Integer> materials;

    private BuildingDesign(ResourceLocation id, String name, DesignCategory category, DesignStyle style, int sizeX, int sizeY, int sizeZ,
                           Direction front, Map<Character, String> paletteSpec, Map<Character, BlockState> palette, char[] cells, int beds,
                           int storage, @Nullable Worksite worksite, List<String> tags, int minLevel, int minPopulation, @Nullable UUID owner,
                           List<String> warnings) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.style = style;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.front = front;
        this.paletteSpec = paletteSpec;
        this.palette = palette;
        this.cells = cells;
        this.beds = beds;
        this.storage = storage;
        this.worksite = worksite;
        this.tags = tags;
        this.minLevel = minLevel;
        this.minPopulation = minPopulation;
        this.owner = owner;
        this.warnings = warnings;
    }

    // ---- accessors ----

    public ResourceLocation getId() { return id; }
    public String getName() { return name; }
    public DesignCategory getCategory() { return category; }
    public DesignStyle getStyle() { return style; }
    public int getSizeX() { return sizeX; }
    public int getSizeY() { return sizeY; }
    public int getSizeZ() { return sizeZ; }
    /** The side of the entrance in the authored frame. */
    public Direction getFront() { return front; }
    public int getBeds() { return beds; }
    public int getStorage() { return storage; }
    @Nullable
    public Worksite getWorksite() { return worksite; }
    public List<String> getTags() { return tags; }
    public int getMinLevel() { return minLevel; }
    public int getMinPopulation() { return minPopulation; }
    /** The player that owns this design (a player blueprint), or {@code null} for a built-in design. */
    @Nullable
    public UUID getOwner() { return owner; }
    public boolean isBuiltIn() { return owner == null; }
    /** Problems that did not make the design unusable (an unknown style, an unused palette character, and so on). */
    public List<String> getWarnings() { return warnings; }

    public boolean hasTag(String tag) {
        return tags.contains(tag.toLowerCase(Locale.ROOT));
    }

    /** The value of a {@code key=value} tag (for example {@code upgrade_of=ancientwarfarenpc:tent_small}), or {@code null}. */
    @Nullable
    public String tagValue(String key) {
        String prefix = key.toLowerCase(Locale.ROOT) + "=";
        for (String tag : tags) {
            if (tag.startsWith(prefix)) {
                return tag.substring(prefix.length());
            }
        }
        return null;
    }

    public DesignMeta meta() {
        return new DesignMeta(id, name, category, style, sizeX, sizeY, sizeZ, front, beds, storage,
                worksite == null ? "" : worksite.block().toString(), tags, isBuiltIn(), minLevel, minPopulation);
    }

    // ---- cells ----

    /** The palette character of a cell in the authored frame. */
    public char cellAt(int x, int y, int z) {
        return cells[(y * sizeZ + z) * sizeX + x];
    }

    /** The state at a cell in the authored frame: air to clear it, {@code null} to leave it as it is. */
    @Nullable
    public BlockState stateAt(int x, int y, int z) {
        char c = cellAt(x, y, z);
        if (c == KEEP) {
            return null;
        }
        if (c == AIR) {
            return Blocks.AIR.defaultBlockState();
        }
        return palette.get(c);
    }

    /** How many blocks of each state the design places (air and "leave as is" cells excluded). Used for the material list. */
    public Map<BlockState, Integer> materialCounts() {
        Map<BlockState, Integer> result = materials;
        if (result == null) {
            Map<BlockState, Integer> counts = new LinkedHashMap<>();
            for (char c : cells) {
                if (c != AIR && c != KEEP) {
                    BlockState state = palette.get(c);
                    if (state != null && !state.isAir()) {
                        counts.merge(state, 1, Integer::sum);
                    }
                }
            }
            result = Collections.unmodifiableMap(counts);
            materials = result;
        }
        return result;
    }

    // ---- rotation and placing ----

    /** Clockwise quarter turns (0 to 3) that bring the front to {@code facing}. A vertical facing counts as the front itself. */
    public static int quarterTurns(Direction front, Direction facing) {
        if (facing.getAxis().isVertical()) {
            return 0;
        }
        int turns = 0;
        Direction d = front;
        while (d != facing && turns < 4) {
            d = d.getClockWise();
            turns++;
        }
        return turns & 3;
    }

    public int quarterTurns(Direction facing) {
        return quarterTurns(front, facing);
    }

    public static Rotation rotationOf(int turns) {
        return switch (turns & 3) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** The size {@code {x, y, z}} of the placed design for a facing. */
    public int[] rotatedSize(Direction facing) {
        return rotatedSize(sizeX, sizeY, sizeZ, front, facing);
    }

    public static int[] rotatedSize(int sx, int sy, int sz, Direction front, Direction facing) {
        return (quarterTurns(front, facing) & 1) == 0 ? new int[]{sx, sy, sz} : new int[]{sz, sy, sx};
    }

    /** Turns a position of the authored frame (relative to the min corner) into the placed frame, for a number of clockwise quarter turns. */
    public BlockPos rotateLocal(int x, int y, int z, int turns) {
        return switch (turns & 3) {
            case 1 -> new BlockPos(sizeZ - 1 - z, y, x);
            case 2 -> new BlockPos(sizeX - 1 - x, y, sizeZ - 1 - z);
            case 3 -> new BlockPos(z, y, sizeX - 1 - x);
            default -> new BlockPos(x, y, z);
        };
    }

    /**
     * All blocks to place, bottom layer first, with {@code origin} as the lowest north-west corner of the placed box. Cells that are left as
     * they are are not listed; a cell to clear is listed with an air state.
     */
    public List<Placement> blocks(BlockPos origin, Direction facing) {
        int turns = quarterTurns(facing);
        Rotation rotation = rotationOf(turns);
        List<Placement> result = new ArrayList<>(cells.length);
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockState state = stateAt(x, y, z);
                    if (state != null) {
                        result.add(new Placement(origin.offset(rotateLocal(x, y, z, turns)), state.rotate(rotation)));
                    }
                }
            }
        }
        return result;
    }

    /** Where the worksite block stands when the design is placed, or {@code null} when it has none. */
    @Nullable
    public BlockPos worksitePos(BlockPos origin, Direction facing) {
        return worksite == null ? null : origin.offset(rotateLocal(worksite.x(), worksite.y(), worksite.z(), quarterTurns(facing)));
    }

    /**
     * The work area of the worksite when the design is placed: {@code {min, max}} (inclusive block positions, sorted), turned with the design; or
     * {@code null} when the design has no worksite or the worksite has no area.
     */
    @Nullable
    public BlockPos[] workArea(BlockPos origin, Direction facing) {
        if (worksite == null || worksite.area() == null) {
            return null;
        }
        Area a = worksite.area();
        int turns = quarterTurns(facing);
        BlockPos p = origin.offset(rotateLocal(a.minX(), a.minY(), a.minZ(), turns));
        BlockPos q = origin.offset(rotateLocal(a.maxX(), a.maxY(), a.maxZ(), turns));
        return new BlockPos[]{new BlockPos(Math.min(p.getX(), q.getX()), Math.min(p.getY(), q.getY()), Math.min(p.getZ(), q.getZ())),
                new BlockPos(Math.max(p.getX(), q.getX()), Math.max(p.getY(), q.getY()), Math.max(p.getZ(), q.getZ()))};
    }

    /** The ground level block just outside the middle of the front face of the placed design. */
    public BlockPos entranceOutside(BlockPos origin, Direction facing) {
        int[] size = rotatedSize(facing);
        Direction f = facing.getAxis().isVertical() ? front : facing;
        return switch (f) {
            case NORTH -> origin.offset(size[0] / 2, 0, -1);
            case SOUTH -> origin.offset(size[0] / 2, 0, size[2]);
            case WEST -> origin.offset(-1, 0, size[2] / 2);
            default -> origin.offset(size[0], 0, size[2] / 2);
        };
    }

    // ---- JSON ----

    /** Parses a design. {@code owner} is the owning player of a blueprint, {@code null} for a built-in design. Throws with a clear reason. */
    public static BuildingDesign parse(ResourceLocation id, JsonObject json, @Nullable UUID owner) throws DesignException {
        List<String> warnings = new ArrayList<>();
        String name = string(json, "name").trim();
        if (name.isEmpty()) {
            name = id.getPath();
            warnings.add("no \"name\", using the id");
        }
        DesignCategory category = DesignCategory.byId(string(json, "category"));
        if (category == null) {
            warnings.add("unknown or missing \"category\" '" + string(json, "category") + "', using \"other\"");
            category = DesignCategory.OTHER;
        }
        DesignStyle style = DesignStyle.byId(string(json, "style"));
        if (style == null) {
            warnings.add("unknown or missing \"style\" '" + string(json, "style") + "', using \"any\"");
            style = DesignStyle.ANY;
        }

        // size
        int[] size = new int[3];
        JsonElement sizeElement = json.get("size");
        if (sizeElement == null || !sizeElement.isJsonArray() || sizeElement.getAsJsonArray().size() != 3) {
            throw new DesignException("\"size\" must be an array of 3 numbers [x, y, z]");
        }
        for (int i = 0; i < 3; i++) {
            size[i] = intOf(sizeElement.getAsJsonArray().get(i), -1);
            if (size[i] < 1 || size[i] > MAX_DIMENSION) {
                throw new DesignException("\"size\" " + sizeElement + " has a side outside 1 to " + MAX_DIMENSION);
            }
        }
        int sx = size[0];
        int sy = size[1];
        int sz = size[2];

        // front
        Direction front = Direction.SOUTH;
        if (json.has("front")) {
            Direction parsed = Direction.byName(string(json, "front").toLowerCase(Locale.ROOT));
            if (parsed == null || parsed.getAxis().isVertical()) {
                throw new DesignException("\"front\" must be north, south, east or west, not '" + string(json, "front") + "'");
            }
            front = parsed;
        } else {
            warnings.add("no \"front\", using south");
        }

        // palette
        Map<Character, String> paletteSpec = new LinkedHashMap<>();
        Map<Character, BlockState> palette = new LinkedHashMap<>();
        if (!json.has("palette") || !json.get("palette").isJsonObject()) {
            throw new DesignException("\"palette\" must be an object of character to block state");
        }
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("palette").entrySet()) {
            String key = entry.getKey();
            if (key.length() != 1 || Character.isWhitespace(key.charAt(0))) {
                throw new DesignException("palette key '" + key + "' must be exactly one visible character");
            }
            char c = key.charAt(0);
            if (c == AIR || c == KEEP) {
                throw new DesignException("palette key '" + c + "' is reserved ('" + AIR + "' clears to air, '" + KEEP + "' leaves the block as it is)");
            }
            if (!entry.getValue().isJsonPrimitive()) {
                throw new DesignException("palette entry '" + c + "' must be a block state string");
            }
            String spec = entry.getValue().getAsString().trim();
            BlockState state;
            try {
                state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), spec, false).blockState();
            } catch (CommandSyntaxException | RuntimeException e) {
                throw new DesignException("palette entry '" + c + "' = \"" + spec + "\" is not a known block state (" + e.getMessage() + ")");
            }
            paletteSpec.put(c, spec);
            palette.put(c, state);
        }

        // layers
        if (!json.has("layers") || !json.get("layers").isJsonArray()) {
            throw new DesignException("\"layers\" must be an array of layers, each an array of rows (strings)");
        }
        JsonArray layers = json.getAsJsonArray("layers");
        if (layers.size() != sy) {
            throw new DesignException("\"size\" says " + sy + " layers but there are " + layers.size());
        }
        char[] cells = new char[sx * sy * sz];
        Set<Character> used = new LinkedHashSet<>();
        Set<Character> undefined = new LinkedHashSet<>();
        for (int y = 0; y < sy; y++) {
            if (!layers.get(y).isJsonArray()) {
                throw new DesignException("layer " + y + " must be an array of " + sz + " rows");
            }
            JsonArray rows = layers.get(y).getAsJsonArray();
            if (rows.size() != sz) {
                throw new DesignException("layer " + y + " has " + rows.size() + " rows, \"size\" says " + sz);
            }
            for (int z = 0; z < sz; z++) {
                String row = rows.get(z).isJsonPrimitive() ? rows.get(z).getAsString() : null;
                if (row == null || row.length() != sx) {
                    throw new DesignException("layer " + y + " row " + z + " has " + (row == null ? "no text" : row.length() + " characters")
                            + ", \"size\" says " + sx);
                }
                for (int x = 0; x < sx; x++) {
                    char c = row.charAt(x);
                    if (c == ' ') {
                        c = KEEP;
                    }
                    if (c != AIR && c != KEEP && !palette.containsKey(c)) {
                        undefined.add(c);
                    }
                    used.add(c);
                    cells[(y * sz + z) * sx + x] = c;
                }
            }
        }
        if (!undefined.isEmpty()) {
            throw new DesignException("the layers use characters that are not in the palette: " + undefined);
        }
        for (Character c : palette.keySet()) {
            if (!used.contains(c)) {
                warnings.add("palette character '" + c + "' is never used");
            }
        }

        // counts
        int beds = counted(json, "beds");
        int storage = counted(json, "storage");
        if (beds < 0 || storage < 0) {
            int bedCount = 0;
            int storageCount = 0;
            for (char c : cells) {
                BlockState state = c == AIR || c == KEEP ? null : palette.get(c);
                if (state == null) {
                    continue;
                }
                if (state.is(BlockTags.BEDS) && state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) == BedPart.FOOT) {
                    bedCount++;
                }
                Block block = state.getBlock();
                if (block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock) {
                    storageCount++;
                }
            }
            beds = beds < 0 ? bedCount : beds;
            storage = storage < 0 ? storageCount : storage;
        }

        // worksite
        Worksite worksite = null;
        if (json.has("worksite") && !json.get("worksite").isJsonNull()) {
            if (!json.get("worksite").isJsonObject()) {
                throw new DesignException("\"worksite\" must be an object {\"block\": id, \"pos\": [x, y, z]}");
            }
            JsonObject ws = json.getAsJsonObject("worksite");
            ResourceLocation block = ResourceLocation.tryParse(string(ws, "block"));
            if (block == null || !BuiltInRegistries.BLOCK.containsKey(block)) {
                throw new DesignException("\"worksite\".\"block\" '" + string(ws, "block") + "' is not a registered block");
            }
            JsonElement pos = ws.get("pos");
            if (pos == null || !pos.isJsonArray() || pos.getAsJsonArray().size() != 3) {
                throw new DesignException("\"worksite\".\"pos\" must be an array [x, y, z]");
            }
            int wx = intOf(pos.getAsJsonArray().get(0), -1);
            int wy = intOf(pos.getAsJsonArray().get(1), -1);
            int wz = intOf(pos.getAsJsonArray().get(2), -1);
            if (wx < 0 || wx >= sx || wy < 0 || wy >= sy || wz < 0 || wz >= sz) {
                throw new DesignException("\"worksite\".\"pos\" " + pos + " is outside the design " + sx + "x" + sy + "x" + sz);
            }
            char c = cells[(wy * sz + wz) * sx + wx];
            BlockState at = c == AIR || c == KEEP ? null : palette.get(c);
            if (at == null || !BuiltInRegistries.BLOCK.getKey(at.getBlock()).equals(block)) {
                warnings.add("the cell at the worksite position is not the block " + block);
            }
            Area area = null;
            if (ws.has("area") && !ws.get("area").isJsonNull()) {
                if (!ws.get("area").isJsonObject()) {
                    throw new DesignException("\"worksite\".\"area\" must be an object {\"min\": [x, y, z], \"max\": [x, y, z]}");
                }
                JsonObject areaJson = ws.getAsJsonObject("area");
                int[] lo = triple(areaJson.get("min"), "\"worksite\".\"area\".\"min\"");
                int[] hi = triple(areaJson.get("max"), "\"worksite\".\"area\".\"max\"");
                int[] limit = {sx, sy, sz};
                for (int i = 0; i < 3; i++) {
                    if (lo[i] < 0 || hi[i] < 0 || lo[i] >= limit[i] || hi[i] >= limit[i]) {
                        throw new DesignException("\"worksite\".\"area\" is outside the design " + sx + "x" + sy + "x" + sz);
                    }
                }
                area = new Area(Math.min(lo[0], hi[0]), Math.min(lo[1], hi[1]), Math.min(lo[2], hi[2]), Math.max(lo[0], hi[0]), Math.max(lo[1], hi[1]),
                        Math.max(lo[2], hi[2]));
            }
            worksite = new Worksite(block, wx, wy, wz, area);
        }

        // tags and minimums
        List<String> tags = new ArrayList<>();
        if (json.has("tags") && json.get("tags").isJsonArray()) {
            for (JsonElement tag : json.getAsJsonArray("tags")) {
                if (tag.isJsonPrimitive() && !tag.getAsString().isBlank()) {
                    tags.add(tag.getAsString().trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        int minLevel = Math.max(0, intOf(json.get("min_level"), 0));
        int minPopulation = Math.max(0, intOf(json.get("min_population"), 0));

        return new BuildingDesign(id, name, category, style, sx, sy, sz, front, Collections.unmodifiableMap(paletteSpec),
                Collections.unmodifiableMap(palette), cells, beds, storage, worksite, List.copyOf(tags), minLevel, minPopulation, owner,
                List.copyOf(warnings));
    }

    /** The design as JSON in the format {@link #parse} reads. */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("category", category.id());
        json.addProperty("style", style.id());
        JsonArray size = new JsonArray();
        size.add(sizeX);
        size.add(sizeY);
        size.add(sizeZ);
        json.add("size", size);
        json.addProperty("front", front.getName());
        JsonObject paletteJson = new JsonObject();
        for (Map.Entry<Character, String> entry : paletteSpec.entrySet()) {
            paletteJson.addProperty(String.valueOf(entry.getKey()), entry.getValue());
        }
        json.add("palette", paletteJson);
        JsonArray layers = new JsonArray();
        for (int y = 0; y < sizeY; y++) {
            JsonArray rows = new JsonArray();
            for (int z = 0; z < sizeZ; z++) {
                rows.add(new String(cells, (y * sizeZ + z) * sizeX, sizeX));
            }
            layers.add(rows);
        }
        json.add("layers", layers);
        json.addProperty("beds", beds);
        json.addProperty("storage", storage);
        if (worksite != null) {
            JsonObject ws = new JsonObject();
            ws.addProperty("block", worksite.block().toString());
            JsonArray pos = new JsonArray();
            pos.add(worksite.x());
            pos.add(worksite.y());
            pos.add(worksite.z());
            ws.add("pos", pos);
            if (worksite.area() != null) {
                Area a = worksite.area();
                JsonObject areaJson = new JsonObject();
                JsonArray lo = new JsonArray();
                lo.add(a.minX());
                lo.add(a.minY());
                lo.add(a.minZ());
                JsonArray hi = new JsonArray();
                hi.add(a.maxX());
                hi.add(a.maxY());
                hi.add(a.maxZ());
                areaJson.add("min", lo);
                areaJson.add("max", hi);
                ws.add("area", areaJson);
            }
            json.add("worksite", ws);
        }
        JsonArray tagArray = new JsonArray();
        tags.forEach(tagArray::add);
        json.add("tags", tagArray);
        if (minLevel > 0) {
            json.addProperty("min_level", minLevel);
        }
        if (minPopulation > 0) {
            json.addProperty("min_population", minPopulation);
        }
        return json;
    }

    private static int[] triple(@Nullable JsonElement element, String what) throws DesignException {
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 3) {
            throw new DesignException(what + " must be an array [x, y, z]");
        }
        int[] result = new int[3];
        for (int i = 0; i < 3; i++) {
            result[i] = intOf(element.getAsJsonArray().get(i), -1);
        }
        return result;
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static int intOf(@Nullable JsonElement element, int fallback) {
        if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt();
        }
        return fallback;
    }

    /** The value of an optional count, or -1 when absent (meaning: count it from the blocks). */
    private static int counted(JsonObject json, String key) {
        return Math.max(-1, intOf(json.get(key), -1));
    }
}
