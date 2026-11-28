/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): the kitchens of a settlement, found as rooms, for the cook (Farmer's Delight).
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import io.github.capttracker.ancientwarfarenpc.compat.KitchenCompat;
import io.github.capttracker.ancientwarfarenpc.compat.ModCompat;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementLife;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The kitchens of a settlement: an enclosed room (roofed, with a door, see {@link RoomScanner}) of the settlement that holds a cooking pot standing over a heat
 * source, a cutting board, or both. There is no block to place for it; any building can be a kitchen, an inn, a diner or a house, by what is put in it. The
 * rooms are found again every few seconds ({@link #refresh}) and are not saved: after a restart the first refresh finds them again. The cook ({@code NpcAICook},
 * an innkeeper) works in them. Config: {@code farmers_delight.cook_enabled}, {@code hand_built_room_max_cells}.
 */
public final class KitchenSites {
    /** A kitchen: the bounding box of its room (blocks, inclusive) and the stations in it. */
    public record Kitchen(BlockPos min, BlockPos max, List<BlockPos> pots, List<BlockPos> boards) {}

    private static final int MAX_STATIONS_PER_REFRESH = 16;
    private static final int REFRESH_INTERVAL = 400;
    private static final Map<UUID, List<Kitchen>> FOUND = new HashMap<>();
    private static final Map<UUID, Long> NEXT_REFRESH = new HashMap<>();

    private KitchenSites() {}

    /** Whether the cook works at all: the integration is on and the other mod is installed. */
    public static boolean enabled() {
        return SettlementConfig.fdEnabled && SettlementConfig.fdCook && ModCompat.KITCHEN.isActive();
    }

    /** The kitchens found by the last refresh (never null). */
    public static List<Kitchen> of(Settlement settlement) {
        List<Kitchen> list = FOUND.get(settlement.getId());
        return enabled() && list != null ? list : List.of();
    }

    public static void forget(UUID settlementId) {
        FOUND.remove(settlementId);
        NEXT_REFRESH.remove(settlementId);
    }

    /** Finds the kitchens: for each station in the loaded chunks of the land, the room around it. Called every second; works every 20 seconds. */
    public static void refresh(ServerLevel level, Settlement settlement) {
        if (!enabled()) {
            forget(settlement.getId());
            return;
        }
        long now = level.getGameTime();
        Long next = NEXT_REFRESH.get(settlement.getId());
        if (next != null && now < next) {
            return;
        }
        NEXT_REFRESH.put(settlement.getId(), now + REFRESH_INTERVAL);
        KitchenCompat compat = ModCompat.KITCHEN.get();
        int[] bounds = SettlementLife.landBounds(settlement);
        List<BlockPos> stations = new ArrayList<>();
        Map<BlockPos, KitchenCompat.Kind> kinds = new HashMap<>();
        for (int cx = bounds[0] >> 4; cx <= bounds[2] >> 4; cx++) {
            for (int cz = bounds[1] >> 4; cz <= bounds[3] >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    KitchenCompat.Kind kind = compat.kindOf(be);
                    if (kind != null && settlement.contains(be.getBlockPos())) {
                        BlockPos pos = be.getBlockPos().immutable();
                        stations.add(pos);
                        kinds.put(pos, kind);
                    }
                }
            }
        }
        List<Kitchen> found = new ArrayList<>();
        if (!stations.isEmpty()) {
            stations.sort(Comparator.comparingLong(BlockPos::asLong));
            List<RoomScanner.Room> seen = new ArrayList<>();
            int scanned = 0;
            for (BlockPos station : stations) {
                boolean inSeen = false;
                for (RoomScanner.Room room : seen) {
                    if (room.touches(station)) {
                        inSeen = true;
                        break;
                    }
                }
                if (inSeen) {
                    continue;
                }
                if (scanned++ >= MAX_STATIONS_PER_REFRESH) {
                    break;
                }
                BlockPos start = RoomScanner.startNear(level, station);
                RoomScanner.Room room = start == null ? null : RoomScanner.scan(level, start, SettlementConfig.handBuiltRoomMaxCells);
                if (room == null) {
                    continue;
                }
                seen.add(room);
                List<BlockPos> pots = new ArrayList<>();
                List<BlockPos> boards = new ArrayList<>();
                for (BlockPos other : stations) {
                    if (!room.touches(other)) {
                        continue;
                    }
                    if (kinds.get(other) == KitchenCompat.Kind.POT) {
                        KitchenCompat.Pot state = compat.pot(level, other);
                        if (state != null && state.heated()) {
                            pots.add(other); // a pot with no heat source under it is no kitchen
                        }
                    } else {
                        boards.add(other);
                    }
                }
                if (!pots.isEmpty() || !boards.isEmpty()) {
                    found.add(new Kitchen(room.min(), room.max(), pots, boards));
                }
            }
        }
        if (found.isEmpty()) {
            FOUND.remove(settlement.getId());
        } else {
            FOUND.put(settlement.getId(), found);
        }
    }
}
