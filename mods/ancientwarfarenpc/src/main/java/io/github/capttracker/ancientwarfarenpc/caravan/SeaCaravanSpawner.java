/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): building a faction trade ship in the world.
 */
package io.github.capttracker.ancientwarfarenpc.caravan;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.api.ISeaDock;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionTrader;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcs;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.trade.FactionTradeList;
import io.github.capttracker.ancientwarfarenpc.trade.SeaDocks;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipRoutePlanner;
import io.github.capttracker.ancientwarfarevehicle.entity.types.VehicleType;
import io.github.capttracker.ancientwarfarevehicle.registry.VehicleTypeIds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds a faction trade ship (rework phase 11): a cog with a faction helmsman at the helm, rowers and guards (faction soldiers) on its benches and in its gun
 * and passenger seats, and a faction trader who trades from the deck. It appears out at sea, on loaded water near a player but out of his sight, at least
 * {@code sea_caravan_min_route} blocks of water route from a dock of the settlement that has deep water access ({@link SeaDocks#mooringOf}), and sails there
 * ({@link SeaCaravanLogic}). The stock, the trade view, the buy orders and the kill rules are those of the caravans by land ({@link CaravanSpawner},
 * {@link CaravanLogic}).
 */
public final class SeaCaravanSpawner {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int HOME_RANGE = 200;
    private static final int SPAWN_TRIES = 12;
    /** The spawn place is at least this far from every player (blocks), so that the ship comes from beyond view. */
    private static final double MIN_PLAYER_DISTANCE = 48.0D;
    private static boolean warnedNoShip = false;

    private SeaCaravanSpawner() {}

    /** True if a dock of the settlement has water a trade ship can moor in ({@code sea_caravan_min_depth} deep, a 5 x 5 hull fits). The docks must be loaded. */
    public static boolean hasSeaAccess(ServerLevel level, Settlement settlement) {
        for (ISeaDock dock : SeaDocks.docksOf(level, settlement)) {
            if (SeaDocks.mooringOf(level, dock, SeaDocks.COG_CLEARANCE, SettlementConfig.seaCaravanMinDepth) != null) {
                return true;
            }
        }
        return false;
    }

    /** A place at sea and the planned route from it to the mooring. */
    private record Place(Vec3 pos, ShipRoutePlanner.Plan plan) {}

    /**
     * Tries to bring a trade ship of the faction to a dock of the settlement. Returns false if no place was found (the scheduler tries again later) or the ship
     * could not be built.
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
        Optional<VehicleBase> shipOpt = VehicleType.getVehicleForType(level, VehicleTypeIds.COG, 0);
        if (shipOpt.isEmpty() || !shipOpt.get().isShip()) {
            if (!warnedNoShip) {
                warnedNoShip = true;
                LOGGER.warn("No trade ships can come: the cog is disabled in the vehicle config");
            }
            return false;
        }
        ISeaDock dock = null;
        BlockPos mooring = null;
        Place place = null;
        for (ISeaDock candidate : SeaDocks.docksOf(level, settlement)) {
            BlockPos found = SeaDocks.mooringOf(level, candidate, SeaDocks.COG_CLEARANCE, SettlementConfig.seaCaravanMinDepth);
            if (found == null) {
                continue;
            }
            place = findPlace(level, found, random);
            if (place != null) {
                dock = candidate;
                mooring = found;
                break;
            }
        }
        if (dock == null || place == null) {
            return false;
        }

        VehicleBase ship = shipOpt.get();
        // the faction's name, for the prize rule (a player takes the ship when no crew is left alive aboard)
        ship.getPersistentData().putString(io.github.capttracker.ancientwarfarenpc.event.ShipPrizeEvents.FACTION_TAG, faction);
        NpcBase traderBase = AwNpcs.createNpc(level, "faction.trader", "", faction);
        NpcBase helmBase = AwNpcs.createNpc(level, "faction.soldier", "", faction);
        if (!(traderBase instanceof NpcFactionTrader trader) || !(helmBase instanceof NpcFaction helm)) {
            return false;
        }
        double dx = mooring.getX() + 0.5D - place.pos.x;
        double dz = mooring.getZ() + 0.5D - place.pos.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, -dz));
        ship.setPos(place.pos.x, place.pos.y, place.pos.z);
        ship.setYRot(yaw);
        ship.yRotO = yaw;
        ship.localTurretDestRot = ship.localTurretRotation = ship.localTurretRotationHome = yaw;

        long now = level.getGameTime();
        CaravanState state = new CaravanState(settlement.getId(), hall, mooring, BlockPos.containing(place.pos), biome.unwrapKey().map(k -> k.location().toString()).orElse(""), now);
        state.ship = true;
        state.dock = dock.getDockPos();
        state.wagon = ship.getUUID();
        // the hold starts empty: goods for buy orders are made only when a trade is paid (CaravanLogic.fillBuyOrders), so a sunk ship has no free goods to drop
        state.virtualCargo = true;
        state.supply.addAll(CaravanStock.supplyOf(trades));
        if (SettlementConfig.caravanTailorBuyOrders) {
            for (Item item : CaravanStock.suppliable(candidates, level.registryAccess())) {
                state.supply.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
            }
        }

        trader.moveTo(place.pos.x, place.pos.y + 1.0D, place.pos.z, yaw, 0.0F);
        trader.setHomePosAndDistance(hall, HOME_RANGE);
        FactionTradeList list = trader.getTradeList();
        for (var trade : trades.getTrades()) {
            list.add(trade.copy());
        }
        helm.moveTo(place.pos.x, place.pos.y + 1.0D, place.pos.z, yaw, 0.0F);
        helm.setHomePosAndDistance(hall, HOME_RANGE);
        helm.setCaravanLeader(trader.getUUID());
        state.helmsman = helm.getUUID();
        state.guards.add(helm.getUUID());

        // rowers on the benches, guards at the gun and in the passenger seats
        List<NpcFaction> crew = new ArrayList<>();
        List<SeatRole[]> crewRoles = new ArrayList<>();
        for (int i = 0; i < SettlementConfig.seaCaravanRowers + SettlementConfig.seaCaravanGuards; i++) {
            NpcBase crewBase = AwNpcs.createNpc(level, "faction.soldier", "", faction);
            if (!(crewBase instanceof NpcFaction member)) {
                continue;
            }
            member.moveTo(place.pos.x, place.pos.y + 1.0D, place.pos.z, yaw, 0.0F);
            member.setHomePosAndDistance(hall, HOME_RANGE);
            member.setCaravanLeader(trader.getUUID());
            crew.add(member);
            crewRoles.add(i < SettlementConfig.seaCaravanRowers ? new SeatRole[] {SeatRole.ROWER, SeatRole.PASSENGER}
                    : new SeatRole[] {SeatRole.GUNNER, SeatRole.PASSENGER, SeatRole.ROWER});
        }

        trader.setCaravan(state);
        if (!level.addFreshEntity(ship)) {
            return false;
        }
        level.addFreshEntity(trader);
        level.addFreshEntity(helm);
        for (NpcFaction member : crew) {
            level.addFreshEntity(member);
        }
        if (!ship.boardStrict(helm, SeatRole.DRIVER) || !ship.boardStrict(trader, SeatRole.PASSENGER)) {
            LOGGER.warn("A trade ship could not seat its helmsman and trader; it is taken back");
            discardAll(ship, trader, helm, crew);
            return true; // dropped, not retried at once: the scheduler's request is used up
        }
        for (int i = 0; i < crew.size(); i++) {
            NpcFaction member = crew.get(i);
            boolean seated = false;
            for (SeatRole role : crewRoles.get(i)) {
                if (ship.boardStrict(member, role)) {
                    seated = true;
                    break;
                }
            }
            if (seated) {
                state.guards.add(member.getUUID());
            } else {
                member.discard();
            }
        }
        CaravanData.get(level.getServer()).setSeaActive(settlement.getId(), trader.getUUID(), now);
        return true;
    }

    private static void discardAll(VehicleBase ship, NpcFactionTrader trader, NpcFaction helm, List<NpcFaction> crew) {
        for (NpcFaction member : crew) {
            member.discard();
        }
        helm.discard();
        trader.setCaravan(null);
        trader.discard();
        ship.discard();
    }

    // ---------------------------------------------------------------- places

    /**
     * The place at sea where the ship appears: {@code sea_caravan_min_route} to 48 blocks more from the mooring, on loaded water where a cog floats, near a player
     * (within {@code caravan_player_range}) but at least 48 blocks from every player, in a chunk where entities tick, with a route over the water to the mooring that
     * is at least {@code sea_caravan_min_route} blocks long.
     */
    @Nullable
    private static Place findPlace(ServerLevel level, BlockPos mooring, RandomSource random) {
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
        int refY = mooring.getY();
        for (int attempt = 0; attempt < SPAWN_TRIES; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = SettlementConfig.seaCaravanMinRoute + random.nextDouble() * 48.0D;
            double x = mooring.getX() + 0.5D + Math.cos(angle) * distance;
            double z = mooring.getZ() + 0.5D + Math.sin(angle) * distance;
            boolean near = false;
            boolean tooClose = false;
            for (ServerPlayer player : players) {
                double ddx = player.getX() - x;
                double ddz = player.getZ() - z;
                double sq = ddx * ddx + ddz * ddz;
                near |= sq <= range * range;
                tooClose |= sq < MIN_PLAYER_DISTANCE * MIN_PLAYER_DISTANCE;
            }
            if (!near || tooClose) {
                continue;
            }
            int cx = (int) Math.floor(x);
            int cz = (int) Math.floor(z);
            BlockPos column = new BlockPos(cx, refY, cz);
            if (!level.isLoaded(column) || !level.isPositionEntityTicking(column)) {
                continue;
            }
            if (!ShipRoutePlanner.clear(level, cx, cz, refY, SeaDocks.COG_CLEARANCE)
                    || ShipRoutePlanner.waterDepth(level, cx, cz, refY) < SettlementConfig.seaCaravanMinDepth) {
                continue;
            }
            int surface = ShipRoutePlanner.surfaceY(level, cx, cz, refY);
            if (surface == Integer.MIN_VALUE) {
                continue;
            }
            Vec3 pos = new Vec3(cx + 0.5D, surface + 0.5D, cz + 0.5D);
            ShipRoutePlanner.Plan plan = ShipRoutePlanner.planBetween(level, pos, mooring, refY, SeaDocks.COG_CLEARANCE, false);
            if (plan != null && plan.length() >= SettlementConfig.seaCaravanMinRoute) {
                return new Place(pos, plan);
            }
        }
        return null;
    }
}
