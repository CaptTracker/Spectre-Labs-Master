/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiRoutingOrder.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.block.AwDirection;
import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Line;
import io.github.capttracker.ancientwarfare.util.InventoryTools;
import io.github.capttracker.ancientwarfare.util.StringTools;
import io.github.capttracker.ancientwarfarenpc.menu.RoutingOrderMenu;
import io.github.capttracker.ancientwarfarenpc.orders.RoutingOrder.RoutePoint;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The routing order editor (1.12.2: {@code GuiRoutingOrder}): per route point the block and position, the side, the route type (right click goes
 * back), the damage and tag switches, twelve filter slots and buttons to move or remove the point. The player's inventory is below to pick items for
 * the filters. The order is sent to the server when the screen closes, if it was changed.
 *
 * <p>Filter slots (the original read the mouse button with LWJGL): with the shift key, a left click adds 32 to the filter and a right click takes
 * 32 off; with the control key the same with 1; with an empty hand a click clears the slot; with a stack on the cursor that matches the filter a left
 * click adds its count and a right click takes it off (never below 1), and a stack that does not match replaces the filter.
 */
public class GuiRoutingOrder extends GuiContainerBase<RoutingOrderMenu> {
    private boolean hasChanged = false;
    private CompositeScrolled area;

    public GuiRoutingOrder(RoutingOrderMenu menu) {
        super(menu);
    }

    @Override
    public void initElements() {
        area = new CompositeScrolled(this, 0, 0, xSize, ySize - 4 * 18 - 8 - 4 - 8);
        addGuiElement(area);
    }

    @Override
    public void setupElements() {
        area.clearElements();
        List<RoutePoint> entries = getContainer().order.getEntries();
        int totalHeight = 12;
        int index = 0;
        for (RoutePoint point : entries) {
            final int pointIndex = index;
            BlockPos pos = point.getTarget();
            area.addGuiElement(new Label(8, totalHeight, player.level().getBlockState(pos).getBlock().getName().getString()));
            area.addGuiElement(new Label(120, totalHeight, StringTools.formatPos(pos)));

            area.addGuiElement(new Button(8, totalHeight + 10, 55, 12, AwDirection.getDirectionFor(point.getBlockSide()).getTranslationKey()) {
                @Override
                protected void onPressed() {
                    getContainer().order.changeBlockSide(pointIndex);
                    refreshGui();
                    hasChanged = true;
                }
            });

            area.addGuiElement(new Button(8 + 55 + 2, totalHeight + 10, 79, 12, point.getRouteType().getTranslationKey()) {
                @Override
                protected void onPressed(int mButton) {
                    getContainer().order.changeRouteType(pointIndex, mButton == 1);
                    refreshGui();
                    hasChanged = true;
                }
            });

            String labelString = point.getIgnoreDamage() ? ChatFormatting.RED.toString() + ChatFormatting.STRIKETHROUGH : "";
            labelString += format("guistrings.dmg");
            Button damageButton = new Button(8 + 55 + 2 + 79 + 2, totalHeight + 10, 38, 12, labelString) {
                @Override
                protected void onPressed() {
                    getContainer().order.toggleIgnoreDamage(pointIndex);
                    hasChanged = true;
                    refreshGui();
                }
            };
            damageButton.addTooltip(format("guistrings.dmg.tooltipprefix." + (point.getIgnoreDamage() ? "disabled" : "enabled")) + format("guistrings.dmg.tooltip"), 28);
            area.addGuiElement(damageButton);

            labelString = point.getIgnoreTag() ? ChatFormatting.RED.toString() + ChatFormatting.STRIKETHROUGH : "";
            labelString += format("guistrings.tag");
            Button tagButton = new Button(8 + 55 + 2 + 79 + 2 + 38 + 2, totalHeight + 10, 38, 12, labelString) {
                @Override
                protected void onPressed() {
                    getContainer().order.toggleIgnoreTag(pointIndex);
                    hasChanged = true;
                    refreshGui();
                }
            };
            tagButton.addTooltip(format("guistrings.tag.tooltipprefix." + (point.getIgnoreTag() ? "disabled" : "enabled")) + format("guistrings.tag.tooltip"), 28);
            area.addGuiElement(tagButton);

            for (int i = 0; i < point.getFilterSize(); i++) {
                final int filterIndex = i;
                area.addGuiElement(new ItemSlot(9 + i * 18, totalHeight + 10 + 12 + 2, point.getFilterInSlot(i), this) {
                    @Override
                    public void onSlotClicked(ItemStack stack, boolean rightClicked) {
                        onFilterSlotClicked(this, point, filterIndex, stack, rightClicked);
                    }
                });
            }

            area.addGuiElement(new Button(8 + 55 + 2 + 80 + 2 + 40 + 2 + 40, totalHeight, 12, 9, "guistrings.npc.remove_point") {
                @Override
                protected void onPressed() {
                    getContainer().order.remove(pointIndex);
                    refreshGui();
                    hasChanged = true;
                }
            });
            area.addGuiElement(new Button(8 + 55 + 2 + 80 + 2 + 40 + 2 + 40, totalHeight + 15, 12, 12, "guistrings.moveup") {
                @Override
                protected void onPressed() {
                    getContainer().order.increment(pointIndex);
                    refreshGui();
                    hasChanged = true;
                }
            });
            area.addGuiElement(new Button(8 + 55 + 2 + 80 + 2 + 40 + 2 + 40, totalHeight + 29, 12, 12, "guistrings.movedown") {
                @Override
                protected void onPressed() {
                    getContainer().order.decrement(pointIndex);
                    refreshGui();
                    hasChanged = true;
                }
            });

            totalHeight += 18 + 10 + 12 + 8;
            area.addGuiElement(new Line(0, totalHeight - 1, xSize - 13, totalHeight - 1, 1, 0x000000ff));
            totalHeight += 6;
            index++;
        }
        area.setAreaSize(totalHeight);
    }

    private void onFilterSlotClicked(ItemSlot slot, RoutePoint point, int index, ItemStack stack, boolean rightClicked) {
        //TODO(port) from the original: move this functionality in as default for item-slots, or toggleable to enable?
        if (!slot.getStack().isEmpty() && isShiftKeyDown()) {
            changeCount(slot, point, index, 32, rightClicked);
        } else if (!slot.getStack().isEmpty() && isCtrlKeyDown()) {
            changeCount(slot, point, index, 1, rightClicked);
        } else if (stack.isEmpty()) {
            point.setFilter(index, ItemStack.EMPTY);
            slot.setItem(ItemStack.EMPTY);
        } else if (InventoryTools.doItemStacksMatchRelaxed(stack, slot.getStack())) {
            changeCount(slot, point, index, stack.getCount(), rightClicked);
        } else {
            ItemStack copy = stack.copy();
            point.setFilter(index, copy);
            slot.setItem(copy);
        }
        hasChanged = true;
    }

    /** Adds to the count of the filter, or (right click) takes from it, never going below 1. */
    private static void changeCount(ItemSlot slot, RoutePoint point, int index, int amount, boolean rightClicked) {
        ItemStack filter = slot.getStack();
        if (rightClicked) {
            filter.shrink(amount);
            if (filter.getCount() < 1) {
                filter.setCount(1);
            }
        } else {
            filter.grow(amount);
        }
        point.setFilter(index, filter);
    }

    /** A lang text (1.12.2: {@code I18n.format}). */
    private static String format(String key) {
        return Component.translatable(key).getString();
    }

    @Override
    protected boolean onGuiCloseRequested() {
        if (hasChanged) {
            getContainer().sendOrder();
        }
        return super.onGuiCloseRequested();
    }
}
