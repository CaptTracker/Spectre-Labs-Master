/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiNpcInventory.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Checkbox;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Text;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcCombat;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPlayerOwned;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcs;
import io.github.capttracker.ancientwarfarenpc.menu.NpcInventoryMenu;
import io.github.capttracker.ancientwarfarenpc.menu.NpcMenuPayloads;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The inventory screen of an NPC (1.12.2: {@code GuiNpcInventory}): name, skin, the buttons for follow, home, the alt GUI, repack and the creative
 * controls, the stand ground box of archers, and the equipment slots with a ghost item that names each slot.
 *
 * <p>Changes from 1.12.2: the alt and creative buttons send the pending name and flag first (the server closes this menu when it opens the other
 * one, and the original lost them); the buttons that open other menus ask the server.
 */
public class GuiNpcInventory extends GuiNpcBase<NpcInventoryMenu> {
    private static final int BUTTON_X = 8 + 18 + 18 + 18 + 18 + 4;

    private Text nameInput;
    private Checkbox doNotPursueCheckbox;
    private Button skinButton;

    public GuiNpcInventory(NpcInventoryMenu menu) {
        super(menu, 178, menu.guiHeight);
    }

    @Override
    public void initElements() {
        NpcBase npc = getContainer().npc;
        addGuiElement(new Label(8 + 18 + 18 + 4, 9, "guistrings.npc.npc_name"));
        nameInput = new Text(75, 8, 95, getContainer().getName(), this) {
            @Override
            public void onTextUpdated(String oldText, String newText) {
                getContainer().handleNpcNameUpdate(newText);
            }
        };
        addGuiElement(nameInput);

        addGuiElement(new Label(8 + 18 + 18 + 4, 21, "guistrings.npc.npc_texture"));
        skinButton = new Button(75, 20, 95, 12, getContainer().skinSettings.getDescription().getString()) {
            @Override
            protected void onPressed() {
                showScreen(new GuiSkinSelection(GuiNpcInventory.this, getContainer(), npc));
            }
        };
        addGuiElement(skinButton);

        Button button = new Button(BUTTON_X, 36, 37, 12, "guistrings.npc.talk.tab.talk") {
            @Override
            protected void onPressed() {
                sendPendingChanges();
                requestOpen(NpcMenuPayloads.Request.TALK);
            }
        };
        addGuiElement(button);
        if (npc instanceof NpcPlayerOwned) {
            button = new Button(BUTTON_X + 38, 36, 37, 12, "guistrings.npc.talk.tab.morale") {
                @Override
                protected void onPressed() {
                    sendPendingChanges();
                    requestOpen(NpcMenuPayloads.Request.MORALE);
                }
            };
            addGuiElement(button);
        }

        button = new Button(BUTTON_X, 48, 75, 12, "guistrings.npc.togglefollow") {
            @Override
            protected void onPressed() {
                getContainer().togglefollow();
                closeGui();
            }
        };
        addGuiElement(button);

        button = new Button(BUTTON_X, 60, 75, 12, "guistrings.npc.set_home") {
            @Override
            protected void onPressed() {
                getContainer().setHome();
            }
        };
        addGuiElement(button);

        button = new Button(BUTTON_X, 72, 75, 12, "guistrings.npc.clear_home") {
            @Override
            protected void onPressed() {
                getContainer().clearHome();
            }
        };
        addGuiElement(button);

        if (npc.hasAltGui()) {
            button = new Button(BUTTON_X, 84, 75, 12, "guistrings.npc.alt_gui") {
                @Override
                protected void onPressed() {
                    sendPendingChanges();
                    requestOpen(NpcMenuPayloads.Request.ALT);
                }
            };
            addGuiElement(button);
        }

        if (!AWNPCStatics.repackCreativeOnly || player.isCreative()) {
            button = new Button(BUTTON_X, 96, 75, 12, "guistrings.npc.repack") {
                @Override
                protected void onPressed() {
                    getContainer().repack();
                    closeGui();
                }
            };
            addGuiElement(button);
        }

        if (player.isCreative()) {
            button = new Button(BUTTON_X, 108, 75, 12, "guistrings.npc.creative_gui") {
                @Override
                protected void onPressed() {
                    sendPendingChanges();
                    requestOpen(NpcMenuPayloads.Request.CREATIVE);
                }
            };
            addGuiElement(button);
        }

        if (getContainer().isArcher) {
            doNotPursueCheckbox = new Checkbox(BUTTON_X, 120, 12, 12, "guistrings.npc.donotpursue") {
                @Override
                public void onToggled() {
                    getContainer().doNotPursue = checked();
                }
            };
            addGuiElement(doNotPursueCheckbox);
        }

        boolean isCombatNpc = npc instanceof NpcCombat;
        addGhost(26, 8, new ItemStack(isCombatNpc ? Items.IRON_SWORD : Items.IRON_PICKAXE), isCombatNpc ? "guistrings.npc.weapon_slot" : "guistrings.npc.tool_slot");
        addGhost(26, 8 + 18, new ItemStack(AwNpcs.WOODEN_SHIELD.get()), isCombatNpc ? "guistrings.npc.shield_slot" : "guistrings.npc.offhand_slot");
        addGhost(26, 8 + 18 * 2, new ItemStack(Items.IRON_HELMET), "guistrings.npc.helmet_slot");
        addGhost(26, 8 + 18 * 3, new ItemStack(Items.IRON_CHESTPLATE), "guistrings.npc.chest_slot");
        addGhost(26, 8 + 18 * 4, new ItemStack(Items.IRON_LEGGINGS), "guistrings.npc.legs_slot");
        addGhost(26, 8 + 18 * 5, new ItemStack(Items.IRON_BOOTS), "guistrings.npc.boots_slot");
        addGhost(28 + 18 * 2, 8 + 18 * 2, new ItemStack(AwNpcs.UPKEEP_ORDER.get()), "guistrings.npc.upkeep_order_slot");
        addGhost(28 + 18 * 2, 8 + 18 * 3, new ItemStack(AwNpcs.ROUTING_ORDER.get()), "guistrings.npc.manual_order_slot");
    }

    /** The faint item in an empty equipment slot that tells what the slot is for. */
    private void addGhost(int x, int y, ItemStack stack, String tooltip) {
        ItemSlot slot = new ItemSlot(x, y, stack, this);
        slot.setRenderSlotBackground(false).setRenderItemQuantity(false).setHighlightOnMouseOver(false).addTooltip(tooltip);
        addGuiElement(slot);
    }

    private void sendPendingChanges() {
        getContainer().sendChangesToServer();
        getContainer().setName();
    }

    @Override
    public void setupElements() {
        nameInput.setText(getContainer().getName());
        if (getContainer().isArcher) {
            doNotPursueCheckbox.setChecked(getContainer().doNotPursue);
        }
        skinButton.setText(getContainer().skinSettings.getDescription().getString());
    }

    @Override
    protected boolean onGuiCloseRequested() {
        sendPendingChanges();
        return super.onGuiCloseRequested();
    }
}
