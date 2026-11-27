/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 2): the daily schedule.
 */
package io.github.capttracker.ancientwarfarenpc.settlement;

import io.github.capttracker.ancientwarfare.api.IInn;
import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.api.WorksiteRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The daily-life upkeep of a settlement, once a second from {@link SettlementData#serverTick}: finds the beds, campfires and bells inside the land
 * (from the block entities of the loaded chunks, every {@link #SCAN_INTERVAL} ticks), forgets beds that were broken, assigns free beds to members
 * without one (the nearest to the member's last known position), and rebuilds the gathering points.
 */
public final class SettlementLife {
    public static final int SCAN_INTERVAL = 100;

    private SettlementLife() {}

    public static void tick(ServerLevel level, Settlement settlement, boolean scan) {
        if (!SettlementConfig.scheduleEnabled) {
            return;
        }
        if (scan) {
            scanLoadedChunks(level, settlement);
        }
        validateBeds(level, settlement);
        io.github.capttracker.ancientwarfarenpc.settlement.family.FamilyLife.claimHomes(level, settlement); // the homes of the families come before the free beds are handed out
        assignBeds(settlement);
        rebuildGatherPoints(level, settlement);
    }

    private static boolean isBedFoot(BlockState state) {
        return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.FOOT;
    }

    /** Campfires and bells found by the last scan; they are checked again when the gathering points are built. */
    private static final Map<UUID, Set<BlockPos>> SOCIAL_BLOCKS = new java.util.HashMap<>();

    /** The bounding box of the land in blocks: {minX, minZ, maxX, maxZ}. */
    public static int[] landBounds(Settlement settlement) {
        int radius = SettlementConfig.defaultRadius;
        int minX = settlement.getHallPos().getX() - radius;
        int maxX = settlement.getHallPos().getX() + radius;
        int minZ = settlement.getHallPos().getZ() - radius;
        int maxZ = settlement.getHallPos().getZ() + radius;
        List<BlockPos> polygon = SettlementArea.polygon(settlement.getHallPos(), settlement.getMarkers());
        if (!polygon.isEmpty()) {
            minX = Integer.MAX_VALUE;
            minZ = Integer.MAX_VALUE;
            maxX = Integer.MIN_VALUE;
            maxZ = Integer.MIN_VALUE;
            for (BlockPos p : polygon) {
                minX = Math.min(minX, p.getX());
                maxX = Math.max(maxX, p.getX());
                minZ = Math.min(minZ, p.getZ());
                maxZ = Math.max(maxZ, p.getZ());
            }
        }
        return new int[] {minX, minZ, maxX, maxZ};
    }

    private static void scanLoadedChunks(ServerLevel level, Settlement settlement) {
        int[] bounds = landBounds(settlement);
        int minX = bounds[0];
        int minZ = bounds[1];
        int maxX = bounds[2];
        int maxZ = bounds[3];
        Set<BlockPos> social = new HashSet<>();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    BlockPos pos = be.getBlockPos();
                    if (be instanceof BedBlockEntity) {
                        if (isBedFoot(be.getBlockState()) && settlement.contains(pos)) {
                            settlement.addBed(pos);
                        }
                    } else if ((be instanceof CampfireBlockEntity || be instanceof BellBlockEntity) && settlement.contains(pos)) {
                        social.add(pos.immutable());
                    }
                }
            }
        }
        SOCIAL_BLOCKS.put(settlement.getId(), social);
    }

    /** A bed that is no longer a bed (its chunk is loaded) or is no longer inside the land is forgotten: its sleeper is homeless again. */
    private static void validateBeds(ServerLevel level, Settlement settlement) {
        for (BlockPos bed : new ArrayList<>(settlement.getBeds())) {
            if (!level.isLoaded(bed)) {
                continue;
            }
            if (!isBedFoot(level.getBlockState(bed)) || !settlement.contains(bed)) {
                settlement.removeBed(bed);
            }
        }
    }

    private static void assignBeds(Settlement settlement) {
        for (BlockPos bed : new ArrayList<>(settlement.getBeds())) {
            UUID owner = settlement.getBedOwner(bed);
            if (owner != null && !settlement.isMember(owner)) {
                settlement.freeBedOf(owner);
            }
        }
        // the beds a household claimed as its home are not given to others (rework phase 10)
        Set<BlockPos> claimed = SettlementConfig.familiesEnabled ? settlement.getFamilies().claimedBeds() : Set.of();
        List<BlockPos> free = new ArrayList<>();
        for (BlockPos bed : settlement.getBeds()) {
            if (settlement.getBedOwner(bed) == null && !claimed.contains(bed)) {
                free.add(bed);
            }
        }
        if (free.isEmpty()) {
            return;
        }
        for (Settlement.Member member : settlement.getMembers().values()) {
            if (free.isEmpty()) {
                return;
            }
            if (!settlement.isHomeless(member.id)) {
                continue;
            }
            BlockPos best = null;
            double bestDist = Double.MAX_VALUE;
            for (BlockPos bed : free) {
                double d = bed.distSqr(member.pos);
                if (d < bestDist) {
                    bestDist = d;
                    best = bed;
                }
            }
            if (best != null) {
                settlement.assignBed(best, member.id);
                free.remove(best);
            }
        }
    }

    private static void rebuildGatherPoints(ServerLevel level, Settlement settlement) {
        List<BlockPos> points = new ArrayList<>();
        points.add(settlement.getHallPos());
        Set<BlockPos> social = SOCIAL_BLOCKS.get(settlement.getId());
        if (social != null) {
            social.removeIf(pos -> {
                if (!level.isLoaded(pos)) {
                    return false;
                }
                BlockState state = level.getBlockState(pos);
                return !(state.is(net.minecraft.world.level.block.Blocks.BELL)
                        || state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT));
            });
            points.addAll(social.stream().sorted().toList());
        }
        for (Settlement.Member member : settlement.getMembers().values()) {
            if (member.type.startsWith("bard") && settlement.contains(member.pos)) {
                points.add(member.pos);
            }
        }
        // an inn with its innkeeper at it is a gathering point too (rework phase 5)
        for (BlockEntity be : WorksiteRegistry.inLevel(level, settlement::contains)) {
            if (be instanceof IInn inn && be instanceof IWorkSite site && JobBoard.mayUse(settlement, site) && inn.isOpen()) {
                points.add(inn.getStandPos());
            }
        }
        settlement.setGatherPoints(points);
    }

    /** The waypoints of a patrol along the border: the markers in angle order, or eight points of a circle inside the radius; the hall comes between. */
    public static List<BlockPos> patrolPoints(ServerLevel level, Settlement settlement) {
        List<BlockPos> points = new ArrayList<>();
        BlockPos hall = settlement.getHallPos();
        if (settlement.getPatrolWaypoints().size() >= 2) {
            return new ArrayList<>(settlement.getPatrolWaypoints()); // the route the guard captain was given with the command baton
        }
        List<BlockPos> polygon = SettlementArea.polygon(hall, settlement.getMarkers());
        if (!polygon.isEmpty()) {
            for (BlockPos marker : polygon) {
                points.add(marker);
                points.add(hall);
            }
        } else {
            double r = SettlementConfig.defaultRadius * 0.8;
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                int x = hall.getX() + (int) Math.round(Math.cos(a) * r);
                int z = hall.getZ() + (int) Math.round(Math.sin(a) * r);
                if (!level.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                points.add(new BlockPos(x, y, z));
                points.add(hall);
            }
        }
        if (points.isEmpty()) {
            points.add(hall);
        }
        return points;
    }

    /** True if the position has a roof two or more blocks above (leaves count) and is not rained on. */
    public static boolean isRoofed(ServerLevel level, BlockPos pos) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) >= pos.getY() + 2 && !level.isRainingAt(pos);
    }

    public static boolean isShelterSpot(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && isRoofed(level, pos)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    /**
     * A roofed spot inside the land near {@code from} (sampled within 16 blocks), preferring spots that are not close to {@code avoid} (the threat);
     * the hall if none is found.
     */
    public static BlockPos findShelter(ServerLevel level, Settlement settlement, BlockPos from, @javax.annotation.Nullable BlockPos avoid,
                                       net.minecraft.util.RandomSource random) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        if (settlement.contains(from) && isRoofed(level, from) && (avoid == null || from.distSqr(avoid) > 144)) {
            return from;
        }
        for (int i = 0; i < 48; i++) {
            BlockPos p = from.offset(random.nextInt(33) - 16, random.nextInt(9) - 4, random.nextInt(33) - 16);
            if (!settlement.contains(p) || !isShelterSpot(level, p)) {
                continue;
            }
            if (avoid != null && p.distSqr(avoid) < 144) {
                continue;
            }
            double d = p.distSqr(from);
            if (d < bestScore) {
                bestScore = d;
                best = p;
            }
        }
        return best != null ? best : settlement.getHallPos().above();
    }

    // ---------------------------------------------------------------- alarm bells (playtest fixes)

    /** The bells found by the last bell scan of a settlement (cached: the scan is repeated every {@link #SCAN_INTERVAL} ticks while the alarm is on). */
    private static final Map<UUID, Set<BlockPos>> BELLS = new java.util.HashMap<>();
    private static final Map<UUID, Long> BELL_SCANNED = new java.util.HashMap<>();
    private static final Map<UUID, Long> BELL_RUNG = new java.util.HashMap<>();

    /**
     * Called every few ticks: while the alarm of the settlement is on, rings every bell block inside the land in the loaded chunks, at once when the
     * alarm turns on and then every {@code bell_ring_interval_ticks}. The positions are cached and checked again before each ring.
     */
    public static void ringBells(ServerLevel level, Settlement settlement) {
        UUID id = settlement.getId();
        if (!settlement.isAlarmActive()) {
            BELL_RUNG.remove(id);
            BELLS.remove(id);
            BELL_SCANNED.remove(id);
            return;
        }
        long now = level.getGameTime();
        Long last = BELL_RUNG.get(id);
        if (last != null && now - last < SettlementConfig.bellRingIntervalTicks) {
            return;
        }
        Long scanned = BELL_SCANNED.get(id);
        if (scanned == null || now - scanned >= SCAN_INTERVAL || now < scanned) {
            scanBells(level, settlement);
            BELL_SCANNED.put(id, now);
        }
        BELL_RUNG.put(id, now);
        Set<BlockPos> bells = BELLS.get(id);
        if (bells == null) {
            return;
        }
        bells.removeIf(pos -> {
            if (!level.isLoaded(pos)) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof net.minecraft.world.level.block.BellBlock bell)) {
                return true;
            }
            bell.attemptToRing(level, pos, state.getValue(net.minecraft.world.level.block.BellBlock.FACING));
            return false;
        });
    }

    private static void scanBells(ServerLevel level, Settlement settlement) {
        int[] bounds = landBounds(settlement);
        Set<BlockPos> bells = new HashSet<>();
        for (int cx = bounds[0] >> 4; cx <= bounds[2] >> 4; cx++) {
            for (int cz = bounds[1] >> 4; cz <= bounds[3] >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    if (be instanceof BellBlockEntity && settlement.contains(be.getBlockPos())) {
                        bells.add(be.getBlockPos().immutable());
                    }
                }
            }
        }
        BELLS.put(settlement.getId(), bells);
    }

    /** Forgets the cached scan results of a settlement that is gone. */
    public static void forget(UUID settlementId) {
        SOCIAL_BLOCKS.remove(settlementId);
        BELLS.remove(settlementId);
        BELL_SCANNED.remove(settlementId);
        BELL_RUNG.remove(settlementId);
    }
}
