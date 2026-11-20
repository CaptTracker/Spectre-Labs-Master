/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 1): settlements.
 */
package io.github.capttracker.ancientwarfarenpc.init;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementEvents;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementLedgerItem;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementMarkerBlock;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementMarkerBlockEntity;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementMarkerItem;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementNetwork;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/** Registrations of the settlements: the boundary marker (block, item, block entity), the ledger, the payload and the config. */
public final class AwNpcSettlements {
    private AwNpcSettlements() {}

    public static final DeferredBlock<SettlementMarkerBlock> SETTLEMENT_MARKER = AncientWarfareNPC.REG.blockWithoutItem("settlement_marker",
            SettlementMarkerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD)
                    .noOcclusion().pushReaction(PushReaction.DESTROY));

    public static final DeferredItem<SettlementMarkerItem> SETTLEMENT_MARKER_ITEM = AncientWarfareNPC.REG.item("settlement_marker",
            p -> new SettlementMarkerItem(SETTLEMENT_MARKER.get(), p));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SettlementMarkerBlockEntity>> SETTLEMENT_MARKER_BE =
            AncientWarfareNPC.REG.blockEntity("settlement_marker", SettlementMarkerBlockEntity::new, SETTLEMENT_MARKER);

    public static final DeferredItem<SettlementLedgerItem> SETTLEMENT_LEDGER = AncientWarfareNPC.REG.item("settlement_ledger",
            SettlementLedgerItem::new, new Item.Properties().stacksTo(1));

    /** Forces this class (and so the registrations above) to initialise. Call from the mod constructor. */
    public static void load() {}

    /** Registers the config, the geometry payload and the game bus handlers. Call from the mod constructor. */
    public static void registerEvents(IEventBus modBus, ModContainer container) {
        SettlementConfig.register(modBus, container);
        modBus.addListener(AwNpcSettlements::onRegisterPayloads);
        NeoForge.EVENT_BUS.register(SettlementEvents.INSTANCE);
        NeoForge.EVENT_BUS.register(io.github.capttracker.ancientwarfarenpc.caravan.CaravanScheduler.INSTANCE);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        SettlementNetwork.registerPayloads(event.registrar(io.github.capttracker.ancientwarfarenpc.network.NpcNetwork.VERSION));
    }
}
