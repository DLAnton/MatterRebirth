package dlx.matterrebirth.client;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.mind.CloneWatch;
import dlx.matterrebirth.network.CloneAlertMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Уведомления о беде клонов на экране (6д-1, концепт 16.11, стиль Glass): карточка вверху по центру, при полосах
 * боссов — под ними. Слева ядро в тревожном переливе в круге цвета беды, справа что случилось и где, внизу полоса
 * 4 с. Видно до 3 карточек стопкой, остальные ждут; звук — один на пачку (временно нотный блок, свой — этап 8).
 * Решает всё сервер (CloneWatch), здесь только показ.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = MatterRebirth.MOD_ID, value = Side.CLIENT)
public final class CloneAlerts {

    private static final ResourceLocation CORE = new ResourceLocation(MatterRebirth.MOD_ID, "textures/gui/core_alarm.png");
    /** Кадров в ленте ядра (16×16 друг под другом), по кадру на тик, со смешиванием соседних. */
    private static final int FRAMES = 64;

    /** Сколько живёт карточка, тиков (4 с), и сколько она появляется и гаснет. */
    private static final int LIFE = 80;
    private static final float FADE_IN = 4;
    private static final float FADE_OUT = 8;
    private static final int MAX_SHOWN = 3;
    private static final int MAX_QUEUE = 12;

    /** Размеры карточки в пикселях интерфейса (по концепту при масштабе 4). */
    private static final int H = 30;
    private static final int R = 6;
    private static final int GAP = 4;
    private static final int TOP = 12;
    private static final int TEXT_X = 30;
    private static final int PAD_RIGHT = 12;
    private static final float DETAIL_SCALE = 0.75F;

    private static final int TINT = 0x141020;
    private static final int WHITE = 0xF2F2F8;
    private static final int RED = 0xFF5460;
    private static final int VIOLET = 0xC87DFF;
    private static final int GREY = 0xA0A0AF;
    /** Ореол цвета беды вокруг карточки: прозрачность колец изнутри наружу. */
    private static final float[] GLOW = {0.32F, 0.18F, 0.09F, 0.04F};

    private static final List<Alert> SHOWN = new ArrayList<>();
    private static final Deque<Alert> QUEUE = new ArrayDeque<>();
    /** Тики клиента без паузы игры: по ним живут карточки. */
    private static long clock;
    /** Через сколько тиков второй тон звука (0 — не ждём). */
    private static int secondTone;
    /** Низ полос боссов в этом кадре (0 — полос нет). */
    private static int bossBottom;

    private CloneAlerts() {
    }

    /** Пришла пачка уведомлений (основной поток игры). */
    public static void show(List<CloneAlertMessage.Card> cards) {
        if (Minecraft.getMinecraft().world == null || cards.isEmpty()) {
            return;
        }
        for (CloneAlertMessage.Card card : cards) {
            if (QUEUE.size() >= MAX_QUEUE) {
                QUEUE.pollFirst();
            }
            QUEUE.addLast(new Alert(card));
        }
        fill();
        tone(1.0F);
        secondTone = 3; // мягкий двойной тон: второй выше на кварту
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            SHOWN.clear();
            QUEUE.clear();
            secondTone = 0;
            return;
        }
        if (mc.isGamePaused()) {
            return;
        }
        clock++;
        if (secondTone > 0 && --secondTone == 0) {
            tone(1.335F);
        }
        SHOWN.removeIf(a -> clock - a.start >= LIFE);
        fill();
        for (int i = 0; i < SHOWN.size(); i++) {
            Alert a = SHOWN.get(i);
            a.prevY = a.y;
            float target = i * (H + GAP);
            a.y += (target - a.y) * 0.5F; // ушла верхняя — остальные плавно поднимаются
            if (Math.abs(target - a.y) < 0.05F) {
                a.y = target;
            }
        }
    }

    /** Свободные места стопки — из очереди. */
    private static void fill() {
        while (SHOWN.size() < MAX_SHOWN && !QUEUE.isEmpty()) {
            Alert a = QUEUE.pollFirst();
            a.start = clock;
            a.y = a.prevY = SHOWN.size() * (H + GAP);
            SHOWN.add(a);
        }
    }

    private static void tone(float pitch) {
        SoundEvent sound = SoundEvent.REGISTRY.getObject(new ResourceLocation("block.note.chime"));
        if (sound == null) {
            sound = SoundEvents.BLOCK_NOTE_PLING;
        }
        Minecraft.getMinecraft().getSoundHandler().playSound(new PositionedSoundRecord(sound.getSoundName(),
                SoundCategory.PLAYERS, 0.6F, pitch, false, 0, ISound.AttenuationType.NONE, 0, 0, 0));
    }

    @SubscribeEvent
    public static void onOverlayPre(RenderGameOverlayEvent.Pre event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
            bossBottom = 0;
        }
    }

    /** Полосы боссов идут сверху по одной; запоминаем низ последней (и отменённые другими модами — место занято). */
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onBossBar(RenderGameOverlayEvent.BossInfo event) {
        bossBottom = Math.max(bossBottom, event.getY() + event.getIncrement());
    }

    @SubscribeEvent
    public static void onOverlayPost(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || SHOWN.isEmpty()) {
            return;
        }
        float partial = event.getPartialTicks();
        int width = event.getResolution().getScaledWidth();
        float top = bossBottom > 0 ? bossBottom + 2 : TOP;
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha(); // иначе слабые блики и тени отсекаются
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        for (Alert a : SHOWN) {
            float age = clock - a.start + partial;
            float alpha = Math.max(0, Math.min(1, Math.min(age / FADE_IN, (LIFE - age) / FADE_OUT)));
            if (alpha > 0) {
                draw(a, width, top + a.prevY + (a.y - a.prevY) * partial, alpha, Math.min(1, age / LIFE));
            }
        }
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.color(1, 1, 1, 1);
        if (cull) {
            GlStateManager.enableCull();
        }
    }

    private static void draw(Alert a, int screenWidth, float y, float alpha, float spent) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;
        int textWidth = Math.max(fr.getStringWidth(a.title), MathHelper.ceil(fr.getStringWidth(a.detail) * DETAIL_SCALE));
        int w = TEXT_X + textWidth + PAD_RIGHT;
        float x = (screenWidth - w) / 2;
        GlStateManager.disableTexture2D();
        // ореол цвета беды и мягкая тень под карточкой
        for (int i = 0; i < GLOW.length; i++) {
            int d = i + 1;
            HudDraw.ring(x - d, y - d, w + 2 * d, H + 2 * d, R + d, 1, HudDraw.argb(a.color, GLOW[i] * alpha));
        }
        for (int d = 1; d <= 3; d++) {
            HudDraw.fillRounded(x - d + 1, y + 1 + d, w + 2 * d - 2, H, R + d, HudDraw.argb(0, 0.07F * alpha),
                    HudDraw.argb(0, 0.07F * alpha));
        }
        // тёмное тонированное стекло, блик сверху плавно гаснет, светлый край
        HudDraw.fillRounded(x, y, w, H, R, HudDraw.argb(TINT, 0.82F * alpha), HudDraw.argb(TINT, 0.82F * alpha));
        HudDraw.fillRounded(x, y, w, H, R, HudDraw.argb(0xFFFFFF, 0.07F * alpha), HudDraw.argb(0xFFFFFF, 0));
        HudDraw.ring(x, y, w, H, R, 0.5F, HudDraw.argb(0xFFFFFF, 0.31F * alpha));
        // круг цвета беды под ядром
        HudDraw.fillRounded(x + 5, y + 5, 20, 20, 10, HudDraw.argb(a.color, 0.22F * alpha), HudDraw.argb(a.color, 0.22F * alpha));
        HudDraw.ring(x + 5, y + 5, 20, 20, 10, 0.75F, HudDraw.argb(a.color, 0.78F * alpha));
        // полоса-таймер: сколько осталось
        float barW = w - 12;
        HudDraw.fillRounded(x + 6, y + H - 4, barW, 1, 0.5F, HudDraw.argb(0xFFFFFF, 0.14F * alpha), HudDraw.argb(0xFFFFFF, 0.14F * alpha));
        HudDraw.fillRounded(x + 6, y + H - 4, barW * (1 - spent), 1, 0.5F, HudDraw.argb(a.color, 0.9F * alpha), HudDraw.argb(a.color, 0.9F * alpha));
        drawCore(mc, x + 7, y + 7, alpha);
        if (alpha >= 0.1F) { // у шрифта прозрачность ниже 4/255 считается непрозрачной
            fr.drawStringWithShadow(a.title, x + TEXT_X, y + 6, HudDraw.argb(WHITE, alpha));
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + TEXT_X, y + 17, 0);
            GlStateManager.scale(DETAIL_SCALE, DETAIL_SCALE, 1);
            fr.drawString(a.detail, 0, 0, HudDraw.argb(a.color, alpha), false);
            GlStateManager.popMatrix();
        }
    }

    /** Ядро в тревожном переливе 16×16: кадр по времени игры и следующий поверх со смешиванием, как у текстур игры. */
    private static void drawCore(Minecraft mc, float x, float y, float alpha) {
        float time = (mc.world.getTotalWorldTime() % FRAMES) + mc.getRenderPartialTicks();
        int frame = (int) time % FRAMES;
        float mix = time - (int) time;
        GlStateManager.enableTexture2D();
        mc.getTextureManager().bindTexture(CORE);
        GlStateManager.color(1, 1, 1, alpha);
        quad(x, y, frame);
        GlStateManager.color(1, 1, 1, alpha * mix);
        quad(x, y, (frame + 1) % FRAMES);
        GlStateManager.color(1, 1, 1, 1);
    }

    private static void quad(float x, float y, int frame) {
        float v0 = (float) frame / FRAMES;
        float v1 = (float) (frame + 1) / FRAMES;
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        b.pos(x, y + 16, 0).tex(0, v1).endVertex();
        b.pos(x + 16, y + 16, 0).tex(1, v1).endVertex();
        b.pos(x + 16, y, 0).tex(1, v0).endVertex();
        b.pos(x, y, 0).tex(0, v0).endVertex();
        t.draw();
    }

    /** Одна карточка: тексты собираются сразу, на языке игры. */
    private static final class Alert {
        final String title;
        final String detail;
        final int color;
        long start;
        float y;
        float prevY;

        Alert(CloneAlertMessage.Card card) {
            String kind;
            switch (card.kind) {
                case CloneWatch.HEALTH:
                    kind = "health";
                    color = RED;
                    break;
                case CloneWatch.ROLLBACK:
                    kind = "rollback";
                    color = VIOLET;
                    break;
                case CloneWatch.DEAD:
                    kind = "dead";
                    color = RED;
                    break;
                default:
                    kind = "link";
                    color = GREY;
                    break;
            }
            String key = "matterrebirth.alert." + kind + "." + (card.count <= 1 ? "one" : plural(card.count));
            title = I18n.format(key, card.count);
            StringBuilder d = new StringBuilder();
            if (card.kind == CloneWatch.HEALTH) {
                String reason = card.reason == CloneWatch.NO_UU ? "uu" : card.reason == CloneWatch.NO_EU ? "eu" : "uu_eu";
                d.append(I18n.format("matterrebirth.alert.reason." + reason)).append(" · ");
            } else if (card.kind == CloneWatch.LINK_LOST) {
                d.append(I18n.format("matterrebirth.alert.reason.power")).append(" · ");
            }
            String dimKey = "matterrebirth.dim." + card.dim;
            d.append(I18n.hasKey(dimKey) ? I18n.format(dimKey) : card.dim)
                    .append("  ").append(card.x).append(' ').append(card.y).append(' ').append(card.z);
            if (card.count > 1) {
                d.append(" · ").append(I18n.format("matterrebirth.alert.more", card.count - 1));
            }
            detail = d.toString();
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
    }
}
