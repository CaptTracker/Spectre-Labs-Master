/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/vehicle/NpcAIDismountVehicle.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.vehicle.IVehicleUser;

/** Gets off the vehicle when the NPC can no longer keep riding it ({@code canContinueRidingVehicle()}: a player-owned engineer without food). */
public class NpcAIDismountVehicle<T extends NpcBase & IVehicleUser> extends NpcAI<T> {
    public NpcAIDismountVehicle(T npc) {
        super(npc);
    }

    @Override
    public boolean canUse() {
        return super.canUse() && npc.isRidingVehicle() && !npc.canContinueRidingVehicle();
    }

    @Override
    public void start() {
        npc.stopRiding();
        npc.getAssignedVehicle().ifPresent(v -> v.moveHelper.stopMotion()); //stop motion in case it started moving based on some other AI task (like AIM one)
        npc.resetAssignedVehicle();
    }
}
