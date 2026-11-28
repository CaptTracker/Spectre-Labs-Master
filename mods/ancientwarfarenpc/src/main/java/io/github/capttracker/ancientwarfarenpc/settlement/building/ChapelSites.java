/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9, builders): a finished chapel is a place that lifts the morale of a devout NPC.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.trait.TraitHooks;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The chapels of a settlement for the devout trait ({@link TraitHooks}): a finished build whose design is of the category {@code chapel} (or has the tag
 * {@code chapel} or {@code devout}) counts as a place a devout NPC of the settlement can be near. The finished builds are saved with the settlement, so a
 * chapel counts again after a restart without any further registration.
 *
 * <p>A chapel that a player built by hand counts too: an enclosed room (roofed, with a door, see {@link RoomScanner}) of the settlement that holds a bell
 * and a lectern. The lectern is the altar and the bell calls the faithful; both are plain vanilla blocks, so any building can be made a chapel by putting
 * them inside. The rooms are found again every few seconds ({@link #refresh}) and are not saved: after a restart the first refresh finds them. The planner
 * does not plan a chapel while the settlement has one ({@link #hasHandBuilt}). Config: {@code hand_built_chapels}, {@code hand_built_room_max_cells}.
 */
public final class ChapelSites {
    /** A hand-built chapel: the bounding box of its room (blocks, inclusive). */
    private record HandChapel(BlockPos min, BlockPos max) {}

    private static final int MAX_BELLS_PER_REFRESH = 8;
    /** The hand-built chapels found by the last refresh of each settlement (server thread only). */
    private static final Map<UUID, List<HandChapel>> HAND_BUILT = new HashMap<>();
    /** The game time of the next refresh of each settlement: the chunks are walked at most every 20 seconds. */
    private static final Map<UUID, Long> NEXT_REFRESH = new HashMap<>();
    private static final int REFRESH_INTERVAL = 400;

    private ChapelSites() {}

    /** Whether the settlement has a hand-built chapel (as of the last refresh). */
    public static boolean hasHandBuilt(Settlement settlement) {
        List<HandChapel> list = HAND_BUILT.get(settlement.getId());
        return SettlementConfig.handBuiltChapels && list != null && !list.isEmpty();
    }

    /** Forgets what was found for a settlement that is gone. */
    public static void forget(UUID settlementId) {
        HAND_BUILT.remove(settlementId);
        NEXT_REFRESH.remove(settlementId);
    }

    /**
     * Finds the hand-built chapels of the settlement: for each bell inside the land (the loaded chunks), the room next to it; the room is a chapel when a
     * lectern is next to the room as well. Called every few seconds by the planner upkeep.
     */
    public static void refresh(ServerLevel level, Settlement settlement) {
        if (!SettlementConfig.handBuiltChapels) {
            HAND_BUILT.remove(settlement.getId());
            return;
        }
        long now = level.getGameTime();
        Long next = NEXT_REFRESH.get(settlement.getId());
        if (next != null && now < next) {
            return;
        }
        NEXT_REFRESH.put(settlement.getId(), now + REFRESH_INTERVAL);
        int[] bounds = io.github.capttracker.ancientwarfarenpc.settlement.SettlementLife.landBounds(settlement);
        List<BlockPos> bells = new ArrayList<>();
        List<BlockPos> lecterns = new ArrayList<>();
        for (int cx = bounds[0] >> 4; cx <= bounds[2] >> 4; cx++) {
            for (int cz = bounds[1] >> 4; cz <= bounds[3] >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    if (be instanceof BellBlockEntity && settlement.contains(pos)) {
                        bells.add(pos.immutable());
                    } else if (be instanceof LecternBlockEntity && settlement.contains(pos)) {
                        lecterns.add(pos.immutable());
                    }
                }
            }
        }
        List<HandChapel> found = new ArrayList<>();
        if (!bells.isEmpty() && !lecterns.isEmpty()) {
            bells.sort(java.util.Comparator.comparingLong(BlockPos::asLong));
            List<RoomScanner.Room> seen = new ArrayList<>();
            int scanned = 0;
            for (BlockPos bell : bells) {
                if (scanned >= MAX_BELLS_PER_REFRESH) {
                    break;
                }
                boolean inSeen = false;
                for (RoomScanner.Room room : seen) {
                    if (room.touches(bell)) {
                        inSeen = true;
                        break;
                    }
                }
                if (inSeen) {
                    continue;
                }
                scanned++;
                BlockPos start = RoomScanner.startNear(level, bell);
                RoomScanner.Room room = start == null ? null : RoomScanner.scan(level, start, SettlementConfig.handBuiltRoomMaxCells);
                if (room == null) {
                    continue;
                }
                seen.add(room);
                for (BlockPos lectern : lecterns) {
                    if (room.touches(lectern)) {
                        found.add(new HandChapel(room.min(), room.max()));
                        break;
                    }
                }
            }
        }
        if (found.isEmpty()) {
            HAND_BUILT.remove(settlement.getId());
        } else {
            HAND_BUILT.put(settlement.getId(), found);
        }
    }

    /** Registers the chapel place with the devout hook. Call once at start up. */
    public static void register() {
        TraitHooks.registerDevoutSite(ChapelSites::isNearChapel);
    }

    /** True if the design is a chapel. */
    public static boolean isChapel(BuildingDesign design) {
        return design.getCategory() == DesignCategory.CHAPEL || design.hasTag("chapel") || design.hasTag("devout");
    }

    private static boolean isNearChapel(NpcPlayerOwned npc, ServerLevel level, int radius) {
        Settlement settlement = npc.getSettlement().orElse(null);
        if (settlement == null || !settlement.getDimension().equals(level.dimension())) {
            return false;
        }
        List<HandChapel> handBuilt = SettlementConfig.handBuiltChapels ? HAND_BUILT.get(settlement.getId()) : null;
        if (handBuilt != null) {
            for (HandChapel chapel : handBuilt) {
                if (npc.getX() >= chapel.min().getX() - radius && npc.getX() <= chapel.max().getX() + 1 + radius
                        && npc.getZ() >= chapel.min().getZ() - radius && npc.getZ() <= chapel.max().getZ() + 1 + radius
                        && npc.getY() >= chapel.min().getY() - radius && npc.getY() <= chapel.max().getY() + 1 + radius) {
                    return true;
                }
            }
        }
        for (BuildProject project : settlement.getBuilding().getProjects()) {
            if (project.getKind() != BuildProject.Kind.BUILD || !project.isDone()) {
                continue;
            }
            BuildingDesign design = BuildJobs.designOf(project);
            if (design == null || !isChapel(design)) {
                continue;
            }
            int[] size = project.getSize();
            BlockPos origin = project.getOrigin();
            double cx = origin.getX() + size[0] / 2.0D;
            double cz = origin.getZ() + size[2] / 2.0D;
            double reach = radius + Math.max(size[0], size[2]) / 2.0D;
            double dx = npc.getX() - cx;
            double dz = npc.getZ() - cz;
            if (dx * dx + dz * dz <= reach * reach && Math.abs(npc.getY() - origin.getY()) <= radius) {
                return true;
            }
        }
        return false;
    }
}
