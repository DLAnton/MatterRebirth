package dlx.matterrebirth.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Бледная копия скина для погибшего клона (Антон 08.11, вариант Б «мертвецки синеватый» листа dead_pale_v1):
 * краски скина приглушаются почти до серого (на 85 %) и подмешиваются к холодному светлому тону (на 40 %).
 * Простым умножением цвета так не выйти (оно только темнит), поэтому копия считается из уже загруженной текстуры скина
 * и живёт отдельной текстурой — с шейдерами работает так же. Скин из сети может подгрузиться позже, поэтому копия
 * пересчитывается не чаще раза в REBUILD_MS, пока её рисуют.
 */
final class PaleSkins {

    private static final float DESAT = 0.85F, MIX = 0.40F;
    private static final float[] TONE = {0.80F, 0.85F, 0.95F};
    private static final long REBUILD_MS = 5000;

    private static final Map<ResourceLocation, Entry> CACHE = new HashMap<>();

    private static final class Entry {
        DynamicTexture texture;
        ResourceLocation location;
        int width, height;
        long built;
    }

    private PaleSkins() {
    }

    /** Бледная копия скина; при ошибке чтения — сам скин. Вызывать на потоке отрисовки. */
    static ResourceLocation get(ResourceLocation skin) {
        Entry e = CACHE.get(skin);
        long now = System.currentTimeMillis();
        if (e != null && now - e.built < REBUILD_MS) {
            return e.location;
        }
        TextureManager tm = Minecraft.getMinecraft().getTextureManager();
        tm.bindTexture(skin); // загружает скин, если ещё не загружен
        int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (w <= 0 || h <= 0 || w > 1024 || h > 1024) {
            return e != null ? e.location : skin;
        }
        IntBuffer pixels = BufferUtils.createIntBuffer(w * h);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        if (e == null || e.width != w || e.height != h) {
            if (e != null) {
                tm.deleteTexture(e.location);
            }
            e = new Entry();
            e.texture = new DynamicTexture(w, h);
            e.location = tm.getDynamicTextureLocation("matterrebirth_pale_skin", e.texture);
            e.width = w;
            e.height = h;
            CACHE.put(skin, e);
        }
        int[] out = e.texture.getTextureData();
        for (int i = 0; i < w * h; i++) {
            out[i] = pale(pixels.get(i));
        }
        e.texture.updateDynamicTexture();
        e.built = now;
        GlStateManager.bindTexture(0); // дальше вызывающий привяжет нужную
        return e.location;
    }

    /** ARGB → бледный ARGB (прозрачность та же). */
    private static int pale(int argb) {
        int a = argb >>> 24;
        float r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
        float l = 0.299F * r + 0.587F * g + 0.114F * b;
        r += (l - r) * DESAT;
        g += (l - g) * DESAT;
        b += (l - b) * DESAT;
        r = r * (1 - MIX) + TONE[0] * 255 * MIX;
        g = g * (1 - MIX) + TONE[1] * 255 * MIX;
        b = b * (1 - MIX) + TONE[2] * 255 * MIX;
        return a << 24 | Math.round(r) << 16 | Math.round(g) << 8 | Math.round(b);
    }
}
