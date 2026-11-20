# Vehicles module entry, registration, config, registries, items, sounds, network, input (slice key: vehicle-base)

Ported from `vehicle/` of Ancient Warfare 2 (1.12.2-2.7.0.1038): `init/*`, `config/AWVehicleStatics`, `registry/*`, `item/*`,
`network/*`, `input/*`, `proxy/*`, `upgrades/*`, `armors/*`, `AWVehicleTab`, and the registration code of `AncientWarfareVehicles`.
Nothing was compiled or run. Names were checked against `analysis/mc-sources` (Item, EntityType.Builder, StreamCodec, ByteBufCodecs,
PayloadRegistrar, IPayloadContext, PacketDistributor, ModConfigSpec, KeyMapping, ItemOverrides, ItemProperties, DeferredRegister,
Level/EntityGetter/ClipContext/AABB). The FML classes (`ModConfigEvent`, `ModConfig`, `ModContainer`, `FMLCommonSetupEvent`,
`@EventBusSubscriber(bus = ...)`) are not in the extracted sources; they are used as the already ported Core and Automation code uses them.

## Files (package `io.github.capttracker.ancientwarfarevehicle`)

- `init/AwVehicles`: the master list. 104 items, the 2 entity types, the 14 sounds, the spawner data component, the creative tab,
  `load()`, `registerEvents(modBus, container)`, `initRegistries()`.
- `config/AWVehicleStatics`: two `ModConfigSpec`s (common and client), the static fields, the per-vehicle options.
- `registry/`: `VehicleTypeIds` (the 21 indices and config names as plain data), `VehicleRegistry` (the 21 type objects), `AmmoRegistry`
  (51 `IAmmo`), `ArmorRegistry`, `UpgradeRegistry`. `VehicleAmmoEntry` is not here: the entity slice moved it to `helpers/`.
- `item/`: `VehicleSpawnerItem`, `VehicleSpawnData` (component), `ItemMisc`, `ItemAmmo`, `ItemArmor`, `ItemUpgrade`.
- `network/`: `VehicleEntityPayloads` (the nine payload records), `VehicleNetwork` (registration, send helpers, authority checks),
  `VehiclePayload`.
- `input/VehicleInputData` (common side of the driving input).
- `upgrades/` (interface, base, 7 upgrades) and `armors/` (interface, base, 3 armors).
- `client/VehicleInputClient` (13 key mappings, key callbacks, input packet, mouse aim), `VehicleKeyConflictContext`, `VehicleCallback`,
  `VehicleItemPropertiesClient` (the `material_level` model property of the spawners).
- Assets and data: see "Data". Script: `analysis/port/vehicle_base_assets.py` (re-runnable). Lang: `vehicle-base.lang.json`.

## Decisions

- **Items: 104, not 84.** 1.12.2 had one `spawner` item (damage value = type index 0-20, NBT `spawnData:{level, health}`). Now every type has
  its own spawner `ancientwarfarevehicle:<config name>_spawner` (21), plus 22 misc, 51 ammo, 3 armor, 7 upgrade items = 104.
  Level (0-4) and saved health are the data component `ancientwarfarevehicle:vehicle_spawn_data` (`{"level": 2, "health": 80.0}`, health optional,
  a missing component counts as level 0). Model: each spawner's item model has `overrides` on the property
  `ancientwarfarevehicle:material_level` (registered in `VehicleItemPropertiesClient`), 105 textures as before.
- **The 21 vehicle types are not items.** They are the `IVehicleType` objects in `VehicleRegistry` (indices 0-20 = the old damage values).
  `VehicleTypeIds` holds index and config name as constants so the config spec and item list do not need the type objects.
- **Creative tab** `ancientwarfarevehicle:main` is built here (not with `ModRegistrar.creativeTab`): all enabled types at levels 0-4 first (like
  `VehicleType.getCreativeDisplayItems`), then the other items in registration order. Do not call `REG.creativeTab(...)` too (same id).
  Ammo that is not craftable (`ammo_ball_shot`, `ammo_iron_ball_shot`, `ammo_arrow`) is registered but kept out of the tab, like `setCreativeTab(null)`.
- **Registry objects are created at common setup**, not in the item registry event: `AwVehicles.initRegistries()` (idempotent, enqueued from
  `FMLCommonSetupEvent`) creates ammo, armor, upgrade objects and the 21 types, then applies the per-vehicle config. The type constructors read the
  common config (health, oversize ammo), which is loaded by then. `VehicleRegistry` also calls the other three registries from its static
  initialiser, so it is safe if it is touched first; it must not be touched before the common config has loaded.
  `AmmoRegistry`/`ArmorRegistry`/`UpgradeRegistry` throw `IllegalStateException` if an `IAmmo`/armor/upgrade object has no item of the same name in `AwVehicles`.
- **Entity types** use a private `DeferredRegister` inside `AwVehicles` (the library `ModRegistrar` has no entity registry yet; add one there later).
  `vehicle` and `missile`: `MobCategory.MISC`, tracking range 8 chunks (the original's 120 blocks), update interval 3, velocity updates on.
  Vehicle dimensions are 2 x 1.7 (the default type); missile 0.4 x 0.4 (original `setSize`).
- **Network**: 9 payloads in `VehicleEntityPayloads` as nested records named like the entity slice's code already expects
  (`AimUpdate`, `AmmoSelect`, `AmmoUpdate`, `FireUpdate`, `SingleAmmoUpdate`, `UpgradeUpdate`, `VehicleMove`) plus `PackCommand`, `VehicleInput`.
  `PacketTurretAnglesUpdate` (never sent) is dropped. Entity id is a var int. Directions: both ways = aim, ammo select, fire; to server = pack,
  input; to client = ammo update, single ammo update, upgrade update, move. New: server-bound payloads are checked (`VehicleNetwork.Authority`):
  sender must be the controlling passenger, or within 8 blocks for pack; input bytes are clamped to -1..1; strings are limited to 64 characters.
  Payload type ids are `ancientwarfarevehicle:<aim_update|ammo_select|ammo_update|fire_update|pack_command|single_ammo_update|upgrade_update|vehicle_input|vehicle_move>`.
  A vehicle not found in the receiver's level means the payload is dropped (as before).
- **Config**: `ancientwarfarevehicle-common.toml` (gameplay, ammo damage, health, per-vehicle) and `ancientwarfarevehicle-client.toml` (5 render options).
  38 static options kept. Fixes: `adjust_missiles_for_accuracy` moved to the common file (the server's firing code reads it; the field stays in
  `clientSettings` for old call sites); duplicate names `vehicle_ballista_health` / `vehicle_catapult_health` split into `*_boat_health`; trailing
  space of `vehicle_battering_ram_health ` removed; `GeneralSettings.batteringRamBreakChance()` returns `percent / 100.0` (the 1.12.2 integer division
  made the ram never break blocks below 100; the entity slice's `BatteringRamVarHelper` already divides as a double). `enableMouseAim` is a runtime toggle only.
  Per-vehicle options (11 keys x 21 types = 231, section `e_vehicle_config.<config name>`): in 1.12.2 they were inside a block comment, so they never worked. They are real
  options now. Because the built-in values live in the type classes, the numeric ones default to `-999` = keep the built-in value (`VehicleConfig.isSet/or`); `enabled`,
  `craftable`, `add_to_chests` default to true. `VehicleRegistry.registerVehicles()` applies them (enabled, add_to_chests, accuracy, forward_speed [blocks per tick], health,
  missile_speed, pitch_max/min, strafe_speed, turret_rotation). `craftable` is only exposed as `AWVehicleStatics.isVehicleCraftable(configName)`; the original never
  applied it either and no recipe checks it yet. A disabled type stays in `VehicleRegistry` with `isEnabled()` false (the original nulled `VehicleType.vehicleTypes[i]`,
  but `getVehicleForType` and the creative tab already test `isEnabled()`).
  The values are also copied into `entity.VehicleSettings` (the entity slice's own holder) on every load/reload.
- **Input**: 13 `KeyMapping`s (category `keybind.category.awVehicles`, same default keys, `VehicleKeyConflictContext` = riding a `VehicleBase` with no screen open), callbacks via Core's
  `client.input.InputHandler.registerCallBack`. Movement is level-triggered as before (packet on key release or every 20 ticks). Differences: release detection watches the mappings each tick
  (the original compared LWJGL key codes read once at startup, so rebinding broke it); mouse aim ray starts at `Player#getEyePosition` (the CodeChickenLib `RayTracer` is gone), entity
  hits use `isPickable()` and `getPickRadius()`; callbacks check that the player rides a `VehicleBase` (Core's key handler counts presses whatever the context). Opening the ammo selection screen
  goes through `VehicleInputClient.setAmmoSelectionOpener(...)`, which the screens slice has to install (the screen is not in this slice).
- **Smelting** (`SmeltingRecipeRegistry`) is four JSON recipes `recipe/smelting/ammo_iron_shot_{5,10,15,25}.json` (3/6/13/19 iron nuggets, 0.1 xp, 200 ticks).
- **Research recipes**: all 186 converted (0 skipped) with Core's `research_recipes_convert.py` through `vehicle_base_assets.py`, which adds the spawner result:
  `{"id": "ancientwarfarevehicle:<type>_spawner", "components": {"ancientwarfarevehicle:vehicle_spawn_data": {"level": N}}}` (the item map is also in
  `analysis/port/research_recipes_vehicle_items.json`). Files are in `data/ancientwarfarevehicle/recipe/research/{ammo,armor,misc,upgrade,vehicles}/`. The tags they use
  (`c:ingots/iron`, `c:rods/wooden`, `c:strings`, `c:feathers`, ...) are NeoForge common tags.

## Data (copied or generated)

- `assets/ancientwarfarevehicle/textures/item/vehicle/**` (192 png, from `textures/items/vehicle`; names lower-cased, nothing else changed)
- `models/item/*.json`: 83 plain models (51 ammo, 22 misc, 3 armor, 7 upgrade) + 21 `<type>_spawner.json` + 84 `models/item/vehicle/<type>_<1-4>.json`
- `sounds.json` (14 events, the `category` key dropped) and `sounds/*.ogg` (21, unchanged)
- `data/ancientwarfarevehicle/recipe/smelting/*` (4) and `recipe/research/**` (186)
- NOT copied: `textures/model/vehicle`, `textures/entity/vehicle`, `models/vehicle/*.m2f` (render slice), `registry/manual/*/vehicles.json` (Core manual slice; it names only plain items),
  chest and entity loot tables (`loot_tables/chests/ammo_*.json`, `entities/siege_engineer.json`): they belong to the structure/npc data work; the item names they use are unchanged.

## Wiring needed in shared files

1. `AncientWarfareVehicles.java`, constructor, before `REG.register(modBus)`:
   ```java
   AwVehicles.load();
   AwVehicles.registerEvents(modBus, container);   // entity types, payloads, common setup, and AWVehicleStatics.register(modBus, container)
   ```
   (import `io.github.capttracker.ancientwarfarevehicle.init.AwVehicles`). Remove the TODO. Nothing else is needed for the key mappings and item properties
   (`@EventBusSubscriber`, client only).
2. `lang/en_us.json`: merge `port-notes/vehicle-base.lang.json` (249 keys; replaces `block.ancientwarfarevehicle.example_block`).
   The `item.vehicleSpawner.tooltip.*` keys are kept under their old names for the type classes' `getDisplayTooltip()`; the entity slice's notes may rename them.
   The damage death messages (`death.attack.AWMissile*`) belong to the missile slice.
3. `neoforge.mods.toml`: nothing; the module already depends on `ancientwarfare`.
4. `AwNetwork` (Core) is not involved: `AwVehicles.registerEvents` adds its own `RegisterPayloadHandlersEvent` listener (version `"1"`).
5. `libs/capttracker`: add an entity-type registry to `ModRegistrar` when convenient; then move `ENTITY_TYPES` out of `AwVehicles` (bump the library version).
6. Screens slice (client): `VehicleInputClient.setAmmoSelectionOpener(vehicle -> ...)` and a `VehicleBridge.setInventoryOpener` for the inventory menu (already the entity slice's hook).
7. Missile/entity slices must provide: `VehicleBase(EntityType<VehicleBase>, Level)` and `MissileBase(EntityType<MissileBase>, Level)` constructors (used as `VehicleBase::new` / `MissileBase::new`),
   the `missiles.Ammo*` classes with the original constructor signatures (see `AmmoRegistry`), `IAmmo.getRegistryName()` equal to the names in `AwVehicles.AMMO_NAMES`.
   They read the config through `VehicleSettings` (copied) or `AWVehicleStatics.generalSettings/vehicleStats` (`shotsDestroysBlocks`, `blockFires`, `allowFriendlyFire`,
   `useVehicleSetupTime`, ammo damages are only in `AWVehicleStatics`, `VehicleSettings` has no field for them).

## Not ported / dropped

- `ItemBaseVehicle` (creative tab, NPC-module client registration: the registrar and JSON models replace it), `proxy/ClientProxy` and `CommonProxy` (packets, containers, screens, renderers are
  registered by their own slices), `AWVehicleTab` (-> `AwVehicles.TAB`), `SmeltingRecipeRegistry` (-> JSON), `VehicleAmmoEntry` (the entity slice has it in `helpers/`),
  `ArmorType` and `IVehicleArmor.getNeededResearch()` (never used, research was removed), `PacketTurretAnglesUpdate` (never sent), `IVehicleMovementHandler` (the key list in `VehicleInputClient`).
- Screens, menus and the GUI id registration (`GUI_VEHICLE_*`) and renderers/overlay registration of `ClientProxy`: other slices.
- The `vehicle_enabled_for_loot` flag is applied to the type (`setEnabledForLoot`) but nothing reads it (as in 1.12.2).

## Unconfirmed API (check at the first build)

- Vehicle entity API that this slice calls and that did not exist yet when it was written (original names assumed): on `VehicleBase`: `firingHelper`, `ammoHelper`, `upgradeHelper`, `moveHelper`
  (public fields), `vehicleType`, `isAmmoLoaded()`, `isAimable()`, `setHealth(float)`, `setSetupState(boolean, int)`, `packVehicle()`, `localTurretDestRot/localTurretRotation/localTurretRotationHome`,
  `currentAccuracy`, `currentForwardSpeedMax`, `currentReloadTicks`, `currentLaunchSpeedPowerMax`, `currentTurretPitchMax/Min`, `localTurretPitch`, `canAimPitch()`; helper methods
  `handleAimKeyInput(float, float)` (the original ints), `handleAimInput(Vec3)`, `handleFireInput()`, `setNextAmmo()`, `setPreviousAmmo()`. The helper methods the payloads call were found in
  the entity slice's files: `updateAim(Optional...)`, `updateSelectedAmmo`, `updateAmmo(CompoundTag)`, `updateAmmoCount`, `updateUpgrades(String[], String[])`, `serializeUpgrades()`,
  `serializeInstalledArmors()`, `handleFireUpdate()`, `handleInputData(byte x4)`, `updateMoveData(...)`.
- `EntityType.Builder#setShouldReceiveVelocityUpdates` (NeoForge extension, read in the source) and `build(String)` with `"ancientwarfarevehicle:vehicle"` (the data fixer lookup is skipped outside dev).
- Item model `overrides` with a custom property: the property value is compared with `>=` and the last matching override wins (read in `ItemOverrides`); `ItemProperties.register(Item, ResourceLocation, ItemPropertyFunction)`.
- Recipe JSON: `components` of a result stack are decoded by the data component codec (`VehicleSpawnData.CODEC`), and the smelting `result` object (`{"id", "count"}`).
- `StreamCodec.ofMember` for the 8-field `VehicleMove` payload; `ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(64))`.
- `KeyMapping#isDown()` includes the conflict context (read in the source); `@EventBusSubscriber` on a nested static class (`VehicleInputClient.TickEvents`).

## Rework candidates

- The per-ammo tooltip "Can be fired from: ..." is a fixed string; generate it from the vehicle types' valid ammo lists.
- Mouse aim sends aim on most ticks while the view points somewhere (the original skips every fifth): validate/limit on the server (anti-cheat) and store the toggle in the client config.
- `PacketVehicleMove` repeats data that is on the entity; an entity data accessor or a movement payload sent less often would be cheaper.
- The 11 per-vehicle options use a sentinel default; a data-driven vehicle type (datapack JSON) would remove them and the type constructors.
- Ammo could be 12 families with a weight component instead of 51 items; the spawners could be one item with a type component and one model property.
- Vanilla recipe viewers: research recipes are normal recipes of the Core type, so a recipe viewer category would show all 186.
