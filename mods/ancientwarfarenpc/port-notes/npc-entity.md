# NPC module, slice npc-entity: NpcBase and every NPC entity class

Ported from `npc/entity/**` (with `entity/faction` and `entity/vehicle`), `npc/inventory` and the entity side of `npc/npc_command`.
Nothing was built or run. The code was checked against the extracted 1.21.1 / NeoForge 21.1 sources with a throw-away `javac -sourcepath`
pass (output to the scratchpad, not a build; script and stub generator in `analysis/port/npc_entity_*.py`). That pass reports no error in
`entity/` or `inventory/` other than the missing logging libraries it does not have on its path (`com.mojang.logging`, `org.slf4j`), with the
classes of the later slices (AI, orders, trade) replaced by scratch stubs that only have the signatures listed under "What this slice expects".

This slice was started in an earlier run (NpcBase, the seven player-owned classes, `NpcFaction`, vehicle package, equipment handler) that ended before
the faction classes and the notes. This run reconciled those files with what the registry, skin, town hall, command and network slices had
written meanwhile, wrote the 18 missing faction files, fixed what the compile pass found and wrote these notes.

## What was ported (5,481 lines, 44 files)

`io.github.capttracker.ancientwarfarenpc.entity` (16 files)
- `NpcBase` (1,384 lines): abstract `PathfinderMob`, implements `IEntityWithComplexSpawn`, `IOwnable`, `IEntityPacketHandler`, `IPathableEntity`,
  Vehicles' `IVehicleCrew` and `Npc`. Attributes (`createNpcAttributes()`), the 6 equipment slots plus `ORDER_SLOT` 6 and `UPKEEP_SLOT` 7 (`getItemBySlot(int)`,
  `setItemSlot(int, ItemStack)`, the 1.12.2 slot numbers), leveling hooks, owner and teams, home (the `Mob` restriction), bed finding and sleeping, shield rules,
  skin settings, the AI task bit field, status values, save data (tag names unchanged), item (spawner) form, repack, spawn data, interaction, command and follow.
- `NpcPlayerOwned` (food, upkeep, town hall, baton commands, defaults from `owned_npc_defaults`), `NpcCombat`, `NpcWorker`, `NpcCourier`, `NpcTrader`, `NpcPriest`, `NpcBard`.
- `NpcLevelingStats`, `RangeAttackHelper`, `NoFriendlyFirePotion` (now a real entity type, `AwNpcs.NO_FRIENDLY_FIRE_POTION`).
- Links: `IKeepFood`, `ITownHall` (implemented by `TownHallBlockEntity`), `NpcBridge` (hooks the item and menu slices install), `NpcItemIds` (identifies batons and
  orders by registry name), `NpcEntityPayloads` (the entity data payload that replaces `PacketEntity`).

`entity.faction` (19 files): `NpcFaction`, `IHorseMountedNpc`, `NpcFactionArcher`, `...ArcherElite`, `...Bard`, `...Civilian` (abstract), `...CivilianMale`, `...CivilianFemale`,
`...Leader`, `...LeaderElite`, `...Mounted` (abstract), `...MountedArcher`, `...MountedSoldier`, `...Priest`, `...SiegeEngineer`, `...Soldier`, `...SoldierElite`,
`...Spellcaster` (the plain one), `...Trader`.

`entity.vehicle` (6 files): `IVehicleUser`, `ITarget` (extends Vehicles' `IAimTarget`), `EntityTarget`, `BlockPosTarget`, `TargetFactory`, `NpcSiegeEngineer`.

`inventory`: `NpcEquipmentHandler` (8 slot item handler view of an NPC).

`npccommand`: not written here. `npccommand/NpcCommand` was ported by the base slice and imports `NpcPlayerOwned` (see `npc-base.md`).

## Decisions that differ from a line-by-line port

- Goals are built in `registerGoals()` of each class (`Mob`'s constructor calls it, server side only, before any field of the NPC classes is initialised).
  Every faction class calls `super.registerGoals()` first (the sun restriction at 2 and the sun flight at 3 of `NpcFaction`), priorities are the original ones.
  Fields that `registerGoals` assigns (`meleeAI`, `horseAI`, `courierAI`, ...) have no initialiser; they are null on the client and are null-checked.
- The 3 synched values `BED_POS`, `BED_DIRECTION` and `IS_SLEEPING` are replaced by vanilla sleeping (`startSleeping`, `Pose.SLEEPING`, the sleeping position; the 0.2 x 0.2
  hit box comes from the pose). Left as synched data: `AI_TASKS`, `SHIELD_DISABLED_TICK`, `SWINGING_ARMS` (`NpcBase`) and `SOUND` (`NpcFaction`). `HEAL_COOLDOWN` belonged to the Wizardry class.
- `getDimensions(Pose)` is final in 1.21.1: the random size of a faction NPC is returned by `getDefaultDimensions` and applied with `refreshDimensions()`.
- Armor: `getArmorValue()` returns the override if set, else the vanilla attribute based value (no `ISpecialArmor`). Attack: `doHurtTarget` is the vanilla one plus the main hand item's
  `hurtEnemy`/`postHurtEnemy` (the vanilla mob attack does not call them). Shields use the vanilla blocking with the original's extra rules (`hurtCurrentlyUsedShield`, `blockUsingShield`).
  The custom `updateActiveHand` is not needed (the vanilla check keeps a damaged shield in use).
- Faction NPCs get their faction with `setFactionNameAndDefaults(String)` after `EntityType.create` (`AwNpcs.NpcDeclaration.createEntity` does that). The faction defaults are
  `NpcDefaultsRegistry.getFactionNpcDefault(faction, getNpcType())`, which the NPC reaches through the new `NpcBase.getNpcDefault()` (owned NPCs: `getOwnedNpcDefault(getNpcType())`).
  The additional attributes are the registry's `FactionAdditionalAttributes` record (`NpcFaction.getAdditionalAttributes()`), not the 1.12.2 `entity/faction/attributes` mini framework.
- Skins: the server no longer resolves player profiles. `setSkinSettings` sends the settings tag (`skinSettings`) to the tracking players through `NpcEntityPayloads`; the client resolves a player name
  with `NpcSkinSettings.PlayerSkinResolver`. Spawn data carries owner and skin settings (the UUID is in the vanilla spawn packet), faction NPCs add name, height and width.
- The two EBWizardry items are dropped: `NpcFactionSpellcasterWizardry` and its `HEAL_COOLDOWN`. `NpcFactionSpellcaster` is the plain class for the registry name `faction.spellcaster`.

## Original bugs fixed (also noted in the code)

- Male player-owned NPCs played the hurt sound on death (`HUMAN_HURT`); now `HUMAN_DEATH`.
- `NpcBase.setOwnerName` never told the clients (compared the new name with itself); it does now.
- `onOffhandInventoryChanged` added a new shield AI at every call and never removed it; it keeps one.
- `NpcFaction.canBeAffected` (was `isPotionApplicable`): compared the faction with the string `lizardman|coven`, so nobody was immune; lizardmen and the coven are now poison immune.
  The faction priest is poison immune as before (the 1.12.2 event check never allowed poison).
- Revenge timers use the game time (the 1.12.2 world time stops with the daylight cycle).
- The skin buffer reader limit (60 against 100) is fixed in the skin slice.

## What this slice expects from slices that do not exist yet (checked only against scratch stubs)

AI slice (`ai`, `ai.owned`, `ai.faction`, `ai.vehicle`; names of report 07, the entity code assumes these members):
- `NpcNavigator(Mob, Level)` must extend `GroundPathNavigation` (`FloatGoal` and the defaults' path settings need ground navigation); `AIHelper.isAdditionalEntityToTarget(Entity)`.
- `NpcAIBlockWithShield(NpcBase)` with `onPreDamage(DamageSource, float)`; `NpcAIAttackMeleeLongRange(NpcBase)` with `setAttackReachFromWeapon(ItemStack)`.
- `NpcAIAttackNearest(NpcBase, Predicate<Entity>)`, `NpcAIFactionHurt(NpcFaction, Predicate<Entity>)`, `NpcAIFactionRangedAttack(npc)` and `(npc, double, int, int)`, `NpcAIFactionPanic(npc, double)`,
  `NpcAIMoveHome(npc, float, float, float, float)`, `NpcAIDoor(npc, boolean)`, `NpcAIWatchClosest(npc, Class, float)`, `NpcAIFactionRideHorse<T>` with `onKilled()`,
  `NpcAIPlayerOwnedRideHorse` with `onKilled()`, `NpcAIPlayerOwnedPatrol`/`Work`/`WorkRandom`/`Courier`/`Trader` with `readFromNBT(CompoundTag)`/`writeToNBT(CompoundTag)` and the
  `onOrdersInventoryChanged`/`onOrdersChanged`/`onOrdersUpdated` callbacks used by `NpcCombat`, `NpcWorker`, `NpcCourier`, `NpcTrader`; the generic vehicle goals (`<T extends NpcBase & IVehicleUser>`).
- Goal constructors must not read NPC state: `registerGoals` runs inside `Mob`'s constructor, before `owner`, `levelingStats`, `additionalAttributes` and the other fields of the NPC classes exist.
  (`NpcAIFactionRestrictSun` and `NpcAIFactionFleeSun` must call `burnsInSun()` in `canUse`, not in the constructor.)
- `NpcAIPlayerOwnedFollowCommand` and others read `NpcPlayerOwned.getCurrentCommand()`, `setPlayerCommand`, `isAlarmed`, `getUpkeepPoint()`, `getUpkeepBlockSide()`, `getUpkeepDimension()`, `withdrawFood(IItemHandler)`.

Orders and trade slice:
- `orders.UpkeepOrder`: `static Optional<UpkeepOrder> getUpkeepOrder(ItemStack)`, `Optional<BlockPos> getUpkeepPosition()`, `Direction getUpkeepBlockSide()`, `ResourceKey<Level> getUpkeepDimension()`, `int getUpkeepAmount()`.
- `orders.WorkOrder`: `static WorkOrder getWorkOrder(ItemStack)` (null if none), `boolean isNightShift()`. `orders.TradeOrder`: `static TradeOrder getTradeOrder(ItemStack)`, `POTradeList getTradeList()`.
- `trade.POTradeList` (public no-arg constructor). `trade.FactionTradeList`: public no-arg constructor, `boolean isEmpty()`, `void tick(Level)`, and NBT through
  `serializeNBT(HolderLookup.Provider)` / `deserializeNBT(HolderLookup.Provider, CompoundTag)` (NeoForge `INBTSerializable<CompoundTag>`). If that slice picks other names, change `NpcFactionTrader`.

## Wiring needed in shared files

All of it is already in `AwNpcs` (written by the base slice): the 22 entity types and the potion type, `EntityAttributeCreationEvent` (`AwNpcs.onEntityAttributes`: `Mob.createMobAttributes()` plus
`ATTACK_DAMAGE` and `ATTACK_SPEED` for every declaration; `NpcBase.createNpcAttributes().build()` is the same set and can replace it), `NpcEntityPayloads.registerPayloads(registrar)`.
Still to do by others:
1. Item slice: `NpcBridge.setSpawnerStackFactory(npc -> ...)` (builds the spawner stack with `npc.writeAdditionalItemData`; repack, pick block and the town hall death list use it).
2. Menu slice: `NpcBridge.setGuiOpener((player, npc, gui) -> ...)`, entity id in the menu's extra data; screens call `NpcBase.openAltGui`, `hasAltGui`, `repackEntity(Player)`, `setSkinSettings`, `getItemBySlot(int)`/`setItemSlot(int, ItemStack)` or `new NpcEquipmentHandler(npc)`.
3. Client: `NpcSkinSettings.setPlayerSkinResolver(...)`; renderers for all 22 types (`RenderNpcBase`, `RenderNpcFaction` read `getTexture()`, `getSkinSettings().renderFemaleModel(isFemale())`, `getAITasks()`, `isSwingingArms()`,
   `getShieldDisabledTick()`, `getRenderSizeModifier()`, `getWidthModifier()`) and a `ThrownItemRenderer` for `AwNpcs.NO_FRIENDLY_FIRE_POTION`.
4. Structures (if its spawner ever exists): `NpcFaction.addDespawnHandler(handler)` to turn a despawned faction NPC back into a spawner (the 1.12.2 respawn data capability).
5. Lang: merge `port-notes/npc-entity.lang.json` (the 673 `entity.ancientwarfarenpc.<full type>.name` keys that `getName()` translates, plus the 1.21 entity type ids). `npc-base.lang.json` already has them.

## Not ported

- `NpcFactionSpellcasterWizardry`, `HEAL_COOLDOWN`, the `spells` tag (EBWizardry does not exist for 1.21.1).
- `entity/faction/attributes` (`AdditionalAttributes`, `*Attribute`): the registry record replaces it. `EntityVehicleProperty`: `registry.RidesVehicleCondition` (base slice).
- `registerFixesNpc` (data fixers: no old worlds). `getYOffset` (-0.35 rider offset: seating is the vehicle's job in 1.21). `onCollideWithPlayer` (empty effect). The custom `updateActiveHand`.
- `EntityAIRestrictOpenDoor` of the 1.12.2 goal lists: it only acts for NPCs inside a vanilla village at night, which does not exist as such in 1.21.1.
- The 1.12.2 `NpcBase.getPickedResult` rename is `getPickResult` (kept); `PacketEntity` is replaced by `NpcEntityPayloads.EntityData`.

## API names that could not be confirmed

- Everything under "What this slice expects" (the code exists only as names in report 07 and 08).
- `Entity.firstTick` is used to skip the skin update packet while the NPC is still being loaded; it exists (protected) in the sources.
- `ThrowableItemProjectile` and `ItemSupplier` for `NoFriendlyFirePotion`, `Arrow(Level, LivingEntity, ItemStack, ItemStack)` and `EnchantmentHelper.onProjectileSpawned` (Flame/Power/Punch for the NPC archers) were
  compile checked against the decompiled sources but not seen running.
- Found by the pseudo compile in a file this slice does not own: `NpcNetwork.NpcCommandPayload` has a record component named `type` (the `CommandType`), which clashes with
  `CustomPacketPayload.type()` ("invalid accessor method in record", line 106). Rename the component (for example `commandType`).

## Rework candidates (step 4, not done)

- Faction NPCs get full health and their default maximum health at every load: `readAdditionalSaveData` applies the defaults after reading the saved health (same as 1.12.2).
- `NpcFactionTrader` despawns an empty trader only after it was loaded from a save (`noTradesDespawn` is set only by `readAdditionalSaveData`, as in the original).
- Hostility keys are player names (revenge lists, standings); UUIDs would survive renames.
- `NpcBase.getName` is `entity.ancientwarfarenpc.<full type>.name` plus the custom name; faction NPCs use the faction in the key (`orc.soldier`). Vanilla's entity type name keys are now also in the lang file.
- The home is the vanilla `Mob` restriction; owned NPCs set it at save time from the town hall or their position (as the original did).
- Sleeping uses the vanilla bed rules (the bed block must stay a bed, an NPC wakes when it is gone); beds of other mods count through `isBed`.
- Spawner NPC subtype from the held item (`getNpcSubType`) walks tool abilities (`axe_dig`, `pickaxe_dig`) and the hammer and quill item classes instead of 1.12.2 tool classes.
