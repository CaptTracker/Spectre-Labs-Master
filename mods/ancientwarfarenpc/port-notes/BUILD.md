# Build record (first compile)

`:ancientwarfarenpc:compileJava` and `:ancientwarfarenpc:build` pass (0 errors; 6 `[removal]` warnings for `EventBusSubscriber.bus()`/`Bus` in `NpcItemPropertiesClient`,
`NpcRenderClient` and `NpcScreensClient`, the same form Core and Vehicles have). Only three real compile errors came up; the slices' scratch `javac` passes had caught the rest.

## Wired

- `AncientWarfareNPC` constructor, before `REG.register(modBus)`: `AwNpcs.load()` + `AwNpcs.registerEvents(modBus, container)` (config, the private entity type, recipe serializer and loot condition
  registers, payloads, `EntityAttributeCreationEvent`, block entity item handler capability, common setup, the five data parsers, skin parser, compat hooks, game events, commands),
  `AwNpcItemComponents.load()` + `registerEvents(modBus)` (spawner stack factory, baton provider, creative tab spawner stacks, scythe), `AwNpcMenus.load()` + `registerEvents(modBus)` (14 menu types,
  menu payload, GUI openers). TODO removed.
- Client classes (`NpcItemPropertiesClient`, `NpcRenderClient`, `NpcScreensClient`, `NpcCommandOverlay`, `NpcWorkLinesOverlay`, `NpcPlayerSkins`) and `NpcShieldEvents` register themselves with
  `@EventBusSubscriber`; nothing to wire. Every entity type of `NPC_MAP` has an attribute supplier and a renderer; all 14 menu types have a screen, each once. There were no earlier
  duplicate screen registrations to remove.
- `lang/en_us.json`: merged the five `*.lang.json` fragments (942 keys), removed `block.ancientwarfarenpc.example_block`. Existing keys won: 8 keys differ in text between fragments
  (for example `itemGroup.ancientwarfarenpc` stays "Ancient Warfare NPCs" from the template; `entity.ancientwarfarenpc.faction.archer.elite` stays "Elite Faction Archer" from the base fragment, over the entity fragment's "Faction Elite Archer").
- `sounds.json`: no `.sounds.json` fragments exist; it already has the 119 events and matches the 119 `add(...)` names of `AwNpcSounds` exactly.
- Creative tab (`AwNpcs.TAB`): applied the item slice's change, the plain data-less spawner is no longer listed (`first.add(NPC_SPAWNER)` removed, the second loop skips it); the spawner stacks of
  all NPC types come from the `BuildCreativeModeTabContentsEvent` listener in `AwNpcItemComponents`. There is only one creative tab.
- All 179 JSON files under `src/main/resources` parse; every registered item has a lang key and a model; no model refers to a missing texture or model.

## Changed

- `network/NpcNetwork.NpcCommandPayload`: record component `type` renamed `commandType` (it clashed with `CustomPacketPayload.type()`, "invalid accessor method"). The positional constructors,
  codec and handler use the new name; no other class read the component.
- `client/render/NpcRenderer.getNameForRender` was `static` but uses the class type variable `T`; now an instance method.
- `init/AwNpcItemComponents`: removed the call `NpcAIPlayerOwnedPriest.setNpcFromStackFactory(...)` (and its import). The priest goal has no such setter, it calls `ItemNpcSpawner.createNpcFromItem` directly.
- Dependencies (Core `RecipeResourceLocation`, `HammerItem`, `AwMenu.setRefreshListener`) needed nothing: they already compile.

## TODO(port) left

- `compat/EpicSiegeCompat` (Epic Siege Mod 1.21 target goal class unknown) and the Twilight Forest dimension id (`twilightforest:twilight_forest`, unconfirmed) are unchanged from the base slice.
- The key handler that calls `NpcCommand.handleCommandClient(type, hit)` and the client logout hook that calls `FactionData.resetClientMirror()` / `TeamData.resetClientMirror()` belong to no slice
  (baton keys need Core's input handling). Not added here, so baton commands cannot be sent from a client yet and the faction/team client mirrors are not cleared on logout.
- Structures module: `IStructureProtection` is never implemented, so hostile spawn / locked chest rules are off. `recipe/research/spawner_*.json` still give the spawner through
  `minecraft:custom_data` (see npc-base.md, wiring item 6); change to the spawner's `npc_spawner` component when testing.
- The Wizardry style spellcaster menu, screen and `ISpellcasterNpc` exist but nothing opens them (Wizardry has no 1.21.1 release).

## Runtime-only risks (nothing was run)

- Registration order: entity types, recipe serializer and loot condition use private `DeferredRegister`s registered in `AwNpcs.registerEvents` (before `REG.register`); items, block, block entity, menus,
  sounds, data components and the creative tab go through the registrar. Registry names were checked for duplicates by grep and by comparing sets (64 items with lang keys and models, 119 sounds,
  14 menus, 8 payload ids, all distinct), but only the game shows a clash between the two registers.
- `AwNpcs.loadNpcSubtypeEquipment` runs in `FMLCommonSetupEvent.enqueueWork`. The five JSON registries load on the `RegistryLoader.SERVER` data pack reload, so `NpcDefaultsRegistry` is empty until the
  first world/server start; an NPC created or read before then gets no defaults.
- `NpcBridge`, `OrderGuiBridge` and `TownHallBlockEntity` hooks are set in the two `registerEvents` calls at constructor time, on both sides, so they are never null on a client.
- Capabilities: only the town hall block entity (`ItemHandler.BLOCK`, every side, `townHall.inventory`) is registered. NPC inventories are not exposed as entity capabilities (1.12.2 did not either).
- Attributes: one shared supplier (`Mob.createMobAttributes()` + attack damage + attack speed) for all 22 NPC types. `NpcDefault.applyAttributes` silently skips attributes the supplier lacks, so a data
  default that names an attribute outside that set is ignored.
- Client-only classes: nothing outside `client/` imports `net.minecraft.client` or the client package (grep; only a Javadoc mention in `AWNPCStatics`). `AWNPCStatics` registers a client config spec that
  common code can read; its values are only used by client classes.
- Payloads: `AwNpcs` and `AwNpcMenus` each call `event.registrar(NpcNetwork.VERSION)`; the source shows `registrar(String)` only creates a new `PayloadRegistrar`, so no conflict. Ids are distinct
  (`npc_command`, `extended_reach_attack`, `faction_update`, `team_membership_update`, `team_standings_update`, `team_standing_update`, `entity_data`, `open_npc_menu`).
- `NpcEventHandler` reads `NearestAttackableTargetGoal.targetType` by reflection (the only non-static `Class` field); if it is not found, mobs are simply not made to hunt NPCs, with an error in the log.
- Trade list item stack JSON (component formats such as `minecraft:enchantments`, `dyed_color`) was written from memory; failing stacks are dropped when a trader opens, not at startup.
- Skin index: 3,606 skin PNGs and 1,768 "custom" skins make the jar 19 MB; the first resource reload on the client may be slow (not measured).
- Gameplay checks listed in the slice notes still apply (mounted soldiers and archers driving a horse, shield blocking duration via `LivingEntityUseItemEvent.Start`, the NPC preview in the inventory screen).
