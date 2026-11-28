/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (third round, mod integrations): Ice and Fire: Community Edition.
 */
package io.github.capttracker.ancientwarfarenpc.compat.iceandfire;

import io.github.capttracker.ancientwarfarenpc.compat.MonsterCompat;
import io.github.capttracker.ancientwarfarenpc.settlement.SettlementConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.entity.PartEntity;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recognises the monsters of Ice and Fire by the registry id of their entity type and by its entity type tags. There is no reference to a class of the mod,
 * so nothing here can break when the mod changes its code: the dragons are the types in the tags {@code iceandfire:fire_dragon}, {@code ice_dragon} and
 * {@code lightning_dragon} (or in the {@code dragon_ids} option), the other monsters are the ids of the {@code monster_ids} option. A part of a multipart
 * creature (the hitboxes of a dragon) counts as its parent.
 */
public final class IceAndFireMonsters implements MonsterCompat {
    public static final String NAMESPACE = "iceandfire";
    private static final List<TagKey<EntityType<?>>> DRAGON_TAGS = List.of(tag("fire_dragon"), tag("ice_dragon"), tag("lightning_dragon"));

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(NAMESPACE, path));
    }

    @Override
    public boolean isMonster(Entity entity) {
        if (!SettlementConfig.iceAndFireEnabled) {
            return false;
        }
        EntityType<?> type = typeOf(entity);
        if (type == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (!NAMESPACE.equals(id.getNamespace())) {
            return false;
        }
        return isDragonType(type, id) || matches(id, SettlementConfig.iceAndFireMonsterIds);
    }

    @Override
    public boolean isMajorThreat(Entity entity) {
        if (!SettlementConfig.iceAndFireEnabled) {
            return false;
        }
        EntityType<?> type = typeOf(entity);
        if (type == null) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return NAMESPACE.equals(id.getNamespace()) && isDragonType(type, id);
    }

    private EntityType<?> typeOf(Entity entity) {
        Entity whole = entity instanceof PartEntity<?> part ? part.getParent() : entity;
        if (whole != null) {
            Entity parent = parentOf(whole);
            if (parent != null) {
                whole = parent;
            }
        }
        return whole == null ? null : whole.getType();
    }

    /** The methods that give the parent of a part, by class of the part (null in the map for a class that has none). */
    private static final Map<Class<?>, Method> PARENT_GETTERS = new ConcurrentHashMap<>();
    private static final Method NO_GETTER = noGetter();

    private static Method noGetter() {
        try {
            return Object.class.getMethod("hashCode");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The parts of the dragons and the other big creatures of Ice and Fire are entities of their own (the types {@code dragon_multipart}, {@code multipart},
     * {@code cylcops_multipart}, {@code hydra_multipart}) with a public {@code getParent()}. It is called by reflection, so the mod's classes are not needed; if
     * the method is not there (a new version of the mod) the part is just not resolved.
     */
    @Override
    public Entity parentOf(Entity entity) {
        if (!SettlementConfig.iceAndFireEnabled) {
            return null;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (!NAMESPACE.equals(id.getNamespace()) || !id.getPath().endsWith("multipart")) {
            return null;
        }
        try {
            Method getter = PARENT_GETTERS.computeIfAbsent(entity.getClass(), type -> {
                try {
                    return type.getMethod("getParent");
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return NO_GETTER;
                }
            });
            if (getter == NO_GETTER) {
                return null;
            }
            return getter.invoke(entity) instanceof Entity parent ? parent : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean isDragonType(EntityType<?> type, ResourceLocation id) {
        for (TagKey<EntityType<?>> tag : DRAGON_TAGS) {
            if (type.is(tag)) {
                return true;
            }
        }
        return matches(id, SettlementConfig.iceAndFireDragonIds);
    }

    /** True if the id is in the list. An entry without a namespace is an Ice and Fire id; an entry that ends with {@code *} matches every id that starts the same. */
    static boolean matches(ResourceLocation id, List<? extends String> entries) {
        String full = id.toString();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            String e = entry.trim().toLowerCase(java.util.Locale.ROOT);
            if (e.indexOf(':') < 0) {
                e = NAMESPACE + ":" + e;
            }
            if (e.endsWith("*") ? full.startsWith(e.substring(0, e.length() - 1)) : full.equals(e)) {
                return true;
            }
        }
        return false;
    }
}
