# Vehicle menus and screens (slice key: vehicle-gui)

Ported from `vehicle/container/**` and `vehicle/gui/**` (with `gui/elements`) of Ancient Warfare 2 (1.12.2-2.7.0.1038). Nothing was compiled or run. Every Minecraft and
NeoForge name was checked against `analysis/mc-sources` (Slot, SlotItemHandler, AbstractContainerMenu, AbstractContainerScreen, GuiGraphics, IMenuTypeExtension,
IContainerFactory, RegisterMenuScreensEvent, RegisterPayloadHandlersEvent, PayloadRegistrar, Entity.getTeam). No assets: the screens use Core's GUI textures.

## Files (package `io.github.capttracker.ancientwarfarevehicle`)

- `menu/VehicleMenu` (ContainerVehicle, the ammo selection menu, no slots; also the base class), `menu/VehicleInventoryMenu` (ContainerVehicleInventory),
  `menu/VehicleMenuPayloads` (new: `OpenAmmoSelection` payload, client to server).
- `init/AwVehicleMenus`: `AMMO_SELECTION_MENU` (`vehicle_ammo_selection`), `INVENTORY_MENU` (`vehicle_inventory`), `load()`, `registerEvents(modBus)`
  (installs `VehicleBridge.setInventoryOpener` and registers the payload).
- `client/screen/GuiVehicleAmmoSelection`, `GuiVehicleInventory`, `GuiVehicleStats`, `client/screen/elements/ButtonAmmo`; `client/VehicleScreensClient`
  (screen registration, installs `VehicleInputClient.setAmmoSelectionOpener`).
- Lang: `vehicle-gui.lang.json` (28 keys; the original strings were hard-coded English with a TODO).

## How it works

- Both menus carry the vehicle as a var int entity id in the extra data buffer. If the client does not know the entity the menu's `vehicle` is null, the screens draw
  nothing and the server closes the menu (it never opens one for a missing vehicle).
- **Inventory** (GUI 51): opened by the server through `VehicleBridge.openInventory` (shift-click on the vehicle, in `VehicleFiringVarsHelper`). Layout numbers are the
  original's (`storageY` 34, `extrasY`, `playerY`, window 195 x `playerY + 84`). Slots: player 0-35, ammo, upgrades, armor, storage (`SlotItemHandler` over the
  `VehicleInventory` handlers, valid items decided by the handlers). Shift-click routing by item class as before, plus the same recount of ammo and upgrades afterwards
  (needed, merges happen in place). Valid while the vehicle is alive and closer than 8 blocks (checked on the server every tick by vanilla).
- **Ammo selection** (GUI 50): the V key (`VehicleInputClient`) calls the installed opener, which sends `OpenAmmoSelection`; the server checks the sender drives the vehicle
  and the type fires ammo, then opens the menu. This is what `NetworkHandler.openGui` did from the client. Choosing ammo sends the existing `AmmoSelect` payload
  (controller authority) through `ammoHelper.handleClientAmmoSelection`.
- **Stats** (GUI 52): client only. `GuiVehicleStats` has a Core `ScreenOnlyMenu` (the DummyContainer of the original) and is shown over the inventory screen with
  `showScreen`, so the inventory menu stays open; closing returns to the inventory screen. Values are read when the screen is built, as in the original.
- **Pack**: `PackCommand` payload (reach authority, 8 blocks, already in the base slice), then the screen closes.

## Changes from 1.12.2

- Storage scrolling: the hidden slots at (-1000, -1000) cannot be moved (slot positions are final). Each storage row is at a fixed place (row modulo 3) and a slot is
  `isActive()` only while its page is showing. The "-" and "+" buttons therefore move by **3 rows** (a page) instead of 1 row. The page is client interface state;
  all 216 slots still exist on the server, so shift-click fills the whole storage as before. See "Rework candidates".
- A trailing partial storage row gets slots (the original dropped them). No real vehicle has such a size (0 or 216).
- Ammo selection menu is only valid for the controlling passenger (others could not select anyway).
- The ammo button's name is the item's hover name instead of the `item.<name>.name` key. The empty `Label`s at the bottom of the stats screen are not created (the gap
  before the team line is kept).
- The stats screen's duplicate registration in the automation module's `ClientProxyAutomation` is not carried over (there is only one screen and it has no menu type).

## Wiring needed in shared files

1. `AncientWarfareVehicles.java` constructor, before `REG.register(modBus)` (import `init.AwVehicleMenus`):
   ```java
   AwVehicleMenus.load();
   AwVehicleMenus.registerEvents(modBus);
   ```
2. `lang/en_us.json`: merge `port-notes/vehicle-gui.lang.json` (28 keys).
3. Nothing for the client: `VehicleScreensClient` is an `@EventBusSubscriber` (client, mod bus). It uses `VehicleInputClient.setAmmoSelectionOpener`, so the base slice's client class
   must stay in the module.
4. Core (not mine): `GuiContainerBase` calls `AwMenu.setRefreshListener`, which is not in the `AwMenu` on disk (the core-gui-toolkit notes list it as a pending shared-file change).
   All three screens depend on it through `GuiContainerBase.bindMenu`.

## Not ported

- `ContainerVehicle.handlePacketData` / `ContainerVehicleInventory.handlePacketData` (empty in the original).
- `GuiVehicleStats`'s three empty labels, `shouldCloseOnVanillaKeys` plumbing beyond the field (Core's base has it).

## Unconfirmed API (check at the first build)

- `missiles.IAmmo` (`getRegistryName()` returning `ResourceLocation`, as the entity slice assumes) and the `missiles` package do not exist yet (missile slice).
- `Slot.isActive()` is honoured by `AbstractContainerScreen` for drawing, hovering and clicking (read in the source: render loop, `findSlot`, `slotClicked` paths); `moveItemStackTo`
  ignores it (intended).
- `IMenuTypeExtension.create(VehicleMenu::new)` with overloaded constructors (the Core menus do the same); `event.<M, U>register` for the screens (as `CoreScreensClient`).
- `RegisterPayloadHandlersEvent.registrar(version)` called a second time in the same mod (the base slice calls it too); the source shows it only creates a new registrar.
- `Entity.getTeam()` for the stats team line (vehicles are not in a scoreboard team unless a command puts them in one, so it normally prints nothing, as in 1.12.2).

## Rework candidates

- Row-by-row storage scrolling: a custom slot that reads from `rowOffset * 9 + i`, with a menu data message (the Core NBT channel) and `sendAllDataToRemote()` on each scroll,
  would restore 1-row steps; or a mouse wheel and scrollbar.
- Make the stats screen live (update the labels every tick) instead of a snapshot; show the real numbers of the server (the client entity's `current*` values depend on
  the upgrade payloads having arrived).
- The ammo selection could be a plain client screen: it holds no server state. The round trip is kept to match the original.
