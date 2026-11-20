/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemExtendedReachWeapon.
 */
package io.github.capttracker.ancientwarfarenpc.item;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/**
 * A sword with its own attack damage, attack speed and reach: the spears, halberds, lances and cleavers of the five tiers and the obsidian
 * spear. The damage is the tier's attack bonus plus {@code attackOffset}; the reach is a modifier of the entity interaction range.
 */
public class ItemExtendedReachWeapon extends SwordItem implements IExtendedReachWeapon {
    private final float reach;

    public ItemExtendedReachWeapon(Properties properties, Tier tier, double attackOffset, double attackSpeed, float reach) {
        super(tier, properties.attributes(NpcItemAttributes.meleeWithReach(tier.getAttackDamageBonus() + attackOffset, attackSpeed, reach)));
        this.reach = reach;
    }

    @Override
    public float getReach() {
        return reach;
    }
}
