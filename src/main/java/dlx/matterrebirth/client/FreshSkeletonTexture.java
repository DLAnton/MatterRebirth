package dlx.matterrebirth.client;

import dlx.matterrebirth.MatterRebirth;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

/**
 * «Свежий» скелет клона (этап 5б, Антон 08.11): ванильная текстура скелета, при загрузке перекрашенная из «высохшей»
 * кости в свежую. Копии ванильной картинки в моде нет: берётся из ресурсов самой игры (её встроенного пакета), а не из
 * выбранных пакетов ресурсов (читается из jar игры) — скелет клона не меняется при смене текстурпака (Антон 08.11). Перекраска: тёмное (яркость меньше 100) — тёмно-бурое, остальное по яркости
 * в градиент от тёплой к светлой кости; прозрачность как у исходника.
 */
public class FreshSkeletonTexture extends AbstractTexture {

    public static final ResourceLocation LOCATION = new ResourceLocation(MatterRebirth.MOD_ID, "dynamic/clone_skeleton");
    private static final ResourceLocation VANILLA = new ResourceLocation("textures/entity/skeleton/skeleton.png");
    private static final String VANILLA_PATH = "/assets/minecraft/textures/entity/skeleton/skeleton.png";

    private static final int DARK = 0x5A463E, LO = 0xD2BCA6, HI = 0xFAF3E6;

    @Override
    public void loadTexture(IResourceManager manager) throws IOException {
        deleteGlTexture();
        // прямо из jar игры (как встроенный пакет ресурсов игры), минуя выбранные пакеты
        InputStream raw = Minecraft.class.getResourceAsStream(VANILLA_PATH);
        if (raw == null) { // нестандартный запуск: тогда через ресурсы (могут быть из пакета)
            raw = manager.getResource(VANILLA).getInputStream();
        }
        try (InputStream in = raw) {
            BufferedImage img = TextureUtil.readBufferedImage(in);
            int w = img.getWidth(), h = img.getHeight();
            int[] px = img.getRGB(0, 0, w, h, null, 0, w);
            for (int i = 0; i < px.length; i++) {
                px[i] = fresh(px[i]);
            }
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            out.setRGB(0, 0, w, h, px, 0, w);
            TextureUtil.uploadTextureImageAllocate(getGlTextureId(), out, false, false);
        }
    }

    static int fresh(int argb) {
        int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        double l = 0.299 * r + 0.587 * g + 0.114 * b;
        int rgb;
        if (l < 100) {
            rgb = DARK;
        } else {
            double t = Math.max(0, Math.min(1, (l - 121) / 95));
            rgb = lerp(LO >> 16 & 255, HI >> 16 & 255, t) << 16 | lerp(LO >> 8 & 255, HI >> 8 & 255, t) << 8
                    | lerp(LO & 255, HI & 255, t);
        }
        return argb & 0xFF000000 | rgb;
    }

    private static int lerp(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }
}
