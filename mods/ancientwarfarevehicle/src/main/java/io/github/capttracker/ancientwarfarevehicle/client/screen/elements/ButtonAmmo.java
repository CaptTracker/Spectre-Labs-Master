/*
 * Ancient Warfare Vehicles - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: vehicle/gui/elements/ButtonAmmo.
 */
package io.github.capttracker.ancientwarfarevehicle.client.screen.elements;

import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfarevehicle.entity.VehicleBase;
import io.github.capttracker.ancientwarfarevehicle.item.ItemAmmo;
import io.github.capttracker.ancientwarfarevehicle.missiles.IAmmo;
import io.github.capttracker.ancientwarfarevehicle.registry.AmmoRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * One row of the ammo selection list: the ammo's icon on the left, its name in the middle and the count the vehicle holds on the
 * right. Pressing it tells the server to select that ammo ({@code VehicleAmmoHelper#handleClientAmmoSelection}).
 *
 * <p>The name is the item's hover name (1.12.2 looked up {@code item.<name>.name}).
 */
public class ButtonAmmo extends Button {
    private final IAmmo ammo;
    private final ItemStack stack;
    private final VehicleBase vehicle;

    public ButtonAmmo(int topLeftX, int topLeftY, int width, int height, IAmmo ammo, VehicleBase vehicle) {
        super(topLeftX, topLeftY, width, height, "");
        this.ammo = ammo;
        this.vehicle = vehicle;
        ItemAmmo item = AmmoRegistry.getItemForAmmo(ammo);
        this.stack = item == null ? ItemStack.EMPTY : new ItemStack(item);
        if (!stack.isEmpty()) {
            setText(stack.getHoverName().getString());
        }
    }

    @Override
    protected void onPressed() {
        vehicle.ammoHelper.handleClientAmmoSelection(ammo.getRegistryName());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (visible) {
            // icon on the left, the name (drawn by the button) in the middle, the count on the far right
            String quantity = String.valueOf(vehicle.ammoHelper.getCountOf(ammo));
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, renderX + 3, renderY + 3);
            }
            int quantityRenderX = renderX + this.width - 10 - font().width(quantity);
            graphics.drawString(font(), quantity, quantityRenderX, renderY + textY, 0xffffffff, true);
        }
    }
}
