/*
 * Ancient Warfare Core - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: core/gui/manual/GuiManual.
 */
package io.github.capttracker.ancientwarfare.client.screen.manual;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.GuiRender;
import io.github.capttracker.ancientwarfare.client.gui.TextureData;
import io.github.capttracker.ancientwarfare.client.gui.elements.ImageButton;
import io.github.capttracker.ancientwarfare.client.screen.manual.elements.BaseElementWrapper;
import io.github.capttracker.ancientwarfare.manual.IContentElement;
import io.github.capttracker.ancientwarfare.manual.ManualContentRegistry;
import io.github.capttracker.ancientwarfare.menu.ManualMenu;
import io.github.capttracker.lib.registry.Ids;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.awt.Color;
import java.util.Collections;
import java.util.List;

/**
 * The in-game manual: an open book of two pages (412 x 254, drawn from {@code manual.png}), showing one category of
 * {@link ManualContentRegistry} at a time. The table of contents links to the other categories; the arrow at the left
 * edge of the book goes back to the previous one.
 *
 * <p>Client only: there is no server menu. Open it with {@code Minecraft.setScreen(new GuiManual(new ManualMenu(inventory)))}.
 */
public class GuiManual extends GuiContainerBase<ManualMenu> {
    private static final int BOOK_WIDTH = 412;
    private static final int PAGE_WIDTH = BOOK_WIDTH / 2;
    private static final int BOOK_HEIGHT = 254;
    public static final int FOOTER_HEIGHT = 14;
    private static final ResourceLocation MANUAL_TEXTURE = Ids.of(AncientWarfareCore.MOD_ID, "textures/gui/manual.png");
    private static final int HORIZONTAL_PAGE_PADDING = 8;
    private static final int VERTICAL_PAGE_PADDING = 7;
    private static final String INITIAL_CATEGORY = "table_of_contents";
    private List<List<BaseElementWrapper<?>>> pageElements;
    private int currentPageIndex = 0;
    private Page leftPage;
    private Page rightPage;
    private ImageButton backButton;
    private String currentCategory = INITIAL_CATEGORY;
    private String previousCategory = "";

    public GuiManual(ManualMenu menu) {
        super(menu, BOOK_WIDTH, BOOK_HEIGHT);

        loadCategory(currentCategory);
    }

    private void loadCategory(String category) {
        List<IContentElement> elements = ManualContentRegistry.getCategoryContent(category);

        int innerPageWidth = BOOK_WIDTH / 2 - HORIZONTAL_PAGE_PADDING - 2 * Page.getPadding();
        pageElements = ElementWrapperFactory.getPagedWrappedContent(this, elements, innerPageWidth, getInnerPageHeight());
    }

    private int getInnerPageHeight() {
        return BOOK_HEIGHT - 2 * VERTICAL_PAGE_PADDING - 2 * Page.getPadding() - FOOTER_HEIGHT;
    }

    /** No server menu to close: Escape just leaves the screen (a container screen would tell the server to close the inventory). */
    @Override
    public void onClose() {
        if (onGuiCloseRequested()) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiRender.resetColor(graphics);
        GuiRender.quartered(graphics, MANUAL_TEXTURE, 512, 512, 0, 0, BOOK_WIDTH, BOOK_HEIGHT, leftPos, topPos, xSize, ySize);
    }

    @Override
    public void initElements() {
        int pageWidth = PAGE_WIDTH - HORIZONTAL_PAGE_PADDING;
        int pageHeight = BOOK_HEIGHT - 2 * VERTICAL_PAGE_PADDING;
        leftPage = new Page(this, HORIZONTAL_PAGE_PADDING, VERTICAL_PAGE_PADDING, pageWidth, pageHeight,
                new TextureData(MANUAL_TEXTURE, 512, 512, HORIZONTAL_PAGE_PADDING, BOOK_HEIGHT + VERTICAL_PAGE_PADDING, pageWidth, pageHeight), true);
        addGuiElement(leftPage);

        rightPage = new Page(this, PAGE_WIDTH, VERTICAL_PAGE_PADDING, PAGE_WIDTH - HORIZONTAL_PAGE_PADDING, BOOK_HEIGHT - 2 * VERTICAL_PAGE_PADDING,
                new TextureData(MANUAL_TEXTURE, 512, 512, PAGE_WIDTH, BOOK_HEIGHT + VERTICAL_PAGE_PADDING, pageWidth, pageHeight), false);
        addGuiElement(rightPage);
        refreshGui();

        backButton = new ImageButton(-8, 0, 14, 16,
                new TextureData(MANUAL_TEXTURE, 512, 512, 412, 41, 14, 16), Color.GRAY, Color.CYAN) {
            @Override
            protected void onPressed(int mButton) {
                setCurrentCategory(previousCategory);
            }
        };
        addGuiElement(backButton);
    }

    private List<BaseElementWrapper<?>> getPageElements(int pageNumber) {
        return pageElements.size() > pageNumber ? pageElements.get(pageNumber) : Collections.emptyList();
    }

    @Override
    public void setupElements() {
        leftPage.updateContentElements(getPageElements(getCurrentPageIndex()));
        rightPage.updateContentElements(getPageElements(getCurrentPageIndex() + 1));
        backButton.setVisible(!currentCategory.equals(INITIAL_CATEGORY));
    }

    public int getCurrentPageIndex() {
        return currentPageIndex;
    }

    public void setCurrentPageIndex(int currentPageIndex) {
        this.currentPageIndex = currentPageIndex;
        refreshGui();
    }

    public int getPageCount() {
        return pageElements.size();
    }

    public void setCurrentCategory(String category) {
        currentPageIndex = 0;
        previousCategory = currentCategory;
        currentCategory = category;
        loadCategory(category);
        refreshGui();
    }
}
