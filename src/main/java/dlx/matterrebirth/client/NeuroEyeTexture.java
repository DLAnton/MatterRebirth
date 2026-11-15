package dlx.matterrebirth.client;

import dlx.matterrebirth.MatterRebirth;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Глаз квантового нейрошлема на голове: из картинки брони квантового шлема IC2 (накладка, она не красится) остаются
 * только пиксели глаза, перекрашенные в фиолетовый, остальное прозрачное. Рисуется поверх шлема (LayerNeuroEye).
 * Если картинку IC2 прочитать не вышло — пустая текстура, а не «нет текстуры» на голове.
 */
public class NeuroEyeTexture extends AbstractTexture {

    public static final ResourceLocation LOCATION = new ResourceLocation(MatterRebirth.MOD_ID, "dynamic/neuro_helmet_eye");
    private static final ResourceLocation SOURCE = new ResourceLocation("ic2", "textures/armor/quantum_1_overlay.png");

    @Override
    public void loadTexture(IResourceManager manager) {
        deleteGlTexture();
        BufferedImage out;
        try (IResource ic2 = manager.getResource(SOURCE)) {
            out = NeuroEye.recolor(TextureUtil.readBufferedImage(ic2.getInputStream()), true);
        } catch (IOException | RuntimeException e) {
            MatterRebirth.LOG.error("Quantum neuro helmet eye could not be built from {}", SOURCE, e);
            out = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        }
        TextureUtil.uploadTextureImageAllocate(getGlTextureId(), out, false, false);
    }
}
