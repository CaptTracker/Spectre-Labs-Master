/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port: names for the bits of the synced task bit field of {@code NpcBase} (1.12.2: the {@code NpcAI.TASK_*} constants and a loop over
 * {@code NpcAI.NUMBER_OF_TASKS} bits in {@code RenderNpcBase}).
 */
package io.github.capttracker.ancientwarfarenpc.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * The tasks of the status bit field that is synced to the clients ({@code NpcBase.getAITasks()}); the renderer draws one icon for each set bit.
 * The goals set and clear the bits with {@code NpcBase.addAITask} and {@code removeAITask}. The bit values are the {@code NpcAI.TASK_*} constants.
 */
public enum NpcTaskStatus {
    ATTACK(NpcAI.TASK_ATTACK),
    UPKEEP(NpcAI.TASK_UPKEEP),
    IDLE_HUNGRY(NpcAI.TASK_IDLE_HUNGRY),
    GO_HOME(NpcAI.TASK_GO_HOME),
    WORK(NpcAI.TASK_WORK),
    PATROL(NpcAI.TASK_PATROL),
    GUARD(NpcAI.TASK_GUARD),
    FOLLOW(NpcAI.TASK_FOLLOW),
    WANDER(NpcAI.TASK_WANDER),
    MOVE(NpcAI.TASK_MOVE),
    ALARM(NpcAI.TASK_ALARM),
    FLEE(NpcAI.TASK_FLEE),
    SLEEP(NpcAI.TASK_SLEEP),
    RAIN(NpcAI.TASK_RAIN);

    private static final NpcTaskStatus[] VALUES = values();

    private final int bit;

    NpcTaskStatus(int bit) {
        this.bit = bit;
    }

    public int getBit() {
        return bit;
    }

    public boolean isSet(int taskBits) {
        return (taskBits & bit) != 0;
    }

    /** The tasks that are set in the bit field, in bit order. */
    public static List<NpcTaskStatus> of(int taskBits) {
        List<NpcTaskStatus> result = new ArrayList<>();
        for (NpcTaskStatus status : VALUES) {
            if (status.isSet(taskBits)) {
                result.add(status);
            }
        }
        return result;
    }
}
