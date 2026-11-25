/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 5): what a caravan sells and can supply.
 */
package io.github.capttracker.ancientwarfarenpc.caravan;

import io.github.capttracker.ancientwarfarenpc.registry.FactionTradeListRegistry;
import io.github.capttracker.ancientwarfarenpc.registry.FactionTradeListTemplate;
import io.github.capttracker.ancientwarfarenpc.registry.FactionTradeTemplate;
import io.github.capttracker.ancientwarfarenpc.trade.FactionTrade;
import io.github.capttracker.ancientwarfarenpc.trade.FactionTradeConversions;
import io.github.capttracker.ancientwarfarenpc.trade.FactionTradeList;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Picks the trade lists of a caravan and what it can supply. The lists are the ones of {@code faction_trade_lists.json} (the {@link FactionTradeListRegistry}).
 * The data has no explicit faction or biome tags, only names, so the choice goes by name:
 * <ul>
 *   <li>the lists a faction has of its own ({@code faction_defaults}) weigh 6;</li>
 *   <li>a default list whose name contains the faction name ({@code MG_Zimba_LowLvl01}) weighs 6;</li>
 *   <li>a default list whose name contains a word of the biome ({@code MG_Desert_LowLvl01}, {@code MG_MidLvl_BirchBuyer01}) weighs 3;</li>
 *   <li>a plain list that no region or faction claims ({@code SimpleGrocer}, {@code Horsetrader}) weighs 1;</li>
 *   <li>any other list (it belongs to another faction or biome) is not drawn.</li>
 * </ul>
 */
public final class CaravanStock {
    private static final int FACTION_WEIGHT = 6;
    private static final int BIOME_WEIGHT = 3;
    private static final int GENERIC_WEIGHT = 1;
    /** The most trades a caravan trader sells. */
    public static final int MAX_TRADES = 18;

    private static final Map<String, String[]> BIOME_WORDS = new LinkedHashMap<>();

    static {
        // a word of the registry path of the biome, to the words that mark the lists of that kind of land (most specific key first)
        BIOME_WORDS.put("birch", new String[]{"birch"});
        BIOME_WORDS.put("plains", new String[]{"plains", "oak"});
        BIOME_WORDS.put("meadow", new String[]{"plains", "oak"});
        BIOME_WORDS.put("dark_forest", new String[]{"forest", "oak"});
        BIOME_WORDS.put("forest", new String[]{"forest", "oak"});
        BIOME_WORDS.put("flower", new String[]{"forest", "plains"});
        BIOME_WORDS.put("taiga", new String[]{"taiga"});
        BIOME_WORDS.put("snow", new String[]{"taiga"});
        BIOME_WORDS.put("grove", new String[]{"taiga"});
        BIOME_WORDS.put("frozen", new String[]{"taiga"});
        BIOME_WORDS.put("desert", new String[]{"desert"});
        BIOME_WORDS.put("beach", new String[]{"beach"});
        BIOME_WORDS.put("ocean", new String[]{"beach"});
        BIOME_WORDS.put("jungle", new String[]{"jungleedge", "vyncanjungle"});
        BIOME_WORDS.put("bamboo", new String[]{"jungleedge"});
        BIOME_WORDS.put("savanna", new String[]{"savana", "savanna"});
        BIOME_WORDS.put("badlands", new String[]{"mesa"});
        BIOME_WORDS.put("swamp", new String[]{"forest"});
    }

    private CaravanStock() {}

    /** A trade list with the weight it is drawn at. */
    public record Candidate(FactionTradeListTemplate template, int weight) {}

    /** The words of the lists that suit a biome (lower case, empty when nothing is known). */
    public static Set<String> biomeWords(Holder<Biome> biome) {
        Set<String> words = new HashSet<>();
        String path = biome.unwrapKey().map(key -> key.location().getPath()).orElse("");
        for (Map.Entry<String, String[]> entry : BIOME_WORDS.entrySet()) {
            if (path.contains(entry.getKey())) {
                for (String word : entry.getValue()) {
                    words.add(word);
                }
                break;
            }
        }
        return words;
    }

    /** The lists a caravan of the faction may carry in a biome, with their weights. Lists with no trade are left out. */
    public static List<Candidate> candidates(String faction, Set<String> biomeWords) {
        String factionKey = faction.toLowerCase(Locale.ROOT);
        Map<String, Candidate> result = new LinkedHashMap<>();
        for (FactionTradeListTemplate template : FactionTradeListRegistry.getFactionDefaults(faction).values()) {
            if (!template.getTrades().isEmpty()) {
                result.put(template.getName(), new Candidate(template, FACTION_WEIGHT));
            }
        }
        for (FactionTradeListTemplate template : FactionTradeListRegistry.getDefaults().values()) {
            if (template.getTrades().isEmpty() || result.containsKey(template.getName())) {
                continue;
            }
            String name = template.getName().toLowerCase(Locale.ROOT);
            if (name.contains(factionKey)) {
                result.put(template.getName(), new Candidate(template, FACTION_WEIGHT));
                continue;
            }
            boolean regional = name.startsWith("mg_");
            boolean biomeMatch = false;
            for (String word : biomeWords) {
                if (name.contains(word)) {
                    biomeMatch = true;
                    break;
                }
            }
            if (biomeMatch) {
                result.put(template.getName(), new Candidate(template, BIOME_WEIGHT));
            } else if (!regional) {
                result.put(template.getName(), new Candidate(template, GENERIC_WEIGHT));
            }
        }
        return new ArrayList<>(result.values());
    }

    /** Draws up to {@code count} different lists by weight. */
    public static List<Candidate> draw(List<Candidate> candidates, int count, RandomSource random) {
        List<Candidate> pool = new ArrayList<>(candidates);
        List<Candidate> chosen = new ArrayList<>();
        while (chosen.size() < count && !pool.isEmpty()) {
            int total = 0;
            for (Candidate candidate : pool) {
                total += candidate.weight();
            }
            int roll = random.nextInt(total);
            for (int i = 0; i < pool.size(); i++) {
                roll -= pool.get(i).weight();
                if (roll < 0) {
                    chosen.add(pool.remove(i));
                    break;
                }
            }
        }
        return chosen;
    }

    /** The trades of the chosen lists, at full stock, at most {@link #MAX_TRADES}. */
    public static FactionTradeList buildTradeList(List<Candidate> chosen, HolderLookup.Provider registries) {
        FactionTradeList list = new FactionTradeList();
        for (Candidate candidate : chosen) {
            FactionTradeList part = FactionTradeConversions.toTradeList(candidate.template(), registries);
            for (FactionTrade trade : part.getTrades()) {
                if (list.size() >= MAX_TRADES) {
                    return list;
                }
                list.add(trade.copy());
            }
        }
        return list;
    }

    /** True for the coins of the NPC module: a caravan does not stock those as goods. */
    public static boolean isCoin(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().startsWith("coin_");
    }

    /** The goods the trades of a caravan give (their outputs, no coins): what the caravan can supply to a buy order, made at the moment of the trade. */
    public static Set<String> supplyOf(FactionTradeList trades) {
        Set<String> ids = new HashSet<>();
        for (FactionTrade trade : trades.getTrades()) {
            for (ItemStack output : trade.getOutput()) {
                if (!output.isEmpty() && !isCoin(output)) {
                    ids.add(BuiltInRegistries.ITEM.getKey(output.getItem()).toString());
                }
            }
        }
        return ids;
    }

    /** Every item that any trade of the candidate lists gives: the goods that the faction and the land can supply. */
    public static Set<Item> suppliable(List<Candidate> candidates, HolderLookup.Provider registries) {
        Set<Item> items = new HashSet<>();
        for (Candidate candidate : candidates) {
            for (FactionTradeTemplate trade : candidate.template().getTrades()) {
                if (!trade.isValid(registries)) {
                    continue;
                }
                for (ItemStack stack : trade.getOutput(registries)) {
                    if (!stack.isEmpty()) {
                        items.add(stack.getItem());
                    }
                }
            }
        }
        return items;
    }
}
