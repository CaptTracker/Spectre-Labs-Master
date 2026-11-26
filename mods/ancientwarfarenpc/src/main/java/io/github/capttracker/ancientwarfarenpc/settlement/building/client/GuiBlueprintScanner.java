/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * New in the port (rework phase 9): building designs. Client only.
 */
package io.github.capttracker.ancientwarfarenpc.settlement.building.client;

import io.github.capttracker.ancientwarfarenpc.settlement.building.DesignCategory;
import io.github.capttracker.ancientwarfarenpc.settlement.building.DesignNetwork;
import io.github.capttracker.ancientwarfarenpc.settlement.building.DesignStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/** The screen after the third click of the blueprint scanner: name, category, style and an optional worksite block. */
public class GuiBlueprintScanner extends Screen {
    private final DesignNetwork.OpenScannerPayload info;
    private EditBox nameBox;
    private DesignCategory category = DesignCategory.HOUSE;
    private DesignStyle style = DesignStyle.ANY;
    private String worksite = "";

    public GuiBlueprintScanner(DesignNetwork.OpenScannerPayload info) {
        super(Component.translatable("gui.ancientwarfarenpc.blueprint.title"));
        this.info = info;
    }

    @Override
    protected void init() {
        int w = 200;
        int x = (width - w) / 2;
        int y = height / 2 - 70;
        String typed = nameBox == null ? "" : nameBox.getValue();
        nameBox = new EditBox(font, x, y + 28, w, 20, Component.translatable("gui.ancientwarfarenpc.blueprint.name"));
        nameBox.setMaxLength(40);
        nameBox.setValue(typed);
        nameBox.setHint(Component.translatable("gui.ancientwarfarenpc.blueprint.name_hint"));
        addRenderableWidget(nameBox);
        setInitialFocus(nameBox);
        addRenderableWidget(CycleButton.builder((DesignCategory c) -> Component.translatable(c.translationKey()))
                .withValues(DesignCategory.values()).withInitialValue(category)
                .create(x, y + 54, w, 20, Component.translatable("gui.ancientwarfarenpc.blueprint.category"), (b, v) -> category = v));
        addRenderableWidget(CycleButton.builder((DesignStyle s) -> Component.translatable(s.translationKey()))
                .withValues(DesignStyle.values()).withInitialValue(style)
                .create(x, y + 78, w, 20, Component.translatable("gui.ancientwarfarenpc.blueprint.style"), (b, v) -> style = v));
        if (!info.worksiteBlocks().isEmpty()) {
            List<String> options = new ArrayList<>();
            options.add("");
            options.addAll(info.worksiteBlocks());
            addRenderableWidget(CycleButton.builder((String s) -> s.isEmpty()
                            ? Component.translatable("gui.ancientwarfarenpc.blueprint.worksite_none") : Component.literal(s))
                    .withValues(options).withInitialValue(worksite)
                    .create(x, y + 102, w, 20, Component.translatable("gui.ancientwarfarenpc.blueprint.worksite"), (b, v) -> worksite = v));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.ancientwarfarenpc.blueprint.save"), b -> save())
                .bounds(x, y + 130, 98, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x + 102, y + 130, 98, 20).build());
    }

    private void save() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            setFocused(nameBox);
            return;
        }
        PacketDistributor.sendToServer(new DesignNetwork.SavePayload(name, category.ordinal(), style.ordinal(), worksite));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int y = height / 2 - 70;
        g.drawCenteredString(font, title, width / 2, y, 0xFFFFFF);
        int sx = info.max().getX() - info.min().getX() + 1;
        int sy = info.max().getY() - info.min().getY() + 1;
        int sz = info.max().getZ() - info.min().getZ() + 1;
        g.drawCenteredString(font, Component.translatable("gui.ancientwarfarenpc.blueprint.summary", sx, sy, sz, info.beds(), info.storage(),
                Component.translatable("direction.ancientwarfarenpc." + info.front().getName())), width / 2, y + 13, 0xA0A0A0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
