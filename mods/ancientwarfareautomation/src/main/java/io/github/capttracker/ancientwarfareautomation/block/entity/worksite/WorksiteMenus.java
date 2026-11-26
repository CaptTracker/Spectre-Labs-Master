/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: replaces the NetworkHandler.INSTANCE.openGui(player, GUI_WORKSITE_*, pos) calls of the worksite
 * tiles (the 1.12.2 container registry of the module entry).
 */
package io.github.capttracker.ancientwarfareautomation.block.entity.worksite;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Where the worksite block entities open their menus. The menus and screens belong to the GUI slice of this module;
 * it sets one {@link Factory} per {@link Kind} (for example from its menu registration code), and a worksite's
 * {@code onBlockClicked} / {@code openAltGui} call {@link #open}. Until a factory is set, opening does nothing.
 *
 * <p>The menu is opened with the block position as extra data, which is what {@code BlockEntityMenu.resolve} reads on the
 * client. The 1.12.2 gui ids map to the kinds as follows: {@code GUI_WORKSITE_QUARRY} to {@link Kind#QUARRY},
 * {@code GUI_WORKSITE_TREE_FARM}, {@code _CROP_FARM}, {@code _FRUIT_FARM} to the farm kinds,
 * {@code GUI_WORKSITE_ANIMAL_FARM} / {@code _ANIMAL_CONTROL} and {@code GUI_WORKSITE_FISH_FARM} / {@code _FISH_CONTROL}
 * to the farm and control kinds, {@code GUI_WORKSITE_AUTO_CRAFT} to {@link Kind#AUTO_CRAFTING}. (The bounds, quarry
 * bounds and inventory side screens are opened from inside those menus and are not listed here.)
 */
public final class WorksiteMenus {
    private WorksiteMenus() {}

    public enum Kind {
        QUARRY,
        TREE_FARM,
        CROP_FARM,
        FRUIT_FARM,
        ANIMAL_FARM,
        /** The second screen of the animal farm (animal limits), {@code openAltGui}. */
        ANIMAL_CONTROL,
        FISH_FARM,
        /** The fishing dock (rework phase 4). */
        DOCK,
        APIARY,
        SMELTER,
        /** The sawmill and the bakery (rework phase 8) and the recipe screen they share, {@code openAltGui}. */
        SAWMILL,
        BAKERY,
        /** The smithy and the tannery (rework phase 8), also on the recipe screen above. */
        SMITHY,
        TANNERY,
        PROCESSING_RECIPES,
        /** The market stall (rework phase 5) and its offers screen, {@code openAltGui}. */
        MARKET_STALL,
        MARKET_OFFERS,
        /** The inn and the stable (rework phase 5). */
        INN,
        STABLE,
        /** The second screen of the fish farm (what to harvest), {@code openAltGui}. */
        FISH_CONTROL,
        AUTO_CRAFTING
    }

    /** Builds the menu for one block entity (server side). */
    @FunctionalInterface
    public interface Factory {
        AbstractContainerMenu create(int containerId, Inventory playerInventory, BlockEntity blockEntity);
    }

    private static final Map<Kind, Factory> FACTORIES = new EnumMap<>(Kind.class);

    /** TODO(port): called by the Automation GUI slice with the menu constructors. Pass null to remove one. */
    public static void setFactory(Kind kind, @Nullable Factory factory) {
        if (factory == null) {
            FACTORIES.remove(kind);
        } else {
            FACTORIES.put(kind, factory);
        }
    }

    public static boolean hasFactory(Kind kind) {
        return FACTORIES.containsKey(kind);
    }

    /**
     * Opens the menu for the player. Does nothing on the client, for a non-server player and when the GUI slice has
     * not set a factory for the kind.
     */
    public static void open(Player player, Kind kind, BlockEntity blockEntity) {
        Factory factory = FACTORIES.get(kind);
        if (factory == null || !(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide) {
            return;
        }
        Component title = switch (kind) {
            case ANIMAL_CONTROL -> Component.translatable("guistrings.automation.animal_control");
            case APIARY -> Component.translatable("block.ancientwarfareautomation.apiary");
            case SMELTER -> Component.translatable("block.ancientwarfareautomation.smelter");
            case MARKET_OFFERS -> Component.translatable("guistrings.automation.market_offers");
            case FISH_CONTROL -> Component.translatable("guistrings.automation.fish_control");
            default -> blockEntity.getBlockState().getBlock().getName();
        };
        serverPlayer.openMenu(
                new SimpleMenuProvider((id, inventory, p) -> factory.create(id, inventory, blockEntity), title),
                buf -> buf.writeBlockPos(blockEntity.getBlockPos()));
    }
}
