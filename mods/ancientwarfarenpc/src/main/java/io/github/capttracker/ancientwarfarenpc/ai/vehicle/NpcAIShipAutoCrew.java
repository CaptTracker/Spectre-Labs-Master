/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (ship crews): idle soldiers join the crew of their owner's ship.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcCombat;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.Command;
import io.github.capttracker.ancientwarfarenpc.orders.CombatOrder;
import io.github.capttracker.ancientwarfarevehicle.entity.SeatRole;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.List;

/**
 * An idle player-owned soldier (no order, no attack target, no patrol orders, not alarmed, not riding anything) whose owner sits aboard a ship walks to the ship
 * if it is within {@code ship_auto_crew_range} blocks of the hull and takes a free gunner seat, or else a rower seat. It stays aboard until it is ordered off with
 * the command baton or the ship sinks. It walks (or swims out, see {@link ShipApproach}) and gives up when it cannot get there, then waits a minute before it tries again.
 */
public class NpcAIShipAutoCrew extends NpcAI<NpcCombat> {
    private static final List<SeatRole> ROLES = List.of(SeatRole.GUNNER, SeatRole.ROWER);
    private static final int COOLDOWN_TICKS = 1200;

    @Nullable
    private VehicleBase ship;
    private final ShipApproach approach = new ShipApproach();
    private boolean finished = false;
    private int cooldownUntil = 0;

    public NpcAIShipAutoCrew(NpcCombat npc) {
        super(npc);
        setMutexBits(MOVE);
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || !AWNPCStatics.shipAutoCrewSoldiers || npc.level().isClientSide || npc.tickCount < cooldownUntil
                || (npc.tickCount + npc.getId()) % 20 != 0 || !isIdle()) {
            return false;
        }
        VehicleBase found = findShip();
        if (found == null) {
            return false;
        }
        ship = found;
        approach.reset();
        finished = false;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return ship != null && !finished && ShipCrew.shipOf(ship) != null && isIdle() && !npc.isPassenger() && ShipCrew.firstFreeRole(ship, ROLES) != null;
    }

    @Override
    public void tick() {
        if (ship == null) {
            return;
        }
        // walks, or swims out to a ship in open water, and boards (see ShipApproach)
        ShipApproach.Status status = approach.tick(npc, ship, ROLES, AWNPCStatics.shipAutoCrewRange + 4);
        if (status == ShipApproach.Status.MOVING) {
            npc.addAITask(TASK_MOVE);
        } else {
            finished = true;
        }
    }

    @Override
    public void stop() {
        npc.removeAITask(TASK_MOVE);
        if (!npc.isPassenger()) {
            cooldownUntil = npc.tickCount + COOLDOWN_TICKS;
        }
        ship = null;
    }

    private boolean isIdle() {
        if (npc.isPassenger() || npc.getTarget() != null || npc.isAlarmed || npc.getCurrentCommand() != Command.NONE) {
            return false;
        }
        CombatOrder orders = CombatOrder.getCombatOrder(npc.ordersStack);
        return orders == null || orders.isEmpty();
    }

    /** The ship the owner sits in, if it has a free seat for this soldier and is near. */
    @Nullable
    private VehicleBase findShip() {
        if (npc.getOwner().getUUID() == null) {
            return null;
        }
        Player owner = npc.level().getPlayerByUUID(npc.getOwner().getUUID());
        VehicleBase boat = owner == null ? null : ShipCrew.shipOf(owner.getVehicle());
        if (boat == null) {
            npc.setRefusedShip(null); // the owner left the ship: the next one is welcome
            return null;
        }
        if (boat.getUUID().equals(npc.getRefusedShip()) || ShipCrew.firstFreeRole(boat, ROLES) == null
                || ShipCrew.hullDistance(boat, npc.getX(), npc.getZ()) > AWNPCStatics.shipAutoCrewRange || Math.abs(npc.getY() - boat.getY()) > 8.0D) {
            return null;
        }
        return boat;
    }
}
