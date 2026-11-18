package dlx.matterrebirth.client;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.item.NeuroLink;
import dlx.matterrebirth.network.CloneListMessage;
import dlx.matterrebirth.tile.TileReplicator;
import ic2.api.item.HudMode;
import ic2.api.item.IItemHudProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Панель «Нейросвязь» (6д-2, концепт 16.11, вид Glass): клоны игрока в квантовом нейрошлеме. Что показывать, решает
 * сервер (CloneWatch присылает список); здесь вид по режиму HUD шлема IC2, порядок и рисование.
 * <p>
 * Режим HUD (клавиша IC2): выключен — панели нет; базовый — свёрнутая слева под HUD брони IC2 (строка «Нейросвязь» и
 * ряд ядер); расширенный и продвинутый — полная справа сверху под значками эффектов (Антон 17.11: слева IC2 в этих
 * режимах пишет описание предмета в руке). Пресеты облика — шаг 6д-3.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = MatterRebirth.MOD_ID, value = Side.CLIENT)
public final class NeuroPanel {

    private static final ResourceLocation CORES = new ResourceLocation(MatterRebirth.MOD_ID, "textures/gui/core_states.png");
    private static final ResourceLocation ANCHOR = new ResourceLocation(MatterRebirth.MOD_ID, "textures/items/dimensional_anchor.png");
    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");
    /** Колонки атласа ядра (stage6d2/gen_core_atlas.py) и кадров в колонке. */
    private static final int CORE_COLS = 7;
    private static final int FRAMES = 64;
    private static final int CORE_GROW = 0;
    private static final int CORE_PAUSE = 1;
    private static final int CORE_WAIT = 2;
    private static final int CORE_ROLLBACK = 3;
    private static final int CORE_RED = 4;
    private static final int CORE_READY = 5;
    private static final int CORE_OFF = 6;

    /** Группы порядка карточек: беда, откат, нет связи, ожидание, рост, готов. */
    private static final int G_TROUBLE = 0;
    private static final int G_ROLLBACK = 1;
    private static final int G_NO_LINK = 2;
    private static final int G_WAIT = 3;
    private static final int G_GROW = 4;
    private static final int G_READY = 5;

    // размеры в пикселях интерфейса (целые: при масштабе 1 и 3 полпикселя округляются неровно)
    private static final int EDGE = 3;
    /** HUD брони IC2: фон 71×69 у левого верхнего угла (GuiOverlayer). */
    private static final int IC2_HUD_BOTTOM = 69;
    private static final int W = 168;
    private static final int HEAD_H = 14;
    private static final int CARD_H = 26;
    private static final int GAP = 2;
    private static final int FOOT_H = 12;
    private static final int R = 5;
    private static final int MAX_CARDS = 4;
    private static final int MINI_H = 20;
    private static final int MINI_STEP = 18;
    private static final int MINI_MAX = 8;

    private static final int TINT = 0x141020;
    private static final int WHITE = 0xF2F2F8;
    private static final int TEXT_DIM = 0xB4B4C0;
    private static final int SUMMARY = 0x9DB5E0;
    private static final int CYAN = 0x5CC8C8;
    private static final int YELLOW = 0xFFD84A;
    private static final int PAUSE_GREY = 0x969696;
    private static final int RED = 0xFF5460;
    private static final int READY = 0x5ADC6E;
    private static final int OFF_GREY = 0x8A8A96;
    private static final float[] GLOW = {0.30F, 0.16F, 0.08F, 0.03F};

    private static List<CloneListMessage.Entry> clones = Collections.emptyList();
    /** Список уже приходил (до первого пакета панели нет). */
    private static boolean got;
    /** Верх полной панели: плавно едет к цели (значки эффектов появились или пропали). */
    private static float top;
    private static float prevTop;
    private static float targetTop = -1;
    private static boolean shownLastFrame;

    private NeuroPanel() {
    }

    /** Пришёл список клонов (основной поток игры). */
    public static void set(List<CloneListMessage.Entry> list) {
        clones = new ArrayList<>(list);
        got = true;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getMinecraft().world == null) {
            clones = Collections.emptyList();
            got = false;
            shownLastFrame = false;
            return;
        }
        prevTop = top;
        if (targetTop >= 0) {
            top += (targetTop - top) * 0.4F;
            if (Math.abs(targetTop - top) < 0.05F) {
                top = targetTop;
            }
        }
    }

    @SubscribeEvent
    public static void onOverlayPost(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        HudMode mode = player == null ? null : hudMode(player.getItemStackFromSlot(EntityEquipmentSlot.HEAD));
        if (!got || mode == null || !mode.shouldDisplay()) {
            shownLastFrame = false;
            return;
        }
        List<View> views = new ArrayList<>(clones.size());
        for (CloneListMessage.Entry e : clones) {
            views.add(new View(e, mc, player));
        }
        views.sort((a, b) -> a.group != b.group ? Integer.compare(a.group, b.group)
                : a.sameDim != b.sameDim ? (a.sameDim ? -1 : 1) : Double.compare(a.dist, b.dist));
        int width = event.getResolution().getScaledWidth();
        int height = event.getResolution().getScaledHeight();
        float partial = event.getPartialTicks();

        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        if (mode.hasTooltip()) {
            targetTop = potionBottom(player) + EDGE;
            if (!shownLastFrame) {
                top = prevTop = targetTop; // панель только что появилась — сразу на месте
            }
            drawFull(mc, views, width - EDGE - W, Math.round(prevTop + (top - prevTop) * partial), height);
        } else {
            drawMini(mc, views, EDGE, IC2_HUD_BOTTOM + EDGE);
        }
        shownLastFrame = mode.hasTooltip();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
        if (cull) {
            GlStateManager.enableCull();
        }
    }

    /** Режим HUD квантового нейрошлема на голове; null — на голове не нейрошлем или IC2 HUD у шлема не даёт. */
    private static HudMode hudMode(ItemStack head) {
        if (!NeuroLink.isNeuroHelmet(head) || !(head.getItem() instanceof IItemHudProvider)) {
            return null;
        }
        IItemHudProvider provider = (IItemHudProvider) head.getItem();
        return provider.doesProvideHUD(head) ? provider.getHudMode(head) : null;
    }

    /** Низ значков эффектов в правом верхнем углу, как их рисует игра (полезные — первый ряд, остальные — второй). */
    private static int potionBottom(EntityPlayerSP player) {
        int bottom = 0;
        for (PotionEffect effect : player.getActivePotionEffects()) {
            Potion potion = effect.getPotion();
            if (potion.shouldRenderHUD(effect) && potion.hasStatusIcon() && effect.doesShowParticles()) {
                bottom = Math.max(bottom, potion.isBeneficial() ? 1 + 24 : 1 + 26 + 24);
            }
        }
        return bottom;
    }

    // --- полная панель ---

    private static void drawFull(Minecraft mc, List<View> views, int x, int y, int screenHeight) {
        FontRenderer fr = mc.fontRenderer;
        int trouble = 0;
        for (View v : views) {
            if (v.group == G_TROUBLE) {
                trouble++;
            }
        }
        // шапка: «Нейросвязь» и сводка
        glass(x, y, W, HEAD_H, 1);
        String summary = views.isEmpty() ? I18n.format("matterrebirth.panel.none")
                : I18n.format("matterrebirth.panel.clones." + plural(views.size()), views.size())
                + (trouble > 0 ? " · " + I18n.format("matterrebirth.panel.trouble", trouble) : "");
        text(fr, I18n.format("matterrebirth.panel.title"), x + 6, y + 3, WHITE, 1);
        text(fr, summary, x + W - 6 - fr.getStringWidth(summary), y + 3, trouble > 0 ? RED : SUMMARY, 1);
        int cy = y + HEAD_H + GAP;
        // сколько карточек влезает до низа экрана (с местом под строку «+ ещё»)
        int room = (screenHeight - EDGE - cy - FOOT_H - GAP) / (CARD_H + GAP);
        int shown = Math.max(0, Math.min(Math.min(MAX_CARDS, room), views.size()));
        for (int i = 0; i < shown; i++) {
            drawCard(mc, views.get(i), x, cy);
            cy += CARD_H + GAP;
        }
        int hidden = views.size() - shown;
        if (hidden > 0) {
            boolean problems = false;
            for (int i = shown; i < views.size(); i++) {
                problems |= views.get(i).group <= G_WAIT;
            }
            glass(x, cy, W, FOOT_H, 1);
            String more = I18n.format("matterrebirth.panel.more", hidden);
            String state = I18n.format(problems ? "matterrebirth.panel.problems" : "matterrebirth.panel.all_ok");
            text(fr, more, x + 6, cy + 2, WHITE, 1);
            text(fr, state, x + 6 + fr.getStringWidth(more) + 6, cy + 2, problems ? YELLOW : READY, 1);
        }
    }

    private static void drawCard(Minecraft mc, View v, int x, int y) {
        FontRenderer fr = mc.fontRenderer;
        float a = v.group == G_NO_LINK ? 0.6F : 1; // без связи карточка приглушена
        if (v.group == G_TROUBLE) {
            GlStateManager.disableTexture2D();
            for (int i = 0; i < GLOW.length; i++) {
                int d = i + 1;
                HudDraw.ring(x - d, y - d, W + 2 * d, CARD_H + 2 * d, R + d, 1, HudDraw.argb(v.color, GLOW[i]));
            }
        }
        glass(x, y, W, CARD_H, a);
        GlStateManager.disableTexture2D();
        // цветная полоска слева
        HudDraw.fillRounded(x + 4, y + 5, 2, CARD_H - 10, 1, HudDraw.argb(v.color, 0.9F * a), HudDraw.argb(v.color, 0.9F * a));
        // ядро в кольце роста или здоровья
        float ccx = x + 19;
        float ccy = y + CARD_H / 2F;
        HudDraw.arc(ccx, ccy, 10, 1.5F, 0, 1, HudDraw.argb(0xFFFFFF, 0.12F * a));
        HudDraw.arc(ccx, ccy, 10, 1.5F, 0, v.ring, HudDraw.argb(v.color, 0.95F * a));
        drawCore(mc, v.core, ccx - 8, ccy - 8, 16, a);
        // справа: плашка причины и здоровье
        int right = x + W - 6;
        int badgeLeft = right;
        if (v.badge != null) {
            int bw = fr.getStringWidth(v.badge) + 12;
            badgeLeft = right - bw;
            GlStateManager.disableTexture2D();
            HudDraw.fillRounded(badgeLeft, y + 3, bw, 10, 5, HudDraw.argb(v.badgeColor, 0.22F), HudDraw.argb(v.badgeColor, 0.22F));
            HudDraw.ring(badgeLeft, y + 3, bw, 10, 5, 0.5F, HudDraw.argb(v.badgeColor, 0.7F));
            HudDraw.fillRounded(badgeLeft + 3, y + 6, 4, 4, 2, HudDraw.argb(v.badgeColor, 1), HudDraw.argb(v.badgeColor, 1));
            text(fr, v.badge, badgeLeft + 9, y + 4, WHITE, 1);
        }
        int healthLeft = right;
        if (v.hearts >= 0) {
            String h = v.hearts + "/" + TileReplicator.MAX_HEALTH;
            int hw = fr.getStringWidth(h);
            text(fr, h, right - hw, y + 15, WHITE, a);
            healthLeft = right - hw - 11;
            heart(mc, healthLeft, y + 15, a);
        }
        // текст: состояние, подробность, место
        int tx = x + 32;
        String title = fr.trimStringToWidth(v.title, badgeLeft - 4 - tx);
        text(fr, title, tx, y + 4, v.group == G_NO_LINK ? OFF_GREY : WHITE, a);
        int dx = tx + fr.getStringWidth(title) + 5;
        if (v.detail != null && dx < badgeLeft - 8) {
            text(fr, fr.trimStringToWidth(v.detail, badgeLeft - 4 - dx), dx, y + 4, v.group == G_NO_LINK ? OFF_GREY : v.color, a);
        }
        int lx = tx;
        if (v.anchor) {
            drawIcon(mc, ANCHOR, lx, y + 15, 8, a);
            lx += 10;
        }
        int placeRight = healthLeft - 4;
        String place = fr.trimStringToWidth(v.place, placeRight - lx);
        text(fr, place, lx, y + 15, TEXT_DIM, a);
        // полоса здоровья — если после места есть хотя бы 24 пикселя
        int barLeft = lx + fr.getStringWidth(place) + 6;
        int barRight = healthLeft - 4;
        if (v.hearts >= 0 && barRight - barLeft >= 24) {
            int bw = Math.min(40, barRight - barLeft);
            int bx = barRight - bw;
            GlStateManager.disableTexture2D();
            HudDraw.fillRounded(bx, y + 18, bw, 2, 1, HudDraw.argb(0xFFFFFF, 0.14F * a), HudDraw.argb(0xFFFFFF, 0.14F * a));
            HudDraw.fillRounded(bx, y + 18, bw * v.healthFraction, 2, 1, HudDraw.argb(v.healthColor, a), HudDraw.argb(v.healthColor, a));
        }
    }

    // --- свёрнутая панель ---

    private static void drawMini(Minecraft mc, List<View> views, int x, int y) {
        FontRenderer fr = mc.fontRenderer;
        String title = I18n.format("matterrebirth.panel.title");
        int n = Math.min(views.size(), MINI_MAX);
        String more = views.size() > MINI_MAX ? I18n.format("matterrebirth.panel.more_short", views.size() - MINI_MAX) : null;
        String none = views.isEmpty() ? I18n.format("matterrebirth.panel.none") : null;
        int w = 6 + fr.getStringWidth(title) + 6 + n * MINI_STEP + (more != null ? fr.getStringWidth(more) + 4 : 0)
                + (none != null ? fr.getStringWidth(none) + 2 : 0) + 4;
        glass(x, y, w, MINI_H, 1);
        text(fr, title, x + 6, y + 6, WHITE, 1);
        int cx = x + 6 + fr.getStringWidth(title) + 6;
        for (int i = 0; i < n; i++) {
            View v = views.get(i);
            float a = v.group == G_NO_LINK ? 0.6F : 1;
            float ccx = cx + MINI_STEP / 2F;
            float ccy = y + MINI_H / 2F;
            GlStateManager.disableTexture2D();
            if (v.group == G_TROUBLE) {
                HudDraw.fillRounded(ccx - 9, ccy - 9, 18, 18, 9, HudDraw.argb(v.color, 0.25F), HudDraw.argb(v.color, 0.25F));
            }
            HudDraw.arc(ccx, ccy, 7.5F, 1, 0, 1, HudDraw.argb(0xFFFFFF, 0.12F * a));
            HudDraw.arc(ccx, ccy, 7.5F, 1, 0, v.ring, HudDraw.argb(v.color, 0.95F * a));
            drawCore(mc, v.core, ccx - 6, ccy - 6, 12, a);
            cx += MINI_STEP;
        }
        if (more != null) {
            text(fr, more, cx + 2, y + 6, TEXT_DIM, 1);
        }
        if (none != null) {
            text(fr, none, cx, y + 6, TEXT_DIM, 1);
        }
    }

    // --- рисование ---

    /** Тёмное тонированное стекло со светлым краем и плавным бликом сверху (как карточка уведомления). */
    private static void glass(int x, int y, int w, int h, float a) {
        GlStateManager.disableTexture2D();
        for (int d = 1; d <= 2; d++) {
            HudDraw.fillRounded(x - d + 1, y + d, w + 2 * d - 2, h, R + d, HudDraw.argb(0, 0.06F * a), HudDraw.argb(0, 0.06F * a));
        }
        HudDraw.fillRounded(x, y, w, h, R, HudDraw.argb(TINT, 0.78F * a), HudDraw.argb(TINT, 0.78F * a));
        HudDraw.fillRounded(x, y, w, h, R, HudDraw.argb(0xFFFFFF, 0.07F * a), HudDraw.argb(0xFFFFFF, 0));
        HudDraw.ring(x, y, w, h, R, 0.5F, HudDraw.argb(0xFFFFFF, 0.28F * a));
    }

    private static void text(FontRenderer fr, String s, float x, float y, int rgb, float a) {
        if (a >= 0.1F && !s.isEmpty()) { // у шрифта прозрачность ниже 4/255 считается непрозрачной
            GlStateManager.enableTexture2D();
            fr.drawStringWithShadow(s, x, y, HudDraw.argb(rgb, a));
        }
    }

    /** Ядро из атласа состояний: кадр по времени игры и следующий поверх со смешиванием. */
    private static void drawCore(Minecraft mc, int col, float x, float y, float size, float a) {
        float time = (mc.world.getTotalWorldTime() % FRAMES) + mc.getRenderPartialTicks();
        int frame = (int) time % FRAMES;
        float mix = time - (int) time;
        GlStateManager.enableTexture2D();
        mc.getTextureManager().bindTexture(CORES);
        GlStateManager.color(1, 1, 1, a);
        coreQuad(x, y, size, col, frame);
        GlStateManager.color(1, 1, 1, a * mix);
        coreQuad(x, y, size, col, (frame + 1) % FRAMES);
        GlStateManager.color(1, 1, 1, 1);
    }

    private static void coreQuad(float x, float y, float size, int col, int frame) {
        float u0 = (float) col / CORE_COLS;
        float u1 = (float) (col + 1) / CORE_COLS;
        float v0 = (float) frame / FRAMES;
        float v1 = (float) (frame + 1) / FRAMES;
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        b.pos(x, y + size, 0).tex(u0, v1).endVertex();
        b.pos(x + size, y + size, 0).tex(u1, v1).endVertex();
        b.pos(x + size, y, 0).tex(u1, v0).endVertex();
        b.pos(x, y, 0).tex(u0, v0).endVertex();
        t.draw();
    }

    /** Значок предмета 16×16 (якорь) уменьшенным до size. */
    private static void drawIcon(Minecraft mc, ResourceLocation tex, float x, float y, float size, float a) {
        GlStateManager.enableTexture2D();
        mc.getTextureManager().bindTexture(tex);
        GlStateManager.color(1, 1, 1, a);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        b.pos(x, y + size, 0).tex(0, 1).endVertex();
        b.pos(x + size, y + size, 0).tex(1, 1).endVertex();
        b.pos(x + size, y, 0).tex(1, 0).endVertex();
        b.pos(x, y, 0).tex(0, 0).endVertex();
        t.draw();
        GlStateManager.color(1, 1, 1, 1);
    }

    /** Сердце из значков игры (полное красное, 9×9). */
    private static void heart(Minecraft mc, int x, int y, float a) {
        GlStateManager.enableTexture2D();
        mc.getTextureManager().bindTexture(ICONS);
        GlStateManager.color(1, 1, 1, a);
        Gui.drawModalRectWithCustomSizedTexture(x, y - 1, 52, 0, 9, 9, 256, 256);
        GlStateManager.color(1, 1, 1, 1);
    }

    /** Форма числа по-русски (1, 21 — n1; 2–4, 22–24 — n2; остальные — n5); в английском все три одинаковы. */
    private static String plural(int n) {
        int m10 = n % 10;
        int m100 = n % 100;
        if (m10 == 1 && m100 != 11) {
            return "n1";
        }
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) {
            return "n2";
        }
        return "n5";
    }

    /** Одна строка панели: тексты, цвета и порядок из присланного состояния. */
    private static final class View {
        int group;
        String title;
        String detail;
        String badge;
        int badgeColor;
        int color;
        int core;
        float ring;
        /** Здоровье готового клона в единицах, как на экране машины (0..20), или −1. */
        int hearts = -1;
        float healthFraction;
        int healthColor;
        final String place;
        final boolean sameDim;
        /** В машине стоит якорь (значок у места, в любом измерении). */
        final boolean anchor;
        final double dist;

        View(CloneListMessage.Entry e, Minecraft mc, EntityPlayerSP player) {
            sameDim = e.dim == player.dimension;
            anchor = e.anchor;
            dist = sameDim ? player.getDistanceSq(e.x + 0.5, e.y + 0.5, e.z + 0.5) : 0;
            String dimKey = "matterrebirth.dim." + e.dimName;
            place = (I18n.hasKey(dimKey) ? I18n.format(dimKey) : e.dimName) + "  " + e.x + " " + e.y + " " + e.z;
            if (e.status == CloneListMessage.NO_LINK) {
                set(G_NO_LINK, "no_link", OFF_GREY, CORE_OFF, 0);
                detail = I18n.format("matterrebirth.panel.no_power");
                return;
            }
            if (e.dead) {
                set(G_TROUBLE, "dead", RED, CORE_RED, 0);
                return;
            }
            TileReplicator.Status[] all = TileReplicator.Status.values();
            TileReplicator.Status s = e.status >= 0 && e.status < all.length ? all[e.status] : TileReplicator.Status.IDLE;
            float growth = Math.max(0, Math.min(1, e.growth / 1000F));
            if (e.stage >= TileReplicator.STAGES) {
                float health = Math.max(0, Math.min(1, e.health / (float) TileReplicator.MAX_HEALTH));
                if (s == TileReplicator.Status.NEED_UU || s == TileReplicator.Status.NO_POWER) {
                    set(G_TROUBLE, "health", RED, CORE_RED, health);
                    badge(s == TileReplicator.Status.NEED_UU ? "uu_none" : "eu_none", RED);
                } else if (s == TileReplicator.Status.READY) {
                    set(G_READY, "ready", READY, CORE_READY, health);
                } else {
                    stateOf(s, health);
                }
                hearts = e.health;
                healthFraction = health;
                healthColor = color == READY ? READY : RED;
                return;
            }
            stateOf(s, growth);
            String stage = I18n.format("matterrebirth.panel.stage", Math.min(e.stage + 1, TileReplicator.STAGES), TileReplicator.STAGES);
            detail = s == TileReplicator.Status.GROWING ? stage + " · " + e.stagePercent + "%" : stage;
        }

        /** Состояния роста и прочие — как строка состояния на экране машины. */
        private void stateOf(TileReplicator.Status s, float ring) {
            switch (s) {
                case GROWING:
                    set(G_GROW, "growing", CYAN, CORE_GROW, ring);
                    break;
                case NEED_UU:
                    set(G_WAIT, "need_uu", YELLOW, CORE_WAIT, ring);
                    break;
                case LOW_POWER:
                    set(G_WAIT, "low_power", YELLOW, CORE_WAIT, ring);
                    break;
                case CHARGING:
                    set(G_WAIT, "charging", YELLOW, CORE_WAIT, ring);
                    break;
                case OVERLOAD:
                    set(G_WAIT, "overload", RED, CORE_RED, ring);
                    break;
                case PAUSED:
                    set(G_WAIT, "paused", PAUSE_GREY, CORE_PAUSE, ring);
                    break;
                case ROLLBACK:
                    set(G_ROLLBACK, "rollback", Shimmer.color(Minecraft.getMinecraft().world), CORE_ROLLBACK, ring);
                    badge("eu_low", YELLOW);
                    break;
                case NO_POWER:
                    set(G_TROUBLE, "health", RED, CORE_RED, ring);
                    break;
                default:
                    set(G_WAIT, "idle", PAUSE_GREY, CORE_PAUSE, ring);
                    break;
            }
        }

        private void set(int group, String state, int color, int core, float ring) {
            this.group = group;
            this.title = I18n.format("matterrebirth.panel.state." + state);
            this.color = color;
            this.core = core;
            this.ring = ring;
        }

        private void badge(String key, int color) {
            badge = I18n.format("matterrebirth.panel." + key);
            badgeColor = color;
        }
    }
}
