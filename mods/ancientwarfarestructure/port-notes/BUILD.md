# Build record: ancientwarfarestructure

Result: `:ancientwarfarestructure:build` passes (compileJava 0 errors, 0 warnings; jar and sourcesJar built). Not run in a game.

## Wired
- `AncientWarfareStructure`: `AwGates.load()`, `AwGates.registerEvents(modBus, container)` (entity type, vehicle `GateLookup` provider at common setup, gate config),
  then the single `REG.creativeTab(...)` (icon: wooden gate item), then `REG.register(modBus)`.
  Only one tab exists (`ancientwarfarestructure:main`): gate items reach it through the registrar's list.
- Client classes (`GateRenderClient`, `GateBoundsOverlay`) are `@EventBusSubscriber(value = Dist.CLIENT)` and load by themselves; no payloads, capabilities, attributes or screens exist in this module.
- `lang/en_us.json`: merged both lang fragments, removed the template `example_block` key (51 keys). `sounds.json` created from both fragments (6 events).

## Changed and why
- `GateProxyBlock.properties()` renamed `proxyProperties()`: a static method cannot hide the instance `BlockBehaviour.properties()`. Caller in `AwGates` updated.

## TODO(port) left
- None added by this step. Known gaps from the slice notes stay: gate control screen (sneak-right-click repacks), drawbridge swing collision/push, `GateControl.setOpener` unused.

## Runtime-only risks
- Registry names checked by grep: no duplicates within one registry (`gate_proxy` appears as block, block entity and item, different registries).
- Gate research recipes need research goal `ancientwarfare:construction` to exist in Core.
- `AwGates.ENTITY_TYPES` is a private DeferredRegister registered in `registerEvents`; it must be called before the constructor ends (it is).
- `EntityGate` spawn data (IEntityWithComplexSpawn) read order vs entity data not traced.
- `GateVehicleLink` is installed in `enqueueWork` at common setup; if the vehicle module's path finder is used before that it sees no provider.
