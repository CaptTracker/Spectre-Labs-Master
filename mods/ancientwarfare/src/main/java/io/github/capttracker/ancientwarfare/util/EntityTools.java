/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/EntityTools.
 */
package io.github.capttracker.ancientwarfare.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Entity helpers. Much of the original is vanilla now: use {@code Level#getEntitiesOfClass} for area
 * queries and {@code Level#getNearestPlayer} for the closest player.
 */
public final class EntityTools {
    private EntityTools() {}

    /**
     * Applies entity data from a spawn NBT tag before and after the entity joins the world. The NPC slice
     * registers one for faction NPCs (the original special-cased {@code NpcFaction} and its "factionName" tag).
     */
    public interface SpawnDataHandler {
        /** Called first, before the generic NBT merge, so data like the faction name is set up early. */
        void apply(Entity entity, CompoundTag spawnData);
    }

    private static final List<SpawnDataHandler> SPAWN_DATA_HANDLERS = new CopyOnWriteArrayList<>();

    public static void registerSpawnDataHandler(SpawnDataHandler handler) {
        SPAWN_DATA_HANDLERS.add(handler);
    }

    /** The hand holding the item, main hand first, or null if neither does. */
    @Nullable
    public static InteractionHand getHandHoldingItem(LivingEntity entity, Item item) {
        if (entity.getMainHandItem().getItem() == item) {
            return InteractionHand.MAIN_HAND;
        } else if (entity.getOffhandItem().getItem() == item) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    /** The stack in either hand whose item is an instance of one of the classes (checked in order, main hand first). */
    public static ItemStack getItemFromEitherHand(Player player, Class<?>... itemClasses) {
        for (Class<?> itemClass : itemClasses) {
            if (itemClass.isInstance(player.getMainHandItem().getItem())) {
                return player.getMainHandItem();
            } else if (itemClass.isInstance(player.getOffhandItem().getItem())) {
                return player.getOffhandItem();
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Translation key of an entity type, {@code entity.<namespace>.<path>}. (1.12.2 inserted the NPC mod id
     * and the entity name; 1.21 keys are derived from the registry name.)
     */
    public static String getUnlocName(ResourceLocation registryName) {
        return "entity." + registryName.getNamespace() + "." + registryName.getPath().replace('/', '.');
    }

    public static String getUnlocName(String resourceLocation) {
        return getUnlocName(ResourceLocation.parse(resourceLocation));
    }

    /** Entities of the class inside the box spanned by the two block positions (both inclusive). */
    public static <T extends Entity> List<T> getEntitiesWithinBounds(Level world, Class<? extends T> clazz, BlockPos p1, BlockPos p2) {
        AABB bb = new AABB(p1.getX(), p1.getY(), p1.getZ(), p2.getX() + 1, p2.getY() + 1, p2.getZ() + 1);
        return new ArrayList<>(world.getEntitiesOfClass(clazz, bb));
    }

    /**
     * Spawns an entity of the registered type at the block position, merging the NBT over its own data.
     * Does nothing if the entity type does not exist (for example a missing mod).
     */
    @Nullable
    public static Entity spawnEntity(ServerLevel world, ResourceLocation entity, CompoundTag entityNBT, BlockPos pos, String... tags) {
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entity);
        if (type.isEmpty()) {
            return null;
        }
        Entity e = type.get().create(world);
        if (e == null) {
            return null;
        }
        e.moveTo(pos.getX() + 0.5d, pos.getY(), pos.getZ() + 0.5d, world.random.nextFloat() * 360, 0);
        if (e instanceof Mob mob) {
            mob.finalizeSpawn(world, world.getCurrentDifficultyAt(e.blockPosition()), MobSpawnType.STRUCTURE, null);
            mob.spawnAnim();
        }
        setDataFromTag(e, entityNBT); // some data needs to be set before spawning the entity in the world (like factionName)
        if (tags.length > 0) {
            e.getTags().addAll(Arrays.asList(tags));
        }
        world.addFreshEntity(e);
        setDataFromTag(e, entityNBT); // and some data needs to be set after the initial spawn logic has run
        return e;
    }

    private static void setDataFromTag(Entity e, CompoundTag entityNBT) {
        for (SpawnDataHandler handler : SPAWN_DATA_HANDLERS) {
            handler.apply(e, entityNBT);
        }
        CompoundTag temp = new CompoundTag();
        e.saveWithoutId(temp);
        temp.merge(entityNBT);
        e.load(temp);
    }

    /** The first player closer than {@code maxSqDistance} (squared) to the position, or null. */
    @Nullable
    public static Player findClosestPlayer(Level world, BlockPos pos, int maxSqDistance) {
        return world.players().stream().filter(player -> player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) < maxSqDistance)
                .findFirst().orElse(null);
    }
}
