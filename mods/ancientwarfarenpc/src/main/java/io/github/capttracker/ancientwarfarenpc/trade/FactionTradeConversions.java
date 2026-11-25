/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: FactionTradeTemplate.toTrade / fromTrade and FactionTradeListTemplate.toTradeList, which sit here because the
 * templates of the registry package keep their stacks as JSON (see registry.FactionTradeTemplate) and need the registry access to decode them.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import io.github.capttracker.ancientwarfarenpc.registry.FactionTradeListTemplate;
import io.github.capttracker.ancientwarfarenpc.registry.FactionTradeTemplate;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Conversions between the trade list templates of {@code faction_trade_lists.json} and the live {@link FactionTradeList} of a faction trader. */
public final class FactionTradeConversions {
    private FactionTradeConversions() {}

    /** The trade of a template: full stock (max trades) and the template's refill frequency. */
    public static FactionTrade toTrade(FactionTradeTemplate template, HolderLookup.Provider registries) {
        FactionTrade trade = new FactionTrade();
        trade.setMaxAvailable(template.getMaxTrades());
        trade.setRefillFrequency(template.getRefillFrequency());
        int slot = 0;
        for (ItemStack stack : template.getInput(registries)) {
            if (slot < trade.size()) {
                trade.setInputStack(slot++, stack);
            }
        }
        slot = 0;
        for (ItemStack stack : template.getOutput(registries)) {
            if (slot < trade.size()) {
                trade.setOutputStack(slot++, stack);
            }
        }
        return trade;
    }

    /**
     * The trade list of a template. A trade that has an item that does not exist in this game (a mod that is not installed) is left out, as
     * 1.12.2 dropped those trades while it read the file.
     */
    public static FactionTradeList toTradeList(FactionTradeListTemplate template, HolderLookup.Provider registries) {
        FactionTradeList tradeList = new FactionTradeList();
        for (FactionTradeTemplate tradeTemplate : template.getTrades()) {
            if (tradeTemplate.isValid(registries)) {
                FactionTrade trade = toTrade(tradeTemplate, registries);
                if (trade.isValid()) {
                    tradeList.add(trade);
                }
            }
        }
        return tradeList;
    }

    /** The template of a trade (the max available stock is the max trades; the current stock is not kept). */
    public static FactionTradeTemplate fromTrade(FactionTrade trade, HolderLookup.Provider registries) {
        return FactionTradeTemplate.fromStacks(trade.getInput(), trade.getOutput(), trade.getRefillFrequency(), trade.getMaxAvailable(), registries);
    }

    /** The named template of a trade list, for saving it with {@code FactionTradeListRegistry.saveTradeList}. */
    public static FactionTradeListTemplate fromTradeList(String name, FactionTradeList tradeList, HolderLookup.Provider registries) {
        List<FactionTradeTemplate> trades = new ArrayList<>();
        for (FactionTrade trade : tradeList.getTrades()) {
            if (trade.hasItems()) {
                trades.add(fromTrade(trade, registries));
            }
        }
        return new FactionTradeListTemplate(name, trades);
    }
}
