/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): a staffed worksite whose work is done from a boat (the fishing dock).
 */
package io.github.capttracker.ancientwarfare.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A staffed worksite ({@link IWorksiteTasks}) whose tasks lie out on the water and are done from a boat the site owns. The worker walks to the
 * {@link #getBoat() boat}, boards it, sails along a {@link #planRoute route} to the target of its task, does the task from the boat and sails back to the
 * {@link #getBerth() berth} when the trip is over. The site lists one task at a time, so one worker uses the boat.
 */
public interface IBoatWorksite extends IWorksiteTasks {

    /** The boat of the site if it exists, is loaded and alive; null while the site has none. */
    @Nullable
    Entity getBoat();

    /** The water block where the boat lies when it is not out. */
    BlockPos getBerth();

    /** A place on land beside the water where the worker stands before it boards and after it gets off (it empties its pouch at the site from there). */
    BlockPos getLanding();

    /** The greatest distance from the site that a task target may be (blocks). */
    int getDockRange();

    /** The number of tasks a worker does on one trip before it sails home. */
    int getTripLength();

    /**
     * The waypoints (water positions, the last one at or next to the target) that the boat steers through to get from {@code from} to {@code target},
     * or null if there is no way over deep enough water.
     */
    @Nullable
    List<Vec3> planRoute(Vec3 from, BlockPos target);
}
