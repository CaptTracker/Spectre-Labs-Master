# Ancient Warfare Structures (gates): ported API

Written by the port workflow. What each finished slice offers to code that builds on it.

## structure-gates

Ported all 7 gate types (vertical, single, double wood and iron, drawbridge) as 7 spawner items, so gates remain craftable via 7 converted research recipes. The gate entity keeps state, animation, health and ownership. Collision is the proxy block's state-driven VoxelShapes. Includes the proxy block and block entity, an EntityRenderer with baked box models, a gate config, and the Vehicles GateLookup provider and IGateEntity. Assets, lang and sounds fragment are in place. Nothing compiled. Gate control GUI and drawbridge swing collision are not ported.

**API**

- init.AwGates: GATE (entity type), GATE_PROXY, GATE_PROXY_BE, GATE_ITEMS, GATE_BOUNDS, gateItem(Gate), load(), registerEvents(modBus[, container])
- gate.types.Gate: all(), getGateByID(int), getGateByName(String), getGateNameFor(EntityGate), constructGate(Level, pos1, pos2, Gate, Direction, Owner), getItemToConstruct(int), BASIC_WOOD..DRAWBRIDGE
- entity.EntityGate: activateGate(), repackEntity(), isClosed(), getHealth()/setHealth(), getOwner()/setOwner(), getGateType(), pos1/pos2, implements IOwnable and vehicle IGateEntity
- entity.GateControl.setOpener(Opener): install the gate control screen opener
- block.GateProxyBlock (AXIS, MODE, stateFor(axis, mode)) and block.GateProxyMode
- block.entity.GateProxyBlockEntity.getGate()
- item.GateSpawnerItem (getGate()) and item.GateBounds component record
- gate.GateConfig: per-type max health options, register(ModContainer)
- gate.GateVehicleLink: GateLookup.Provider installed by AwGates at common setup

**Not ported**

- Gate control menu and screens (ContainerGateControl, GuiGateControl, GuiGateControlCreative) and the creative owner editor; GateControl is the hook, default action is repack
- Drawbridge swing collision and the push of entities inside its box (applyEntityCollision/onCollideWithPlayer)
- DualBoundingBox, RotateBoundingBox, RenderGateInvisible, GateProxyRenderer TESR, World.MAX_ENTITY_RADIUS hack, reflective bounding box swap
- TemplateRuleGates (template slice); only Gate.getGateByName/getGateNameFor/constructGate are provided for it
- Orphan blockstates/structure/gate_spawner.json and the nonexistent gate_wood_1_damaged_2.png

