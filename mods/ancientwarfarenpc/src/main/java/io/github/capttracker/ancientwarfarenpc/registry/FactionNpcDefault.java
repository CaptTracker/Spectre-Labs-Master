/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/registry/FactionNpcDefault (FactionSpellcasterDefault, an unused copy of it, is not ported).
 */
package io.github.capttracker.ancientwarfarenpc.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The defaults of one faction NPC type of one faction: the {@link NpcDefault} stats plus whether the type exists for the faction,
 * its loot table, body size, and {@link FactionAdditionalAttributes}.
 */
public class FactionNpcDefault extends NpcDefault {
    private FactionAdditionalAttributes additionalAttributes;
    private boolean enabled;
    @Nullable
    private ResourceLocation lootTable;
    private HeightRange heightRange;
    private float thinness;

    public FactionNpcDefault(Map<ResourceLocation, Double> attributes, int experienceDrop, boolean canSwim, boolean canBreakDoors, Map<String, Item> equipment,
                             FactionAdditionalAttributes additionalAttributes, boolean enabled, @Nullable ResourceLocation lootTable,
                             HeightRange heightRange, float thinness) {
        super(attributes, experienceDrop, canSwim, canBreakDoors, equipment);
        this.additionalAttributes = additionalAttributes;
        this.enabled = enabled;
        this.lootTable = lootTable;
        this.heightRange = heightRange;
        this.thinness = thinness;
    }

    /** Range of the random body height of an NPC (1.12.2: commons-lang {@code Range<Float>}). */
    public record HeightRange(float min, float max) {
        public static final HeightRange DEFAULT = new HeightRange(1.8F, 1.8F);

        public HeightRange {
            if (min > max) {
                float swap = min;
                min = max;
                max = swap;
            }
        }

        /** A height between min and max, evenly distributed. */
        public float random(RandomSource random) {
            return min + random.nextFloat() * (max - min);
        }

        public float getMinimum() {
            return min;
        }

        public float getMaximum() {
            return max;
        }
    }

    private FactionNpcDefault copy() {
        return new FactionNpcDefault(new HashMap<>(attributes), experienceDrop, canSwim, canBreakDoors, new HashMap<>(equipment), additionalAttributes, enabled,
                lootTable, heightRange, thinness);
    }

    private FactionNpcDefault change(Consumer<FactionNpcDefault> makeChange) {
        FactionNpcDefault copy = copy();
        makeChange.accept(copy);
        return copy;
    }

    @Override
    public FactionNpcDefault setExperienceDrop(int experienceDrop) {
        return change(def -> def.experienceDrop = experienceDrop);
    }

    @Override
    public FactionNpcDefault setCanSwim(boolean canSwim) {
        return change(def -> def.canSwim = canSwim);
    }

    @Override
    public FactionNpcDefault setCanBreakDoors(boolean canBreakDoors) {
        return change(def -> def.canBreakDoors = canBreakDoors);
    }

    public FactionNpcDefault setEnabled(boolean enabled) {
        return change(def -> def.enabled = enabled);
    }

    @Override
    public FactionNpcDefault setAttributes(Map<ResourceLocation, Double> additionalAttributes) {
        return change(def -> def.attributes.putAll(additionalAttributes));
    }

    @Override
    public FactionNpcDefault setEquipment(Map<String, Item> additionalEquipment) {
        return change(def -> def.equipment.putAll(additionalEquipment));
    }

    public FactionNpcDefault setAdditionalAttributes(FactionAdditionalAttributes overrides) {
        return change(def -> def.additionalAttributes = def.additionalAttributes.override(overrides));
    }

    public FactionNpcDefault setLootTable(ResourceLocation lootTable) {
        return change(def -> def.lootTable = lootTable);
    }

    public FactionNpcDefault setHeightRange(HeightRange heightRange) {
        return change(def -> def.heightRange = heightRange);
    }

    public FactionNpcDefault setThinness(float thinness) {
        return change(def -> def.thinness = thinness);
    }

    /** Replaces 1.12.2 {@code applyAdditionalAttributes(NpcFaction)}: the NPC reads the values it needs from here. */
    public FactionAdditionalAttributes getAdditionalAttributes() {
        return additionalAttributes;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** The loot table id (for {@code ResourceKey.create(Registries.LOOT_TABLE, id)}), or null for the entity's own. {@code minecraft:empty} means no loot. */
    @Nullable
    public ResourceLocation getLootTable() {
        return lootTable;
    }

    public HeightRange getHeightRange() {
        return heightRange;
    }

    public float getThinness() {
        return thinness;
    }
}
