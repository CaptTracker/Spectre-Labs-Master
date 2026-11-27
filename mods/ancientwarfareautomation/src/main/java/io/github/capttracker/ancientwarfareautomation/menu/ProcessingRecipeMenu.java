/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 8).
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.ProcessingWorksiteBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteMenus;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The second screen of a production site: for each recipe a switch and the target stock (keep N in storage, 0 = no limit), the stock now, and the
 * status of the site. No slots. Protocol: the server sends {@code enabled} (byte array), {@code targets} and {@code stocks} (int arrays) and
 * {@code status} on open and whenever one changes; the client sends {@code idx} with {@code on} (boolean) or {@code target} (int) for one recipe, and
 * {@code return} to get the main screen of the site back.
 */
public class ProcessingRecipeMenu extends AutomationMenu<ProcessingWorksiteBlockEntity> {
    private static final String ENABLED = "enabled";
    private static final String TARGETS = "targets";
    private static final String STOCKS = "stocks";
    private static final String NEEDED = "needed";
    private static final String STATUS = "status";
    private static final String INDEX = "idx";
    private static final String ON = "on";
    private static final String TARGET = "target";
    private static final String RETURN = "return";
    private static final int CHECK_TICKS = 20;

    public final boolean[] enabled;
    public final int[] targets;
    public final int[] stocks;
    /** Per recipe: how many of its product the settlement is short of, 0 if none (rework phase 8). */
    public final int[] needed;
    public String status = "";
    private int checkDelay = 0;

    /** Client side. */
    public ProcessingRecipeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, ProcessingWorksiteBlockEntity.class));
    }

    public ProcessingRecipeMenu(int containerId, Inventory inventory, ProcessingWorksiteBlockEntity site) {
        super(AwAutomationMenus.PROCESSING_RECIPES.get(), containerId, inventory, site);
        int count = site.recipeCount();
        enabled = new boolean[count];
        targets = new int[count];
        stocks = new int[count];
        needed = new int[count];
    }

    private CompoundTag stateTag() {
        CompoundTag tag = new CompoundTag();
        byte[] on = new byte[enabled.length];
        for (int i = 0; i < on.length; i++) {
            on[i] = (byte) (enabled[i] ? 1 : 0);
        }
        tag.putByteArray(ENABLED, on);
        tag.putIntArray(TARGETS, targets);
        tag.putIntArray(STOCKS, stocks);
        tag.putIntArray(NEEDED, needed);
        tag.putString(STATUS, status);
        return tag;
    }

    /** Server: reads the site into the fields. @return true if something changed */
    private boolean readSite() {
        boolean changed = false;
        for (int i = 0; i < enabled.length; i++) {
            boolean on = blockEntity.isRecipeEnabled(i);
            int target = blockEntity.getRecipeTarget(i);
            int stock = blockEntity.getRecipeStock(i);
            int need = blockEntity.demandOf(i).count();
            changed |= on != enabled[i] || target != targets[i] || stock != stocks[i] || need != needed[i];
            enabled[i] = on;
            targets[i] = target;
            stocks[i] = stock;
            needed[i] = need;
        }
        String now = blockEntity.statusKey();
        changed |= !now.equals(status);
        status = now;
        return changed;
    }

    @Override
    protected void sendInitialState() {
        readSite();
        sendDataToClient(stateTag());
    }

    @Override
    protected void detectChanges() {
        if (checkDelay-- <= 0) {
            checkDelay = CHECK_TICKS;
            if (readSite()) {
                sendDataToClient(stateTag());
            }
        }
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient()) {
            if (tag.contains(ENABLED, Tag.TAG_BYTE_ARRAY)) {
                byte[] on = tag.getByteArray(ENABLED);
                int[] t = tag.getIntArray(TARGETS);
                int[] s = tag.getIntArray(STOCKS);
                int[] n = tag.getIntArray(NEEDED);
                for (int i = 0; i < enabled.length; i++) {
                    enabled[i] = i < on.length && on[i] != 0;
                    targets[i] = i < t.length ? t[i] : 0;
                    stocks[i] = i < s.length ? s[i] : 0;
                    needed[i] = i < n.length ? n[i] : 0;
                }
                status = tag.getString(STATUS);
                refreshGui();
            }
            return;
        }
        if (!isRequestAllowed()) {
            return;
        }
        if (tag.contains(INDEX, Tag.TAG_INT)) {
            int index = tag.getInt(INDEX);
            if (index >= 0 && index < enabled.length) {
                if (tag.contains(ON, Tag.TAG_BYTE)) {
                    blockEntity.setRecipeEnabled(index, tag.getBoolean(ON));
                }
                if (tag.contains(TARGET, Tag.TAG_INT)) {
                    blockEntity.setRecipeTarget(index, Mth.clamp(tag.getInt(TARGET), 0, ProcessingWorksiteBlockEntity.MAX_TARGET));
                }
                checkDelay = 0;
            }
        }
        if (tag.getBoolean(RETURN) && player instanceof ServerPlayer serverPlayer) {
            WorksiteMenus.open(serverPlayer, blockEntity.menuKind(), blockEntity);
        }
    }

    /** Client: the switch of a recipe. */
    public void sendEnabled(int index, boolean on) {
        enabled[index] = on;
        CompoundTag tag = new CompoundTag();
        tag.putInt(INDEX, index);
        tag.putBoolean(ON, on);
        sendDataToServer(tag);
    }

    /** Client: the target stock of a recipe. */
    public void sendTarget(int index, int value) {
        targets[index] = value;
        CompoundTag tag = new CompoundTag();
        tag.putInt(INDEX, index);
        tag.putInt(TARGET, value);
        sendDataToServer(tag);
    }

    /** Client: asks for the main screen of the site. */
    public void sendReturn() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(RETURN, true);
        sendDataToServer(tag);
    }
}
