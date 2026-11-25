/*
 * Ancient Warfare Structures - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: structure/entity/EntityGate (and DualBoundingBox / RotateBoundingBox, which are gone:
 * collision is the shapes of the gate's proxy blocks now).
 */
package io.github.capttracker.ancientwarfarestructure.entity;

import io.github.capttracker.ancientwarfare.owner.IOwnable;
import io.github.capttracker.ancientwarfare.owner.Owner;
import io.github.capttracker.ancientwarfare.util.BlockTools;
import io.github.capttracker.ancientwarfarestructure.gate.types.Gate;
import io.github.capttracker.ancientwarfarestructure.gate.types.GateRotatingBridge;
import io.github.capttracker.ancientwarfarevehicle.entity.IGateEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

import javax.annotation.Nullable;

/**
 * The gate: one entity for all seven gate types. It owns the state (corners, type, opening progress, health, owner), runs
 * the opening and closing animation that the renderer draws, takes damage and keeps the gate's proxy blocks in step. It
 * is not a collider: the proxy blocks are ({@code GateProxyBlock}, mode set by {@link Gate}). What is left of the
 * entity's own box is for hits, picking and area queries.
 *
 * <p>Differences from 1.12.2:
 * <ul>
 *   <li>The bounding box is not swapped by reflection; {@link #makeBoundingBox} asks the gate type.</li>
 *   <li>The entity itself is drawn (by {@code GateRenderer}); the 1.12.2 TESR on a render proxy block is gone.</li>
 *   <li>Health is synched entity data instead of a {@code PacketEntity}; the opening state still travels as entity event
 *   -1/1 plus the spawn data, and client and server still animate on their own.</li>
 *   <li>{@code World.MAX_ENTITY_RADIUS} has no equivalent. 1.21 finds an entity through the section of its position, so a
 *   query far from the centre of a wide gate can miss it; own queries inflate their search box.</li>
 *   <li>The drawbridge pushes the entities inside its box down from {@code tick} ({@code pushEntitiesDown}), since the gate
 *   is not a collider and has no {@code applyEntityCollision}.</li>
 * </ul>
 */
public class EntityGate extends Entity implements IEntityWithComplexSpawn, IOwnable, IGateEntity {
    private static final String HEALTH_TAG = "health";
    private static final EntityDataAccessor<Integer> DATA_HEALTH = SynchedEntityData.defineId(EntityGate.class, EntityDataSerializers.INT);

    @Nullable
    public BlockPos pos1;
    @Nullable
    public BlockPos pos2;

    public float edgePosition; // the bottom/opening edge of the gate (closed should correspond to pos1)
    public float edgeMax; // the 'fully extended' position of the gate

    public float openingSpeed = 0.f; // calculated speed of the opening gate -- used during animation

    public Gate gateType = Gate.BASIC_WOOD;

    private Owner owner = Owner.EMPTY;
    public int hurtAnimationTicks = 0;
    private byte gateStatus = 0;
    public Direction gateOrientation = Direction.SOUTH;
    private int hurtInvulTicks = 0;

    private boolean wasPoweredA = false;
    private boolean wasPoweredB = false;
    private AABB renderBoundingBox = new AABB(0, 0, 0, 0, 0, 0);

    private int soundTicks = 15;
    private int clientHealthSeen = 0;
    /** The proxy blocks are checked on the first tick after loading (and again while some chunk of them was not loaded). */
    private boolean proxiesPending = true;
    private int proxyRetryTicks = 0;

    public EntityGate(EntityType<?> type, Level level) {
        super(type, level);
        noCulling = true; // 1.12.2: ignoreFrustumCheck
        blocksBuilding = true; // 1.12.2: preventEntitySpawning
    }

    // ---- owner ----

    @Override
    public void setOwner(Owner owner) {
        this.owner = owner;
    }

    @Override
    public void setOwner(Player player) {
        owner = new Owner(player);
    }

    @Override
    public Owner getOwner() {
        return owner;
    }

    @Override
    public boolean isOwner(Player player) {
        return owner.isOwnerOrSameTeamOrFriend(player);
    }

    @Nullable
    @Override
    public PlayerTeam getTeam() {
        return level().getScoreboard().getPlayersTeam(owner.getName());
    }

    // ---- type and bounds ----

    public void setPositions(BlockPos pos1, BlockPos pos2) {
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    public Gate getGateType() {
        return gateType;
    }

    public void setGateType(Gate type) {
        gateType = type;
        entityData.set(DATA_HEALTH, type.getMaxHealth());
    }

    public AABB getRenderBoundingBox() {
        return renderBoundingBox;
    }

    public void setRenderBoundingBox(AABB renderBoundingBox) {
        this.renderBoundingBox = renderBoundingBox;
    }

    /** Takes the box from the gate type again; called when the opening state changed. */
    private void refreshBox() {
        if (pos1 != null && pos2 != null) {
            setBoundingBox(makeBoundingBox());
        }
    }

    /** Runs from the constructor of Entity (via setPos) before the fields are set, so everything is checked. */
    @Override
    protected AABB makeBoundingBox() {
        if (pos1 == null || pos2 == null || gateType == null) {
            return super.makeBoundingBox();
        }
        return gateType.getEntityBox(this);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return renderBoundingBox.getSize() > 0 ? renderBoundingBox : super.getBoundingBoxForCulling();
    }

    /** 1.12.2 drew gates up to 256 blocks away (the TESR's maximum render distance). */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 65536.0D;
    }

    /** The light is taken at the top of the gate, as {@code getBrightnessForRender} did. */
    @Override
    public Vec3 getLightProbePosition(float partialTicks) {
        if (pos1 == null || pos2 == null) {
            return super.getLightProbePosition(partialTicks);
        }
        int y = Math.max(Mth.floor(getY()), Math.max(pos1.getY(), pos2.getY()));
        return new Vec3(getX(), y + 0.5D, getZ());
    }

    // ---- synched data and saving ----

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HEALTH, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_HEALTH.equals(key) && level().isClientSide) {
            int health = getHealth();
            if (health < clientHealthSeen) {
                hurtAnimationTicks = 20;
            }
            clientHealthSeen = health;
        }
    }

    public int getHealth() {
        return entityData.get(DATA_HEALTH);
    }

    public void setHealth(int val) {
        int newHealth = Math.max(val, 0);
        if (newHealth < getHealth()) {
            hurtAnimationTicks = 20;
        }
        entityData.set(DATA_HEALTH, newHealth);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (!tag.contains("pos1") || !tag.contains("pos2")) {
            return; // not a gate that was set up; it removes itself on its first tick
        }
        setPositions(BlockPos.of(tag.getLong("pos1")), BlockPos.of(tag.getLong("pos2")));
        gateType = Gate.getGateByID(tag.getInt("type"));
        owner = Owner.deserializeFromNBT(tag);
        edgePosition = tag.getFloat("edge");
        edgeMax = tag.getFloat("edgeMax");
        // set directly: setHealth would start the hurt animation for a gate that loads with less than full health
        entityData.set(DATA_HEALTH, tag.getInt(HEALTH_TAG));
        gateStatus = tag.getByte("status");
        gateOrientation = Direction.from3DDataValue(tag.getByte("orient"));
        wasPoweredA = tag.getBoolean("power");
        wasPoweredB = tag.getBoolean("power2");
        realignEntityPosition();
        gateType.updateRenderBoundingBox(this);
        refreshBox();
    }

    private void realignEntityPosition() {
        BlockPos min = BlockTools.getMin(pos1, pos2);
        BlockPos max = BlockTools.getMax(pos1, pos2);
        setPos(min.getX() + ((max.getX() - min.getX() + 1) / 2d), min.getY(), min.getZ() + ((max.getZ() - min.getZ() + 1) / 2d));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (pos1 == null || pos2 == null) {
            return;
        }
        tag.putLong("pos1", pos1.asLong());
        tag.putLong("pos2", pos2.asLong());
        tag.putInt("type", gateType.getGlobalID());
        owner.serializeToNBT(tag);
        tag.putFloat("edge", edgePosition);
        tag.putFloat("edgeMax", edgeMax);
        tag.putInt(HEALTH_TAG, getHealth());
        tag.putByte("status", gateStatus);
        tag.putByte("orient", (byte) gateOrientation.get3DDataValue());
        tag.putBoolean("power", wasPoweredA);
        tag.putBoolean("power2", wasPoweredB);
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf data) {
        data.writeLong(pos1 == null ? 0L : pos1.asLong());
        data.writeLong(pos2 == null ? 0L : pos2.asLong());
        data.writeInt(gateType.getGlobalID());
        data.writeFloat(edgePosition);
        data.writeFloat(edgeMax);
        data.writeByte(gateStatus);
        data.writeByte(gateOrientation.get3DDataValue());
        data.writeInt(getHealth());
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf data) {
        setPositions(BlockPos.of(data.readLong()), BlockPos.of(data.readLong()));
        gateType = Gate.getGateByID(data.readInt());
        edgePosition = data.readFloat();
        edgeMax = data.readFloat();
        gateStatus = data.readByte();
        gateOrientation = Direction.from3DDataValue(data.readByte());
        int health = data.readInt();
        clientHealthSeen = health;
        entityData.set(DATA_HEALTH, health);
        gateType.updateRenderBoundingBox(this);
        refreshBox();
    }

    // ---- state ----

    public boolean isClosed() {
        return gateStatus == 0 && edgePosition == 0;
    }

    /** -1 closing, 0 idle, 1 opening. */
    public byte getOpeningStatus() {
        return gateStatus;
    }

    /** Makes the gate check its proxy blocks again soon (a cell was in a chunk that is not loaded). */
    public void markProxiesDirty() {
        proxiesPending = true;
    }

    @Override
    public ItemStack getPickResult() {
        return gateType.getConstructingItem();
    }

    /** Drops the gate as its spawner item and removes it (server only). */
    public void repackEntity() {
        if (level().isClientSide || isRemoved()) {
            return;
        }
        ItemEntity item = new ItemEntity(level(), getX(), getY() + 0.5d, getZ(), gateType.getConstructingItem());
        level().addFreshEntity(item);
        discard();
    }

    /**
     * Only a real removal (killed, repacked) takes the proxies away and plays the break sound; 1.12.2 did this in
     * {@code setDead}, which an unloading chunk never called.
     */
    @Override
    public void remove(RemovalReason reason) {
        boolean destroy = reason.shouldDestroy() && !isRemoved();
        super.remove(reason);
        if (destroy && !level().isClientSide && pos1 != null && pos2 != null) {
            gateType.removeProxies(this);
            level().playSound(null, getX(), getY(), getZ(), gateType.getBreakSound(), SoundSource.NEUTRAL, 1, 1);
        }
    }

    private void setOpeningStatus(byte op) {
        boolean wasClosed = isClosed();
        gateStatus = op;
        if (!level().isClientSide) {
            level().broadcastEntityEvent(this, op);
            if (op == 1 && wasClosed) {
                gateType.onGateStartOpen(this);
            }
        }
        if (op == -1) {
            gateType.onGateStartClose(this);
        }
    }

    /** The opening state arrives as an entity event, as the 1.12.2 status byte did. */
    @Override
    public void handleEntityEvent(byte id) {
        if (id == -1 || id == 0 || id == 1) {
            setOpeningStatus(id);
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void activateGate() {
        tryActivateGate();
    }

    /** @return false if the gate could not move (for example a drawbridge whose landing space is blocked) */
    public boolean tryActivateGate() {
        if (gateStatus == 1 && gateType.canActivate(this, false)) {
            setOpeningStatus((byte) -1);
        } else if (gateStatus == -1 && gateType.canActivate(this, true)) {
            setOpeningStatus((byte) 1);
        } else if (edgePosition == 0 && gateType.canActivate(this, true)) {
            setOpeningStatus((byte) 1);
        } else if (edgePosition != 0 && gateType.canActivate(this, false)) { // gate is already open/opening, set to closing
            setOpeningStatus((byte) -1);
        } else {
            return false;
        }
        return true;
    }

    // ---- IGateEntity (the part the vehicles use) ----

    @Override
    public float getEdgePosition() {
        return edgePosition;
    }

    @Override
    public boolean canSoldierActivate() {
        return gateType.canSoldierActivate();
    }

    @Override
    public String getGateVariantName() {
        return gateType.getVariant().name().toLowerCase(java.util.Locale.ROOT);
    }

    // ---- interaction ----

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        boolean gateOwner = owner.isOwnerOrSameTeamOrFriend(player);
        boolean creative = player.getAbilities().instabuild;
        if (creative || owner == Owner.EMPTY || gateOwner) {
            if (player.isShiftKeyDown()) {
                if (player instanceof ServerPlayer serverPlayer) {
                    if (creative) {
                        GateControl.open(serverPlayer, this, true);
                    } else if (gateOwner) {
                        GateControl.open(serverPlayer, this, false);
                    }
                }
            } else if (!tryActivateGate() && isClosed()) {
                // Port addition: 1.12.2 gave no hint why a drawbridge stayed shut.
                player.displayClientMessage(Component.translatable("guistrings.gate.landing_blocked"), true);
            }
            return InteractionResult.CONSUME;
        }
        player.sendSystemMessage(Component.translatable("guistrings.gate.use_error"));
        return InteractionResult.FAIL;
    }

    // ---- tick ----

    @Override
    public void tick() {
        if (pos1 == null || pos2 == null) {
            // created without Gate.constructGate (for example by a command): it has no volume
            if (!level().isClientSide) {
                discard();
            }
            return;
        }
        super.tick();
        boolean server = !level().isClientSide;
        float prevEdge = edgePosition;
        if (hurtInvulTicks > 0) {
            hurtInvulTicks--;
        }
        if (server) {
            checkForPowerUpdates();
            if (proxiesPending && proxyRetryTicks-- <= 0) {
                proxiesPending = false;
                proxyRetryTicks = 20;
                gateType.restoreProxies(this);
            }
        }
        if (hurtAnimationTicks > 0) {
            hurtAnimationTicks--;
        }
        if (gateStatus == 1) {
            edgePosition += gateType.getMoveSpeed();
            if (soundTicks == 15) {
                playGateMoveSound();
            }
            soundTicks++;
            if (edgePosition >= edgeMax) {
                edgePosition = edgeMax;
                gateStatus = 0;
                soundTicks = 15;
                if (server) {
                    gateType.onGateFinishOpen(this);
                }
            }
        } else if (gateStatus == -1) {
            edgePosition -= gateType.getMoveSpeed();
            if (soundTicks == 15) {
                playGateMoveSound();
            }
            soundTicks++;
            if (edgePosition <= 0) {
                edgePosition = 0;
                gateStatus = 0;
                soundTicks = 15;
                if (server) {
                    gateType.onGateFinishClose(this);
                }
            }
        }
        if (server && gateStatus != 0 && gateType instanceof GateRotatingBridge) {
            pushEntitiesDown();
        }
        openingSpeed = prevEdge - edgePosition;
        if (prevEdge != edgePosition) {
            refreshBox();
        }
    }

    /** 1.12.2 applyEntityCollision: a moving drawbridge pushes what is inside its box down. */
    private void pushEntitiesDown() {
        AABB box = getBoundingBox().inflate(0.1D);
        // search wider than the box: a query far from a wide gate's centre can miss it (see class comment)
        for (Entity e : level().getEntitiesOfClass(Entity.class, box.inflate(2.0D), other -> other != this && !(other instanceof EntityGate))) {
            if (e.getBoundingBox().intersects(box)) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, -gateStatus * 0.5D, 0));
                e.hasImpulse = true;
                e.hurtMarked = true; // sends the velocity to players
            }
        }
    }

    /** The sound is played by the server only (a client-side playSound with no player would not be heard in 1.21). */
    private void playGateMoveSound() {
        if (!level().isClientSide && pos1 != null) {
            level().playSound((Player) null, pos1, gateType.getMoveSound(), SoundSource.AMBIENT, 2, 1);
        }
        soundTicks = 0;
    }

    private void checkForPowerUpdates() {
        int y = Math.min(pos2.getY(), pos1.getY());
        BlockPos posA = new BlockPos(pos1.getX(), y, pos1.getZ());
        BlockPos posB = new BlockPos(pos2.getX(), y, pos2.getZ());
        if (!level().hasChunkAt(posA) || !level().hasChunkAt(posB)) {
            return;
        }
        boolean activate = false;
        boolean foundPowerA = level().getBestNeighborSignal(posA) > 0;
        boolean foundPowerB = level().getBestNeighborSignal(posB) > 0;
        if (foundPowerA && !wasPoweredA) {
            activate = true;
        }
        if (foundPowerB && !wasPoweredB) {
            activate = true;
        }
        wasPoweredA = foundPowerA;
        wasPoweredB = foundPowerB;
        if (activate) {
            activateGate();
        }
    }

    // ---- damage ----

    private boolean isInsensitiveTo(DamageSource source) {
        return source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.FALL) || source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.STARVE);
    }

    @Override
    public boolean hurt(DamageSource damageSource, float amount) {
        if (isInsensitiveTo(damageSource) || amount < 0) {
            return false;
        }
        if (level().isClientSide) {
            return true;
        }
        if (!damageSource.is(DamageTypeTags.IS_EXPLOSION)) {
            if (hurtInvulTicks > 0) {
                return false;
            }
            hurtInvulTicks = 10;
        } else {
            amount *= 10;
        }

        playSound(gateType.getHurtSound(), 1, 1);
        Entity direct = damageSource.getDirectEntity();
        if (direct instanceof AbstractArrow) { // ignore arrow dmg
            return false;
        }
        if (direct instanceof LivingEntity attacker) {
            // the direct entity, not the true source: ranged attacks and vehicles must not get the melee reduction
            ItemStack held = attacker.getMainHandItem();
            if (!held.isEmpty()) {
                Item heldItem = held.getItem();
                if (gateType.isWood()) { // wooden gates: half dmg for an axe, 1/4 for anything else
                    amount = heldItem instanceof AxeItem ? amount / 2 : amount / 4;
                } else if (heldItem instanceof PickaxeItem pickaxe) { // iron gates: half for a diamond pickaxe, 1/3 for any other
                    // 1.12.2 threw for a modded tool material (ToolMaterial.valueOf); comparing the tier does not
                    amount = pickaxe.getTier() == Tiers.DIAMOND ? amount / 2 : amount / 3;
                } else { // anything but a pickaxe
                    amount = amount / 4;
                }
            }
        }
        setHealth((int) (getHealth() - amount));

        if (getHealth() <= 0) {
            kill();
        }
        return !isRemoved();
    }

    // ---- an entity that does not move, collide or travel ----

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public float getPickRadius() {
        return 0.1F; // 1.12.2: collisionBorderSize
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level oldLevel, Level newLevel) {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public void push(double x, double y, double z) {
        // never moves
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false;
    }
}
