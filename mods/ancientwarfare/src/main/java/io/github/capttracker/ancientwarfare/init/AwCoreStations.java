/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the research bench registrations of core/init/AWCoreBlocks and AWCoreItems
 * (block, tile entity, GUI id 12) and the hammer mode that was an item tag.
 */
package io.github.capttracker.ancientwarfare.init;

import com.mojang.serialization.Codec;
import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.api.AwCapabilities;
import io.github.capttracker.ancientwarfare.block.ResearchStationBlock;
import io.github.capttracker.ancientwarfare.block.entity.ResearchStationBlockEntity;
import io.github.capttracker.ancientwarfare.menu.ResearchStationMenu;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Registrations of the research bench slice: the research bench block, its block entity and menu, the hammer's
 * work-mode component, and the bench's capabilities (its resource inventory as an item handler on every side,
 * and torque).
 *
 * <p>Wiring: {@code AwCoreStations.load()} and {@code AwCoreStations.registerEvents(modBus)} from the mod
 * constructor before {@code REG.register(modBus)}; remove {@code RESEARCH_STATION} from {@code AncientWarfareCore}
 * (this class registers the block under the same name).
 */
public final class AwCoreStations {
    private AwCoreStations() {}

    // 1.12.2: Material.WOOD, hardness 2, not a full or opaque cube (same properties as the engineering bench).
    public static final DeferredBlock<ResearchStationBlock> RESEARCH_STATION = AncientWarfareCore.REG.block("research_station",
            ResearchStationBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD).ignitedByLava().noOcclusion());

    // 1.12.2 id: research_station_tile
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResearchStationBlockEntity>> RESEARCH_STATION_BE =
            AncientWarfareCore.REG.blockEntity("research_station", ResearchStationBlockEntity::new, RESEARCH_STATION);

    // 1.12.2: GUI_RESEARCH_STATION (12)
    public static final DeferredHolder<MenuType<?>, MenuType<ResearchStationMenu>> RESEARCH_STATION_MENU =
            AncientWarfareCore.REG.menus.register("research_station", () -> IMenuTypeExtension.create(ResearchStationMenu::new));

    /** Hammer mode: true = work mode, absent or false = rotate mode (was the item tag {@code workMode}). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> HAMMER_WORK_MODE =
            AncientWarfareCore.REG.dataComponent("hammer_work_mode", Codec.BOOL, ByteBufCodecs.BOOL);

    /** Forces this class (and so the registrations above) to initialise. Call from the mod constructor. */
    public static void load() {}

    /** Registers the mod-bus listeners. Call from the mod constructor. */
    public static void registerEvents(IEventBus modBus) {
        modBus.addListener(AwCoreStations::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // The original exposed the resource inventory on every side (also for hoppers and automation).
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, RESEARCH_STATION_BE.get(), (station, side) -> station.resourceInventory);
        event.registerBlockEntity(AwCapabilities.TORQUE, RESEARCH_STATION_BE.get(), (station, side) -> station);
    }
}
