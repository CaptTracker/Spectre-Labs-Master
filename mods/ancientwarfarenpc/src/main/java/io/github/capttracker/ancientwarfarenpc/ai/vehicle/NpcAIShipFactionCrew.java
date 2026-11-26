/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (ship crews): idle soldiers of an NPC faction join the crew of the ship of an allied player.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

/**
 * Ships cannot be owned by an NPC faction (an owner is a player) and the command baton only commands NPCs of its owner, so a faction's soldiers crew a ship by
 * themselves: an idle faction NPC that has ship seat roles (soldiers and archers: gunner, then rower; see {@code getShipSeatPreference}) walks or swims to a ship
 * within {@code ship_auto_crew_range} blocks whose helm is held by a player that is ALLIED to its faction, and takes a free seat. Aboard it does what any crew
 * does ({@link NpcAIShipCrew}: man the guns, fire at what its faction is hostile to) and leaves the ship, onto land if there is some close, when no allied helm is
 * left. It does not board the ship it left again while the allied helm stays aboard.
 */
public class NpcAIShipFactionCrew extends NpcAI<NpcFaction> {
    private static final int COOLDOWN_TICKS = 1200;

    @Nullable
    private VehicleBase ship;
    private final ShipApproach approach = new ShipApproach();
    private boolean finished = false;
    private int cooldownUntil = 0;

    public NpcAIShipFactionCrew(NpcFaction npc) {
        super(npc);
        setMutexBits(MOVE);
    }

    @Override
    public boolean canUse() {
        if (!super.canUse() || !AWNPCStatics.shipFactionCrew || npc.level().isClientSide || npc.tickCount < cooldownUntil
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
        return ship != null && !finished && ShipCrew.shipOf(ship) != null && isIdle() && !npc.isPassenger() && ShipCrew.isAlliedHelm(npc, ship)
                && ShipCrew.firstFreeRole(ship, npc.getShipSeatPreference()) != null;
    }

    @Override
    public void tick() {
        if (ship == null) {
            return;
        }
        ShipApproach.Status status = approach.tick(npc, ship, npc.getShipSeatPreference(), AWNPCStatics.shipAutoCrewRange + 4);
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
        return !npc.isPassenger() && npc.getTarget() == null && !npc.getShipSeatPreference().isEmpty();
    }

    /** The nearest ship within range whose helm is an ally of the NPC's faction and that has a free seat for it. */
    @Nullable
    private VehicleBase findShip() {
        double range = AWNPCStatics.shipAutoCrewRange;
        List<VehicleBase> near = npc.level().getEntitiesOfClass(VehicleBase.class, npc.getBoundingBox().inflate(range + 16.0D, 8.0D, range + 16.0D),
                v -> ShipCrew.shipOf(v) != null && v.getControllingPassenger() instanceof Player);
        if (npc.getRefusedShip() != null && near.stream().noneMatch(v -> v.getUUID().equals(npc.getRefusedShip()) && ShipCrew.isAlliedHelm(npc, v))) {
            npc.setRefusedShip(null); // the ship it left has no allied helm any more: the next one is welcome
        }
        near.removeIf(v -> v.getUUID().equals(npc.getRefusedShip()) || !ShipCrew.isAlliedHelm(npc, v)
                || ShipCrew.firstFreeRole(v, npc.getShipSeatPreference()) == null
                || ShipCrew.hullDistance(v, npc.getX(), npc.getZ()) > range || Math.abs(npc.getY() - v.getY()) > 8.0D);
        return near.stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(npc))).orElse(null);
    }
}
