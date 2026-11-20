# NPCs: module entry, entity classes, factions, skins, events, network
> Part of the Ancient Warfare 2 (1.12.2-2.7.0.1038) legacy inventory. Source paths are relative to src/main/java/net/shadowmage/ancientwarfare/ in upstream 1.12.x @ 78178170b.

## Scope

Packages covered (all under `npc/`), counted with a script over SRC:

| Package | Files | Lines |
|---|---|---|
| `npc/` (AncientWarfareNPC, AWNPCTab, package-info) | 3 | 236 |
| `npc/entity` | 13 | 2,891 |
| `npc/entity/faction` | 21 | 1,946 |
| `npc/entity/faction/attributes` | 9 | 225 |
| `npc/entity/vehicle` | 7 | 383 |
| `npc/faction` | 4 | 176 |
| `npc/skin` | 3 | 418 |
| `npc/client` | 2 | 45 |
| `npc/event` | 2 | 211 |
| `npc/init` | 5 | 807 |
| `npc/proxy` | 3 | 255 |
| `npc/network` | 7 | 344 |
| `npc/npc_command` | 2 | 169 |
| **Total** | **81** | **8,106** |

`entity/faction/attributes` was not named in the task but sits under `entity/faction`, so it is included. The data files that drive faction NPCs (`RES/assets/ancientwarfare/registry/npc/*.json`, `skin_pack/`) were also analysed. Parsers for those files live in `npc/registry` (another slice) and are only referenced here.

This part of the mod defines every NPC entity (7 player-owned types, 15 faction types), the shared `NpcBase` that holds ownership, leveling, upkeep, sleeping, skin and sync logic, the faction standing facade (`FactionTracker`), the skin system, the module entry point (registries, containers, packets, parsers, commands), the module-wide event handler (AI injection into foreign mobs, spawn blocking and chest/dig protection in structures) and the baton command and team/faction packets.

## What it adds

### Entities

Registered in `init/AWNPCEntities.java` through `AWEntityRegistry.registerEntity` (core) -> `EntityRegistry.registerModEntity(ResourceLocation(modid, name), class, name, id, mod, trackingRange, updateFrequency, sendsVelocityUpdates)`. All 22 use the same settings: tracking range 120, update frequency 3, velocity updates true (`AWNPCEntities.NpcDeclaration`, lines for `trackingRange()` etc.). Network ids are a running counter `nextID++` in registration order (0..21). Registry names are the constants in `core/entity/AWEntityRegistry.java:13-35`. Full registry name is `ancientwarfarenpc:<name>`.

| Registry name | Class | What it is | Porting note |
|---|---|---|---|
| `aw_npc_combat` | `entity/NpcCombat` | Player-owned soldier/archer/commander/medic/engineer. Subtype derived from the held item (`getSubtypeFromEquipment`) | One EntityType; subtype is a derived value, not stored |
| `aw_npc_worker` | `entity/NpcWorker` | Player-owned farmer/lumberjack/miner/researcher/craftsman. Subtype from held tool (`getWorkTypeFromEquipment`) | Same; tool classes ("axe", "pickaxe", "hammer", "quill") need replacing with item tags or data components |
| `aw_npc_courier` | `entity/NpcCourier` | Moves items between routing-order points; weapon slot holds a backpack item exposing `ITEM_HANDLER_CAPABILITY` | Capability on item stack -> item component/`IItemHandler` item capability (unverified) |
| `aw_npc_trader` | `entity/NpcTrader` | Player-owned shop NPC (trade list from orders stack) | |
| `aw_npc_priest` | `entity/NpcPriest` | Heals nearby friendlies (AI `NpcAIPlayerOwnedPriest`) | |
| `aw_npc_bard` | `entity/NpcBard` | Plays songs (`ISinger`, `SongPlayData`) | |
| `aw_npc_siege_engineer` | `entity/vehicle/NpcSiegeEngineer` | Mans AW vehicles (`IVehicleUser`) | Depends on vehicle module |
| `faction.archer` | `entity/faction/NpcFactionArcher` | Ranged soldier | One EntityType per class, faction is instance data |
| `faction.soldier` | `NpcFactionSoldier` | Melee soldier | |
| `faction.leader` (constant `NPC_FACTION_COMMANDER`) | `NpcFactionLeader` | Melee leader; `NpcAIFactionCommander` directs nearby faction NPCs | |
| `faction.priest` | `NpcFactionPriest` | Throws witch-style potions (`NoFriendlyFirePotion`) | Potion logic copied from `EntityWitch` |
| `faction.trader` | `NpcFactionTrader` | Trader with `FactionTradeList`; despawns when list empties | |
| `faction.cavalry` | `NpcFactionMountedSoldier` | Soldier spawned on a mount | Mount handling (riding AI) |
| `faction.mounted_archer` | `NpcFactionMountedArcher` | Archer with mount | |
| `faction.civilian.male` | `NpcFactionCivilianMale` | Passive, panics (`NpcAIFactionPanic`) | |
| `faction.civilian.female` | `NpcFactionCivilianFemale` | Same, female flag | |
| `faction.archer.elite` | `NpcFactionArcherElite` | Subclass of archer, only `getNpcType()` differs (`archer.elite`) | Could be data (a different default stat block), not a class |
| `faction.soldier.elite` | `NpcFactionSoldierElite` | same pattern | |
| `faction.leader.elite` | `NpcFactionLeaderElite` | same pattern | |
| `faction.bard` | `NpcFactionBard` | Singing bard, has alt GUI | |
| `faction.siege_engineer` | `NpcFactionSiegeEngineer` | Mans vehicles | Depends on vehicle module |
| `faction.spellcaster` | `NpcFactionSpellcaster` (no EBWizardry) or `NpcFactionSpellcasterWizardry` (EBWizardry loaded) | Mage. Same registry name, two classes chosen at runtime by `Loader.isModLoaded("ebwizardry")` (`AWNPCEntities.registerSpellcasterFactionNpc`) | Wizardry variant cannot port (mod is 1.12-only); keep the plain class |

Not registered as an entity: `entity/NoFriendlyFirePotion` (extends `EntityPotion`). It is only given a renderer (`proxy/NpcClientProxy.java:114`) and spawned directly; vanilla 1.12 tracks any `EntityPotion` subclass by fallback. In 1.21.1 it will need a real EntityType.

Reconciliation with STATS: 7 + 15 = 22 registered NPC entity types. STATS `npc/entity/faction` has 30 classes (incl. nested/anonymous), `npc/entity` 13, `npc/entity/vehicle` 7: these match the 21+13+7 source files once nested classes and package-infos are counted. 19 `NpcFaction*` source files minus abstract `NpcFaction`, `NpcFactionCivilian`, `NpcFactionMounted` leaves 16 concrete classes (the interface `IHorseMountedNpc` is a separate file); one (`NpcFactionSpellcaster`) shares a registry name with `NpcFactionSpellcasterWizardry`, so 15 registrations. STATS lists 28 `net/minecraft/entity/INpc` implementors (module-wide; includes `NpcBase` subclasses and vehicle-module users).

Faction counts (data, not code): there are 44 factions in `RES/.../registry/npc/factions.json` and `faction_npc_defaults.json` has an entry for each. A faction therefore has at most 15 NPC types (the 15 faction entity types, with spellcaster counted once). By my reading of the parser order (global default -> faction -> faction subtype `enabled`), roughly 487 of the 660 (44 x 15) type slots are enabled; per faction from 3 (`rakshasa`) to 15 (`demon` and about 15 others). A recount from the JSON (enabled flags only, no parser run) gives the same 487, and the minimum (3, `rakshasa`) and maximum (15) per faction match. The defaults file also defines 6 spellcaster presets (`spellcaster`, `spellcaster.1` to `.5`) that the Wizardry GUI applies as NPC "subtypes" (`gui/GuiNpcFactionSpellcasterWizardry.java:71`); they are not separate entities.

Subtype lists per npc type, from `AWNPCEntities`:

| NPC type | Subtypes (spawner variants) | Starter weapon (post-init `loadNpcSubtypeEquipment`) |
|---|---|---|
| combat | commander, soldier, archer, medic, engineer | iron command baton, iron sword, bow, iron axe, iron hammer |
| worker | miner, farmer, lumberjack, researcher, craftsman | iron pickaxe, iron hoe, iron axe, iron quill, iron hammer |
| courier, trader, priest, bard, siege_engineer | none | none |

### Sounds

119 `SoundEvent`s registered in `init/AWNPCSounds.java` `register(RegistryEvent.Register<SoundEvent>)` (`registry.register(createSoundEvent(name))`, namespace `ancientwarfarenpc`). They are: `bard.tune1` to `bard.tune6` (6), `teleport_in`, `teleport_out` (2), and 111 creature sounds. The 111 are named `<creature>_attack|hurt|death|ambient`. Public fields exist for 111 (`@ObjectHolder`), the 8 others are only reachable through the string map `npcSoundEvents`. Creature prefixes (36): barbarian, beast, brigand, coven_dryad, coven_familiar, coven_puppet, coven_satyr, coven_scarecrow, coven_witch, dwarf, elf, ent, gargoyle, giant, gnome, gremlin, hobbit, human, human_female, ishtari_anubite, ishtari_mummy, ishtari_pharoah, klown, kobold, kong_ape, lizardman, malice, monster, norska, orc, orc_uruk, pirate, skeleton, vampire, vampire_boss, vampire_bride, zombie (zombie_*: ambient/attack/death/hurt). Not every creature has all four variants (for example `coven_dryad` has only death and hurt, `barbarian_death` is an alias to `HUMAN_DEATH` via `addAlternativeSoundReferences`).

How a faction NPC picks a sound: the faction data field `additional_attributes.entity_sound` (for example `"monster"`, `"vampire_bride"`) is stored in the synced `SOUND` string; `NpcFaction.getHurtSound/getDeathSound/playAttackSound` build `<sound>_hurt|_death|_attack` and look the string up in `AWNPCSounds.npcSoundEvents`. `"none"` falls back to vanilla generic hurt/death. Sound files and `sounds.json` are in `RES/assets/ancientwarfare/sounds` (not analysed in detail).

Porting note: register through `ModRegistrar` (SoundEvent registry). The string-keyed lookup needs a `Map<String, Holder<SoundEvent>>` or registry lookup by `ResourceLocation`. `sounds.json` can be reused almost as is.

### Blocks, tile entities (registration only; classes belong to another slice)

| Registry name | Class | What it is | Porting note |
|---|---|---|---|
| `ancientwarfarenpc:town_hall` | `block/BlockTownHall` (+ `ItemBlockOwned`) | Town hall block (registered in `init/AWNPCBlocks`) | Owner stored in tile; see npc-items-orders-trade |
| tile id `ancientwarfarenpc:town_hall_tile` | `tile/TileTownHall` | Town hall tile entity | BlockEntity |

### Items (registration list only; classes belong to npc-items-orders-trade)

58 items registered in `init/AWNPCItems.register` (59 with the `ItemBlockOwned` for `town_hall`, registered in `init/AWNPCBlocks`):

| Registry names | Class | Count |
|---|---|---|
| `wooden/stone/iron/gold/diamond_command_baton` | `ItemCommandBaton(name, ToolMaterial)` | 5 |
| `bard_instrument` | `ItemBardInstrument` | 1 |
| `wooden/stone/iron/gold/diamond_shield`, `shield_tribal_1`, `_2`, `shield_round_1` to `_6`, `shield_witchbane_1`, `_2`, `shield_buffloka` (max damage 336/506/759/64/1138, tribal/round/buffloka 336, witchbane 759) | `ItemShield` | 16 |
| `work_order`, `upkeep_order`, `combat_order`, `routing_order`, `trade_order`, `npc_spawner` | `ItemWorkOrder`, `ItemUpkeepOrder`, `ItemCombatOrder`, `ItemRoutingOrder`, `ItemTradeOrder`, `ItemNpcSpawner` | 6 |
| `coin` | `ItemCoin` | 1 |
| `macuahuitl`, `sickle`, `pitchfork`, `scythe`, `death_scythe` (wither on hit), `giant_club`, `ice_spear` | `ItemMacuahuitl`, `ItemSickle`, `ItemPitchfork`, `ItemScythe`, `ItemClub`, `ItemIceSpear` | 7 |
| `food_bundle` (also OreDict `foodBundle`) | `ItemFoodBundle` | 1 |
| `<wooden|stone|iron|golden|diamond>_` x `spear`, `halberd`, `lance`, `cleaver` | `ItemExtendedReachWeapon` (reach 4.2/4.5/5.5/3.0) | 20 |
| `obsidian_spear` | `ItemExtendedReachWeapon` | 1 |
| ItemBlock for `town_hall` | `ItemBlockOwned` | (1 more, not counted above) |

Porting note: material-parameterised classes (`Item.ToolMaterial`) must move to 1.21.1 `Tier` plus data components. `OreDictionary` -> item tags. `ItemShield` is a custom shield (not vanilla `ItemShield` in 1.12); decide whether to build on vanilla `ShieldItem` (unverified fit). `addFactionBlocks()` (called post-init) injects per-faction colour tags into structure-module `BlockFlag` and `ItemBlockColored` instances from `factions.json` `themed_blocks`.

### Network packets (client/server messages)

All extend core `PacketBase`; registered in `AncientWarfareNPC.preInit` with `PacketBase.registerPacketType(id, class, factory)`. Ids are in `core/network/NetworkHandler.java`.

| Id (name) | Class | Direction | Payload | Porting note |
|---|---|---|---|---|
| 12 `PACKET_NPC_COMMAND` | `network/PacketNpcCommand` | C->S | command type ordinal (int), blockTarget flag, BlockPos long or entity id int | Custom payload record + StreamCodec; send ordinal as enum codec |
| 13 `PACKET_FACTION_UPDATE` | `network/PacketFactionUpdate` | S->C | compressed NBT of a player's faction standings (built in `gamedata/FactionData`) | Payload with CompoundTag codec |
| 27 `PACKET_EXTENDED_REACH_ATTACK` | `network/PacketExtendedReachAttack` | C->S | entity id; server checks held `IExtendedReachWeapon` reach and calls `attackTargetEntityWithCurrentItem` | Needs `getReach` check; 1.21.1 has entity interaction range attribute (unverified), could replace |
| 31 `PACKET_TEAM_MEMBERSHIP_UPDATE` | `network/PacketTeamMembershipUpdate` | S->C | team ResourceLocation, player name, action ADD/REMOVE short | |
| 32 `PACKET_TEAM_STANDINGS_UPDATE` | `network/PacketTeamStandingsUpdate` | S->C | team name + `FactionEntry` NBT | |
| 33 `PACKET_TEAM_STANDING_UPDATE` | `network/PacketTeamStandingUpdate` | S->C | team name, faction name, int standing | |

Also registered here but defined in the structure module: 29 `PACKET_STRUCTURE_ENTRY` (`PacketStructureEntry`), registered in the NPC mod's preInit (odd: it belongs to structures; fix during port). The entity-targeted sync packet used by `NpcBase` (`PacketEntity`, core) is described under How it works.

Receivers: `PacketTeam*Update` call `TeamData` on `player.world` which on the client side mirrors server data (`gamedata/TeamData`, other slice). Reconciliation: the 7 files in `npc/network` are 6 packets plus package-info.

### Commands

Slash commands registered in `AncientWarfareNPC.serverStart`: `CommandFaction`, `CommandTeams`, `CommandDebugAI` (classes in `npc/command`, other slice). The baton command set in this slice is `npc_command/NpcCommand.CommandType`: MOVE, ATTACK, ATTACK_AREA, GUARD, SET_HOME, SET_UPKEEP, CLEAR_HOME, CLEAR_UPKEEP, CLEAR_COMMAND, NONE (persistent ones: ATTACK, GUARD, ATTACK_AREA).

### Containers and GUIs (registration only)

`AncientWarfareNPC.preInit` registers 13 containers (`NetworkHandler.registerContainer(id, class)`), all in the other slice: GUI ids `GUI_NPC_INVENTORY` 4, `GUI_NPC_TRADE_ORDER` 6, `GUI_NPC_WORK_ORDER` 34, `UPKEEP_ORDER` 35, `COMBAT_ORDER` 36, `ROUTING_ORDER` 37, `FACTION_TRADE_SETUP` 39, `TOWN_HALL` 41, `FACTION_TRADE_VIEW` 42, `BARD` 43, `CREATIVE` 44, `PLAYER_OWNED_TRADE` 47, `FACTION_BARD` 49, and conditionally `FACTION_SPELLCASTER_WIZARDRY` 62 (only with EBWizardry). `NpcClientProxy.preInit` registers the matching `Gui*` classes. NPC GUIs are opened with `NetworkHandler.INSTANCE.openGui(player, id, entityId, 0, 0)` (entity id passed in the first int), see `NpcBase.openGUI`, `NpcTrader.openAltGui`.

### Saved data and capabilities

None defined in this slice. `FactionTracker` reads `FactionData` and `TeamData` (both in `npc/gamedata`, via `AWGameData.INSTANCE.getData(world, ...)`). `NpcFaction.despawnEntity` uses `CapabilityRespawnData.RESPAWN_DATA_CAPABILITY` (structure module) to turn a despawning structure-spawned NPC back into a spawner. `AncientWarfareNPC.worldLoaded` reads the per-world `WorldData` key `NpcAIDebugMode`.

### Event handlers

| Handler | Events | What it does | Porting note |
|---|---|---|---|
| `event/EventHandler` (singleton, registered on `MinecraftForge.EVENT_BUS` in preInit) | `EntityJoinWorldEvent` (LOWEST) | (1) `injectAi`: for any non-NPC `EntityCreature` that already has an `EntityAINearestAttackableTarget` aimed at `EntityPlayer`, adds a target goal for `NpcBase` at the same priority, filtered by `entityShouldTarget` (faction targets list or owned-NPC defaults). `registerAdditionalHostileAICheck` lets compat mods add predicates (used by Epic Siege / Twilight Forest compat). (2) `preventHostileSpawnsInStructures`: cancels join of any `IMob` inside a structure or town bounding box (`StructureMap`, `TownMap`) unless tagged `noSpawnPrevention`. | Goal injection into foreign mobs is fragile in 1.21.1 (goal selectors are `GoalSelector` with different access); use `EntityJoinLevelEvent`; spawn blocking could use `FinalizeSpawnEvent` (unverified) |
| same | `PlayerInteractEvent.RightClickBlock` | Non-creative players cannot open generated-loot chests inside a structure if the protection flag applies, or if a hostile faction NPC is inside the structure (that NPC gets Glowing 200 ticks); status message translation keys `gui.ancientwarfarenpc.no_chest_access*` | Event class renamed; lock check uses tile NBT `generatedInventory` and `ISpecialLootContainer` |
| same | `PlayerEvent.BreakSpeed` | Digging in a claimed structure is slowed to 1 percent for players the protection flag protects against, except mob spawners | `PlayerEvent.BreakSpeed` exists under that name (unverified) |
| `faction/FactionTracker` (registered post-init) | `PlayerLoggedInEvent` | Sends `FactionData.onPlayerLogin` and `TeamData.checkAndUpdatePlayerTeamMemberships` | |
| `AncientWarfareNPC` | `OnConfigChangedEvent`, `WorldEvent.Load` | Saves config; loads debug-AI flag | Config UI will be replaced |
| `proxy/NpcClientProxy` | `ModelRegistryEvent` | Runs `IClientRegister` callbacks | Model baking changed |
| `init/AWNPCEntities`, `AWNPCSounds`, `AWNPCItems`, `AWNPCBlocks` | `RegistryEvent.Register<...>` | Registrations | -> `ModRegistrar` / `DeferredRegister` |

### Renderers and models (registration only)

`NpcClientProxy.preInit`: `RenderNpcBase` for `NpcBase`, `RenderNpcFaction` for `NpcFaction` (both in `npc/render`, other slice), `RenderPotion` for `NoFriendlyFirePotion`. `RenderWorkLines`, `RenderCommandOverlay` and `IExtendedReachWeapon.MouseClickHandler` are registered on the event bus. `client/NPCItemColors` registers item colour handlers: spawner tint index 1 = faction colour, index 2 = fixed red `0xEF5757` if the faction exists; coin tint = metal colour.

## How it works

### Module entry and registration (`AncientWarfareNPC`, `init/`, `proxy/`)

`@Mod(modid="ancientwarfarenpc", dependencies="required-after:ancientwarfare")`. Lifecycle:

- preInit: builds `AWNPCStatics` (config file `AncientWarfareNpc`), registers the `rides_vehicle` loot-table entity property (`EntityVehicleProperty.Serializer`, which tests that a rider NPC sits in a vehicle whose config name contains a string), calls `proxy.preInit()`, registers event handlers, 13/14 containers, 7 packets, 3 compat objects (`EpicSiegeCompat`, `TwilightForestCompat`, `EBWizardryCompat`) and 5 JSON registry parsers (`FactionRegistry.FactionParser`, `TargetRegistry.TargetListParser`, `NpcDefaultsRegistry.FactionNpcDefaultsParser`, `OwnedNpcDefaultsParser`, `FactionTradeListRegistry.Parser`).
- Registry events then fire `AWNPCEntities.register` (entities), `AWNPCSounds.register`, `AWNPCItems.register`, `AWNPCBlocks.register*`.
- init: client proxy registers item colours.
- postInit: `proxy.loadSkins()` (client scans the skin pack), `AWNPCEntities.loadNpcSubtypeEquipment()` (needs items initialised), registers `FactionTracker`, saves config, `AWNPCItems.addFactionBlocks()`.
- serverStart: 3 slash commands.

`AWNPCEntities` also acts as the NPC factory: `npcMap` (npcType -> `NpcDeclaration`) is used by `ItemNpcSpawner` and structure spawning via `createNpc(world, npcType, subtype, faction)`. `NpcDeclaration` creates the entity reflectively (`getConstructor(World.class)`); `NpcFactionDeclaration` uses the `(World, String factionName)` constructor and sets defaults there. For player-owned NPCs the subtype only sets main-hand equipment. The npcType key for faction entries equals the registry name (`faction.archer`), while `NpcFaction.getNpcType()` returns the short form (`archer`); the short form is used for the defaults lookup. `AWEntityRegistry.registerEntity` skips a registry name that already exists (a TODO there notes multiple faction registrations were once a problem).

### Faction system: hard-coded entity classes, data-driven content

Entity classes are hard-coded (15 types, table above); factions themselves are data. A faction is an entry in `registry/npc/factions.json` (`name`, `color` hex, `standing_settings` {`player_default_standing`, `standing_can_change`, `standing_changes` {`kill`, `trade`}}, `hostile_towards_factions` {faction: bool}, optional `targets` {`lists`, `include`, `exclude`}, optional `themed_blocks`) and an entry in `faction_npc_defaults.json` (per subtype: attributes using 1.12 names `generic.maxHealth`, `movementSpeed`, `followRange`, `attackDamage`, `armor`; `experience_drop`, `can_swim`, `can_break_doors`, `equipment` {mainhand, offhand, ...}, `loot_table`, `enabled`, `height` {min,max}, `thinness`, `additional_attributes`, `spells`). Global defaults in both files are merged with faction and subtype overrides by `NpcDefaultsRegistry`/`FactionRegistry` (other slice). Files are loaded by core `RegistryLoader` from the jar `assets/ancientwarfare/registry` and, first, from a config folder `<config>/registry` (`AWCoreStatics.configPathForFiles + "registry"`), and `load_after` orders dependent files. Custom faction variants are therefore created by dropping JSON files in that config folder: a new faction needs its entry in both files (otherwise `NpcDefaultsRegistry.fillRemainingFactionDefaults` falls back to defaults), optional skins under `<config>/npc/skins/` mapped by `skin_pack.meta` lines `<faction>.<npcType>=<file>.png`, and sounds only from the fixed 111 creature sounds. There is no per-NPC custom faction in code; one `NpcFaction` holds a `factionName` string only. The structure module's `FactionExpansion*` datafixers exist for "faction expansion" content from older versions (other slice).

Per-faction data: 44 factions (empire, norska, sarkonid, xoltec, witchbane, nogg, buffloka, reiksgard, zimba, kong, orc, brigand, pirate, evil, good, undead, demon, coven, elf, barbarian, dwarf, shakayana, smingol, lizardman, mindflayer, zamurai, rakshasa, klown, ishtari, icelord, guild, wizardly, amazon, minossian, sealsker, vyncan, hobbit, monster, beast, ent, gnome, gremlin, vampire, giant). 22 have their own `targets` block, 7 have `themed_blocks`. Additional per-NPC attribute keys used in the data: `entity_sound` (97 occurrences in `faction_npc_defaults.json`), `burns_in_sun` (28), `undead` (20), `horse_entity` (14, a Java class name, default `net.minecraft.entity.passive.EntityHorse` as used by `NpcFactionMounted`), `can_swim` (6; a top-level key read by `NpcDefaultsRegistry`, not inside `additional_attributes`), plus `heal_per_try` (1 use) defined in `AdditionalAttributes` for priests. Counts were taken from the JSON with a script, which walks every nested key. `ClassAttribute.parseValue` does `Class.forName` on the string, so data refers to 1.12 class names.

The owned-NPC equivalent is `owned_npc_defaults.json`: every owned type gets maxHealth 20 (combat 30), speed 0.325, follow range 60, attack damage 1, target list `hostile`, can_swim true, can_break_doors true.

### NpcBase (1,459 lines)

`NpcBase extends EntityCreature implements IEntityAdditionalSpawnData, IOwnable, IEntityPacketHandler, IPathableEntity, INpc`. Navigation is replaced by `NpcNavigator` (ai package). `setPathPriority(DOOR_WOOD_CLOSED, 0)`.

Attributes: `applyEntityAttributes` registers `ATTACK_DAMAGE` and `ATTACK_SPEED` on the attribute map (the 1.12 mob map lacks them). Values come from data: owned NPCs call `NpcDefault.applyAttributes/applyPathSettings` in `NpcPlayerOwned`'s constructor; faction NPCs in `setFactionNameAndDefaults`/`applyFactionNpcSettings`. Overrides stored on the instance: `maxHealthOverride` (-1 = none), `attackDamage` (`attackDamageOverride`), `armorValue` (`armorValueOverride`), `hasCustomEquipment` (faction-only; set by structure scans to keep scanned gear). Attack damage per level: `base * (1 + level * npcLevelDamageMultiplier)` (`getLeveledAttack`, base from `NpcDefaultsRegistry`). Health per base level: `baseHealth + level` unless overridden. Natural regeneration: +1 HP every 200 ticks when upkeep is satisfied (`onUpdate`).

Equipment and inventory slots: vanilla `EntityEquipmentSlot` (indices equal ordinals) plus two NPC-only slots addressed by int: `ORDER_SLOT = 6` (`ordersStack`) and `UPKEEP_SLOT = 7` (`upkeepStack`). `getItemStackFromSlot(int)` / `setItemStackToSlot(int, stack)` are `final` overloads used by the container classes. Off-hand = shield (`getShieldStack`). Changing main hand calls `onWeaponInventoryChanged()` (subclasses swap melee/ranged AI), off-hand calls `onOffhandInventoryChanged()` which adds `NpcAIBlockWithShield` at priority 3 if the off-hand is a shield and the main hand is not a bow. `canDespawn()` is false for all NPCs.

Leveling: `NpcLevelingStats` (132 lines). Per full type (`npcType.subtype`, e.g. `worker.miner`) an `ExperienceEntry {xp, level}`, plus a generic base `xp`/`level`. XP needed for level n: `(n+3)^2`. Max level from `AWNPCStatics.maxNpcCombatLevel` (combat) or `maxNpcWorkerLevel` (everything else). Experience sources: `NpcBase.onKillEntity` adds `npcXpFromKill`; worker/AI code calls `addExperience`. Level-up recomputes max health and damage. NBT (`levelingStats` tag): `xp`, `level`, `entryList` [{`type`, `xp`, `level`}]. Worker effectiveness is `1 + level * 0.05` plus tool-material efficiency (`NpcWorker.getWorkEffectiveness`). Ranged inaccuracy shrinks with sqrt(base level) from 3.0 to 0 (`NpcCombat.attackEntityWithRangedAttack`).

Food and upkeep: `IKeepFood` (marker + upkeep position API) is implemented by `NpcPlayerOwned` only. `foodValueRemaining` ticks down by 1 per tick while awake (`NpcPlayerOwned.onLivingUpdate`), NBT `foodValue` (and `food` via base tags). Right-click with a food item (`AWNPCStatics.getFoodValue`) adds its value if below `getUpkeepAmount()`. `withdrawFood(IItemHandler)` pulls items from the upkeep block. Upkeep position: from the `UpkeepOrder` in the upkeep slot (position, block side, dimension, amount) or the auto position set by the nearest town hall broadcast (`upkeepAutoBlock`). Hungry NPCs idle (`NpcAIPlayerOwnedIdleWhenHungry`) and heal slower. Trader gating: non-owners can only trade when `getFoodRemaining() > 0`.

Ownership and teams: `Owner` (core, `core/owner/Owner`) holds UUID and name; `isOwner` = owner, same scoreboard team, or listed friend (`isOwnerOrSameTeamOrFriend`); `getTeam()` uses `world.getScoreboard().getPlayersTeam(owner name)` (faction NPCs return null). Command permission via `hasCommandPermissions` (faction NPCs always false). Hostility API is abstract: `isHostileTowards`, `canTarget`, `canBeAttackedBy`. Owned NPCs are hostile to non-team players/NPCs, to faction NPCs by faction standing, and to mobs in their `targets` list (`NpcDefaultsRegistry.getOwnedNpcDefault(...).isTarget` or `AIHelper.isAdditionalEntityToTarget`). `setAttackTarget`/`setRevengeTarget` refuse non-targetable entities and `setAttackTarget` pushes the target id to tracking clients (`PacketEntity` with `attackTarget`).

Commands and following: `tryCommand` (called from `processInteract`): if the player holds a baton the interaction is passed on; sneak-click toggles `followingPlayerName`; plain click opens the inventory GUI (owner or faction-creative only). Baton orders arrive as `NpcCommand.Command` through `NpcPlayerOwned.handlePlayerCommand`: ATTACK sets the attack target and clears the command; everything else is stored in `playerIssuedCommand` for `NpcAIPlayerOwnedFollowCommand` to act on; stored in NBT under `command`. `NpcSiegeEngineer` overrides it for ATTACK_AREA (a `BlockPosTarget`) and CLEAR_COMMAND.

Home and town hall: `home` (long BlockPos) + `homeRange` saved in NBT; if no home is set at save time it is set to the town hall or to the current position (`writeEntityToNBT`). `townHall` (long) and `upkeepPos` for owned NPCs; `handleTownHallBroadcast(pos)` chooses the closest hall and syncs alarm state (`isAlarmed`); `validateTownHallPosition` drops the hall if the tile is missing or the owner mismatches; dimension change clears both. On death an owned NPC notifies `TileTownHall.handleNpcDeath` (respawn queue handled in the tile, other slice). There is no respawn logic inside the entity itself. Repack: `repackEntity(player)` (blocked if `repackCreativeOnly` and not creative) converts the NPC back to a spawner item (`ItemNpcSpawner.getSpawnerItemForNpc` using `writeAdditionalItemData`), `getPickedResult` returns the same item.

Sleeping: beds are not vanilla sleep. `findBed()` scans +-6 blocks for an unoccupied `BlockBed` foot part and caches it; `lieDown(pos)` sets the bed OCCUPIED, stores bed pos/direction, shrinks the hitbox to 0.2x0.2 and moves the entity; `wakeUp()` restores size and tries bedside positions. Fields saved: `bedDirection`, `isSleeping`, `cachedBedPos`, `bedPos`, `foundBed`, `originalWidth`, `originalHeight`. `shouldSleep()` is "not daytime" (`WorldTools.isDaytimeInDimension`); workers can invert it with a night-shift work order. While sleeping, damage (including suffocation) is ignored, movement and pushing are disabled.

Synced data parameters (`EntityDataManager.createKey`): `AI_TASKS` (VARINT bitfield of running AI task flags, used by the renderer), `SHIELD_DISABLED_TICK` (VARINT), `BED_POS` (BLOCK_POS), `BED_DIRECTION` (BYTE), `IS_SLEEPING` (BOOLEAN), `SWINGING_ARMS` (BOOLEAN). Subclass additions: `NpcFaction.SOUND` (STRING, default `"none"`), `NpcFactionSpellcasterWizardry.HEAL_COOLDOWN` (VARINT, -1). 8 parameters total.

Spawn data payload (`IEntityAdditionalSpawnData`): `NpcBase.writeSpawnData` writes UUID msb, UUID lsb, `Owner.serializeToBuffer` (UUID+name), `NpcSkinSettings.serializeToBuffer` (skin type name, player name, random flag, npc type name, npc skin ResourceLocation string cut to 100 chars, isAlexModel). `NpcFaction` appends `factionName` (string), `height` and `width` floats. The client reads the UUID so that `getIDForSkin()` (UUID least significant bits) matches the server and the chosen random skin is the same.

NBT layout, entity save (`writeEntityToNBT`): vanilla mob data, `home`, `homeRange`, bed fields, `originalWidth/Height`, then the base tags: `ordersStack`, `upkeepStack` (item NBT), `levelingStats`, `maxHealth`, `health`, `name`, `food`, `attackDamageOverride`, `armorValueOverride`, `aiEnabled`, `donotpursue`, `hasCustomEquipment`, `skinSettings` {skinTypeSelected, playerName, random, npcTypeName, npcTypeSkin, isAlexModel}, `ownerName` + `ownerId`. Subclass tags: owned `foodValue`, `command`, `townHall`, `upkeepPos`; combat `patrolAI`; worker `workAI`, `workRandomAI`; courier `courierAI`; trader `tradeAI`; bard `tuneData`; siege engineer `targetPos`; faction `factionName`, `canDespawn`, `revengePlayers` [{playerName, time}], `height`, `width`; mounted `horseLives`; faction trader `tradeList`; faction bard `tuneData`; Wizardry `spells` (registry-name strings). Item/structure-scan form (`writeAdditionalItemData`): `equipment` list with `slotNum` plus the base tags (no AI state); the Wizardry class also writes `spells`. A `DataFixer` walker (`registerFixesNpc`) fixes the two stacks. Mount state is not saved here (vanilla riding).

Interaction handling: `processInteract` -> `tryCommand`. Overrides: `NpcPlayerOwned` accepts food first; `NpcTrader` (owner: command GUI; stranger: opens the player-owned trade GUI if the NPC is fed and not already trading); `NpcFactionTrader` opens `GUI_NPC_FACTION_TRADE_VIEW` for anyone; other faction NPCs only react for creative players. `NpcBase.getName` is `entity.ancientwarfarenpc.<fullType>.name` translated server-side (deprecated `I18n.translateToLocal`).

Combat details: `attackEntityAsMob` is a hand-copied vanilla `EntityMob` body with an optional offhand-shield attribute swap and calls `hitEntity` on the main-hand item (so extended-reach weapons with potion effects apply). `applyArmorCalculations` uses Forge `ISpecialArmor.ArmorProperties.applyArmor` over the armor list, or vanilla calculations if an armor override is set. `getTotalArmorValue` returns the override if >= 0. Shield mechanics are re-implemented: `blockUsingShield`, `disableShield` (80 tick cooldown, status 30), `damageShield`, a custom `updateActiveHand` that lets shields persist. `getMaxFallHeight`, `getBlockPathWeight` (avoid cactus/lava, prefer lit blocks), `getYOffset` -0.35 are overridden.

### Player-owned classes: what actually differs

All extend `NpcPlayerOwned` (health/damage from `owned_npc_defaults.json`, 100 percent equipment drop, hurt/death sounds `HUMAN_*` or `HUMAN_FEMALE_*` for female or Alex skin, subtype-driven `getNpcSubType`). Differences are almost entirely the AI task lists in each constructor (AI is in the npc-ai slice) and a few overrides:

| Class | Distinguishing behaviour |
|---|---|
| `NpcCombat` | Melee AI or bow AI chosen from the main-hand item; implements `IRangedAttackMob`; patrol AI persisted; distress response (`respondToDistress`); `shouldSleep` false, passive false, works in rain; combat order item validates orders; can pick up loot; `canEquipItem` only if hand empty or same item |
| `NpcWorker` | Work AIs (`NpcAIPlayerOwnedWork`, `...WorkRandom`, `...FindWorksite`); implements `IWorker` (`getWorkEffectiveness`, `getWorkRangeSq`, `canWorkAt`); subtype from tool; night-shift sleeping by `WorkOrder` |
| `NpcCourier` | Routing-order AI; weapon slot is a backpack (`backpackInventory` from item handler capability) |
| `NpcTrader` | Trade list from `TradeOrder` in orders slot; `shouldBeAtHome` at night or rain; never hostile; non-owners open trade GUI |
| `NpcPriest` | Priest AI only; orders slot unused |
| `NpcBard` | Sings from `SongPlayData` (`ISinger`); alt GUI `GUI_NPC_BARD` |
| `NpcSiegeEngineer` | Vehicle AIs (find, mount, dismount, aim, fire); `canBeAttackedBy` always true; follow range 120; `ITarget` (entity or block position) handling; continues riding only while fed |

### Faction NPC classes: what actually differs

All extend `NpcFaction` (stats from `faction_npc_defaults.json` per faction+type). The shared behaviours in `NpcFaction`:
- Variable size: height random in the faction's `height` range, width `= height/1.8 * 0.6 * thinness`; saved as `height`/`width`; sent in spawn data; `getRenderSizeModifier = height/1.8` used by the renderer.
- `canDespawn` false by default; `setCanDespawn()` used by spawners. If it despawns and carries a respawn-data capability it re-creates a spawner block (`SpawnerHelper.createSpawner`).
- Burning in sun if `burns_in_sun`; `undead` makes `getCreatureAttribute` UNDEAD; `isPotionApplicable` blocks poison for the faction string `lizardman|coven` (the check string seems to be a leftover: factions are named `lizardman` and `coven`, never combined, so it never matches; unverified intent) and for priests.
- Hostility to players: `revengePlayers` map (name -> expiry) or `FactionTracker.isHostileToPlayer` (standing below 0). Revenge: hit by player = 300 ticks of revenge (`HIT_REVENGE_TICKS = 6000/20`), kill = 6000 ticks for the killer and all same-faction NPCs within 50 blocks. Hostility between factions is `FactionRegistry.getFaction(a).isHostileTowards(b)`. Non-faction mobs by faction `targets`.
- On death by a player or a player's NPC: standing for that faction changes by the faction's `kill` value through `FactionTracker.adjustStandingFor`.
- Loot table from data; experience drop from data; equipment from data unless `hasCustomEquipment`; drop chances 0.085 or 1.0 by config `vanillaEquipmentDropRate`.
- Fall height scales with difficulty and max health.
- Only creative players may use the inventory GUI.

Per-type differences: Soldier, Leader (also `NpcAIFactionCommander`), Cavalry/Mounted (`NpcFactionMounted` adds a horse: `instantiateMountedEntity` via `horse_entity`, `NpcAIFactionRideHorse`, `horseLives` flag, mount removed on repack/despawn), Archer/Mounted archer (ranged, selector limits targets to 30 blocks from home, `RangeAttackHelper` with 1.0 inaccuracy), Priest (potion thrower with `NoFriendlyFirePotion`, poison-immune), Trader (trade list, ticks list, `noTradesDespawn`), Bard (songs, alt GUI), Civilian male/female (never hostile, panic AI), Siege engineer (vehicle AIs), Spellcaster (plain: stays home, no spells, or Wizardry: spells list, heal cooldown, ISpellCaster implementation, faction-coloured heal particles, alt GUI for spell selection). The three elite types differ from their base only by `getNpcType()` suffix `.elite` and therefore by the data block used.

### Faction standing (`faction/`)

`FactionEntry` (map faction -> int standing, NBT list `entryList` [{name, standing}]), `PlayerFactionEntry` (adds `playerName`). New entries are filled with `AncientWarfareNPC.statics.getPlayerDefaultStanding(name)` per faction (default -70 in the JSON, -1000 and fixed for `empire`). `setStandingFor` refuses changes where `standing_can_change` is false. `FactionTracker.INSTANCE`: effective standing is the minimum of the player's own standing (`FactionData`) and the worst standing among the player's teams (`TeamData.getWorstStandingFor`, with team join/leave cooldowns by world time). `adjustStandingFor` is routed through `TeamData.adjustStanding` which applies the change to player and team data. Standings are saved in `FactionData` / `TeamData` (`gamedata`, other slice) and synced by `PacketFactionUpdate` and the three team packets. `FactionData` NBT is gzip-compressed inside the packet.

### Skins (`skin/`)

Three sources, selected by `NpcSkinSettings.SkinType`: `DEFAULT` (skin pool keyed by `npc.getNpcFullType()`, e.g. `orc.soldier`, `worker.farmer`, picked by seeding `Random` with the UUID LSB, fallback texture `ancientwarfare:textures/entity/npc/npc_default.png`), `NPC_TYPE` (user chooses another type name, random or a specific skin), `PLAYER` (a player name; the server resolves the `GameProfile` through the profile cache and `MinecraftSessionService.fillProfileProperties` (`NpcCommonProxy.cacheProfile`) and sends it to tracking clients in a `PacketEntity` tag `skinSettings`/`profile`; the client loads the skin through `SkinManager.loadSkinFromCache/loadSkin` (`NpcClientProxy.getPlayerSkin`)). So there is a remote lookup, using Mojang's session service via the vanilla skin manager, only for the PLAYER type; there is no custom download code. `isAlexModel` chooses slim arms for player skins.

Skin pool loading (`NpcSkinManager.loadSkins`, client only via `proxy.loadSkins`): if `loadDefaultSkinPack` is true, reads the jar folder `assets/ancientwarfare/skin_pack` (3,606 PNG files, 21 MB uncompressed, plus `skin_pack.meta` with 2,694 `<type>=<file>.png` lines; skins not listed go to type `custom`), then the external config folder `<config>/npc/skins/` (same format). Each PNG is read with `ImageIO` and registered as a dynamic texture `ancientwarfare:skinpack/<name>` using core `TextureImageBased`. Mapping keys are full NPC type names such as `buffloka.soldier.elite` (22 for `orc.soldier` at most per key, up to about 20 variants).

NBT/packet details for settings are listed under NpcBase. `NpcSkinSettings.minimizeData()` strips fields not relevant to the selected type. A read bug risk: `deserializeFromBuffer` reads the npc-skin string with limit 60 in the non-NPC_TYPE branch while the writer allows 100 characters (harmless today because the skin path is cleared unless NPC_TYPE, unverified for structure-scan edge cases).

### Baton commands and `PacketNpcCommand`

`ItemCommandBaton` (items slice) maps key presses to `NpcCommand.handleCommandClient(type, rayTraceHit)` which sends `PacketNpcCommand` with the block position or entity id. The server calls `handleServerCommand`, which builds a `Command`, finds all NPCs bound to the baton (`ItemCommandBaton.getCommandedEntities`) and calls `NpcPlayerOwned.handlePlayerCommand` on each. `Command` NBT: `type` ordinal, `block`, `pos`, `idmsb/idlsb`, `entityid`. The code has a TODO to turn commands into class instances.

### Entity events and misc

- `EventHandler.injectAi` (see table above) is the way foreign mobs learn to attack NPCs; it requires an existing `EntityAINearestAttackableTarget` for players and copies its priority.
- `NpcFaction.despawnEntity`, `NpcFactionMounted.despawnEntity` clean up mounts and respawn data.
- Vehicle riders: `NpcBase.setPath/getRidingVehicle` forward navigation to a `VehicleBase`'s navigator (vehicle module).
- `RangeAttackHelper` fires `EntityTippedArrow` with a distance-based speed factor and honours Power, Punch, Flame of the main-hand item.
- `NpcFactionPriest` throws `NoFriendlyFirePotion` (harming/slowness/poison/weakness by situation) which only affects entities hostile to the thrower.

## Dependencies

Other Ancient Warfare modules (from STATS cross-module counts and source):
- core: `ContainerBase`, `Button`/GUI widgets (116 classes in npc reference core, mostly GUI), `NetworkHandler`, `PacketBase`, `PacketEntity`, `IEntityPacketHandler`, `AWEntityRegistry`, `Owner`/`IOwnable`, `AWGameData`, `WorldData`, `RegistryLoader`, `CompatLoader`, `InventoryTools`, `EntityTools`, `NBTHelper`, `FileUtils`, `TextureImageBased`, `AWCoreStatics`, `AWCoreItems` (iron hammer, quill), `InjectionTools`, `IClientRegister`.
- structure (5 classes): `AWStructureBlocks` (flags), `BlockFlag`, `StructureMap`, `TownMap`, `TileProtectionFlag`, `SpawnerHelper`, `CapabilityRespawnData`/`IRespawnData`, `PacketStructureEntry`, `ISpecialLootContainer`. Structure module depends back on npc (`NpcFaction`, `ItemCoin`, `NpcBase` for spawner scanning, 10 classes).
- vehicle (10 classes): `VehicleBase`, `IVehicleType`, navigation (`IPathableEntity`, `Navigator`, `Node`), `VehicleMoveHelper`, `VehicleFiringHelper`. Vehicle depends back on npc (`NpcBase`, `AWNPCStatics`, `NpcLevelingStats`, `ITarget`).
- Within npc: `npc/ai` (every AI class), `npc/registry` (`FactionRegistry`, `NpcDefaultsRegistry`, `TargetRegistry`, `FactionTradeListRegistry`), `npc/gamedata`, `npc/trade`, `npc/orders`, `npc/item`, `npc/tile`, `npc/config` (`AWNPCStatics`), `npc/render`, `npc/gui`, `npc/container`.

Third-party mods: Electroblob's Wizardry (`ebwizardry`, optional: `NpcFactionSpellcasterWizardry`, `EBWizardryCompat`, spell container/GUI; STATS counts 4 spell, 3 util, 2 entity, 2 registry classes in npc), Epic Siege and Twilight Forest (compat objects, optional, in `npc/compat`, other slice). AgriCraft etc. do not touch this slice.

Minecraft/Forge 1.12 APIs that no longer exist in the target: `EntityDataManager`/`DataParameter`, `applyEntityAttributes`, `SharedMonsterAttributes`, `EntityAITasks`/`EntityAIBase` (replaced by `GoalSelector`/`Goal`), `PathNavigateGround`, `EntityCreature` home position (`setHomePosAndDistance`), `canDespawn`/`despawnEntity`, `processInteract(EntityPlayer, EnumHand)`, `entityInit`, `EnumCreatureAttribute`, `DamageSource.causeMobDamage`, `ISpecialArmor` (Forge), `IEntityAdditionalSpawnData`, `EntityRegistry.registerModEntity`, `Loader.isModLoaded`, `@SidedProxy`, `@ObjectHolder` injection, `GameRegistry.registerTileEntity`, `FMLCommonHandler`-era events, `CreativeTabs`, `EntityPropertyManager` (loot property), `ItemColors.registerItemColorHandler`, `I18n.translateToLocal`, `Capabilities` (ITEM_HANDLER_CAPABILITY), `NBTTagCompound`-based packets, `PotionEvent.PotionApplicableEvent`, `world.setEntityState` status bytes, `DataFixer`/`IDataWalker`, `EntityEquipmentSlot` ordinals as slot ids.

## Porting notes (1.21.1 NeoForge)

Ranked hardest first. Names marked (unverified) are from memory and must be checked against the target source.

1. **NpcBase architecture and the 22 entity types (XL).** 1,459 lines of base class, 2,891 + 1,946 lines in entity packages, plus AI dependency. It extends `EntityCreature` and re-implements vanilla internals (attack, shield use, armor, sleeping, path weights). Suggested approach: keep a single abstract `NpcBase extends PathfinderMob` (unverified: `PathfinderMob` is the `EntityCreature` equivalent), build all 22 types as `EntityType`s through the registrar, move per-type data (stats, subtype, hostility) into a small `NpcTypeData` record, and fold the three `.elite` classes and the two civilian classes into data. Synched data: `defineSynchedData(SynchedEntityData.Builder)` with `EntityDataSerializers` (INT, BLOCK_POS, BYTE, BOOLEAN, STRING); bit-field `AI_TASKS` can stay an int. Attributes: register defaults with `EntityAttributeCreationEvent` per type (unverified event name) instead of setting at construction; runtime overrides (`maxHealthOverride`, leveling) become attribute base values or permanent `AttributeModifier`s with `ResourceLocation` ids. Entity NBT: `addAdditionalSaveData`/`readAdditionalSaveData` with `CompoundTag`; keep tag names to ease conversion of old worlds if ever wanted. Spawn data: NeoForge's `IEntityWithComplexSpawn` with `RegistryFriendlyByteBuf` (unverified), or avoid it by moving owner/skin/faction into synched data. Prefer synced data where values change at runtime (skin, owner, faction), because the spawn payload approach is only a one-shot. Eye height and size: `EntityType.Builder#sized` and `#eyeHeight` (unverified) set fixed dimensions; per-instance height/width needs `Attributes.SCALE` (unverified) or an override of `getDefaultDimensions(Pose)` plus `refreshDimensions`, and width/thinness separate from height is not possible with SCALE alone (it is uniform). Despawn: replace `canDespawn()` with `removeWhenFarAway(double)` returning false and `setPersistenceRequired` (unverified), and the faction `canDespawn` flag by returning it from `removeWhenFarAway`; `despawnEntity` override -> `checkDespawn` (unverified). Interaction: `mobInteract(Player, InteractionHand)` returning `InteractionResult`. Damage and armor: no `ISpecialArmor`; armor reduction should use the 1.21.1 vanilla armor formula and equipment attribute modifiers; the `armorValueOverride` hack becomes an `ARMOR` attribute base value. `attackEntityAsMob` -> `doHurtTarget`; enchantment helper calls changed (enchantments are data-driven in 1.21, signature changes: unverified); re-check whether the copied vanilla code is still needed. Shields: re-check against 1.21.1's `ShieldItem`/blocking implementation before keeping the hand-written override set (blockUsingShield, damageShield, updateActiveHand, `world.setEntityState(30)`). Mounts: `startRiding`/`getVehicle` naming. Slot ids: 1.21.1 `EquipmentSlot` has an extra `BODY` entry (unverified ordering), so the `ORDER_SLOT = 6` / `UPKEEP_SLOT = 7` int scheme can clash; replace the two extra stacks with explicit fields and container slots. Effort XL, driven by AI and the 22 classes; the entity layer alone is L.

2. **Faction data pipeline and faction NPC stats (L).** Faction content is JSON, which is a good fit for data-driven design, but it uses 1.12 attribute names (`generic.maxHealth`, `generic.followRange`, ...), 1.12 class names (`net.minecraft.entity.passive.EntityHorse`), 1.12 item ids (`minecraft:iron_sword` still valid, mod ids `ancientwarfarenpc:iron_halberd`), 1.12 loot table paths and EBWizardry spell ids. About 1.3 MB of JSON (`faction_trade_lists.json` is 1.05 MB and belongs to the trade slice). Approach: keep `RegistryLoader`-style loading but convert to NeoForge datapack reloads (`SimpleJsonResourceReloadListener`, unverified name) or keep config-folder JSON with a one-time conversion script that rewrites attribute names to the 1.21.1 ones (`generic.max_health` etc., unverified exact ids), `horse_entity` to an entity-type registry id, and spells out. A script-based migration of the JSON is mechanical; the loader redesign is M. The `AdditionalAttributes` mini-framework (5 keys: `burns_in_sun`, `undead`, `horse_entity`, `entity_sound`, `heal_per_try`) can become a record. Effort L for the whole pipeline with registry classes; the part in this slice (attributes package, `NpcFaction` defaults hooks) is M.

3. **Skins (L).** 3,606 PNG files (21 MB) and a meta file. In 1.21.1 these should become normal resource-pack textures (`textures/entity/npc/<name>.png`) so that `RenderNpc*` can use `ResourceLocation`s without runtime texture upload; the user-config folder loader would have to be kept or replaced by supporting resource packs (the clean way). Mapping: `skin_pack.meta` -> a JSON resource listing per type (or a datapack-style asset reload). Player skins: use `SkinManager`/`PlayerSkin` and `GameProfile` resolution from `ResolvableProfile` (unverified naming in 1.21.1); 1.12's `loadSkinFromCache` is gone. `NpcCommonProxy.cacheProfile` calls `getPlayerProfileCache` and `fillProfileProperties` on the server and sends the profile as NBT: replace with a synced `GameProfile` or just the player name and let the client resolve. The Alex (slim) flag is available from `PlayerSkin.Model` (unverified). Effort L mainly for asset handling and renderer integration; code in this slice (~420 lines) is M. Binary assets should stay out of the jar-style runtime loader: consider whether to ship the 21 MB pack at all.

4. **NetworkHandler/Packet/GUI plumbing for NPCs (M).** `PacketEntity`-style NBT packets with string tags (`ownerName`, `skinSettings`, `attackTarget`) become typed payloads or synched data. Six packets here map one-to-one to `CustomPacketPayload` records registered through `RegisterPayloadHandlersEvent` (unverified name) with `StreamCodec`s; enum ordinals should become enum codecs. Menu opening for entity-bound menus uses `player.openMenu(provider, buf -> buf.writeVarInt(entityId))` (unverified). `PacketStructureEntry` must be registered with the structure module, not here. Effort M.

5. **Baton command system (M).** `NpcCommand` (161 lines): a payload plus `Command` records; the entity-lookup-by-id-then-UUID hack should become UUID-only. The commands that mutate `NpcPlayerOwned` (follow, home, upkeep) rely on AI. Effort S for this slice's code, M with AI.

6. **Sleeping/bed handling (M).** `findBed/lieDown/wakeUp` with 0.2x0.2 size hack and BlockBed state `OCCUPIED`: use the vanilla `Pose.SLEEPING`, `LivingEntity.startSleeping(BlockPos)` (unverified) and `BedBlock.OCCUPIED`; dimensions come from pose now (`getDimensions(Pose)`), removing `setSize`. The bed block is now one block state per colour and the facing property is `HORIZONTAL_FACING`; part property `PART` kept. Effort M.

7. **Event handler (M).** AI injection into every `EntityCreature` on join is O(entities) and relies on reading `GoalSelector` internals (`availableGoals`/`WrappedGoal`, `NearestAttackableTargetGoal.targetType` - names unverified); `targetTasks.taskEntries` and `EntityAINearestAttackableTarget.targetClass` are private in 1.21.1 and need access transformers or reflection. Alternatives: a mixin, or give hostile mobs a tag-based generic target goal via `EntityJoinLevelEvent`. Spawn cancellation: `FinalizeSpawnEvent` cancel (unverified) for natural spawns is better than cancelling join for every spawn (also affects structure-spawned and command-spawned mobs). Chest/dig protection: `PlayerEvent.BreakSpeed`, `PlayerInteractEvent.RightClickBlock` exist; chest locking by tile NBT `generatedInventory` -> data attachment or block entity data. Effort M.

8. **Faction standing/team classes (M).** `FactionTracker` and `FactionEntry` are small (176 lines). Saved data must move to `SavedData` with codecs on the overworld data storage (data owned by the gamedata slice, other file); login event -> `PlayerEvent.PlayerLoggedInEvent`. Standing lookups use player *names* (not UUIDs) as keys in several places, a data-model decision for the port. Effort M together with `gamedata`.

9. **Faction mounted NPCs (M).** Horse spawning by class name (`horse_entity`), mount killed on despawn or repack, rider AI. In 1.21.1 `Horse`/`AbstractHorse` constructors take `(EntityType, Level)`; use an entity-type registry id instead of a Java class name. The hack `getRidingEntity() instanceof EntityHorse` -> `getVehicle()`. Effort M.

10. **EBWizardry spellcaster (cannot port as is).** `NpcFactionSpellcasterWizardry` (273 lines) implements Wizardry's `ISpellCaster` and uses its particle builder and potions. The mod is not available for 1.21.1; keep only the plain `NpcFactionSpellcaster` and replace spell casting with a small built-in spell set (fireball, lightning, snare...) or a compat layer if a 1.21.1 Wizardry release appears. 5 spellcaster presets and the `spells` JSON strings must then be remapped. Effort L if built-in spells are wanted, S if dropped.

11. **Sound registry and string lookup (S).** 119 sounds: registrar + string map. Effort S.

12. **Item colour handler, creative tab, module entry (S).** `NPCItemColors` -> `RegisterColorHandlersEvent.Item` (unverified exact name); `AWNPCTab` -> creative mode tab registered through the registrar with a custom sort (SortItemsFirstComparator logic). `@SidedProxy` is gone: use a client-only class in the `client` subpackage and `FMLClientSetupEvent`. Effort S.

13. **Leveling stats (S).** `NpcLevelingStats` 132 lines; moves unchanged except NBT API and `instanceof` branches. Could become a data attachment (`AttachmentType`) if shared with other entity types, but it is tightly coupled to `NpcBase`. Effort S.

14. **Vehicle-user coupling (M).** `IVehicleUser`, `ITarget` and the two siege engineer classes need the vehicle module; plan them with the vehicle port, not before. Effort M.

## Candidates for capttrackerlib

Generic enough for reuse by another mod:
- Typed JSON data-registry loader with `load_after` dependency ordering and config-folder overrides (the `RegistryLoader` pattern used here by five parsers); core slice owns the class, but the NPC faction pipeline is its main user.
- A small "additional attribute" typed-key framework (`IAdditionalAttribute<T>` with `parseValue`) if the library wants typed per-entity data from JSON; low value alone.
- Owner/team/friend check (`Owner.isOwnerOrSameTeamOrFriend`), useful for any pet/ownable-entity mod.
- Sleep-in-bed helper for non-player mobs (find nearest free bed, sleep pose, wake beside bed), if another mod has bed-using NPCs.
- Hand-written ranged-attack helper (`RangeAttackHelper`: distance-compensated arrow shot) for any mod with archer mobs.
- Entity skin pool selection (deterministic random skin from the entity UUID, with player-skin option) is reusable for any humanoid-NPC mod.

Looks generic but should stay inside Ancient Warfare:
- `NpcBase` and leveling (tied to orders, upkeep, town halls, faction defaults).
- `FactionTracker`/`FactionEntry` (tied to `TeamData`, `FactionRegistry`).
- `EventHandler.injectAi` (tied to NPC target lists and structure protection).
- Sound registry with 111 creature sounds (assets specific to AW factions).

## Open questions

- What is the intended check in `NpcFaction.isPotionApplicable` (`getFaction().equals("lizardman|coven")`)? No faction is named that way; confirm whether the poison immunity for lizardmen and coven should be per faction or per data flag.
- Which of the three faction sounds/creature prefixes (`kobold`, `gargoyle`, `skeleton`, `zombie`, `kong_ape`, etc.) are actually referenced by `entity_sound` in the data? A distinct-value count over `faction_npc_defaults.json` gives 34 values (`barbarian`, `brigand`, `coven_dryad`, ..., `vampire_bride`, `zombie`). Two referenced values have no registered sound in `AWNPCSounds`: `owlbear` and `coven_puppetd` (the registered name is `coven_puppet_*`). The unused registered sounds could be dropped.
- Spellcaster presets: the data defines `spellcaster` and `spellcaster.1` to `.5`, while the GUI loops over `spellcaster.<i+1>`; I did not trace how a preset is applied to an NPC whose `getNpcType()` is always `spellcaster` (it is applied through the GUI in the container slice).
- Faction count per type: a JSON-only recount gives 487 of 660 slots enabled (matches the estimate). The parser's own merge order was not executed; an exact value needs `NpcDefaultsRegistry` read in full.
- `NoFriendlyFirePotion` is not registered as an entity; confirm on a running 1.12.2 client that it renders and syncs through the `EntityPotion` fallback tracking and not via registry.
- Decision: keep NPCs keyed by player *name* (existing saves, `revengePlayers`, standings) or migrate to UUID.
- Decision: replace the hand-copied vanilla mob and shield logic by 1.21.1 vanilla behaviour where possible, or port the behaviour verbatim (affects combat balance).
- Decision: whether to ship the 21 MB skin pack inside the jar or as an optional resource pack.
- I did not read the AI classes, `ItemNpcSpawner`, `NpcDefaultsRegistry` in full, `gamedata`, or the `render` package (other slices), so cross-slice details are from the call sites only.

## Verification

Checked by a second pass that re-derived the lists from source and JSON (no code from the mod was run).

Checked and found correct:
- Scope: all 13 `npc/` directories and their file counts (3, 13, 21, 9, 7, 4, 3, 2, 2, 5, 3, 7, 2 = 81) match the table. Line totals were recounted with `wc -l`; `npc/init` is 807 lines, so the total is 8,106.
- 22 entity types: `init/AWNPCEntities.java` (7 `NpcDeclaration` plus 15 faction registrations, spellcaster chosen by EBWizardry load). Tracking range 120, update frequency 3, velocity updates true, `nextID++` ids (lines 62, 262-270). Registry name constants match `core/entity/AWEntityRegistry.java` lines 13-35. `registerEntity` skips existing names (line 46-54).
- 119 sounds (`createSoundEvent` calls in `AWNPCSounds.java`), of which 8 are bard and teleport and 111 creature sounds.
- Items: 58 registered in `AWNPCItems.register` (the table already sums to 58; the summary said 57). Plus `ItemBlockOwned` makes 59.
- Blocks: 1 block (`town_hall`), 1 tile entity (`town_hall_tile`).
- Containers: 13 in `AncientWarfareNPC.preInit` plus 1 conditional Wizardry container (id 62). The GUI ids in the body match `core/network/NetworkHandler.java`.
- Packets: ids 12, 13, 27, 31, 32, 33 are NPC-owned, and 29 (`PACKET_STRUCTURE_ENTRY`) is registered from `AncientWarfareNPC` as stated.
- Compat objects (3) and JSON parsers (5) registered in `preInit` (lines 126-134).
- Slash commands: 3 (`CommandFaction`, `CommandTeams`, `CommandDebugAI`).
- Baton `NpcCommand.CommandType` values and the persistent set (ATTACK, GUARD, ATTACK_AREA).
- `NpcBase` is 1,459 lines; its 6 base `DataParameter`s plus `NpcFaction.SOUND` and the Wizardry `HEAL_COOLDOWN` give the 8 synced params. `ORDER_SLOT = 6` and `UPKEEP_SLOT = 7`. Bed hitbox `setSize(0.2F, 0.2F)`.
- Leveling XP `(level + 3)^2`; damage `base * (1 + level * npcLevelDamageMultiplier)`.
- `NpcFaction` extends `NpcBase`; `NpcBase` implements `IEntityAdditionalSpawnData, IOwnable, IEntityPacketHandler, IPathableEntity, INpc`; `IKeepFood` is implemented only by `NpcPlayerOwned`; 16 concrete faction classes.
- Faction defaults: 44 factions in `factions.json`, 22 with a `targets` block, 7 with `themed_blocks`; `empire` standing fixed (`standing_can_change` false, -1000); default -70.
- Owned NPC defaults: maxHealth 20 (combat 30), speed 0.325, follow range 60, attack damage 1, can_swim and can_break_doors true.
- Skin pack: 3,606 PNGs, `skin_pack.meta` with 2,694 lines. Skin types DEFAULT, PLAYER, NPC_TYPE.
- `NPCItemColors` fixed colour 0xEF5757; `EventHandler` uses `EntityJoinWorldEvent` at LOWEST priority, `RightClickBlock`, `BreakSpeed`.
- `NoFriendlyFirePotion` renders via `RenderPotion` (`NpcClientProxy` line 114) and is not in `AWNPCEntities`.
- Dependencies: STATS cross-module counts (npc to core 116, to structure 5, to vehicle 10; structure to npc 10; vehicle to npc 6) and the Electroblob's Wizardry counts (spell 4, util 3, entity 2, registry 2) match `jar_stats.txt`.
- Faction JSON enabled-slot recount: 487 of 660 (rakshasa 3, max 15), now stated as verified against JSON only.
- Skin bug: `NpcSkinSettings` writes the NPC skin string with a 100-char limit (line 100) and reads it with 100 in the NPC_TYPE branch (line 116) and 60 in the other branch (line 123). The report's description is correct.

Corrected in the body:
- Scope total 8,107 to 8,106, `npc/init` 808 to 807.
- `NpcFaction*` file count 20 to 19.
- Item count 57 to 58 (59 with ItemBlockOwned).
- JSON counts: `entity_sound` 64 to 97, `burns_in_sun` 27 to 28, `undead` 19 to 20, `horse_entity` 13 to 14, `can_swim` 5 to 6 (and it is top-level, not inside `additional_attributes`).
- Open question on sounds: 19 distinct values to 34; `owlbear` and `coven_puppetd` are referenced but have no registered sound.
- Marked `player.openMenu(provider, buf -> ...)` as unverified.

New observation (not in the report): `factions.json` `hostile_towards_factions` maps contain the key `lizardmen`, which matches no faction (the faction is `lizardman`), so that hostility entry is never applied. Likewise the `isPotionApplicable` string `lizardman|coven` never matches, as the report says.

Not settled:
- Exact enabled-type counts from the merge order in `NpcDefaultsRegistry`; the JSON-only recount is 487 but the parser was not executed.
- Which of the 111 creature sounds are referenced beyond the 34 `entity_sound` values; `barbarian_death` alias and `Public fields for 111` were not checked.
- The default horse class (`EntityHorse`) for `horse_entity` was taken from `NpcFactionMounted`; the fallback was not traced.
- 1.21.1 API names marked "(unverified)" in the body were not checked against a 1.21.1 source (no network access or 1.21.1 sources in scope). The unmarked names used in this report (for example `PlayerEvent.BreakSpeed`, `PlayerLoggedInEvent`, `SavedData`, `AttachmentType`, `Pose.SLEEPING`, `BedBlock.OCCUPIED`) were not individually verified in this pass.
- `NpcBase`, AI, `gamedata`, `render` and `item` slices were not re-read; cross-slice claims rest on STATS counts and call sites.
