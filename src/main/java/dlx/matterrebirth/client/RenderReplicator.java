package dlx.matterrebirth.client;

import dlx.matterrebirth.block.BlockReplicator;
import dlx.matterrebirth.init.MRConfig;
import dlx.matterrebirth.init.MRContent;
import dlx.matterrebirth.tile.TileReplicator;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Отрисовщик репликатора (этап 5а): живые огни экранчика основания и ореол колец пола и излучателя.
 * Здесь же пузырьки в камере: отрисовщик машины рисует до полупрозрачного слоя, поэтому стекло и UU ложатся поверх них,
 * как и должно быть (частицы Minecraft рисовались поверх стекла — замечание Антона 08.11).
 * UU в камере — часть модели блока (ReplicatorBakedModel), не отсюда.
 * Всё рисуется в координатах модели (1/16 блока), повёрнутых как машина: лицо — на север модели (z = 0).
 * Экранчик всегда работает (Антон 08.11): лампочка UU — есть UU в баке, лампочка EU — есть энергия,
 * пять делений — выращенные стадии, полоса — здоровье готового клона.
 * Ореол — мягкое цветное свечение вокруг колец (свет блока в 1.12 бесцветный), выключается настройкой ringGlow.
 * Клон в камере (этап 5б) рисует CloneRenderer — первым, до пузырьков.
 */
public class RenderReplicator extends TileEntitySpecialRenderer<TileReplicator> {

    private static final ResourceLocation PARTICLES = new ResourceLocation("textures/particle/particles.png");
    /** Пузырёк на листе частиц Minecraft (номер 32: столбец 0, строка 2 по 8 пикселей из 128). */
    private static final float BUB_U0 = 0F, BUB_U1 = 8 / 128F, BUB_V0 = 16 / 128F, BUB_V1 = 24 / 128F;
    /** Пузырьки полупрозрачные (Антон 08.11). */
    private static final float BUB_ALPHA = 0.4F;

    private static final int LED_UU = 0xC05CD8, LED_EU = 0xE8C83A, HEALTH = 0xD83A30;
    /** Выращенные стадии — зелёные, как текст готовой стадии в окне (Антон 08.11). */
    private static final int SEGMENT_DONE = 0x5ADC6E;
    /**
     * Планки якоря по бокам экранчика (5г, рисунок Антона 09.11): столбцы 7 и 24 лица, строки 3..6. Якорь стоит
     * и есть энергия — ярко; без энергии — тускло (40 % пути от гнезда планки к цвету, вариант А 13.11); якоря нет — ничего не рисуется,
     * видны гнёзда планок на самой текстуре лица, как у погасших ламп (Антон 13.11).
     */
    private static final int ANCHOR_BAR = 0x7030A0, ANCHOR_SLOT = 0x3E4C4D;
    /** Пульсация лампочек и текущей стадии, тиков на период: медленно — идёт процесс, быстро — тревога (Антон 08.11). */
    private static final float PULSE_SLOW = 40, PULSE_FAST = 12;

    /**
     * Цвет деления текущей стадии — тот же, что у значка стадии и линии печати в окне (одна палитра, Антон 13.11):
     * рост бирюзовый, ожидание жёлтое, тревога красная, откат — перелив красный ↔ фиолетовый (shimmer), пауза серая.
     */
    private static int segmentColor(BlockReplicator.Light light, boolean rollback, int shimmer) {
        if (rollback) {
            return shimmer;
        }
        switch (light) {
            case ROLLBACK:
                return shimmer;
            case GROW:
                return 0x5CC8C8;
            case WAIT:
                return 0xFFD84A;
            case ALERT:
                return 0xFF0000; // тревога — чистый красный (Антон 14.11)
            default: // пауза (кольца при ней белые — это подсветка, не цвет состояния)
                return 0x969696;
        }
    }

    /**
     * Цвета ореола по BlockReplicator.Light (OFF не рисуется) — те же, что у колец и в окне (одна палитра, Антон 14.11):
     * рост бирюзовый, ожидание жёлтое, тревога красная, клон здоров зелёный (как «Готов»), откат и распад — перелив
     * красный ↔ фиолетовый (shimmer, вместе с кольцами); пауза и пустая машина — белый.
     */
    private static int glowColor(BlockReplicator.Light light, int shimmer) {
        switch (light) {
            case GROW:
                return 0x5CC8C8;
            case WAIT:
                return 0xFFD84A;
            case ALERT:
                return 0xFF0000; // тревога — чистый красный (Антон 14.11)
            case OK:
                return 0x5ADC6E;
            case ROLLBACK:
                return shimmer;
            default: // IDLE
                return 0xF4F8FA;
        }
    }

    @Override
    public void render(TileReplicator te, double x, double y, double z, float partialTicks, int destroyStage, float alpha) {
        IBlockState state = te.getWorld().getBlockState(te.getPos());
        if (state.getBlock() != MRContent.replicator || state.getValue(BlockReplicator.PART) != BlockReplicator.Part.LOWER) {
            return;
        }
        EnumFacing facing = state.getValue(BlockReplicator.FACING);
        BlockReplicator.Light light = te.getLookLight();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x + 0.5, y, z + 0.5);
        GlStateManager.rotate(180 - facing.getHorizontalAngle(), 0, 1, 0); // как поворот модели в blockstate
        GlStateManager.translate(-0.5, 0, -0.5);
        GlStateManager.scale(1 / 16.0, 1 / 16.0, 1 / 16.0);

        CloneRenderer.render(this, te, partialTicks); // клон первым: пузырьки за ним прячутся, стекло и UU ложатся поверх

        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F); // огни светятся сами
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();

        drawScreen(te, partialTicks);
        if (MRConfig.CLIENT.ringGlow && light != BlockReplicator.Light.OFF) {
            drawGlow(glowColor(light, Shimmer.color(te.getWorld())));
        }

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GlStateManager.popMatrix();

        drawBubbles(te, x, y, z, partialTicks);
    }

    /** Привязать текстуру (для CloneRenderer: bindTexture защищён). */
    void bind(ResourceLocation texture) {
        bindTexture(texture);
    }

    // --- пузырьки ---

    /**
     * Пузырьки лицом к камере, как частицы; координаты пузырьков — в блоках от нижней части, без поворота машины.
     * Направления «вправо» и «вверх» для камеры считаются здесь по взгляду игрока: готовые значения ActiveRenderInfo
     * под Actinium не годятся (пузырьки были видны только спереди и сплющены — проверка Антона 08.11).
     */
    private void drawBubbles(TileReplicator te, double x, double y, double z, float partialTicks) {
        if (te.getBubbles().isEmpty()) {
            return;
        }
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (view == null) {
            return;
        }
        double yaw = Math.toRadians(view.prevRotationYaw + (view.rotationYaw - view.prevRotationYaw) * partialTicks);
        double pitch = Math.toRadians(view.prevRotationPitch + (view.rotationPitch - view.prevRotationPitch) * partialTicks);
        // взгляд f, «вправо» r (горизонтально), «вверх» u = r × f
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        bindTexture(PARTICLES);
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 200F, 200F); // UU слегка светится
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (float[] b : te.getBubbles()) {
            double bx = x + b[3] + (b[0] - b[3]) * partialTicks;
            double by = y + b[4] + (b[1] - b[4]) * partialTicks;
            double bz = z + b[5] + (b[2] - b[5]) * partialTicks;
            float s = b[9];
            bubbleVertex(buf, bx - (rx + ux) * s, by - uy * s, bz - (rz + uz) * s, BUB_U0, BUB_V1);
            bubbleVertex(buf, bx + (rx - ux) * s, by - uy * s, bz + (rz - uz) * s, BUB_U1, BUB_V1);
            bubbleVertex(buf, bx + (rx + ux) * s, by + uy * s, bz + (rz + uz) * s, BUB_U1, BUB_V0);
            bubbleVertex(buf, bx - (rx - ux) * s, by + uy * s, bz - (rz - uz) * s, BUB_U0, BUB_V0);
        }
        tess.draw();
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
    }

    private static void bubbleVertex(BufferBuilder buf, double x, double y, double z, float u, float v) {
        buf.pos(x, y, z).tex(u, v).color(0.88F, 0.75F, 1F, BUB_ALPHA).endVertex();
    }

    // --- экранчик основания ---

    /**
     * Огни поверх тёмного экранчика текстуры replicator_front. Тексели картинки 32x32 на лице: столбец u, строка снизу vy
     * (как в генераторе gen_textures5a.py). Лампочки UU (9, 4) и EU (11, 4), деления стадий 14 + 2i в строке 4,
     * полоса здоровья 9..22 в строке 5.
     */
    private void drawScreen(TileReplicator te, float partialTicks) {
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        float time = te.getWorld().getTotalWorldTime() % 24000 + partialTicks;
        if (te.getLookAnchor()) {
            int bar = te.getLookEu() ? ANCHOR_BAR : mix(ANCHOR_SLOT, ANCHOR_BAR, 0.4F);
            for (int vy = 3; vy <= 6; vy++) {
                texel(buf, 7, vy, 1, bar);
                texel(buf, 24, vy, 1, bar);
            }
        }
        lamp(buf, 9, te.getLookUuLamp(), LED_UU, time);
        lamp(buf, 11, te.getLookEuLamp(), LED_EU, time);
        int stage = te.getLookStage();
        for (int i = 0; i < Math.min(stage, TileReplicator.STAGES); i++) {
            texel(buf, 14 + i * 2, 4, 1, SEGMENT_DONE);
        }
        // текущая стадия — цветом колец: рост медленно пульсирует, тревога быстро, ожидание и пауза ровно (Антон 08.11)
        BlockReplicator.Light light = te.getLookLight();
        if (te.getLookCurrent() && stage < TileReplicator.STAGES && light != BlockReplicator.Light.OFF) {
            int mode = light == BlockReplicator.Light.GROW ? 2
                    : light == BlockReplicator.Light.ALERT || light == BlockReplicator.Light.ROLLBACK ? 3 : 1;
            int rgb = segmentColor(light, te.getLookRollback(), Shimmer.color(te.getWorld()));
            texel(buf, 14 + stage * 2, 4, 1, dim(rgb, pulse(mode, time)));
        }
        if (stage >= TileReplicator.STAGES) {
            int w = Math.round(14F * te.getLookHealth() / TileReplicator.MAX_HEALTH);
            if (w > 0) {
                texel(buf, 9, 5, w, HEALTH);
            }
        }
        tess.draw();
    }

    /** Лампочка в строке 4 по режиму: 0 не горит, 1 ровно, 2 медленно, 3 быстро. */
    private static void lamp(BufferBuilder buf, int u, int mode, int rgb, float time) {
        if (mode != 0) {
            texel(buf, u, 4, 1, dim(rgb, pulse(mode, time)));
        }
    }

    /** Яркость 1 (ровно) или мягкая пульсация 0,25..1. */
    private static float pulse(int mode, float time) {
        if (mode < 2) {
            return 1;
        }
        float period = mode == 2 ? PULSE_SLOW : PULSE_FAST;
        return 0.25F + 0.75F * (0.5F + 0.5F * (float) Math.cos(Math.PI * 2 * time / period));
    }

    /** Цвет на доле k пути от a к b. */
    private static int mix(int a, int b, float k) {
        int r = Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * k);
        int g = Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * k);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
        return r << 16 | g << 8 | bl;
    }

    private static int dim(int rgb, float k) {
        int r = Math.round((rgb >> 16 & 255) * k), g = Math.round((rgb >> 8 & 255) * k), b = Math.round((rgb & 255) * k);
        return r << 16 | g << 8 | b;
    }

    /** Полоса из w текселей лица начиная с u (вправо по картинке), строка vy снизу; чуть перед гранью. */
    private static void texel(BufferBuilder buf, int u, int vy, int w, int rgb) {
        // на лице (север модели) картинка идёт справа налево по x: тексель u занимает x от 16 - (u + 1) / 2 до 16 - u / 2
        double x0 = 16 - (u + w) / 2.0, x1 = 16 - u / 2.0;
        double y0 = vy / 2.0, y1 = (vy + 1) / 2.0;
        double z = -0.02;
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
        buf.pos(x0, y0, z).color(r, g, b, 1F).endVertex();
        buf.pos(x0, y1, z).color(r, g, b, 1F).endVertex();
        buf.pos(x1, y1, z).color(r, g, b, 1F).endVertex();
        buf.pos(x1, y0, z).color(r, g, b, 1F).endVertex();
    }

    // --- ореол колец ---

    /**
     * Ореол: полупрозрачное кольцо, ярче всего на самом кольце и тающее внутрь и наружу; складывается со светом под ним.
     * Пол: круг радиусом 3,75 над полом (высота 5). Излучатель: ромб «радиусом» 3,5 на потолке камеры (16 + 12,5).
     */
    private void drawGlow(int rgb) {
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        GlStateManager.depthMask(false);
        GlStateManager.disableAlpha();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        ring(buf, 5.05, 2.7, 3.75, 5.6, 32, r, g, b, 0.55F);
        ring(buf, 28.45, 2.4, 3.5, 5.2, 4, r, g, b, 0.5F); // 4 отрезка — ромб
        tess.draw();

        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableBlend();
    }

    /**
     * Плоское кольцо на высоте y вокруг центра (8, 8): от r0 (прозрачно) к r1 (яркость a) и к r2 (снова прозрачно).
     * Круг — n отрезков; ромб — 4 отрезка с вершинами на осях.
     */
    private static void ring(BufferBuilder buf, double y, double r0, double r1, double r2, int n,
                             float r, float g, float b, float a) {
        for (int i = 0; i < n; i++) {
            double t0 = Math.PI * 2 * i / n, t1 = Math.PI * 2 * (i + 1) / n;
            double c0 = Math.cos(t0), s0 = Math.sin(t0), c1 = Math.cos(t1), s1 = Math.sin(t1);
            band(buf, y, c0, s0, c1, s1, r0, r1, r, g, b, 0F, a);
            band(buf, y, c0, s0, c1, s1, r1, r2, r, g, b, a, 0F);
        }
    }

    private static void band(BufferBuilder buf, double y, double c0, double s0, double c1, double s1,
                             double ra, double rb, float r, float g, float b, float aa, float ab) {
        buf.pos(8 + c0 * ra, y, 8 + s0 * ra).color(r, g, b, aa).endVertex();
        buf.pos(8 + c1 * ra, y, 8 + s1 * ra).color(r, g, b, aa).endVertex();
        buf.pos(8 + c1 * rb, y, 8 + s1 * rb).color(r, g, b, ab).endVertex();
        buf.pos(8 + c0 * rb, y, 8 + s0 * rb).color(r, g, b, ab).endVertex();
    }

    @Override
    public boolean isGlobalRenderer(TileReplicator te) {
        return false;
    }
}
