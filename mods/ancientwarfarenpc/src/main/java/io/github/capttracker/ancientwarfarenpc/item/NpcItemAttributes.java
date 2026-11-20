/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: the main hand attribute modifiers that the 1.12.2 NPC weapons built in getAttributeModifiers, as the item attributes component.
 */
package io.github.capttracker.ancientwarfarenpc.item;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Builds the attack and reach modifiers of the NPC weapons. */
public final class NpcItemAttributes {
    /** The reach modifier of the extended reach weapons. */
    public static final ResourceLocation REACH_MODIFIER_ID = Ids.of(AncientWarfareNPC.MOD_ID, "extended_reach");
    /** The entity interaction range of a player without a reach weapon (the 1.12.2 attack reach). */
    public static final double BASE_REACH = 3.0D;

    private NpcItemAttributes() {}

    /**
     * Melee attack damage and attack speed in the main hand; the amounts are added to the player's base values like the 1.12.2 modifiers
     * ({@code Item.BASE_ATTACK_DAMAGE_ID} and {@code BASE_ATTACK_SPEED_ID} make the tooltip show the total).
     */
    public static ItemAttributeModifiers melee(double attackDamage, double attackSpeed) {
        return meleeBuilder(attackDamage, attackSpeed).build();
    }

    /** As {@link #melee} plus a longer entity interaction range, so that the attack reaches {@code reach} blocks (1.12.2 sent its own packet). */
    public static ItemAttributeModifiers meleeWithReach(double attackDamage, double attackSpeed, float reach) {
        return meleeBuilder(attackDamage, attackSpeed)
                .add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(REACH_MODIFIER_ID, reach - BASE_REACH, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    private static ItemAttributeModifiers.Builder meleeBuilder(double attackDamage, double attackSpeed) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, attackDamage, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attackSpeed, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
    }
}
