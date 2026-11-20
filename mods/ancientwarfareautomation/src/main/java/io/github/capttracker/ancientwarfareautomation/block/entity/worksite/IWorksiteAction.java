/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/tile/worksite/IWorksiteAction.
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

/** One thing a worksite can do next (plant, harvest, mine a block, ...); it only knows what it costs. */
public interface IWorksiteAction {
    /**
     * @param efficiencyBonusFactor the summed bonus of the installed tool upgrades, see
     *                              {@code IWorkSite.WorksiteImplementation.getEfficiencyFactor}
     * @return the torque energy the action uses up
     */
    double getEnergyConsumed(double efficiencyBonusFactor);
}
