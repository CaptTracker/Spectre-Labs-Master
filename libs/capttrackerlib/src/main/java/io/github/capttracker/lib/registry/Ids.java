package io.github.capttracker.lib.registry;

import net.minecraft.resources.ResourceLocation;

/** Short-hand for building ResourceLocations (1.12.2's {@code new ResourceLocation(modid, path)}). */
public final class Ids {
    private Ids() {}

    public static ResourceLocation of(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    public static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }
}
