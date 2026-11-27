/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): finding docks and their mooring places for the cargo ships.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import io.github.capttracker.ancientwarfare.api.ISeaDock;
import io.github.capttracker.ancientwarfare.api.IWorkSite;
import io.github.capttracker.ancientwarfare.api.WorksiteRegistry;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfarenpc.settlement.JobBoard;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipHelper;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipRoutePlanner;
import io.github.capttracker.spectrefactions.api.FactionsApi;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The docks of the sea trade (rework phase 11): the dock block entities ({@link ISeaDock}), who may use them, and where a cargo ship lies when it is moored at
 * one. The mooring place is the water cell nearest to the dock block (within 70% of {@code sea_trade_moor_range} blocks of it) where the whole hull of the ship
 * floats in water deep enough ({@link ShipRoutePlanner#findOpenWater}); a ship that has sailed there is moored and trades with the dock.
 */
public final class SeaDocks {
    /** The cells round a hull cell that must be water too: half the width of the hull, at least 1. */
    public static int clearanceOf(VehicleBase ship) {
        return ship.ship != null && ship.ship.hasSpec() ? Math.max(1, (int) (ship.ship.spec().width() / 2.0F)) : 1;
    }

    /** The clearance of a cog (5 blocks wide), for the places that look for a mooring before a ship exists. */
    public static final int COG_CLEARANCE = 2;

    private SeaDocks() {}

    /** The dock at the position, or null if the chunk is not loaded or the block is not a dock. */
    @Nullable
    public static ISeaDock dockAt(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof ISeaDock dock ? dock : null;
    }

    /** True if a trader of this owner may trade with the dock: the dock has no owner, or its owner is the same player or of the same actor. */
    public static boolean mayUse(ISeaDock dock, UUID ownerId) {
        Owner owner = dock.getOwner();
        if (owner == null || owner == Owner.EMPTY) {
            return true;
        }
        UUID id = owner.getUUID();
        return id.equals(ownerId) || FactionsApi.actorOf(id).equals(FactionsApi.actorOf(ownerId));
    }

    /**
     * The mooring place of a hull with this clearance at the dock (the top water block), or null if the dock has no water there that is {@code minDepth} blocks
     * deep and wide enough. The dock has to be loaded.
     */
    @Nullable
    public static BlockPos mooringOf(Level level, ISeaDock dock, int clearance, int minDepth) {
        BlockPos pos = dock.getDockPos();
        int radius = Math.max(4, (int) (SettlementConfig.seaTradeMoorRange * 0.7D));
        int refY = pos.getY() - 1;
        BlockPos found = ShipRoutePlanner.findOpenWater(level, pos, radius, refY, clearance, minDepth);
        if (found == null) {
            found = ShipRoutePlanner.findOpenWater(level, dock.getBerth(), Math.min(radius, 8), dock.getBerth().getY(), clearance, minDepth);
        }
        return found;
    }

    /** The mooring of the dock for a player's route: a hull of this ship in water at least {@link ShipHelper#MIN_DEPTH} deep. */
    @Nullable
    public static BlockPos mooringOf(Level level, ISeaDock dock, VehicleBase ship) {
        return mooringOf(level, dock, clearanceOf(ship), ShipHelper.MIN_DEPTH);
    }

    /** The loaded docks inside the land of the settlement that its owner may use, nearest to the hall first. */
    public static List<ISeaDock> docksOf(ServerLevel level, Settlement settlement) {
        List<ISeaDock> result = new ArrayList<>();
        for (BlockEntity be : WorksiteRegistry.inLevel(level, settlement::contains)) {
            if (be instanceof ISeaDock dock && be instanceof IWorkSite site && JobBoard.mayUse(settlement, site)) {
                result.add(dock);
            }
        }
        BlockPos hall = settlement.getHallPos();
        result.sort(Comparator.comparingDouble(dock -> dock.getDockPos().distSqr(hall)));
        return result;
    }
}
