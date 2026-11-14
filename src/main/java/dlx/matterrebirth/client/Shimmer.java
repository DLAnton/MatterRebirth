package dlx.matterrebirth.client;

import dlx.matterrebirth.tile.TileReplicator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/**
 * Перелив отката и распада (Антон 14.11): клон разрушается — красный, четверть UU уходит в бак — фиолетовый.
 * Цвет плавно идёт красный → фиолетовый → красный за TileReplicator.SHIMMER_TICKS тиков. Часы — время мира, поэтому
 * кольца (ShimmerSprite), ореол, деление на экранчике, срез клона и окно переливаются вместе, а тревога отката
 * звучит на красном пике (начало каждого периода).
 */
public final class Shimmer {

    public static final int RED = 0xFF0000, PURPLE = 0xC05CD8; // чистый красный — проба Антона 14.11 (с #DC4646 текст казался оранжевым)

    private Shimmer() {
    }

    /** Доля фиолетового 0..1 в момент t (тики от начала периода, можно дробные): 0 — красный пик, середина — фиолетовый. */
    public static float blend(float t) {
        return (float) ((1 - Math.cos(2 * Math.PI * t / TileReplicator.SHIMMER_TICKS)) / 2);
    }

    /** Цвет 0xRRGGBB при доле фиолетового b (так же считает генератор кадров колец). */
    public static int mix(float b) {
        int r = channel(RED >> 16, PURPLE >> 16, b);
        int g = channel(RED >> 8 & 255, PURPLE >> 8 & 255, b);
        int bl = channel(RED & 255, PURPLE & 255, b);
        return r << 16 | g << 8 | bl;
    }

    /** Текущий цвет перелива (между тиками — плавно). */
    public static int color(@Nullable World world) {
        return mix(blend(now(world)));
    }

    /** Время в периоде перелива: тики мира по модулю периода плюс доля тика отрисовки. */
    public static float now(@Nullable World world) {
        if (world == null) {
            return 0;
        }
        return world.getTotalWorldTime() % TileReplicator.SHIMMER_TICKS + Minecraft.getMinecraft().getRenderPartialTicks();
    }

    private static int channel(int a, int b, float t) {
        return Math.max(0, Math.min(255, (int) (a + (b - a) * t + 0.5F)));
    }
}
