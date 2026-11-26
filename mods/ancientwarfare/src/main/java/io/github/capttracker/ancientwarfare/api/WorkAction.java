/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 3): what a worker does at one target of a worksite.
 */
package io.github.capttracker.ancientwarfare.api;

/**
 * The actions of a {@link WorkTask}. The breaking actions advance the crack overlay of the target block while the worker swings; the others only
 * show particles and sounds. The worksite decides what an action really does when the worker completes it ({@link IWorksiteTasks#completeTask}).
 */
public enum WorkAction {
    TILL(false), PLANT(false), HARVEST(true), CHOP(true), MINE(true), PLACE(false), SHEAR(false), MILK(false), BREED(false), CULL(false),
    FISH(false), COLLECT(false), SMELT(false),
    /** Rework phase 8: the work at a sawmill (logs into planks and the things made of planks). */
    SAW(false),
    /** Rework phase 8: the work at a bakery. */
    BAKE(false),
    /** Rework phase 8: the work at a smithy (tools, weapons and armour, and repairs). */
    SMITH(false),
    /** Rework phase 8: the work at a tannery. */
    TAN(false),
    /** Bone meal on a crop (not in the original list of the spec: the crop farm has this fourth thing to do). */
    FERTILIZE(false);

    private final boolean breaking;

    WorkAction(boolean breaking) {
        this.breaking = breaking;
    }

    /** True if the worker breaks the target block (the block's crack overlay advances and the worker's tool speed counts). */
    public boolean isBreaking() {
        return breaking;
    }
}
