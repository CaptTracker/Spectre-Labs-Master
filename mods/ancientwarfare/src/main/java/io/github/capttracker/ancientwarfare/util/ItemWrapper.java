/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/util/ItemWrapper.
 */
package io.github.capttracker.ancientwarfare.util;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An item from a config style list. The damage value of 1.12.2 ("modid:item:damage") no longer exists:
 * a third segment is accepted and ignored with a warning, so old lists still load.
 */
public class ItemWrapper {
    private static final Logger LOGGER = LogUtils.getLogger();

    public final Item item;

    public ItemWrapper(Item item) {
        this.item = item;
    }

    public static List<ItemWrapper> buildList(String listName, List<? extends String> input) {
        List<ItemWrapper> outputList = new ArrayList<>();

        LOGGER.info("Building {}...", listName);

        for (String itemName : input) {
            itemName = itemName.trim();
            if (itemName.isEmpty()) {
                continue;
            }
            String[] itemId = itemName.split(":");
            if (itemId.length != 2 && itemId.length != 3) {
                LOGGER.warn(" - Invalid item (bad length of {}): {}", itemId.length, itemName);
                continue;
            }

            ResourceLocation id = ResourceLocation.tryParse(itemId[0] + ":" + itemId[1]);
            Optional<Item> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
            if (item.isEmpty()) {
                LOGGER.warn(" - Skipping missing item: {}", itemName);
                continue;
            }
            if (itemId.length == 3) {
                LOGGER.warn(" - Damage value of {} ignored, items no longer have one", itemName);
            }
            outputList.add(new ItemWrapper(item.get()));
        }

        LOGGER.info("...added {} items to {}", outputList.size(), listName);

        return outputList;
    }
}
