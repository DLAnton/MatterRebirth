package dlx.matterrebirth.client;

import java.awt.image.BufferedImage;

/**
 * Фиолетовый глаз квантового нейрошлема (вариант А листа neuro_helmet_v1, Антон 14.11): зелёный кристалл квантового
 * шлема IC2 перекрашивается в цвета кристаллов синхронизатора. Перекраска картинок IC2 при загрузке — копий графики
 * IC2 в моде нет. Синий визор не меняется.
 */
final class NeuroEye {

    private static final int[][] MAP = {
            {0x008000, 0x8A2CB0}, // тёмно-зелёный → фиолетовый
            {0x00FF00, 0xD89CF0}, // ярко-зелёный → светло-фиолетовый
    };

    private NeuroEye() {
    }

    /** Перекрашенный цвет пикселя глаза или -1, если пиксель не глаз (прозрачные не трогаются). */
    static int eye(int argb) {
        if (argb >>> 24 == 0) {
            return -1;
        }
        int rgb = argb & 0xFFFFFF;
        for (int[] m : MAP) {
            if (rgb == m[0]) {
                return argb & 0xFF000000 | m[1];
            }
        }
        return -1;
    }

    /** Копия картинки с фиолетовым глазом; onlyEye — всё, кроме глаза, прозрачное. */
    static BufferedImage recolor(BufferedImage src, boolean onlyEye) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = src.getRGB(x, y);
                int eye = eye(argb);
                out.setRGB(x, y, eye != -1 ? eye : onlyEye ? 0 : argb);
            }
        }
        return out;
    }
}
