/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 4): the goal that a hitched horse runs.
 */
package io.github.capttracker.ancientwarfarevehicle.entity.hitch;

import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

import java.util.EnumSet;

/**
 * Added to a horse's goal selector at priority 0 while it is hitched to a cart. It takes the MOVE flag, so the horse neither wanders off nor flees
 * (it is tied to the cart), and does what {@link CartHitch#controlHorse} says: the cart's driver steers it, it follows a leader or a destination,
 * trails its slot beside the lead horse, or stands. It is idle while somebody rides the horse (the rider drives) and once the cart is gone, so a goal
 * left on a horse whose cart was unloaded does nothing.
 */
public final class HitchGoal extends Goal {
    private final CartHitch hitch;
    private final AbstractHorse horse;

    HitchGoal(CartHitch hitch, AbstractHorse horse) {
        this.hitch = hitch;
        this.horse = horse;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    /** The cart this goal belongs to. */
    public VehicleBase getCart() {
        return hitch.getVehicle();
    }

    /** True while the cart still holds this horse. */
    boolean isActive() {
        return !hitch.getVehicle().isRemoved() && hitch.holds(horse);
    }

    @Override
    public boolean canUse() {
        return isActive() && !horse.isVehicle() && !horse.isBaby();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public void tick() {
        hitch.controlHorse(horse);
    }

    @Override
    public void stop() {
        horse.getNavigation().stop();
    }
}
