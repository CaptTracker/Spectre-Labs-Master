/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/gates/types/Gate.
 */
package io.github.capttracker.ancientwarfarestructure.gate.types;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfarestructure.AncientWarfareStructure;
import io.github.capttracker.ancientwarfarestructure.block.GateProxyBlock;
import io.github.capttracker.ancientwarfarestructure.block.GateProxyMode;
import io.github.capttracker.ancientwarfarestructure.block.entity.GateProxyBlockEntity;
import io.github.capttracker.ancientwarfarestructure.entity.EntityGate;
import io.github.capttracker.ancientwarfarestructure.gate.GateConfig;
import io.github.capttracker.ancientwarfarestructure.gate.IGateType;
import io.github.capttracker.ancientwarfarestructure.init.AwGates;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The base gate type (the vertical gate that lifts up) and the registry of the seven types by global id and by template
 * name. The sound events and the item are looked up lazily because {@code AwGates} needs these instances to register the
 * spawner items.
 *
 * <p>Collision is no longer the entity's bounding box (1.12.2 swapped it by reflection every tick). Every cell of the
 * gate holds a {@link GateProxyBlock} whose {@link GateProxyMode} this class sets: {@code CLOSED} when the gate has closed,
 * {@code OPEN} (and {@code LINTEL} in the top row, the half block that the original entity box left there) from the moment
 * it starts to open.
 */
public class Gate implements IGateType {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gate[] gateTypes = new Gate[16];
    private static final List<Gate> ALL = new ArrayList<>();

    public static final Gate BASIC_WOOD = new Gate(0, "gate_basic_wood", "_wood_1.png", () -> AwGates.WOODEN_GATE.get(), () -> SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, () -> AwGates.WOODEN_GATE_BREAK.get(), GateConfig::verticalWoodenMaxHealth).setName("gateBasicWood").setVariant(Variant.WOOD_BASIC);
    public static final Gate BASIC_IRON = new Gate(1, "gate_basic_iron", "_iron_1.png", () -> AwGates.IRON_GATE.get(), () -> SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, () -> AwGates.IRON_GATE_BREAK.get(), GateConfig::verticalIronMaxHealth).setName("gateBasicIron").setVariant(Variant.IRON_BASIC).setModel(1);

    public static final Gate SINGLE_WOOD = new GateSingle(4, "gate_single_wood", "_wood_1.png", () -> AwGates.WOODEN_GATE.get(), () -> SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, () -> AwGates.WOODEN_GATE_BREAK.get(), GateConfig::singleWoodMaxHealth).setName("gateSingleWood").setVariant(Variant.WOOD_SINGLE);
    public static final Gate SINGLE_IRON = new GateSingle(5, "gate_single_iron", "_iron_1.png", () -> AwGates.IRON_GATE.get(), () -> SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, () -> AwGates.IRON_GATE_BREAK.get(), GateConfig::singleIronMaxHealth).setName("gateSingleIron").setVariant(Variant.IRON_SINGLE).setModel(1);

    public static final Gate DOUBLE_WOOD = new GateDouble(8, "gate_double_wood", "_wood_1.png", () -> AwGates.WOODEN_GATE.get(), () -> SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, () -> AwGates.WOODEN_GATE_BREAK.get(), GateConfig::doubleWoodMaxHealth).setName("gateDoubleWood").setVariant(Variant.WOOD_DOUBLE);
    public static final Gate DOUBLE_IRON = new GateDouble(9, "gate_double_iron", "_iron_1.png", () -> AwGates.IRON_GATE.get(), () -> SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, () -> AwGates.IRON_GATE_BREAK.get(), GateConfig::doubleIronMaxHealth).setName("gateDoubleIron").setVariant(Variant.IRON_DOUBLE).setModel(1);

    public static final Gate DRAWBRIDGE = new GateRotatingBridge(12, "gate_drawbridge", "_bridge_wood_1.png", () -> AwGates.WOODEN_GATE.get(), () -> SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, () -> AwGates.WOODEN_GATE_BREAK.get(), GateConfig::drawbridgeMaxHealth);

    private static final HashMap<String, Integer> gateIDByName = new HashMap<>();

    static {
        // the names that structure templates store
        gateIDByName.put("gate.verticalWooden", 0);
        gateIDByName.put("gate.verticalIron", 1);
        gateIDByName.put("gate.singleWood", 4);
        gateIDByName.put("gate.singleIron", 5);
        gateIDByName.put("gate.doubleWood", 8);
        gateIDByName.put("gate.doubleIron", 9);
        gateIDByName.put("gate.drawbridge", 12);
    }

    public enum Variant {
        WOOD_BASIC, IRON_BASIC, WOOD_SINGLE, IRON_SINGLE, WOOD_DOUBLE, IRON_DOUBLE, WOOD_ROTATING
    }

    private final int globalID;
    private final String registryName;
    protected String displayName = "";
    protected Variant variant;
    private final IntSupplier maxHealth;
    private int modelType = 0;
    private final Supplier<SoundEvent> moveSound;
    private final Supplier<SoundEvent> hurtSound;
    private final Supplier<SoundEvent> breakSound;

    protected boolean canSoldierInteract = true;

    protected float moveSpeed = 0.5f * 0.05f; /// 1/2 block / second

    private final ResourceLocation textureLocation;

    public Gate(int id, String registryName, String textureLocation, Supplier<SoundEvent> moveSound, Supplier<SoundEvent> hurtSound, Supplier<SoundEvent> breakSound, IntSupplier maxHealth) {
        globalID = id;
        this.registryName = registryName;
        if (id >= 0 && id < gateTypes.length && gateTypes[id] == null) {
            gateTypes[id] = this;
            ALL.add(this);
        }
        this.textureLocation = Ids.of(AncientWarfareStructure.MOD_ID, "textures/entity/gate/gate" + textureLocation);
        this.moveSound = moveSound;
        this.hurtSound = hurtSound;
        this.breakSound = breakSound;
        this.maxHealth = maxHealth;
    }

    protected final Gate setName(String name) {
        displayName = name;
        return this;
    }

    protected final Gate setVariant(Variant variant) {
        this.variant = variant;
        return this;
    }

    protected final Gate setModel(int type) {
        modelType = type;
        return this;
    }

    public Variant getVariant() {
        return variant;
    }

    /** Whether this is one of the wooden gates (1.12.2: {@code isWood(getVariant())}). */
    public boolean isWood() {
        return switch (variant) {
            case WOOD_DOUBLE, WOOD_ROTATING, WOOD_SINGLE, WOOD_BASIC -> true;
            default -> false;
        };
    }

    public SoundEvent getMoveSound() {
        return moveSound.get();
    }

    public SoundEvent getHurtSound() {
        return hurtSound.get();
    }

    public SoundEvent getBreakSound() {
        return breakSound.get();
    }

    @Override
    public int getGlobalID() {
        return globalID;
    }

    @Override
    public int getModelType() {
        return modelType;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getRegistryName() {
        return registryName;
    }

    @Override
    public ItemStack getConstructingItem() {
        return new ItemStack(AwGates.gateItem(this));
    }

    @Override
    public int getMaxHealth() {
        return maxHealth.getAsInt();
    }

    @Override
    public float getMoveSpeed() {
        return moveSpeed;
    }

    @Override
    public ResourceLocation getTexture() {
        return textureLocation;
    }

    @Override
    public boolean canActivate(EntityGate gate, boolean open) {
        return true;
    }

    @Override
    public boolean canSoldierActivate() {
        return canSoldierInteract;
    }

    // ---- registry ----

    /** The seven gate types in global id order. */
    public static List<Gate> all() {
        return List.copyOf(ALL);
    }

    public static String getGateNameFor(EntityGate gate) {
        return getGateNameFor(gate.getGateType().getGlobalID());
    }

    private static String getGateNameFor(int id) {
        for (Map.Entry<String, Integer> entry : gateIDByName.entrySet()) {
            if (entry.getValue() == id) {
                return entry.getKey();
            }
        }
        return "gate.verticalWooden";
    }

    public static Gate getGateByName(String name) {
        if (gateIDByName.containsKey(name)) {
            return getGateByID(gateIDByName.get(name));
        }
        return BASIC_WOOD;
    }

    /** The gate of the id; the vertical wooden gate for an unknown id (the original returned null for the unused ids). */
    public static Gate getGateByID(int id) {
        if (id >= 0 && id < gateTypes.length && gateTypes[id] != null) {
            return gateTypes[id];
        }
        return BASIC_WOOD;
    }

    public static ItemStack getItemToConstruct(int type) {
        return getGateByID(type).getConstructingItem();
    }

    // ---- geometry helpers ----

    /** The axis the gate is long on (1.12.2: {@code wideOnXAxis}; a gate one block wide counts as Z). */
    public static Direction.Axis lengthAxis(EntityGate gate) {
        return gate.pos1.getX() != gate.pos2.getX() ? Direction.Axis.X : Direction.Axis.Z;
    }

    /** The volume of the closed gate. */
    protected static AABB volume(BlockPos min, BlockPos max) {
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    public void updateRenderBoundingBox(EntityGate gate) {
        if (gate.getRenderBoundingBox().getSize() == 0) {
            BlockPos min = BlockTools.getMin(gate.pos1, gate.pos2);
            BlockPos max = BlockTools.getMax(gate.pos1, gate.pos2);
            gate.setRenderBoundingBox(getRenderBoundingBox(gate, min, max));
        }
    }

    protected AABB getRenderBoundingBox(EntityGate gate, BlockPos min, BlockPos max) {
        return volume(min, max);
    }

    @Override
    public AABB getEntityBox(EntityGate gate) {
        BlockPos min = BlockTools.getMin(gate.pos1, gate.pos2);
        BlockPos max = BlockTools.getMax(gate.pos1, gate.pos2);
        if (gate.edgePosition > 0) {
            // the half block at the top of the volume is all that is left while the gate is open
            return new AABB(min.getX(), max.getY() + 0.5d, min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
        }
        return volume(min, max);
    }

    @Override
    public boolean arePointsValidPair(BlockPos pos1, BlockPos pos2) {
        return pos1.getX() == pos2.getX() || pos1.getZ() == pos2.getZ();
    }

    @Override
    public void setInitialBounds(EntityGate gate, BlockPos pos1, BlockPos pos2) {
        BlockPos min = BlockTools.getMin(pos1, pos2);
        BlockPos max = BlockTools.getMax(pos1, pos2);
        boolean wideOnXAxis = min.getX() != max.getX();
        float width = wideOnXAxis ? max.getX() - min.getX() + 1 : max.getZ() - min.getZ() + 1;
        float xOffset = wideOnXAxis ? width * 0.5f : 0.5f;
        float zOffset = wideOnXAxis ? 0.5f : width * 0.5f;
        gate.setPositions(min, max);
        gate.setRenderBoundingBox(getRenderBoundingBox(gate, min, max));
        gate.edgeMax = (float) max.getY() - min.getY() + 1;
        gate.setPos(min.getX() + xOffset, min.getY(), min.getZ() + zOffset);
    }

    // ---- proxies ----

    /** The mode that the proxy of the cell has in the gate's present state. */
    protected GateProxyMode modeForCell(EntityGate gate, BlockPos cell, BlockPos min, BlockPos max) {
        if (gate.isClosed()) {
            return GateProxyMode.CLOSED;
        }
        return cell.getY() == max.getY() ? GateProxyMode.LINTEL : GateProxyMode.OPEN;
    }

    /** Places the missing proxies of the gate and sets the mode of all of them (server only). */
    public void updateProxies(EntityGate gate) {
        if (gate.level().isClientSide) {
            return;
        }
        BlockPos min = BlockTools.getMin(gate.pos1, gate.pos2);
        BlockPos max = BlockTools.getMax(gate.pos1, gate.pos2);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            placeProxy(gate, pos, modeForCell(gate, pos, min, max));
        }
    }

    /**
     * Makes sure that every cell of the gate holds its proxy; run once after the gate entity was loaded (1.12.2:
     * {@code setRenderedTileIfNotPresent}, every tick until the render proxy was found).
     */
    public void restoreProxies(EntityGate gate) {
        updateProxies(gate);
    }

    /** Removes the proxies that belong to this gate (server only); the drawbridge also has some in its landing area. */
    public void removeProxies(EntityGate gate) {
        if (gate.level().isClientSide) {
            return;
        }
        removeProxiesBetween(gate, BlockTools.getMin(gate.pos1, gate.pos2), BlockTools.getMax(gate.pos1, gate.pos2), pos -> false);
    }

    /**
     * Places a proxy in the cell, or sets the mode of the one that is there, and links it to the gate. A block that is in
     * the way is dropped and replaced, as in 1.12.2. A cell in an unloaded chunk is skipped and the gate retries later.
     */
    protected final void placeProxy(EntityGate gate, BlockPos pos, GateProxyMode mode) {
        Level level = gate.level();
        if (!level.hasChunkAt(pos)) {
            gate.markProxiesDirty();
            return;
        }
        GateProxyBlock proxy = AwGates.GATE_PROXY.get();
        BlockState target = proxy.stateFor(lengthAxis(gate), mode);
        BlockState state = level.getBlockState(pos);
        if (!state.is(proxy)) {
            if (!state.isAir()) {
                Block.dropResources(state, level, pos, level.getBlockEntity(pos));
            }
            level.setBlock(pos, target, Block.UPDATE_ALL);
        } else if (state != target) {
            level.setBlock(pos, target, Block.UPDATE_CLIENTS);
        }
        if (level.getBlockEntity(pos) instanceof GateProxyBlockEntity proxyEntity) {
            proxyEntity.setGate(gate);
        }
    }

    protected final void removeProxiesBetween(EntityGate gate, BlockPos min, BlockPos max, Predicate<BlockPos> skip) {
        Level level = gate.level();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (skip.test(pos) || !level.hasChunkAt(pos) || !level.getBlockState(pos).is(AwGates.GATE_PROXY.get())) {
                continue;
            }
            // only this gate's proxies (1.12.2 removed any proxy in the way)
            if (level.getBlockEntity(pos) instanceof GateProxyBlockEntity proxyEntity
                    && proxyEntity.getGateId() != null && !proxyEntity.getGateId().equals(gate.getUUID())) {
                continue;
            }
            level.setBlock(pos.immutable(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void onGateStartOpen(EntityGate gate) {
        updateProxies(gate);
    }

    @Override
    public void onGateFinishOpen(EntityGate gate) {
        // overridden in subclass
    }

    @Override
    public void onGateStartClose(EntityGate gate) {
        // overridden in subclass
    }

    @Override
    public void onGateFinishClose(EntityGate gate) {
        updateProxies(gate);
    }

    /**
     * Creates a fully set up gate, or empty if a block is in the way (the gate is not added to the level, the caller does
     * that). Its proxies are placed already.
     */
    public static Optional<EntityGate> constructGate(Level level, BlockPos pos1, BlockPos pos2, Gate type, Direction facing, Owner owner) {
        BlockPos min = BlockTools.getMin(pos1, pos2);
        BlockPos max = BlockTools.getMax(pos1, pos2);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isEmptyBlock(pos)) {
                LOGGER.info("could not create gate for non-air block at: {},{},{} block: {}", pos.getX(), pos.getY(), pos.getZ(), level.getBlockState(pos).getBlock());
                return Optional.empty();
            }
        }

        Direction rotatedFacing = facing;
        if (pos1.getX() == pos2.getX()) {
            if (facing == Direction.SOUTH || facing == Direction.NORTH) {
                rotatedFacing = facing.getClockWise();
            }
        } else if (pos1.getZ() == pos2.getZ() && (facing == Direction.EAST || facing == Direction.WEST)) {
            rotatedFacing = facing.getClockWise();
        }

        EntityGate ent = new EntityGate(AwGates.GATE.get(), level);
        ent.setGateType(type);
        ent.gateOrientation = rotatedFacing;
        ent.setOwner(owner);
        type.setInitialBounds(ent, pos1, pos2);
        type.onGateFinishClose(ent);
        return Optional.of(ent);
    }
}
