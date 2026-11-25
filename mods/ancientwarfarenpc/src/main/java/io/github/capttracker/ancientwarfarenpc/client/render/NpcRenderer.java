/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/render/RenderNpcBase and npc/render/RenderNpcFaction.
 */
package io.github.capttracker.ancientwarfarenpc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.capttracker.ancientwarfarenpc.ai.NpcTaskStatus;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.scores.PlayerTeam;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws every NPC (player-owned and faction) as a player-style humanoid: the skin of the NPC ({@link NpcBase#getTexture()}), the wide or the
 * slim model ({@code getSkinSettings().renderFemaleModel(isFemale())}), armour, held items (the vanilla {@code ItemInHandLayer} of the humanoid
 * renderer draws the main hand item and the shield in the off hand, as the 1.12.2 held item layer did), the sleeping pose, the name label and
 * the row of status icons. Faction NPCs are scaled to their random size.
 *
 * <p>Changes from 1.12.2:
 * <ul>
 *   <li>One class for both renderers ({@code RenderNpcBase} and {@code RenderNpcFaction}): the faction scaling is applied when the NPC is a
 *       {@link NpcFaction}.</li>
 *   <li>The sleeping pose is the vanilla one ({@code Pose.SLEEPING} and the bed orientation); the manual bed rotation and the offset of the body
 *       are not needed because the NPC now sleeps like a player.</li>
 *   <li>The labels and icons are drawn through the buffer source with {@code Font.drawInBatch} (a see-through pass and a normal pass, as the
 *       vanilla name tag) instead of immediate mode quads. The vanilla name tag is switched off. The label is full bright, as the original had
 *       lighting off, and keeps its dark background of 25 percent.</li>
 *   <li>Fix: the team prefix and suffix of the label compared the NPC's team with the team <i>named like the player</i> (a 1.12.2 lookup by
 *       the wrong key that was practically always empty); the player's real team is used, so NPCs of the viewer's own team are left plain.</li>
 *   <li>Not ported: {@code transformHeldFull3DItemLayer} (the skeleton arm offset the original copied); the player-style arms need none and the
 *       slim arms are offset by the model's {@code translateToHand}.</li>
 * </ul>
 *
 * @param <T> NPC entity class
 */
public class NpcRenderer<T extends NpcBase> extends HumanoidMobRenderer<T, NpcModel<T>> {
    private static final float SHADOW_RADIUS = 0.6F;
    private static final double LABEL_RANGE_SQ = 64.0D * 64.0D;
    /** Text space scale of the name label (1.12.2: 0.016666668 * 0.8). */
    private static final float LABEL_SCALE = 0.016666668F * 0.8F;
    /** Text space scale of the icon row (1.12.2: 0.016666668 * 1.6). */
    private static final float ICON_SCALE = 0.016666668F * 1.6F;
    /** Background of the label: black at 25 percent. */
    private static final int LABEL_BACKGROUND = 0x40000000;

    private final NpcModel<T> wideModel;
    private final NpcModel<T> slimModel;

    public NpcRenderer(EntityRendererProvider.Context context) {
        this(context, new NpcModel<>(context.bakeLayer(ModelLayers.PLAYER), false), new NpcModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true));
    }

    private NpcRenderer(EntityRendererProvider.Context context, NpcModel<T> wideModel, NpcModel<T> slimModel) {
        super(context, wideModel, SHADOW_RADIUS);
        this.wideModel = wideModel;
        this.slimModel = slimModel;
        // the armour models are the same for the wide and the slim arms
        HumanoidArmorModel<T> inner = new HumanoidArmorModel<T>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR));
        HumanoidArmorModel<T> outer = new HumanoidArmorModel<T>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
        addLayer(new HumanoidArmorLayer<T, NpcModel<T>, HumanoidArmorModel<T>>(this, inner, outer, context.getModelManager()));
    }

    @Override
    public void render(T npc, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        NpcModel<T> chosen = npc.getSkinSettings().renderFemaleModel(npc.isFemale()) ? slimModel : wideModel;
        this.model = chosen;
        chosen.updateArmPoses(npc);
        super.render(npc, entityYaw, partialTicks, poseStack, buffer, packedLight);
        renderOverlays(npc, poseStack, buffer);
    }

    @Override
    public ResourceLocation getTextureLocation(T npc) {
        return npc.getTexture();
    }

    /** Faction NPCs: the size and width of the NPC's hit box (1.12.2 {@code RenderNpcFaction.preRenderCallback}). */
    @Override
    protected void scale(T npc, PoseStack poseStack, float partialTickTime) {
        if (!(npc instanceof NpcFaction faction)) {
            return;
        }
        float scale = faction.getRenderSizeModifier();
        float widthScale = faction.getWidthModifier();
        if (Math.abs(scale - 1.0F) < 1.0E-5F && Math.abs(widthScale - 1.0F) < 1.0E-5F) {
            return;
        }
        poseStack.scale(widthScale, scale, widthScale);
        if (npc.isPassenger() && scale < 1.0F && !(npc.getVehicle() instanceof AbstractHorse)) {
            float ratio = (1.0F - scale) * (1.0F - scale);
            poseStack.translate(0, -3.0F * ratio, 0);
        }
    }

    /** The vanilla name tag is replaced by {@link #renderOverlays}. */
    @Override
    protected boolean shouldShowName(T npc) {
        return false;
    }

    // ---- label and status icons ----

    private void renderOverlays(T npc, PoseStack poseStack, MultiBufferSource buffer) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Entity camera = minecraft.getCameraEntity();
        if (player == null || camera == null) {
            return;
        }
        if (this.entityRenderDispatcher.distanceToSqr(npc) > LABEL_RANGE_SQ) {
            return;
        }

        boolean hostile = npc.isHostileTowards(player);
        boolean canBeCommandedBy = false;
        boolean drawLabel;
        boolean drawIcons = false;
        if (hostile) {
            drawLabel = AWNPCStatics.renderHostileNames;
        } else {
            canBeCommandedBy = npc.hasCommandPermissions(player.getUUID(), player.getGameProfile().getName());
            drawLabel = AWNPCStatics.renderFriendlyNames;
            drawIcons = canBeCommandedBy && AWNPCStatics.renderAI;
        }
        if (!drawLabel && !drawIcons) {
            return;
        }
        if (!npc.hasLineOfSight(camera)) {
            return;
        }

        if (drawLabel) {
            Component name = getNameForRender(npc, hostile);
            if (hostile) {
                if (AWNPCStatics.renderTeamColors) {
                    name = withTeam(npc, player, name);
                }
                drawLabel(npc, name, 0x20FF0000, 0xFFFF0000, poseStack, buffer);
            } else {
                if (AWNPCStatics.renderTeamColors) {
                    name = withTeam(npc, player, name);
                } else if (!canBeCommandedBy) {
                    name = Component.empty().append(name).withStyle(ChatFormatting.DARK_GRAY);
                }
                drawLabel(npc, name, 0x20FFFFFF, 0xFFFFFFFF, poseStack, buffer);
            }
        }
        if (drawIcons) {
            drawStatusIcons(npc, poseStack, buffer);
        }
    }

    private Component getNameForRender(T npc, boolean hostile) {
        Component name = npc.getName();
        boolean addHealth = hostile ? AWNPCStatics.renderHostileHealth : AWNPCStatics.renderFriendlyHealth;
        if (addHealth) {
            return Component.empty().append(name).append(" " + String.format("%.1f", npc.getHealth()));
        }
        return name;
    }

    /** Team prefix and suffix around the name, unless the NPC is in the viewer's own team (or has none). */
    private static Component withTeam(NpcBase npc, LocalPlayer player, Component name) {
        PlayerTeam npcTeam = npc.getTeam();
        if (npcTeam != null && npcTeam != player.getTeam()) {
            return Component.empty().append(npcTeam.getPlayerPrefix()).append(name).append(npcTeam.getPlayerSuffix());
        }
        return name;
    }

    /** Moves the pose stack to a point above the NPC's head that faces the camera, in text space (pixels, y down). */
    private void beginBillboard(T npc, PoseStack poseStack, float yOffset, float scale) {
        poseStack.pushPose();
        poseStack.translate(0.0F, npc.getBbHeight() + yOffset + 0.5F, 0.0F);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.scale(scale, -scale, scale);
    }

    private void drawLabel(T npc, Component text, int seeThroughColor, int color, PoseStack poseStack, MultiBufferSource buffer) {
        float heightScalingOffset = npc.getBbHeight() / 6 - 0.3F;
        beginBillboard(npc, poseStack, offsetForRiddenEntity(npc, heightScalingOffset), LABEL_SCALE);
        Matrix4f pose = poseStack.last().pose();
        Font font = getFont();
        float x = -font.width(text) / 2.0F;
        // first the faint see-through text with the dark background, then the opaque text that only shows where it is not hidden
        font.drawInBatch(text, x, 0, seeThroughColor, false, pose, buffer, Font.DisplayMode.SEE_THROUGH, LABEL_BACKGROUND, LightTexture.FULL_BRIGHT);
        font.drawInBatch(text, x, 0, color, false, pose, buffer, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private void drawStatusIcons(T npc, PoseStack poseStack, MultiBufferSource buffer) {
        List<NpcTaskStatus> tasks = NpcTaskStatus.of(npc.getAITasks());
        if (tasks.isEmpty()) {
            return;
        }
        beginBillboard(npc, poseStack, 0.0F, ICON_SCALE);
        NpcStatusIcons.render(poseStack, buffer, tasks);
        poseStack.popPose();
    }

    private static float offsetForRiddenEntity(NpcBase npc, float heightScalingOffset) {
        float result = heightScalingOffset;
        if (npc.isPassenger() && npc.getVehicle() instanceof Pig) {
            result += 0.4F;
        }
        return result;
    }
}
