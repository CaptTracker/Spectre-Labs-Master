/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 10): what the family does to the morale of a member.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.family;

import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.settlement.NpcMorale;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The family factors of {@link NpcMorale}, each a named line of the morale tab ({@code guistrings.npc.morale.<key>}):
 * <ul>
 *   <li>{@code family_partner}: +{@code partner_morale} for a member with a partner;</li>
 *   <li>{@code family_partner_near}: +{@code partner_nearby_morale} while the partner is within {@code partner_nearby_radius} blocks;</li>
 *   <li>{@code family_home}: +{@code family_home_morale} for sleeping in a bed of the home of the household (living together);</li>
 *   <li>{@code family_children}: +{@code children_near_morale} for a parent while one of its children is within {@code children_near_radius} blocks;</li>
 *   <li>{@code family_grief}: minus {@code grief_morale} (fading) after a partner or a child died, for {@code grief_days} days.</li>
 * </ul>
 */
public final class FamilyMorale {
    private FamilyMorale() {}

    /** The family factors of a member; empty when families are off or the member has no family. */
    public static List<NpcMorale.Factor> factors(NpcPlayerOwned npc, ServerLevel level, Settlement settlement, long now) {
        List<NpcMorale.Factor> factors = new ArrayList<>(4);
        if (!SettlementConfig.familiesEnabled) {
            return factors;
        }
        UUID id = npc.getUUID();
        SettlementFamilies families = settlement.getFamilies();
        Optional<UUID> partner = families.partnerOf(id);
        if (partner.isPresent()) {
            if (SettlementConfig.partnerMorale != 0) {
                factors.add(new NpcMorale.Factor("family_partner", SettlementConfig.partnerMorale));
            }
            Entity other = level.getEntity(partner.get());
            if (other != null && other.isAlive() && SettlementConfig.partnerNearbyMorale != 0
                    && npc.distanceToSqr(other) <= (double) SettlementConfig.partnerNearbyRadius * SettlementConfig.partnerNearbyRadius) {
                factors.add(new NpcMorale.Factor("family_partner_near", SettlementConfig.partnerNearbyMorale));
            }
        }
        Optional<Household> household = families.householdOf(id);
        if (household.isPresent() && household.get().hasHome() && SettlementConfig.familyHomeMorale != 0) {
            Optional<BlockPos> bed = settlement.getBedOf(id);
            if (bed.isPresent() && household.get().getHomeBeds().contains(bed.get())) {
                factors.add(new NpcMorale.Factor("family_home", SettlementConfig.familyHomeMorale));
            }
        }
        if (SettlementConfig.childrenNearMorale != 0) {
            double radiusSq = (double) SettlementConfig.childrenNearRadius * SettlementConfig.childrenNearRadius;
            for (UUID child : families.childrenOf(id)) {
                if (!families.isMinor(child)) {
                    continue;
                }
                Entity entity = level.getEntity(child);
                if (entity != null && entity.isAlive() && npc.distanceToSqr(entity) <= radiusSq) {
                    factors.add(new NpcMorale.Factor("family_children", SettlementConfig.childrenNearMorale));
                    break;
                }
            }
        }
        if (npc.isChild() && SettlementConfig.childrenNearMorale != 0) {
            // a child feels safe with a parent within reach (rework phase 10)
            double radiusSq = (double) SettlementConfig.childrenNearRadius * SettlementConfig.childrenNearRadius;
            for (UUID parent : families.parentsOf(id)) {
                Entity entity = level.getEntity(parent);
                if (entity != null && entity.isAlive() && npc.distanceToSqr(entity) <= radiusSq) {
                    factors.add(new NpcMorale.Factor("family_parent_near", SettlementConfig.childrenNearMorale));
                    break;
                }
            }
        }
        int grief = families.griefPenalty(id, now);
        if (grief > 0) {
            factors.add(new NpcMorale.Factor("family_grief", -grief));
        }
        return factors;
    }
}
