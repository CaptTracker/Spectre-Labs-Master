package io.github.capttracker.spectrefactions.event;

import io.github.capttracker.spectrefactions.client.ClientFactionsView;
import io.github.capttracker.spectrefactions.command.FactionsCommand;
import io.github.capttracker.spectrefactions.config.FactionsConfig;
import io.github.capttracker.spectrefactions.data.Faction;
import io.github.capttracker.spectrefactions.data.FactionsSavedData;
import io.github.capttracker.spectrefactions.network.ClientboundFactionsSnapshot.FactionInfo;
import io.github.capttracker.spectrefactions.service.FactionsSync;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Optional;

/** Server lifecycle, ticking, login and the chat / tab-list / nameplate tag. */
public final class FactionsEvents {
    private FactionsEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent e) {
        FactionsCommand.register(e.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent e) {
        // Loads the data and applies the NPC factions that mods registered earlier.
        FactionsConfig.bake();
        FactionsSavedData.get(e.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        FactionsSync.tick(server);
        int interval = FactionsConfig.decayIntervalTicks;
        if (interval > 0) {
            ServerLevel overworld = server.getLevel(Level.OVERWORLD);
            if (overworld != null && overworld.getGameTime() > 0 && overworld.getGameTime() % interval == 0) {
                FactionsSavedData d = FactionsSavedData.get(server);
                if (d != null) {
                    d.decay();
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer player) {
            FactionsSavedData d = FactionsSavedData.get(player.server);
            if (d != null) {
                d.rememberName(player.getUUID(), player.getGameProfile().getName());
                FactionsSync.sendTo(player);
                FactionsSync.refreshNames(player);
                d.markChanged();
            }
        }
    }

    /** Chat, death messages and nameplates: {@code [TAG] Name}. */
    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat e) {
        MutableComponent tag = tagFor(e.getEntity());
        if (tag != null) {
            e.setDisplayname(Component.empty().append(tag).append(" ").append(e.getDisplayname()));
        }
    }

    @SubscribeEvent
    public static void onTabListNameFormat(PlayerEvent.TabListNameFormat e) {
        MutableComponent tag = tagFor(e.getEntity());
        if (tag != null) {
            e.setDisplayName(Component.empty().append(tag).append(" ").append(e.getEntity().getName()));
        }
    }

    private static MutableComponent tagFor(Player player) {
        try {
            if (player.level().isClientSide()) {
                ClientFactionsView view = ClientFactionsView.INSTANCE;
                if (!view.settings().chatTags()) {
                    return null;
                }
                Optional<FactionInfo> f = view.factionOfPlayer(player.getUUID());
                return f.map(info -> Component.literal("[" + info.tag() + "]").withStyle(info.color())).orElse(null);
            }
            if (!FactionsConfig.chatTags) {
                return null;
            }
            FactionsSavedData d = FactionsSavedData.current();
            if (d == null) {
                return null;
            }
            return d.factionOfPlayer(player.getUUID()).map(Faction::tagComponent).orElse(null);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
