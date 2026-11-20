/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): building a caravan in the world.
 */
package io.github.capttracker.ancientwarfarenpc.caravan;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.api.IMarketStall;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionTrader;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcs;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.trade.FactionTradeList;
import io.github.capttracker.ancientwarfarenpc.trade.MarketApi;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.hitch.CartHitch;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleType;
import io.github.capttracker.ancientwarfarevehicle.registry.VehicleTypeIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Builds a caravan: a faction trader leading a horse that is hitched to a wagon (the phase 4 hitch API), and the guards. It appears 64 to 96 blocks from the
 * town hall (config) on loaded ground near a player, and starts walking to the settlement.
 */
public final class CaravanSpawner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int HOME_RANGE = 200;
    private static final int SPAWN_TRIES = 24;
    private static boolean warnedNoWagon = false;

    private CaravanSpawner() {}

    /**
     * Tries to bring a caravan of the faction to the settlement. Returns false if no place was found (the scheduler tries again later) or the
     * caravan could not be built.
     */
    public static boolean spawn(ServerLevel level, Settlement settlement, String faction) {
        RandomSource random = level.getRandom();
        BlockPos hall = settlement.getHallPos();
        Holder<Biome> biome = level.getBiome(hall);
        var candidates = CaravanStock.candidates(faction, CaravanStock.biomeWords(biome));
        var chosen = CaravanStock.draw(candidates, 2, random);
        if (chosen.isEmpty()) {
            return false;
        }
        FactionTradeList trades = CaravanStock.buildTradeList(chosen, level.registryAccess());
        if (trades.isEmpty()) {
            return false;
        }
        Place place = findPlace(level, hall, random);
        if (place == null) {
            return false;
        }

        // the wagon, the horse
        Optional<VehicleBase> wagonOpt = VehicleType.getVehicleForType(level, VehicleTypeIds.WAGON, 0);
        if (wagonOpt.isEmpty()) {
            wagonOpt = VehicleType.getVehicleForType(level, VehicleTypeIds.CART, 0);
        }
        if (wagonOpt.isEmpty() || !wagonOpt.get().vehicleType.isHorseDrawn()) {
            if (!warnedNoWagon) {
                warnedNoWagon = true;
                LOGGER.warn("No caravans can come: the wagon and the cart are both disabled in the vehicle config");
            }
            return false;
        }
        VehicleBase wagon = wagonOpt.get();
        double hitchReach = wagon.vehicleType.getHitchOffset() + CartHitch.HORSE_REAR;
        Horse horse = EntityType.HORSE.create(level);
        NpcBase traderBase = AwNpcs.createNpc(level, "faction.trader", "", faction);
        if (horse == null || !(traderBase instanceof NpcFactionTrader trader)) {
            return false;
        }

        double dx = place.dir.x;
        double dz = place.dir.z;
        double sideX = -dz;
        double sideZ = dx;
        float horseYaw = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
        float wagonYaw = 180.0F - horseYaw;
        wagon.setPos(place.pos.x, place.pos.y, place.pos.z);
        wagon.setYRot(wagonYaw);
        wagon.yRotO = wagonYaw;
        wagon.localTurretDestRot = wagon.localTurretRotation = wagon.localTurretRotationHome = wagonYaw;
        Vec3 horsePos = groundAt(level, place.pos.x + dx * hitchReach, place.pos.z + dz * hitchReach, place.pos.y);
        Vec3 traderPos = groundAt(level, place.pos.x + dx * (hitchReach + 2.8D), place.pos.z + dz * (hitchReach + 2.8D), place.pos.y);
        if (horsePos == null || traderPos == null) {
            return false;
        }
        horse.moveTo(horsePos.x, horsePos.y, horsePos.z, horseYaw, 0.0F);
        horse.setYBodyRot(horseYaw);
        horse.setYHeadRot(horseYaw);
        horse.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(horsePos)), MobSpawnType.EVENT, null);
        horse.setTamed(true);
        horse.setPersistenceRequired();

        trader.moveTo(traderPos.x, traderPos.y, traderPos.z, horseYaw, 0.0F);
        trader.setHomePosAndDistance(hall, HOME_RANGE);
        trader.setYBodyRot(horseYaw);
        trader.setYHeadRot(horseYaw);
        FactionTradeList list = trader.getTradeList();
        for (var trade : trades.getTrades()) {
            list.add(trade.copy());
        }
        CaravanStock.stockCargo(wagon.inventory.storageInventory, trades);
        if (SettlementConfig.caravanTailorBuyOrders) {
            tailorCargo(level, settlement, wagon.inventory.storageInventory, CaravanStock.suppliable(candidates, level.registryAccess()));
        }

        long now = level.getGameTime();
        BlockPos target = tradingPlace(level, settlement, place.pos);
        CaravanState state = new CaravanState(settlement.getId(), hall, target, BlockPos.containing(place.pos), biome.unwrapKey().map(k -> k.location().toString()).orElse(""), now);
        state.wagon = wagon.getUUID();
        state.horses.add(horse.getUUID());

        List<NpcFaction> guards = new ArrayList<>();
        for (int i = 0; i < SettlementConfig.caravanGuards; i++) {
            NpcBase guardBase = AwNpcs.createNpc(level, "faction.soldier", "", faction);
            if (!(guardBase instanceof NpcFaction guard)) {
                continue;
            }
            double side = (i % 2 == 0 ? 1.0D : -1.0D) * (1.8D + (i / 2) * 1.2D);
            Vec3 guardPos = groundAt(level, place.pos.x + dx * (hitchReach + 1.0D) + sideX * side, place.pos.z + dz * (hitchReach + 1.0D) + sideZ * side, place.pos.y);
            if (guardPos == null) {
                guardPos = traderPos;
            }
            guard.moveTo(guardPos.x, guardPos.y, guardPos.z, horseYaw, 0.0F);
            guard.setHomePosAndDistance(hall, HOME_RANGE);
            guard.setCaravanLeader(trader.getUUID());
            guards.add(guard);
            state.guards.add(guard.getUUID());
        }

        trader.setCaravan(state);
        level.addFreshEntity(wagon);
        level.addFreshEntity(horse);
        level.addFreshEntity(trader);
        for (NpcFaction guard : guards) {
            level.addFreshEntity(guard);
        }
        CartHitch.Result hitched = wagon.hitch.hitch(horse, false);
        if (hitched != CartHitch.Result.OK) {
            LOGGER.warn("A caravan wagon could not hitch its horse: {}", hitched);
        }
        wagon.hitch.setLeader(trader, 3.0D);
        CaravanData.get(level.getServer()).setActive(settlement.getId(), trader.getUUID(), now);
        return true;
    }

    // ---------------------------------------------------------------- cargo for buy orders

    /** Adds goods for the buy orders of the settlement's stalls (up to a stack each), for the items that the faction and the land can supply. */
    private static void tailorCargo(ServerLevel level, Settlement settlement, IItemHandler cargo, Set<Item> suppliable) {
        for (MarketApi.PostedOrder posted : MarketApi.listBuyOrders(level, settlement)) {
            var item = posted.order().item();
            if (item.isEmpty() || !suppliable.contains(item.getItem())) {
                continue;
            }
            int wanted = Math.max(1, posted.order().deals()) * item.getCount();
            if (posted.order().remaining() >= 0) {
                wanted = Math.min(wanted, posted.order().remaining());
            }
            wanted = Math.min(wanted, item.getMaxStackSize() * 2);
            while (wanted > 0) {
                int part = Math.min(wanted, item.getMaxStackSize());
                ItemHandlerHelper.insertItem(cargo, item.copyWithCount(part), false);
                wanted -= part;
            }
        }
    }

    // ---------------------------------------------------------------- places

    /** Where the caravan appears: the middle of the wagon, and the direction it faces (towards the hall). */
    private record Place(Vec3 pos, Vec3 dir) {}

    @Nullable
    private static Place findPlace(ServerLevel level, BlockPos hall, RandomSource random) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator()) {
                players.add(player);
            }
        }
        if (players.isEmpty()) {
            return null;
        }
        double range = SettlementConfig.caravanPlayerRange;
        for (int attempt = 0; attempt < SPAWN_TRIES; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = SettlementConfig.caravanSpawnMin + random.nextDouble() * (SettlementConfig.caravanSpawnMax - SettlementConfig.caravanSpawnMin);
            double x = hall.getX() + 0.5D + Math.cos(angle) * distance;
            double z = hall.getZ() + 0.5D + Math.sin(angle) * distance;
            boolean near = false;
            boolean tooClose = false;
            for (ServerPlayer player : players) {
                double ddx = player.getX() - x;
                double ddz = player.getZ() - z;
                double sq = ddx * ddx + ddz * ddz;
                near |= sq <= range * range;
                tooClose |= sq < 24.0D * 24.0D;
            }
            if (!near || tooClose) {
                continue;
            }
            Vec3 dir = new Vec3(hall.getX() + 0.5D - x, 0.0D, hall.getZ() + 0.5D - z).normalize();
            Vec3 ground = groundAt(level, x, z, Double.NaN);
            if (ground == null) {
                continue;
            }
            // the road ahead must be loaded and level enough for the wagon, the horse and the trader
            boolean ok = true;
            for (double ahead = 3.0D; ahead <= 12.0D && ok; ahead += 3.0D) {
                Vec3 step = groundAt(level, x + dir.x * ahead, z + dir.z * ahead, ground.y);
                ok = step != null;
            }
            if (ok) {
                return new Place(ground, dir);
            }
        }
        return null;
    }

    /**
     * The standing place at the column: on the surface, not in or over a fluid, in a chunk that entities tick in, and within 2 blocks of the given height
     * (NaN for any). Null if the place does not do.
     */
    @Nullable
    private static Vec3 groundAt(ServerLevel level, double x, double z, double nearY) {
        BlockPos column = BlockPos.containing(x, 0, z);
        if (!level.isLoaded(column) || !level.isPositionEntityTicking(column)) {
            return null;
        }
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        BlockPos below = surface.below();
        if (!level.getFluidState(surface).isEmpty() || !level.getFluidState(below).isEmpty() || level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            return null;
        }
        if (!level.getBlockState(surface).getCollisionShape(level, surface).isEmpty() || !level.getBlockState(surface.above()).getCollisionShape(level, surface.above()).isEmpty()) {
            return null;
        }
        if (!Double.isNaN(nearY) && Math.abs(surface.getY() - nearY) > 2.0D) {
            return null;
        }
        return new Vec3(x, surface.getY(), z);
    }

    /** Where the trader stands to sell: in front of the first stall, else a few blocks from the hall on the side the caravan came from. */
    private static BlockPos tradingPlace(ServerLevel level, Settlement settlement, Vec3 from) {
        List<IMarketStall> stalls = MarketApi.stallsOf(level, settlement);
        if (!stalls.isEmpty()) {
            return stalls.get(0).getStandPos();
        }
        BlockPos hall = settlement.getHallPos();
        Vec3 out = new Vec3(from.x - hall.getX() - 0.5D, 0.0D, from.z - hall.getZ() - 0.5D).normalize();
        BlockPos column = BlockPos.containing(hall.getX() + 0.5D + out.x * 5.0D, hall.getY(), hall.getZ() + 0.5D + out.z * 5.0D);
        if (level.isLoaded(column)) {
            return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
        }
        return hall;
    }
}
