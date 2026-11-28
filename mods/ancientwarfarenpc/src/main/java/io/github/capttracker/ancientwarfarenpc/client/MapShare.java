/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round): "Share waypoint" for Xaero's Minimap, by chat message only (no code of that mod is used).
 */
package io.github.capttracker.ancientwarfarenpc.client;

import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import java.util.Locale;

/**
 * Posts a waypoint to the player's own chat. Xaero's Minimap reads chat lines of the form
 * {@code xaero-waypoint:<name>:<initials>:<x>:<y>:<z>:<color index>:false:0:<waypoint set>} and turns them into a clickable "add waypoint" line; the set
 * names are {@code Internal-overworld-waypoints}, {@code Internal-the-nether-waypoints}, {@code Internal-the-end-waypoints} and
 * {@code Internal-dim%<namespace>$<path>-waypoints} for other dimensions. The line is added to the local chat only (it is never sent to the server).
 * Without Xaero's Minimap the same position is shown as plain text; a click copies the coordinates to the clipboard (no command is run).
 */
public final class MapShare {
    public static final String XAERO_ID = "xaerominimap";
    public static final String XAERO_FAIR_ID = "xaerominimapfair";

    private MapShare() {}

    /** Whether the Share buttons are on (client config). */
    public static boolean enabled() {
        return AWNPCStatics.xaeroShareEnabled;
    }

    public static boolean xaeroInstalled() {
        ModList mods = ModList.get();
        return mods != null && (mods.isLoaded(XAERO_ID) || mods.isLoaded(XAERO_FAIR_ID));
    }

    /** The Xaero share string for a position (public for tests and docs). */
    public static String xaeroString(String name, BlockPos pos, ResourceKey<Level> dimension, int colorIndex) {
        String clean = name.replace(':', ' ').replace('\n', ' ').trim();
        if (clean.isEmpty()) {
            clean = "Waypoint";
        }
        String initials = clean.substring(0, clean.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
        return "xaero-waypoint:" + clean + ":" + initials + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ() + ":"
                + Math.floorMod(colorIndex, 16) + ":false:0:" + setName(dimension);
    }

    private static String setName(ResourceKey<Level> dimension) {
        if (dimension.equals(Level.OVERWORLD)) {
            return "Internal-overworld-waypoints";
        }
        if (dimension.equals(Level.NETHER)) {
            return "Internal-the-nether-waypoints";
        }
        if (dimension.equals(Level.END)) {
            return "Internal-the-end-waypoints";
        }
        return "Internal-dim%" + dimension.location().getNamespace() + "$" + dimension.location().getPath() + "-waypoints";
    }

    /** Shares a waypoint in the current dimension of the client level. */
    public static void share(String name, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        share(name, pos, mc.level.dimension(), (name.hashCode() & 0x7FFFFFFF) % 16);
    }

    public static void share(String name, BlockPos pos, ResourceKey<Level> dimension, int colorIndex) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (xaeroInstalled()) {
            mc.gui.getChat().addMessage(Component.literal(xaeroString(name, pos, dimension, colorIndex)));
            return;
        }
        String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        Component line = Component.translatable("guistrings.share.plain", name, coords, dimension.location().toString()).withStyle(Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, coords))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("guistrings.share.copy"))));
        mc.gui.getChat().addMessage(line);
    }
}
