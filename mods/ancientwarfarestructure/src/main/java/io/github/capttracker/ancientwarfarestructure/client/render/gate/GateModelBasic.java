/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/model/ModelGateBasic (25 boxes, texture 256x256; the Techne export).
 */
package io.github.capttracker.ancientwarfarestructure.client.render.gate;

import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfarestructure.AncientWarfareStructure;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The box model of the vertical, single and double gates: the wall panel ({@code part1}), the bars, the top piece, the top
 * corner piece and the side piece, each with its child boxes. One baked instance is shared by every gate; the renderer sets
 * the rotation of the five root parts before every draw, as 1.12.2 did.
 */
public final class GateModelBasic {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Ids.of(AncientWarfareStructure.MOD_ID, "gate_basic"), "main");

    private final ModelPart part1;
    private final ModelPart top1;
    private final ModelPart barsTop;
    private final ModelPart cornerTop1;
    private final ModelPart side1;

    public GateModelBasic(ModelPart root) {
        part1 = root.getChild("part1");
        top1 = root.getChild("top1");
        barsTop = root.getChild("barsTop");
        cornerTop1 = root.getChild("cornerTop1");
        side1 = root.getChild("side1");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        root.addOrReplaceChild("part1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0f, -16.0f, -8.0f, 2, 16, 16), PartPose.ZERO);

        PartDefinition top1 = root.addOrReplaceChild("top1", CubeListBuilder.create().texOffs(41, 26).addBox(4.0f, 0.0f, -8.0f, 2, 6, 16), PartPose.offset(0.0f, -16.0f, 0.0f));
        top1.addOrReplaceChild("top4", CubeListBuilder.create().texOffs(39, 0).addBox(1.0f, 0.0f, -8.0f, 3, 8, 16), PartPose.ZERO);
        top1.addOrReplaceChild("top3", CubeListBuilder.create().texOffs(39, 0).addBox(0.0f, 0.0f, -8.0f, 3, 8, 16), PartPose.offset(-4.0f, 0.0f, 0.0f));
        top1.addOrReplaceChild("top2", CubeListBuilder.create().texOffs(41, 26).addBox(0.0f, 0.0f, -8.0f, 2, 6, 16), PartPose.offset(-6.0f, 0.0f, 0.0f));

        PartDefinition barsTop = root.addOrReplaceChild("barsTop", CubeListBuilder.create().texOffs(0, 75).addBox(-1.0f, -13.0f, -8.0f, 2, 2, 16), PartPose.ZERO);
        barsTop.addOrReplaceChild("barsLeft1", CubeListBuilder.create().texOffs(0, 52).addBox(-1.0f, -16.0f, -5.0f, 2, 3, 2), PartPose.ZERO);
        barsTop.addOrReplaceChild("barsLeft3", CubeListBuilder.create().texOffs(0, 58).addBox(-1.0f, -11.0f, -5.0f, 2, 6, 2), PartPose.ZERO);
        barsTop.addOrReplaceChild("barsLeft5", CubeListBuilder.create().texOffs(0, 67).addBox(-1.0f, -3.0f, -5.0f, 2, 3, 2), PartPose.ZERO);
        barsTop.addOrReplaceChild("barsRight1", CubeListBuilder.create().texOffs(27, 52).addBox(-1.0f, -16.0f, 3.0f, 2, 3, 2), PartPose.ZERO);
        barsTop.addOrReplaceChild("barsRight3", CubeListBuilder.create().texOffs(27, 58).addBox(-1.0f, -11.0f, 3.0f, 2, 6, 2), PartPose.ZERO);
        barsTop.addOrReplaceChild("barsRight5", CubeListBuilder.create().texOffs(27, 67).addBox(-1.0f, -3.0f, 3.0f, 2, 3, 2), PartPose.ZERO);
        barsTop.addOrReplaceChild("barsBottom", CubeListBuilder.create().texOffs(0, 33).addBox(-1.0f, -5.0f, -8.0f, 2, 2, 16), PartPose.ZERO);

        PartDefinition cornerTop1 = root.addOrReplaceChild("cornerTop1", CubeListBuilder.create().texOffs(39, 49).addBox(1.0f, 0.0f, 0.0f, 3, 8, 8),
                PartPose.offsetAndRotation(0.0f, -16.0f, 0.0f, 0.0f, 2.1014006E-7f, 0.0f));
        cornerTop1.addOrReplaceChild("cornerTop2", CubeListBuilder.create().texOffs(39, 66).addBox(0.0f, 0.0f, 0.0f, 2, 6, 10), PartPose.offset(4.0f, 0.0f, -2.0f));
        cornerTop1.addOrReplaceChild("cornerSide2", CubeListBuilder.create().texOffs(62, 83).addBox(0.0f, 0.0f, 0.0f, 2, 16, 6), PartPose.offset(4.0f, 0.0f, -8.0f));
        cornerTop1.addOrReplaceChild("cornerSide1", CubeListBuilder.create().texOffs(39, 83).addBox(0.0f, 0.0f, 0.0f, 3, 16, 8), PartPose.offset(1.0f, 0.0f, -8.0f));
        cornerTop1.addOrReplaceChild("cornerTop3", CubeListBuilder.create().texOffs(62, 49).addBox(0.0f, 0.0f, 0.0f, 3, 8, 8), PartPose.offset(-4.0f, 0.0f, 0.0f));
        cornerTop1.addOrReplaceChild("cornerTop4", CubeListBuilder.create().texOffs(64, 66).addBox(0.0f, 0.0f, 0.0f, 2, 6, 10), PartPose.offset(-6.0f, 0.0f, -2.0f));
        cornerTop1.addOrReplaceChild("cornerSide3", CubeListBuilder.create().texOffs(39, 108).addBox(0.0f, 0.0f, 0.0f, 3, 16, 8), PartPose.offset(-4.0f, 0.0f, -8.0f));
        cornerTop1.addOrReplaceChild("cornerSide4", CubeListBuilder.create().texOffs(62, 108).addBox(0.0f, 0.0f, 0.0f, 2, 16, 6), PartPose.offset(-6.0f, 0.0f, -8.0f));

        PartDefinition side1 = root.addOrReplaceChild("side1", CubeListBuilder.create().texOffs(39, 83).addBox(1.0f, 0.0f, -8.0f, 3, 16, 8), PartPose.offset(0.0f, -16.0f, 0.0f));
        side1.addOrReplaceChild("side2", CubeListBuilder.create().texOffs(62, 83).addBox(0.0f, 0.0f, -8.0f, 2, 16, 6), PartPose.offset(4.0f, 0.0f, 0.0f));
        side1.addOrReplaceChild("side3", CubeListBuilder.create().texOffs(39, 108).addBox(0.0f, 0.0f, -8.0f, 3, 16, 8), PartPose.offset(-4.0f, 0.0f, 0.0f));
        side1.addOrReplaceChild("side4", CubeListBuilder.create().texOffs(62, 108).addBox(0.0f, 0.0f, -8.0f, 2, 16, 6), PartPose.offset(-6.0f, 0.0f, 0.0f));

        return LayerDefinition.create(mesh, 256, 256);
    }

    public void renderTop(GateDraw draw) {
        draw.render(top1);
    }

    public void renderCorner(GateDraw draw) {
        draw.render(cornerTop1);
    }

    public void renderSide(GateDraw draw) {
        draw.render(side1);
    }

    public void renderSolidWall(GateDraw draw) {
        draw.render(part1);
    }

    public void renderBars(GateDraw draw) {
        draw.render(barsTop);
    }

    /** Turns the five root parts about the vertical axis, in degrees. */
    public void setModelRotation(float rot) {
        float val = Trig.toRadians(rot);
        top1.yRot = val;
        cornerTop1.yRot = val;
        side1.yRot = val;
        part1.yRot = val;
        barsTop.yRot = val;
    }
}
