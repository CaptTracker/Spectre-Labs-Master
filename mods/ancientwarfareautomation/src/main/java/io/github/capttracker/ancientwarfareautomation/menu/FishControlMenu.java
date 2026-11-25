/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWorksiteFishControl.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.FishFarmBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteMenus;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

/**
 * The second screen of the fish farm: whether it harvests fish and ink. No slots. Protocol (original keys):
 * {@code fish} and {@code ink} booleans both ways, {@code return} from the client to get the farm's main screen back.
 */
public class FishControlMenu extends AutomationMenu<FishFarmBlockEntity> {
    private static final String FISH = "fish";
    private static final String INK = "ink";
    private static final String RETURN = "return";

    public boolean harvestFish;
    public boolean harvestInk;

    /** Client side. */
    public FishControlMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, FishFarmBlockEntity.class));
    }

    public FishControlMenu(int containerId, Inventory inventory, FishFarmBlockEntity farm) {
        super(AwAutomationMenus.FISH_CONTROL.get(), containerId, inventory, farm);
        this.harvestFish = farm.harvestFish();
        this.harvestInk = farm.harvestInk();
    }

    private CompoundTag settingsTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(FISH, harvestFish);
        tag.putBoolean(INK, harvestInk);
        return tag;
    }

    @Override
    protected void sendInitialState() {
        sendDataToClient(settingsTag());
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        boolean hasSettings = tag.contains(FISH, Tag.TAG_BYTE) && tag.contains(INK, Tag.TAG_BYTE);
        if (isClient()) {
            if (hasSettings) {
                harvestFish = tag.getBoolean(FISH);
                harvestInk = tag.getBoolean(INK);
                refreshGui();
            }
            return;
        }
        if (!isRequestAllowed()) {
            return;
        }
        if (hasSettings) {
            harvestFish = tag.getBoolean(FISH);
            harvestInk = tag.getBoolean(INK);
            blockEntity.setHarvest(harvestFish, harvestInk);
        }
        if (tag.getBoolean(RETURN) && player instanceof ServerPlayer serverPlayer) {
            WorksiteMenus.open(serverPlayer, WorksiteMenus.Kind.FISH_FARM, blockEntity);
        }
    }

    /** Client: sends the two flags, and asks for the farm's main screen when {@code returnToFarm}. */
    public void sendSettingsToServer(boolean returnToFarm) {
        CompoundTag tag = settingsTag();
        if (returnToFarm) {
            tag.putBoolean(RETURN, true);
        }
        sendDataToServer(tag);
    }

    @Override
    protected void detectChanges() {
        if (harvestFish != blockEntity.harvestFish() || harvestInk != blockEntity.harvestInk()) {
            // The original resent every tick here, as it never stored the new values.
            harvestFish = blockEntity.harvestFish();
            harvestInk = blockEntity.harvestInk();
            sendInitialState();
        }
    }
}
