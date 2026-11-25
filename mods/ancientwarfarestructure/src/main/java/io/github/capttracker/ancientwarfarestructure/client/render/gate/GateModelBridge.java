/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/model/ModelGateBridge (12 boxes, texture 256x256; the Techne export).
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

/** The box model of the drawbridge: a hinge block, the wall panel, four sides, four corners and two top pieces. */
public final class GateModelBridge {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Ids.of(AncientWarfareStructure.MOD_ID, "gate_bridge"), "main");

    private static final String[] PARTS = {"part1", "bridgePiece", "bridgeSide1", "bridgeSide4", "bridgeCorner1", "bridgeCorner2",
            "bridgeTop1", "bridgeTop2", "bridgeSide3", "bridgeSide2", "bridgeCorner4", "bridgeCorner3"};

    private final ModelPart part1;
    private final ModelPart bridgePiece;
    private final ModelPart bridgeSide1;
    private final ModelPart bridgeSide4;
    private final ModelPart bridgeCorner1;
    private final ModelPart bridgeCorner2;
    private final ModelPart bridgeTop1;
    private final ModelPart bridgeTop2;
    private final ModelPart bridgeSide3;
    private final ModelPart bridgeSide2;
    private final ModelPart bridgeCorner4;
    private final ModelPart bridgeCorner3;
    private final ModelPart[] all;

    public GateModelBridge(ModelPart root) {
        part1 = root.getChild("part1");
        bridgePiece = root.getChild("bridgePiece");
        bridgeSide1 = root.getChild("bridgeSide1");
        bridgeSide4 = root.getChild("bridgeSide4");
        bridgeCorner1 = root.getChild("bridgeCorner1");
        bridgeCorner2 = root.getChild("bridgeCorner2");
        bridgeTop1 = root.getChild("bridgeTop1");
        bridgeTop2 = root.getChild("bridgeTop2");
        bridgeSide3 = root.getChild("bridgeSide3");
        bridgeSide2 = root.getChild("bridgeSide2");
        bridgeCorner4 = root.getChild("bridgeCorner4");
        bridgeCorner3 = root.getChild("bridgeCorner3");
        all = new ModelPart[PARTS.length];
        for (int i = 0; i < PARTS.length; i++) {
            all[i] = root.getChild(PARTS[i]);
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("part1", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0f, -8.0f, -8.0f, 16, 16, 16), PartPose.ZERO);
        root.addOrReplaceChild("bridgePiece", CubeListBuilder.create().texOffs(0, 33).addBox(-8.0f, -8.0f, -8.0f, 16, 16, 8), PartPose.ZERO);
        root.addOrReplaceChild("bridgeSide1", CubeListBuilder.create().texOffs(0, 58).addBox(-2.0f, -8.0f, 0.0f, 10, 16, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeSide4", CubeListBuilder.create().texOffs(27, 58).addBox(-8.0f, -8.0f, 3.0f, 6, 16, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeCorner1", CubeListBuilder.create().texOffs(49, 34).addBox(-8.0f, -8.0f, 0.0f, 6, 9, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeCorner2", CubeListBuilder.create().texOffs(49, 47).addBox(-8.0f, -8.0f, 3.0f, 10, 6, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeTop1", CubeListBuilder.create().texOffs(49, 58).addBox(-8.0f, -8.0f, 0.0f, 16, 9, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeTop2", CubeListBuilder.create().texOffs(49, 72).addBox(-8.0f, -8.0f, 3.0f, 16, 6, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeSide3", CubeListBuilder.create().texOffs(0, 58).addBox(-8.0f, -8.0f, 0.0f, 10, 16, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeSide2", CubeListBuilder.create().texOffs(27, 58).addBox(2.0f, -8.0f, 3.0f, 6, 16, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeCorner4", CubeListBuilder.create().texOffs(49, 47).addBox(-2.0f, -8.0f, 3.0f, 10, 6, 3), PartPose.ZERO);
        root.addOrReplaceChild("bridgeCorner3", CubeListBuilder.create().texOffs(49, 34).addBox(2.0f, -8.0f, 0.0f, 6, 9, 3), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 256);
    }

    public void renderGateBlock(GateDraw draw) {
        draw.render(part1);
    }

    public void renderTop(GateDraw draw) {
        draw.render(bridgeTop1);
        draw.render(bridgeTop2);
    }

    public void renderCorner(GateDraw draw) {
        draw.render(bridgeCorner1);
        draw.render(bridgeCorner2);
        draw.render(bridgeSide1);
        draw.render(bridgeSide2);
    }

    public void renderCorner2(GateDraw draw) {
        draw.render(bridgeCorner3);
        draw.render(bridgeCorner4);
        draw.render(bridgeSide3);
        draw.render(bridgeSide4);
    }

    public void renderSide1(GateDraw draw) {
        draw.render(bridgeSide1);
        draw.render(bridgeSide2);
    }

    public void renderSide2(GateDraw draw) {
        draw.render(bridgeSide3);
        draw.render(bridgeSide4);
    }

    public void renderSolidWall(GateDraw draw) {
        draw.render(bridgePiece);
    }

    /** Turns every part about the vertical axis, in degrees. */
    public void setModelRotation(float rot) {
        float val = Trig.toRadians(rot);
        for (ModelPart part : all) {
            part.yRot = val;
        }
    }
}
