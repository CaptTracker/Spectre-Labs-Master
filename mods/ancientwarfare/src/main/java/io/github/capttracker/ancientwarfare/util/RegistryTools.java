/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/RegistryTools.
 */
package io.github.capttracker.ancientwarfare.util;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.util.MissingResourceException;

/**
 * Lookup of blocks and items by registry name for data files: an entry of a mod that is not installed
 * becomes air (so optional mod content can be listed in data), a missing entry of an installed mod is an error.
 */
public final class RegistryTools {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RegistryTools() {}

    public static Block getBlock(String registryName) {
        return getRegistryEntry(registryName, BuiltInRegistries.BLOCK, Blocks.AIR);
    }

    public static Item getItem(String registryName) {
        return getRegistryEntry(registryName, BuiltInRegistries.ITEM, Items.AIR);
    }

    /** @throws MissingResourceException if the name is not registered but its mod is loaded */
    private static <T> T getRegistryEntry(String registryName, Registry<T> registry, T defaultValue) {
        ResourceLocation key = ResourceLocation.parse(registryName);
        if (!registry.containsKey(key)) {
            if (!ModList.get().isLoaded(key.getNamespace())) {
                LOGGER.debug("Mod {} is not loaded. Replacing {} with {}", key.getNamespace(), key, registry.getKey(defaultValue));
                return defaultValue;
            }
            throw new MissingResourceException("Unable to find entry with registry name \"" + registryName + "\"",
                    registry.key().location().toString(), registryName);
        }
        return registry.get(key);
    }
}
