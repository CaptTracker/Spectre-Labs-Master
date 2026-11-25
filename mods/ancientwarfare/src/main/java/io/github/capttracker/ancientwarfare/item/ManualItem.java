/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/item/ItemManual.
 */
package io.github.capttracker.ancientwarfare.item;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.gamedata.WorldData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The in-game manual. Using it opens the manual screen, which is client only and has no menu: the client
 * runs the opener installed with {@link #setClientOpener}. Players are given one the first time they craft
 * any Ancient Warfare item ({@link #handlePlayerFirstAWCraft}, registered by {@code AwCoreMisc}).
 */
public class ManualItem extends Item {
    /** Opens the manual screen on the client. Installed by the manual screen slice at client setup; null until then. */
    @Nullable
    private static volatile Runnable clientOpener;

    public static void setClientOpener(@Nullable Runnable opener) {
        clientOpener = opener;
    }

    public ManualItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            Runnable opener = clientOpener;
            if (opener != null) {
                opener.run();
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ancientwarfare.manual.tooltip"));
    }

    /** Gives the player a manual on the first craft of an Ancient Warfare item (other than the manual itself). */
    public static void handlePlayerFirstAWCraft(PlayerEvent.ItemCraftedEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Item item = evt.getCrafting().getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (item != AncientWarfareCore.MANUAL.get() && id.getNamespace().startsWith(AncientWarfareCore.MOD_ID)) {
            WorldData data = WorldData.get(player.server);
            if (!data.wasPlayerGivenManual(player)) {
                ItemEntity manualDrop = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(),
                        new ItemStack(AncientWarfareCore.MANUAL.get()));
                manualDrop.setPickUpDelay(0);
                player.level().addFreshEntity(manualDrop);
                data.addPlayerThatWasGivenManual(player);
            }
        }
    }
}
