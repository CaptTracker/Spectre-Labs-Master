/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/registry/OwnedNpcDefault.
 */
package io.github.capttracker.ancientwarfarenpc.registry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** The defaults of a player-owned NPC type (owned_npc_defaults.json), including the entity types it attacks. */
public class OwnedNpcDefault extends NpcDefault {
    private final Set<String> targetList;

    public OwnedNpcDefault(Set<String> targetList, Map<ResourceLocation, Double> attributes, int experienceDrop, boolean canSwim, boolean canBreakDoors,
                           Map<String, Item> equipment) {
        super(attributes, experienceDrop, canSwim, canBreakDoors, equipment);
        this.targetList = targetList;
    }

    public OwnedNpcDefault overrideTargets(Set<String> newTargetList) {
        return new OwnedNpcDefault(newTargetList, attributes, experienceDrop, canSwim, canBreakDoors, equipment);
    }

    /** Whether the NPC attacks this kind of entity (the entity type id is in its target list). */
    public boolean isTarget(Entity entity) {
        return targetList.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }

    @Override
    public OwnedNpcDefault setExperienceDrop(int experienceDrop) {
        return new OwnedNpcDefault(targetList, attributes, experienceDrop, canSwim, canBreakDoors, equipment);
    }

    @Override
    public OwnedNpcDefault setCanSwim(boolean canSwim) {
        return new OwnedNpcDefault(targetList, attributes, experienceDrop, canSwim, canBreakDoors, equipment);
    }

    @Override
    public OwnedNpcDefault setCanBreakDoors(boolean canBreakDoors) {
        return new OwnedNpcDefault(targetList, attributes, experienceDrop, canSwim, canBreakDoors, equipment);
    }

    @Override
    public OwnedNpcDefault setAttributes(Map<ResourceLocation, Double> additionalAttributes) {
        Map<ResourceLocation, Double> newAttributes = new HashMap<>(this.attributes);
        newAttributes.putAll(additionalAttributes);
        return new OwnedNpcDefault(targetList, newAttributes, experienceDrop, canSwim, canBreakDoors, equipment);
    }

    @Override
    public OwnedNpcDefault setEquipment(Map<String, Item> additionalEquipment) {
        Map<String, Item> newEquipment = new HashMap<>(equipment);
        newEquipment.putAll(additionalEquipment);
        return new OwnedNpcDefault(targetList, attributes, experienceDrop, canSwim, canBreakDoors, newEquipment);
    }
}
