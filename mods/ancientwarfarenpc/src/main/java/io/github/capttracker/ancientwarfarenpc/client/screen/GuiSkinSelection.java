/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/gui/GuiSkinSelection.
 */
package io.github.capttracker.ancientwarfarenpc.client.screen;

import io.github.capttracker.ancientwarfare.client.gui.GuiContainerBase;
import io.github.capttracker.ancientwarfare.client.gui.GuiSelectFromList;
import io.github.capttracker.ancientwarfare.client.gui.elements.Button;
import io.github.capttracker.ancientwarfare.client.gui.elements.Checkbox;
import io.github.capttracker.ancientwarfare.client.gui.elements.Text;
import io.github.capttracker.ancientwarfare.menu.ScreenOnlyMenu;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.menu.ISkinSettingsMenu;
import io.github.capttracker.ancientwarfarenpc.skin.NpcSkinManager;
import io.github.capttracker.ancientwarfarenpc.skin.NpcSkinSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The skin selection of an NPC (1.12.2: {@code GuiSkinSelection}): the default skin, the skin of a player by name, or a skin of an NPC type
 * (random or chosen), the slim arm model, and a preview of the NPC that follows the mouse. Shown over the inventory screen or the creative controls
 * (the parent) and edits the skin settings of the parent's menu.
 *
 * <p>The screen has its own empty menu so that the parent's slots are not drawn over it (the original gave it a fresh container); the parent's menu
 * only receives the changes. The preview is the real NPC, so it shows a change when the server has applied it and told this client; the player skin
 * is sent at most every two seconds while the name is typed (1.12.2: every 40 frames).
 */
public class GuiSkinSelection extends GuiContainerBase<ScreenOnlyMenu> {
    private static final int SUBCONTROLS_INDENT = 24;

    private final GuiContainerBase<?> parent;
    private final ISkinSettingsMenu skinMenu;
    private final NpcBase npc;
    private final NpcSkinSettings skinSettings;

    private Checkbox defaultType;
    private Checkbox playerType;
    private Checkbox npcType;
    private int previewTop = 0;
    private int playerSkinUpdateCooldown = 0;
    private boolean updatePlayerSkin = false;

    /**
     * @param parent   the screen this one was opened from; its menu must be the {@code skinMenu}
     * @param skinMenu the menu that holds the skin settings
     * @param npc      the NPC to draw
     */
    public GuiSkinSelection(GuiContainerBase<?> parent, ISkinSettingsMenu skinMenu, NpcBase npc) {
        super(new ScreenOnlyMenu(parent.getContainer().player.getInventory()), 200, 240);
        this.parent = parent;
        this.skinMenu = skinMenu;
        this.npc = npc;
        this.skinSettings = skinMenu.getSkinSettings();
    }

    @Override
    protected void init() {
        super.init();
        // the menu of the parent talks to this screen while it is open (1.12.2: container.setGui)
        parent.getContainer().setRefreshListener(this::refreshGui);
    }

    @Override
    public void initElements() {
    }

    @Override
    public void setupElements() {
        clearElements();
        ySize = calculateYSize();
        imageHeight = ySize;
        topPos = (this.height - this.ySize) / 2;
        guiTop = topPos;

        int startHeight = 8;
        defaultType = new Checkbox(8, startHeight, 12, 12, "gui.ancientwarfarenpc.skin_selection.default") {
            @Override
            public void onToggled() {
                super.onToggled();
                if (checked()) {
                    skinSettings.setSkinType(NpcSkinSettings.SkinType.DEFAULT);
                    playerType.setChecked(false);
                    npcType.setChecked(false);
                    refreshGui();
                } else {
                    setChecked(true);
                }
            }
        };
        defaultType.setChecked(skinSettings.getSkinType() == NpcSkinSettings.SkinType.DEFAULT);
        addGuiElement(defaultType);
        startHeight += 16;

        playerType = new Checkbox(8, startHeight, 12, 12, "gui.ancientwarfarenpc.skin_selection.player") {
            @Override
            public void onToggled() {
                super.onToggled();
                if (checked()) {
                    skinSettings.setSkinType(NpcSkinSettings.SkinType.PLAYER);
                    defaultType.setChecked(false);
                    npcType.setChecked(false);
                    refreshGui();
                } else {
                    setChecked(true);
                }
            }
        };
        playerType.setChecked(skinSettings.getSkinType() == NpcSkinSettings.SkinType.PLAYER);
        addGuiElement(playerType);
        startHeight += 16;

        if (playerType.checked()) {
            startHeight = addPlayerElements(startHeight);
        }

        npcType = new Checkbox(8, startHeight, 12, 12, "gui.ancientwarfarenpc.skin_selection.npc_type") {
            @Override
            public void onToggled() {
                super.onToggled();
                if (checked()) {
                    skinSettings.setSkinType(NpcSkinSettings.SkinType.NPC_TYPE);
                    playerType.setChecked(false);
                    defaultType.setChecked(false);
                    refreshGui();
                } else {
                    setChecked(true);
                }
            }
        };
        npcType.setChecked(skinSettings.getSkinType() == NpcSkinSettings.SkinType.NPC_TYPE);
        addGuiElement(npcType);
        startHeight += 16;

        if (npcType.checked()) {
            startHeight = addNpcTypeElements(startHeight);
        }

        if (!defaultType.checked()) {
            Checkbox alex = new Checkbox(8, startHeight, 16, 16, "gui.ancientwarfarenpc.skin_selection.alex_model") {
                @Override
                public void onToggled() {
                    super.onToggled();
                    skinSettings.setAlexModel(checked());
                    refreshGui();
                }
            };
            alex.setChecked(skinSettings.isAlexModel());
            addGuiElement(alex);
            startHeight += 20;
        }
        previewTop = startHeight;
    }

    private int calculateYSize() {
        switch (skinSettings.getSkinType()) {
            case NPC_TYPE:
                return skinSettings.getNpcTypeName().isEmpty() || skinSettings.isRandom() ? 250 : 266;
            case PLAYER:
                return 230;
            case DEFAULT:
            default:
                return 194;
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (playerSkinUpdateCooldown > 0) {
            playerSkinUpdateCooldown--;
        } else if (updatePlayerSkin) {
            updatePlayerSkin = false;
            skinMenu.handleNpcSkinUpdate();
            playerSkinUpdateCooldown = 40;
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        int backgroundWidth = xSize - 16;
        int backgroundHeight = 130;
        int left = guiLeft + 8;
        int top = guiTop + previewTop;
        graphics.fill(left, top, left + backgroundWidth, top + backgroundHeight, 0xFF000000);
        InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, left, top, left + backgroundWidth, top + backgroundHeight, 35, 0.0625F,
                mouseX, mouseY, npc);
    }

    private static String skinName(ResourceLocation skin) {
        return skin.getPath().replaceFirst("^textures/[^/]+/", "").replace(".png", "");
    }

    private int addNpcTypeElements(int startHeight) {
        addGuiElement(new Button(SUBCONTROLS_INDENT, startHeight, 150, 12,
                skinSettings.getNpcTypeName().isEmpty() ? "gui.ancientwarfarenpc.skin_selection.select_npc_type" : skinSettings.getNpcTypeName()) {
            @Override
            protected void onPressed() {
                showScreen(new GuiSelectFromList<>(GuiSkinSelection.this, skinSettings.getNpcTypeName(), s -> s, this::getNpcTypes, s -> {
                    setText(s);
                    skinSettings.setNpcTypeName(s);
                    skinSettings.resetNpcTypeSkin();
                    refreshGui();
                }));
            }

            private List<String> getNpcTypes() {
                return NpcSkinManager.getSkinTypes().stream().sorted().collect(Collectors.toList());
            }
        });
        startHeight += 16;

        Checkbox random = new Checkbox(SUBCONTROLS_INDENT, startHeight, 16, 16, "gui.ancientwarfarenpc.skin_selection.random") {
            @Override
            public void onToggled() {
                super.onToggled();
                skinSettings.setRandom(checked());
                refreshGui();
            }
        };
        random.setChecked(skinSettings.isRandom());
        addGuiElement(random);
        startHeight += 20;

        if (!skinSettings.getNpcTypeName().isEmpty() && !skinSettings.isRandom()) {
            addGuiElement(new Button(SUBCONTROLS_INDENT, startHeight, 150, 12,
                    skinSettings.getNpcTypeSkin().map(GuiSkinSelection::skinName).orElse("gui.ancientwarfarenpc.skin_selection.select_npc_skin")) {
                @Override
                protected void onPressed() {
                    showScreen(new GuiSelectFromList<ResourceLocation>(GuiSkinSelection.this, skinSettings.getNpcTypeSkin().orElse(null),
                            rl -> rl == null ? "" : skinName(rl), this::getNpcSkins, rl -> {
                        setText(skinName(rl));
                        skinSettings.setNpcTypeSkin(rl);
                    }));
                }

                private List<ResourceLocation> getNpcSkins() {
                    return NpcSkinManager.getTypeSkins(skinSettings.getNpcTypeName()).stream().sorted().collect(Collectors.toList());
                }
            });
            startHeight += 16;
        }
        return startHeight;
    }

    private int addPlayerElements(int startHeight) {
        addGuiElement(new Text(SUBCONTROLS_INDENT, startHeight, 100, skinSettings.getPlayerName(), this) {
            @Override
            public void onTextUpdated(String oldText, String newText) {
                skinSettings.setPlayerName(newText);
                updatePlayerSkin = true;
            }
        });
        return startHeight + 16;
    }

    @Override
    protected boolean onGuiCloseRequested() {
        skinMenu.setSkinSettings(skinSettings);
        skinMenu.handleNpcSkinUpdate();
        parent.bindMenu();
        showScreen(parent);
        parent.refreshGui();
        return false;
    }
}
