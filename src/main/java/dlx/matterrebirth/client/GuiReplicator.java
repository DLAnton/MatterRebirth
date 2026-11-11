package dlx.matterrebirth.client;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.compat.jei.MRJeiPlugin;
import dlx.matterrebirth.inventory.ContainerReplicator;
import dlx.matterrebirth.inventory.ReplicatorInventory;
import dlx.matterrebirth.tile.TileReplicator;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно репликатора клонов. Раскладка как у репликатора IC2, но выше на 12 (176x196): зазор над инвентарём для кнопок модов сортировки. Слоты и экран свои,
 * а панель со скруглённой рамкой, резервуар, стрелка подачи UU и молния заряда берутся из текстур самого IC2, чтобы выглядеть как в его машинах.
 * Шаблон и кнопки (пауза, растить заново, корзина) есть с этапа 4а. Экран «Репликация» (этап 4б, макеты Антона 07.11):
 * шкала стадий слева, пунктирный каркас клона с линией печати, справа заголовок и список стадий, между кнопками строка состояния.
 * Послойная модель организма и клон в скине владельца появятся позже (этапы 4в и 5), пока на их месте каркас.
 */
@SideOnly(Side.CLIENT)
public class GuiReplicator extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(MatterRebirth.MOD_ID, "textures/gui/replicator.png");
    private static final ResourceLocation IC2_COMMON = new ResourceLocation("ic2", "textures/gui/common.png");
    private static final ResourceLocation IC2_REPLICATOR = new ResourceLocation("ic2", "textures/gui/guireplicator.png");
    // кнопка «i» IC2 (RecipeButton, значок 10x10 в infobutton.png): у нас открывает страницу машины в HEI.
    // Слева над инвентарём, зеркально кнопкам сортировки справа (решение Антона 07.11: угол занят капсулой UU)
    private static final ResourceLocation IC2_INFO = new ResourceLocation("ic2", "textures/gui/infobutton.png");
    private static final int INFO_X = 7, INFO_Y = 102;

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

    private static final int YELLOW = 0xFFD84A, GREY = 0x9A9A9A, WHITE = 0xE6E6E6;
    // стрелки выбора шаблона в шапке экрана (значки 4x7 в нашей текстуре: серые y=28, под мышью y=35)
    private static final int PAT_Y = SY + 2, PAT_L_X = SX + 3, PAT_R_X = SX + SW - 7;
    // кнопки 12x12 внизу экрана, как у сканера IC2: слева пауза/продолжить и «растить заново», справа корзина
    private static final int BTN_Y = SY + 91 - 14, PAUSE_X = SX + 3, REPEAT_X = PAUSE_X + 14, TRASH_X = SX + SW - 3 - 12;

    // экран «Репликация»: шкала стадий слева (рамка 6x60), справа заголовок и список, модель стоит низом на AREA_BOTTOM
    private static final int BAR_X = SX + 3, BAR_Y = SY + 14, BAR_H = 60;
    private static final int TEXT_X = SX + 44, TITLE_Y = SY + 16, LIST_Y = SY + 28, LIST_STEP = 9;
    private static final int STATUS_CX = SX + 55; // строка состояния по центру между «растить заново» и корзиной
    private static final int AREA_CX = SX + 26, AREA_BOTTOM = SY + 73;
    // цвета стадий снизу вверх: скелет, нервы, сосуды, мышцы, кожа; выключенный экран — всё серое
    private static final int[] STAGE_COLORS = {0xE2DABE, 0xEBCD46, 0xCD3737, 0xB9555F, 0xDEAA87};
    private static final int BAR_FRAME = 0x3C3C3C;
    private static final int TITLE = 0xC8C8C8, DONE = 0x5ADC6E, DIM = 0x505050, PAUSE_GREY = 0x969696, RED = 0xDC4646;
    private static final int HEALTH_RED = 0xC82828;
    private static final int GROW_CYAN = 0x5CC8C8; // рост бирюзовый, как кольца машины (Антон 08.11) // шкала здоровья на экране «Поддержание» (макет 07.11)
    private static final String[] STAGE_KEYS = {"skeleton", "nerves", "vessels", "muscles", "skin"};

    /*
     * Пунктирный каркас клона (как чертёж): фигура игрока из 6 коробок в проекции макетов (поворот 30°, наклон 25°,
     * 1,74 пикселя окна на единицу модели). Видимые грани рисуются от дальних к ближним: чёрная заливка закрывает
     * задние линии, по краям тонкий пунктир. Считается один раз.
     */
    private static final float[][] GHOST_FACES; // по 8 чисел: 4 точки x,y от левого верхнего угла каркаса
    private static final float GHOST_W, GHOST_H;
    /** Начало координат каркаса на экране относительно его левого верхнего угла (для клона в окне, 5б+). */
    private static final float GHOST_OX, GHOST_OY, GHOST_UNIT;

    static {
        double th = Math.toRadians(30), ph = Math.toRadians(25), unit = 1.74;
        int[][] boxes = {{0, 24, -2, 8, 8, 8}, {0, 12, 0, 8, 12, 4}, {-4, 12, 0, 4, 12, 4}, {8, 12, 0, 4, 12, 4},
                {0, 0, 0, 4, 12, 4}, {4, 0, 0, 4, 12, 4}}; // голова, тело, руки, ноги: x, y, z, ширина, высота, глубина
        int[][] normals = {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
        int[][][] quads = {
                {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}}, {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
                {{1, 0, 0}, {1, 0, 1}, {1, 1, 1}, {1, 1, 0}}, {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},
                {{0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}}, {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}}};
        List<double[]> faces = new ArrayList<>(); // [глубина, x0, y0, ... x3, y3]
        for (int[] b : boxes) {
            for (int f = 0; f < 6; f++) {
                if (project(normals[f][0], normals[f][1], normals[f][2], th, ph)[2] >= -1e-6) {
                    continue; // грань смотрит от зрителя
                }
                double[] face = new double[9];
                for (int k = 0; k < 4; k++) {
                    int[] q = quads[f][k];
                    double[] p = project(b[0] + q[0] * b[3], b[1] + q[1] * b[4], b[2] + q[2] * b[5], th, ph);
                    face[1 + k * 2] = p[0];
                    face[2 + k * 2] = p[1];
                    face[0] += p[2] / 4;
                }
                faces.add(face);
            }
        }
        faces.sort((a, b) -> Double.compare(b[0], a[0])); // дальние первыми
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (double[] f : faces) {
            for (int k = 0; k < 4; k++) {
                minX = Math.min(minX, f[1 + k * 2]);
                maxX = Math.max(maxX, f[1 + k * 2]);
                minY = Math.min(minY, f[2 + k * 2]);
                maxY = Math.max(maxY, f[2 + k * 2]);
            }
        }
        GHOST_FACES = new float[faces.size()][8];
        for (int i = 0; i < faces.size(); i++) {
            for (int k = 0; k < 4; k++) {
                GHOST_FACES[i][k * 2] = (float) ((faces.get(i)[1 + k * 2] - minX) * unit);
                GHOST_FACES[i][k * 2 + 1] = (float) ((faces.get(i)[2 + k * 2] - minY) * unit);
            }
        }
        GHOST_W = (float) ((maxX - minX) * unit);
        GHOST_H = (float) ((maxY - minY) * unit);
        GHOST_OX = (float) (-minX * unit);
        GHOST_OY = (float) (-minY * unit);
        GHOST_UNIT = (float) unit;
    }

    /** Поворот вокруг вертикали на th, наклон на ph; y экрана вниз. Возвращает x, y, глубину (больше — дальше). */
    private static double[] project(double x, double y, double z, double th, double ph) {
        double x1 = x * Math.cos(th) + z * Math.sin(th), z1 = -x * Math.sin(th) + z * Math.cos(th);
        double y2 = y * Math.cos(ph) + z1 * Math.sin(ph), z2 = -y * Math.sin(ph) + z1 * Math.cos(ph);
        return new double[]{x1, -y2, z2};
    }

    // подтверждение корзины в самом экране (макет 07.11): рамка 80x42, кнопки «Да» и «Нет» 22x12
    private static final int CONF_X = SX + 8, CONF_Y = SY + 24, CONF_W = SW - 16, CONF_H = 42;
    private static final int YES_X = CONF_X + 8, NO_X = CONF_X + CONF_W - 30, CONF_BTN_Y = CONF_Y + 26;

    private final ContainerReplicator container;
    /**
     * Плавные шкалы (Антон 08.11): значения с сервера приходят ступенями (тысячные, раз в тик), окно догоняет их
     * понемногу каждый тик и рисует между тиками с дробными координатами — линия печати, шкала стадий и здоровья,
     * бак и молния движутся без рывков. Прогресс стадии при смене стадии — сразу.
     */
    private final Smooth progress = new Smooth(), healthBar = new Smooth(), fluidBar = new Smooth(), energyBar = new Smooth();
    private int smoothStage = -1;
    private float partial;
    /** Открыто подтверждение корзины (только в этом окне, на клиенте). */
    private boolean confirming;
    /** HEI/JEI установлен: без него кнопки «i» нет. */
    private final boolean infoOn = Loader.isModLoaded("jei");

    public GuiReplicator(InventoryPlayer playerInv, TileReplicator tile) {
        super(new ContainerReplicator(playerInv, tile));
        this.container = (ContainerReplicator) inventorySlots;
        xSize = 176;
        ySize = 196; // 184 как у репликатора IC2 + 12 над инвентарём под кнопки модов сортировки
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        int stage = container.getStage();
        progress.tick(container.getStageProgress(), stage != smoothStage);
        smoothStage = stage;
        healthBar.tick(container.getHealthFraction(), false);
        fluidBar.tick(container.getFluid(), container.getFluid() <= 0);
        int cap = container.getCapacity();
        energyBar.tick(cap > 0 ? (float) container.getEnergy() / cap : 0, container.getEnergy() <= 0);
    }

    /** Прогресс текущей стадии для шкал и модели, 0..1, плавно. */
    private float shownProgress() {
        return container.getStage() == smoothStage ? progress.get(partial, container.getStageProgress()) : container.getStageProgress();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        partial = partialTicks;
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        renderHoveredToolTip(mouseX, mouseY);
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        List<String> tip = new ArrayList<>();
        if (infoAt(mx, my)) {
            tip.add(I18n.format("gui.matterrebirth.replicator.info"));
        } else if (mx >= BOLT_X - 4 && mx < BOLT_X + 12 && my >= BOLT_Y - 1 && my < BOLT_Y + 15) {
            // как у предметов IC2: «954 k / 1 M EU», формат самой IC2 (Util.toSiString, 3 значащие цифры; Антон 07.11)
            // заряд — вниз до 3 значащих цифр: почти полный запас (999 984) не округляется в «1000 k» (Антон 11.11)
            tip.add(I18n.format("gui.matterrebirth.replicator.charge", ic2.core.util.Util.toSiString(floor3(container.getEnergy()), 3),
                    ic2.core.util.Util.toSiString(container.getCapacity(), 3)));
            String name = tierName(container.getTier());
            tip.add(name.isEmpty() ? I18n.format("gui.matterrebirth.replicator.tier_plain", container.getTier())
                    : I18n.format("gui.matterrebirth.replicator.tier", container.getTier(), name));
        } else if (confirming && mx >= SX && mx < SX + SW && my >= SY && my < SY + 91) {
            // подтверждение закрывает экран: подсказок под ним нет
        } else if (buttonAt(PAUSE_X, mx, my)) {
            // тёмные кнопки без подсказок, как весь выключенный экран
            if (pauseOn()) {
                String key = !container.getTile().isPaused() ? "pause" : container.hasGrowth() ? "resume" : "start";
                tip.add(I18n.format("gui.matterrebirth.replicator." + key));
            }
        } else if (buttonAt(REPEAT_X, mx, my)) {
            if (repeatOn()) {
                tip.add(I18n.format(container.getTile().isRepeat() ? "gui.matterrebirth.replicator.repeat_on" : "gui.matterrebirth.replicator.repeat_off"));
            }
        } else if (buttonAt(TRASH_X, mx, my)) {
            if (trashOn()) {
                tip.add(I18n.format("gui.matterrebirth.replicator.trash"));
            }
        } else if (my >= BTN_Y && my < BTN_Y + 12 && mx >= REPEAT_X + 12 && mx < TRASH_X) {
            String statusTip = statusTip();
            if (statusTip != null) {
                tip.addAll(java.util.Arrays.asList(statusTip.split("\\\\n"))); // в lang строки разделены буквальным \n
            }
        } else if (arrowsOn() && arrowAt(mx, my) >= 0) {
            tip.add(I18n.format("gui.matterrebirth.replicator.pattern_" + (arrowAt(mx, my) == TileReplicator.BUTTON_NEXT ? "next" : "prev")));
        } else if (mx >= TANK_X && mx < TANK_X + 20 && my >= TANK_Y && my < TANK_Y + TANK_H) {
            int amount = container.getFluid();
            tip.add(amount > 0 ? I18n.format("gui.matterrebirth.replicator.tank", amount, TileReplicator.TANK_CAPACITY)
                    : I18n.format("gui.matterrebirth.replicator.tank_empty"));
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
        // два состояния бака, как у IC2: пустой (70) или фон (6) + жидкость + шкала поверх (38)
        int tx = guiLeft + TANK_X, ty = guiTop + TANK_Y;
        if (container.getFluid() > 0) {
            drawTankFrame(6, tx, ty, TANK_H);
            drawFluid(tx + 4, ty + 4, 12, TANK_H - 8, fluidBar.get(partial, container.getFluid()), TileReplicator.TANK_CAPACITY);
            mc.getTextureManager().bindTexture(IC2_COMMON);
            drawTankTicks(38, tx, ty, TANK_H);
        } else {
            drawTankFrame(70, tx, ty, TANK_H);
            drawTankTicks(70, tx, ty, TANK_H);
        }
        drawTexturedModalRect(guiLeft + BOLT_X - 4, guiTop + BOLT_Y - 1, 96, 64, 16, 16);
        if (infoOn) {
            mc.getTextureManager().bindTexture(IC2_INFO);
            drawTexturedModalRect(guiLeft + INFO_X, guiTop + INFO_Y, 0, 0, 10, 10);
            if (infoAt(mouseX - guiLeft, mouseY - guiTop)) {
                drawRect(guiLeft + INFO_X, guiTop + INFO_Y, guiLeft + INFO_X + 10, guiTop + INFO_Y + 10, 0x40FFFFFF);
                GlStateManager.enableBlend(); // drawRect выключает смешивание, а значки ниже рисуются с прозрачностью
                GlStateManager.color(1, 1, 1, 1);
            }
        }
        // значки экрана: стрелки шаблона и кнопки режима (своя текстура)
        mc.getTextureManager().bindTexture(TEXTURE);
        int mx = mouseX - guiLeft, my = mouseY - guiTop;
        // тёмно-серые (значки y=44, стрелки y=52) — не работают: весь экран без заряда; стрелки, пока в камере что-то есть;
        // пауза, «растить заново» и корзина без шаблона (Антон 07.11); пауза и корзина у готового клона (корзина — до 4в)
        if (arrowsOn()) {
            int arrow = confirming ? -1 : arrowAt(mx, my);
            drawTexturedModalRect(guiLeft + PAT_L_X, guiTop + PAT_Y, 180, arrow == TileReplicator.BUTTON_PREV ? 35 : 28, 4, 7);
            drawTexturedModalRect(guiLeft + PAT_R_X, guiTop + PAT_Y, 184, arrow == TileReplicator.BUTTON_NEXT ? 35 : 28, 4, 7);
        } else {
            drawTexturedModalRect(guiLeft + PAT_L_X, guiTop + PAT_Y, 180, 52, 4, 7);
            drawTexturedModalRect(guiLeft + PAT_R_X, guiTop + PAT_Y, 184, 52, 4, 7);
        }
        // значки 8x8 в текстуре (x = 180 + 8*n): 0 корзина, 1 продолжить, 2 растить заново, 3 пауза, 4 растить заново зелёный (только y=12)
        // пауза/продолжить: значок показывает, что сделает нажатие (идёт рост — пауза, стоит — продолжить)
        int pauseGlyph = container.getTile().isPaused() ? 1 : 3;
        if (pauseOn()) {
            boolean hover = !confirming && buttonAt(PAUSE_X, mx, my); // под окном подтверждения без подсветки
            drawButton(PAUSE_X, hover ? 204 : 180, pauseGlyph, hover ? 20 : 12);
        } else {
            drawButton(PAUSE_X, 180, pauseGlyph, 44);
        }
        // растить заново: выключен — серый, включён — зелёный
        if (repeatOn()) {
            boolean repeat = container.getTile().isRepeat();
            boolean hover = !confirming && buttonAt(REPEAT_X, mx, my); // под окном подтверждения без подсветки
            drawButton(REPEAT_X, repeat ? 216 : hover ? 204 : 180, repeat ? 4 : 2, hover ? 20 : 12);
        } else if (container.getTile().isRepeat()) {
            // тёмная кнопка при включённом «растить заново» — приглушённо-зелёная: видно, что режим включён (Антон 08.11)
            GlStateManager.color(0.45F, 0.45F, 0.45F, 1F);
            drawButton(REPEAT_X, 216, 4, 12);
            GlStateManager.color(1, 1, 1, 1);
        } else {
            drawButton(REPEAT_X, 180, 2, 44);
        }
        if (trashOn()) {
            boolean hover = !confirming && buttonAt(TRASH_X, mx, my); // под окном подтверждения без подсветки
            drawButton(TRASH_X, hover ? 204 : 180, 0, hover ? 20 : 12);
        } else {
            drawButton(TRASH_X, 180, 0, 44);
        }
        mc.getTextureManager().bindTexture(IC2_COMMON);
        int cap = container.getCapacity();
        if (cap > 0 && container.getEnergy() > 0) {
            float h = Math.max(1, Math.min(13, 13f * energyBar.get(partial, (float) container.getEnergy() / cap)));
            texRect(guiLeft + BOLT_X, guiTop + BOLT_Y + 13 - h, 116, 65 + 13 - h, 7, h);
        }
        drawStageBar();
        // клон слоями на месте каркаса (5б+, Антон 08.11); каркас остаётся контуром ещё не выращенного, у готового
        // и погибшего клона (он виден сам: бледный, обмякший) каркаса нет
        TileReplicator tile = container.getTile();
        if (!ready() && !tile.isDead()) {
            drawGhost();
        }
        float gx = guiLeft + AREA_CX - GHOST_W / 2, gy = guiTop + AREA_BOTTOM - GHOST_H;
        float shown = container.getStage() == tile.getStage() ? shownProgress() : tile.getShownGrowth(partialTicks);
        CloneRenderer.renderGui(tile, shown, gx + GHOST_OX, gy + GHOST_OY, 100, GHOST_UNIT);
        drawScanLine();
        GlStateManager.color(1, 1, 1, 1);
    }

    /** Доля выращенного в стадии i: ниже текущей — целиком, текущая — по прогрессу, выше — ничего. */
    private float stageFill(int i) {
        int stage = container.getStage();
        return i < stage ? 1 : i == stage ? shownProgress() : 0;
    }

    /** Шкала стадий слева: высоты отрезков по времени стадий, снизу вверх; выключенный экран — заливка серая. */
    private void drawStageBar() {
        int x = guiLeft + BAR_X, top = guiTop + BAR_Y;
        drawRect(x, top, x + 6, top + BAR_H, 0xFF000000 | BAR_FRAME);
        if (ready()) {
            // «Поддержание»: та же шкала становится красной шкалой здоровья, одним отрезком снизу вверх
            int inner = BAR_H - 2;
            drawRect(x + 1, top + 1, x + 5, top + BAR_H - 1, 0xFF000000);
            float f = inner * Math.max(0, Math.min(1, healthBar.get(partial, container.getHealthFraction())));
            fill(x + 1, top + BAR_H - 1 - f, x + 5, top + BAR_H - 1, 0xFF000000 | HEALTH_RED);
            return;
        }
        int total = 0;
        for (int t : TileReplicator.STAGE_SHARE) { // время стадий делится в этой же пропорции при любых настройках (5в+)
            total += t;
        }
        int inner = BAR_H - 2 - (TileReplicator.STAGES - 1); // между отрезками перегородки в 1 пиксель
        int y = top + BAR_H - 1;
        for (int i = 0; i < TileReplicator.STAGES; i++) {
            int h = i < TileReplicator.STAGES - 1 ? Math.round((float) inner * TileReplicator.STAGE_SHARE[i] / total) : y - (top + 1);
            y -= h;
            drawRect(x + 1, y, x + 5, y + h, 0xFF000000);
            float f = h * stageFill(i);
            fill(x + 1, y + h - f, x + 5, y + h, 0xFF000000 | STAGE_COLORS[i]); // экран показывает состояние и без заряда (Антон 07.11)
            y -= 1;
        }
    }

    /** Пунктирный каркас клона на месте модели: тонкие линии в пиксель экрана, пунктир 4 через 4. */
    private void drawGhost() {
        float x0 = guiLeft + AREA_CX - GHOST_W / 2, y0 = guiTop + AREA_BOTTOM - GHOST_H;
        // клон погиб — каркас красный (особая иконка и вид машины — отдельное обсуждение, Антон 08.11)
        boolean dead = container.getStatus() == TileReplicator.Status.DEAD;
        float c = 85 / 255f, r = dead ? 0.6f : c, g = dead ? 0.15f : c, b = dead ? 0.15f : c;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.glLineWidth(1.0F);
        for (float[] f : GHOST_FACES) {
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
            for (int k = 0; k < 4; k++) {
                buf.pos(x0 + f[k * 2], y0 + f[k * 2 + 1], zLevel).color(0f, 0f, 0f, 1f).endVertex();
            }
            tess.draw();
            GL11.glEnable(GL11.GL_LINE_STIPPLE);
            GL11.glLineStipple(1, (short) 0x0F0F);
            buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
            for (int k = 0; k < 4; k++) {
                int n = (k + 1) % 4;
                buf.pos(x0 + f[k * 2] + 0.5f, y0 + f[k * 2 + 1] + 0.5f, zLevel).color(r, g, b, 1f).endVertex();
                buf.pos(x0 + f[n * 2] + 0.5f, y0 + f[n * 2 + 1] + 0.5f, zLevel).color(r, g, b, 1f).endVertex();
            }
            tess.draw();
            GL11.glDisable(GL11.GL_LINE_STIPPLE);
        }
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
    }

    /** Линия печати: идёт по каркасу снизу вверх на высоте прогресса текущей стадии. */
    private void drawScanLine() {
        int stage = container.getStage();
        if (stage >= TileReplicator.STAGES || !container.hasGrowth()) {
            return;
        }
        // цвет как у значка текущей стадии в списке: рост бирюзовый, ожидание жёлтое, откат красный, пауза серая (Антон 07–08.11)
        int rgb = stageMarkColor();
        float y = guiTop + AREA_BOTTOM - GHOST_H * shownProgress();
        int x1 = guiLeft + Math.round(AREA_CX - GHOST_W / 2) - 3, x2 = guiLeft + Math.round(AREA_CX + GHOST_W / 2) + 1; // не доходит до точек списка
        fill(x1, y - 1, x2, y, 0x40000000 | rgb);
        fill(x1, y, x2, y + 1, 0xB0000000 | rgb);
        fill(x1, y + 1, x2, y + 2, 0x40000000 | rgb);
    }

    /** Цвет текущей стадии: рост бирюзовый, откат красный, пауза серая, иначе (ждёт UU, энергию) жёлтый. */
    private int stageMarkColor() {
        switch (container.getStatus()) {
            case GROWING:
                return GROW_CYAN;
            case ROLLBACK:
            case NO_POWER:
            case OVERLOAD:
                return RED;
            case PAUSED:
                return PAUSE_GREY;
            default:
                return YELLOW;
        }
    }

    /** Клон готов: экран «Поддержание». */
    private boolean ready() {
        return container.getStage() >= TileReplicator.STAGES;
    }

    private boolean hasPattern() {
        return !container.getTile().getPattern().isEmpty();
    }

    /** Стрелки шаблона работают, пока камера пуста (иначе сначала корзина). */
    private boolean arrowsOn() {
        return powered() && !container.hasGrowth();
    }

    /**
     * Пауза: есть шаблон и клон ещё растёт; в «Диапаузе» и «Перегрузке» тёмная — рост и так стоит, нажать нечего (Антон 07.11).
     * Те же правила на сервере: TileReplicator.canPause.
     */
    private boolean pauseOn() {
        return powered() && hasPattern() && container.getStage() < TileReplicator.STAGES
                && container.getStatus() != TileReplicator.Status.CHARGING
                && container.getStatus() != TileReplicator.Status.OVERLOAD;
    }

    private boolean repeatOn() {
        return powered() && hasPattern();
    }

    /** Корзина: есть шаблон (при непустой камере — через подтверждение). Те же правила: TileReplicator.canReset. */
    private boolean trashOn() {
        return powered() && hasPattern();
    }

    private static boolean confirmButtonAt(int x, int mx, int my) {
        return mx >= x && mx < x + 22 && my >= CONF_BTN_Y && my < CONF_BTN_Y + 12;
    }

    /** Подтверждение корзины поверх экрана: затемнение, рамка, вопрос, сколько плоти останется, «Да» и «Нет». */
    private void drawConfirm(int mx, int my) {
        GlStateManager.disableDepth();
        drawRect(SX, SY, SX + SW, SY + 91, 0xAA000000);
        drawRect(CONF_X, CONF_Y, CONF_X + CONF_W, CONF_Y + CONF_H, 0xFF969696);
        drawRect(CONF_X + 1, CONF_Y + 1, CONF_X + CONF_W - 1, CONF_Y + CONF_H - 1, 0xFF000000);
        boolean ready = container.getStage() >= TileReplicator.STAGES;
        String title = I18n.format(ready ? "gui.matterrebirth.replicator.confirm_destroy" : "gui.matterrebirth.replicator.confirm_reset");
        String flesh = I18n.format("gui.matterrebirth.replicator.confirm_flesh", container.getResetFlesh());
        int cx = CONF_X + CONF_W / 2, maxW = CONF_W - 4;
        title = fontRenderer.trimStringToWidth(title, maxW);
        flesh = fontRenderer.trimStringToWidth(flesh, maxW);
        fontRenderer.drawString(title, cx - fontRenderer.getStringWidth(title) / 2, CONF_Y + 4, WHITE);
        fontRenderer.drawString(flesh, cx - fontRenderer.getStringWidth(flesh) / 2, CONF_Y + 14, GREY);
        drawConfirmButton(YES_X, I18n.format("gui.matterrebirth.replicator.confirm_yes"),
                confirmButtonAt(YES_X, mx, my) ? 0xE05050 : 0xAA2828, 0xFF7878, mx, my);
        drawConfirmButton(NO_X, I18n.format("gui.matterrebirth.replicator.confirm_no"),
                confirmButtonAt(NO_X, mx, my) ? 0xB4B4B4 : 0x5F5F5F, WHITE, mx, my);
        GlStateManager.enableDepth();
    }

    private void drawConfirmButton(int x, String text, int frame, int textColor, int mx, int my) {
        drawRect(x, CONF_BTN_Y, x + 22, CONF_BTN_Y + 12, 0xFF000000 | frame);
        drawRect(x + 1, CONF_BTN_Y + 1, x + 21, CONF_BTN_Y + 11, 0xFF000000);
        fontRenderer.drawString(text, x + 11 - fontRenderer.getStringWidth(text) / 2, CONF_BTN_Y + 2, textColor);
    }

    /** Число вниз до 3 значащих цифр (999 984 → 999 000): IC2 при выводе округляет, а полным запас показывать рано. */
    private static double floor3(double v) {
        if (v < 1000) {
            return Math.floor(v);
        }
        double scale = Math.pow(10, Math.floor(Math.log10(v)) - 2);
        return Math.floor(v / scale + 1e-9) * scale;
    }

    /** Подсказка к строке состояния: сколько не хватает UU или энергии, что значит откат. */
    private String statusTip() {
        if (ready()) {
            // поддержание (4в, числа 5в, настройки 5в+): жизнь стоит mB UU в игровой день и EU/t — числа сервера;
            // без них здоровье падает на 1 в минуту
            int life = container.getLifeEu();
            int perDay = container.getLifeUuPerDay();
            switch (container.getStatus()) {
                case NEED_UU:
                    return I18n.format("gui.matterrebirth.replicator.upkeep_no_uu_tip", perDay);
                case NO_POWER:
                    return I18n.format("gui.matterrebirth.replicator.upkeep_no_power_tip", life);
                default:
                    return I18n.format(container.getHealth() < TileReplicator.MAX_HEALTH
                            ? "gui.matterrebirth.replicator.upkeep_heal_tip" : "gui.matterrebirth.replicator.upkeep_tip", perDay, life);
            }
        }
        switch (container.getStatus()) {
            case DEAD:
                return I18n.format("gui.matterrebirth.replicator.dead_tip");
            case NEED_UU:
                return I18n.format("gui.matterrebirth.replicator.need_uu_tip", container.getUuShort());
            case LOW_POWER:
                return I18n.format("gui.matterrebirth.replicator.low_power_tip",
                        Math.round(TileReplicator.growEu(container.getGrowEu(), container.getTile().overclockers())));
            case CHARGING:
                if (container.getTile().isPaused()) {
                    return I18n.format("gui.matterrebirth.replicator.charging_paused_tip", // после зарядки снова пауза
                            container.getHoldEu());
                }
                return I18n.format("gui.matterrebirth.replicator.charging_tip", container.getHoldEu(),
                        Math.round(TileReplicator.growEu(container.getGrowEu(), container.getTile().overclockers())));
            case OVERLOAD:
                long grow = Math.round(TileReplicator.growEu(container.getGrowEu(), container.getTile().overclockers()));
                return I18n.format("gui.matterrebirth.replicator.overload_tip", grow, container.getCapacity() / 2,
                        container.getHoldEu(), grow);
            case ROLLBACK:
                return I18n.format("gui.matterrebirth.replicator.rollback_tip");
            case PAUSED:
                // с выращенным пауза стоит удержания, без него (шаблон ждёт «Начать рост») — ничего (Антон 07.11)
                return container.hasGrowth()
                        ? I18n.format("gui.matterrebirth.replicator.paused_tip", container.getHoldEu())
                        : I18n.format("gui.matterrebirth.replicator.paused_idle_tip");
            default:
                return null;
        }
    }

    /** Кнопка 12x12: рамка (u в ряду y=0) и значок 8x8 по центру. Текстура окна уже привязана. */
    private void drawButton(int x, int frameU, int glyph, int glyphV) {
        drawTexturedModalRect(guiLeft + x, guiTop + BTN_Y, frameU, 0, 12, 12);
        drawTexturedModalRect(guiLeft + x + 2, guiTop + BTN_Y + 2, 180 + glyph * 8, glyph == 4 ? 12 : glyphV, 8, 8);
    }

    /** Машина активна по данным окна (с сервера): заряд в запасе или в кристалле резерва; иначе экран выключен. */
    private boolean powered() {
        return container.isPowered();
    }

    /** Мышь над кнопкой нижнего ряда с левым краем x. Координаты относительно окна. */
    private static boolean buttonAt(int x, int mx, int my) {
        return my >= BTN_Y && my < BTN_Y + 12 && mx >= x && mx < x + 12;
    }

    /** Стрелка шаблона под мышью (BUTTON_PREV / BUTTON_NEXT) или -1; область чуть шире значка, чтобы попадать легче. */
    private static int arrowAt(int mx, int my) {
        if (my < PAT_Y - 1 || my >= PAT_Y + 8) {
            return -1;
        }
        if (mx >= PAT_L_X - 2 && mx < PAT_L_X + 6) {
            return TileReplicator.BUTTON_PREV;
        }
        if (mx >= PAT_R_X - 2 && mx < PAT_R_X + 6) {
            return TileReplicator.BUTTON_NEXT;
        }
        return -1;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (confirming && !trashOn()) {
            confirming = false; // пропало питание или шаблон, пока окно было открыто
        }
        if (mouseButton == 0 && infoAt(mouseX - guiLeft, mouseY - guiTop)) {
            mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            MRJeiPlugin.showMachine(); // вызывается только при установленном HEI: infoAt проверяет infoOn
            return;
        }
        if (confirming && mouseButton == 0) {
            int mx = mouseX - guiLeft, my = mouseY - guiTop;
            if (confirmButtonAt(YES_X, mx, my)) {
                confirming = false;
                mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                mc.playerController.sendEnchantPacket(inventorySlots.windowId, TileReplicator.BUTTON_TRASH);
                return;
            }
            if (confirmButtonAt(NO_X, mx, my)) {
                confirming = false;
                mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return;
            }
            if (mx >= SX && mx < SX + SW && my >= SY && my < SY + 91) {
                return; // остальной экран под подтверждением не нажимается; слоты и инвентарь работают как обычно
            }
        }
        if (mouseButton == 0 && powered() && !confirming) {
            int mx = mouseX - guiLeft, my = mouseY - guiTop;
            int id = arrowsOn() ? arrowAt(mx, my) : -1;
            if (id < 0 && buttonAt(PAUSE_X, mx, my) && pauseOn()) {
                id = TileReplicator.BUTTON_PAUSE;
            }
            if (id < 0 && buttonAt(REPEAT_X, mx, my) && repeatOn()) {
                id = TileReplicator.BUTTON_REPEAT;
            }
            if (id < 0 && buttonAt(TRASH_X, mx, my) && trashOn()) {
                if (container.hasGrowth()) {
                    // в камере что-то есть: сначала подтверждение в экране (решение Антона 07.11)
                    confirming = true;
                    mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return;
                }
                id = TileReplicator.BUTTON_TRASH;
            }
            if (id >= 0) {
                mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                mc.playerController.sendEnchantPacket(inventorySlots.windowId, id);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    /** Рамка бака IC2 любой высоты: 4 верхних ряда, гладкий ряд повторяется, 4 нижних ряда. */
    private void drawTankFrame(int u, int x, int y, int h) {
        drawTexturedModalRect(x, y, u, 100, 20, 4);
        for (int r = 4; r < h - 4; r++) {
            drawTexturedModalRect(x, y + r, u, 104, 20, 1);
        }
        drawTexturedModalRect(x, y + h - 4, u, 151, 20, 4);
    }

    /** Жидкая UU снизу вверх по объёму: текстура жидкости кусками 16x16, как в баках IC2. */
    private void drawFluid(int x, int y, int w, int h, float amount, int capacity) {
        Fluid fluid = FluidRegistry.getFluid(ReplicatorInventory.UU_FLUID);
        if (fluid == null || fluid.getStill() == null || amount <= 0 || capacity <= 0) {
            return;
        }
        float fh = Math.max(1, Math.min(h, h * amount / capacity)); // капля тоже видна
        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(fluid.getStill().toString());
        mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        int color = fluid.getColor();
        GlStateManager.color(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, 1f);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        float u0 = sprite.getMinU(), u1 = sprite.getInterpolatedU(Math.min(16, w));
        for (int done = 0; done < fh; done += 16) {
            float part = Math.min(16, fh - done);
            float bottom = y + h - done, top = bottom - part;
            float v0 = sprite.getInterpolatedV(16 - part), v1 = sprite.getMaxV();
            buf.pos(x, bottom, zLevel).tex(u0, v1).endVertex();
            buf.pos(x + w, bottom, zLevel).tex(u1, v1).endVertex();
            buf.pos(x + w, top, zLevel).tex(u1, v0).endVertex();
            buf.pos(x, top, zLevel).tex(u0, v0).endVertex();
        }
        tess.draw();
        GlStateManager.color(1, 1, 1, 1);
    }

    /** Прямоугольник с дробными координатами (плавные шкалы), цвет ARGB, как drawRect. */
    private static void fill(float x1, float y1, float x2, float y2, int argb) {
        if (x2 <= x1 || y2 <= y1) {
            return;
        }
        float a = (argb >>> 24) / 255F, r = (argb >> 16 & 255) / 255F, g = (argb >> 8 & 255) / 255F, b = (argb & 255) / 255F;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        buf.pos(x1, y2, 0).color(r, g, b, a).endVertex();
        buf.pos(x2, y2, 0).color(r, g, b, a).endVertex();
        buf.pos(x2, y1, 0).color(r, g, b, a).endVertex();
        buf.pos(x1, y1, 0).color(r, g, b, a).endVertex();
        tess.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /** Кусок текстуры 256x256 с дробной высотой и v (молния заряда). */
    private void texRect(float x, float y, float u, float v, float w, float h) {
        float k = 1 / 256F;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buf.pos(x, y + h, zLevel).tex(u * k, (v + h) * k).endVertex();
        buf.pos(x + w, y + h, zLevel).tex((u + w) * k, (v + h) * k).endVertex();
        buf.pos(x + w, y, zLevel).tex((u + w) * k, v * k).endVertex();
        buf.pos(x, y, zLevel).tex(u * k, v * k).endVertex();
        tess.draw();
    }

    /** Значение шкалы, которое плавно догоняет присланное: раз в тик на четверть разницы, между тиками — по partialTicks. */
    private static final class Smooth {
        private float value = Float.NaN, prev;

        void tick(float target, boolean snap) {
            if (Float.isNaN(value) || snap) {
                value = prev = target;
                return;
            }
            prev = value;
            value += (target - value) * 0.25F;
            if (Math.abs(target - value) <= Math.abs(target) * 1e-4F + 1e-4F) {
                value = target;
            }
        }

        /** До первого тика окна — присланное значение как есть. */
        float get(float partialTicks, float fallback) {
            return Float.isNaN(value) ? fallback : prev + (value - prev) * partialTicks;
        }
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
        // в шапке экрана ник из выбранного шаблона крови, стрелки по бокам листают шаблоны из хранилищ у портов
        // экран показывает состояние и без заряда (Антон 07.11: иначе не видно, что выращенное откатывается);
        // без заряда тёмные и не жмутся только стрелки и кнопки
        // текст экрана мелким шрифтом Юникода, как в макетах (по-русски Minecraft и так пишет им; по-английски иначе не влезет)
        boolean unicode = fontRenderer.getUnicodeFlag();
        fontRenderer.setUnicodeFlag(true);
        try {
            drawScreenText();
            if (confirming && !trashOn()) {
                confirming = false;
            }
            if (confirming) {
                drawConfirm(mouseX - guiLeft, mouseY - guiTop);
            }
        } finally {
            fontRenderer.setUnicodeFlag(unicode);
        }
    }

    private void drawScreenText() {
        String name = container.getTile().getPatternName();
        String pattern = name != null ? name : I18n.format("gui.matterrebirth.replicator.no_pattern");
        pattern = fontRenderer.trimStringToWidth(pattern, PAT_R_X - PAT_L_X - 8); // длинный ник не налезает на стрелки
        fontRenderer.drawString(pattern, SX + (SW - fontRenderer.getStringWidth(pattern)) / 2, SY + 2, name != null ? WHITE : GREY);
        if (ready()) {
            drawUpkeepText();
        } else {
            drawGrowthText();
        }
        drawStatusLine();
    }

    /**
     * «Поддержание» (макет 07.11): заголовок, «Здоровье» и N/20, «UU на» и N дн. Пока клон теряет здоровье, число
     * красное; UU кончилась — дни красные.
     */
    private void drawUpkeepText() {
        int maxW = SX + SW - 2 - TEXT_X;
        fontRenderer.drawString(fontRenderer.trimStringToWidth(I18n.format("gui.matterrebirth.replicator.title_upkeep"), maxW),
                TEXT_X, TITLE_Y, TITLE);
        TileReplicator.Status st = container.getStatus();
        boolean falling = st == TileReplicator.Status.NEED_UU || st == TileReplicator.Status.NO_POWER;
        fontRenderer.drawString(fontRenderer.trimStringToWidth(I18n.format("gui.matterrebirth.replicator.health"), maxW),
                TEXT_X, LIST_Y, GREY);
        fontRenderer.drawString(container.getHealth() + "/" + TileReplicator.MAX_HEALTH, TEXT_X, LIST_Y + LIST_STEP,
                falling ? RED : WHITE);
        fontRenderer.drawString(fontRenderer.trimStringToWidth(I18n.format("gui.matterrebirth.replicator.uu_for"), maxW),
                TEXT_X, LIST_Y + LIST_STEP * 2 + 5, GREY);
        // только полные дни; меньше дня — «<1 дн.» (5в, Антон 09.11); UU нет совсем — 0 красным
        int d = container.getUuDays();
        String days = fontRenderer.trimStringToWidth(d == TileReplicator.UU_DAYS_LESS_THAN_ONE
                ? I18n.format("gui.matterrebirth.replicator.uu_days_less") : I18n.format("gui.matterrebirth.replicator.uu_days", d), maxW);
        fontRenderer.drawString(days, TEXT_X, LIST_Y + LIST_STEP * 3 + 5, d != 0 ? WHITE : RED);
    }

    private void drawGrowthText() {
        fontRenderer.drawString(I18n.format("gui.matterrebirth.replicator.title_growth"), TEXT_X, TITLE_Y, TITLE);
        // список стадий сверху вниз (кожа сверху, скелет снизу, как на шкале): готовые зелёные, текущая жёлтая с процентом
        int stage = container.getStage();
        boolean growing = container.hasGrowth() || stage >= TileReplicator.STAGES;
        // значок текущей стадии (предложение Антона 07.11): рост — жёлтая стрелка вправо, откат — красная вниз,
        // пауза — маленький серый значок паузы; текст стадии того же цвета
        int nameX = TEXT_X + fontRenderer.getStringWidth("\u2022 ");
        int maxW = SX + SW - 2 - nameX;
        TileReplicator.Status st = container.getStatus();
        for (int i = 0; i < TileReplicator.STAGES; i++) {
            String stageName = I18n.format("gui.matterrebirth.replicator.stage." + STAGE_KEYS[i]);
            int y = LIST_Y + (TileReplicator.STAGES - 1 - i) * LIST_STEP;
            if (i == stage && growing) {
                int color = stageMarkColor();
                if (st == TileReplicator.Status.PAUSED) {
                    // крошечная пауза размером с точку списка (эскиз Антона 07.11): в полупикселях,
                    // две палочки 1x4 с зазором 1, по центру точки списка (ниже на 2 пикселя — правка 07.11)
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(0.5F, 0.5F, 1.0F);
                    int px = TEXT_X * 2 + 2, py = y * 2 + 9;
                    drawRect(px, py, px + 1, py + 4, 0xFF000000 | color);
                    drawRect(px + 2, py, px + 3, py + 4, 0xFF000000 | color);
                    GlStateManager.popMatrix();
                } else if (st == TileReplicator.Status.CHARGING) {
                    // «Диапауза»: вместо стрелки роста маленькая жёлтая молния (Антон 07.11), в полупикселях 4x5,
                    // на месте точки списка, как значок паузы
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(0.5F, 0.5F, 1.0F);
                    int px = TEXT_X * 2 + 2, py = y * 2 + 8, c = 0xFF000000 | color;
                    drawRect(px + 2, py, px + 4, py + 1, c);
                    drawRect(px + 1, py + 1, px + 3, py + 2, c);
                    drawRect(px, py + 2, px + 4, py + 3, c);
                    drawRect(px + 1, py + 3, px + 3, py + 4, c);
                    drawRect(px, py + 4, px + 2, py + 5, c);
                    GlStateManager.popMatrix();
                } else if (st == TileReplicator.Status.OVERLOAD) {
                    fontRenderer.drawString("!", TEXT_X + 1, y, color); // «Перегрузка»: красный восклицательный знак
                } else {
                    boolean down = st == TileReplicator.Status.ROLLBACK || st == TileReplicator.Status.NO_POWER;
                    fontRenderer.drawString(down ? "\u25BE" : "\u25B8", TEXT_X, y, color);
                }
                String line = stageName + " " + (int) Math.floor(container.getStageProgress() * 100) + "%";
                fontRenderer.drawString(fontRenderer.trimStringToWidth(line, maxW), nameX, y, color);
            } else {
                int color = i < stage ? DONE : DIM;
                fontRenderer.drawString("\u2022", TEXT_X, y, color);
                fontRenderer.drawString(fontRenderer.trimStringToWidth(stageName, maxW), nameX, y, color);
            }
        }
    }

    private void drawStatusLine() {
        String status;
        int color;
        switch (container.getStatus()) {
            case GROWING:
                int sec = container.getSecondsLeft();
                status = sec / 60 + ":" + (sec % 60 < 10 ? "0" : "") + sec % 60;
                color = WHITE;
                break;
            case NEED_UU:
                status = I18n.format("gui.matterrebirth.replicator.need_uu");
                color = YELLOW;
                break;
            case PAUSED:
                status = I18n.format("gui.matterrebirth.replicator.paused");
                color = PAUSE_GREY;
                break;
            case LOW_POWER:
                status = I18n.format("gui.matterrebirth.replicator.low_power");
                color = YELLOW;
                break;
            case CHARGING:
                status = I18n.format("gui.matterrebirth.replicator.charging");
                color = YELLOW;
                break;
            case OVERLOAD:
                status = I18n.format("gui.matterrebirth.replicator.overload");
                color = RED;
                break;
            case ROLLBACK:
                status = I18n.format("gui.matterrebirth.replicator.rollback");
                color = RED;
                break;
            case NO_POWER:
                status = I18n.format("gui.matterrebirth.replicator.no_power");
                color = RED;
                break;
            case READY:
                status = I18n.format("gui.matterrebirth.replicator.ready");
                color = DONE;
                break;
            case DEAD:
                status = I18n.format("gui.matterrebirth.replicator.dead");
                color = RED;
                break;
            default:
                status = I18n.format("gui.matterrebirth.replicator.idle");
                color = YELLOW;
                break;
        }
        status = fontRenderer.trimStringToWidth(status, TRASH_X - REPEAT_X - 14);
        fontRenderer.drawString(status, STATUS_CX - fontRenderer.getStringWidth(status) / 2, BTN_Y + 2, color);
    }

    /** Кнопка «i» под курсором (координаты окна); без HEI её нет. */
    private boolean infoAt(int mx, int my) {
        return infoOn && mx >= INFO_X && mx < INFO_X + 10 && my >= INFO_Y && my < INFO_Y + 10;
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
