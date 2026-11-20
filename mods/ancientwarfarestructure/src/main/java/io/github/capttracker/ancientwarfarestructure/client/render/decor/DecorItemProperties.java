/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the NBT-driven item model selection ({@code ModelLoader.setCustomMeshDefinition}) of
 * structure/block/BlockCoinStack and BlockFirePit.
 */
package io.github.capttracker.ancientwarfarestructure.client.render.decor;

import io.github.capttracker.ancientwarfarestructure.AncientWarfareStructure;
import io.github.capttracker.ancientwarfarestructure.decor.CoinMetal;
import io.github.capttracker.ancientwarfarestructure.decor.CoinStackBlock;
import io.github.capttracker.ancientwarfarestructure.decor.FirePitBlock;
import io.github.capttracker.ancientwarfarestructure.decor.FirePitVariant;
import io.github.capttracker.ancientwarfarestructure.init.AwDecor;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;

/**
 * Item model predicates. The item models {@code coin_stack_<metal>} pick the pile model of the stack's size with the
 * predicate {@code ancientwarfarestructure:size} (1 to 8), and the fire pit item models pick the lit model with
 * {@code ancientwarfarestructure:lit} (0 or 1). Both read the {@code minecraft:block_state} item component.
 */
public final class DecorItemProperties {
    private DecorItemProperties() {}

    public static final ResourceLocation SIZE = Ids.of(AncientWarfareStructure.MOD_ID, "size");
    public static final ResourceLocation LIT = Ids.of(AncientWarfareStructure.MOD_ID, "lit");

    /** Call from client setup, inside {@code enqueueWork}. */
    public static void register() {
        for (CoinMetal metal : CoinMetal.values()) {
            ItemProperties.register(AwDecor.coinStackItem(metal).get(), SIZE, (stack, level, entity, seed) -> {
                BlockItemStateProperties properties = stack.get(DataComponents.BLOCK_STATE);
                Integer size = properties == null ? null : properties.get(CoinStackBlock.SIZE);
                return size == null ? 1f : size;
            });
        }
        for (FirePitVariant variant : FirePitVariant.values()) {
            ItemProperties.register(AwDecor.firePitItem(variant).get(), LIT,
                    (stack, level, entity, seed) -> isLit(stack) ? 1f : 0f);
        }
    }

    private static boolean isLit(ItemStack stack) {
        BlockItemStateProperties properties = stack.get(DataComponents.BLOCK_STATE);
        return properties != null && Boolean.TRUE.equals(properties.get(FirePitBlock.LIT));
    }
}
