/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/vehicle/NpcAIFireVehicle.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.vehicle.IVehicleUser;
import net.minecraft.world.phys.Vec3;

/** When the vehicle is aimed at the target, ready to fire and the target is within the effective range, it fires once every 20 ticks ({@code VehicleFiringHelper.handleFireUpdate}). */
public class NpcAIFireVehicle<T extends NpcBase & IVehicleUser> extends NpcAI<T> {
    private int actionTick = 0;

    public NpcAIFireVehicle(T npc) {
        super(npc);
    }

    @Override
    public boolean canUse() {
        return super.canUse() && npc.getVehicleTarget().isPresent() && npc.canContinueRidingVehicle() && npc.isRidingVehicle() && isInRange() && canFire();
    }

    private boolean canFire() {
        return npc.getAssignedVehicle().map(v -> npc.getVehicleTarget().map(t -> v.firingHelper.isAimedAt(t)
                && v.firingHelper.isReadyToFire()).orElse(false)).orElse(false);
    }

    private boolean isInRange() {
        return npc.getAssignedVehicle().map(v -> npc.getVehicleTarget().map(t -> v.getEffectiveRange((float) (t.getY() - v.getY())) >= v.getMissileOffset().add(v.position())
                .distanceTo(new Vec3(t.getX(), t.getY(), t.getZ()))).orElse(false)).orElse(false);
    }

    @Override
    public void tick() {
        if (actionTick <= 0) {
            npc.getAssignedVehicle().ifPresent(v -> v.firingHelper.handleFireUpdate());
            actionTick = 20;
        } else {
            actionTick--;
        }
    }
}
