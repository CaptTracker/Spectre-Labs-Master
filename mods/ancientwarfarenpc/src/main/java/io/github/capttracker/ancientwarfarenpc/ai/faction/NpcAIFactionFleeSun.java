/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/faction/NpcAIFactionFleeSun.
 */
package io.github.capttracker.ancientwarfarenpc.ai.faction;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAI;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * A burning faction NPC without a helmet looks for a shaded spot in daylight (10 random tries within 10 blocks) and walks there. A copy of the vanilla
 * flee sun goal that rates the spots with the NPC's walk target value, and that sets no flags (like the original).
 */
public class NpcAIFactionFleeSun extends NpcAI<NpcFaction> {
    private final double movementSpeed;
    private double shelterX;
    private double shelterY;
    private double shelterZ;

    public NpcAIFactionFleeSun(NpcFaction npc, double movementSpeed) {
        super(npc);
        this.movementSpeed = movementSpeed;
    }

    @Override
    public boolean canUse() {
        Level level = npc.level();
        if (!super.canUse() || !npc.getNavigation().isDone() || !level.isDay() || !npc.isOnFire()
                || !level.canSeeSky(BlockPos.containing(npc.getX(), npc.getBoundingBox().minY, npc.getZ())) || !npc.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            return false;
        }
        Vec3 vec3d = findPossibleShelter();
        if (vec3d == null) {
            return false;
        } else {
            shelterX = vec3d.x;
            shelterY = vec3d.y;
            shelterZ = vec3d.z;
            return true;
        }
    }

    @Override
    public void start() {
        npc.getNavigation().moveTo(shelterX, shelterY, shelterZ, movementSpeed);
    }

    @Nullable
    private Vec3 findPossibleShelter() {
        Level level = npc.level();
        BlockPos entityPos = BlockPos.containing(npc.getX(), getMinY(), npc.getZ());
        for (int i = 0; i < 10; ++i) {
            BlockPos randomPos = entityPos.offset(npc.getRandom().nextInt(20) - 10, npc.getRandom().nextInt(6) - 3, npc.getRandom().nextInt(20) - 10);
            if (!level.canSeeSky(randomPos) && npc.getWalkTargetValue(randomPos) > 0.0F) {
                return new Vec3(randomPos.getX(), randomPos.getY(), randomPos.getZ());
            }
        }
        return null;
    }

    private double getMinY() {
        Entity mount = npc.getVehicle();
        return mount != null ? mount.getBoundingBox().minY : npc.getBoundingBox().minY;
    }
}
