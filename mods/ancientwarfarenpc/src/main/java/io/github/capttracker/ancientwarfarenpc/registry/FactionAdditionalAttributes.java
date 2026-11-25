/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/faction/attributes/AdditionalAttributes, IAdditionalAttribute, BaseAttribute,
 * BoolAttribute, FloatAttribute, StringAttribute and ClassAttribute (the mini framework became this record).
 */
package io.github.capttracker.ancientwarfarenpc.registry;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * The {@code additional_attributes} of a faction NPC default (faction_npc_defaults.json). Each value is optional: a faction or
 * subtype overrides only the keys it names, see {@link #override}.
 *
 * <ul>
 *   <li>{@code burns_in_sun} and {@code undead}: booleans, default false</li>
 *   <li>{@code horse_entity}: the entity type id of the mount of a mounted NPC (1.12.2: a Java class name; the data converter
 *       replaced the 9 class names that occur by ids). Default {@code minecraft:horse}</li>
 *   <li>{@code entity_sound}: prefix of the creature sounds ({@code <prefix>_hurt}, {@code _death}, {@code _attack}), default
 *       {@code none} (vanilla sounds)</li>
 *   <li>{@code heal_per_try}: float of the faction priest (defined in 1.12.2 and read by nothing)</li>
 * </ul>
 */
public record FactionAdditionalAttributes(Optional<Boolean> burnsInSun, Optional<Boolean> undead, Optional<ResourceLocation> horseEntity,
                                          Optional<String> entitySound, Optional<Float> healPerTry) {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String BURNS_IN_SUN = "burns_in_sun";
    public static final String UNDEAD = "undead";
    public static final String HORSE_ENTITY = "horse_entity";
    public static final String ENTITY_SOUND = "entity_sound";
    public static final String HEAL_PER_TRY = "heal_per_try";

    public static final ResourceLocation DEFAULT_HORSE_ENTITY = ResourceLocation.withDefaultNamespace("horse");
    public static final String NO_SOUND = "none";

    public static final FactionAdditionalAttributes NONE =
            new FactionAdditionalAttributes(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public boolean isBurnsInSun() {
        return burnsInSun.orElse(false);
    }

    public boolean isUndead() {
        return undead.orElse(false);
    }

    /** The mount's entity type id; an id that is not an entity type falls back to the horse. */
    public ResourceLocation getHorseEntity() {
        ResourceLocation id = horseEntity.orElse(DEFAULT_HORSE_ENTITY);
        return BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? id : DEFAULT_HORSE_ENTITY;
    }

    public String getEntitySound() {
        return entitySound.orElse(NO_SOUND);
    }

    /** This with every value that {@code overrides} has set replaced. */
    public FactionAdditionalAttributes override(FactionAdditionalAttributes overrides) {
        return new FactionAdditionalAttributes(
                overrides.burnsInSun.isPresent() ? overrides.burnsInSun : burnsInSun,
                overrides.undead.isPresent() ? overrides.undead : undead,
                overrides.horseEntity.isPresent() ? overrides.horseEntity : horseEntity,
                overrides.entitySound.isPresent() ? overrides.entitySound : entitySound,
                overrides.healPerTry.isPresent() ? overrides.healPerTry : healPerTry);
    }

    /** Reads the {@code additional_attributes} object of a default; unknown keys are logged and ignored. */
    public static FactionAdditionalAttributes parse(JsonObject json) {
        if (!json.has("additional_attributes")) {
            return NONE;
        }
        Optional<Boolean> burns = Optional.empty();
        Optional<Boolean> undead = Optional.empty();
        Optional<ResourceLocation> horse = Optional.empty();
        Optional<String> sound = Optional.empty();
        Optional<Float> heal = Optional.empty();
        for (var entry : json.getAsJsonObject("additional_attributes").entrySet()) {
            String value = entry.getValue().getAsString();
            switch (entry.getKey()) {
                case BURNS_IN_SUN -> burns = Optional.of(Boolean.parseBoolean(value));
                case UNDEAD -> undead = Optional.of(Boolean.parseBoolean(value));
                case HORSE_ENTITY -> {
                    ResourceLocation id = ResourceLocation.tryParse(value);
                    if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                        horse = Optional.of(id);
                    } else {
                        LOGGER.error("Mount entity type was not found for: {}", value);
                    }
                }
                case ENTITY_SOUND -> sound = Optional.of(value);
                case HEAL_PER_TRY -> {
                    try {
                        heal = Optional.of(Float.parseFloat(value));
                    } catch (NumberFormatException e) {
                        LOGGER.error("Invalid heal_per_try value: {}", value);
                    }
                }
                // "can_swim" also occurs in the bundled data under additional_attributes; the top-level key is the one that is read
                default -> LOGGER.debug("Ignoring unknown additional attribute {}", entry.getKey());
            }
        }
        return new FactionAdditionalAttributes(burns, undead, horse, sound, heal);
    }
}
