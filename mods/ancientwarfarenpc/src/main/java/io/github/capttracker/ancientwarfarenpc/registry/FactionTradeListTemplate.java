/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/registry/FactionTradeListTemplate.
 */
package io.github.capttracker.ancientwarfarenpc.registry;

import java.util.Collections;
import java.util.List;

/**
 * A named list of trades ({@code faction_trade_lists.json}: 130 default lists, 1,536 trades) that a faction trader can be set up with.
 *
 * <p>{@code toTradeList} is implemented in {@code trade/FactionTradeConversions}.
 */
public final class FactionTradeListTemplate {
    public static final FactionTradeListTemplate EMPTY = new FactionTradeListTemplate("empty", Collections.emptyList());

    private final String name;
    private final List<FactionTradeTemplate> trades;

    public FactionTradeListTemplate(String name, List<FactionTradeTemplate> trades) {
        this.name = name;
        this.trades = trades;
    }

    public String getName() {
        return name;
    }

    public List<FactionTradeTemplate> getTrades() {
        return trades;
    }
}
