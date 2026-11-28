/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): hearty meals (the nourishment and comfort of Farmer's Delight) for the NPCs that eat them.
 */
package io.github.capttracker.ancientwarfarenpc.compat;

import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A food whose effects include one of the ids of {@code farmers_delight.hearty_meal_effects} (by default the nourishment and comfort of Farmer's Delight, which
 * its meals carry) is a hearty meal. An NPC that eats one gets the effects of the food, as a player would, and a morale lift for a day ({@code hearty_meal}
 * factor, see {@code NpcMorale}). This works on the food component and on effect ids alone, so no class of the other mod is involved and nothing happens
 * without it (no food has such an effect). Server side.
 */
public final class HeartyMeals {
    private HeartyMeals() {}

    private static boolean listed(MobEffectInstance instance, List<String> ids) {
        ResourceLocation key = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value());
        return key != null && ids.contains(key.toString());
    }

    /** True if the stack is a food with a hearty effect (false when the integration is off). */
    public static boolean isHearty(ItemStack stack) {
        if (!SettlementConfig.fdEnabled || stack.isEmpty()) {
            return false;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) {
            return false;
        }
        for (FoodProperties.PossibleEffect possible : food.effects()) {
            if (listed(possible.effect(), SettlementConfig.fdHeartyEffects)) {
                return true;
            }
        }
        return false;
    }

    /**
     * An NPC eats the food: if it is a hearty meal the NPC gets the harmless effects of it (each with its own chance) and the time of the meal is kept for the
     * morale. Other foods are left to the normal feeding.
     */
    public static void eat(NpcPlayerOwned npc, ItemStack stack) {
        if (!isHearty(stack)) {
            return;
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food != null) {
            for (FoodProperties.PossibleEffect possible : food.effects()) {
                MobEffectInstance effect = possible.effect();
                if (effect.getEffect().value().getCategory() != MobEffectCategory.HARMFUL && npc.getRandom().nextFloat() < possible.probability()) {
                    npc.addEffect(new MobEffectInstance(effect));
                }
            }
        }
        npc.onHeartyMeal();
    }
}
