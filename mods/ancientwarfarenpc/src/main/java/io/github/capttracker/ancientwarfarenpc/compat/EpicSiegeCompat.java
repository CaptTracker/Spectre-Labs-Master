/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/compat/EpicSiegeCompat.
 */
package io.github.capttracker.ancientwarfarenpc.compat;

import com.mojang.logging.LogUtils;
import io.github.capttracker.ancientwarfare.compat.ICompat;
import io.github.capttracker.ancientwarfarenpc.event.NpcEventHandler;
import org.slf4j.Logger;

/**
 * Epic Siege Mod makes mobs hunt through its own target goal; counting that goal as "hunts players" lets {@link NpcEventHandler} give those
 * mobs the NPC hunting goal too. The goal class name is the 1.12.2 one; Epic Siege's 1.21 class name is not confirmed, and if it is not found the
 * compat just logs it and does nothing.
 */
public class EpicSiegeCompat implements ICompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    // unverified for 1.21: needs the 1.21 Epic Siege jar to confirm the class name
    private static final String HOSTILE_AI_CLASS = "funwayguy.epicsiegemod.ai.ESM_EntityAINearestAttackableTarget";

    @Override
    public String getModId() {
        return "epicsiegemod";
    }

    @Override
    public void init() {
        try {
            Class<?> hostileAI = Class.forName(HOSTILE_AI_CLASS);
            NpcEventHandler.INSTANCE.registerAdditionalHostileAICheck(hostileAI::isInstance);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("Epic Siege target goal class {} not found (the 1.21 class name is unverified); compat disabled", HOSTILE_AI_CLASS);
        }
    }
}
