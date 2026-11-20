/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/crafting/RecipeResourceLocation.
 */
package io.github.capttracker.ancientwarfare.crafting;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Names a recipe together with the kind of recipe it is: {@code research|ancientwarfare:research/backpack_0} or
 * {@code regular|minecraft:stick}. This is the form the engineering bench saves in its block entity and sends in
 * menu data. In 1.12.2 research recipes and ordinary recipes lived in two registries, so the kind was needed to look
 * a name up; in 1.21.1 both are in the recipe manager under one set of ids, but the kind is kept as a cheap check
 * that the recipe found is of the expected type.
 */
public final class RecipeResourceLocation {
    public static final RecipeResourceLocation NO_RECIPE_REGISTRY_NAME = new RecipeResourceLocation(Kind.NONE, ResourceLocation.fromNamespaceAndPath("minecraft", "none"));

    private static final Pattern VALID_PATTERN = Pattern.compile("([a-z_]+)[|](.+:.+)");

    private final Kind kind;
    private final ResourceLocation id;

    public RecipeResourceLocation(Kind kind, ResourceLocation id) {
        this.kind = kind;
        this.id = id;
    }

    public Kind getRecipeType() {
        return kind;
    }

    public ResourceLocation getResourceLocation() {
        return id;
    }

    public boolean isNone() {
        return kind == Kind.NONE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RecipeResourceLocation other = (RecipeResourceLocation) o;
        return kind == other.kind && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return 31 * kind.hashCode() + id.hashCode();
    }

    /** {@code kind|namespace:path}; {@link #deserialize} reads it back. The "none" recipe serialises to an empty string. */
    @Override
    public String toString() {
        return kind == Kind.NONE ? "" : kind.getName() + "|" + id;
    }

    /** @return the recipe name, or {@link #NO_RECIPE_REGISTRY_NAME} for an empty or malformed string */
    public static RecipeResourceLocation deserialize(String text) {
        Matcher match = VALID_PATTERN.matcher(text);
        if (match.find() && !match.group(1).equals(Kind.NONE.getName())) {
            ResourceLocation id = ResourceLocation.tryParse(match.group(2));
            if (id != null) {
                return new RecipeResourceLocation(Kind.deserialize(match.group(1)), id);
            }
        }
        return NO_RECIPE_REGISTRY_NAME;
    }

    /** Was {@code RecipeResourceLocation.RecipeType}; renamed so it does not clash with Minecraft's {@code RecipeType}. */
    public enum Kind {
        NONE, RESEARCH, REGULAR;

        public String getName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Kind deserialize(String name) {
            for (Kind kind : values()) {
                if (kind.getName().equals(name)) {
                    return kind;
                }
            }
            return NONE;
        }
    }
}
