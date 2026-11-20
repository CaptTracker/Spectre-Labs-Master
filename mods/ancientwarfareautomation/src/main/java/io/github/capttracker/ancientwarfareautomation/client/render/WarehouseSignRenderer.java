/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/render/WarehouseStockViewerRenderer and WarehouseStockLinkerRenderer
 * (two copies of one class, merged into this one).
 */
package io.github.capttracker.ancientwarfareautomation.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStockLinkerBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.warehouse.WarehouseStockViewerBlockEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Draws up to ten filter lines (item, name, count) on the front face of the warehouse stock viewer and stock linker
 * signs, at 200 units per block like the original (which borrowed the GUI item drawing and squashed it flat). The text
 * is white and unlit. The direction comes from the {@code facing} of the block state (1.12.2: the block metadata).
 */
public class WarehouseSignRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    private static final int MAX_LINES = 10;
    private static final int MAX_NAME_LENGTH = 20;
    private static final float UNIT = 0.0050F;//200 units (pixels) per block

    /** One line of a sign: the item that is counted and the count. */
    public record Line(ItemStack item, int quantity) {
    }

    private final Font font;
    private final ItemRenderer itemRenderer;
    private final Function<T, List<Line>> lines;

    public WarehouseSignRenderer(BlockEntityRendererProvider.Context context, Function<T, List<Line>> lines) {
        this.font = context.getFont();
        this.itemRenderer = context.getItemRenderer();
        this.lines = lines;
    }

    /** The renderer of the stock viewer sign. */
    public static WarehouseSignRenderer<WarehouseStockViewerBlockEntity> forViewer(BlockEntityRendererProvider.Context context) {
        return new WarehouseSignRenderer<>(context, viewer -> {
            List<Line> result = new ArrayList<>();
            for (WarehouseStockViewerBlockEntity.WarehouseStockFilter filter : viewer.getFilters()) {
                result.add(new Line(filter.getFilterItem(), filter.getQuantity()));
            }
            return result;
        });
    }

    /** The renderer of the stock linker sign. */
    public static WarehouseSignRenderer<WarehouseStockLinkerBlockEntity> forLinker(BlockEntityRendererProvider.Context context) {
        return new WarehouseSignRenderer<>(context, linker -> {
            List<Line> result = new ArrayList<>();
            for (WarehouseStockLinkerBlockEntity.WarehouseStockFilter filter : linker.getFilters()) {
                result.add(new Line(filter.getFilterItem(), filter.getQuantity()));
            }
            return result;
        });
    }

    @Override
    public void render(T sign, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = sign.getBlockState();
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        List<Line> signLines = lines.apply(sign);
        if (signLines.isEmpty()) {
            return;
        }
        Direction d = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();

        poseStack.pushPose();
        poseStack.translate(0.5F, 1.0F, 0.5F);//translate the point to the top-center of the block
        poseStack.mulPose(Axis.YP.rotationDegrees(getRotationFromDirection(d)));//rotate for rotation
        poseStack.translate(0.5F, 0.0F, 0.5F);//translate to top-left corner
        poseStack.translate(0.0F, -0.125F, -0.127F);//move out and down for front-face of sign
        renderSignContents(sign, signLines, poseStack, buffers);
        poseStack.popPose();
    }

    private static float getRotationFromDirection(Direction d) {
        return switch (d) {
            case NORTH -> 180.0F;
            case EAST -> 90.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }

    /*
     * The pose is set up so that 0,0 is the upper left hand corner of the sign board, with 1 being 1 BLOCK.
     */
    private void renderSignContents(T sign, List<Line> signLines, PoseStack poseStack, MultiBufferSource buffers) {
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, -0.002F);//move out from block face slightly, for depth-buffer/z-fighting
        poseStack.scale(-1.0F, -1.0F, -1.0F);//rescale for gui rendering axis flip
        poseStack.scale(UNIT, UNIT, UNIT);//this scale puts it at 200 units(pixels) per block
        poseStack.scale(1.0F, 1.0F, 0.0001F);//squash Z axis for 'flat' rendering of 3d blocks/items
        int max = Math.min(signLines.size(), MAX_LINES);
        for (int i = 0; i < max; i++) {
            Line line = signLines.get(i);
            ItemStack filterItem = line.item();
            if (!filterItem.isEmpty()) {
                // like GuiGraphics.renderItem: the slot is 16 units square, its centre is 8 units in, and the gui y axis points down
                poseStack.pushPose();
                poseStack.translate(24.0F + 8.0F, i * 18 + 14 + 8.0F, 0.0F);
                poseStack.scale(16.0F, -16.0F, 16.0F);
                itemRenderer.renderStatic(filterItem, ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                        poseStack, buffers, sign.getLevel(), 0);
                poseStack.popPose();
            }
            String name = filterItem.isEmpty() ? "Empty Filter" : filterItem.getHoverName().getString();
            if (name.length() > MAX_NAME_LENGTH) {
                name = name.substring(0, MAX_NAME_LENGTH);
            }
            font.drawInBatch(name, 20 + 12 + 12, i * 18 + 4 + 14, 0xFFFFFFFF, false, poseStack.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            String quantity = String.valueOf(line.quantity());
            font.drawInBatch(quantity, 200 - 25 - font.width(quantity), i * 18 + 4 + 14, 0xFFFFFFFF, false, poseStack.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
        poseStack.popPose();
    }
}
