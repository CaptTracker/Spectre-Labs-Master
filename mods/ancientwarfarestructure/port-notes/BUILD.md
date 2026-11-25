# Build record: ancientwarfarestructure

Result: `:ancientwarfarestructure:build` passes (compileJava 0 errors, 0 warnings; jar and sourcesJar built). Not run in a game.

## Wired
- `AncientWarfareStructure`: `AwGates.load()`, `AwGates.registerEvents(modBus, container)` (entity type, vehicle `GateLookup` provider at common setup, gate config),
  `AwDecor.load()`, `AwDecor.registerEvents(modBus)` (creative tab contents), then the single `REG.creativeTab(...)` (icon: default fire pit item), then `REG.register(modBus)`.
  Only one tab exists (`ancientwarfarestructure:main`): gate items reach it through the registrar's list, decor items through `AwDecor`'s `BuildCreativeModeTabContentsEvent` listener.
- Client classes (`GateRenderClient`, `DecorRenderClient`, `GateBoundsOverlay`) are `@EventBusSubscriber(value = Dist.CLIENT)` and load by themselves; no payloads, capabilities, attributes or screens exist in this module.
- `lang/en_us.json`: merged both lang fragments, removed the template `example_block` key (51 keys). `sounds.json` created from both fragments (6 events).

## Changed and why
- `GateProxyBlock.properties()` renamed `proxyProperties()`: a static method cannot hide the instance `BlockBehaviour.properties()`. Caller in `AwGates` updated.
- `TotemPartBlock`: `new ItemStack(DeferredItem)` was ambiguous (ItemLike vs Holder); now `.get()`.
- `@EventBusSubscriber(bus = Bus.MOD)` dropped in `DecorRenderClient` and `GateRenderClient` (deprecated for removal; mod-bus events are routed automatically in NeoForge 21.1).

## TODO(port) left
- None added by this step. Known gaps from the slice notes stay: gate control screen (sneak-right-click repacks), drawbridge swing collision/push, `GateControl.setOpener` unused.

## Runtime-only risks
- Registry names checked by grep: no duplicates within one registry (`gate_proxy` and `totem_part` appear as block plus block entity, and `gate_proxy` also as item, different registries).
- `COIN_STACK_SOUND` (`DeferredSoundType`) uses the sound holders in a static initialiser; fine as long as the sounds are registered before a coin stack sound plays (they are, via `REG.sounds`).
- Gate research recipes need research goal `ancientwarfare:construction` to exist in Core; the 4 coin stack recipes need `ancientwarfarenpc` (`neoforge:conditions` mod_loaded) and `ancientwarfarenpc:coin_<metal>` items; unverified that the condition JSON syntax is accepted by NeoForge 21.1.266.
- `AwGates.ENTITY_TYPES` is a private DeferredRegister registered in `registerEvents`; it must be called before the constructor ends (it is).
- `EntityGate` spawn data (IEntityWithComplexSpawn) read order vs entity data not traced.
- Blockstates (totem 128 combos), item model predicates (`size`, `lit`) and the 4 recipe/tag data files were not validated by data loading; first client/server start will show model or recipe parse errors if any.
- `GateVehicleLink` is installed in `enqueueWork` at common setup; if the vehicle module's path finder is used before that it sees no provider.
