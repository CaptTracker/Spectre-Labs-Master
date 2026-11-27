/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): the mantlet.
 */
package io.github.capttracker.ancientwarfarevehicle.client.model;

import io.github.capttracker.ancientwarfare.util.Trig;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Box model of the mantlet: a wooden shield 3 blocks wide and 2.5 high, covered in hide, in a frame with an iron strap, on two small wheels, with push handles behind it. The shield
 * stands 0.55 blocks in front of the middle of the vehicle, where {@code entity.siege.MantletShield} puts it. Pixels, y down, the front of the vehicle is -z. Uses the zones of the
 * atlas of the siege towers (see {@link ModelSiegeTower}).
 */
public final class ModelMantlet extends AwPartModel {
    public static final ModelLayerLocation LAYER = layer("mantlet");

    private final ModelPart left;
    private final ModelPart right;

    public ModelMantlet(ModelPart root) {
        super(root);
        this.left = root.getChild("wheelLeft");
        this.right = root.getChild("wheelRight");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // the shield plane is at z = -8.8: the hide in front, the plank frame behind it
        CubeListBuilder shield = CubeListBuilder.create();
        ModelSiegeTower.add(shield, ModelSiegeTower.HIDE, -24.0f, -38.0f, -11.0f, 48.0f, 32.0f, 2.0f);
        ModelSiegeTower.add(shield, ModelSiegeTower.PLANK, -24.0f, -38.0f, -9.0f, 48.0f, 32.0f, 2.0f);
        ModelSiegeTower.add(shield, ModelSiegeTower.DARK, -24.0f, -40.0f, -12.0f, 48.0f, 3.0f, 5.0f);
        ModelSiegeTower.add(shield, ModelSiegeTower.DARK, -24.0f, -8.0f, -12.0f, 48.0f, 3.0f, 5.0f);
        ModelSiegeTower.add(shield, ModelSiegeTower.DARK, -24.0f, -40.0f, -12.0f, 3.0f, 35.0f, 5.0f);
        ModelSiegeTower.add(shield, ModelSiegeTower.DARK, 21.0f, -40.0f, -12.0f, 3.0f, 35.0f, 5.0f);
        ModelSiegeTower.add(shield, ModelSiegeTower.DARK, -2.0f, -40.0f, -12.0f, 4.0f, 35.0f, 5.0f);
        ModelSiegeTower.add(shield, ModelSiegeTower.IRON, -24.0f, -26.0f, -12.5f, 48.0f, 3.0f, 1.0f);
        root.addOrReplaceChild("shield", shield, PartPose.ZERO);
        // the struts behind it and the handles the rider pushes by
        CubeListBuilder handles = CubeListBuilder.create();
        ModelSiegeTower.add(handles, ModelSiegeTower.DARK, -16.0f, -24.0f, -7.0f, 2.0f, 2.0f, 22.0f);
        ModelSiegeTower.add(handles, ModelSiegeTower.DARK, 14.0f, -24.0f, -7.0f, 2.0f, 2.0f, 22.0f);
        ModelSiegeTower.add(handles, ModelSiegeTower.DARK, -16.0f, -24.0f, 13.0f, 32.0f, 2.0f, 2.0f);
        ModelSiegeTower.add(handles, ModelSiegeTower.DARK, -16.0f, -22.0f, -7.0f, 2.0f, 14.0f, 2.0f);
        ModelSiegeTower.add(handles, ModelSiegeTower.DARK, 14.0f, -22.0f, -7.0f, 2.0f, 14.0f, 2.0f);
        root.addOrReplaceChild("handles", handles, PartPose.ZERO);
        ModelSiegeTower.wheel(root, "wheelLeft", 18.0f, -6.0f, -2.0f, 6.0f);
        ModelSiegeTower.wheel(root, "wheelRight", -18.0f, -6.0f, -2.0f, 6.0f);
        return LayerDefinition.create(mesh, 256, 512);
    }

    /** Turns the wheels to the angle (degrees). */
    public void setWheelRotation(float degrees) {
        this.left.xRot = Trig.toRadians(degrees);
        this.right.xRot = Trig.toRadians(degrees);
    }
}
