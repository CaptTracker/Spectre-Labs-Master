# Build record (ancientwarfare Core)

`:ancientwarfare:compileJava` and `:ancientwarfare:build` pass (Java 21, NeoForge 21.1.266). The first compile of the five uncompiled
slices found only three problems; everything else the slices had written was already consistent.

## Wired (from the slice notes)

- `AncientWarfareCore`: removed the `RESEARCH_STATION` field and the `StationBlock` import (`AwCoreStations` registers that name;
  `StationBlock` itself stays as the base of the engineering bench). `INFO_TOOL` / `MANUAL` are now `DeferredItem<InfoToolItem>` /
  `DeferredItem<ManualItem>`. Quills and hammers use `QuillItem` / `HammerItem` (`toolProps` deleted). Constructor now calls
  `AwCoreStations`, `AwCoreCrafting`, `AwCoreScreens`, `AwCoreMisc` `.load()` + `.registerEvents(modBus)` and
  `AWCoreStatics.register(modBus, container)` before `REG.register(modBus)`.
- `AwNetwork.register`: added `CoreMiscPayloads.registerPayloads(registrar)`. No duplicate payload ids (menu_data, research_sync,
  item_alt_key, item_scroll, manual_reload, clipboard).
- `AwMenu`: added `setRefreshListener(Runnable)` (public final) and `refreshGui()` (protected final).
- `ResearchBookItem.use`: opens `ResearchBookMenu.open(serverPlayer)`; the chat listing (`listResearch`) and its imports were removed.
- Client: deleted `client/AwClient`, `client/PanelScreen`, `client/BackpackScreen` (all superseded by `CoreScreensClient`, which registers
  backpack, engineering bench, research bench, research book and info tool screens exactly once).
- Lang: merged the 5 `*.lang.json` into `en_us.json` (47 keys added, existing keys kept; the template `example_block` key was not present).
  One conflict: `guistrings.research.research_time` is `%s` in the file already there and `%d` in core-screens; the existing `%s` was kept
  (works for the int argument too). There were no `.sounds.json` fragments, so no `sounds.json` was created.
- `@EventBusSubscriber(... bus = Bus.MOD)` in `CoreMiscClient` and `CoreScreensClient` lost the deprecated `bus` argument (NeoForge picks
  the bus by event type); `InputHandler` already had none.

## Fixed to compile

1. `crafting/RecipeResourceLocation`: the pattern `"([a-z_]+)\|(.+:.+)"` had an illegal Java escape; now `"([a-z_]+)[|](.+:.+)"`.
2. `item/HammerItem`: `PistonHeadBlock` import moved to `net.minecraft.world.level.block.piston`.

## Left as is / TODO(port)

- `AwCoreCrafting` keeps two private `DeferredRegister`s (recipe types, recipe serializers) because `ModRegistrar` has no recipe registries.
  The library was not touched (other modules build in parallel). When the library gains them, move the two registers and bump
  `libs/capttrackerlib` `mod_version`.
- Deprecation warning left: `BlockTools.getHighestSectionPosition()` (ChunkAccess), removal-marked in 1.21.1; works but should move to
  `getHighestFilledSectionIndex()` based code.
- Notes' open items that are not wiring: the research recipe converter for other modules, `SlotResearchCrafting.onTake` trimmed-input
  indexing (unused by the bench), `InventoryTools.removeItem` over-removal across slots, items of other modules that the loot injection
  tables and manual name (`ancientwarfarenpc:*`, ...) must exist under those names.

## Runtime-only risks (not exercised, runServer/runClient not run)

- Registration order: the `Aw*` init classes register into `REG` from the constructor, so the research station block item appears in the
  creative tab after the items declared in `AncientWarfareCore` (tab content is registration order). Names are unique (checked by grep:
  blocks engineering_station/research_station, block entities engineering_station/research_station, menus backpack/engineering_station/
  research_station/research_book/info_tool, components researcher/research_goal/info_tool_mode/hammer_work_mode, recipe type research,
  serializers research_recipe/shapeless_research_recipe).
- The static fields of the init classes use `AncientWarfareCore.REG`; they are touched from the `AncientWarfareCore` constructor, after
  its own static init, so no class-init cycle. Anything that reads `AwCoreStations.*` from `AncientWarfareCore` statics would break that.
- Menu type `.get()` calls inside menu constructors are fine only after registries fire; none run at class load.
- Client-only code: no class outside `client/` imports `net.minecraft.client`. Client hooks are installed by the `Dist.CLIENT`
  subscribers (`CoreScreensClient`, `CoreMiscClient`, `InputHandler`) via setters (`ManualItem.setClientOpener`,
  `CoreMiscPayloads.setClientHandler`); on a dedicated server those stay null and the payload handlers that use them must be reached only
  from the client (they are play-to-client payloads).
- Capabilities: `AwCoreStations.registerCapabilities` (research bench item handler + torque) and `AncientWarfareCore.registerCapabilities`
  (backpack `ComponentItemHandler`) are on the mod bus. `AwCapabilities.TORQUE` must be created before the event fires (static field, fine).
  The engineering station block entity registers no capability.
- `AWCoreStatics.register` (common config) is called in the constructor; `IConfigScreenFactory` is set in client setup.
- `RegisterClientReloadListenersEvent` is used by both `CoreMiscClient` (adds `RegistryLoader.CLIENT`) and `CoreScreensClient` (registers the
  manual parser); the loader must be added to the event before the parser is used. Both run in the same event, parser registration only
  adds to a table, so order does not matter, but this is untested.
- Data: loot modifiers name items `ancientwarfare:iron_spear` etc. of other modules; an unknown item logs an error at datapack load.
