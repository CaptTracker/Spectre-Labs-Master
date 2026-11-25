# NPCs: items, orders, trading, registries, GUIs, compat, config
> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

Packages covered (all under `npc/`): `item/` (22 files, 1,825 lines), `orders/` (6, 924), `trade/` (13, 874), `registry/` (13, 1,305), `gui/` (19, 3,024), `container/` (17, 1,191), `block/` (3, 106), `tile/` (3, 277), `crafting/` (2, 121), `inventory/` (2, 140), `config/` (2, 191), `gamedata/` (4, 592), `command/` (4, 207), `datafixes/` (8, 490), `compat/` including `compat/ebwizardry` (6, 438), and `init/` (5, 807; read for item and block registration, while `AWNPCEntities` and most of `AWNPCSounds` belong to other slices). Total 129 files and 12,512 lines (summed with `wc -l` over the listed directories, including `package-info.java`). Three files directly in `npc/` (`AncientWarfareNPC` and two others) and the `entity/`, `ai/`, `render/`, `network/`, `proxy/` and `skin/` trees belong to other slices. Data files analysed: the 5 registry JSONs under `RES/assets/ancientwarfare/registry/npc`, `registry/compat/ebwizardry/entity_spawn_nbt.json`, `trades/trades.cfg`, and `loot_tables/entities` (20 files).

This part of the mod is the player-facing control surface of the NPC module: every item the player holds (spawner, order slips, command baton, coin, weapons, shields, bard instruments), the data model behind the order slips (work, upkeep, combat, routing, trade), the player-owned and faction trade systems, the JSON registries that define 44 factions, NPC stat defaults and 130 trade-list templates, the Town Hall block, per-player faction standing and team standing saved data, and the Electroblob's Wizardry add-on. The NPC entities, AI and renderers are in other slices; this slice supplies the configuration and orders they read.

## What it adds

### Blocks

| Registry name | Class | What it is | Porting note |
|---|---|---|---|
| `ancientwarfarenpc:town_hall` | `block/BlockTownHall` (extends `BlockBaseNPC`) | Rock block (hardness 2), non-opaque, cutout layer, owner-only GUI; redstone power sets `alarmActive` on the tile (`neighborChanged`) | Single block, no variants. Needs a `BlockEntity`. Block item is `ItemBlockOwned` from core (stores owner). |

Registered in `init/AWNPCBlocks.java`. `tile/TileRecruitingStation` exists but is never registered and has no block (stub with a TODO); do not port it.

### Items

58 standalone items plus 1 ItemBlock (town hall) are registered in `init/AWNPCItems.java` (`register`). STATS counts classes, not registrations, so this table comes from the registration code.

| Registry name(s) | Class | What it is | Porting note |
|---|---|---|---|
| `wooden_command_baton`, `stone_command_baton`, `iron_command_baton`, `gold_command_baton`, `diamond_command_baton` (5) | `item/ItemCommandBaton` | Melee weapon (attack 4 + tier damage, speed -2.3) that selects NPCs and issues commands by mode | State in NBT (`mode`, `entityList`): 2 data components. Tier from `Item.ToolMaterial` becomes a `Tier`. Needs key action + scroll handling. |
| `bard_instrument` (meta 0-3: lute, flute, harp, drum) | `item/ItemBardInstrument` | Plays a vanilla note-block sound on right click (drum is the fallback sound) | 4 metadata variants: split into 4 items. |
| `wooden_shield`, `stone_shield`, `iron_shield`, `gold_shield`, `diamond_shield` (durability 336, 506, 759, 64, 1138) | `item/ItemShield` | Custom shield (`EnumAction.BLOCK`, 72000 use ticks, `isShield` true) | Rebuild on vanilla shield behaviour (unverified exact hook). Replace OreDict repair with a repair ingredient. |
| `shield_tribal_1`, `shield_tribal_2`, `shield_round_1` to `shield_round_6`, `shield_buffloka` (WOOD, 336) | `item/ItemShield` | Cosmetic wooden shields | 9 items differing only by model. |
| `shield_witchbane_1`, `shield_witchbane_2` (IRON, 759) | `item/ItemShield` | Cosmetic iron shields | Total shields: 16. |
| `work_order` | `item/ItemWorkOrder` | Order slip: up to 8 worksite positions | NBT `orders`: data component. |
| `upkeep_order` | `item/ItemUpkeepOrder` | Order slip: one upkeep inventory position | NBT `orders`: data component. |
| `combat_order` | `item/ItemCombatOrder` | Order slip: patrol point list | NBT `orders`: data component. |
| `routing_order` | `item/ItemRoutingOrder` | Order slip: courier route with item filters | NBT `orders`: data component containing item stacks. |
| `trade_order` | `item/ItemTradeOrder` | Order slip: trader route, restock and trade list | NBT `orders`: data component; the largest of the five. |
| `npc_spawner` | `item/ItemNpcSpawner` | Stack size 16; spawns an NPC and can store a whole NPC (equipment, levels, name) | NBT `npcType`, `npcSubtype`, `faction`, `npcStoredData`: data component(s). Creative tab lists one stack per NPC type, subtype and faction (`getSpawnerSubItems`). |
| `coin` (NBT `metal`: ancient, gold, silver, copper) | `item/ItemCoin` | Currency; 8 coins right-clicked place a coin-stack block from the Structures module | NBT string `metal`: split into 4 items. Depends on 4 `COIN_STACK_*` blocks in structure. |
| `food_bundle` | `item/ItemFoodBundle` (extends `ItemFood`: 15 hunger, 1.0 saturation, wolf food) | Food; registered in OreDict as `foodBundle` | Food properties component; OreDict becomes an item tag. |
| `macuahuitl` (IRON, durability max-100) | `item/ItemMacuahuitl` | Sword, dark-purple rarity, repaired with obsidian | Sword item. |
| `sickle` (IRON, speed -2.3) | `item/ItemSickle` | Attack attributes only; no harvest logic | Attribute modifiers only. |
| `pitchfork` (IRON, speed -2.3) | `item/ItemPitchfork` | Attack attributes only | Same. |
| `scythe` (IRON), `death_scythe` (DIAMOND; Wither 100 ticks on hit) | `item/ItemScythe` | Harvests every `IPlantable` block in a 5x5x5 cube on block start-break (`onBlockStartBreak`) | Needs a break hook (unverified which); `IPlantable` replaced by a crop/bush check. |
| `giant_club` (DIAMOND; 3.5 / -3.6 / reach 4.2) | `item/ItemClub` | Knockback weapon, disables shields, cannot take the Knockback enchant | Extended-reach item. |
| `ice_spear` (DIAMOND, rare) | `item/ItemIceSpear` | Slowness 50 ticks and Mining Fatigue 150 ticks on hit | Mob effects on hit. |
| `wooden_/stone_/iron_/golden_/diamond_` + `spear` (2 / -3 / 4.2), `halberd` (3 / -3.2 / 4.5), `lance` (2.5 / -3.2 / 5.5), `cleaver` (3.5 / -2.8 / 3.0): 20 items | `item/ItemExtendedReachWeapon` (extends `ItemSword`, implements `IExtendedReachWeapon`) | Swords with a custom reach value (offset / speed / reach in brackets) | Reach was custom (`PacketExtendedReachAttack`); use a reach attribute modifier in 1.21 (unverified exact id). |
| `obsidian_spear` (DIAMOND) | `item/ItemExtendedReachWeapon` | Single unique spear | As above. |
| `ancientwarfarenpc:town_hall` (ItemBlock) | core `item/ItemBlockOwned` | Block item for the town hall | Owner stored on placement. |

Totals: 5 + 1 + 16 + 5 + 1 + 1 + 1 + 1 + 1 + 1 + 2 + 1 + 1 + 21 = 58 items, plus 1 ItemBlock = 59 registrations. After flattening metadata (bard 4, coin 4) the port has 64 items.

Creative tab: `AWNPCTab` (`tabs.npc`, icon `npc_spawner`) with a custom sort comparator that lists the town hall and a fixed set first.

### Tile entities

| Id | Class | What it is | Porting note |
|---|---|---|---|
| `ancientwarfarenpc:town_hall_tile` | `tile/TileTownHall` (extends core `TileOwned`; `ITickable`, `IInteractableTile`, `IBlockBreakHandler`) | Town Hall: broadcasts to owned NPCs, 27-slot inventory, death-notice list, name, range, alarm flag | See "Town Hall". Needs `BlockEntity`, NBT save/load, item handler capability. |
| (not registered) | `tile/TileRecruitingStation` | Dead stub | Skip. |

### Containers and GUIs

GUI ids are `NetworkHandler.GUI_NPC_*` ints from core; containers are registered in `AncientWarfareNPC.preInit` and screens in `proxy/NpcClientProxy`. All sync by hand-rolled NBT packets (`sendDataToServer`, `sendDataToClient`, `handlePacketData` in core `ContainerBase`). The NPC entity id is passed as the GUI argument x; order GUIs use the held item.

| GUI id | Container | Screen | What it edits | Sync |
|---|---|---|---|---|
| 4 `GUI_NPC_INVENTORY` | `ContainerNpcInventory` (+`ContainerNpcBase`) | `GuiNpcInventory` | NPC equipment (8 slots through `NpcEquipmentHandler`), name, repack, set/clear home, follow toggle, do-not-pursue, skin | NBT keys `customName`, `repack`, `setHome`, `clearHome`, `togglefollow`, `donotpursue`, `skinSettings` |
| 6 `GUI_NPC_TRADE_ORDER` | `ContainerTradeOrder` | `GuiTradeOrder` (461 lines) | Whole `TradeOrder` in the held item | One `tradeOrder` compound to server |
| 34 `GUI_NPC_WORK_ORDER` | `ContainerWorkOrder` | `GuiWorkOrder` | `WorkOrder` in held item | `wo` compound |
| 35 `GUI_NPC_UPKEEP_ORDER` | `ContainerUpkeepOrder` | `GuiUpkeepOrder` | `UpkeepOrder` in held item | `upkeepOrder` compound |
| 36 `GUI_NPC_COMBAT_ORDER` | `ContainerCombatOrder` | `GuiCombatOrder` | `CombatOrder` in held item | `combatOrder` compound |
| 37 `GUI_NPC_ROUTING_ORDER` | `ContainerRoutingOrder` | `GuiRoutingOrder` | `RoutingOrder`, 12 ghost filter slots per point | `routingOrder` compound |
| 39 `GUI_NPC_FACTION_TRADE_SETUP` | `ContainerNpcFactionTradeSetup` | `GuiNpcFactionTradeSetup` (+ `GuiTradeTemplateSelect`, `GuiTradeTemplateSave`) | Faction trader's `FactionTradeList`; can save templates to config JSON | `tradeData` compound; save calls `FactionTradeListRegistry.saveTradeList` / `saveFactionTradeList` |
| 41 `GUI_NPC_TOWN_HALL` | `ContainerTownHall` | `GuiTownHallInventory` (+ `GuiTownHallDeathList`) | 27-slot inventory, range, name, death list (resurrect, clear) | `deathList` down; `range`, `name`, `playerName`, `clear` up |
| 42 `GUI_NPC_FACTION_TRADE_VIEW` | `ContainerNpcFactionTradeView` | `GuiNpcFactionTradeView` | Player buys from faction trader | `tradeData` down, `doTrade` index up; server runs `FactionTradeList.performTrade` |
| 43 `GUI_NPC_BARD` | `ContainerNpcBard` | `GuiNpcBard` | Bard tune (`SongPlayData`, core) | `tuneData` compound |
| 44 `GUI_NPC_CREATIVE` | `ContainerNpcCreativeControls` | `GuiNpcCreativeControls` | Owner name, skin, wander, max health, attack damage, armor value, custom-equipment flag | Whole container state as one NBT |
| 47 `GUI_NPC_PLAYER_OWNED_TRADE` | `ContainerNpcPlayerOwnedTrade` | `GuiNpcPlayerOwnedTrade` | Customer buys from a player-owned trader, stock is the trader's held backpack | `tradeData` down, `doTrade` up; backpack slots mirrored at x=100000 so the client can validate |
| 49 `GUI_NPC_FACTION_BARD` | `ContainerNpcFactionBard` | `GuiNpcFactionBard` | Faction bard tune | as bard |
| 62 `GUI_NPC_FACTION_SPELLCASTER_WIZARDRY` | `ContainerNpcFactionSpellcasterWizardry` | `GuiNpcFactionSpellcasterWizardry` | Max health, skin, assigned spells | `assignedSpells` int list + skin; registered only if `ebwizardry` is loaded |

14 containers, 14 registered screens, plus 4 helper screens (`GuiSkinSelection` 301 lines, `GuiTradeTemplateSave`, `GuiTradeTemplateSelect`, `GuiTownHallDeathList`): 18 GUI classes. `ISkinSettingsContainer` is a helper interface. Faction/trader interaction: `NpcTrader.processInteract` opens the trade GUI for non-owners only while the NPC has food remaining.

### Network packets

None defined in this slice. `AncientWarfareNPC.preInit` registers `PacketNpcCommand` (12), `PacketFactionUpdate` (13), `PacketExtendedReachAttack` (27), `PacketStructureEntry` (29), `PacketTeamMembershipUpdate` (31), `PacketTeamStandingsUpdate` (32), `PacketTeamStandingUpdate` (33). Their classes live in `npc/network` and `structure/network`; this slice uses them from `FactionData`, `TeamData` and `ItemCommandBaton`.

### Commands

| Name | Class | What it does | Porting note |
|---|---|---|---|
| `/awfaction get <player>` | `command/CommandFaction` (level 2) | Prints every faction standing of a player | Brigadier. |
| `/awteams list`, `describe <team>`, `player [name]` | `command/CommandTeams` (extends core `RootCommand`) | Lists teams, members, standings | Brigadier tree with 3 subcommands. |
| `/awnpcdebug` | `command/CommandDebugAI` (level 2) | Toggles `AWNPCStatics.npcAIDebugMode`, saved in per-world `WorldData` key `NpcAIDebugMode` | Persist in a saved-data flag. |

Registered in `AncientWarfareNPC.serverStart`.

### Config options

All in `config/AWNPCStatics.java` (extends core `ModConfiguration`, file `AncientWarfareNpc.cfg`). Defaults are the field initialisers.

| Key | Default | Meaning |
|---|---|---|
| `npc_max_level` (deprecated) | 10 | Old combined max level |
| `npc_worker_max_level` | 10 | Worker max level |
| `npc_combat_max_level` | 10 | Combat max level |
| `npc_xp_per_attack` | 1 | XP on damage |
| `npc_xp_per_kill` | 5 | XP on kill |
| `npc_level_damage_multiplier` | 0.05 | Damage bonus per level (comment: level 10 does 2x) |
| `npc_xp_per_trade` | 1 | XP per trade |
| `npc_xp_per_work` | 1 | XP per work action (owned only) |
| `npc_xp_per_item_moved` | 1 | XP per courier move (owned only) |
| `npc_work_ticks` | 50 | Ticks between worksite work calls (`NpcAIPlayerOwnedWork.doWork`) |
| `allow_upkeep_any_inventory` | true | If false only town halls accept upkeep points |
| `town_hall_max_range` | 100 | Cap on town hall range |
| `town_hall_ticks` | 100 | Ticks between town hall broadcasts |
| `load_default_skin_pack` (client) | true | Load bundled skins |
| `archer_attack_range` | 60.0 | Archer range (mounted archers half) |
| `render_npc_ai`, `render_work_points`, `render_friendly_health`, `render_hostile_health`, `render_team_colors` (client) | true | Debug and HUD rendering |
| `render_friendly_nameplates`, `render_hostile_nameplates` (client) | false | Name plates |
| `npc_action_range` (general) | 3 | Worker action range (minimum 3) |
| `npc_vanilla_equipment_drop_rate` (general) | true | Faction NPCs use the vanilla drop chance |
| `npc_repack_creative_only` (general) | false | Repack only in creative |
| `npc_death_keep_orders_items` (general) | true | NPC keeps order items on death if a town hall caught the death |
| `food_multiplier` (file `AncientWarfareNpcFood.cfg`, category Default) | 350 | Ticks per food point for foods without an explicit entry |

That is 27 named options (26 in the main file plus `food_multiplier`). Dynamic options on top: `AncientWarfareNpcFood.cfg` category `01_food_settings` has one tick value per item id (seeded with apple 3000, mushroom_stew 4500, rotten_flesh 0, poisonous_potato 0, spider_eye 0; 0 or less rejects the food); `AncientWarfareNpcFactionStandings.cfg` has `<faction>.starting_faction_standing` for each of the 44 factions (read in `getPlayerDefaultStanding`, only for factions whose `standing_can_change` is true). Hard-coded and not configurable: `npcDefaultUpkeepWithdraw = 6000`, baton range 120 (`ItemCommandBaton.range`, TODO to make it configurable), `WorkOrder.MAX_SIZE = 8`.

### Sounds

`init/AWNPCSounds.java` declares 112 `SoundEvent` static fields (verified by grep) (attack, hurt, death per faction or creature) and a name-to-event map. Owned by the entity slice, listed here because it is in `init/`.

### Recipes and other data

| Id | Class | What it is | Porting note |
|---|---|---|---|
| `upkeep_order_copy`, `routing_order_copy`, `combat_order_copy`, `work_order_copy` | `crafting/AWNpcCrafting.OrderCopyingRecipe` (custom `IRecipe`) | Exactly 2 stacks of the same order item and nothing else. Output = copy of the second item carrying the first's NBT, count 2. There is no trade-order copy recipe. | Custom recipe type that copies the data component. |
| `registry/npc/factions.json` | `FactionRegistry.FactionParser` | 44 factions with defaults and overrides | See Registries. |
| `registry/npc/faction_npc_defaults.json` | `NpcDefaultsRegistry.FactionNpcDefaultsParser` | Per-faction by per-subtype NPC stats | See Registries. |
| `registry/npc/owned_npc_defaults.json` | `NpcDefaultsRegistry.OwnedNpcDefaultsParser` | Player-owned NPC stats | See Registries. |
| `registry/npc/target_lists.json` | `TargetRegistry.TargetListParser` | Named entity-id lists (`hostile` 687, `friendly` 91) | See Registries. |
| `registry/npc/faction_trade_lists.json` | `FactionTradeListRegistry.Parser` | 130 named trade lists, 1,536 trades, 2 faction overrides | See Registries. |
| `registry/compat/ebwizardry/entity_spawn_nbt.json` | core compat loader (other slice) | NBT keys to keep for `EntityWizard` (`trades`, `element`, `skin`, `spells`) and `EntityEvilWizard` (`element`, `skin`, `spells`) when spawned by structures | 1.12 class names; EBW only. |
| `trades/trades.cfg` | no reader found in `src/main/java` | 57-line legacy trade file (stick for apple etc.) | Dead data; drop. |
| `loot_tables/entities/*.json` (19 files) | vanilla loot tables | `siege_engineer.json` uses condition `ancientwarfarenpc:rides_vehicle` with values ballista, cannon, catapult, hwacha, trebuchet | See loot property. |

### Saved data and capabilities

| Name | Class | What it stores | Porting note |
|---|---|---|---|
| FactionData | `gamedata/FactionData` (`WorldSavedData`) | Map of player name to `PlayerFactionEntry` (standing per faction); tag `entryList`. Sent to the player on login by `PacketFactionUpdate` (keys `factionInit`, `factionUpdate`). | Keyed by player name, not UUID: re-key to UUID. |
| TeamData | `gamedata/TeamData` (`WorldSavedData`) | Teams (name, member names, per-faction standings) and per-player "leaving teams" with expiry (5 Minecraft days = 120,000 ticks) | Persistence bug: `writeToNBT` calls `NBTHelper.mapToCompoundList(...)` and discards the returned list, so `playerLeavingTeams` is never written. It is read on load but is always empty after a restart. |
| Team | `gamedata/Team` | One team: members, `FactionEntry` standings, averaging maths when a member joins | |
| `NpcAIDebugMode` | core `WorldData` | Debug flag | |
| Item handler on tile | `TileTownHall.getCapability` | Exposes an `ItemStackHandler(27)` | Item handler capability on the block entity. |
| `NpcEquipmentHandler` | `inventory/NpcEquipmentHandler` (`IItemHandlerModifiable`, 8 slots) | View of NPC equipment: 0 mainhand, 1 offhand, 2-5 armor, 6 `ORDER_SLOT`, 7 `UPKEEP_SLOT`. Slots 2-7 hold 1 item; slot 7 only accepts `ItemUpkeepOrder`; slot 6 accepts `npc.isValidOrdersStack`; armor slots check `isValidArmor` | Plain handler wrapper passed to menu slots. |

### Event handlers

This slice has no stand-alone event handler classes except: `init/AWNPCItems`, `init/AWNPCBlocks`, `init/AWNPCSounds`, `crafting/AWNpcCrafting` (all `@Mod.EventBusSubscriber`, registry events), `compat/ebwizardry/FactionAllyDesignation` (`LivingAttackEvent`), `datafixes/FactionEntityFixer` (`MissingMappings<EntityEntry>`, ignores old `bandit.*` ids). In `AncientWarfareNPC`: `worldLoaded` (`WorldEvent.Load`, reads the debug flag) and `onConfigChanged`. `FactionTracker` and `EventHandler` are in other slices.

### Key bindings

None defined here. Order items use `InputHandler.ALT_ITEM_USE_1` (and 2 and 3 for the trade order) from core through `IItemKeyInterface.onKeyAction` / `onKeyActionClient`.

### Renderers and models

No renderer classes. Client registration only: `ItemBaseNPC.registerClient` calls `ModelLoaderHelper.registerItem(this, "npc")`; `ItemNpcSpawner.registerClient` uses a custom mesh definition choosing the model variant by NPC type and subtype (`AWNPCEntities.NpcDeclaration.getItemModelVariant`); `BlockBaseNPC.registerClient` maps all states to one model; shields add a `blocking` property override. Models live under `assets/ancientwarfare/models/npc`.

### AI tasks

None defined here. This slice supplies their data: `NpcAIPlayerOwnedWork` reads `WorkOrder`, `NpcAIPlayerOwnedCourier` reads `RoutingOrder`, `NpcAIPlayerOwnedPatrol` reads `CombatOrder`, `NpcAIPlayerOwnedTrader` reads `TradeOrder`, `NpcPlayerOwned` reads `UpkeepOrder`. The EBWizardry compat adds `EntityAIAttackSpellImproved`.

## How it works

### Order items and the order data model

Each order class (`WorkOrder`, `UpkeepOrder`, `CombatOrder`, `RoutingOrder`, `TradeOrder`) follows one pattern: a static `getXOrder(ItemStack)` builds a fresh object from `stack.getTagCompound().getCompoundTag("orders")` when the stack item is the matching `ItemXOrder`, and `write(ItemStack)` stores `serializeNBT()` back under `"orders"`. Nothing is cached; the GUI, the item's key action and the AI each deserialise on demand. `WorkOrder`, `CombatOrder` and `RoutingOrder` extend core `util/OrderingList<T>` (a list with move up and down).

An NPC holds one order stack in `NpcBase.ordersStack` (slot 6, `ORDER_SLOT`) and an upkeep order in slot 7 (`UPKEEP_SLOT`). Both are saved in the NPC's NBT under `ordersStack` and `upkeepStack` (`entity/NpcBase.java` around lines 94, 1062-1108). When a slot changes, `onOrdersInventoryChanged` runs and the AI re-reads. `isValidOrdersStack` is overridden per NPC class (the trader accepts `ItemTradeOrder`; worker, courier and combat accept their own).

Player workflow: right click opens the GUI; the ALT item-use key adds the block being looked at (`BlockTools.getBlockClickedOn` or `RayTraceUtils.getPlayerTarget`, range 5). `ItemOrders.getPositionsForRender` returns the positions the client highlights while the item is held.

NBT layouts (each becomes a data component):

| Order | Layout under stack tag `orders` |
|---|---|
| Work | `entryList`: list of `{pos: long (BlockPos.toLong), dim: int, length: int}`; `priorityType`: int (ordinal of `SITE_NEED`, `ROUTE`, `TIMED`); `nightShift`: bool. Max 8 entries. Default priority `ROUTE`. |
| Upkeep | Written only if a point is set: `upkeepPosition`: long, `dim`: int, `side`: byte (EnumFacing ordinal), `upkeepAmount`: int (default 6000). Accepts only inventories (`InventoryTools.isInventory`); with `allow_upkeep_any_inventory` false only `BlockTownHall`. |
| Combat | `dim`: int; `pointList`: list of longs (omitted when empty). A point added in another dimension clears the list first. Sneak + ALT clears. Highlighted one block above. |
| Routing | `entryList`: list of route points `{type: int (RouteType ordinal), position: long, blockSide: byte, ignoreDamage: bool, ignoreTag: bool, filterList: list of {ItemStack tag with count 1, slot: int}, filterCounts: int[12]}`. 12 filter slots; real counts live in `filterCounts` (added by data fixer v6). |
| Trade | `tradeList`: `{tradeList: [{inputItems: [ItemStack + slot], outputItems: [ItemStack + slot]}]}` (9 slots each); `tradeRoute`: `{route: [{pos: long, delay: int, upkeep: bool}]}` (new points default delay 1200 ticks); `restockEntry`: `{withdrawPoint: long, withdrawSide: byte, depositPoint: long, depositSide: byte, depositList: [{item: ItemStack, type: int}], withdrawList: [{item, type}]}`. |

Other stateful items: spawner `{npcType: string, npcSubtype: string, faction: string (optional), npcStoredData: compound written by NpcBase.writeAdditionalItemData}`; baton `{mode: string (clear, attack, move, setHome, clearHome, setUpkeep, clearUpkeep), entityList: {entryList: [{uuid}]}}`; coin `{metal: string}`. `ItemCommandBaton.addInformation` writes a default `mode` tag from the tooltip call, a client-side mutation of the stack that must not be ported.

How AI consumes orders:
- Work: `NpcAIPlayerOwnedWork` loads the `WorkOrder` once, walks to `entries[workIndex]`, works when within `IWorker.getWorkRangeSq()`, and after `npc_work_ticks` ticks calls `IWorkSite.addEnergyFromWorker` and gives XP. The next index comes from `WorkPriorityType.getNextWorkIndex`: `ROUTE` cycles; `SITE_NEED` picks the first entry whose tile is an `IWorkSite` the worker can work and that `hasWork()`; `TIMED` moves on after `WorkEntry.workLength`. `nightShift` is read by `NpcWorker` (around line 125).
- Routing: `NpcAIPlayerOwnedCourier` calls `RoutingOrder.handleRouteAction(point, npc.backpackInventory, target)`. The 12 route types: `FILL_TARGET_TO`, `FILL_COURIER_TO`, `DEPOSIT_ALL_OF`, `WITHDRAW_ALL_OF`, `DEPOSIT_ALL_EXCEPT`, `WITHDRAW_ALL_EXCEPT`, `DEPOSIT_RATIO` (1/filter count), `WITHDRAW_RATIO`, `DEPOSIT_EXACT`, `WITHDRAW_EXACT`, `FILL_MINIMUM`, `TAKE_MINIMUM`. It returns stacks moved, which the AI turns into time spent at the point. Moves use core `InventoryTools` on `IItemHandler`.
- Combat: `NpcAIPlayerOwnedPatrol` walks the point list.
- Trade: `NpcAIPlayerOwnedTrader` is a state machine (shelter at night or rain, upkeep, restock, patrol) over route points with delay counters; it withdraws and deposits through `POTradeRestockData`.
- Upkeep: `NpcPlayerOwned` returns upkeep position, side, dimension and amount from the upkeep stack (lines 226-246); `NpcAIPlayerOwnedGetFood` fetches food there.

### Command baton

Right clicking an `NpcBase` the player may command (`hasCommandPermissions`) toggles its UUID in `entityList` (stale UUIDs pruned by `validateEntities`). Mode changes by scroll wheel while sneaking (`IScrollableItem`). The ALT key runs on the client (`onKeyActionClient`): it raytraces up to 120 blocks and sends `PacketNpcCommand` with a `NpcCommand.CommandType` (CLEAR_COMMAND, ATTACK, ATTACK_AREA, MOVE, GUARD, SET_HOME, CLEAR_HOME, SET_UPKEEP, CLEAR_UPKEEP). `getCommandedEntities` resolves UUIDs on the server with `WorldServer.getEntityFromUuid` and on the client by scanning `loadedEntityList`. Sneak right-click has a `//TODO openGUI`.

### Trading

Two systems share the `Trade` base class (9 input and 9 output stacks, `performTrade`, `isValid`, NBT):

1. Player-owned trader (`NpcTrader`, `POTrade`, `POTradeList`). Trades live in the `TradeOrder` on the NPC. `POTrade` keeps compacted input and output lists; `isAvailable(storage)` requires the stock to hold all outputs and have room for inputs. Stock is the backpack the trader holds (`AWCoreItems.BACKPACK` item handler found with `EntityTools.getHandHoldingItem`). On a trade, inputs move from the player into the backpack and outputs from the backpack to the player, with leftovers dropped at the player. The trader patrols a `POTradeRoute` (points with delay and a "refill upkeep here" flag) and restocks through `POTradeRestockData`: withdraw entries (`POTradeWithdrawEntry`: `ALL_OF`, `FILL_TO`) and deposit entries (`POTradeDepositEntry`: `ALL_OF`, `QUANTITY`, `DEPOSIT_EXCESS`). Only one customer at a time (`startTrade` / `closeTrade`, released in `ContainerNpcPlayerOwnedTrade.onContainerClosed`).
2. Faction trader (`NpcFactionTrader`, `FactionTrade`, `FactionTradeList`). No storage. Each trade has `maxAvailable`, `currentAvailable` and `refillFrequency` (default 6000 ticks; 0 = instant restock; negative = never). `FactionTrade.updateTrade(worldTime)` refills lazily when the view GUI opens, and a purchase decrements stock and sets the next refill time. The list is saved in the NPC under `tradeList` (`NpcFactionTrader` lines 105-112) and ticked in the entity's update. Faction standing change on a trade (`StandingChanges.TRADE`) is applied by the faction code elsewhere.

Currency is only the `coin` item (4 metals) used as an ordinary item in trade stacks; there is no balance or exchange rate logic. Placing 8 coins creates the structure-module coin-stack block.

Template save and load: a faction trader's setup GUI loads a template by name from `FactionTradeListRegistry.getDefaults()` merged with `getFactionDefaults(faction)` (faction wins), and saves back by rewriting the whole `registry/npc/faction_trade_lists.json` in the config folder (`AWCoreStatics.configPathForFiles + "registry/"`). That file overrides the bundled copy on next load because `RegistryLoader.load` reads the override folder first.

### Registries (`npc/registry`)

All parsers are registered in `AncientWarfareNPC.preInit` through core `RegistryLoader.registerParser`. `RegistryLoader` first loads config-dir `registry/` overrides, then walks `assets/<modid>/registry/**/*.json` in the jar, selects the parser by file base name, and honours a `"load_after": [names]` array (both `factions.json` and `faction_npc_defaults.json` load after `target_lists`).

| Registry class | JSON | What it loads | Hard-coded parts |
|---|---|---|---|
| `TargetRegistry` | `target_lists.json` | Map of list name to entity registry ids (`hostile` 687, `friendly` 91). Helper `parseTargets(json)` resolves `{lists, include, exclude}` and is used by factions and NPC defaults. | none |
| `FactionRegistry`, `FactionDefinition` | `factions.json` | `defaults` (player_default_standing, `standing_settings` {player_default_standing, standing_can_change, standing_changes {kill, trade}}, `hostile_towards_factions` name to bool, `targets`) and a `factions` array of 44 entries each with `name`, hex `color`, optional overrides and `themed_blocks` (block id to JSON with `color`, parsed by `JsonToNBT` and stamped `customData` = faction name) | `EMPTY_FACTION` fallback; a faction is never hostile to itself. |
| `NpcDefaultsRegistry.FactionNpcDefaultsParser` | `faction_npc_defaults.json` | `defaults` (attributes, `enabled`, `experience_drop`, `can_swim`, `can_break_doors`, `additional_attributes` {`burns_in_sun`, `horse_entity`}, `loot_table`, `height` {min,max} or number, `thinness`) with `npc_subtypes` of 20 subtypes (soldier, soldier.elite, archer, archer.elite, cavalry, mounted_archer, leader, leader.elite, siege_engineer, priest, spellcaster, spellcaster.1 to .5, bard, trader, civilian.female, civilian.male) and `factions` (44 keys) with per-faction overrides and own subtypes. `spells` string for subtypes containing "spellcaster" (TODO in code to move into compat). | Equipment slot names `work` and `upkeep` map to `NpcBase.ORDER_SLOT` / `UPKEEP_SLOT`; missing subtypes are filled from `AWNPCEntities.getNpcMap()` keys starting with `faction.` (code dependency on entity registration); item ids resolved by core `RegistryTools.getItem`. |
| `NpcDefaultsRegistry.OwnedNpcDefaultsParser` | `owned_npc_defaults.json` (1 KB) | `defaults` (targets list `hostile`, maxHealth 20, movementSpeed 0.325, followRange 60, attackDamage 1, experience_drop 0, can_swim, can_break_doors) and `npc_subtypes` (combat maxHealth 30; worker, courier, trader, priest, bard, siege_engineer only set `can_swim`) | Subtypes without an entry get the overall default. |
| `FactionTradeListRegistry` | `faction_trade_lists.json` (1.05 MB) | `defaults`: array of `{name, trades: [{input: [stack], output: [stack], refill_frequency, max_trades}]}`; `faction_defaults`: faction to list array (only `nogg` and `buffloka`, 1 list each). 130 default lists, 1,536 trades; one list has an empty-string name and one has no trades. Stack JSON `{name, data, count}` via core `JsonHelper.getItemStacks` / `ItemTools.serializeToJson`. | `DEFAULT_REGISTRY_LOCATION = "npc/faction_trade_lists.json"` for save-back. |
| `FactionTradeListTemplate`, `FactionTradeTemplate` | data classes | Template to `FactionTradeList` / `FactionTrade` and back | |
| `NpcDefault`, `OwnedNpcDefault`, `FactionNpcDefault`, `FactionSpellcasterDefault` | immutable value classes | `applyAttributes(npc)` sets attribute base values by name (and health when `generic.maxHealth`), `applyPathSettings`, `applyEquipment`. Faction variant adds loot table, random height range, thinness, additional attributes, `enabled`, and for spellcasters a `spells` string | Attribute keys are 1.12 names (`generic.maxHealth`, `generic.movementSpeed`, `generic.followRange`, `generic.attackDamage`). |
| `StandingChanges` | constants | `kill`, `trade` | |

Data quirk: `factions.json` defaults list hostility toward `lizardmen` but the faction is named `lizardman`. Treat it as a data bug when porting.

`AWNPCItems.addFactionBlocks()` runs in `postInit`. For every faction it adds a flag definition to the structure module's `PROTECTION_FLAG` and `DECORATIVE_FLAG` blocks, and for every `themed_blocks` entry it finds the block and, if its item is `ItemBlockColored`, calls `addCustomItemTag` with the NBT stamped `unlocalizedNamePart=faction`. This is how faction data creates creative-tab content in the Structures module.

### Town Hall

`TileTownHall` ticks on the server. Every `town_hall_ticks` (100) `broadcast()` finds `NpcPlayerOwned` entities in a box of radius `range` horizontally and range/2 vertically and, for those the owner can command, calls `handleTownHallBroadcast(pos)` so the NPC remembers the hall. When an owned NPC dies the hall stores a `NpcDeathEntry` (spawner item stack with the NPC's data, type, name, damage type, position); the GUI death list lets the owner resurrect or clear. Saved NBT keys: `inventory` (ItemStackHandler, 27), `deathNotices`, `name` (default "Unnamed", TODO says unclear use), `range`, `alarmActive`, `isActive`, `isNeglected`, `oldOwner`, plus `TileOwned` owner fields. `alarmActive` is driven by redstone and read by the NPC alarm AI. `isNeglected`, `oldOwner` and a `ForgeChunkManager.Ticket` field are saved or declared but nothing in this slice uses them. Breaking the block drops the inventory (`IBlockBreakHandler`).

### Faction standing and teams

`FactionData` stores an integer standing per (player name, faction). `TeamData` adds team-wide standings; team names are `ResourceLocation`s supplied by core `TeamViewerRegistry`, which can wrap other mods' teams. `TeamData.adjustStanding` changes the player's standing, adjusts each of the player's teams and spreads the delta divided by member count to the other members. `getWorstStandingFor` returns the minimum over current teams and recently-left teams (expire after 5 Minecraft days, but see the persistence bug above). Kill and trade deltas come from `FactionDefinition.StandingSettings.getStandingChange`. Packets: `PacketFactionUpdate` (player standing), `PacketTeamMembershipUpdate`, `PacketTeamStandingsUpdate`, `PacketTeamStandingUpdate` (broadcast to all players).

### Loot-table entity property

`entity/faction/attributes/EntityVehicleProperty` is an `EntityProperty` named `ancientwarfarenpc:rides_vehicle`, registered in `AncientWarfareNPC.preInit` with `EntityPropertyManager.registerProperty`. `testProperty` is true when the entity is an `IVehicleUser` riding a vehicle whose `vehicleType.getConfigName()` contains the JSON string. Only `loot_tables/entities/siege_engineer.json` uses it, once per vehicle type, so siege engineers drop vehicle-specific loot.

### Data fixers

Registered in core `core/datafixes/AWDataFixes` (not in this slice). Each is a 1.12 `IFixableData` with a fix version: `FactionEntityFixer` (v3, renames 104 old ids such as `ancientwarfarenpc:bandit.archer` to faction entities plus `factionName`; also ignores the old ids in `MissingMappings`), `FactionSpawnerItemFixer` (v3, same for spawner items), `FactionExpansionEntityFixer` (v5, rename map of 5 entries) and `FactionExpansionItemFixer` (v5, no map of its own; it works from the `faction` tag), `RoutingOrderFilterCountsFixer` (v6, builds `filterCounts`), `FoodBundleDataFixer` (v7, converts core `ancientwarfare:component` damage 100 to `food_bundle`), `NpcSkinFixer` (v9, converts `customTex` to `skinSettings`). All upgrade old saves.

### Electroblob's Wizardry compat

`EBWizardryCompat` (mod id `ebwizardry`, registered with `CompatLoader`) registers `FactionAllyDesignation` on the Forge bus inside a `Supplier<Runnable>` lambda to avoid class loading. `FactionAllyDesignation.onLivingAttackEvent` cancels `IElementalDamage` from an `NpcFaction` against another `NpcFaction` of the same faction (no magical friendly fire). The rest of the integration is outside `compat/` but in this module: `NpcFactionSpellcasterWizardry` (entity), `ContainerNpcFactionSpellcasterWizardry`, `GuiNpcFactionSpellcasterWizardry` (GUI 62, registered only when the mod is loaded), the `spells` key in `faction_npc_defaults.json`, and `EntityAIAttackSpellImproved<T extends EntityLiving & ISpellCaster>` (332 lines, adapted from EBW's spell AI: strafing, continuous spells, `SpellCastEvent.Pre/Tick/Post`, cooldowns, `PacketNPCCastSpell` sent to clients within 128 blocks). It uses EBW internals: `Spell`, `Spells.none`, `SpellModifiers`, `ISpellCaster`, `WizardryPacketHandler`, `PacketNPCCastSpell`, `SpellCastEvent`, `Source.NPC`, `IElementalDamage` (STATS per-package `electroblob/wizardry/*` counts from the NPC module sum to 13: spell 4, util 3, entity 2, registry 2, event 1, packet 1; the earlier figure of 17 is not reproduced). Depth: one event handler, one AI class, one entity with container and GUI; moderate.

### Other compat

- `EpicSiegeCompat` (`epicsiegemod`): loads `funwayguy.epicsiegemod.ai.ESM_EntityAINearestAttackableTarget` by name and registers it as an extra "hostile AI" check on the NPC `EventHandler`.
- `TwilightForestCompat` (`twilightforest`): reads `TFConfig.dimension.dimensionID` by reflection and registers a daytime rule for it (`getWorldTime() % 24000 < 12000`) through `WorldTools.registerDimensionDaytimeLogic`, so NPCs know when to sleep there.

## Dependencies

From other Ancient Warfare modules:

| Module | Classes | Used for |
|---|---|---|
| core | `ContainerBase`, `ContainerEntityBase`, `ContainerTileBase`, `GuiContainerBase`, `Button`, `NumberInput`, `Checkbox`, `IWidgetSelection`, `NetworkHandler`, `PacketBase`, `ItemBase`, `IItemKeyInterface`, `IScrollableItem`, `InputHandler`, `OrderingList`, `InventoryTools`, `ItemTools`, `NBTHelper`, `NBTBuilder`, `BlockTools`, `WorldTools`, `RayTraceUtils`, `EntityTools`, `JsonHelper`, `RegistryLoader`, `IRegistryDataParser`, `RegistryTools`, `ModConfiguration`, `AWCoreStatics`, `AWGameData`, `WorldData`, `TeamViewerRegistry`, `ItemBlockOwned`, `TileOwned`, `IInteractableTile`, `IBlockBreakHandler`, `BlockBase`, `AWCoreItems.BACKPACK`, `SongPlayData`, `InjectionTools`, `CompatLoader`, `ICompat`, `AWDataFixes` | UI framework, networking, registries, utilities. STATS: 116 NPC classes reference core; top targets `ContainerBase` (91), `Button` (77), `SongPlayData` (44). |
| npc (other slices) | `NpcBase`, `NpcPlayerOwned`, `NpcFaction`, `NpcTrader`, `NpcFactionTrader`, `NpcBard`, `NpcFactionBard`, `AWNPCEntities` (`NpcDeclaration`), `NpcCommand`, `FactionTracker`, `FactionEntry`, `PlayerFactionEntry`, `NpcSkinSettings`, `EventHandler`, the owned AI classes | Order consumption, spawner, standings. |
| structure | `AWStructureBlocks` (`COIN_STACK_*`, `PROTECTION_FLAG`, `DECORATIVE_FLAG`), `BlockFlag`, `ItemBlockColored`, `AWStructureSounds.COIN_STACK_INTERACT`, `PacketStructureEntry`, `StructureEntry`, `StructureMap`, `SpawnerHelper`, `IRespawnData`, `CapabilityRespawnData` | Coin blocks, faction flag blocks, themed blocks. |
| vehicle | `VehicleBase`, `IVehicleType`, `IVehicleUser` | `EntityVehicleProperty`. |
| automation | `IWorkSite` | `WorkOrder.WorkPriorityType.SITE_NEED`, work AI (`orders/WorkOrder.java`, `ai/owned/NpcAIPlayerOwnedWork.java`, `ai/owned/NpcAIPlayerOwnedFindWorksite.java`, `ai/owned/NpcAIPlayerOwnedWorkRandom.java`, `entity/NpcWorker.java`). The jar STATS table has no `npc -> automation` row, so this comes from source only. |

Third-party mods: Electroblob's Wizardry (`ebwizardry`, optional, deep class dependency), Epic Siege Mod (`epicsiegemod`, optional, reflection by class name), Twilight Forest (`twilightforest`, optional, reflection on config fields). Trade lists and target lists also name items and entities of many other mods by registry id only (for example `rustic:grape_stem`, `abyssalcraft:*`); that is data, not code.

Minecraft and Forge APIs that no longer exist in 1.21.1: `RegistryEvent.Register`, `@ObjectHolder`, `GameRegistry.registerTileEntity`, `OreDictionary`, item metadata and damage as variants, `Item.ToolMaterial`, `IRecipe` and `InventoryCrafting`, `WorldSavedData`, item NBT (`getTagCompound`, `setTagInfo`), `EntityPropertyManager` and `EntityProperty`, `ForgeChunkManager.Ticket`, `IFixableData` and the data-fixer registry, `CommandBase`, `Configuration` and `Property`, `ModelLoader` and variant model locations, `addPropertyOverride`, the Forge GUI handler (`openGui`), `SidedProxy`, `EntityEquipmentSlot`, `CreativeTabs.getSubItems`, `EntityAIBase`, `EnumFacing`, `getUnlocalizedName`.

## Porting notes (1.21.1 NeoForge)

Ranked hardest first. Effort per the task scale. Class names that I am not sure of are marked (unverified).

1. **Order items, GUIs and menus.** What: 5 order types with nested structures (routing filter stacks, trade lists, restock lists) and the NBT-packet GUI protocol (14 containers, 18 screens, about 4,200 lines of screen and container code) on a core widget framework (`GuiContainerBase`, `Button`, `NumberInput`) that belongs to the core slice. Why hard: every order is deserialised on demand from item NBT and re-serialised by the GUI; `sendDataToServer(NBT)` must become custom payloads with stream codecs; item-held menus need a new way to open (no x,y,z argument). Approach: define each order as an immutable record with a `Codec` and `StreamCodec`, register one `DataComponentType` each (`work_order`, `upkeep_order`, `combat_order`, `routing_order`, `trade_order`); menus read and write the component on the held item through a small "item-bound menu" base class. The screens are a rewrite on `Screen` / `AbstractContainerScreen`; reuse only if the core widget library is ported first. Effort: XL (mostly GUI).
2. **Data-driven registries and JSON content.** What: 5 JSON files (about 1.3 MB; trade lists 1.05 MB with 1,536 trades) loaded by the custom `RegistryLoader` with `load_after`, config-dir override and a writable template save. Why hard: the content embeds 1.12 identifiers: item metadata (`"data": 0`), attribute names (`generic.maxHealth`), Java class names (`net.minecraft.entity.passive.EntityHorse`), hundreds of modded entity and item ids, and `JsonToNBT`. The four-level layering (global, subtype, faction, faction subtype) in `faction_npc_defaults.json` must be preserved. Approach: write a one-time offline converter that remaps metadata items to flattened ids (needs a table), converts attribute names, drops unknown modded ids and emits datapack-style JSON; load it with a resource-reload listener (`SimpleJsonResourceReloadListener`, unverified exact package) so it reloads with `/reload` and datapacks can override it. Runtime saving of faction trade templates needs a different design (write a datapack file, or store in saved data). Effort: L.
3. **Trading system.** What: two trade types, restock logic, route points, stock in a held backpack, faction trade lists persisted per NPC, template save and load. Why hard: item moves use core `InventoryTools` over `IItemHandler` and need rewriting against NeoForge item-handler capabilities (exact 21.1.x registration unverified); "stock is a backpack in the NPC's hand" needs an item-attached inventory design (a data component holding stacks exposed as a handler). `NonNullList<ItemStack>` in trades needs stack codecs. Approach: keep the data model, switch persistence to codecs, wrap the backpack component as an `IItemHandler`. Effort: L.
4. **Items with custom behaviour.** 58 items; real logic in `ItemNpcSpawner`, `ItemCommandBaton`, `ItemScythe` (area harvest), the 21 extended-reach weapons plus club and ice spear, 16 shields, coin and bard. Why: no metadata variants, `Tier` and the attribute-modifier component replace `getAttributeModifiers`, reach attack needs `PacketExtendedReachAttack` (other slice) or a reach attribute, shield blocking uses a different hook in 1.21.1, OreDict repair becomes a repair tag. Approach: separate registrations for metadata variants; generate the weapon and shield families in a loop as the current code does; spawner and baton as data-component items. Effort: L.
5. **NPC defaults, attributes and equipment application.** `NpcDefault.applyAttributes` uses attribute name strings; 1.21.1 attributes are registry holders with namespaced ids (exact ids unverified) so the converter remaps names and code looks them up in the registry. `additional_attributes.horse_entity` holds a class name; replace with an entity type id. Effort: M.
6. **Saved data, teams and standings.** `FactionData` and `TeamData` keyed by player name with hand-written sync packets and the persistence bug in `TeamData.writeToNBT`. Approach: new `SavedData` with codecs (factory signature unverified), keyed by UUID, fix leaving-team persistence, keep averaging rules; re-sync with payloads. Effort: M.
7. **Config.** 27 options plus two dynamic config files; use `ModConfigSpec` (unverified exact class) for the fixed ones; food values as a data map or item-tag-based data and starting standings folded into `factions.json`. Effort: M.
8. **Town Hall block and block entity.** One block, one block entity (27 slots, death list of spawner stacks, name, range, flags). Death notices embed whole spawner stacks, so they depend on the spawner component. Effort: S to M.
9. **Entity-property loot condition.** Replace with a custom loot condition type (registry `LOOT_CONDITION_TYPE`, unverified) or entity sub-predicate (unverified) with a `MapCodec`; only one loot table uses it. Effort: S.
10. **Commands.** 3 commands, about 200 lines; Brigadier rewrite. Effort: S.
11. **EBWizardry compat.** I do not know of a 1.21.1 NeoForge build of the mod (unverified); the integration (EBW spell AI, GUI, entity, event) cannot be ported as it is. Alternatives: drop it, or implement a mod-neutral spellcaster NPC with its own small spell list and keep `FactionAllyDesignation` as a generic "same faction takes no damage from its own NPCs" rule. Effort: S to drop, L to rebuild.
12. **Data fixers.** Not portable and not needed for a fresh 1.21.1 world; drop. Only the old-id rename tables would matter for a one-off save converter. Effort: S (delete).
13. **Epic Siege and Twilight Forest compat.** Reflection on foreign class names; check whether those mods exist for 1.21.1 (unverified); the Twilight Forest daytime rule could become a dimension-type check. Effort: S each.
14. **Order copy recipes.** One custom recipe class taking the order item and copying the data component. Effort: S.

Cannot be ported as it is:
- Item metadata variants (bard instrument) and string NBT variants (coin `metal`): become separate items.
- `trades/trades.cfg` (never read) and `TileRecruitingStation` (stub): delete.
- `openGui(player, id, x, y, z)` and the NBT-key packet protocol: replace by menus and payloads.
- The baton's tooltip that writes NBT: no equivalent; set defaults via the component default.
- 1.12 data fixers.
- EBWizardry integration (see above).

## Candidates for capttrackerlib

Generic code that another unrelated mod could reuse:
- Item-bound menu base class plus codec helpers for "held item stores a structure, menu edits it" (any programmable-tool mod). Write fresh.
- `OrderingList<T>` equivalent (list with move up/down and remove): trivial and generic, but it belongs to the core slice.
- Layered JSON defaults resolver pattern (global, subtype, faction override merging of attribute maps) from `NpcDefaultsRegistry`: reusable for any mod with data-driven mob stats; the concrete maps stay AW specific.
- `TargetRegistry` include/exclude/lists resolver: generic named id-set composition from JSON for any mod with target lists.
- Item logistics over `IItemHandler` behind `RoutingOrder` (fill to, deposit exact, fill minimum, ratio, all-except): reusable by any logistics mod; extract from `RoutingOrder.RoutePoint` and `InventoryTools`.
- Team standing aggregator (`Team`, `TeamData` maths): generic reputation with team averaging, but tied to `TeamViewerRegistry`.

Looks generic but should stay inside Ancient Warfare: the faction JSON schema and themed-block link to structures, `NpcEquipmentHandler` and its slot layout, `AWNPCStatics`, `FactionAllyDesignation`, the trade classes (tied to the AW backpack and the `FactionTrade` refill rules), the town hall death-notice list, and the order item classes themselves.

## Open questions

1. Should old worlds be migrated? All data fixers target 1.12 saves and 1.21.1 cannot read them. Decide: fresh start only, or an offline converter (affects standings, trade lists, spawner stacks).
2. `ancientwarfarenpc:rides_vehicle` only matters if the Vehicles module and siege engineers are ported together; the owner decides module port order.
3. Is Electroblob's Wizardry available for the target? The code cannot answer that. If not, decide between dropping spellcasters or writing a generic spell system.
4. Trade lists reference items by 1.12 id and metadata. Who owns the remap table, and what happens to entries naming unported mods (rustic, grimoireofgaia and others)? I did not measure how many of the 1,536 trades survive.
5. `lizardmen` vs `lizardman` in `factions.json`: confirm it is a bug and fix during conversion.
6. `trades/trades.cfg` has no reader in `src/main/java`; I grepped only that tree, so confirm it is dead.
7. `TileTownHall` fields `isNeglected`, `oldOwner`, `name` and the chunk-loading `ticket` look unfinished; decide drop or finish.
8. `NpcEquipmentHandler` validates armor with `EntityEquipmentSlot.values()[slot]`; I did not check that slots 2-5 line up with real armor slots in `NpcBase.getItemStackFromSlot` (I read it only for the ORDER and UPKEEP slots).
9. The baton range is hard-coded at 120 with a TODO; decide whether to add a config option.
10. Not examined in detail: widget code inside `gui/` and the restock part of `NpcAIPlayerOwnedTrader` beyond its state machine.

## Verification

Checked by a second pass against the source (`SRC` = `upstream/AncientWarfare2/src/main/java/net/shadowmage/ancientwarfare/npc`) and the resource tree, not by re-reading this report.

What was checked:
- Items: `init/AWNPCItems.java` registration calls. Re-counted 58 standalone items plus 1 ItemBlock (59 registrations). Shields 16 (5 tiered, 9 cosmetic wood, 2 cosmetic iron), batons 5, orders 5, extended reach 21 (4 families x 5 materials + obsidian spear), spawner, coin, food bundle, macuahuitl, sickle, pitchfork, 2 scythes, club, ice spear. Matches the table.
- Blocks and tile entities: `init/AWNPCBlocks.java`. One block (`town_hall`) and one tile (`town_hall_tile`, the only `registerTile` call). `TileRecruitingStation` is not registered. Matches.
- Containers and screens: `AncientWarfareNPC.java` lines 93-111 register 13 containers plus the wizardry container when `ebwizardry` is loaded (14). `proxy/NpcClientProxy.java` lines 87-105 register 13 screens plus the wizardry screen (14). Matches. GUI ids checked against `core/network/NetworkHandler.java` (4, 6, 34-37, 39, 41-44, 47, 49, 62). All match. The 4 helper screens are not re-counted.
- Packets: `AncientWarfareNPC.java` lines 118-124 and `NetworkHandler` constants. Ids 12, 13, 27, 29, 31, 32, 33. The report had no id for `PacketStructureEntry`; it is 29 and has been added.
- Commands: `command/` has 3 commands (`awfaction`, `awteams`, `awnpcdebug`). `CommandFaction` requires level 2 (line 21-23). `CommandTeams` has `list`, `describe`, `player` subcommands. `CommandDebugAI` level 2.
- Config: `config/AWNPCStatics.java` registers 26 keys in the main file plus `food_multiplier` in the food file (27). Defaults match the table. Dynamic food and faction-standing keys exist as described.
- Registries: `factions.json` has 44 factions. `faction_npc_defaults.json` has 20 subtypes under `defaults.npc_subtypes` and 44 faction keys. `faction_trade_lists.json` has 130 default lists, 1,536 trades, one list with an empty name, one list with no trades, and `faction_defaults` for `nogg` and `buffloka` only. `target_lists.json` has `hostile` 687 and `friendly` 91. `owned_npc_defaults.json` exists. All match.
- Lizardman mismatch: `factions.json` uses `lizardman` as the faction name and `lizardmen` as the hostility key in several factions. Confirmed.
- Loot: `loot_tables/entities` has 20 files (not 19). `rides_vehicle` appears 5 times, all in `siege_engineer.json`. Confirmed.
- Recipes: `crafting/AWNpcCrafting.java` registers 4 order-copy recipes (no trade-order recipe). Output count is 2, NBT copied from the second stack. Confirmed.
- Sounds: `init/AWNPCSounds.java` has 112 static `SoundEvent` fields (not 111).
- Team persistence bug: `gamedata/TeamData.java` line 131 calls `NBTHelper.mapToCompoundList` for `playerLeavingTeams` and never calls `setTag` on the result, so leaving teams are not written. Confirmed. (The return type of `mapToCompoundList` is not checked in core.)
- Faction data keyed by player name: `gamedata/FactionData.java` line 20 (`HashMap<String, PlayerFactionEntry>`). Confirmed.
- Order NBT defaults: `WorkOrder.MAX_SIZE = 8`, `UpkeepOrder` default `upkeepAmount = 6000`, `ItemCommandBaton` range 120 with a TODO, `TileTownHall` inventory `ItemStackHandler(27)`, `TileTownHall` name "Unnamed" with a TODO. Confirmed.
- Routing: `RoutingOrder.RouteType` has 12 values. `WorkOrder.WorkPriorityType` has SITE_NEED, ROUTE, TIMED. Confirmed.
- Coin: `ItemCoin.CoinMetal` has ANCIENT, GOLD, SILVER, COPPER. Confirmed.
- Spawner stack size 16 (`ItemNpcSpawner`). Bard instrument names lute, flute, harp, drum (`ItemBardInstrument`). Confirmed.
- Compat: `EpicSiegeCompat` loads `funwayguy.epicsiegemod.ai.ESM_EntityAINearestAttackableTarget` by name. `TwilightForestCompat` reads `TFConfig` and `TFConfig$Dimension.dimensionID` by reflection. The `ebwizardry` folder has 5 Java files plus `package-info.java` (6). Confirmed.
- Data fixers: `FactionEntityFixer` has 104 `ancientwarfarenpc:` rename entries. Confirmed. `AWDataFixes` (core) registers the npc fixers. Confirmed.
- Scope totals: the 16 listed directories hold 129 files and 12,512 lines (wc -l). The report said 124 files and about 10,500 lines. Corrected.
- Dependencies: `IWorkSite` is used by npc work AI and `WorkOrder`. `IVehicleUser` is used by npc vehicle AI. The STATS file has no `npc -> automation` row even though the source uses `IWorkSite`. Noted in the dependency table.
- Modern API names: `DataComponentType`, `StreamCodec`, `Codec`, `MapCodec`, `Screen`, `AbstractContainerScreen`, `IItemHandler`, `IItemHandlerModifiable`, `Tier`, `SavedData`, `ModConfigSpec`, `BlockEntity` exist in 1.21.1 NeoForge as far as I know, but I did not verify exact packages or signatures in this pass; the existing "(unverified)" marks stay. `LOOT_CONDITION_TYPE`, `SimpleJsonResourceReloadListener`, the reach attribute id and the `onBlockStartBreak` hook remain marked unverified. Removed-in-1.21 names listed in the report (`RegistryEvent`, `GameRegistry`, `OreDictionary`, `Item.ToolMaterial`, `IRecipe`, `WorldSavedData`, `EnumFacing`, `EntityEquipmentSlot`, `CommandBase`, `SidedProxy`, `CreativeTabs.getSubItems`, `EntityAIBase`) are correct as removed or renamed.

What was changed:
- Scope totals: 124 files / about 10,500 lines corrected to 129 files / 12,512 lines. Directory list kept.
- Loot table count: 19 corrected to 20 (scope paragraph).
- Sound fields: 111 corrected to 112 (Sounds section).
- Packet ids: added 29 to `PacketStructureEntry`.
- Data fixers: the 5-entry rename map belongs to `FactionExpansionEntityFixer` only; `FactionExpansionItemFixer` has no map of its own.
- EBWizardry reference count: 17 corrected to the STATS per-package sum of 13 (the 17 figure is not reproduced).
- Dependencies: automation row now cites the source files and notes the missing STATS row.

What could not be settled:
- The exact sneak condition of the baton's `//TODO openGUI` (line 142) was not read in detail.
- `CommandBase`-level permission details for `awfaction` are confirmed as 2; the `awnpcdebug` subcommand behaviour and `Registered in serverStart` were not checked.
- The helper GUI screens (`GuiSkinSelection`, `GuiTradeTemplateSave`, `GuiTradeTemplateSelect`, `GuiTownHallDeathList`) were not re-counted line by line.
- The creative tab id `tabs.npc`, the baton's `NpcCommand.CommandType` values (9), the town hall field list, the Rock hardness value and the `NpcEquipmentHandler` armor slot order were not checked.
- Whether `mapToCompoundList` returns a list that the caller should have stored (core source not read in this pass).
- The 1.21.1 API names and packages marked above were not checked against the NeoForge 21.1.x sources (no network access in this task).
- 1,536 trades: which entries reference unported mods was not measured.
