/*
 * Ancient Warfare Core - NeoForge 1.21.1 port.
 * Ported from Ancient Warfare 2 (1.12.2-2.7.0.1038), Copyright (C) 2013-2021 Shadowmage, P3pp3rF1y and contributors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version. It is distributed WITHOUT ANY WARRANTY.
 * See LICENSE.txt in this mod's folder.
 */
package io.github.capttracker.ancientwarfare;

import io.github.capttracker.ancientwarfare.block.EngineeringStationBlock;
import io.github.capttracker.ancientwarfare.block.entity.EngineeringStationBlockEntity;
import io.github.capttracker.ancientwarfare.menu.EngineeringStationMenu;
import net.minecraft.world.level.block.entity.BlockEntityType;
import io.github.capttracker.ancientwarfare.config.AWCoreStatics;
import io.github.capttracker.ancientwarfare.init.AwCoreCrafting;
import io.github.capttracker.ancientwarfare.init.AwCoreMisc;
import io.github.capttracker.ancientwarfare.init.AwCoreScreens;
import io.github.capttracker.ancientwarfare.init.AwCoreStations;
import io.github.capttracker.ancientwarfare.item.BackpackItem;
import io.github.capttracker.ancientwarfare.item.HammerItem;
import io.github.capttracker.ancientwarfare.item.InfoToolItem;
import io.github.capttracker.ancientwarfare.item.ManualItem;
import io.github.capttracker.ancientwarfare.item.QuillItem;
import io.github.capttracker.ancientwarfare.item.ResearchBookItem;
import io.github.capttracker.ancientwarfare.item.ResearchNoteItem;
import io.github.capttracker.ancientwarfare.research.Research;
import io.github.capttracker.ancientwarfare.research.ResearchCommand;
import io.github.capttracker.ancientwarfare.research.ResearchGoal;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import io.github.capttracker.ancientwarfare.menu.BackpackMenu;
import io.github.capttracker.ancientwarfare.network.AwNetwork;
import io.github.capttracker.lib.registry.ModRegistrar;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.items.ComponentItemHandler;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.LinkedHashMap;
import java.util.Map;

@Mod(AncientWarfareCore.MOD_ID)
public final class AncientWarfareCore {
    public static final String MOD_ID = "ancientwarfare";
    public static final ModRegistrar REG = ModRegistrar.create(MOD_ID);

    // ---- Blocks (1.12.2: AWCoreBlocks) ----
    public static final DeferredBlock<EngineeringStationBlock> ENGINEERING_STATION =
            REG.block("engineering_station", EngineeringStationBlock::new, stationProps());

    // ---- Block entities (1.12.2 id: engineering_station_tile) ----
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EngineeringStationBlockEntity>> ENGINEERING_STATION_BE =
            REG.blockEntity("engineering_station", EngineeringStationBlockEntity::new, ENGINEERING_STATION);

    // ---- Items (1.12.2: AWCoreItems, same order) ----
    public static final DeferredItem<InfoToolItem> INFO_TOOL = REG.item("info_tool", InfoToolItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<ResearchBookItem> RESEARCH_BOOK = REG.item("research_book", ResearchBookItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<ResearchNoteItem> RESEARCH_NOTE = REG.item("research_note", ResearchNoteItem::new);

    // ---- Item data components (were raw item NBT in 1.12.2) ----
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResearchBookItem.Researcher>> RESEARCHER =
            REG.dataComponent("researcher", ResearchBookItem.Researcher.CODEC, ResearchBookItem.Researcher.STREAM_CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> RESEARCH_GOAL =
            REG.dataComponent("research_goal", ResourceLocation.CODEC, ResourceLocation.STREAM_CODEC);

    /** Was one item with damage values 0-3; slots = (index + 1) * 9. */
    public static final Map<String, DeferredItem<BackpackItem>> BACKPACKS = new LinkedHashMap<>();
    /** Quills and hammers by registry name, in tier order. */
    public static final Map<String, DeferredItem<Item>> QUILLS = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<Item>> HAMMERS = new LinkedHashMap<>();
    /** Was one "component" item with damage values 0-8. */
    public static final Map<String, DeferredItem<Item>> COMPONENTS = new LinkedHashMap<>();

    private static final Map<String, Tier> TIERS = new LinkedHashMap<>();

    static {
        TIERS.put("wooden", Tiers.WOOD);
        TIERS.put("stone", Tiers.STONE);
        TIERS.put("iron", Tiers.IRON);
        TIERS.put("gold", Tiers.GOLD);
        TIERS.put("diamond", Tiers.DIAMOND);

        String[] sizes = {"small", "travel", "medium", "large"};
        for (int i = 0; i < sizes.length; i++) {
            String name = sizes[i] + "_backpack";
            int rows = i + 1;
            BACKPACKS.put(name, REG.item(name, p -> new BackpackItem(p, rows), new Item.Properties().stacksTo(1)));
        }
        // Quill: attack damage 1 + tier bonus, default attack speed.
        TIERS.forEach((prefix, tier) -> QUILLS.put(prefix + "_quill",
                REG.item(prefix + "_quill", p -> new QuillItem(tier, p), QuillItem.properties(tier))));
        // Hammer: attack damage 4 + tier bonus, attack speed modifier -3.1.
        TIERS.forEach((prefix, tier) -> HAMMERS.put(prefix + "_hammer",
                REG.item(prefix + "_hammer", p -> new HammerItem(tier, p), HammerItem.properties(tier))));
    }

    public static final DeferredItem<ManualItem> MANUAL = REG.item("manual", ManualItem::new, new Item.Properties().stacksTo(1));

    static {
        for (String name : new String[]{
                "wooden_gear_set", "iron_gear_set", "steel_gear_set",
                "wooden_bearings", "iron_bearings", "steel_bearings",
                "wooden_torque_shaft", "iron_torque_shaft", "steel_torque_shaft"}) {
            COMPONENTS.put(name, REG.simpleItem(name));
        }
    }

    public static final DeferredItem<Item> STEEL_INGOT = REG.simpleItem("steel_ingot");

    // ---- Menus (1.12.2: GUI ids in NetworkHandler) ----
    public static final DeferredHolder<MenuType<?>, MenuType<BackpackMenu>> BACKPACK_MENU =
            REG.menus.register("backpack", () -> IMenuTypeExtension.create(BackpackMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<EngineeringStationMenu>> ENGINEERING_STATION_MENU =
            REG.menus.register("engineering_station", () -> IMenuTypeExtension.create(EngineeringStationMenu::new));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            REG.creativeTab(() -> new ItemStack(RESEARCH_BOOK.get()));

    public AncientWarfareCore(IEventBus modBus, ModContainer container) {
        // The init classes register into REG (and their own registers); load() forces their static fields to run
        // before REG.register(modBus) so every deferred entry is known when the registers go on the bus.
        AwCoreStations.load();
        AwCoreStations.registerEvents(modBus);
        AwCoreCrafting.load();
        AwCoreCrafting.registerEvents(modBus);
        AwCoreScreens.load();
        AwCoreScreens.registerEvents(modBus);
        AwCoreMisc.load();
        AwCoreMisc.registerEvents(modBus);
        AWCoreStatics.register(modBus, container);

        REG.register(modBus);
        modBus.addListener(AwNetwork::register);
        modBus.addListener(AncientWarfareCore::registerCapabilities);
        modBus.addListener((DataPackRegistryEvent.NewRegistry event) ->
                event.dataPackRegistry(ResearchGoal.REGISTRY_KEY, ResearchGoal.CODEC, ResearchGoal.CODEC));
        modBus.addListener(AncientWarfareCore::addResearchNotesToTab);

        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> ResearchCommand.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                Research.sync(player);
            }
        });
    }

    /** One research note per goal, after the blank note (1.12.2: ItemResearchNotes.getSubItems). */
    private static void addResearchNotesToTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != TAB.getKey()) {
            return;
        }
        event.getParameters().holders().lookup(ResearchGoal.REGISTRY_KEY).ifPresent(goals ->
                goals.listElementIds().map(ResourceKey::location).sorted()
                        .forEach(goal -> event.accept(ResearchNoteItem.forGoal(goal))));
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // The original exposed backpack contents as an item handler; couriers will need it later.
        BACKPACKS.values().forEach(backpack -> event.registerItem(Capabilities.ItemHandler.ITEM,
                (stack, context) -> new ComponentItemHandler(stack, DataComponents.CONTAINER, backpack.get().rows * 9),
                backpack.get()));
    }

    private static BlockBehaviour.Properties stationProps() {
        // 1.12.2: Material.WOOD, hardness 2, not a full or opaque cube.
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD).ignitedByLava().noOcclusion();
    }
}
