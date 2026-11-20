/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcAIRideHorse.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * An NPC rides a horse: the horse's own goals are taken away while the NPC rides it, its speed is multiplied and its follow range is widened by 24
 * (so that long paths work), and everything is put back when the NPC gets off or dies ({@link #onKilled()}).
 *
 * <h2>How the horse is driven in 1.21.1</h2>
 * {@code Mob.getNavigation()} and {@code getMoveControl()} of a mob that rides a controllable mob are the mount's (the passenger is its
 * {@code getControllingPassenger}), and {@code Mob.updateControlFlags} disables the MOVE, LOOK and JUMP goals of the mount. So the custom navigator
 * that forwarded the paths to the horse in 1.12.2 is not needed; the goals are still taken away (all of them, also the unflagged ones) as before.
 * The horse is not given a saddle item (1.12.2 only set a flag): its drops stay the same, the saddle is not drawn.
 *
 * <h2>Changes from 1.12.2</h2>
 * <ul>
 *   <li>The goal keeps running while the NPC rides the horse ({@link #canContinueToUse()}); with the 1.12.2 test ({@code horse == null}) it stopped at its
 *       first tick, gave the horse its goals back and started again, over and over.</li>
 *   <li>Attribute modifiers have ids now (the move speed one and the follow range one).</li>
 * </ul>
 */
public class NpcAIRideHorse<T extends NpcBase> extends NpcAI<T> {
    private static final ResourceLocation FOLLOW_RANGE_MODIFIER_ID = Ids.of(AncientWarfareNPC.MOD_ID, "npc_horse_path_extension");
    private static final ResourceLocation MOVE_SPEED_MODIFIER_ID = Ids.of(AncientWarfareNPC.MOD_ID, "npc_ride_speed");
    private static final AttributeModifier FOLLOW_RANGE_MODIFIER = new AttributeModifier(FOLLOW_RANGE_MODIFIER_ID, 24.d, AttributeModifier.Operation.ADD_VALUE);

    private final AttributeModifier moveSpeedModifier;
    @Nullable
    protected Mob horse;
    private final List<WrappedGoal> horseAI = new ArrayList<>();

    public NpcAIRideHorse(T npc, double speedFactor) {
        super(npc);
        moveSpeedModifier = new AttributeModifier(MOVE_SPEED_MODIFIER_ID, speedFactor, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    @Override
    public boolean canUse() {
        return super.canUse() && shouldRideHorse();
    }

    @Override
    public boolean canContinueToUse() {
        return npc.getIsAIEnabled() && horse != null && !horse.isRemoved() && npc.getVehicle() == horse;
    }

    protected boolean shouldRideHorse() {
        return horse == null && npc.getVehicle() instanceof Horse;
    }

    @Override
    public void start() {
        if (npc.getVehicle() instanceof Mob mount) {
            horse = mount;
            onMountHorse();
        }
    }

    protected void onMountHorse() {
        removeHorseAI();
        if (horse instanceof AbstractHorse abstractHorse) {
            abstractHorse.setEating(false);
            abstractHorse.setStanding(false);
        }
        applyModifiers();
    }

    /** The NPC died or was repacked: the horse gets its goals and attributes back. */
    public void onKilled() {
        if (horse != null) {
            onDismountHorse();
            horse = null;
        }
    }

    protected void onDismountHorse() {
        addHorseAI();
        if (horse instanceof AbstractHorse) {
            removeModifiers();
        }
    }

    private void applyModifiers() {
        if (horse instanceof AbstractHorse abstractHorse) {
            removeModifiers();
            AttributeInstance speed = abstractHorse.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                speed.addTransientModifier(moveSpeedModifier);
            }
            AttributeInstance followRange = abstractHorse.getAttribute(Attributes.FOLLOW_RANGE);
            if (followRange != null) {
                followRange.addTransientModifier(FOLLOW_RANGE_MODIFIER);
            }
        }
    }

    private void removeModifiers() {
        if (horse == null) {
            return;
        }
        AttributeInstance speed = horse.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(MOVE_SPEED_MODIFIER_ID);
        }
        AttributeInstance followRange = horse.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.removeModifier(FOLLOW_RANGE_MODIFIER_ID);
        }
    }

    private void removeHorseAI() {
        if (horse == null) {
            return;
        }
        horseAI.clear();
        horseAI.addAll(horse.goalSelector.getAvailableGoals());
        for (WrappedGoal task : horseAI) {
            horse.goalSelector.removeGoal(task.getGoal());
        }
    }

    private void addHorseAI() {
        if (horse != null && horse.goalSelector.getAvailableGoals().isEmpty()) {
            for (WrappedGoal task : horseAI) {
                horse.goalSelector.addGoal(task.getPriority(), task.getGoal());
            }
        }
        horseAI.clear();
    }
}
