/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): the guard captain.
 */
package io.github.capttracker.ancientwarfarenpc.entity;

import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.Command;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.CommandType;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

/**
 * The guard captain (rework phase 5): a combat NPC, at most one for each settlement. While the captain is in combat, the combat NPCs of its settlement within
 * {@code captain_buff_radius} blocks (24) get Strength I and Resistance I. The patrol route of the settlement is the border markers, or the waypoints
 * the owner sets with the command baton: a move command given to the captain at a place on the land adds a waypoint to the route (a "clear command"
 * clears the route). During an alarm the captain stands in the middle of a line of the combat NPCs facing the threat ({@code NpcAIPlayerOwnedRally}).
 * The patrol on and off, the night watch and the automatic alarm are set on the Guard tab of the town hall.
 */
public class NpcGuardCaptain extends NpcCombat {
    private static final int BUFF_INTERVAL = 20;
    private static final int COMBAT_MEMORY = 100;

    public NpcGuardCaptain(EntityType<? extends NpcGuardCaptain> type, Level level) {
        super(type, level);
    }

    @Override
    public String getNpcType() {
        return "guard_captain";
    }

    /** The captain has no subtype of its own: it does not become a medic or archer by what it holds. */
    @Override
    public String getNpcSubType() {
        return "";
    }

    // ---------------------------------------------------------------- one captain for each settlement

    /** A settlement with a captain already does not take a second one: the new captain stays outside it and its owner is told. */
    @Override
    public void joinSettlement(Settlement settlement) {
        if (level().isClientSide) {
            return;
        }
        Optional<Settlement.Member> other = settlement.getCaptain();
        if (other.isPresent() && !other.get().id.equals(getUUID())) {
            onDismissedFromSettlement(); // does not try again by itself
            if (getOwner() != null && level() instanceof ServerLevel serverLevel) {
                Player owner = serverLevel.getPlayerByUUID(getOwner().getUUID());
                if (owner != null) {
                    owner.displayClientMessage(Component.translatable("message.ancientwarfarenpc.captain.second", settlement.getName()), true);
                }
            }
            return;
        }
        super.joinSettlement(settlement);
    }

    // ---------------------------------------------------------------- the baton route

    @Override
    public void handlePlayerCommand(Command cmd) {
        if (!level().isClientSide && getSettlement().isPresent()) {
            Settlement settlement = getSettlement().get();
            if (cmd.type == CommandType.MOVE && settlement.addPatrolWaypoint(cmd.pos)) {
                return; // a waypoint of the patrol route, the captain does not walk there
            }
            if (cmd.type == CommandType.CLEAR_COMMAND) {
                settlement.clearPatrolWaypoints();
            }
        }
        super.handlePlayerCommand(cmd);
    }

    // ---------------------------------------------------------------- the buffs

    /** In combat: it has an attack target, or was hurt by a creature in the last 5 seconds. */
    public boolean isInCombat() {
        LivingEntity target = getTarget();
        return (target != null && target.isAlive()) || (getLastHurtByMob() != null && tickCount - getLastHurtByMobTimestamp() < COMBAT_MEMORY);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && isAlive() && tickCount % BUFF_INTERVAL == 0 && isInCombat()) {
            buffSoldiers();
        }
    }

    private void buffSoldiers() {
        UUID settlementId = getSettlementId();
        if (settlementId == null) {
            return;
        }
        double radius = SettlementConfig.captainBuffRadius;
        AABB box = getBoundingBox().inflate(radius);
        for (NpcCombat soldier : level().getEntitiesOfClass(NpcCombat.class, box,
                s -> s != this && s.isAlive() && settlementId.equals(s.getSettlementId()) && s.distanceToSqr(this) <= radius * radius)) {
            soldier.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, SettlementConfig.captainBuffTicks, 0, true, false, true));
            soldier.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SettlementConfig.captainBuffTicks, 0, true, false, true));
        }
    }

    // ---------------------------------------------------------------- the line

    /** The loaded, living guard captain of the settlement, if there is one. */
    @Nullable
    public static NpcGuardCaptain captainOf(ServerLevel level, Settlement settlement) {
        Optional<Settlement.Member> member = settlement.getCaptain();
        if (member.isPresent() && level.getEntity(member.get().id) instanceof NpcGuardCaptain captain && captain.isAlive()) {
            return captain;
        }
        return null;
    }

    /**
     * Where a combat NPC of the settlement stands in the line the captain forms against the threat: the middle of the line lies between the hall and the
     * threat (at most {@code captain_line_distance} blocks from the hall), the line runs across that direction, the captain in the middle (one step ahead)
     * and the others alternating to its left and right, {@code captain_line_spacing} blocks apart, in the order of the member list.
     */
    public static BlockPos lineSlot(ServerLevel level, Settlement settlement, UUID member, BlockPos threat) {
        BlockPos hall = settlement.getHallPos();
        Vec3 away = new Vec3(threat.getX() - hall.getX(), 0.0D, threat.getZ() - hall.getZ());
        double distance = away.length();
        Vec3 direction = distance < 1.0D ? new Vec3(0.0D, 0.0D, 1.0D) : away.scale(1.0D / distance);
        double ahead = Math.min(SettlementConfig.captainLineDistance, Math.max(3.0D, distance - 6.0D));
        double cx = hall.getX() + 0.5D + direction.x * ahead;
        double cz = hall.getZ() + 0.5D + direction.z * ahead;
        double px = -direction.z;
        double pz = direction.x;
        int index = 0; // 0 is the captain
        int k = 0;
        boolean captain = false;
        for (Settlement.Member m : settlement.getMembers().values()) {
            if (m.type.startsWith("guard_captain")) {
                if (m.id.equals(member)) {
                    captain = true;
                }
            } else if (m.type.startsWith("combat") && !m.type.equals("combat.medic")) {
                k++;
                if (m.id.equals(member)) {
                    index = k;
                }
            }
        }
        double offset = 0.0D;
        double forward = 0.0D;
        if (captain) {
            forward = 1.5D;
        } else if (index > 0) {
            offset = (index % 2 == 1 ? 1 : -1) * ((index + 1) / 2) * SettlementConfig.captainLineSpacing;
        }
        int x = (int) Math.floor(cx + px * offset + direction.x * forward);
        int z = (int) Math.floor(cz + pz * offset + direction.z * forward);
        int y = level.hasChunk(x >> 4, z >> 4) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : hall.getY();
        return new BlockPos(x, y, z);
    }
}
