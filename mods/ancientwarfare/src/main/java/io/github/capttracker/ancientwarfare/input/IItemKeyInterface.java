/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/input/IItemKeyInterface.
 */
package io.github.capttracker.ancientwarfare.input;

import io.github.capttracker.ancientwarfare.config.AWCoreStatics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Implemented by items that can receive server-side alternate key-action events. The key is determined
 * client side, so the item need only react to it. The five alt keys replace left-click on items.
 */
public interface IItemKeyInterface {
    enum ItemAltFunction {
        ALT_FUNCTION_1(AWCoreStatics.KEY_ALT_ITEM_USE_1),
        ALT_FUNCTION_2(AWCoreStatics.KEY_ALT_ITEM_USE_2),
        ALT_FUNCTION_3(AWCoreStatics.KEY_ALT_ITEM_USE_3),
        ALT_FUNCTION_4(AWCoreStatics.KEY_ALT_ITEM_USE_4),
        ALT_FUNCTION_5(AWCoreStatics.KEY_ALT_ITEM_USE_5);

        private final String keyMappingName;

        ItemAltFunction(String keyMappingName) {
            this.keyMappingName = keyMappingName;
        }

        /** The name (translation key) of the key mapping for this function. */
        public String keyMappingName() {
            return keyMappingName;
        }

        /**
         * The key currently bound to this function, as text for tooltips. Safe to call from common code: the
         * client draws a keybind component as the name of the key bound to that mapping (1.12.2 items read the
         * client-only key binding directly).
         */
        public Component keyDisplay() {
            return Component.keybind(keyMappingName);
        }
    }

    /**
     * Called client side before the packet is sent to the server.
     *
     * @param altFunction the alt-use key that is being pressed
     * @return true to send the activation packet to the server
     */
    boolean onKeyActionClient(Player player, ItemStack stack, ItemAltFunction altFunction);

    /**
     * Called server side when a client presses the key that is bound to an alternate item-use function.
     *
     * @param player      the player using the item
     * @param stack       the item stack that is in use
     * @param altFunction the alt-use key that is being pressed
     */
    void onKeyAction(Player player, ItemStack stack, ItemAltFunction altFunction);
}
