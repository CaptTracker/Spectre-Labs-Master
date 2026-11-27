/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/init/AWVehicleItems, AWVehicleEntities and AWVehicleSounds, vehicle/AWVehicleTab,
 * and the registration done in vehicle/AncientWarfareVehicles (config, packets).
 */
package io.github.capttracker.ancientwarfarevehicle.init;

import io.github.capttracker.ancientwarfarevehicle.AncientWarfareVehicles;
import io.github.capttracker.ancientwarfarevehicle.config.AWVehicleStatics;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBridge;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.FloatingCrate;
import io.github.capttracker.ancientwarfarevehicle.entity.ship.GrappleHook;
import io.github.capttracker.ancientwarfarevehicle.item.GrapplingHookItem;
import io.github.capttracker.ancientwarfarevehicle.item.ItemAmmo;
import io.github.capttracker.ancientwarfarevehicle.item.ItemArmor;
import io.github.capttracker.ancientwarfarevehicle.item.ItemMisc;
import io.github.capttracker.ancientwarfarevehicle.item.ItemMisc.VehicleItemType;
import io.github.capttracker.ancientwarfarevehicle.item.ItemUpgrade;
import io.github.capttracker.ancientwarfarevehicle.item.VehicleSpawnData;
import io.github.capttracker.ancientwarfarevehicle.item.VehicleSpawnerItem;
import io.github.capttracker.ancientwarfarevehicle.missiles.MissileBase;
import io.github.capttracker.ancientwarfarevehicle.network.VehicleNetwork;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.ArmorRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.UpgradeRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.VehicleRegistry;
import io.github.capttracker.ancientwarfarevehicle.registry.VehicleTypeIds;
import io.github.capttracker.lib.registry.ModRegistrar;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The master list of everything the Vehicles module registers: the spawner, ingredient, ammo, armor and upgrade items, the
 * vehicle and missile entity types, the 14 sounds and the spawner data component. Everything goes through the module's
 * {@link ModRegistrar} ({@link AncientWarfareVehicles#REG}) except the two entity types, which need a register the library
 * does not have yet.
 *
 * <p>Items (1.12.2: 84 items, now 104):
 * <ul>
 *   <li>21 spawners, one per vehicle type ({@code <config name>_spawner}); 1.12.2 had one {@code spawner} item with the type as
 *       damage value and level and health in NBT, now the {@link VehicleSpawnData} component</li>
 *   <li>22 ingredients ({@link ItemMisc}): 7 ammo materials, 15 vehicle components</li>
 *   <li>51 ammunition items ({@link ItemAmmo}), named like the ammo they belong to</li>
 *   <li>3 armor plates ({@link ItemArmor}) and 7 upgrades ({@link ItemUpgrade})</li>
 * </ul>
 * The 21 vehicle types themselves are not items: they are data in {@link VehicleRegistry}.
 *
 * <p>Wiring (mod constructor, before {@code REG.register(modBus)}): {@code AwVehicles.load(); AwVehicles.registerEvents(modBus, container);}.
 */
public final class AwVehicles {
    public static final String MOD_ID = AncientWarfareVehicles.MOD_ID;
    private static final ModRegistrar REG = AncientWarfareVehicles.REG;

    private AwVehicles() {}

    /** Forces this class to initialise, which registers everything above through the registrars. */
    public static void load() {}

    // ---- entity types (1.12.2: AWVehicleEntities; tracking range 120 blocks, update every 3 ticks, velocity updates) ----
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);

    /**
     * The one entity type of all 21 vehicles. The dimensions are those of the default type (catapult stand); the entity
     * resizes itself when its type is set. Needs a {@code VehicleBase(EntityType<VehicleBase>, Level)} constructor.
     * The tracking range of 120 blocks is 8 chunks (the builder takes chunks).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<VehicleBase>> VEHICLE = ENTITY_TYPES.register("vehicle",
            () -> EntityType.Builder.<VehicleBase>of(VehicleBase::new, MobCategory.MISC)
                    .sized(2.0F, 1.7F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .setShouldReceiveVelocityUpdates(true)
                    .build(MOD_ID + ":vehicle"));

    /**
     * The entity type of the big ships (rework phase 4). The same {@code VehicleBase} class as {@link #VEHICLE}; the type tells the entity to carry the part entities of
     * a hull from its constructor on (see {@code ShipHelper}). The box of the entity is only the middle of the hull, the hull itself is its parts.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<VehicleBase>> SHIP = ENTITY_TYPES.register("ship",
            () -> EntityType.Builder.<VehicleBase>of(VehicleBase::new, MobCategory.MISC)
                    .sized(2.5F, 2.5F)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .setShouldReceiveVelocityUpdates(true)
                    .build(MOD_ID + ":ship"));

    /**
     * The entity type of the siege towers (rework phase 12). The same {@code VehicleBase} class; the type tells the entity to carry the part entities of its drop bridge from its
     * constructor on (see {@code SiegeHelper}). The box of the entity is the body of the tower (3 x 3, as tall as its top platform; the size is set again when its type is known).
     */
    public static final DeferredHolder<EntityType<?>, EntityType<VehicleBase>> SIEGE_TOWER = ENTITY_TYPES.register("siege_tower",
            () -> EntityType.Builder.<VehicleBase>of(VehicleBase::new, MobCategory.MISC)
                    .sized(3.0F, 6.0F)
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .setShouldReceiveVelocityUpdates(true)
                    .build(MOD_ID + ":siege_tower"));

    /** The grappling line between two ships (rework phase 11): not saved, a small box at the hook on the target ship's hull. */
    public static final DeferredHolder<EntityType<?>, EntityType<GrappleHook>> GRAPPLE_HOOK = ENTITY_TYPES.register("grapple_hook",
            () -> EntityType.Builder.<GrappleHook>of(GrappleHook::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .noSave()
                    .fireImmune()
                    .build(MOD_ID + ":grapple_hook"));

    /** The crate that floats up from a sunken ship (rework phase 11). */
    public static final DeferredHolder<EntityType<?>, EntityType<FloatingCrate>> FLOATING_CRATE = ENTITY_TYPES.register("floating_crate",
            () -> EntityType.Builder.<FloatingCrate>of(FloatingCrate::new, MobCategory.MISC)
                    .sized(0.9F, 0.9F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build(MOD_ID + ":floating_crate"));

    /** The projectile entity of all 51 ammo types. Needs a {@code MissileBase(EntityType<MissileBase>, Level)} constructor. */
    public static final DeferredHolder<EntityType<?>, EntityType<MissileBase>> MISSILE = ENTITY_TYPES.register("missile",
            () -> EntityType.Builder.<MissileBase>of(MissileBase::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(3)
                    .setShouldReceiveVelocityUpdates(true)
                    .build(MOD_ID + ":missile"));

    // ---- sounds (1.12.2: AWVehicleSounds; the events are described in assets/ancientwarfarevehicle/sounds.json) ----
    public static final DeferredHolder<SoundEvent, SoundEvent> BALLISTA_BOLT_HIT_ENTITY = REG.sound("ballista_bolt_hit_entity");
    public static final DeferredHolder<SoundEvent, SoundEvent> BALLISTA_BOLT_HIT_GROUND = REG.sound("ballista_bolt_hit_ground");
    public static final DeferredHolder<SoundEvent, SoundEvent> BALLISTA_LAUNCH = REG.sound("ballista_launch");
    public static final DeferredHolder<SoundEvent, SoundEvent> BALLISTA_RELOAD = REG.sound("ballista_reload");
    public static final DeferredHolder<SoundEvent, SoundEvent> BATTERING_RAM_LAUNCH = REG.sound("battering_ram_launch");
    public static final DeferredHolder<SoundEvent, SoundEvent> BATTERING_RAM_HIT_WOOD = REG.sound("battering_ram_hit_wood");
    public static final DeferredHolder<SoundEvent, SoundEvent> BATTERING_RAM_HIT_IRON = REG.sound("battering_ram_hit_iron");
    public static final DeferredHolder<SoundEvent, SoundEvent> BATTERING_RAM_HIT_STONE = REG.sound("battering_ram_hit_stone");
    public static final DeferredHolder<SoundEvent, SoundEvent> CATAPULT_LAUNCH = REG.sound("catapult_launch");
    public static final DeferredHolder<SoundEvent, SoundEvent> CATAPULT_RELOAD = REG.sound("catapult_reload");
    public static final DeferredHolder<SoundEvent, SoundEvent> GIANT_TREBUCHET_LAUNCH = REG.sound("giant_trebuchet_launch");
    public static final DeferredHolder<SoundEvent, SoundEvent> TREBUCHET_LAUNCH = REG.sound("trebuchet_launch");
    public static final DeferredHolder<SoundEvent, SoundEvent> TREBUCHET_RELOAD = REG.sound("trebuchet_reload");
    public static final DeferredHolder<SoundEvent, SoundEvent> VEHICLE_MOVING = REG.sound("vehicle_moving");

    // ---- the grappling hook (rework phase 11) ----
    public static final DeferredItem<GrapplingHookItem> GRAPPLING_HOOK = REG.item("grappling_hook", p -> new GrapplingHookItem(p.stacksTo(1).durability(64)));

    // ---- item data component (1.12.2: NBT "spawnData" of the spawner) ----
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<VehicleSpawnData>> SPAWN_DATA =
            REG.dataComponent("vehicle_spawn_data", VehicleSpawnData.CODEC, VehicleSpawnData.STREAM_CODEC);

    // ---- items, in the order of 1.12.2 (AWVehicleItems.register): spawner, 22 misc, ammo, armor, upgrades ----

    /** The spawner of each vehicle type, indexed by global type index (0 to 20). Not part of the registrar's tab list: the tab adds one stack per level. */
    public static final List<DeferredItem<VehicleSpawnerItem>> SPAWNERS;

    /** The 22 ingredient items by registry path, in registration order. */
    public static final Map<String, DeferredItem<ItemMisc>> MISC_ITEMS;

    /**
     * Registry paths of the 51 ammo items in the order of {@code AmmoRegistry.registerAmmo}. They must equal the registry names the
     * {@code Ammo*} classes pass to their constructor ({@code AmmoRegistry} checks that at setup).
     */
    public static final List<String> AMMO_NAMES = List.of(
            "ammo_ball_shot", "ammo_iron_ball_shot",
            "ammo_stone_shot_10", "ammo_stone_shot_15", "ammo_stone_shot_30", "ammo_stone_shot_45",
            "ammo_flame_shot_10", "ammo_flame_shot_15", "ammo_flame_shot_30", "ammo_flame_shot_45",
            "ammo_explosive_shot_10", "ammo_explosive_shot_15", "ammo_explosive_shot_30", "ammo_explosive_shot_45",
            "ammo_explosive_shot_10_big", "ammo_explosive_shot_15_big", "ammo_explosive_shot_30_big", "ammo_explosive_shot_45_big",
            "ammo_napalm_shot_10", "ammo_napalm_shot_15", "ammo_napalm_shot_30", "ammo_napalm_shot_45",
            "ammo_cluster_shot_10", "ammo_cluster_shot_15", "ammo_cluster_shot_30", "ammo_cluster_shot_45",
            "ammo_pebble_shot_10", "ammo_pebble_shot_15", "ammo_pebble_shot_30", "ammo_pebble_shot_45",
            "ammo_iron_shot_5", "ammo_iron_shot_10", "ammo_iron_shot_15", "ammo_iron_shot_25",
            "ammo_canister_shot_5", "ammo_canister_shot_10", "ammo_canister_shot_15", "ammo_canister_shot_25",
            "ammo_grape_shot_5", "ammo_grape_shot_10", "ammo_grape_shot_15", "ammo_grape_shot_25",
            "ammo_arrow", "ammo_ballista_bolt", "ammo_ballista_bolt_flame", "ammo_ballista_bolt_explosive", "ammo_ballista_bolt_iron",
            "ammo_hwacha_rocket", "ammo_hwacha_rocket_flame", "ammo_hwacha_rocket_explosive", "ammo_hwacha_rocket_airburst");

    /** Ammo that is not craftable ({@code Ammo.isAvailableAsItem()} false): registered, but hidden from the creative tab. */
    private static final List<String> HIDDEN_AMMO = List.of("ammo_ball_shot", "ammo_iron_ball_shot", "ammo_arrow");

    /** The 51 ammo items by registry path (= ammo registry name path), in registration order. */
    public static final Map<String, DeferredItem<ItemAmmo>> AMMO_ITEMS;

    /** {@code armor_stone}, {@code armor_iron}, {@code armor_obsidian}. */
    public static final Map<String, DeferredItem<ItemArmor>> ARMOR_ITEMS;

    /** The seven {@code vehicle_upgrade_*} items. */
    public static final Map<String, DeferredItem<ItemUpgrade>> UPGRADE_ITEMS;

    static {
        List<DeferredItem<VehicleSpawnerItem>> spawners = new ArrayList<>();
        for (int typeId = 0; typeId < VehicleTypeIds.COUNT; typeId++) {
            final int id = typeId;
            spawners.add(REG.items.registerItem(VehicleTypeIds.spawnerName(typeId),
                    properties -> new VehicleSpawnerItem(properties, id), new Item.Properties().stacksTo(1)));
        }
        SPAWNERS = Collections.unmodifiableList(spawners);

        Map<String, DeferredItem<ItemMisc>> misc = new LinkedHashMap<>();
        for (String name : new String[]{"flame_charge", "explosive_charge", "rocket_charge", "cluster_charge", "napalm_charge", "clay_casing", "iron_casing"}) {
            misc.put(name, REG.item(name, p -> new ItemMisc(p, VehicleItemType.AMMO_MATERIAL)));
        }
        for (String name : new String[]{"mobility_unit", "turret_components", "torsion_unit", "counter_weight_unit", "powder_case", "equipment_bay",
                "rough_wood", "treated_wood", "ironshod_wood", "iron_core_wood", "rough_iron", "fine_iron", "tempered_iron", "minor_alloy", "major_alloy"}) {
            misc.put(name, REG.item(name, p -> new ItemMisc(p, VehicleItemType.VEHICLE_COMPONENT)));
        }
        MISC_ITEMS = Collections.unmodifiableMap(misc);

        Map<String, DeferredItem<ItemAmmo>> ammo = new LinkedHashMap<>();
        for (String name : AMMO_NAMES) {
            boolean available = !HIDDEN_AMMO.contains(name);
            DeferredItem<ItemAmmo> item;
            if (available) {
                item = REG.item(name, p -> new ItemAmmo(p, name, true));
            } else {
                // registered without a creative tab entry (1.12.2: setCreativeTab(null))
                item = REG.items.registerItem(name, p -> new ItemAmmo(p, name, false), new Item.Properties());
            }
            ammo.put(name, item);
        }
        AMMO_ITEMS = Collections.unmodifiableMap(ammo);

        Map<String, DeferredItem<ItemArmor>> armor = new LinkedHashMap<>();
        for (String name : new String[]{"armor_stone", "armor_iron", "armor_obsidian"}) {
            armor.put(name, REG.item(name, p -> new ItemArmor(p, name)));
        }
        ARMOR_ITEMS = Collections.unmodifiableMap(armor);

        Map<String, DeferredItem<ItemUpgrade>> upgrades = new LinkedHashMap<>();
        for (String name : new String[]{"vehicle_upgrade_speed", "vehicle_upgrade_aim", "vehicle_upgrade_reload", "vehicle_upgrade_power",
                "vehicle_upgrade_turret_pitch", "vehicle_upgrade_pitch_up", "vehicle_upgrade_pitch_down"}) {
            upgrades.put(name, REG.item(name, p -> new ItemUpgrade(p, name)));
        }
        UPGRADE_ITEMS = Collections.unmodifiableMap(upgrades);
    }

    // ---- creative tab (1.12.2: AWVehicleTab, icon = the spawner with damage 0, the catapult stand) ----
    /** Title {@code itemGroup.ancientwarfarevehicle}. Spawners first (every enabled type at every material level), then the rest in registration order. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = REG.creativeTabs.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MOD_ID))
            .icon(() -> VehicleSpawnerItem.createStack(VehicleTypeIds.CATAPULT_STAND_FIXED, 0))
            .displayItems((parameters, output) -> {
                // 1.12.2: VehicleType.getCreativeDisplayItems
                for (int typeId = 0; typeId < VehicleTypeIds.COUNT; typeId++) {
                    if (!AWVehicleStatics.isVehicleEnabled(typeId)) {
                        continue;
                    }
                    for (int level = 0; level < VehicleTypeIds.LEVELS; level++) {
                        output.accept(VehicleSpawnerItem.createStack(typeId, level));
                    }
                }
                REG.tabContents().forEach(item -> output.accept(item.get()));
            })
            .build());

    // ---- events ----

    /**
     * Registers what is not covered by {@code REG.register(modBus)}: the entity types, the payloads and the setup of the rule
     * registries, plus the config (both specs and the listeners that bake them). Call from the mod constructor.
     */
    public static void registerEvents(IEventBus modBus, ModContainer container) {
        AWVehicleStatics.register(modBus, container);
        registerEvents(modBus);
    }

    /** As {@link #registerEvents(IEventBus, ModContainer)} but without the config; use it only if the config is registered elsewhere. */
    public static void registerEvents(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener(AwVehicles::onRegisterPayloads);
        modBus.addListener(AwVehicles::onCommonSetup);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        VehicleNetwork.registerPayloads(event.registrar(VehicleNetwork.VERSION));
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        // the common config is loaded by now; the rule objects read it when they are created
        event.enqueueWork(AwVehicles::initRegistries);
    }

    private static boolean registriesInitialised = false;

    /**
     * Creates the ammo, armor and upgrade rule objects and the 21 vehicle types, and applies the per-vehicle config. Runs once at
     * common setup (1.12.2: inside the item registry event); calling it again does nothing.
     */
    public static synchronized void initRegistries() {
        if (registriesInitialised) {
            return;
        }
        registriesInitialised = true;
        // the vehicle entity code builds spawner stacks through this hook (VehicleBase#getItemForVehicle, IVehicleType#getStackForLevel)
        VehicleBridge.setStackFactory((type, materialLevel, health) -> health == null
                ? VehicleSpawnerItem.createStack(type.getGlobalVehicleType(), materialLevel)
                : VehicleSpawnerItem.createStack(type.getGlobalVehicleType(), materialLevel, health));
        AmmoRegistry.registerAmmo();
        ArmorRegistry.registerArmorTypes();
        UpgradeRegistry.registerUpgrades();
        VehicleRegistry.registerVehicles();
    }
}
