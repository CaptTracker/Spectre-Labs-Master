# Build record (first compile)

`:ancientwarfarevehicle:compileJava` and `:ancientwarfarevehicle:build` pass (0 errors; 8 `[removal]` warnings for `EventBusSubscriber.bus()`/`Bus` in the four client classes, the same form Core uses).
The ported source needed no API fixes: the first compile was already clean.

## Wired

- `AncientWarfareVehicles` constructor: `AwVehicles.load()` + `AwVehicles.registerEvents(modBus, container)` (entity types, payloads, common setup, both config specs),
  `AwVehicleEntity.load()` + `registerEvents(modBus)` (item handler capability), `AwVehicleMenus.load()` + `registerEvents(modBus)` (two menu types, `OpenAmmoSelection` payload,
  inventory opener), all before `REG.register(modBus)`. TODO removed.
- Client classes (`VehicleInputClient`, `VehicleItemPropertiesClient`, `VehicleRenderClient`, `VehicleScreensClient`, `VehicleAimOverlay`) register themselves with `@EventBusSubscriber`; nothing to wire.
  The renderer slice already registers the vehicle and missile renderers, layer definitions and the HUD layer; the screens slice already registers its two screens (there was no earlier duplicate to remove).
- `lang/en_us.json`: merged the five `*.lang.json` fragments (296 keys, no conflicting texts), removed `block.ancientwarfarevehicle.example_block`. There were no `.sounds.json`
  fragments: `sounds.json` already holds the 14 events and matches the 14 `REG.sound(...)` names and the 21 `.ogg` files. All 104 item models have a lang key.
- No template example content was left in code or data (the empty `blockstates`, `models/block` and `loot_table/blocks` folders are harmless).

## Changed

- Deleted `registry/VehicleAmmoEntry.java`: a duplicate of `helpers/VehicleAmmoEntry.java` (the version the helper and inventory use; the registry copy was unreferenced).
- `network/VehicleEntityPayloads.java` (rebuilt by the entity slice) was compared with the originals in `upstream/.../vehicle/network/` and with every sender and handler:
  nine records, each registered once in `VehicleNetwork.registerPayloads` (ids `aim_update`, `ammo_select`, `ammo_update`, `fire_update`, `pack_command`, `single_ammo_update`,
  `upgrade_update`, `vehicle_input`, `vehicle_move`; the tenth, `open_ammo_selection`, is in `VehicleMenuPayloads`, registered once from `AwVehicleMenus`). Fields, handler calls and directions
  match 1.12.2 (aim, ammo select and fire both ways; pack and input to the server; ammo, single ammo, upgrade and move to the client). Wire format differs from 1.12.2 only in var-int ids,
  optional codecs and the 64 character string limit, which does not matter (no cross-version networking). No change was needed.

## TODO(port) left

- `client/VehicleInputClient`: the mouse aim toggle is not stored in the config yet.
- `missiles/Ammo` (friendly fire): relies on `IVehicleCrew` and `IOwnable` being implemented by `NpcBase` in the NPC module; until then NPC crews are not recognised.
- Wiring owned by other modules (not done here): `NpcBase implements IVehicleCrew`, NPC `ITarget extends IAimTarget`, `EntityGate implements IGateEntity` and `GateLookup.setProvider(...)`.

## Runtime-only risks (nothing was run)

- Registration: the entity types use a private `DeferredRegister` in `AwVehicles` (registered in `registerEvents`, before `REG.register`); the library registrar has no entity registry. Item, sound, component,
  menu and tab names were checked unique by grep and script (21 spawners, 22 misc, 51 ammo, 3 armor, 7 upgrades, 14 sounds, 1 tab, 2 menus, 2 entities, 10 payloads).
- `AwVehicles.initRegistries()` (ammo, armor, upgrades, the 21 vehicle types, `VehicleBridge` stack factory) runs in `FMLCommonSetupEvent.enqueueWork`. Anything that touches `VehicleRegistry` or `AmmoRegistry`
  before common setup (datapack recipes load later, fine; the creative tab is built later, fine; a static initialiser calling them would not be) gets empty registries. The renderer builds its arrow set on the first frame for this reason.
- Config values are copied into plain fields/`VehicleSettings` by listeners; the creative tab and `isVehicleEnabled` read the config when the tab is built, which is after the common config loaded.
- Two `RegisterPayloadHandlersEvent` listeners each call `event.registrar("1")`; the source shows that only creates a new registrar, so no conflict (ids are distinct).
- Capability registration (`AwVehicleEntity`) registers `ItemHandler.ENTITY` and `ENTITY_AUTOMATION` for `AwVehicles.VEHICLE`; the lambdas return `inventory.storageInventory`, which must be non-null on both sides.
- Client-only code: nothing outside `client/` imports `net.minecraft.client` or the client package, and all client subscribers have `value = Dist.CLIENT`. The `VehicleBridge`/`VehicleInputClient` hooks are set from client
  subscribers only, so on a dedicated server `VehicleInputClient.setAmmoSelectionOpener` is never touched.
- Gameplay checks listed in the slice notes still apply (rider camera follows the vehicle turn, custom move payload vs vanilla tracking, turret yaw/pitch signs in the models).
