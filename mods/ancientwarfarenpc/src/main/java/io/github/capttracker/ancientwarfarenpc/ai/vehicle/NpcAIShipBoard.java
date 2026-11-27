/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): soldiers that board an enemy ship and fight on the decks.
 */
package io.github.capttracker.ancientwarfarenpc.ai.vehicle;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.GrappleHook;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

/**
 * Boarding and defending (rework phase 11), for the melee soldiers of owned crews and of NPC factions. The mission comes from {@link ShipCombat#mission}: the soldier's ship
 * is grappled alongside a hostile ship with a foe aboard (board it), or boarders stand on the deck of his ship (defend it).
 *
 * <p>A soldier in a rower or passenger seat leaves it (the crew goal lets go of him) and walks to the nearest point of the other deck; the pathfinder knows nothing of ship
 * decks, so he moves by the move control, and where the rails of the hulls are in the way he leaps over to the nearest deck cell of the other ship once he is within a
 * couple of blocks of its hull (the lines hold the decks side by side). On the deck he fights the nearest foe aboard (riders in seats included) with the usual melee swing and
 * hurt call, closing in by the move control. When nobody is left to fight he goes back to his own deck and takes a seat there again (if the other ship is lost, he stays where
 * he is). A defender fights the boarders on his own deck the same way and sits down again afterwards. The goal holds the move and look flags and cannot be interrupted.
 */
public class NpcAIShipBoard extends NpcAI<NpcBase> {
    private enum Phase { CROSS, FIGHT, RETURN }

    private static final double REACH = 2.4D;
    private static final int LEAP_RANGE_TICKS = 80;
    private static final int MAX_TICKS = 1800;
    private static final int COOLDOWN = 100;

    @Nullable
    private ShipCombat.Mission mission;
    @Nullable
    private VehicleBase home;
    @Nullable
    private VehicleBase enemy;
    private Phase phase = Phase.CROSS;
    private boolean defend = false;
    @Nullable
    private LivingEntity foe;
    private int ticks = 0;
    private int phaseTicks = 0;
    private int idleTicks = 0;
    private int attackDelay = 0;
    private int cooldownUntil = 0;
    private boolean finished = false;

    public NpcAIShipBoard(NpcBase npc) {
        super(npc);
        setMutexBits(MOVE | ATTACK);
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public boolean canUse() {
        if (npc.level().isClientSide || !npc.getIsAIEnabled() || npc.tickCount < cooldownUntil || (npc.tickCount + npc.getId()) % 10 != 0) {
            return false;
        }
        mission = ShipCombat.mission(npc);
        return mission != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !finished && home != null && !home.isRemoved() && npc.isAlive() && ticks < MAX_TICKS;
    }

    @Override
    public void start() {
        home = mission.home();
        enemy = mission.enemy();
        defend = mission.defend();
        phase = defend ? Phase.FIGHT : Phase.CROSS;
        ticks = 0;
        phaseTicks = 0;
        idleTicks = 0;
        attackDelay = 0;
        finished = false;
        foe = null;
        if (npc.isPassenger()) {
            npc.stopRiding(); // it stands on the deck where its seat was
        }
        npc.getNavigation().stop();
    }

    @Override
    public void stop() {
        cooldownUntil = npc.tickCount + COOLDOWN;
        foe = null;
        home = null;
        enemy = null;
    }

    @Override
    public void tick() {
        ticks++;
        phaseTicks++;
        npc.getNavigation().stop();
        if (npc.isPassenger()) {
            npc.stopRiding();
        }
        switch (phase) {
            case CROSS -> tickCross();
            case FIGHT -> tickFight();
            case RETURN -> tickReturn();
        }
    }

    private void setPhase(Phase newPhase) {
        phase = newPhase;
        phaseTicks = 0;
    }

    private boolean alongside() {
        return enemy != null && GrappleHook.usable(enemy) && home != null && home.ship != null && home.ship.alongside().contains(enemy);
    }

    private void tickCross() {
        if (enemy == null || !alongside() || ShipCombat.foesAboard(npc, enemy).isEmpty()) {
            setPhase(Phase.RETURN);
            return;
        }
        double hull = ShipCrew.hullDistance(enemy, npc.getX(), npc.getZ());
        if (hull <= 2.2D || phaseTicks > LEAP_RANGE_TICKS) {
            leapTo(enemy);
            setPhase(Phase.FIGHT);
            return;
        }
        Vec3 edge = ShipCrew.nearestHullPoint(enemy, npc.getX(), npc.getZ());
        npc.getMoveControl().setWantedPosition(edge.x, npc.getY(), edge.z, 1.0D);
        npc.getLookControl().setLookAt(edge.x, npc.getEyeY(), edge.z);
    }

    /** The leap over the rails to the nearest deck cell of the other ship. */
    private void leapTo(VehicleBase ship) {
        Vec3 at = ShipCombat.nearestDeckPoint(ship, npc.position());
        npc.teleportTo(at.x, at.y, at.z);
        npc.fallDistance = 0.F;
        npc.getNavigation().stop();
        if (npc.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.3D, at.z, 5, 0.2D, 0.1D, 0.2D, 0.02D);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, npc.getSoundSource(), 0.6F, 1.2F);
        }
    }

    private void tickFight() {
        if (attackDelay > 0) {
            attackDelay--;
        }
        VehicleBase arena = defend ? home : enemy;
        if (arena == null || !GrappleHook.usable(arena) && !defend) {
            setPhase(Phase.RETURN);
            return;
        }
        if (foe == null || !foe.isAlive() || ticks % 10 == 0) {
            foe = pickFoe(arena);
        }
        if (foe == null) {
            idleTicks += 1;
            if (idleTicks > 40) {
                setPhase(Phase.RETURN);
            }
            return;
        }
        idleTicks = 0;
        double reach = REACH + npc.getBbWidth() / 2.0D + foe.getBbWidth() / 2.0D;
        double dx = foe.getX() - npc.getX();
        double dz = foe.getZ() - npc.getZ();
        double dy = Math.abs(foe.getY() - npc.getY());
        npc.getLookControl().setLookAt(foe, 30.0F, 30.0F);
        if (dx * dx + dz * dz > reach * reach || dy > 2.5D) {
            npc.getMoveControl().setWantedPosition(foe.getX(), npc.getY(), foe.getZ(), 1.0D);
            if (dy > 2.5D && phaseTicks % 40 == 39) {
                leapTo(arena); // stuck behind a rail, or the foe is on the other deck: hop over
            }
            return;
        }
        if (attackDelay <= 0) {
            npc.swing(InteractionHand.MAIN_HAND);
            npc.doHurtTarget(foe);
            npc.addExperience(AWNPCStatics.npcXpFromAttack);
            attackDelay = 14;
        }
    }

    @Nullable
    private LivingEntity pickFoe(VehicleBase arena) {
        List<LivingEntity> foes = ShipCombat.foesAboard(npc, arena);
        if (defend) {
            foes.removeIf(LivingEntity::isPassenger); // a defender goes for the boarders on the deck
        }
        return foes.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(npc))).orElse(null);
    }

    /** Back to the own ship: hop over to its deck, and sit down again. If the own ship is not alongside any more, the soldier stays where he is. */
    private void tickReturn() {
        if (home == null || !GrappleHook.usable(home)) {
            finished = true;
            return;
        }
        if (!defend && enemy != null && !home.ship.isOnDeck(npc)) {
            if (!(home.ship.alongside().contains(enemy)) && ShipCrew.hullDistance(home, npc.getX(), npc.getZ()) > 3.0D) {
                finished = true; // the line is gone and the ship has drifted away: he stays on the other deck
                return;
            }
            if (ShipCrew.hullDistance(home, npc.getX(), npc.getZ()) <= 2.2D || phaseTicks > LEAP_RANGE_TICKS) {
                leapTo(home);
            } else {
                Vec3 edge = ShipCrew.nearestHullPoint(home, npc.getX(), npc.getZ());
                npc.getMoveControl().setWantedPosition(edge.x, npc.getY(), edge.z, 1.0D);
            }
            return;
        }
        ShipCrew.tryBoard(npc, home, npc.getShipSeatPreference().stream()
                .filter(role -> role != io.github.capttracker.ancientwarfarevehicle.entity.SeatRole.GUNNER).toList());
        finished = true;
    }
}
