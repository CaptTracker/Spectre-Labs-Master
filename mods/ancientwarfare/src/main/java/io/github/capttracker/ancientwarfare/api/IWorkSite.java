/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/interfaces/IWorkSite.
 */
package io.github.capttracker.ancientwarfare.api;

import io.github.capttracker.ancientwarfare.config.AWCoreStatics;
import io.github.capttracker.ancientwarfare.owner.IOwnable;
import io.github.capttracker.ancientwarfare.upgrade.WorksiteUpgrade;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.Set;

public interface IWorkSite extends ITorque.ITorqueTile, IBlockBreakHandler, IOwnable {

    /**
     * Workers should call this before calling doWork() to make sure that the site
     * actually has work to do.
     */
    boolean hasWork();

    /** Can be called by a worker if hasWork() returns true. */
    void addEnergyFromWorker(IWorker worker);

    void addEnergyFromPlayer(Player player);

    /**
     * Called by workers to validate work-type when IWorker.canWorkAt(IWorkSite) is called.
     * Workers should be responsible for maintaining their own list of acceptable work types.
     */
    WorkType getWorkType();

    @Nullable
    Team getTeam();

    Set<WorksiteUpgrade> getUpgrades();

    Set<WorksiteUpgrade> getValidUpgrades();

    /**
     * Add the input upgrade to the present upgrade set. Apply any necessary bonuses at this time.
     * Calling this method with an upgrade that is already present has undefined results.
     */
    void addUpgrade(WorksiteUpgrade upgrade);

    /**
     * Remove the input upgrade from the present upgrade set. Remove any bonuses that it had applied.
     * Calling this method with an upgrade that is not present has undefined results.
     */
    void removeUpgrade(WorksiteUpgrade upgrade);

    enum WorkType {
        /*
         * any change to reg. names will fubar npc-leveling system, as they use these names to store accumulated xp
         */
        MINING("work_type.mining"), FARMING("work_type.farming"), FORESTRY("work_type.forestry"), CRAFTING("work_type.crafting"), RESEARCH("work_type.research"),
        /** Rework phase 8: the smithy (worked by the smith role). */
        SMITHING("work_type.smithing"),
        /** Rework phase 8: the tannery (worked by the tanner role). */
        TANNING("work_type.tanning"),
        /** Rework phase 9: building (the builder role; there is no worksite block, builders work the build queue of the settlement). */
        BUILDING("work_type.building"), NONE("work_type.none");
        public final String regName;

        WorkType(String regName) {
            this.regName = regName;
        }
    }

    /**
     * Static methods for default implementation of worksite logic.
     *
     * @author Shadowmage
     */
    final class WorksiteImplementation {

        private WorksiteImplementation() {
        }

        /**
         * Kept exactly as the original, which subtracts the bonus factor from the energy cost instead of scaling the
         * cost by it (a known bug, listed as a rework candidate: efficiency upgrades barely matter).
         */
        public static double getEnergyPerActivation(double efficiencyBonusFactor) {
            return AWCoreStatics.energyPerWorkUnit * 1.f - efficiencyBonusFactor;
        }

        public static double getEfficiencyFactor(EnumSet<WorksiteUpgrade> upgrades) {
            double efficiencyBonusFactor = 0.d;
            if (upgrades.contains(WorksiteUpgrade.ENCHANTED_TOOLS_1)) {
                efficiencyBonusFactor += 0.05;
            }
            if (upgrades.contains(WorksiteUpgrade.ENCHANTED_TOOLS_2)) {
                efficiencyBonusFactor += 0.1;
            }
            if (upgrades.contains(WorksiteUpgrade.TOOL_QUALITY_1)) {
                efficiencyBonusFactor += 0.05;
            }
            if (upgrades.contains(WorksiteUpgrade.TOOL_QUALITY_2)) {
                efficiencyBonusFactor += 0.15;
            }
            if (upgrades.contains(WorksiteUpgrade.TOOL_QUALITY_3)) {
                efficiencyBonusFactor += 0.25;
            }
            return efficiencyBonusFactor;
        }
    }
}
