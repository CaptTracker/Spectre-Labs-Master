/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (ship crews): what an owned NPC does while it sits in a rower, gunner or passenger seat of a ship.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfarenpc.ai.AIHelper;
import io.github.capttracker.ancientwarfarenpc.ai.MonsterRules;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipHardpoint;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.ShipHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The duty of an owned NPC that crews a ship (it sits in a seat of a ship that is not the helm). The goal holds the move and look flags at the top priority, so
 * the NPC's work, patrol and wander goals leave it in its seat; a rower or passenger does nothing else (the rowers add speed through the ship by sitting
 * there). A soldier in a gunner seat is a turret crew: it picks a target, aims its mount and fires it through the same server side calls a player's
 * {@code HardpointAim} and {@code HardpointFire} payloads end in ({@code ShipHelper.aimHardpoint}, {@code fireHardpoint}), so reload, wind-up and the ship's
 * ammo are the same.
 *
 * <p>Targets: the NPC's own attack target if it has one (an order or its target goals), else the nearest living entity within the configured range that it is
 * hostile to by its owner's faction relation ({@code isHostileTowards}) and may target ({@link AIHelper#isTarget}, line of sight needed) and that its mount can
 * hit (arc, elevation, reach of the shot, see {@code ShipHelper.solveHardpoint}). It fires only when the mount points at the aim and is reloaded and the ship has
 * ammo (the loaded type is switched when it is out).
 */
public class NpcAIShipCrew extends NpcAI<NpcBase> {
    private static final int SCAN_INTERVAL = 10;
    private static final int MAX_CANDIDATES = 12;
    private static final float AIM_SEND_STEP = 0.4F;

    @Nullable
    private LivingEntity target;
    private int scanDelay = 0;
    private float sentYaw = Float.NaN;
    private float sentPitch = Float.NaN;

    public NpcAIShipCrew(NpcBase npc) {
        super(npc);
        setMutexBits(MOVE | ATTACK);
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public boolean canUse() {
        // a soldier that is to board or defend (rework phase 11) is let go of: NpcAIShipBoard takes him out of his seat
        return !npc.level().isClientSide && npc.getIsAIEnabled() && npc.isShipCrew() && !ShipCombat.shouldLeaveSeat(npc);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        target = null;
        scanDelay = npc.getRandom().nextInt(SCAN_INTERVAL);
        sentYaw = Float.NaN;
        sentPitch = Float.NaN;
    }

    @Override
    public void stop() {
        target = null;
    }

    @Override
    public void tick() {
        VehicleBase ship = npc.getRidingShip();
        if (ship == null || ship.ship == null || ship.ship.isSinking()) {
            return;
        }
        npc.getNavigation().stop();
        if (npc instanceof NpcPlayerOwned owned) {
            ShipCrew.eatAboard(owned, ship); // a hungry rower eats from the ship's storage without leaving the seat
        } else if (npc instanceof NpcFaction faction && !faction.isCaravanMember() && npc.tickCount % 20 == 0 && !ShipCrew.isAlliedHelm(npc, ship)) {
            // the allied player at the helm is gone: the soldiers of a faction do not stay on a ship of someone else
            npc.setRefusedShip(ship.getUUID());
            ShipCrew.disembark(npc, ship, ship.position());
            return;
        }
        ShipCombat.tickCrew(npc, ship); // a soldier may throw a grappling hook at a hostile ship (rework phase 11)
        int mount = ship.ship.hardpointOf(npc);
        if (mount < 0 || !AWNPCStatics.shipGunnersFire || !npc.isShipGunner()) {
            return;
        }
        ShipHelper helper = ship.ship;
        double range = AWNPCStatics.shipGunnerRange;
        if (target != null && (!target.isAlive() || helper.solveHardpoint(mount, aimPoint(helper, mount, target), range) == null)) {
            target = null;
        }
        if (--scanDelay <= 0) {
            scanDelay = SCAN_INTERVAL;
            if (target == null) {
                target = pickTarget(ship, mount, range);
            }
        }
        if (target == null) {
            return;
        }
        ShipHelper.AimSolution aim = helper.solveHardpoint(mount, aimPoint(helper, mount, target), range);
        if (aim == null) {
            target = null;
            return;
        }
        npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
        ShipHardpoint hardpoint = helper.hardpoint(mount);
        if (Float.isNaN(sentYaw) || Math.abs(Mth.wrapDegrees(aim.yaw() - sentYaw)) > AIM_SEND_STEP || Math.abs(aim.pitch() - sentPitch) > AIM_SEND_STEP) {
            sentYaw = aim.yaw();
            sentPitch = aim.pitch();
            helper.aimHardpoint(mount, aim.yaw(), aim.pitch(), true);
        }
        if (hardpoint.isReady() && hardpoint.onTarget() && ship.ammoHelper.ensureAmmoAvailable()) {
            helper.fireHardpoint(mount, npc);
        }
    }

    /** The point to hit: the middle of the target's body, led by how far it moves while the shot flies. */
    private Vec3 aimPoint(ShipHelper helper, int mount, LivingEntity entity) {
        Vec3 center = entity.getBoundingBox().getCenter();
        Vec3 step = new Vec3(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo);
        if (step.lengthSqr() < 1.0E-4D) {
            return center;
        }
        VehicleBase ship = npc.getRidingShip();
        double speed = ship == null ? 30.0D : Math.max(5.0D, ship.firingHelper.getAdjustedMaxMissileVelocity());
        double seconds = center.distanceTo(helper.toWorld(0, 0, 0)) / speed;
        Vec3 lead = step.scale(20.0D * seconds);
        if (lead.lengthSqr() > 64.0D) {
            lead = lead.normalize().scale(8.0D);
        }
        return center.add(lead);
    }

    @Nullable
    private LivingEntity pickTarget(VehicleBase ship, int mount, double range) {
        ShipHelper helper = ship.ship;
        List<LivingEntity> candidates = new ArrayList<>();
        LivingEntity own = npc.getTarget();
        if (own != null && own.isAlive() && npc.canTarget(own)) {
            candidates.add(own);
        }
        List<LivingEntity> near = npc.level().getEntitiesOfClass(LivingEntity.class, ship.getBoundingBox().inflate(range), EntitySelector.NO_SPECTATORS);
        near.removeIf(e -> e == npc || e == own || !e.isAlive() || ship.hasPassenger(e) || e.isPassengerOfSameVehicle(npc) || !npc.isHostileTowards(e));
        near.sort(Comparator.comparingInt((LivingEntity e) -> MonsterRules.rangedPrefers(npc, e) ? 0 : 1).thenComparingDouble(e -> e.distanceToSqr(npc))); // dragons first (Ice and Fire integration)
        for (LivingEntity candidate : near.subList(0, Math.min(MAX_CANDIDATES, near.size()))) {
            if (AIHelper.isTarget(npc, candidate, true)) {
                candidates.add(candidate);
            }
        }
        candidates.sort(Comparator.comparingInt(e -> MonsterRules.rangedPrefers(npc, e) ? 0 : 1)); // stable: the rest keeps its order
        for (LivingEntity candidate : candidates) {
            if (helper.solveHardpoint(mount, aimPoint(helper, mount, candidate), range) != null) {
                return candidate;
            }
        }
        return null;
    }
}
