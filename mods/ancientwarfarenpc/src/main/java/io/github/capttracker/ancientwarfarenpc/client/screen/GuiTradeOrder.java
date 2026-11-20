/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiTradeOrder.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.block.AwDirection;
import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Checkbox;
import io.github.capttracker.ancientwarfare.client.gui.elements.CompositeScrolled;
import io.github.capttracker.ancientwarfare.client.gui.elements.ItemSlot;
import io.github.capttracker.ancientwarfare.client.gui.elements.Label;
import io.github.capttracker.ancientwarfare.client.gui.elements.Line;
import io.github.capttracker.ancientwarfare.client.gui.elements.NumberInput;
import io.github.capttracker.ancientwarfare.util.StringTools;
import io.github.capttracker.ancientwarfarenpc.menu.TradeOrderMenu;
import io.github.capttracker.ancientwarfarenpc.trade.POTrade;
import io.github.capttracker.ancientwarfarenpc.trade.POTradeDepositEntry;
import io.github.capttracker.ancientwarfarenpc.trade.POTradeList;
import io.github.capttracker.ancientwarfarenpc.trade.POTradePoint;
import io.github.capttracker.ancientwarfarenpc.trade.POTradeRestockData;
import io.github.capttracker.ancientwarfarenpc.trade.POTradeRoute;
import io.github.capttracker.ancientwarfarenpc.trade.POTradeWithdrawEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The trade order editor (1.12.2: {@code GuiTradeOrder}), three modes: the trades (input and output slots, set with the stack on the cursor), the
 * route (block, position, move and delete buttons, the upkeep box and the delay of each stop) and the restock lists (the withdraw and the deposit
 * inventory with their side and the entries). The player's inventory is below to pick items. The order is always sent to the server when the screen
 * closes (as in the original).
 *
 * <p>Change from 1.12.2: the side buttons of the restock lists show the new side at once (the original updated their text before it changed the side).
 */
public class GuiTradeOrder extends GuiContainerBase<TradeOrderMenu> {
    private CompositeScrolled tradesArea;
    private CompositeScrolled routeArea;
    private CompositeScrolled restockArea;
    private Button tradeButton;
    private Button routeButton;
    private Button restockButton;
    private Button currentMode;

    public GuiTradeOrder(TradeOrderMenu menu) {
        super(menu);
    }

    @Override
    protected boolean onGuiCloseRequested() {
        getContainer().sendOrder();
        return super.onGuiCloseRequested();
    }

    @Override
    public void initElements() {
        tradesArea = new CompositeScrolled(this, 0, 24, xSize, ySize - 24 - 4 - 4 - 8 - 4 * 18);
        routeArea = new CompositeScrolled(this, 0, 24, xSize, ySize - 24 - 4 - 4 - 8 - 4 * 18);
        restockArea = new CompositeScrolled(this, 0, 24, xSize, ySize - 24 - 4 - 4 - 8 - 4 * 18);

        tradeButton = new Button(8, 8, 75, 12, "guistrings.npc.trades") {
            @Override
            protected void onPressed() {
                currentMode = tradeButton;
                refreshGui();
            }
        };
        routeButton = new Button(75 + 8, 8, 75, 12, "guistrings.npc.route") {
            @Override
            protected void onPressed() {
                currentMode = routeButton;
                refreshGui();
            }
        };
        restockButton = new Button(150 + 8, 8, 75, 12, "guistrings.npc.restock") {
            @Override
            protected void onPressed() {
                currentMode = restockButton;
                refreshGui();
            }
        };
        currentMode = tradeButton;
    }

    @Override
    public void setupElements() {
        clearElements();
        addGuiElement(tradeButton);
        addGuiElement(routeButton);
        addGuiElement(restockButton);
        tradeButton.setEnabled(currentMode != tradeButton);
        routeButton.setEnabled(currentMode != routeButton);
        restockButton.setEnabled(currentMode != restockButton);
        if (currentMode == tradeButton) {
            addGuiElement(tradesArea);
            setupTradeMode();
        } else if (currentMode == routeButton) {
            addGuiElement(routeArea);
            setupRouteMode();
        } else if (currentMode == restockButton) {
            addGuiElement(restockArea);
            setupRestockMode();
        }
    }

    // ---- trades ----

    private void setupTradeMode() {
        tradesArea.clearElements();
        POTradeList tradeList = getContainer().order.getTradeList();
        int totalHeight = 8;
        for (int i = 0; i < tradeList.size(); i++) {
            totalHeight = addTrade(tradeList.get(i), i, totalHeight);
        }
        Button newTradeButton = new Button(8, totalHeight, xSize - 20, 12, "guistrings.new_trade") {
            @Override
            protected void onPressed() {
                tradeList.addNewTrade();
                refreshGui();
            }
        };
        tradesArea.addGuiElement(newTradeButton);
        totalHeight += 12;
        tradesArea.setAreaSize(totalHeight);
    }

    private int addTrade(final POTrade trade, final int tradeIndex, int startHeight) {
        int gridY = TradeWidgets.addGrid(trade, startHeight, this, tradesArea::addGuiElement, () -> { });
        int startWidth = TradeWidgets.arrowX(trade);
        tradesArea.addGuiElement(new Label(startWidth + 1, startHeight + (gridY + 1) * 5, ">"));
        addTradeControls(startWidth, startHeight, tradeIndex);
        startHeight += 18 * gridY;
        tradesArea.addGuiElement(new Line(0, startHeight + 1, xSize, startHeight + 1, 1, 0x000000ff));
        startHeight += 5;
        return startHeight;
    }

    private void addTradeControls(int startWidth, int startHeight, final int tradeNum) {
        POTradeList tradeList = getContainer().order.getTradeList();
        startHeight -= 1;//offset by 1 to lineup better with the item slot boxes, as they align to the inner slot rather than border
        int infoX = 2 * startWidth + 5;
        tradesArea.addGuiElement(new Button(infoX, startHeight, 55, 12, "guistrings.up") {
            @Override
            protected void onPressed() {
                tradeList.increment(tradeNum);
                refreshGui();
            }
        });
        tradesArea.addGuiElement(new Button(infoX, startHeight + 3 * 18 - 12, 55, 12, "guistrings.down") {
            @Override
            protected void onPressed() {
                tradeList.decrement(tradeNum);
                refreshGui();
            }
        });
        tradesArea.addGuiElement(new Button(infoX, startHeight + 21, 55, 12, "guistrings.delete") {
            @Override
            protected void onPressed() {
                tradeList.remove(tradeNum);
                refreshGui();
            }
        });
    }

    // ---- route ----

    private void setupRouteMode() {
        routeArea.clearElements();
        POTradeRoute route = getContainer().order.getRoute();
        int totalHeight = 8;
        for (int i = 0; i < route.size(); i++) {
            totalHeight = addRoutePoint(route.get(i), i, totalHeight);
        }
        routeArea.setAreaSize(totalHeight);
    }

    private int addRoutePoint(final POTradePoint point, final int index, int startHeight) {
        BlockPos pos = point.getPosition();
        Label blockName = new Label(8, startHeight, "Unknown Block");
        Label posLabel = new Label(8, startHeight + 12, StringTools.formatPos(pos));
        if (player.level().isLoaded(pos)) {
            blockName.setText(player.level().getBlockState(pos).getBlock().getName().getString());
        }
        routeArea.addGuiElement(blockName);
        routeArea.addGuiElement(posLabel);

        routeArea.addGuiElement(new Button(120, startHeight, 55, 12, "guistrings.up") {
            @Override
            protected void onPressed() {
                getContainer().order.getRoute().decrementRoutePoint(index);
                refreshGui();
            }
        });
        routeArea.addGuiElement(new Button(120, startHeight + 12 + 12, 55, 12, "guistrings.down") {
            @Override
            protected void onPressed() {
                getContainer().order.getRoute().incrementRoutePoint(index);
                refreshGui();
            }
        });
        routeArea.addGuiElement(new Button(120, startHeight + 12, 55, 12, "guistrings.delete") {
            @Override
            protected void onPressed() {
                getContainer().order.getRoute().deleteRoutePoint(index);
                refreshGui();
            }
        });

        Checkbox upkeep = new Checkbox(120 + 55 + 4, startHeight, 12, 12, "guistrings.upkeep") {
            @Override
            public void onToggled() {
                getContainer().order.getRoute().setUpkeep(index, checked());
            }
        };
        upkeep.setChecked(point.shouldUpkeep());
        routeArea.addGuiElement(upkeep);

        routeArea.addGuiElement(new Label(120 + 55 + 4, startHeight + 12 + 1, "guistrings.delay"));
        NumberInput delayInput = new NumberInput(120 + 55 + 4, startHeight + 24, 55, point.getDelay(), this) {
            @Override
            public void onValueUpdated(float value) {
                getContainer().order.getRoute().setPointDelay(index, (int) value);
            }
        };
        delayInput.setIntegerValue();
        routeArea.addGuiElement(delayInput);

        startHeight += 12 + 12 + 12;
        routeArea.addGuiElement(new Line(0, startHeight + 2, xSize, startHeight + 2, 1, 0x000000ff));
        startHeight += 5;
        return startHeight;
    }

    // ---- restock ----

    private void setupRestockMode() {
        final POTradeRestockData restock = getContainer().order.getRestockData();
        restockArea.clearElements();
        int totalHeight = 8;

        /********************************* DEPOSIT LIST **********************************************/
        restockArea.addGuiElement(new Label(120, totalHeight, "guistrings.deposit").setRenderCentered());
        totalHeight += 12;
        restockArea.addGuiElement(new Label(70, totalHeight, restock.getDepositPoint() == null ? "guistrings.none" : StringTools.formatPos(restock.getDepositPoint())));
        restockArea.addGuiElement(new Label(8, totalHeight, "guistrings.position"));
        totalHeight += 12;
        restockArea.addGuiElement(new Label(8, totalHeight, "guistrings.side"));
        restockArea.addGuiElement(new Button(70, totalHeight, 55, 12, AwDirection.getDirectionFor(restock.getDepositSide()).getTranslationKey()) {
            @Override
            protected void onPressed() {
                Direction dir = Direction.values()[(restock.getDepositSide().ordinal() + 1) % Direction.values().length];
                restock.setDepositPoint(restock.getDepositPoint(), dir);
                setText(AwDirection.getDirectionFor(restock.getDepositSide()).getTranslationKey());
            }
        });
        totalHeight += 16;

        List<POTradeDepositEntry> depositList = restock.getDepositList();
        for (int i = 0; i < depositList.size(); i++) {
            totalHeight = addDepositEntry(depositList.get(i), i, totalHeight);
        }
        restockArea.addGuiElement(new Button(8, totalHeight, 120, 12, "guistrings.new_deposit") {
            @Override
            protected void onPressed() {
                restock.addDepositEntry();
                refreshGui();
            }
        });
        totalHeight += 14;

        restockArea.addGuiElement(new Line(0, totalHeight, xSize, totalHeight, 2, 0x000000ff));
        totalHeight += 4;

        /********************************* WITHDRAW LIST **********************************************/
        restockArea.addGuiElement(new Label(120, totalHeight, "guistrings.withdraw").setRenderCentered());
        totalHeight += 12;
        restockArea.addGuiElement(new Label(70, totalHeight, restock.getWithdrawPoint() == null ? "guistrings.none" : StringTools.formatPos(restock.getWithdrawPoint())));
        restockArea.addGuiElement(new Label(8, totalHeight, "guistrings.position"));
        totalHeight += 12;
        restockArea.addGuiElement(new Label(8, totalHeight, "guistrings.side"));
        restockArea.addGuiElement(new Button(70, totalHeight, 55, 12, AwDirection.getDirectionFor(restock.getWithdrawSide()).getTranslationKey()) {
            @Override
            protected void onPressed() {
                Direction dir = Direction.values()[(restock.getWithdrawSide().ordinal() + 1) % Direction.values().length];
                restock.setWithdrawPoint(restock.getWithdrawPoint(), dir);
                setText(AwDirection.getDirectionFor(restock.getWithdrawSide()).getTranslationKey());
            }
        });
        totalHeight += 16;

        List<POTradeWithdrawEntry> withdrawList = restock.getWithdrawList();
        for (int i = 0; i < withdrawList.size(); i++) {
            totalHeight = addWithdrawEntry(withdrawList.get(i), i, totalHeight);
        }
        restockArea.addGuiElement(new Button(8, totalHeight, 120, 12, "guistrings.new_withdraw") {
            @Override
            protected void onPressed() {
                restock.addWithdrawEntry();
                refreshGui();
            }
        });
        totalHeight += 14;
        restockArea.setAreaSize(totalHeight);
    }

    private int addDepositEntry(final POTradeDepositEntry entry, final int index, int startHeight) {
        restockArea.addGuiElement(filterSlot(8, startHeight, entry.getFilter(), entry::setFilter));
        restockArea.addGuiElement(new Button(8 + 18 + 4, startHeight + 3, 120, 12, entry.getType().toString()) {
            @Override
            protected void onPressed() {
                entry.toggleType();
                setText(entry.getType().toString());
            }
        });
        restockArea.addGuiElement(new Button(8 + 18 + 4 + 4 + 120, startHeight + 3, 55, 12, "guistrings.delete") {
            @Override
            protected void onPressed() {
                getContainer().order.getRestockData().removeDepositEntry(index);
                refreshGui();
            }
        });
        startHeight += 18;
        startHeight += 5;
        return startHeight;
    }

    private int addWithdrawEntry(final POTradeWithdrawEntry entry, final int index, int startHeight) {
        restockArea.addGuiElement(filterSlot(8, startHeight, entry.getFilter(), entry::setFilter));
        restockArea.addGuiElement(new Button(8 + 18 + 4, startHeight + 3, 120, 12, entry.getType().toString()) {
            @Override
            protected void onPressed() {
                entry.toggleType();
                setText(entry.getType().toString());
            }
        });
        restockArea.addGuiElement(new Button(8 + 18 + 4 + 4 + 120, startHeight + 3, 55, 12, "guistrings.delete") {
            @Override
            protected void onPressed() {
                getContainer().order.getRestockData().removeWithdrawEntry(index);
                refreshGui();
            }
        });
        startHeight += 18;
        startHeight += 5;
        return startHeight;
    }

    /** A filter slot of a restock entry: a click sets it to a copy of the stack on the cursor (or clears it). */
    private ItemSlot filterSlot(int x, int y, ItemStack filter, java.util.function.Consumer<ItemStack> setFilter) {
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
