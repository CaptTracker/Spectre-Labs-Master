/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/input/ItemKeyConflictContext.
 */
package io.github.capttracker.ancientwarfare.client.input;

import io.github.capttracker.ancientwarfare.input.IItemKeyInterface;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;

/**
 * Active only in the world with no screen open while the main or off hand holds an {@link IItemKeyInterface}
 * item, so the alt keys can share keys with other bindings the rest of the time.
 */
public final class ItemKeyConflictContext implements IKeyConflictContext {
    public static final ItemKeyConflictContext INSTANCE = new ItemKeyConflictContext();

    private ItemKeyConflictContext() {}

    @Override
    public boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.level == null) {
            return false;
        }

        return mc.player.getMainHandItem().getItem() instanceof IItemKeyInterface
                || mc.player.getOffhandItem().getItem() instanceof IItemKeyInterface;
    }

    @Override
    public boolean conflicts(IKeyConflictContext other) {
        return this == other;
    }
}
