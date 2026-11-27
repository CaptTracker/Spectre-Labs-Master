/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 6): the highlight of a member clicked in the Census tab of the town hall.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.client;

import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Highlights one NPC for 10 seconds, for this player only: the NPC glows (the glowing flag is set on the client copy of the entity, and set again every
 * tick because the server's next sync of the entity flags would clear it) and a beam of particles runs from the player to it. The server is not involved,
 * so nobody else sees anything. An NPC that is not loaded, or that is not in the view range of the client, is shown as its last known position in the chat.
 */
public final class CensusHighlight {
    /** 10 seconds. */
    private static final int DURATION_TICKS = 200;
    private static final double BEAM_SPACING = 1.5D;
    private static final int BEAM_MAX_POINTS = 48;
    private static final DustParticleOptions BEAM = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.2F), 1.0F);

    @Nullable
    private static UUID target;
    private static long endTime;
    @Nullable
    private static Entity glowing;
    private static boolean glowedBefore;

    private CensusHighlight() {}

    /**
     * Starts the highlight (the screen calls it on a click).
     *
     * @param loaded whether the server has the NPC loaded; if not (or if the client does not see it) the last known position goes to the chat
     */
    public static void start(UUID id, String name, BlockPos lastPos, boolean loaded, BlockPos hall) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        stop();
        target = id;
        endTime = mc.level.getGameTime() + DURATION_TICKS;
        Entity found = find(mc, id);
        if (found == null || !loaded) {
            double dx = lastPos.getX() - hall.getX();
            double dz = lastPos.getZ() - hall.getZ();
            Component text = Component.translatable(loaded ? "message.ancientwarfarenpc.census.out_of_view" : "message.ancientwarfarenpc.census.unloaded", name,
                    lastPos.getX(), lastPos.getY(), lastPos.getZ(), (int) Math.round(Math.sqrt(dx * dx + dz * dz)),
                    Component.translatable("guistrings.census.dir." + direction(dx, dz)));
            mc.player.displayClientMessage(text, false);
        }
    }

    /** The eight compass names, 0 degrees being north (negative z). */
    public static String direction(double dx, double dz) {
        String[] names = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        return names[Math.floorMod((int) Math.round(angle / 45.0D), 8)];
    }

    private static void stop() {
        if (glowing != null && !glowedBefore) {
            glowing.setGlowingTag(false);
        }
        glowing = null;
        target = null;
    }

    @Nullable
    private static Entity find(Minecraft mc, UUID id) {
        if (mc.level == null) {
            return null;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity.getUUID().equals(id)) {
                return entity;
            }
        }
        return null;
    }

    private static void tick(Minecraft mc) {
        if (target == null) {
            return;
        }
        if (mc.level == null || mc.player == null || mc.level.getGameTime() >= endTime) {
            stop();
            return;
        }
        if (glowing != null && (glowing.isRemoved() || glowing.level() != mc.level)) {
            glowing = null;
        }
        if (glowing == null && mc.level.getGameTime() % 10 == 0) {
            Entity found = find(mc, target);
            if (found != null) {
                glowing = found;
                glowedBefore = found.hasGlowingTag();
            }
        }
        if (glowing == null) {
            return;
        }
        glowing.setGlowingTag(true);
        if (mc.level.getGameTime() % 2 == 0) {
            drawBeam(mc, glowing);
        }
    }

    private static void drawBeam(Minecraft mc, Entity entity) {
        Vec3 from = mc.player.position().add(0.0D, mc.player.getBbHeight() * 0.6D, 0.0D);
        Vec3 to = entity.position().add(0.0D, entity.getBbHeight() + 0.5D, 0.0D);
        Vec3 line = to.subtract(from);
        double length = line.length();
        if (length < 2.0D) {
            return;
        }
        Vec3 step = line.scale(1.0D / length);
        int points = Math.min(BEAM_MAX_POINTS, (int) (length / BEAM_SPACING));
        double offset = (mc.level.getGameTime() % 8) / 8.0D * BEAM_SPACING;
        for (int i = 0; i < points; i++) {
            double along = 1.0D + offset + i * BEAM_SPACING;
            if (along >= length) {
                break;
            }
            Vec3 point = from.add(step.scale(along));
            mc.level.addParticle(BEAM, point.x, point.y, point.z, 0.0D, 0.0D, 0.0D);
        }
    }

    @EventBusSubscriber(modid = AncientWarfareNPC.MOD_ID, value = Dist.CLIENT)
    public static final class GameBus {
        private GameBus() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (!mc.isPaused()) {
                tick(mc);
            }
        }
    }
}
