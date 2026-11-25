/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: the energy cost of a worksite action. Replaces the calls to
 * IWorkSite.WorksiteImplementation.getEnergyPerActivation that every concrete worksite of the original made.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import io.github.capttracker.ancientwarfare.config.AWCoreStatics;

/**
 * The cost of the actions of the concrete worksites.
 *
 * <p><b>Fixed bug (efficiency upgrades).</b> The original cost was
 * {@code energyPerWorkUnit * 1.f - efficiencyBonusFactor}. Because of the operator precedence the bonus of the tool
 * upgrades was <i>subtracted</i> from the 50 energy of a work unit instead of scaling it, so the upgrades saved
 * about one percent. The cost is now {@code energyPerWorkUnit * (1 - efficiencyBonusFactor)}: a bonus of 0.25 makes
 * an action 25 percent cheaper. Core's {@code IWorkSite.WorksiteImplementation.getEnergyPerActivation} still has
 * the original formula (Core is not part of this slice); the concrete worksites use this class instead.
 *
 * <p>An action is identified by object identity in {@code processAction} (as in the original, where each action was
 * a static constant), so every {@link #full()} / {@link #fraction(double)} call makes a new object that the worksite
 * keeps in a {@code static final} field.
 */
public final class WorksiteActions {
    private WorksiteActions() {}

    /** The torque a full work unit costs with the given summed upgrade bonus. */
    public static double energyPerActivation(double efficiencyBonusFactor) {
        return AWCoreStatics.energyPerWorkUnit * (1.0D - efficiencyBonusFactor);
    }

    /** An action that costs one work unit. */
    public static IWorksiteAction full() {
        return new Action(1.0D);
    }

    /** An action that costs a work unit divided by {@code divisor} (crop farm actions 5, trunk chopping 2, leaf chopping 10). */
    public static IWorksiteAction fraction(double divisor) {
        return new Action(divisor);
    }

    private static final class Action implements IWorksiteAction {
        private final double divisor;

        private Action(double divisor) {
            this.divisor = divisor;
        }

        @Override
        public double getEnergyConsumed(double efficiencyBonusFactor) {
            return energyPerActivation(efficiencyBonusFactor) / divisor;
        }
    }
}
