# NPC module, slice npc-items-orders-trade: items, order slips, trade systems

Ported from `npc/item/**`, `npc/orders/**` and `npc/trade/**` (and the item NBT of the order slips, the spawner and the command baton).
Nothing was built with Gradle or run. The code was checked with a throw-away `javac -sourcepath` pass (scratch output, not a build) against the
extracted 1.21.1 / NeoForge 21.1.266 sources, the real DFU and library jars and every file of the module, including the AI and entity code
that calls this slice: **no error in this slice and none in its callers**. The only errors the pass reported are outside this slice (see "Found in
other files"). Scripts: `analysis/port/npc_items_spawner_recipes.py`, `analysis/port/npc_items_lang.py` (both run with `python -I`).

An earlier partial run had written `orders/OrderCodecs`, `WorkOrder`, `UpkeepOrder`, `CombatOrder` and `trade/Trade`; they were kept, reviewed against the
originals and extended (the data components moved from the not existing `item.NpcItemComponents` to `init.AwNpcItemComponents`).

## What was ported (44 files, 4,106 lines)

`item` (16): `ItemCommandBaton` (+ `BatonMode`), `ItemBardInstrument`, `ItemShield`, `ItemCoin` (+ `CoinMetal`), `ItemFoodBundle`, `ItemMacuahuitl`, `ItemSickle`, `ItemPitchfork`,
`ItemScythe`, `ItemClub`, `ItemIceSpear`, `ItemExtendedReachWeapon`, `IExtendedReachWeapon`, `ItemNpcSpawner`, plus new `NpcSpawnerData` (the spawner component),
`NpcItemAttributes` (attack and reach modifiers). Constructors are exactly the ones `AwNpcs` documents.
`item.order` (7): `ItemOrders` (base), `ItemWorkOrder`, `ItemUpkeepOrder`, `ItemCombatOrder`, `ItemRoutingOrder`, `ItemTradeOrder`, new `OrderGuiBridge`.
`orders` (6): `WorkOrder`, `UpkeepOrder`, `CombatOrder`, `RoutingOrder`, `TradeOrder`, `OrderCodecs` (+ helper `lenientList`, `SLOT_STACKS`).
`trade` (14): `Trade`, `TradeList`, `POTrade`, `POTradeList`, `POTradePoint`, `POTradeRoute`, `POTradeRestockData`, `POTradeTransferEntry`, `POTradeDepositEntry`, `POTradeWithdrawEntry`,
`FactionTrade`, `FactionTradeList`; new `POTradeStock` (finds the backpack a trader holds), `FactionTradeConversions` (template to trade list and back).
`init` (1): `AwNpcItemComponents` (all data components, hooks, events).

Data: the 7 research recipes that craft a spawner now use the `ancientwarfarenpc:npc_spawner` component and `minecraft:custom_model_data`
(`analysis/port/npc_items_spawner_recipes.py`). Models, textures, tags, loot and the 4 order copy recipes were already written by the base slice; all 64 item models exist.

## Data components (init/AwNpcItemComponents, all through `AncientWarfareNPC.REG.dataComponent`)

| Component | Value | 1.12.2 stack tag |
|---|---|---|
| `work_order` | `WorkOrder`: `entryList` [{pos, dim, length}], `priorityType` (ordinal), `nightShift` | `orders` |
| `upkeep_order` | `UpkeepOrder`: `upkeepPosition`, `dim`, `side`, `upkeepAmount` (only written when a point is set) | `orders` |
| `combat_order` | `CombatOrder`: `dim`, `pointList` | `orders` |
| `routing_order` | `RoutingOrder`: `entryList` [{type, position, blockSide, ignoreDamage, ignoreTag, filterList, filterCounts}] | `orders` |
| `trade_order` | `TradeOrder`: `tradeList` {tradeList: [...]}, `tradeRoute` {route: [...]}, `restockEntry` {withdrawPoint/Side, depositPoint/Side, depositList, withdrawList} | `orders` |
| `npc_spawner` | `NpcSpawnerData`: `npcType`, `npcSubtype`, optional `faction`, optional `npcStoredData` | `npcType`, `npcSubtype`, `faction`, `npcStoredData` |
| `baton_mode` | `BatonMode` as its 1.12.2 name (`clear`, `attack`, `move`, `setHome`, ...), default `clear` | `mode` |
| `baton_entities` | list of UUIDs | `entityList.entryList[].uuid` |

All have codecs and stream codecs (the orders use `fromCodecWithRegistries`, so item stacks with components survive the network) and `equals`/`hashCode`. Values are
treated as immutable: `getWorkOrder`, `getRoutingOrder`, `getTradeOrder`, ... return a copy (a fresh object when the stack has no data, null when the stack is not that
order item), `write(stack)` stores a copy. Item stacks inside orders and trades are stored as `{slot, item}` with the 1.21 stack codec (a stack of a missing mod is skipped on
reading, as 1.12.2 dropped unknown items; the whole list is not lost). Routing filters keep stack size 1 in `filterList` and the real quantity in `filterCounts`, as in 1.12.2
(a data component stack is limited to 99); a missing count keeps the stack's own size (1.12.2 indexed the array unchecked).

## Decisions that differ from a line-by-line port

- **Shields** extend vanilla `ShieldItem`: the block animation, 72000 use ticks and blocking (`ItemAbilities.SHIELD_BLOCK`) come from it; the item adds the 2-durability hit, no foil
  and the tier's repair ingredient. The `blocking` model property is registered by the base slice's `NpcItemPropertiesClient`.
- **Weapons and batons use `Tier`** and the item attribute modifiers component: damage amount = tier bonus + offset, speed as given (same numbers as 1.12.2). Extended reach is an
  `entity_interaction_range` modifier of `reach - 3.0` (`NpcItemAttributes.meleeWithReach`) in the main hand, so the vanilla attack reaches that far: `PacketExtendedReachAttack` and
  the client `MouseClickHandler` are not needed. `IExtendedReachWeapon.getReach()` stays for the NPC attack AI.
- **Macuahuitl**: `TieredItem` resets the durability of the properties to the tier's, so `getMaxDamage` returns tier uses minus 100. The dark purple name is a styled name (there is
  no custom rarity colour). Ice spear: `Rarity.RARE`.
- **Club and ice spear** do not lose durability when they hit (1.12.2 replaced `hitEntity` without calling `damageItem`); kept, listed as a rework candidate. Club knockback:
  1.12.2 divided by the horizontal motion of the target, which is NaN for a target that stands still; now it pushes away from the attacker in that case (obvious local fix).
  Knockback is no longer an enchantment of the club (`supportsEnchantment`, `isBookEnchantable`); it disables shields (`canDisableShield`).
- **Scythe**: NeoForge has no `onBlockStartBreak` on items and no `IPlantable`. The 5x5x5 harvest is `ItemScythe.onBlockBreak` on `BlockEvent.BreakEvent` (game bus, registered by
  `AwNpcItemComponents.registerEvents`), which cancels the break when something was harvested, as the original did. "Plant" is `BushBlock`, `SugarCaneBlock` or `CactusBlock`.
- **Coin**: four items `coin_<metal>`, plain currency (no block placing). The ancient coin tooltip is the lang text with
  the style dark aqua italic instead of the `%1$s` formatting codes argument.
- **Bard instruments**: four items; lute = bass, flute, harp, drum = base drum (the default of 1.12.2), `onEntitySwing` cancels the swing.
- **Spawner**: the creative tab stacks come from `ItemNpcSpawner.getSpawnerSubItems()` (a `BuildCreativeModeTabContentsEvent` listener on `AwNpcs.TAB`); every stack gets
  `custom_model_data` = `AwNpcs.getSpawnerModelIndex(variant)` for its icon. `getName` shows the stored NPC's custom name, else the lang key
  `entity.ancientwarfarenpc.[faction.]<type>[.<subtype>].name` (the entity names that the entity slice's lang file has). `ItemNpcSpawner.getSpawnerItemForNpc(NpcBase)` is installed as
  `NpcBridge`'s spawner stack factory, `createNpcFromItem(Level, ItemStack)` as the priest AI's resurrection hook.
- **Command baton**: the commanded NPCs are UUIDs in a component. `getCommandedEntities(Player, ItemStack)` replaces `(World, ItemStack)`: the client search needs the player (it scans the
  NPCs within 256 blocks of him; `ClientLevel` is not available to common code). `getCommandedEntities(Player)` takes the baton from either hand and is installed as the
  `NpcCommand.CommandedEntitiesProvider`. The tooltip no longer writes a `mode` tag into the stack. Range 120 stays a constant (`RANGE`).
- **Order slips**: right click opens the editor through `OrderGuiBridge` (menu slice installs it); the key action (`ItemAltFunction`) adds positions as in 1.12.2. The editor is
  opened with the hand that holds the slip (the key packet finds the slip in the main hand, then the off hand).
- **Trades**: `POTradeList` is `TradeList<POTrade>` (1.12.2: `TradeList<Trade>`); every list implements `INBTSerializable<CompoundTag>` with the `{tradeList: [...]}` layout and has a
  `CODEC` and `STREAM_CODEC` for the order component and the menus. A trade that is not valid after reading (nothing to pay or to get) is dropped, as in 1.12.2.
  `TradeList.performTrade` ignores an index out of range (1.12.2 threw on a bad index from the client). `FactionTradeList.tick` uses the game time (`getTotalWorldTime`).
  Stock of a player owned trader: `POTradeStock.getStorage(entity)` is the item handler capability (core's `ComponentItemHandler`) of the backpack in the NPC's main or off hand.
- `OrderingList` (core) is the base of `WorkOrder`, `CombatOrder`, `RoutingOrder` and `TradeList`; `ItemBaseNPC` (model registration) and `ItemOrders`' creative tab code are gone.

## Wiring needed in shared files

1. `AncientWarfareNPC.java` constructor, before `REG.register(modBus)` (next to the `AwNpcs` lines):
   ```java
   AwNpcItemComponents.load();
   AwNpcItemComponents.registerEvents(modBus);
   ```
   Import `io.github.capttracker.ancientwarfarenpc.init.AwNpcItemComponents`. `registerEvents` installs `NpcBridge.setSpawnerStackFactory`, `NpcCommand.setCommandedEntitiesProvider`,
   `NpcAIPlayerOwnedPriest.setNpcFromStackFactory`, the creative tab listener and the scythe listener on the game bus.
2. `init/AwNpcs.java`, creative tab `displayItems`: the plain (data-less) spawner should not be listed. Remove `first.add(NPC_SPAWNER);` and change the second loop's condition to
   `if (!first.contains(item) && item != NPC_SPAWNER)`. The spawner stacks of every NPC type are added by the listener in `AwNpcItemComponents` (they appear after the other items).
   To keep them at the front as in 1.12.2, instead call `ItemNpcSpawner.getSpawnerSubItems().forEach(output::accept);` right after the `first` loop and delete the listener.
3. Menu slice: `OrderGuiBridge.setGuiOpener((player, hand, gui) -> ...)` opens the work, upkeep, combat, routing and trade order menus for the stack in the given hand; the menus read and
   write the order with the order classes (`WorkOrder.getWorkOrder(stack)` ... `write(stack)`) and sync with the codecs (`WorkOrder.STREAM_CODEC`, ...). The faction trade setup
   menu loads a template with `FactionTradeConversions.toTradeList(template, registryAccess)` and saves with `FactionTradeConversions.fromTradeList(name, list, registryAccess)` +
   `FactionTradeListRegistry.saveTradeList`. The player owned trade menu uses `POTradeStock.getStorage(npc)` and `POTradeList.performTrade(player, storage, index)`.
4. Client slice: order slip highlights via `ItemOrders.getPositionsForRender(stack)` (held slip); command overlay via `ItemCommandBaton.getCommandedEntities(player, stack)` (replaces
   `getCommandedEntities(world, stack)`); the baton mode text via `ItemCommandBaton.getMode(stack).getTranslationKey()`.
5. `lang/en_us.json`: merge `port-notes/npc-items-orders-trade.lang.json` (39 keys: order and baton texts, the 12 `route.*` keys with tooltips, the two item tooltips).
6. `registry/FactionTradeTemplate` and `registry/FactionTradeListTemplate`: the `TODO(port)` notes about `toTrade`/`toTradeList` are done by `trade.FactionTradeConversions` (no change needed, the comments can go).

## Found in other files (by the scratch compile; not changed, not mine)

- Core `crafting/RecipeResourceLocation.java:23`: `"([a-z_]+)\|(.+:.+)"` is an illegal escape in a Java string; it must be `"([a-z_]+)\\|(.+:.+)"`.
- Core `item/HammerItem.java:34`: `PistonHeadBlock` is `net.minecraft.world.level.block.piston.PistonHeadBlock` in 1.21.1.
- NPC `network/NpcNetwork.java:106`: the record component `type` of `NpcCommandPayload` clashes with `CustomPacketPayload.type()` (already reported by the entity slice).
  Everything else of the module compiled in the scratch pass (with scratch copies of the two Core files fixed).

## Not ported

- `trades/trades.cfg` (never read) and `TileRecruitingStation` (dead stub), as instructed.
- `PacketExtendedReachAttack` and `IExtendedReachWeapon.MouseClickHandler` (replaced by the reach attribute), `ItemBaseNPC`/`ModelLoaderHelper` registration, the client mutation of the baton stack in `addInformation`.
- `ItemCommandBaton.getCommandedEntities(World, ItemStack)` (replaced, see above); the sneak right click of the baton is still the original `//TODO openGUI`.
- The Electroblob's Wizardry parts that sat next to these packages (none of the classes of this slice).

## API names that could not be confirmed

None by source: every Minecraft, NeoForge and DFU call was resolved by the scratch compile. Behaviour that only a run can confirm:
- the vanilla attack honours `entity_interaction_range` of a main hand modifier for player attacks (it does in 1.21.1; mobs ignore it, the NPC AI reads `IExtendedReachWeapon.getReach()`);
- `BuildCreativeModeTabContentsEvent` for the custom tab and `minecraft:custom_model_data` as a plain int in recipe results;
- `BlockEvent.BreakEvent` cancel for the scythe also notifies the client correctly after the extra blocks broke;
- `ComponentItemHandler` of the backpack accepts the trader's insert/extract (Core registers it; `POTradeStock` only asks for the capability).

## Rework candidates (not done)

- The club and ice spear never wear out when they hit (kept from 1.12.2); a `postHurtEnemy` with 1 damage would be the vanilla behaviour.
- `NpcSpawnerData` carries the whole stored NPC in a synced component (equipment, orders): large stacks are sent to every client that sees the stack; a server-only part of the component would avoid it.
- `getXOrder(stack)` copies the order on every call (the AI and GUIs call it often); a cache keyed by the component value would do.
- Order slips could use a per-slip `ItemStack` count of 1 (they stack to 64 when equal, as in 1.12.2).
- The baton's `RANGE` 120 as a config option (the original TODO), the sneak-click GUI of the baton.
- `ItemScythe`/`ItemSickle`/`ItemPitchfork` could become real tools (`Tool` component for crops) now that the harvest is an event.
- The coin could place the pile through a Core API interface instead of looking the block up by registry id.
- Template trades keep their stacks as JSON (registry package); `FactionTradeConversions.toTradeList` decodes them each time a trader is set up.
