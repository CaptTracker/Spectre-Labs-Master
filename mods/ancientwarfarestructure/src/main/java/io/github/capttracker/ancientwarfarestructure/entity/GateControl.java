/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the GUI_GATE_CONTROL and GUI_GATE_CONTROL_CREATIVE calls of
 * structure/entity/EntityGate#processInitialInteract.
 */
package io.github.capttracker.ancientwarfarestructure.entity;

import net.minecraft.server.level.ServerPlayer;

/**
 * How a sneaking right-click on a gate opens its control screen (1.12.2: {@code ContainerGateControl}, a repack button and,
 * for creative players, an owner name field). {@code AwGateMenus.load()} installs the real
 * opener with {@link #setOpener}; the default (used if nothing installs one) repacks the gate, which is what the screen's only button does for the owner.
 */
public final class GateControl {
    /** Opens the control screen of a gate for a player (called on the server). */
    @FunctionalInterface
    public interface Opener {
        /**
         * @param creative whether the creative owner editor opens (the player is in creative mode) or the owner's repack screen
         */
        void open(ServerPlayer player, EntityGate gate, boolean creative);
    }

    private static volatile Opener opener = (player, gate, creative) -> gate.repackEntity();

    private GateControl() {}

    public static void setOpener(Opener newOpener) {
        opener = newOpener;
    }

    static void open(ServerPlayer player, EntityGate gate, boolean creative) {
        opener.open(player, gate, creative);
    }
}
