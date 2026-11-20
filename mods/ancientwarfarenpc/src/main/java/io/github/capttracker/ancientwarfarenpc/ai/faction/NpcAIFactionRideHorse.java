/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/faction/NpcAIFactionRideHorse.
 */
package io.github.capttracker.ancientwarfarenpc.ai.faction;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAIRideHorse;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.faction.IHorseMountedNpc;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The riding goal of the mounted faction NPCs (speed factor 1.5): on its first run it spawns a tamed mount under the NPC (the entity type of the faction
 * defaults) and remembers whether the mount is alive ({@link IHorseMountedNpc}); if the mount died the NPC stays on foot.
 */
public class NpcAIFactionRideHorse<T extends NpcBase & IHorseMountedNpc> extends NpcAIRideHorse<T> {
    public NpcAIFactionRideHorse(T npc) {
        super(npc, 1.5);
    }

    @Override
    protected boolean shouldRideHorse() {
        return npc.isHorseAlive() && (npc.getVehicle() == null || horse != npc.getVehicle());
    }

    @Override
    public void start() {
        if (horse == null && npc.isHorseAlive()) {
            if (npc.getVehicle() instanceof Mob mount) {
                // original bug fixed: an NPC that already rides (loaded from a save) did not get the horse's goals taken away and its speed raised
                horse = mount;
                onMountHorse();
            } else {
                spawnHorse();
            }
        } else if (horse != null && !horse.isAlive()) {
            npc.setHorseKilled();
            horse = null;
        }
    }

    private void spawnHorse() {
        if (!(npc.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Mob newHorse = npc.instantiateMountedEntity();
        newHorse.moveTo(npc.getX(), npc.getY(), npc.getZ(), npc.getYRot(), npc.getXRot());
        EventHooks.finalizeMobSpawn(newHorse, serverLevel, serverLevel.getCurrentDifficultyAt(npc.blockPosition()), MobSpawnType.EVENT, null);
        if (newHorse instanceof AbstractHorse abstractHorse) {
            abstractHorse.setAge(0);
            abstractHorse.setTamed(true);
        }
        this.horse = newHorse;
        serverLevel.addFreshEntity(newHorse);
        npc.startRiding(newHorse);
        onMountHorse();
    }
}
