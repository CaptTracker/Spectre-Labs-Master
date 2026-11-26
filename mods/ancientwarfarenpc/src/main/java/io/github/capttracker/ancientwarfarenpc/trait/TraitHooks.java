/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 7): the places a devout NPC likes to be near.
 */
package io.github.capttracker.ancientwarfarenpc.trait;

import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPriest;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The reserved hook of the devout trait. A devout NPC gets a morale lift near a priest of its settlement; the buildings of phase 9 (a chapel and so on) add
 * their own places with {@link #registerDevoutSite}.
 */
public final class TraitHooks {
    /** A kind of place that lifts the morale of a devout NPC. */
    @FunctionalInterface
    public interface DevoutSite {
        /** Whether the NPC is near such a place (within {@code radius} blocks). Server side. */
        boolean isNear(NpcPlayerOwned npc, ServerLevel level, int radius);
    }

    private static final List<DevoutSite> SITES = new CopyOnWriteArrayList<>();

    static {
        SITES.add((npc, level, radius) -> !level.getEntitiesOfClass(NpcPriest.class, npc.getBoundingBox().inflate(radius),
                priest -> priest != npc && priest.isAlive() && (npc.getSettlementId() == null || npc.getSettlementId().equals(priest.getSettlementId()))).isEmpty());
    }

    private TraitHooks() {}

    public static void registerDevoutSite(DevoutSite site) {
        SITES.add(site);
    }

    /** True if a priest of the settlement, or a registered place, is within the devout radius of the config. */
    public static boolean isNearDevoutSite(NpcPlayerOwned npc, ServerLevel level) {
        int radius = SettlementConfig.traitDevoutRadius;
        for (DevoutSite site : SITES) {
            if (site.isNear(npc, level, radius)) {
                return true;
            }
        }
        return false;
    }
}
