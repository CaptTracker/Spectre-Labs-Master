/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemCoin.
 */
package io.github.capttracker.ancientwarfarenpc.item;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcs;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Currency. There is no balance or exchange logic: a coin is an ordinary item that traders ask for and give (the faction trade lists use them).
 * 1.12.2 had one item with the NBT string {@code metal}; there is one item per metal now.
 */
public class ItemCoin extends Item {

    private final CoinMetal metal;

    /**
     * @param metal {@code ancient}, {@code gold}, {@code silver} or {@code copper}
     */
    public ItemCoin(Properties properties, String metal) {
        super(properties);
        CoinMetal byName = CoinMetal.byName(metal);
        this.metal = byName == null ? CoinMetal.COPPER : byName;
    }

    public CoinMetal getMetal() {
        return metal;
    }

    /** The metal of a coin stack (copper for anything else, like 1.12.2 for a stack without data). */
    public static CoinMetal getMetal(ItemStack stack) {
        return stack.getItem() instanceof ItemCoin coin ? coin.metal : CoinMetal.COPPER;
    }

    public static boolean isSpecificCoin(ItemStack stack, CoinMetal coinMetal) {
        return stack.getItem() instanceof ItemCoin coin && coin.metal == coinMetal;
    }

    public static ItemStack getCoinStack(CoinMetal metal, int stackSize) {
        return new ItemStack(AwNpcs.COINS.get("coin_" + metal.getName()).get(), stackSize);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (metal == CoinMetal.ANCIENT) {
            // the lang text starts with %1$s, which 1.12.2 filled with the formatting codes dark aqua and italic
            tooltip.add(Component.translatable("item." + AncientWarfareNPC.MOD_ID + ".coin_ancient.tooltip", "").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
        }
    }

    public enum CoinMetal {
        ANCIENT("ancient", 0x445948),
        GOLD("gold", 0xFFD700),
        SILVER("silver", 0xC0C0C0),
        COPPER("copper", 0xB87333);

        private static final Map<String, CoinMetal> BY_NAME = new HashMap<>();

        static {
            Arrays.stream(values()).forEach(m -> BY_NAME.put(m.getName(), m));
        }

        private final String name;
        private final int color;

        CoinMetal(String name, int color) {
            this.name = name;
            this.color = color;
        }

        public String getName() {
            return name;
        }

        /** The metal of the name, or null. */
        @Nullable
        public static CoinMetal byName(String name) {
            return BY_NAME.get(name);
        }

        public int getColor() {
            return color;
        }
    }
}
