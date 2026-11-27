/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 11): the editor of the sea route chart.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Line;
import io.github.capttracker.ancientwarfare.client.gui.elements.NumberInput;
import io.github.capttracker.ancientwarfare.util.StringTools;
import io.github.capttracker.ancientwarfarenpc.menu.SeaRouteMenu;
import io.github.capttracker.ancientwarfarenpc.orders.SeaRoute;
import io.github.capttracker.ancientwarfarenpc.trade.POTradeDepositEntry;
import io.github.capttracker.ancientwarfarenpc.trade.POTradeWithdrawEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * The sea route chart editor, one scrolling list of the stops. Each stop shows its dock, the up, down and delete buttons, the seconds the ship stays moored
 * and two lists of item filters: what the ship unloads into the dock's storage and what it loads out of it (a click on a filter slot with a stack on the cursor
 * sets it, as in the trade order's restock lists; the stack size is the quantity; the type button cycles the way the quantity is used). The player's inventory
 * is below to pick items. The chart is sent to the server when the screen closes, like the other order editors.
 */
public class GuiSeaRoute extends GuiContainerBase<SeaRouteMenu> {
    private CompositeScrolled area;

    public GuiSeaRoute(SeaRouteMenu menu) {
        super(menu);
    }

    @Override
    protected boolean onGuiCloseRequested() {
        getContainer().sendOrder();
        return super.onGuiCloseRequested();
    }

    @Override
    public void initElements() {
        area = new CompositeScrolled(this, 0, 24, xSize, ySize - 24 - 4 - 4 - 8 - 4 * 18);
    }

    @Override
    public void setupElements() {
        clearElements();
        addGuiElement(new Label(8, 8, "guistrings.npc.sea_route.title"));
        addGuiElement(area);
        area.clearElements();
        SeaRoute route = getContainer().order;
        int height = 8;
        if (route.size() == 0) {
            area.addGuiElement(new Label(8, height, "guistrings.npc.sea_route.empty"));
            height += 12;
        }
        for (int i = 0; i < route.size(); i++) {
            height = addStop(route.get(i), i, height);
        }
        area.setAreaSize(height);
    }

    private int addStop(final SeaRoute.Stop stop, final int index, int y) {
        BlockPos pos = stop.getDock();
        String blockName = player.level().isLoaded(pos) ? player.level().getBlockState(pos).getBlock().getName().getString() : "?";
        area.addGuiElement(new Label(8, y, (index + 1) + ". " + blockName));
        area.addGuiElement(new Label(8, y + 12, StringTools.formatPos(pos)));

        area.addGuiElement(new Button(120, y, 55, 12, "guistrings.up") {
            @Override
            protected void onPressed() {
                getContainer().order.moveStopUp(index);
                refreshGui();
            }
        });
        area.addGuiElement(new Button(120, y + 12, 55, 12, "guistrings.delete") {
            @Override
            protected void onPressed() {
                getContainer().order.deleteStop(index);
                refreshGui();
            }
        });
        area.addGuiElement(new Button(120, y + 24, 55, 12, "guistrings.down") {
            @Override
            protected void onPressed() {
                getContainer().order.moveStopDown(index);
                refreshGui();
            }
        });
        area.addGuiElement(new Label(120 + 55 + 4, y + 1, "guistrings.npc.sea_route.wait"));
        NumberInput wait = new NumberInput(120 + 55 + 4, y + 12, 40, stop.getWaitTicks() / 20.0F, this) {
            @Override
            public void onValueUpdated(float value) {
                stop.setWaitTicks(Math.round(value * 20.0F));
            }
        };
        wait.setIntegerValue();
        area.addGuiElement(wait);
        y += 40;

        area.addGuiElement(new Label(8, y, "guistrings.npc.sea_route.unload"));
        y += 12;
        List<POTradeDepositEntry> unload = stop.getUnload();
        for (int i = 0; i < unload.size(); i++) {
            y = addUnloadEntry(stop, unload.get(i), i, y);
        }
        if (unload.size() < SeaRoute.MAX_ENTRIES) {
            area.addGuiElement(new Button(8, y, 120, 12, "guistrings.npc.sea_route.add_unload") {
                @Override
                protected void onPressed() {
                    stop.addUnloadEntry();
                    refreshGui();
                }
            });
            y += 16;
        }

        area.addGuiElement(new Label(8, y, "guistrings.npc.sea_route.load"));
        y += 12;
        List<POTradeWithdrawEntry> load = stop.getLoad();
        for (int i = 0; i < load.size(); i++) {
            y = addLoadEntry(stop, load.get(i), i, y);
        }
        if (load.size() < SeaRoute.MAX_ENTRIES) {
            area.addGuiElement(new Button(8, y, 120, 12, "guistrings.npc.sea_route.add_load") {
                @Override
                protected void onPressed() {
                    stop.addLoadEntry();
                    refreshGui();
                }
            });
            y += 16;
        }
        area.addGuiElement(new Line(0, y, xSize, y, 2, 0x000000ff));
        return y + 6;
    }

    private int addUnloadEntry(SeaRoute.Stop stop, final POTradeDepositEntry entry, final int index, int y) {
        area.addGuiElement(filterSlot(8, y, entry.getFilter(), entry::setFilter));
        area.addGuiElement(new Button(8 + 18 + 4, y + 3, 120, 12, entry.getType().toString()) {
            @Override
            protected void onPressed() {
                entry.toggleType();
                setText(entry.getType().toString());
            }
        });
        area.addGuiElement(new Button(8 + 18 + 4 + 4 + 120, y + 3, 55, 12, "guistrings.delete") {
            @Override
            protected void onPressed() {
                stop.getUnload().remove(index);
                refreshGui();
            }
        });
        return y + 18 + 5;
    }

    private int addLoadEntry(SeaRoute.Stop stop, final POTradeWithdrawEntry entry, final int index, int y) {
        area.addGuiElement(filterSlot(8, y, entry.getFilter(), entry::setFilter));
        area.addGuiElement(new Button(8 + 18 + 4, y + 3, 120, 12, entry.getType().toString()) {
            @Override
            protected void onPressed() {
                entry.toggleType();
                setText(entry.getType().toString());
            }
        });
        area.addGuiElement(new Button(8 + 18 + 4 + 4 + 120, y + 3, 55, 12, "guistrings.delete") {
            @Override
            protected void onPressed() {
                stop.getLoad().remove(index);
                refreshGui();
            }
        });
        return y + 18 + 5;
    }

    /** A filter slot of an entry: a click sets it to a copy of the stack on the cursor (or clears it). */
    private ItemSlot filterSlot(int x, int y, ItemStack filter, Consumer<ItemStack> setFilter) {
        return new ItemSlot(x, y, filter, this) {
            @Override
            public void onSlotClicked(ItemStack stack, boolean rightClicked) {
                stack = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
                setFilter.accept(stack);
                setItem(stack);
            }
        };
    }
}
