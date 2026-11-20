/*
 * Ancient Warfare Automation - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: automation/container/ContainerWorksiteAnimalControl.
 */
package io.github.capttracker.ancientwarfareautomation.menu;

import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.AnimalFarmBlockEntity;
import io.github.capttracker.ancientwarfareautomation.block.entity.worksite.WorksiteMenus;
import io.github.capttracker.ancientwarfareautomation.init.AwAutomationMenus;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The second screen of the animal farm: how many pigs, sheep, cows and chickens it keeps alive. No slots.
 *
 * <p>Protocol (original keys): the server sends {@code pigs, cows, sheep, chickens} on open and whenever the farm's
 * limits change; the client sends the same four numbers when the screen changes them or closes, and
 * {@code return} when it wants the farm's main screen back. The server clamps each number to 0..{@value #MAX_COUNT}
 * (the original took any int).
 */
public class AnimalControlMenu extends AutomationMenu<AnimalFarmBlockEntity> {
    public static final int MAX_COUNT = 1000;
    private static final String PIGS = "pigs";
    private static final String COWS = "cows";
    private static final String SHEEP = "sheep";
    private static final String CHICKENS = "chickens";
    private static final String RETURN = "return";

    public int maxPigs;
    public int maxSheep;
    public int maxCows;
    public int maxChickens;

    /** Client side. */
    public AnimalControlMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(containerId, inventory, resolve(inventory, buf, AnimalFarmBlockEntity.class));
    }

    public AnimalControlMenu(int containerId, Inventory inventory, AnimalFarmBlockEntity farm) {
        super(AwAutomationMenus.ANIMAL_CONTROL.get(), containerId, inventory, farm);
        maxPigs = farm.maxPigCount;
        maxSheep = farm.maxSheepCount;
        maxCows = farm.maxCowCount;
        maxChickens = farm.maxChickenCount;
    }

    private CompoundTag limitsTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(PIGS, maxPigs);
        tag.putInt(COWS, maxCows);
        tag.putInt(SHEEP, maxSheep);
        tag.putInt(CHICKENS, maxChickens);
        return tag;
    }

    private static boolean hasLimits(CompoundTag tag) {
        return tag.contains(PIGS, Tag.TAG_INT) && tag.contains(COWS, Tag.TAG_INT)
                && tag.contains(SHEEP, Tag.TAG_INT) && tag.contains(CHICKENS, Tag.TAG_INT);
    }

    @Override
    protected void sendInitialState() {
        sendDataToClient(limitsTag());
    }

    @Override
    protected void handlePacketData(CompoundTag tag) {
        if (isClient()) {
            if (hasLimits(tag)) {
                maxCows = tag.getInt(COWS);
                maxPigs = tag.getInt(PIGS);
                maxChickens = tag.getInt(CHICKENS);
                maxSheep = tag.getInt(SHEEP);
                refreshGui();
            }
            return;
        }
        if (!isRequestAllowed()) {
            return;
        }
        if (hasLimits(tag)) {
            maxCows = Mth.clamp(tag.getInt(COWS), 0, MAX_COUNT);
            maxPigs = Mth.clamp(tag.getInt(PIGS), 0, MAX_COUNT);
            maxChickens = Mth.clamp(tag.getInt(CHICKENS), 0, MAX_COUNT);
            maxSheep = Mth.clamp(tag.getInt(SHEEP), 0, MAX_COUNT);
            blockEntity.setMaxCounts(maxPigs, maxCows, maxChickens, maxSheep);
        }
        if (tag.getBoolean(RETURN) && player instanceof ServerPlayer serverPlayer) {
            WorksiteMenus.open(serverPlayer, WorksiteMenus.Kind.ANIMAL_FARM, blockEntity);
        }
    }

    /** Client: sends the four limits, and asks for the farm's main screen when {@code returnToFarm}. */
    public void sendSettingsToServer(boolean returnToFarm) {
        CompoundTag tag = limitsTag();
        if (returnToFarm) {
            tag.putBoolean(RETURN, true);
        }
        sendDataToServer(tag);
    }

    @Override
    protected void detectChanges() {
        if (maxPigs != blockEntity.maxPigCount || maxChickens != blockEntity.maxChickenCount
                || maxSheep != blockEntity.maxSheepCount || maxCows != blockEntity.maxCowCount) {
            maxPigs = blockEntity.maxPigCount;
            maxChickens = blockEntity.maxChickenCount;
            maxSheep = blockEntity.maxSheepCount;
            maxCows = blockEntity.maxCowCount;
            sendInitialState();
        }
    }
}
