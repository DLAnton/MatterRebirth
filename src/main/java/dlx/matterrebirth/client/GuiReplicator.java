package dlx.matterrebirth.client;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.inventory.ContainerReplicator;
import dlx.matterrebirth.inventory.ReplicatorInventory;
import dlx.matterrebirth.tile.TileReplicator;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно репликатора клонов. Раскладка как у репликатора IC2, но выше на 12 (176x196): зазор над инвентарём для кнопок модов сортировки. Слоты и экран свои,
 * а панель со скруглённой рамкой, резервуар, стрелка подачи UU и молния заряда берутся из текстур самого IC2, чтобы выглядеть как в его машинах.
 * Шаблон, модель клона, стадии и кнопки режима появятся на этапе 4.
 */
@SideOnly(Side.CLIENT)
public class GuiReplicator extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(MatterRebirth.MOD_ID, "textures/gui/replicator.png");
    private static final ResourceLocation IC2_COMMON = new ResourceLocation("ic2", "textures/gui/common.png");
    private static final ResourceLocation IC2_REPLICATOR = new ResourceLocation("ic2", "textures/gui/guireplicator.png");

    // всё по сетке слотов: верхний блок 7..78 (высота 4 слотов), нижний ряд 82..99
    // экран
    private static final int SX = 51, SY = 8, SW = 96;
    // резервуар IC2 20x55, вытянутый до 72: пустой (70,100); с жидкостью фон (6,100), жидкость со сдвигом (4,4),
    // шкала (38,100). Деления берутся строками из текстуры и расставляются на 8 равных частей.
    private static final int TANK_X = 27, TANK_Y = 7, TANK_H = 72;
    private static final int[] TICK_ROWS = {9, 15, 21, 27, 21, 15, 9};
    // стрелка IC2 «капсула → бак → капсула», в guireplicator.png (13,45) 14x25, вытянутая до 34 рядов
    private static final int ARROW_X = 13, ARROW_Y = 26;
    // молния IC2: фон (96,64) 16x16 со сдвигом (-4,-1), заливка (116,65) 7x13
    private static final int BOLT_X = 33, BOLT_Y = 84; // под баком, рядом с резервом (раскладка Антона 06.11)

    private static final int YELLOW = 0xFFD84A, GREY = 0x9A9A9A;

    private final ContainerReplicator container;

    public GuiReplicator(InventoryPlayer playerInv, TileReplicator tile) {
        super(new ContainerReplicator(playerInv, tile));
        this.container = (ContainerReplicator) inventorySlots;
        xSize = 176;
        ySize = 196; // 184 как у репликатора IC2 + 12 над инвентарём под кнопки модов сортировки
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        List<String> tip = new ArrayList<>();
        if (mx >= BOLT_X - 4 && mx < BOLT_X + 12 && my >= BOLT_Y - 1 && my < BOLT_Y + 15) {
            tip.add(I18n.format("gui.matterrebirth.replicator.charge", container.getEnergy(), container.getCapacity()));
            String name = tierName(container.getTier());
            tip.add(name.isEmpty() ? I18n.format("gui.matterrebirth.replicator.tier_plain", container.getTier())
                    : I18n.format("gui.matterrebirth.replicator.tier", container.getTier(), name));
        } else if (mx >= TANK_X && mx < TANK_X + 20 && my >= TANK_Y && my < TANK_Y + TANK_H) {
            tip.add(I18n.format("gui.matterrebirth.replicator.tank_empty"));
        }
        if (!tip.isEmpty()) {
            drawHoveringText(tip, mouseX, mouseY);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        mc.getTextureManager().bindTexture(IC2_COMMON);
        drawPanel(guiLeft, guiTop, xSize, ySize);
        GlStateManager.enableBlend();
        mc.getTextureManager().bindTexture(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        mc.getTextureManager().bindTexture(IC2_REPLICATOR);
        int ax = guiLeft + ARROW_X, ay = guiTop + ARROW_Y;
        for (int r = 0; r < 12; r++) {
            drawTexturedModalRect(ax, ay + r, 13, 45, 14, 1);
        }
        drawTexturedModalRect(ax, ay + 12, 13, 52, 14, 11);
        for (int r = 23; r < 34; r++) {
            drawTexturedModalRect(ax, ay + r, 13, 45, 14, 1);
        }

        mc.getTextureManager().bindTexture(IC2_COMMON);
        // бак пока всегда пустой: приём UU появится в шаге 3г, тогда здесь будет второе состояние (фон 6, шкала 38)
        drawTankFrame(70, guiLeft + TANK_X, guiTop + TANK_Y, TANK_H);
        drawTankTicks(70, guiLeft + TANK_X, guiTop + TANK_Y, TANK_H);
        drawTexturedModalRect(guiLeft + BOLT_X - 4, guiTop + BOLT_Y - 1, 96, 64, 16, 16);
        int cap = container.getCapacity();
        if (cap > 0 && container.getEnergy() > 0) {
            int h = Math.max(1, Math.min(13, Math.round(13f * container.getEnergy() / cap)));
            drawTexturedModalRect(guiLeft + BOLT_X, guiTop + BOLT_Y + 13 - h, 116, 65 + 13 - h, 7, h);
        }
    }

    /** Рамка бака IC2 любой высоты: 4 верхних ряда, гладкий ряд повторяется, 4 нижних ряда. */
    private void drawTankFrame(int u, int x, int y, int h) {
        drawTexturedModalRect(x, y, u, 100, 20, 4);
        for (int r = 4; r < h - 4; r++) {
            drawTexturedModalRect(x, y + r, u, 104, 20, 1);
        }
        drawTexturedModalRect(x, y + h - 4, u, 151, 20, 4);
    }

    /** Деления шкалы: 7 штук на 8 равных частей, средняя длинная. */
    private void drawTankTicks(int u, int x, int y, int h) {
        int inner = h - 8;
        for (int k = 0; k < TICK_ROWS.length; k++) {
            drawTexturedModalRect(x, y + 4 + Math.round(inner * (k + 1) / 8f), u, 100 + TICK_ROWS[k], 20, 1);
        }
    }

    /** Панель машины IC2: заготовка 64x64 в common.png (16,16), углы 16x16, края и середина повторяются кусками. */
    private void drawPanel(int x, int y, int w, int h) {
        drawTexturedModalRect(x, y, 16, 16, 16, 16);
        drawTexturedModalRect(x + w - 16, y, 64, 16, 16, 16);
        drawTexturedModalRect(x, y + h - 16, 16, 64, 16, 16);
        drawTexturedModalRect(x + w - 16, y + h - 16, 64, 64, 16, 16);
        tile(32, 16, x + 16, y, w - 32, 16);
        tile(32, 64, x + 16, y + h - 16, w - 32, 16);
        tile(16, 32, x, y + 16, 16, h - 32);
        tile(64, 32, x + w - 16, y + 16, 16, h - 32);
        tile(32, 32, x + 16, y + 16, w - 32, h - 32);
    }

    /** Заполняет область кусками до 32x32 из common.png начиная с (u,v). */
    private void tile(int u, int v, int x, int y, int w, int h) {
        int tw = Math.min(32, w), th = Math.min(32, h);
        for (int dy = 0; dy < h; dy += th) {
            for (int dx = 0; dx < w; dx += tw) {
                drawTexturedModalRect(x + dx, y + dy, u, v, Math.min(tw, w - dx), Math.min(th, h - dy));
            }
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // трансформатор, без которого не работает накопитель в резерве, затемнён: снять его нельзя
        ReplicatorInventory inv = container.getTile().getInventory();
        GlStateManager.disableDepth();
        for (int i = 0; i < ReplicatorInventory.UPGRADE_COUNT; i++) {
            int slot = ReplicatorInventory.UPGRADE_FIRST + i;
            ItemStack stack = inv.getStackInSlot(slot);
            if (ReplicatorInventory.upgradeKind(stack) == ReplicatorInventory.Upgrade.TRANSFORMER
                    && inv.extractItem(slot, 1, true).isEmpty()) {
                drawRect(152, 8 + i * 18, 168, 24 + i * 18, 0x5A000000); // мягкая маска: значок остаётся различим (правка Антона 06.11)
            }
        }
        GlStateManager.enableDepth();
        // заголовка нет, как у репликатора IC2: верх окна занят рядом слотов и экраном
        // в шапке экрана ник из выбранного шаблона крови; выбор шаблона появится на этапе 4
        String pattern = I18n.format("gui.matterrebirth.replicator.no_pattern");
        fontRenderer.drawString(pattern, SX + (SW - fontRenderer.getStringWidth(pattern)) / 2, SY + 2, GREY);
        fontRenderer.drawString(I18n.format("gui.matterrebirth.replicator.idle"), SX + 4, SY + 16, YELLOW);
    }

    private static String tierName(int tier) {
        switch (tier) {
            case 1: return "LV";
            case 2: return "MV";
            case 3: return "HV";
            case 4: return "EV";
            case 5: return "IV";
            default: return "";
        }
    }
}
