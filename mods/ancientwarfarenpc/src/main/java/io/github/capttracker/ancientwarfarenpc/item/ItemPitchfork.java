/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemPitchfork.
 */
package io.github.capttracker.ancientwarfarenpc.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;

/** The pitchfork: attack attributes only (the tier's attack bonus and the given speed), as in 1.12.2. */
public class ItemPitchfork extends Item {
    private final Tier tier;

    public ItemPitchfork(Properties properties, Tier tier, float attackSpeed) {
        super(properties.attributes(NpcItemAttributes.melee(tier.getAttackDamageBonus(), attackSpeed)));
        this.tier = tier;
    }

    @Override
    public int getEnchantmentValue() {
        return tier.getEnchantmentValue();
    }
}
