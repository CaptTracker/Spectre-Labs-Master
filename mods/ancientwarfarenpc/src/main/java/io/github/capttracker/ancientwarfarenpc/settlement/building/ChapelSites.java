/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9, builders): a finished chapel is a place that lifts the morale of a devout NPC.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building;

import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.settlement.Settlement;
import io.github.capttracker.ancientwarfarenpc.trait.TraitHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The chapels of a settlement for the devout trait ({@link TraitHooks}): a finished build whose design is of the category {@code chapel} (or has the tag
 * {@code chapel} or {@code devout}) counts as a place a devout NPC of the settlement can be near. The finished builds are saved with the settlement, so a
 * chapel counts again after a restart without any further registration.
 */
public final class ChapelSites {
    private ChapelSites() {}

    /** Registers the chapel place with the devout hook. Call once at start up. */
    public static void register() {
        TraitHooks.registerDevoutSite(ChapelSites::isNearChapel);
    }

    /** True if the design is a chapel. */
    public static boolean isChapel(BuildingDesign design) {
        return design.getCategory() == DesignCategory.CHAPEL || design.hasTag("chapel") || design.hasTag("devout");
    }

    private static boolean isNearChapel(NpcPlayerOwned npc, ServerLevel level, int radius) {
        Settlement settlement = npc.getSettlement().orElse(null);
        if (settlement == null || !settlement.getDimension().equals(level.dimension())) {
            return false;
        }
        for (BuildProject project : settlement.getBuilding().getProjects()) {
            if (project.getKind() != BuildProject.Kind.BUILD || !project.isDone()) {
                continue;
            }
            BuildingDesign design = BuildJobs.designOf(project);
            if (design == null || !isChapel(design)) {
                continue;
            }
            int[] size = project.getSize();
            BlockPos origin = project.getOrigin();
            double cx = origin.getX() + size[0] / 2.0D;
            double cz = origin.getZ() + size[2] / 2.0D;
            double reach = radius + Math.max(size[0], size[2]) / 2.0D;
            double dx = npc.getX() - cx;
            double dz = npc.getZ() - cz;
            if (dx * dx + dz * dz <= reach * reach && Math.abs(npc.getY() - origin.getY()) <= radius) {
                return true;
            }
        }
        return false;
    }
}
