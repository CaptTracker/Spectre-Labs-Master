/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemSickle.
 */
package io.github.capttracker.ancientwarfarenpc.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;

/** The sickle: attack attributes only (the tier's attack bonus and the given speed), no harvesting logic, as in 1.12.2. */
public class ItemSickle extends Item {
    private final Tier tier;

    public ItemSickle(Properties properties, Tier tier, float attackSpeed) {
        super(properties.attributes(NpcItemAttributes.melee(tier.getAttackDamageBonus(), attackSpeed)));
        this.tier = tier;
    }

    @Override
    public int getEnchantmentValue() {
        return tier.getEnchantmentValue();
    }
}
