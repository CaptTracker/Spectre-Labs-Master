/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/interfaces/IWorker.
 */
package io.github.capttracker.ancientwarfare.api;

/**
 * Mark entities that are capable of doing 'work'.
 *
 * @author Shadowmage
 */
public interface IWorker {

    /**
     * Get worker effectiveness. base == 1. Higher values are more effective at most work-types.
     *
     * @param type the type of work to perform
     */
    float getWorkEffectiveness(IWorkSite.WorkType type);

    boolean canWorkAt(IWorkSite.WorkType type);

    double getWorkRangeSq();
}
