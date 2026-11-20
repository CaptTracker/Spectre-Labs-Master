/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcAIFleeHostiles.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.owner.TeamViewerRegistry;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcCombat;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Civilian-type owned NPCs run home (or away) when hostiles are within 16 blocks, then call the soldiers nearby for help ({@code announceDistress}).
 * The hostile is made the NPC's attack target; the target is used as "who am I running from".
 *
 * <p>Original bug fixed: the 1.12.2 search box was {@code AxisAlignedBB.expand(16, 3, 16)}, which only extends the box towards positive x, y and z, so
 * hostiles to the west, north and below were not seen; the box is grown on every side now.
 */
public class NpcAIFleeHostiles extends NpcAI<NpcPlayerOwned> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_STAY_AWAY = 50;
    private static final int MAX_FLEE_RANGE = 16;
    private static final int HEIGHT_CHECK = 7;
    private static final int PURSUE_RANGE = 16 * 16;
    private static final double DISTANCE_FROM_ENTITY = 16;
    private static final int TICKER_MAX = 5; // scan for hostiles every 5 checks

    private final Predicate<Mob> hostileOrFriendlyCombatNpcSelector;
    private final Comparator<Entity> sorter;
    @Nullable
    private Vec3 fleeVector;
    private int stayOutOfSightTimer = 0;
    private int fearLevel = 0; // fear makes NPC's wait/flee for progressively longer periods
    private boolean homeCompromised = false;
    private final LinkedHashSet<NpcCombat> nearbySoldiers = new LinkedHashSet<>();
    private int ticker = 0;

    public NpcAIFleeHostiles(final NpcPlayerOwned npc) {
        super(npc);
        hostileOrFriendlyCombatNpcSelector = selectedEntity -> {
            if (selectedEntity != null && selectedEntity.isAlive()) {
                if (selectedEntity instanceof NpcBase selectedNpc) {
                    return isFriendlySoldier(npc, selectedNpc);
                } else {
                    return NpcAIFleeHostiles.this.npc.isHostileTowards(selectedEntity);
                }
            }
            return false;
        };
        sorter = Comparator.comparingDouble(e -> npc.distanceToSqr(e));
        setMutexBits(MOVE);
    }

    private boolean isFriendlySoldier(NpcPlayerOwned npc, NpcBase selectedEntity) {
        return selectedEntity.getNpcSubType().equals("soldier") && selectedEntity.hasCommandPermissions(npc.getOwner())
                || selectedEntity.isHostileTowards(npc);
    }

    @Override
    public boolean canUse() {
        if (!super.canUse()) {
            return false;
        }
        if (stayOutOfSightTimer > 0) {
            return true;
        }
        ticker++;
        if (ticker != TICKER_MAX) {
            return false;
        }
        ticker = 0;
        boolean flee = false;
        findNearbyRelevantEntities();
        if (!npc.nearbyHostiles.isEmpty()) {
            Entity nearestHostile = npc.nearbyHostiles.iterator().next();
            if (npc.getTownHallPosition().isPresent() || npc.hasHome()) {
                flee = true;
            } else {
                fleeVector = DefaultRandomPos.getPosAway(npc, MAX_FLEE_RANGE, HEIGHT_CHECK, nearestHostile.position());
                flee = fleeVector != null && nearestHostile.distanceToSqr(fleeVector.x, fleeVector.y, fleeVector.z) >= nearestHostile.distanceToSqr(npc);
            }
            if (flee) {
                stayOutOfSightTimer = MAX_STAY_AWAY + fearLevel;
                if (nearestHostile instanceof LivingEntity living) {
                    npc.setTarget(living);
                } else {
                    LOGGER.error("Attempted to flee an entity that isn't a living entity: '{}', ignoring! Please report this error.",
                            BuiltInRegistries.ENTITY_TYPE.getKey(nearestHostile.getType()));
                }
            }
        }
        return flee;
    }

    private void findNearbyRelevantEntities() {
        npc.nearbyHostiles.clear();
        nearbySoldiers.clear();
        List<Mob> nearbyHostilesOrFriendlySoldiers = npc.level().getEntitiesOfClass(Mob.class,
                npc.getBoundingBox().inflate(DISTANCE_FROM_ENTITY, 3.0D, DISTANCE_FROM_ENTITY), hostileOrFriendlyCombatNpcSelector);
        if (nearbyHostilesOrFriendlySoldiers.isEmpty()) {
            return;
        }
        nearbyHostilesOrFriendlySoldiers.sort(sorter);
        for (Mob entity : nearbyHostilesOrFriendlySoldiers) {
            if (npc.hasLineOfSight(entity)) {
                if (entity instanceof NpcCombat combat) {
                    if (combat.getNpcSubType().equals("soldier") && combat.hasCommandPermissions(npc.getOwner())) {
                        nearbySoldiers.add(combat);
                    }
                } else if (entity instanceof OwnableEntity ownable && ownable.getOwner() != null) {
                    Entity owner = ownable.getOwner();
                    if (owner != null && isFriendly(entity.level(), owner.getUUID(), owner.getName().getString())) {
                        return;
                    }
                } else {
                    npc.nearbyHostiles.add(entity);
                }
            }
        }
    }

    // check if  the owner is same or owned by a team member or owned by a friend
    public boolean isFriendly(Level world, @Nullable UUID ownerId, String ownerName) {
        return TeamViewerRegistry.areFriendly(world, npc.getOwner().getUUID(), ownerId, npc.getOwner().getName(), ownerName);
    }

    @Override
    public void start() {
        npc.addAITask(TASK_GO_HOME);
        npc.addAITask(TASK_FLEE);
        stayOutOfSightTimer = MAX_STAY_AWAY + fearLevel;
        fearLevel += MAX_STAY_AWAY;
    }

    @Override
    public void tick() {
        if (fearLevel > 0) {
            fearLevel--;
        }
        LivingEntity attackTarget = npc.getTarget();
        if (attackTarget == null || !attackTarget.isAlive()) {
            npc.setTarget(null);
            stayOutOfSightTimer = 0;
            return;
        }
        BlockPos pos = null;
        double distSq = 0;
        if (npc.hasHome()) {
            distSq = npc.getDistanceSqFromHome();
            pos = npc.getHomePosition();
            if (distSq < MIN_RANGE && !npc.nearbyHostiles.isEmpty()) {
                homeCompromised = true;
                npc.addAITask(TASK_ALARM);
            }
        }
        if (homeCompromised || !npc.hasHome()) {
            final double homeDistSq = distSq;
            distSq = npc.getTownHallPosition().map(this::distanceSq).orElse(homeDistSq);
            // TODO(original): Check if within range of town hall and somehow defend it/themselves from present hostiles
        }
        if (pos == null) {
            if (fleeVector == null) {
                return;
            }
            //check distance to flee vector
            distSq = npc.distanceToSqr(fleeVector.x, fleeVector.y, fleeVector.z);
            if (distSq > MIN_RANGE) {
                moveToPosition(fleeVector.x, fleeVector.y, fleeVector.z, distSq);
            } else {
                if (npc.distanceToSqr(attackTarget) < PURSUE_RANGE) {//entity still chasing, find a new flee vector
                    fleeVector = DefaultRandomPos.getPosAway(npc, MAX_FLEE_RANGE, HEIGHT_CHECK, attackTarget.position());
                    if (fleeVector == null) {
                        npc.setTarget(null);//retry next tick..perhaps...
                    }
                } else // entity too far to care, stop running
                {
                    npc.setTarget(null);
                }
            }
        }
        // keep running home
        if (pos != null && distSq > MIN_RANGE) {
            moveToPosition(pos, distSq);
        }
        if (stayOutOfSightTimer > 0) {
            stayOutOfSightTimer--;
        } else {
            announceDistress();
        }
    }

    @Override
    public void stop() {
        fleeVector = null;
        npc.getNavigation().stop();
        npc.setTarget(null);
        npc.removeAITask(TASK_GO_HOME + TASK_FLEE + TASK_ALARM);
        homeCompromised = false;
    }

    private void announceDistress() {
        for (NpcCombat soldier : nearbySoldiers) {
            soldier.respondToDistress(npc);
        }
    }
}
