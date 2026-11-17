package dlx.matterrebirth.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

/**
 * Гладкие фигуры для HUD нейросвязи (6д): скруглённые прямоугольники, рамки и круги цветом с прозрачностью, в
 * пикселях интерфейса (дробные координаты допустимы). Состояние GL (текстуры выключены, смешивание, без отсечения по
 * прозрачности) готовит вызывающий. Цвета — ARGB.
 */
@SideOnly(Side.CLIENT)
public final class HudDraw {

    /** Отрезков на четверть скругления. */
    private static final int SEG = 6;

    private HudDraw() {
    }

    /** Скруглённый прямоугольник с вертикальным градиентом от top (сверху) к bottom (снизу). */
    public static void fillRounded(float x, float y, float w, float h, float r, int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        float[] p = path(x, y, w, h, r);
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_TRIANGLE_FAN, DefaultVertexFormats.POSITION_COLOR);
        vertex(b, x + w / 2, y + h / 2, lerp(top, bottom, 0.5F));
        for (int i = 0; i <= p.length; i += 2) {
            int k = i % p.length; // замкнуть веер первой точкой
            vertex(b, p[k], p[k + 1], lerp(top, bottom, (p[k + 1] - y) / h));
        }
        t.draw();
    }

    /** Рамка толщиной width по внутреннему краю скруглённого прямоугольника. */
    public static void ring(float x, float y, float w, float h, float r, float width, int color) {
        if (w <= 0 || h <= 0 || width <= 0) {
            return;
        }
        float[] out = path(x, y, w, h, r);
        float[] in = path(x + width, y + width, Math.max(0, w - 2 * width), Math.max(0, h - 2 * width), Math.max(0, r - width));
        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
        for (int i = 0; i <= out.length; i += 2) {
            int k = i % out.length;
            vertex(b, in[k], in[k + 1], color); // внутренняя первой: треугольники того же обхода, что и веер
            vertex(b, out[k], out[k + 1], color);
        }
        t.draw();
    }

    /** Точки контура против часовой стрелки на экране (как у прямоугольников игры), x и y подряд. */
    private static float[] path(float x, float y, float w, float h, float r) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        float[] cx = {x + w - r, x + r, x + r, x + w - r};
        float[] cy = {y + r, y + r, y + h - r, y + h - r};
        float[] start = {0, 270, 180, 90}; // градусы; на экране y вниз, поэтому убывание угла — против часовой
        float[] p = new float[4 * (SEG + 1) * 2];
        int n = 0;
        for (int c = 0; c < 4; c++) {
            for (int s = 0; s <= SEG; s++) {
                double a = Math.toRadians(start[c] - 90.0 * s / SEG);
                p[n++] = (float) (cx[c] + Math.cos(a) * r);
                p[n++] = (float) (cy[c] + Math.sin(a) * r);
            }
        }
        return p;
    }

    private static void vertex(BufferBuilder b, float x, float y, int argb) {
        b.pos(x, y, 0).color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24).endVertex();
    }

    /** ARGB между a и b по t (0..1). */
    public static int lerp(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = a >>> shift & 255;
            int cb = b >>> shift & 255;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }

    /** Цвет RGB с прозрачностью alpha (0..1). */
    public static int argb(int rgb, float alpha) {
        int a = Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        return a << 24 | rgb & 0xFFFFFF;
    }
}
