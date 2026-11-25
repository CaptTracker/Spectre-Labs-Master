package io.github.capttracker.spectrefactions.client;

import io.github.capttracker.spectrefactions.SpectreFactions;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot;
import io.github.capttracker.spectrefactions.network.ServerboundFactionAction;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Client-side entry points. Only loaded on a client. */
public final class FactionsClient {
    /** Set by the screen stage to open the faction screen. Until then {@link #openScreen()} does nothing but log. */
    public static Runnable screenOpener = () -> SpectreFactions.LOGGER.info("The faction screen is not available yet.");

    private FactionsClient() {}

    public static final KeyMapping OPEN_KEY = new KeyMapping("key.spectrefactions.open_screen",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.spectrefactions");

    public static void init(IEventBus modBus) {
        screenOpener = () -> {
            Minecraft mc = Minecraft.getInstance();
            if (!(mc.screen instanceof FactionsScreen)) {
                mc.setScreen(new FactionsScreen());
            }
        };
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN_KEY));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            while (OPEN_KEY.consumeClick()) {
                if (mc.screen == null && mc.player != null) {
                    openScreen();
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> ClientFactionsView.INSTANCE.reset());
    }

    /** Opens the faction screen. The screen stage replaces {@link #screenOpener}. */
    public static void openScreen() {
        screenOpener.run();
    }

    /** Sends an action to the server (see {@code FactionService.ACTIONS}). */
    public static void sendAction(String actionType, String... args) {
        PacketDistributor.sendToServer(new ServerboundFactionAction(actionType, List.of(args)));
    }

    public static void acceptSnapshot(ClientboundFactionsSnapshot snapshot) {
        Minecraft mc = Minecraft.getInstance();
        ClientFactionsView.INSTANCE.update(snapshot, mc.player == null ? null : mc.player.getUUID());
        if (mc.level != null) {
            for (Player p : mc.level.players()) {
                p.refreshDisplayName();
            }
        }
    }
}
