/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: the gate parts of structure/init/AWStructureBlocks, AWStructureItems and
 * AWStructureSounds, and the aw_gate entity registration of AncientWarfareStructure.
 */
package io.github.capttracker.ancientwarfarestructure.init;

import io.github.capttracker.ancientwarfarestructure.block.GateProxyBlock;
import io.github.capttracker.ancientwarfarestructure.block.entity.GateProxyBlockEntity;
import io.github.capttracker.ancientwarfarestructure.entity.EntityGate;
import io.github.capttracker.ancientwarfarestructure.gate.GateConfig;
import io.github.capttracker.ancientwarfarestructure.gate.GateVehicleLink;
import io.github.capttracker.ancientwarfarestructure.gate.types.Gate;
import io.github.capttracker.ancientwarfarestructure.item.GateBounds;
import io.github.capttracker.ancientwarfarestructure.item.GateSpawnerItem;
import io.github.capttracker.ancientwarfarevehicle.pathing.GateLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.Map;

import static io.github.capttracker.ancientwarfarestructure.AncientWarfareStructure.MOD_ID;
import static io.github.capttracker.ancientwarfarestructure.AncientWarfareStructure.REG;

/**
 * Registrations of the gate slice: the gate entity type, the gate proxy block with its item and block entity, the seven
 * gate spawner items (one per gate type, 1.12.2 had one item with damage values 0, 1, 4, 5, 8, 9 and 12), the item data
 * component that holds the two chosen corners, and the four gate sounds. Everything goes through the module's
 * {@code ModRegistrar} except the entity type, which needs a register the library does not have.
 *
 * <p>Wiring (mod constructor, before {@code REG.register(modBus)}): {@code AwGates.load();
 * AwGates.registerEvents(modBus, container);}. The sounds still need their entries in the shared {@code sounds.json}
 * (port-notes/structure-gates.sounds.json).
 */
public final class AwGates {
    private AwGates() {}

    /** Forces this class to initialise, which registers everything below through the registrars. */
    public static void load() {}

    // ---- entity (1.12.2: "aw_gate", id 0, tracking range 250 blocks = 16 chunks, update every 200 ticks, no velocity updates) ----
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);

    /** Needs an {@code EntityGate(EntityType<?>, Level)} constructor. Not summonable: a gate needs its corners. */
    public static final DeferredHolder<EntityType<?>, EntityType<EntityGate>> GATE = ENTITY_TYPES.register("aw_gate",
            () -> EntityType.Builder.<EntityGate>of(EntityGate::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(16)
                    .updateInterval(200)
                    .setShouldReceiveVelocityUpdates(false)
                    .noSummon()
                    .build(MOD_ID + ":aw_gate"));

    // ---- sounds ----
    public static final DeferredHolder<SoundEvent, SoundEvent> IRON_GATE = REG.sound("iron_gate");
    public static final DeferredHolder<SoundEvent, SoundEvent> IRON_GATE_BREAK = REG.sound("iron_gate_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> WOODEN_GATE = REG.sound("wooden_gate");
    public static final DeferredHolder<SoundEvent, SoundEvent> WOODEN_GATE_BREAK = REG.sound("wooden_gate_break");

    // ---- the proxy block (1.12.2: gate_proxy, tile id gate_proxy_tile; its item had no creative tab) ----
    public static final DeferredBlock<GateProxyBlock> GATE_PROXY = REG.blockWithoutItem("gate_proxy", GateProxyBlock::new, GateProxyBlock.proxyProperties());
    public static final DeferredItem<BlockItem> GATE_PROXY_ITEM = REG.items.registerSimpleBlockItem(GATE_PROXY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GateProxyBlockEntity>> GATE_PROXY_BE =
            REG.blockEntity("gate_proxy", GateProxyBlockEntity::new, GATE_PROXY);

    // ---- the spawner items ----

    /** The two corners chosen with a spawner item (was the AWGateInfo item NBT). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GateBounds>> GATE_BOUNDS =
            REG.dataComponent("gate_bounds", GateBounds.CODEC, GateBounds.STREAM_CODEC);

    /** The spawner item of each gate type, in global id order. */
    public static final Map<Gate, DeferredItem<GateSpawnerItem>> GATE_ITEMS = new LinkedHashMap<>();

    static {
        for (Gate gate : Gate.all()) {
            GATE_ITEMS.put(gate, REG.item(gate.getRegistryName(), properties -> new GateSpawnerItem(properties, gate), new Item.Properties().stacksTo(1)));
        }
    }

    /** The spawner item that places the gate type. */
    public static Item gateItem(Gate gate) {
        return GATE_ITEMS.get(gate).get();
    }

    // ---- events ----

    /** Registers the gate config, then everything of {@link #registerEvents(IEventBus)}. */
    public static void registerEvents(IEventBus modBus, ModContainer container) {
        GateConfig.register(container);
        registerEvents(modBus);
    }

    /** Registers the entity type and the setup that links the vehicles to the gates. Call from the mod constructor. */
    public static void registerEvents(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(AwGates::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        // the Vehicles module's path finder and battering ram find gates through this provider
        event.enqueueWork(() -> GateLookup.setProvider(new GateVehicleLink()));
    }
}
