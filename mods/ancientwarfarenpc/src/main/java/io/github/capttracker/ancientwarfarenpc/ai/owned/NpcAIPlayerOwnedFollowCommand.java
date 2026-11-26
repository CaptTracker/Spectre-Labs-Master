/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/owned/NpcAIPlayerOwnedFollowCommand.
 */
package io.github.capttracker.ancientwarfarenpc.ai.owned;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.ShipApproach;
import io.github.capttracker.ancientwarfarenpc.ai.vehicle.ShipCrew;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.Command;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.CommandType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.Horse;

import javax.annotation.Nullable;

/**
 * Executes the commands given with the command baton: move, guard (walk to an entity, mount or dismount a horse), attack area, set and clear the home,
 * set and clear the upkeep point, clear the command. The guard and attack area commands wait for the NPC to have no attack target.
 */
public class NpcAIPlayerOwnedFollowCommand extends NpcAI<NpcPlayerOwned> {
    @Nullable
    private BlockPos moveTargetPos = null;
    /** The way to a ship the NPC was ordered aboard (walking, swimming out, walking the deck). */
    private final ShipApproach boarding = new ShipApproach();

    public NpcAIPlayerOwnedFollowCommand(NpcPlayerOwned npc) {
        super(npc);
        setMutexBits(ATTACK | MOVE);
    }

    @Override
    public boolean canUse() {
        if (!super.canUse()) {
            return false;
        }
        Command cmd = npc.getCurrentCommand();
        return cmd != Command.NONE && (!(cmd.type == CommandType.GUARD || cmd.type == CommandType.ATTACK_AREA) || npc.getTarget() == null);
    }

    @Override
    public void start() {
        boarding.reset();
    }

    @Override
    public void stop() {
        Command cmd = npc.getCurrentCommand();
        if (cmd != Command.NONE && (npc.getTarget() == null || !cmd.type.isPersistent())) {
            npc.handlePlayerCommand(Command.NONE);
        }
    }

    @Override
    public void tick() {
        Command cmd = npc.getCurrentCommand();
        handleCommand(cmd);
        if (!cmd.type.isPersistent()) {
            npc.setPlayerCommand(Command.NONE);
        }
    }

    @SuppressWarnings("squid:S1199")
    private void handleCommand(Command cmd) {
        switch (cmd.type)//handle instant type commands
        {
            case CLEAR_HOME: {
                npc.clearRestriction();
                break;
            }
            case CLEAR_UPKEEP: {
                npc.setUpkeepAutoPosition(null);
                break;
            }
            case SET_HOME: {
                npc.setHomePosAndDistance(cmd.pos, npc.getHomeRange());
                break;
            }
            case SET_UPKEEP: {
                npc.setUpkeepAutoPosition(cmd.pos);
                break;
            }
            case CLEAR_COMMAND:
            case ATTACK: {
                //should already be handled by npc 'handle command' functionality when command first received
                npc.setPlayerCommand(Command.NONE);
                break;
            }
            case ATTACK_AREA: {
                //TODO(original) this likely needs an implementaion that will prioritize attacking targets over moving
                handleMoveCommand(cmd);
                break;
            }
            case GUARD: {
                handleGuardCommand(cmd);
                break;
            }
            case MOVE: {
                handleMoveCommand(cmd);
                break;
            }
            default:
        }
    }

    /*
     * move towards the commanded guard-entity.
     */
    private void handleGuardCommand(Command cmd) {
        Entity e = cmd.getEntityTarget(npc.level());
        if (e == null) {
            npc.setPlayerCommand(Command.NONE);//clear the command if the target entity cannot be found
            return;
        }
        VehicleBase ship = ShipCrew.shipOf(e);
        if (ship != null) {
            handleBoardShip(ship);
            return;
        }
        double sqDist = npc.distanceToSqr(e);
        if (sqDist > MIN_RANGE) {
            moveToEntity(e, sqDist);//move to entity...
        } else {
            npc.getNavigation().stop();//clear path to stop moving
            if (e instanceof Horse) {
                if (!npc.isPassenger() && e.getPassengers().isEmpty()) {
                    npc.startRiding(e);
                    e.setYRot(npc.getYRot() % 360F);
                    e.yRotO = e.getYRot();
                    npc.setPlayerCommand(Command.NONE);//clear command if horse was mounted successfully..
                } else if (npc.isPassenger() && npc.getVehicle() == e) {
                    npc.stopRiding();
                    npc.setPlayerCommand(Command.NONE);
                }
            }
            //do not clear command, guard command is persistent
        }
    }

    /**
     * Ordered at a ship (the baton's move mode aimed at it): get to it (walk, or swim out to a ship that lies in the water; {@link ShipApproach}) and take the
     * best free seat of the NPC's roles ({@code ShipCrew.boardRoles}: its preference, and the helm if it is free). The order ends once aboard, when the ship has
     * no seat for the NPC, or when the NPC cannot get there (farther than {@code ship_board_range}, no way, no headway, out of time).
     */
    private void handleBoardShip(VehicleBase ship) {
        ShipApproach.Status status = boarding.tick(npc, ship, ShipCrew.boardRoles(npc, ship), AWNPCStatics.shipBoardRange);
        if (status == ShipApproach.Status.MOVING) {
            npc.addAITask(TASK_MOVE);
            npc.setDoing("ship.board", ship.blockPosition(), net.minecraft.network.chat.Component.empty());
            return;
        }
        boarding.reset();
        npc.removeAITask(TASK_MOVE);
        if (status == ShipApproach.Status.FAILED) {
            npc.think("ship_unreachable");
        }
        npc.setPlayerCommand(Command.NONE);
    }

    private void handleMoveCommand(Command cmd) {
        if (moveTargetPos == null || moveTargetPos != cmd.pos) {
            moveTargetPos = cmd.pos;
        }
        double sqDist = distanceSq(moveTargetPos);
        if (sqDist > MIN_RANGE) {
            moveToPosition(moveTargetPos, sqDist);//not finished moving...move along path (or at least try)
        } else {
            npc.setPlayerCommand(Command.NONE);//finished moving..clear the command...
        }
    }
}
