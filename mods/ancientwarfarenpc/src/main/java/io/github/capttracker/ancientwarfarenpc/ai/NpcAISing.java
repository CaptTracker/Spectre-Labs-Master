/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/ai/NpcAISing.
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import io.github.capttracker.ancientwarfare.api.ISinger;
import io.github.capttracker.ancientwarfare.util.SongPlayData;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Bard: picks songs from the {@link SongPlayData} of its {@link ISinger} (in order or random, optionally only when a player is within 20 blocks),
 * plays the sound for the length of the song, then waits a random delay.
 *
 * <p>Original bug fixed: the 1.12.2 player search box was {@code AxisAlignedBB.expand(20, 20, 20)}, which only extends the box towards positive x, y
 * and z; the box is grown on every side now.
 */
public class NpcAISing extends NpcAI<NpcBase> {
    private static final int PLAYER_DELAY = 10;
    private static final int PLAYER_RANGE = 20;

    private boolean playing = false;//if currently playing a tune.
    private int currentDelay;//the current cooldown delay.  if not playing, this delay will be incremented before attempting to start next song
    private int tuneIndex = -1;//will be incremented to 0 before first song selected
    private int playerCheckDelay;//used to not check for players -every- tick. checks every 10 ticks
    private int playTime;//tracking current play time.  when this exceeds length, cooldown delay is triggered
    private int maxPlayTime;
    private final ISinger bard;

    public <S extends NpcBase & ISinger> NpcAISing(S npc) {
        super(npc);
        bard = npc;
    }

    @Override
    public boolean canUse() {
        return super.canUse() && bard.getSongs().size() > 0;
    }

    @Override
    public void tick() {
        SongPlayData data = bard.getSongs();
        if (playing) {
            playTime++;
            if (playTime >= maxPlayTime) {
                playTime = 0;
                playing = false;
                int d = data.getMaxDelay() - data.getMinDelay();
                currentDelay = data.getMinDelay() + (d > 0 ? npc.getRandom().nextInt(d) : 0);
            }
        } else if (currentDelay > 0) {
            currentDelay--;
        } else {
            if (data.getPlayOnPlayerEntry()) {
                playerCheckDelay--;
                if (playerCheckDelay <= 0) {
                    playerCheckDelay = PLAYER_DELAY;
                    AABB aabb = npc.getBoundingBox().inflate(PLAYER_RANGE);
                    List<Player> list = npc.level().getEntitiesOfClass(Player.class, aabb);
                    if (!list.isEmpty()) {
                        setNextSong(data);
                    }
                }
            } else {
                setNextSong(data);
            }
        }
    }

    private void setNextSong(SongPlayData data) {
        if (data.getIsRandom()) {
            //TODO(original) (ensure it was not the last song played?)
            tuneIndex = npc.getRandom().nextInt(data.size());
        } else {
            tuneIndex++;
            if (tuneIndex >= data.size()) {
                tuneIndex = 0;
            }
        }
        SongPlayData.SongEntry entry = data.get(tuneIndex);
        maxPlayTime = (int) (entry.length() * 20.f);//convert minutes into ticks
        float volume = (float) entry.volume() * 0.03f;
        entry.getSound().ifPresent(s -> npc.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), s, SoundSource.NEUTRAL, volume, 1.f));
        playing = true;
        playTime = 0;
        if (npc instanceof io.github.capttracker.ancientwarfarenpc.entity.NpcBard bard) {
            bard.markPerformed(npc.level().getGameTime() + maxPlayTime);
        }
    }
}
