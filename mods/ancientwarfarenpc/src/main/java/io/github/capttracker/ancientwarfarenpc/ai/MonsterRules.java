/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): how the NPCs deal with the dragons of Ice and Fire.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.compat.ModCompat;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcCombat;
import io.github.capttracker.ancientwarfarenpc.entity.vehicle.IVehicleUser;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.trait.Trait;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.neoforged.neoforge.entity.PartEntity;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The rules about major threats (dragons) that the goals share. They only use {@link ModCompat#MONSTERS}, so they do nothing without the integration.
 * An NPC either fights a dragon from a distance (an archer, a crew of a siege weapon or of a ship gun: they take dragons before nearer targets), or
 * keeps out of the reach of its breath (a melee soldier, unless it is brave or has orders).
 */
public final class MonsterRules {
    private MonsterRules() {}

    /** The whole creature for a part of it (the hitbox of a dragon is a part entity); anything else as it is. */
    @Nullable
    public static Entity wholeOf(@Nullable Entity entity) {
        if (entity instanceof PartEntity<?> part) {
            return part.getParent();
        }
        if (entity == null) {
            return null;
        }
        Entity parent = ModCompat.MONSTERS.get().parentOf(entity); // the parts of the dragons of Ice and Fire are entities of their own
        return parent != null ? parent : entity;
    }

    /** True if the entity (or the creature that it is a part of) is a major threat. */
    public static boolean isMajor(@Nullable Entity entity) {
        Entity whole = wholeOf(entity);
        return whole != null && ModCompat.MONSTERS.get().isMajorThreat(whole);
    }

    /** True for an NPC that fights from a distance: a bow in the hand, a seat at a siege weapon, or a gunner seat of a ship. */
    public static boolean fightsFromDistance(NpcBase npc) {
        if (npc.getMainHandItem().getItem() instanceof ProjectileWeaponItem || npc.isShipGunner() && npc.getRidingShip() != null) {
            return true;
        }
        return npc instanceof IVehicleUser user && user.isRidingVehicle();
    }

    /** True if archers and crews of siege weapons and ship guns should take this target before nearer ones. */
    public static boolean rangedPrefers(NpcBase npc, @Nullable Entity target) {
        return SettlementConfig.iceAndFireRangedPrefersDragons && isMajor(target) && fightsFromDistance(npc);
    }

    /**
     * True if the NPC keeps away from the threat instead of fighting it: it is a major threat, the NPC is a soldier that fights in melee, it is not brave, and
     * it has no orders (an order to attack is the player's choice). The distance is {@code melee_keep_away_distance}; 0 turns the rule off.
     */
    public static boolean keepsAwayFrom(NpcBase npc, @Nullable Entity threat) {
        return isKeepAwayType(npc) && isMajor(threat);
    }

    /** True for an NPC that keeps away from major threats: a melee soldier that is not brave and has no orders (and the rule is on). */
    public static boolean isKeepAwayType(NpcBase npc) {
        if (SettlementConfig.iceAndFireMeleeKeepAway <= 0 || !(npc instanceof NpcCombat combat) || !ModCompat.MONSTERS.isActive()) {
            return false;
        }
        return !fightsFromDistance(combat) && !combat.hasTrait(Trait.BRAVE) && combat.ordersStack.isEmpty()
                && combat.getCurrentCommand() == NpcCommand.Command.NONE;
    }

    /** True if the settlement has a threat this NPC would fight: any threat, unless the NPC keeps away from dragons and the dragons are all there is. */
    public static boolean hasEngageableThreat(NpcBase npc, Settlement settlement) {
        if (!isKeepAwayType(npc) || !(npc.level() instanceof ServerLevel level)) {
            return true;
        }
        for (UUID id : settlement.getThreatIds()) {
            Entity e = level.getEntity(id);
            if (e instanceof LivingEntity living && living.isAlive() && !isMajor(living)) {
                return true;
            }
        }
        return false;
    }

    public static double keepAwayDistance() {
        return SettlementConfig.iceAndFireMeleeKeepAway;
    }
}
