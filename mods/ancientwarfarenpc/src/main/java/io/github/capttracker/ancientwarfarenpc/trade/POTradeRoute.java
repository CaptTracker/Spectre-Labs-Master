/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/trade/POTradeRoute.
 */
package io.github.capttracker.ancientwarfarenpc.trade;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** The route a player owned trader walks ({@code route}: the list of {@link POTradePoint}s). */
public final class POTradeRoute {
    /** The delay of a new route point: one minute. */
    public static final int DEFAULT_DELAY = 20 * 60;

    public static final Codec<POTradeRoute> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            POTradePoint.CODEC.listOf().optionalFieldOf("route", List.of()).forGetter(route -> route.route)
    ).apply(instance, POTradeRoute::new));

    private final List<POTradePoint> route = new ArrayList<>();

    public POTradeRoute() {
    }

    private POTradeRoute(List<POTradePoint> points) {
        for (POTradePoint point : points) {
            route.add(point.copy());
        }
    }

    public POTradeRoute copy() {
        return new POTradeRoute(route);
    }

    public int size() {
        return route.size();
    }

    public POTradePoint get(int index) {
        return route.get(index);
    }

    public void decrementRoutePoint(int index) {
        if (index <= 0 || index >= route.size()) {
            return;
        }
        POTradePoint p = route.remove(index);
        route.add(index - 1, p);
    }

    public void incrementRoutePoint(int index) {
        if (index < 0 || index >= route.size() - 1) {
            return;
        }
        POTradePoint p = route.remove(index);
        route.add(index + 1, p);
    }

    public void deleteRoutePoint(int index) {
        if (index < 0 || index >= route.size()) {
            return;
        }
        route.remove(index);
    }

    public void addRoutePoint(BlockPos pos) {
        route.add(new POTradePoint(pos, DEFAULT_DELAY, false));
    }

    public void setPointDelay(int index, int delay) {
        route.get(index).setDelay(delay);
    }

    public void setUpkeep(int index, boolean val) {
        route.get(index).setShouldUpkeep(val);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof POTradeRoute other && route.equals(other.route);
    }

    @Override
    public int hashCode() {
        return route.hashCode();
    }
}
