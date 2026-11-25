/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/registry/FactionTradeTemplate.
 */
package io.github.capttracker.ancientwarfarenpc.registry;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One trade of a {@link FactionTradeListTemplate}: up to 9 input and 9 output stacks, how often the stock refills (ticks, 0 =
 * instantly, negative = never) and how many times it can be bought before that.
 *
 * <p>The stacks are kept as JSON in the 1.21 item stack format ({@code {"id": ..., "count": ..., "components": {...}}}) and decoded on
 * demand, because decoding stacks with components (enchantments, potions) needs the registry access of a running server, which is not
 * available while the data files load. A stack of an item that does not exist (a mod that is not installed) makes
 * {@link #isValid} false; 1.12.2 dropped such trades while parsing.
 *
 * <p>The conversions to and from {@code FactionTrade} (and {@code FactionTradeList}) live in {@code trade/FactionTradeConversions}.
 */
public final class FactionTradeTemplate {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final List<JsonElement> input;
    private final List<JsonElement> output;
    private final int refillFrequency;
    private final int maxTrades;

    public FactionTradeTemplate(List<JsonElement> input, List<JsonElement> output, int refillFrequency, int maxTrades) {
        this.input = List.copyOf(input);
        this.output = List.copyOf(output);
        this.refillFrequency = refillFrequency;
        this.maxTrades = maxTrades;
    }

    /** A template from item stacks (for saving a trade list from the setup screen). Empty stacks are left out. */
    public static FactionTradeTemplate fromStacks(List<ItemStack> input, List<ItemStack> output, int refillFrequency, int maxTrades, HolderLookup.Provider registries) {
        return new FactionTradeTemplate(encode(input, registries), encode(output, registries), refillFrequency, maxTrades);
    }

    private static List<JsonElement> encode(List<ItemStack> stacks, HolderLookup.Provider registries) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        List<JsonElement> ret = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                ItemStack.CODEC.encodeStart(ops, stack).resultOrPartial(error -> LOGGER.error("Couldn't save trade stack: {}", error)).ifPresent(ret::add);
            }
        }
        return ret;
    }

    /** The stacks of the item stack JSON, or empty if one of them cannot be decoded. */
    private static Optional<List<ItemStack>> decode(List<JsonElement> json, HolderLookup.Provider registries) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        List<ItemStack> ret = new ArrayList<>(json.size());
        for (JsonElement element : json) {
            Optional<ItemStack> stack = ItemStack.CODEC.parse(ops, element).resultOrPartial(error -> LOGGER.debug("Skipping trade stack: {}", error));
            if (stack.isEmpty()) {
                return Optional.empty();
            }
            ret.add(stack.get());
        }
        return Optional.of(ret);
    }

    /** Whether every stack of the trade exists in this game. */
    public boolean isValid(HolderLookup.Provider registries) {
        return decode(input, registries).isPresent() && decode(output, registries).isPresent();
    }

    /** Decoded input stacks (fresh copies); the stacks that cannot be decoded are left out, check {@link #isValid} first. */
    public List<ItemStack> getInput(HolderLookup.Provider registries) {
        return decodeLenient(input, registries);
    }

    public List<ItemStack> getOutput(HolderLookup.Provider registries) {
        return decodeLenient(output, registries);
    }

    private static List<ItemStack> decodeLenient(List<JsonElement> json, HolderLookup.Provider registries) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        List<ItemStack> ret = new ArrayList<>(json.size());
        for (JsonElement element : json) {
            ItemStack.CODEC.parse(ops, element).resultOrPartial(error -> LOGGER.debug("Skipping trade stack: {}", error)).ifPresent(ret::add);
        }
        return ret;
    }

    /** The raw stack JSON, for saving. */
    public List<JsonElement> getInputJson() {
        return input;
    }

    public List<JsonElement> getOutputJson() {
        return output;
    }

    public int getRefillFrequency() {
        return refillFrequency;
    }

    public int getMaxTrades() {
        return maxTrades;
    }
}
