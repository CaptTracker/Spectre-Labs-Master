/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the item NBT of npc/item/ItemWorkOrder, ItemUpkeepOrder, ItemCombatOrder, ItemRoutingOrder, ItemTradeOrder,
 * ItemNpcSpawner and ItemCommandBaton, which 1.21.1 stores in data components.
 */
package io.github.capttracker.ancientwarfarenpc.init;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBridge;
import io.github.capttracker.ancientwarfarenpc.item.ItemCommandBaton;
import io.github.capttracker.ancientwarfarenpc.item.ItemNpcSpawner;
import io.github.capttracker.ancientwarfarenpc.item.ItemScythe;
import io.github.capttracker.ancientwarfarenpc.item.NpcSpawnerData;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand;
import io.github.capttracker.ancientwarfarenpc.orders.CombatOrder;
import io.github.capttracker.ancientwarfarenpc.orders.RoutingOrder;
import io.github.capttracker.ancientwarfarenpc.orders.SeaRoute;
import io.github.capttracker.ancientwarfarenpc.orders.TradeOrder;
import io.github.capttracker.ancientwarfarenpc.orders.UpkeepOrder;
import io.github.capttracker.ancientwarfarenpc.orders.WorkOrder;
import io.github.capttracker.lib.registry.ModRegistrar;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;
import java.util.UUID;

/**
 * The data components of the NPC items, all registered through the module's {@link ModRegistrar}. Every component value is treated as immutable:
 * the order classes hand out copies ({@code getWorkOrder} and so on) and store copies ({@code write}).
 *
 * <ul>
 *   <li>{@code work_order}, {@code upkeep_order}, {@code combat_order}, {@code routing_order}, {@code trade_order}: the 1.12.2 stack tag {@code orders}</li>
 *   <li>{@code npc_spawner}: the stack tags {@code npcType}, {@code npcSubtype}, {@code faction}, {@code npcStoredData}</li>
 *   <li>{@code baton_mode}, {@code baton_entities}: the stack tags {@code mode} and {@code entityList}</li>
 * </ul>
 *
 * <p>Wiring: {@code AwNpcItemComponents.load(); AwNpcItemComponents.registerEvents(modBus);} in the mod constructor, before {@code REG.register(modBus)}.
 */
public final class AwNpcItemComponents {
    private static final ModRegistrar REG = AncientWarfareNPC.REG;

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<WorkOrder>> WORK_ORDER =
            REG.dataComponent("work_order", WorkOrder.CODEC, WorkOrder.STREAM_CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UpkeepOrder>> UPKEEP_ORDER =
            REG.dataComponent("upkeep_order", UpkeepOrder.CODEC, UpkeepOrder.STREAM_CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CombatOrder>> COMBAT_ORDER =
            REG.dataComponent("combat_order", CombatOrder.CODEC, CombatOrder.STREAM_CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RoutingOrder>> ROUTING_ORDER =
            REG.dataComponent("routing_order", RoutingOrder.CODEC, RoutingOrder.STREAM_CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TradeOrder>> TRADE_ORDER =
            REG.dataComponent("trade_order", TradeOrder.CODEC, TradeOrder.STREAM_CODEC);
    /** Rework phase 11: the stops of a sea route chart. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SeaRoute>> SEA_ROUTE =
            REG.dataComponent("sea_route", SeaRoute.CODEC, SeaRoute.STREAM_CODEC);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<NpcSpawnerData>> NPC_SPAWNER =
            REG.dataComponent("npc_spawner", NpcSpawnerData.CODEC, NpcSpawnerData.STREAM_CODEC);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemCommandBaton.BatonMode>> BATON_MODE =
            REG.dataComponent("baton_mode", ItemCommandBaton.BatonMode.CODEC, ItemCommandBaton.BatonMode.STREAM_CODEC);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<UUID>>> BATON_ENTITIES =
            REG.dataComponent("baton_entities", UUIDUtil.CODEC.listOf(), UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()));

    private AwNpcItemComponents() {}

    /** Forces this class to initialise, which registers the components through the registrar. */
    public static void load() {
    }

    /**
     * Installs the links the entity slice and the command code expect from the item slice ({@link NpcBridge#setSpawnerStackFactory},
     * {@link NpcCommand#setCommandedEntitiesProvider}), adds the spawner stacks of every NPC type to the creative tab and registers the scythe's
     * area harvest.
     */
    public static void registerEvents(IEventBus modBus) {
        NpcBridge.setSpawnerStackFactory(ItemNpcSpawner::getSpawnerItemForNpc);
        NpcCommand.setCommandedEntitiesProvider(ItemCommandBaton::getCommandedEntities);
        modBus.addListener(AwNpcItemComponents::onBuildTabContents);
        // the area harvest of the scythes (1.12.2: ItemScythe.onBlockStartBreak)
        NeoForge.EVENT_BUS.addListener(ItemScythe::onBlockBreak);
    }

    private static void onBuildTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(AwNpcs.TAB.getKey())) {
            ItemNpcSpawner.getSpawnerSubItems().forEach(stack -> event.accept(stack));
        }
    }
}
