# NPC module, slice npc-gui: menus and screens

Ported from `npc/container/**` (13 containers plus the wizardry one) and `npc/gui/**` (18 screens) of Ancient Warfare 2 (1.12.2-2.7.0.1038).
Nothing was built with Gradle or run. The slice was type-checked with a throw-away `javac` pass (`analysis/port/npc_gui_javac.sh`, scratch output only)
against the extracted 1.21.1 / NeoForge 21.1.266 sources and the library jars of the Gradle cache: **no error in the slice**. The only error the pass
reports is in another slice (`client/render/NpcRenderer.java:173`, "non-static type variable T cannot be referenced from a static context").
The pass needs scratch copies of three Core files (see "Wiring" 1 and "Found in other files").

## What was ported (46 Java files, about 5,400 lines)

`menu` (23): `NpcMenuBase` (ContainerNpcBase and ContainerEntityBase), `NpcInventoryMenu`, `NpcCreativeControlsMenu`, `NpcSongMenu` + `NpcBardMenu` + `NpcFactionBardMenu`,
`NpcSpellcasterMenu` (the Wizardry container), `FactionTradeSetupMenu`, `FactionTradeViewMenu`, `PlayerOwnedTradeMenu`, `TownHallMenu`, `OrderMenu` (new base)
+ `WorkOrderMenu`, `UpkeepOrderMenu`, `CombatOrderMenu`, `RoutingOrderMenu`, `TradeOrderMenu`, `ISkinSettingsMenu` (ISkinSettingsContainer), `NpcSkinInput`
(new, skin validation), `ISpellcasterNpc` and `NpcSpellSupport` (new, stand-ins for Wizardry), `NpcMenuOpener` and `NpcMenuPayloads` (new: how menus are opened).
`client.screen` (21): `GuiNpcInventory`, `GuiSkinSelection`, `GuiNpcCreativeControls`, `GuiNpcBard`, `GuiNpcFactionBard` (+ new base `GuiNpcSongBase`),
`GuiNpcFactionSpellcasterWizardry`, `GuiNpcFactionTradeSetup`, `GuiNpcFactionTradeView`, `GuiNpcPlayerOwnedTrade`, `GuiTradeTemplateSave`, `GuiTradeTemplateSelect`,
`GuiTownHallInventory`, `GuiTownHallDeathList`, `GuiWorkOrder`, `GuiUpkeepOrder`, `GuiCombatOrder`, `GuiRoutingOrder`, `GuiTradeOrder`; new helpers `GuiNpcBase`
(shared screen base) and `TradeWidgets` (the trade slot grid that four screens each had a copy of).
`init/AwNpcMenus` (14 menu types, hooks, payload registration) and `client/NpcScreensClient` (screen registration). No assets: the screens use Core's GUI textures.
Scripts: `analysis/port/npc_gui_javac.sh` (the type check), `analysis/port/npc_gui_lang.py` (the lang file). Lang: `npc-gui.lang.json` (121 keys).

## How it works

- **Opening.** Every NPC menu carries the NPC as a var int entity id in the extra data buffer (client constructors read it and look it up with
  `player.level().getEntity`); the town hall carries its block position; the order menus carry the hand (`writeEnum(hand)`). The server opens a menu through
  `NpcMenuOpener` (the hook of `NpcBridge`), `TownHallMenu.open` (hook of `TownHallBlockEntity`) and `XxxOrderMenu.open` (hook of `OrderGuiBridge`), then calls
  `sendInitData()`; a menu whose opener forgot is pushed on its first `broadcastChanges()` (same pattern as the Automation menus).
- **Menus the client asks for.** The 1.12.2 screens called `NetworkHandler.openGui` on the client. That is now `NpcMenuPayloads.OpenNpcMenu(entityId, request)` with
  the requests `INVENTORY` (calls `npc.openGUI`), `ALT` (`npc.openAltGui`), `CREATIVE` and `TRADE_SETUP`. The server checks the NPC is alive and within 8 blocks and that the
  player may use the menu; a refused INVENTORY/ALT closes the player's menu (the screens that ask return to it when they close, so they must not be left open).
- **NBT message protocol kept** (the `AwMenu` channel, same keys as 1.12.2): `customName`, `repack`, `setHome`, `clearHome`, `togglefollow`, `donotpursue`, `skinSettings`
  (inventory); `ownerName`, `wander`, `hasCustomEquipment`, `maxHealth`, `attackDamage`, `armorValue`, `skinSettings` (creative); `tuneData` (bards); `tradeData`,
  `doTrade` (trade menus); `deathList`, `range`, `name`, `clear` (town hall); `wo`, `upkeepOrder`, `combatOrder`, `routingOrder`, `tradeOrder` (orders, the order's codec as NBT).
  New keys: `templates`, `applyTemplate`, `saveTemplate` (trade setup), `assignedSpells` (strings now), `presets`, `preset` (spellcaster).
- **Sub-screens** (skin selection, death list, template save/select, `GuiSelectFromList`) have their own empty `ScreenOnlyMenu` so the parent's slots are not drawn over
  them; the parent's menu only supplies data. Same approach as the Core research screens.
- **Who may use what** (`NpcMenuBase.mayEdit`): the commanders of a player-owned NPC (`hasCommandPermissions`), creative players for a faction NPC. Trade menus: anyone. Creative
  controls, trade setup, spellcaster: creative players. Town hall: its owner. Order menus: the stack in the hand must still be that order item. All of it is in `stillValid`, so a
  menu closes itself when the player stops being allowed.

## Server validation (report 08: containers that trusted client NBT)

1. **Order menus** (`OrderMenu`): the original deserialised any order the client sent into the held item. Now the server decodes it with the order's codec and only accepts it if it
   fits the stored order: work, combat and routing points (and trade route points) must be ones the stored order already has (nobody can invent positions), the upkeep and
   restock points must be unchanged, sizes and counts are capped (8 work entries; 64 routing entries and 1,000,000 per filter; 512 trades; 64 route/deposit/withdraw entries),
   delays, work lengths and upkeep amounts are 0 or more. A message that fails is ignored and logged.
2. **Routing order decoder hole** (not my file, found while writing the check): `RoutingOrder.RoutePoint`'s constructor grows its filter list to the `slot` number it reads
   (`while (slot >= filters.size()) filters.add(EMPTY)`), so a client-sent slot of 2 billion would allocate until the server died. `RoutingOrderMenu.isRawValid` rejects slots outside 0 to 11
   before decoding. The same cap should go into the constructor (it also protects a hand-edited item): ignore `slot >= FILTER_SLOTS`.
3. **Inventory**: permission check on every message; name at most 64 characters without control characters; skin settings checked field by field (`NpcSkinInput`: player name
   pattern `[A-Za-z0-9_]{0,16}`, type name `[a-z0-9_.]{0,30}`, skin id at most 100 characters and a valid resource location).
4. **Creative controls**: creative players only (the original opened it for anyone who sent the request); numbers limited to +-1,000,000; owner name at most 32 characters; the skin-only
   message no longer zeroes the other values (the original read missing keys as 0 and then applied them).
5. **Bards**: only for a player who may edit the NPC; tune data limited (128 tunes, sound name 200 characters, length 0 to 36,000 s, volume 0 to 1000 %, delays 0 to 2,400,000 ticks).
6. **Faction trade setup**: creative only; at most 256 trades; template choice and template saving are done by the server (see below); template names cleaned.
7. **Trade views**: the trade index is checked (out of range does nothing); the client no longer performs the trade first.
8. **Town hall**: owner only (the menu is invalid for anyone else); range limited to 0 to the `town_hall_max_range` setting; name at most 64 characters.
9. **Spellcaster**: creative only; spell ids must be in the catalog (at most 64); health limited; skin checked.

## Other changes from 1.12.2

- Entity missing on the client: the original threw `IllegalArgumentException`; the menus hold a null NPC, the screens (`GuiNpcBase`) build nothing and close on their first tick.
- **Trade templates are server data now** (`registry/faction_trade_lists.json` is a data pack file, a remote client has none): the setup menu sends the template names with its first state,
  `applyTemplate` makes the server replace the list and send it back, `saveTemplate` makes the server save through `FactionTradeListRegistry` (the original read and wrote the
  client's registry). The original's two code paths disagreed on which template wins when a default and a faction template share a name (the screen: default; `GuiTradeTemplateSelect`:
  faction); the faction's wins now. `GuiTradeTemplateSelect` is ported but, as in the original, nothing opens it. The "Template Saved" text got a lang key (`guistrings.template.saved`).
- **Trade views**: no client-side prediction. The server answers a `doTrade` with the new list (and the inventory sync shows the items). The player-owned shop keeps the original trick
  of synchronising the trader's backpack slots to the client (off screen, inactive `HiddenSlot`s) so the screen can show only the trades in stock; it rebuilds when those slots change
  (`setItem` / `initializeContents` call `refreshGui()`).
- **Skin selection**: the 40 "tick" cooldown for sending a typed player name now counts game ticks (the original counted rendered frames); the preview is `InventoryScreen.renderEntityInInventoryFollowsMouse`.
- **Inventory screen**: the alt and creative buttons first send the pending name and flag (the server closes the menu when it opens the other one, which lost them).
- **Town hall screen**: the name box is created once (the original made a new one at every `setupElements`, stacking boxes); range and name are menu fields, not written into the client's copy of
  the block entity. `ContainerTownHall.teleportPlayer` (sent a name that nothing read) is not ported.
- **Inventory menu shift-click**: the original also dropped `slot.onTake`'s remainder when the weapon slot was shift-clicked (duplicating a partly moved stack); not ported.
- **Trade order screen**: the side buttons of the restock lists update their text after the side changes (the original did it before). The entry type buttons still show the enum name (`ALL_OF`).
- **Spellcaster**: the preset (name, spells, health, skin) is applied by the server (the NPC defaults are server data) and the name now really reaches the NPC (the original set it on the
  client's copy). Spell ids are registry names (the original sent Wizardry's metadata numbers).
- Order menus apply the order when the message arrives, not when the menu closes (the screen sends it just before closing, so the result is the same). Work, upkeep and combat menus have no
  slots (the original added the player slots and moved them off screen); routing and trade keep the player inventory.
- Lang text with arguments uses `%s` only (`%d`/`%b` become `%s` in the lang file: `%b` is not supported by Minecraft's formatter).

## Wiring needed in shared files

1. `menu/AwMenu.java` (Core, not mine): the refresh listener of `core-gui-toolkit.md` wiring item 1 (`setRefreshListener(Runnable)` public final, `refreshGui()` protected final). All
   these menus call `refreshGui()` and the screens call `setRefreshListener` (through `GuiContainerBase.bindMenu`). Without it the slice does not compile.
2. `AncientWarfareNPC.java` constructor, before `REG.register(modBus)` (import `init.AwNpcMenus`):
   ```java
   AwNpcMenus.load();
   AwNpcMenus.registerEvents(modBus);
   ```
   `registerEvents` installs `NpcBridge.setGuiOpener`, `OrderGuiBridge.setGuiOpener`, `TownHallBlockEntity.setMenuOpener` and registers the `OpenNpcMenu` payload with `event.registrar(NpcNetwork.VERSION)`.
3. `lang/en_us.json`: merge `port-notes/npc-gui.lang.json` (121 keys).
4. Nothing for the client: `client/NpcScreensClient` is an `@EventBusSubscriber` (client, mod bus).
5. Optional, for Wizardry-style spellcasters: the entity implements `menu.ISpellcasterNpc`, calls `NpcMenuOpener.openSpellcaster(player, npc)` from `openAltGui` and reports
   `hasAltGui() == true`; whatever provides the spells calls `NpcSpellSupport.setCatalog(...)` on both sides. Nothing in the module does this (Wizardry has no 1.21.1 version, so
   `NpcFactionSpellcaster` casts nothing); the menu type, screen and server checks exist and are tested only by the type check.
6. `NpcBridge.Gui` has no kind for the creative, trade setup and spellcaster menus; they are opened with `NpcMenuOpener.openCreative / openTradeSetup / openSpellcaster` and the request payload.
   No change needed there.

## Not ported

- `ContainerTownHall.teleportPlayer` (see above); the unused `GuiTradeTemplateSelect` is ported but nothing opens it (as in the original).
- The EBWizardry spell classes themselves (`Spell`, spell metadata numbers): replaced by `NpcSpellSupport.Catalog` and registry-name strings.
- Item-slot mouse polling (`Mouse.getEventButton()` of the routing screen): the slot passes the button as `rightClicked` now.

## API names that could not be confirmed

None by source: every Minecraft, NeoForge and library name was resolved by the scratch compile. Behaviour that only a run can confirm:
- `ServerPlayer.openMenu(MenuProvider, Consumer<RegistryFriendlyByteBuf>)` followed at once by `menu.sendInitData()`: the open-screen packet and the data payload arrive in that order and the
  payload handler runs on the main thread, after the client menu exists (same as the Automation and Vehicle slices).
- `Slot.isActive()` false for the backpack mirror slots of the shop: vanilla skips them when drawing and clicking, but `broadcastChanges` and `initializeContents` still handle them.
- `NeoForgeStreamCodecs.enumCodec` for the request enum.
- `SlotItemHandler` over the NPC equipment handler writes through `setStackInSlot` (an `IItemHandlerModifiable`), which calls `NpcBase.setItemSlot(int, ItemStack)`.
- The preview of the NPC (`renderEntityInInventoryFollowsMouse`) inside the container screen's background pass, scaled 35 as in the original.
- The skin of the preview follows the NPC's server-side settings (tracking payload), as in the original.

## Found in other files (by the scratch compile; not changed, not mine)

- Core `crafting/RecipeResourceLocation.java:23`: `"([a-z_]+)\|(.+:.+)"` needs `\\|` (already reported by the items slice).
- Core `item/HammerItem.java:34`: `PistonHeadBlock` is `net.minecraft.world.level.block.piston.PistonHeadBlock` (already reported).
- NPC `client/render/NpcRenderer.java:173`: "non-static type variable T cannot be referenced from a static context".
- NPC `orders/RoutingOrder.java` (`RoutePoint` constructor): unbounded filter slot, see "Server validation" 2.

## Rework candidates

- The item-bound order menus could use a real "item holder" slot or the item's own slot id instead of the hand, so dual wielding the same kind of slip is unambiguous.
- Screens built with `Button` and `Label` strings are all lang keys already, but the restock entry types (`ALL_OF`, `FILL_TO`, ...) and `Unknown Block` are still raw text.
- The trade setup could keep a list of unsaved edits when a template is applied (the server replaces the list; unsent edits are lost, as in the original).
- A creative player cannot resurrect NPCs from the death list (the original had no button for it either, only the clear button); the data (`canRes`, `resurrected`) is there for it.
- `NpcSongMenu` and the `NpcMenuBase` checks could be shared with the Structures module's sound block screen if that is ever ported.
- The preset buttons and spell list of the spellcaster screen are untested: there is no spell provider for 1.21.1.
