/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/registry/NpcDefault.
 */
package io.github.capttracker.ancientwarfarenpc.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.function.Consumer;

/**
 * The stats of one kind of NPC as data: attribute base values, experience dropped, swimming and door behaviour, and starting
 * equipment. Immutable; the {@code set...} methods return a changed copy.
 *
 * <p>Attributes are keyed by 1.21 attribute id ({@code minecraft:generic.max_health}); the parser also accepts the 1.12.2 names
 * ({@code generic.maxHealth}). Equipment is keyed by slot name: the vanilla names {@code mainhand}, {@code offhand}, {@code head},
 * {@code chest}, {@code legs}, {@code feet}, plus {@code work} and {@code upkeep} for the two NPC-only order slots (1.12.2 keyed
 * them by int, with the vanilla slot ordinals, which changed in 1.21).
 */
public abstract class NpcDefault {
    public static final String WORK_SLOT = "work";
    public static final String UPKEEP_SLOT = "upkeep";

    protected Map<ResourceLocation, Double> attributes;
    protected int experienceDrop;
    protected boolean canSwim;
    protected boolean canBreakDoors;
    protected Map<String, Item> equipment;

    protected NpcDefault(Map<ResourceLocation, Double> attributes, int experienceDrop, boolean canSwim, boolean canBreakDoors, Map<String, Item> equipment) {
        this.attributes = attributes;
        this.experienceDrop = experienceDrop;
        this.canSwim = canSwim;
        this.canBreakDoors = canBreakDoors;
        this.equipment = equipment;
    }

    public abstract NpcDefault setExperienceDrop(int experienceDrop);

    public abstract NpcDefault setCanSwim(boolean canSwim);

    public abstract NpcDefault setCanBreakDoors(boolean canBreakDoors);

    public abstract NpcDefault setAttributes(Map<ResourceLocation, Double> additionalAttributes);

    public abstract NpcDefault setEquipment(Map<String, Item> additionalEquipment);

    public int getExperienceDrop() {
        return experienceDrop;
    }

    public boolean canSwim() {
        return canSwim;
    }

    public boolean canBreakDoors() {
        return canBreakDoors;
    }

    public double getBaseHealth() {
        return getBase(Attributes.MAX_HEALTH);
    }

    public double getBaseAttack() {
        return getBase(Attributes.ATTACK_DAMAGE);
    }

    private double getBase(Holder<Attribute> attribute) {
        return attributes.getOrDefault(attribute.unwrapKey().orElseThrow().location(), 0D);
    }

    /** Sets the base value of every attribute of the default that the mob has; the health is set to the new maximum. */
    public void applyAttributes(Mob npc) {
        attributes.forEach((id, value) -> {
            Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(id).orElse(null);
            AttributeInstance attribute = holder == null ? null : npc.getAttribute(holder);
            if (attribute != null) {
                attribute.setBaseValue(value);
                if (holder.is(Attributes.MAX_HEALTH)) {
                    npc.setHealth(value.floatValue());
                }
            }
        });
    }

    /** Swimming and door behaviour of ground navigation (1.12.2: {@code setCanSwim} and {@code setBreakDoors}). Other navigation is left alone. */
    public void applyPathSettings(PathNavigation navigator) {
        navigator.setCanFloat(canSwim);
        if (navigator instanceof GroundPathNavigation ground) {
            ground.setCanOpenDoors(canBreakDoors);
        }
    }

    /** Equips the vanilla slots only; see {@link #applyEquipment(Mob, Consumer, Consumer)} for the order slots. */
    public void applyEquipment(Mob npc) {
        applyEquipment(npc, stack -> {}, stack -> {});
    }

    /**
     * Equips the mob from the default. The vanilla slots are set directly; the stacks of the {@code work} and {@code upkeep}
     * slots are handed to the given consumers (the NPC base class keeps them in its own fields).
     */
    public void applyEquipment(Mob npc, Consumer<ItemStack> workSlot, Consumer<ItemStack> upkeepSlot) {
        equipment.forEach((slot, item) -> {
            ItemStack stack = new ItemStack(item);
            switch (slot) {
                case WORK_SLOT -> workSlot.accept(stack);
                case UPKEEP_SLOT -> upkeepSlot.accept(stack);
                default -> npc.setItemSlot(EquipmentSlot.byName(slot), stack);
            }
        });
    }
}
