/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/render/ModelNpc.
 */
package io.github.capttracker.ancientwarfarenpc.client.render;

import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

/**
 * The player-style model of the NPCs, with wide (4 pixel) or slim (3 pixel, "Alex") arms: the renderer bakes the vanilla layers
 * {@code ModelLayers.PLAYER} and {@code ModelLayers.PLAYER_SLIM} into two instances and picks one per frame.
 *
 * <p>1.12.2 overrode {@code setLivingAnimations} to set the arm poses; the 1.21.1 model has no such hook (the poses of the vanilla player are set
 * by its renderer before the model is animated), so {@link #updateArmPoses} does the same job and the renderer calls it before each draw. As in
 * the original only two poses are used: a bow that is being drawn in the main hand ({@code BOW_AND_ARROW}) and a shield that is being held up in the
 * off hand ({@code BLOCK}). Every other held item leaves the arm in the walking swing, exactly like the old {@code ModelBiped}.
 */
public class NpcModel<T extends NpcBase> extends PlayerModel<T> {
    public NpcModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    /** Sets the arm poses (and the sneak flag) from what the NPC is using right now. */
    public void updateArmPoses(T npc) {
        ArmPose mainArmPose = ArmPose.EMPTY;
        ArmPose offArmPose = ArmPose.EMPTY;
        if (npc.getUseItemRemainingTicks() > 0) {
            InteractionHand usedHand = npc.getUsedItemHand();
            ItemStack mainHandItem = npc.getMainHandItem();
            ItemStack offHandItem = npc.getOffhandItem();
            if (usedHand == InteractionHand.MAIN_HAND && !mainHandItem.isEmpty() && mainHandItem.getUseAnimation() == UseAnim.BOW) {
                mainArmPose = ArmPose.BOW_AND_ARROW;
            }
            if (usedHand == InteractionHand.OFF_HAND && !offHandItem.isEmpty() && offHandItem.getUseAnimation() == UseAnim.BLOCK) {
                offArmPose = ArmPose.BLOCK;
            }
        }

        if (npc.getMainArm() == HumanoidArm.RIGHT) {
            rightArmPose = mainArmPose;
            leftArmPose = offArmPose;
        } else {
            leftArmPose = mainArmPose;
            rightArmPose = offArmPose;
        }
        crouching = npc.isCrouching();
    }
}
