/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/render/RenderNpcBase (getIconFor, renderIcon and the icon row of renderNpcAITasks).
 */
package io.github.capttracker.ancientwarfarenpc.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.ai.NpcTaskStatus;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The 16 x 16 status icons that float above the NPCs the viewer may command, one for each bit that is set in the NPC's synced task field
 * ({@link NpcTaskStatus}). The 14 textures are in {@code textures/entity/npc/ai/}.
 *
 * <p>The quads are drawn with the vanilla text render type (translucent, lightmap) into the entity buffer source, like the glyphs of a name tag,
 * instead of 1.12.2's immediate mode tessellator. The caller has already moved the pose stack to the point above the head and turned it to face
 * the camera in "text space" (one unit is one pixel, y points down).
 */
public final class NpcStatusIcons {
    /** Width and height of an icon in pixels (text space). */
    public static final int ICON_SIZE = 16;
    /** Distance between the centres of two icons. */
    public static final int ICON_SPACING = 20;
    /** The icon centres are this far above the origin of the row (text space: negative is up). */
    public static final int ICON_Y = -16;

    private static final Map<NpcTaskStatus, ResourceLocation> ICONS = new EnumMap<>(NpcTaskStatus.class);

    static {
        ICONS.put(NpcTaskStatus.ATTACK, icon("task_attack"));
        ICONS.put(NpcTaskStatus.UPKEEP, icon("task_upkeep"));
        ICONS.put(NpcTaskStatus.IDLE_HUNGRY, icon("task_upkeep2"));
        ICONS.put(NpcTaskStatus.GO_HOME, icon("task_home"));
        ICONS.put(NpcTaskStatus.WORK, icon("task_work"));
        ICONS.put(NpcTaskStatus.PATROL, icon("task_patrol"));
        ICONS.put(NpcTaskStatus.GUARD, icon("task_guard"));
        ICONS.put(NpcTaskStatus.FOLLOW, icon("task_follow"));
        ICONS.put(NpcTaskStatus.WANDER, icon("task_wander"));
        ICONS.put(NpcTaskStatus.MOVE, icon("task_move"));
        ICONS.put(NpcTaskStatus.ALARM, icon("task_alarm"));
        ICONS.put(NpcTaskStatus.FLEE, icon("task_flee"));
        ICONS.put(NpcTaskStatus.SLEEP, icon("task_sleep"));
        ICONS.put(NpcTaskStatus.RAIN, icon("task_rain"));
    }

    private NpcStatusIcons() {}

    private static ResourceLocation icon(String name) {
        return Ids.of(AncientWarfareNPC.MOD_ID, "textures/entity/npc/ai/" + name + ".png");
    }

    /** The texture of the icon of a task. */
    public static ResourceLocation getIcon(NpcTaskStatus status) {
        return ICONS.get(status);
    }

    /**
     * Draws the row of icons, centred on the origin of the pose stack.
     *
     * <p>Fix: 1.12.2 started the row at {@code -(count * 10 / 2)} and stepped by 20, which left the row off centre (a single icon sat 5 pixels
     * to the left of the NPC). The row is centred now.
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, List<NpcTaskStatus> tasks) {
        int count = tasks.size();
        for (int i = 0; i < count; i++) {
            float centreX = (i - (count - 1) / 2.0F) * ICON_SPACING;
            drawIcon(poseStack, buffers, getIcon(tasks.get(i)), centreX, ICON_Y);
        }
    }

    private static void drawIcon(PoseStack poseStack, MultiBufferSource buffers, ResourceLocation texture, float x, float y) {
        float half = ICON_SIZE / 2.0F;
        Matrix4f pose = poseStack.last().pose();
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(texture));
        int light = LightTexture.FULL_BRIGHT;
        consumer.addVertex(pose, x - half, y - half, 0).setColor(255, 255, 255, 255).setUv(0, 0).setLight(light);
        consumer.addVertex(pose, x - half, y + half, 0).setColor(255, 255, 255, 255).setUv(0, 1).setLight(light);
        consumer.addVertex(pose, x + half, y + half, 0).setColor(255, 255, 255, 255).setUv(1, 1).setLight(light);
        consumer.addVertex(pose, x + half, y - half, 0).setColor(255, 255, 255, 255).setUv(1, 0).setLight(light);
    }
}
