/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 12): the siege towers.
 */
package io.github.capttracker.ancientwarfarevehicle.client.model;

import io.github.capttracker.ancientwarfare.util.Trig;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleSettings;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Box model of the two siege towers, written by hand (not generated) because the height of the tower is a config value ({@code siege_tower_medium_height}, {@code siege_tower_large_height}):
 * the layer is built from it when the models are registered. A wheeled wooden tower 3 x 3 blocks wide whose roof is the top platform, with plank and corner posts, a hide front armour with iron
 * straps, a ladder on the back wall, push bars for the pushers, a parapet round the platform and a drop bridge hinged on the top of the front, which {@link #setBridge} swings from standing
 * against the front (0) to lying out over the wall (1). Pixels, y down, the front of the vehicle is -z, 16 pixels are a block; the renderer applies {@code scale(-1, -1, 1)}.
 *
 * <p>The texture is the 256 x 512 atlas of {@code analysis/port/vehicle_siege_models.py}: four zones of 256 x 128 (plank, dark wood, iron, hide) of which a box takes the one at
 * {@code texOffs(0, 128 * zone)}; a box must be no taller than 128 pixels with its depth added (the tall members are cut into pieces of 64).
 */
public final class ModelSiegeTower extends AwPartModel {
    public static final ModelLayerLocation MEDIUM_LAYER = layer("siege_tower");
    public static final ModelLayerLocation LARGE_LAYER = layer("siege_tower_large");

    static final int PLANK = 0;
    static final int DARK = 1;
    static final int IRON = 2;
    static final int HIDE = 3;
    private static final int PIECE = 64;

    private final ModelPart[] wheels;
    private final ModelPart bridge;

    public ModelSiegeTower(ModelPart root, int axles) {
        super(root);
        this.wheels = new ModelPart[axles * 2];
        for (int i = 0; i < wheels.length; i++) {
            wheels[i] = root.getChild("wheel" + i);
        }
        this.bridge = root.getChild("bridge");
        setBridge(0.f, 0.f);
    }

    public static LayerDefinition createMediumLayer() {
        return createLayer(VehicleSettings.siegeTowerMediumHeight, 2);
    }

    public static LayerDefinition createLargeLayer() {
        return createLayer(VehicleSettings.siegeTowerLargeHeight, 3);
    }

    /** One box of a zone of the atlas. */
    static CubeListBuilder add(CubeListBuilder b, int zone, float x, float y, float z, float w, float h, float d) {
        return b.texOffs(0, zone * 128).addBox(x, y, z, w, h, d);
    }

    /** A vertical member from {@code yBottom} up to {@code yTop} (both y down: bottom is the larger), cut into pieces of 64 pixels. */
    static CubeListBuilder column(CubeListBuilder b, int zone, float x, float z, float w, float d, float yBottom, float yTop) {
        float y = yBottom;
        while (y > yTop) {
            float piece = Math.min(PIECE, y - yTop);
            add(b, zone, x, y - piece, z, w, piece, d);
            y -= piece;
        }
        return b;
    }

    static void wheel(PartDefinition root, String name, float x, float y, float z, float radius) {
        PartDefinition wheel = root.addOrReplaceChild(name, CubeListBuilder.create().texOffs(0, 128).addBox(-2.0f, -2.0f, -2.0f, 4.0f, 4.0f, 4.0f), PartPose.offset(x, y, z));
        float apothem = radius - 1.5f;
        float side = (float) Math.ceil(2 * apothem * Math.tan(Math.PI / 8)) + 1;
        for (int k = 0; k < 8; k++) {
            wheel.addOrReplaceChild("rim" + k, CubeListBuilder.create().texOffs(0, 128).addBox(-1.5f, -apothem - 1, -side / 2.0f, 3.0f, 2.0f, side),
                    PartPose.rotation((float) (k * Math.PI / 4), 0.0f, 0.0f));
        }
        float length = 2 * (radius - 2);
        for (int k = 0; k < 4; k++) {
            wheel.addOrReplaceChild("spoke" + k, CubeListBuilder.create().texOffs(0, 0).addBox(-0.5f, -length / 2.0f, -0.5f, 1.0f, length, 1.0f),
                    PartPose.rotation((float) (k * Math.PI / 4), 0.0f, 0.0f));
        }
    }

    public static LayerDefinition createLayer(int topBlocks, int axles) {
        float top = topBlocks * 16.0f;
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // chassis and the push bars the pushers hold at the back
        CubeListBuilder chassis = CubeListBuilder.create();
        add(chassis, DARK, -24.0f, -14.0f, -24.0f, 48.0f, 4.0f, 48.0f);
        add(chassis, DARK, -17.0f, -22.0f, 24.0f, 2.0f, 2.0f, 22.0f);
        add(chassis, DARK, 15.0f, -22.0f, 24.0f, 2.0f, 2.0f, 22.0f);
        add(chassis, DARK, -17.0f, -22.0f, 44.0f, 34.0f, 2.0f, 2.0f);
        root.addOrReplaceChild("chassis", chassis, PartPose.ZERO);

        // corner posts and the side walls
        CubeListBuilder frame = CubeListBuilder.create();
        for (float cx : new float[] {-24.0f, 20.0f}) {
            for (float cz : new float[] {-24.0f, 20.0f}) {
                column(frame, DARK, cx, cz, 4.0f, 4.0f, -14.0f, -(top - 2.0f));
            }
        }
        column(frame, PLANK, -24.0f, -20.0f, 2.0f, 40.0f, -14.0f, -(top - 2.0f));
        column(frame, PLANK, 22.0f, -20.0f, 2.0f, 40.0f, -14.0f, -(top - 2.0f));
        // ribs on the outside of the walls at every second block, a back wall on both sides of the ladder
        for (float level = 32.0f; level < top - 8.0f; level += 32.0f) {
            add(frame, DARK, -26.0f, -level - 2.0f, -22.0f, 2.0f, 3.0f, 44.0f);
            add(frame, DARK, 24.0f, -level - 2.0f, -22.0f, 2.0f, 3.0f, 44.0f);
        }
        column(frame, PLANK, -20.0f, 22.0f, 12.0f, 2.0f, -14.0f, -(top - 2.0f));
        column(frame, PLANK, 8.0f, 22.0f, 12.0f, 2.0f, -14.0f, -(top - 2.0f));
        root.addOrReplaceChild("frame", frame, PartPose.ZERO);

        // front armour: hide over the planks, with iron straps
        CubeListBuilder armour = CubeListBuilder.create();
        column(armour, HIDE, -20.0f, -24.0f, 40.0f, 2.0f, -14.0f, -(top - 14.0f));
        for (float level = 24.0f; level < top - 14.0f; level += 32.0f) {
            add(armour, IRON, -22.0f, -level - 2.0f, -26.0f, 44.0f, 4.0f, 2.0f);
        }
        root.addOrReplaceChild("armour", armour, PartPose.ZERO);

        // the ladder on the back wall
        CubeListBuilder ladder = CubeListBuilder.create();
        column(ladder, PLANK, -7.0f, 20.0f, 2.0f, 2.0f, -14.0f, -(top - 2.0f));
        column(ladder, PLANK, 5.0f, 20.0f, 2.0f, 2.0f, -14.0f, -(top - 2.0f));
        for (float rung = 22.0f; rung < top - 4.0f; rung += 8.0f) {
            add(ladder, DARK, -7.0f, -rung, 20.0f, 14.0f, 1.0f, 2.0f);
        }
        root.addOrReplaceChild("ladder", ladder, PartPose.ZERO);

        // the roof (the top platform) with its parapet at the sides and the back
        CubeListBuilder roof = CubeListBuilder.create();
        add(roof, PLANK, -24.0f, -top, -24.0f, 48.0f, 2.0f, 48.0f);
        add(roof, DARK, -24.0f, -top - 14.0f, -22.0f, 2.0f, 14.0f, 44.0f);
        add(roof, DARK, 22.0f, -top - 14.0f, -22.0f, 2.0f, 14.0f, 44.0f);
        add(roof, DARK, -22.0f, -top - 14.0f, 22.0f, 44.0f, 14.0f, 2.0f);
        for (float cx : new float[] {-24.0f, 20.0f}) {
            for (float cz : new float[] {-24.0f, 20.0f}) {
                add(roof, DARK, cx, -top - 22.0f, cz, 4.0f, 22.0f, 4.0f);
            }
        }
        root.addOrReplaceChild("roof", roof, PartPose.ZERO);

        // the drop bridge, hinged on the top edge of the front
        CubeListBuilder planks = CubeListBuilder.create();
        add(planks, DARK, -24.0f, 0.0f, -48.0f, 48.0f, 5.0f, 48.0f);
        add(planks, IRON, -24.0f, -2.0f, -48.0f, 2.0f, 2.0f, 48.0f);
        add(planks, IRON, 22.0f, -2.0f, -48.0f, 2.0f, 2.0f, 48.0f);
        root.addOrReplaceChild("bridge", planks, PartPose.offset(0.0f, -top, -24.0f));

        // the wheels: two or three axles
        float[] axleZ = axles == 3 ? new float[] {-16.0f, 0.0f, 16.0f} : new float[] {-14.0f, 14.0f};
        for (int i = 0; i < axleZ.length; i++) {
            wheel(root, "wheel" + (i * 2), 22.5f, -10.0f, axleZ[i], 10.0f);
            wheel(root, "wheel" + (i * 2 + 1), -22.5f, -10.0f, axleZ[i], 10.0f);
        }
        return LayerDefinition.create(mesh, 256, 512);
    }

    /** Turns the wheels to the angle (degrees). */
    public void setWheelRotation(float degrees) {
        for (ModelPart wheel : wheels) {
            wheel.xRot = Trig.toRadians(degrees);
        }
    }

    /** Swings the bridge: 0 stands against the front of the tower, 1 lies out in front of it, level or, for a tilt other than 0, sloping down (positive degrees of the tilt) or up (negative). */
    public void setBridge(float progress, float tilt) {
        float p = Math.max(0.0f, Math.min(1.0f, progress));
        this.bridge.xRot = -(1.0f - p) * ((float) Math.PI / 2.0f) + p * tilt;
    }
}
