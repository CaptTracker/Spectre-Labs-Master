/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/input/InputHandler (with InputCallbackDispatcher).
 */
package io.github.capttracker.ancientwarfare.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.config.AWCoreStatics;
import io.github.capttracker.ancientwarfare.input.IInputCallback;
import io.github.capttracker.ancientwarfare.input.IItemKeyInterface.ItemAltFunction;
import io.github.capttracker.ancientwarfare.input.IScrollableItem;
import io.github.capttracker.ancientwarfare.network.CoreMiscPayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * The five alternate item-use keys and the sneak plus mouse wheel item scroll. Key presses are polled once per
 * client tick; the callbacks of every registered mapping run for each press. Other slices attach their own with
 * {@link #registerCallBack(KeyMapping, IInputCallback)} (the key mapping itself must be registered with the game,
 * for example from their own {@code RegisterKeyMappingsEvent} handler).
 */
@EventBusSubscriber(modid = AncientWarfareCore.MOD_ID, value = Dist.CLIENT)
public final class InputHandler {
    private static final String CATEGORY = "keybind.category.awCore";

    public static final KeyMapping ALT_ITEM_USE_1 = new KeyMapping(AWCoreStatics.KEY_ALT_ITEM_USE_1, ItemKeyConflictContext.INSTANCE, InputConstants.Type.KEYSYM, InputConstants.KEY_Z, CATEGORY);
    public static final KeyMapping ALT_ITEM_USE_2 = new KeyMapping(AWCoreStatics.KEY_ALT_ITEM_USE_2, ItemKeyConflictContext.INSTANCE, InputConstants.Type.KEYSYM, InputConstants.KEY_X, CATEGORY);
    public static final KeyMapping ALT_ITEM_USE_3 = new KeyMapping(AWCoreStatics.KEY_ALT_ITEM_USE_3, ItemKeyConflictContext.INSTANCE, InputConstants.Type.KEYSYM, InputConstants.KEY_C, CATEGORY);
    public static final KeyMapping ALT_ITEM_USE_4 = new KeyMapping(AWCoreStatics.KEY_ALT_ITEM_USE_4, ItemKeyConflictContext.INSTANCE, InputConstants.Type.KEYSYM, InputConstants.KEY_V, CATEGORY);
    public static final KeyMapping ALT_ITEM_USE_5 = new KeyMapping(AWCoreStatics.KEY_ALT_ITEM_USE_5, ItemKeyConflictContext.INSTANCE, InputConstants.Type.KEYSYM, InputConstants.KEY_B, CATEGORY);

    private static final List<InputCallbackDispatcher> KEYBINDING_CALLBACKS = new ArrayList<>();

    static {
        registerCallBack(ALT_ITEM_USE_1, new ItemInputCallback(ItemAltFunction.ALT_FUNCTION_1));
        registerCallBack(ALT_ITEM_USE_2, new ItemInputCallback(ItemAltFunction.ALT_FUNCTION_2));
        registerCallBack(ALT_ITEM_USE_3, new ItemInputCallback(ItemAltFunction.ALT_FUNCTION_3));
        registerCallBack(ALT_ITEM_USE_4, new ItemInputCallback(ItemAltFunction.ALT_FUNCTION_4));
        registerCallBack(ALT_ITEM_USE_5, new ItemInputCallback(ItemAltFunction.ALT_FUNCTION_5));
    }

    private InputHandler() {}

    /** Called from {@code CoreMiscClient} on the mod bus. */
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ALT_ITEM_USE_1);
        event.register(ALT_ITEM_USE_2);
        event.register(ALT_ITEM_USE_3);
        event.register(ALT_ITEM_USE_4);
        event.register(ALT_ITEM_USE_5);
    }

    /** Adds a callback for a key mapping; callbacks of the same mapping run in registration order. */
    public static synchronized void registerCallBack(KeyMapping keyMapping, IInputCallback callback) {
        for (InputCallbackDispatcher dispatcher : KEYBINDING_CALLBACKS) {
            if (dispatcher.keyMapping().equals(keyMapping)) {
                dispatcher.addInputCallback(callback);
                return;
            }
        }
        KEYBINDING_CALLBACKS.add(new InputCallbackDispatcher(keyMapping, callback));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        // The 1.12.2 handler ran on each key-down event; consumeClick() counts the presses since the last tick.
        for (InputCallbackDispatcher dispatcher : List.copyOf(KEYBINDING_CALLBACKS)) {
            while (dispatcher.keyMapping().consumeClick()) {
                dispatcher.onKeyPressed();
            }
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        double scroll = event.getScrollDeltaY();
        if (player == null || Minecraft.getInstance().screen != null || scroll == 0 || !player.isShiftKeyDown()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof IScrollableItem scrollable) {
            if (scroll > 0) {
                if (scrollable.onScrollUp(player.level(), player, stack)) {
                    PacketDistributor.sendToServer(new CoreMiscPayloads.ItemScrollPayload(true));
                }
            } else if (scrollable.onScrollDown(player.level(), player, stack)) {
                PacketDistributor.sendToServer(new CoreMiscPayloads.ItemScrollPayload(false));
            }
            event.setCanceled(true);
        }
    }

    /** All callbacks of one key mapping. */
    private static final class InputCallbackDispatcher {
        private final KeyMapping keyMapping;
        private final List<IInputCallback> inputCallbacks = new ArrayList<>();

        private InputCallbackDispatcher(KeyMapping keyMapping, IInputCallback initialCallback) {
            this.keyMapping = keyMapping;
            inputCallbacks.add(initialCallback);
        }

        KeyMapping keyMapping() {
            return keyMapping;
        }

        void addInputCallback(IInputCallback inputCallback) {
            inputCallbacks.add(inputCallback);
        }

        void onKeyPressed() {
            inputCallbacks.forEach(IInputCallback::onKeyPressed);
        }
    }
}
