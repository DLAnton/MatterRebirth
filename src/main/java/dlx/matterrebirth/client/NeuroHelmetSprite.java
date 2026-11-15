package dlx.matterrebirth.client;

import dlx.matterrebirth.MatterRebirth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.PngSizeInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.function.Function;

/** Значок квантового нейрошлема: слой картинки квантового шлема IC2 с фиолетовым глазом (NeuroEye). */
public class NeuroHelmetSprite extends TextureAtlasSprite {

    public static final String BASE = MatterRebirth.MOD_ID + ":items/neuro_quantum_helmet";
    public static final String OVERLAY = BASE + "_overlay";

    private final ResourceLocation source;

    public NeuroHelmetSprite(String name, String ic2Texture) {
        super(name);
        this.source = new ResourceLocation("ic2", "textures/items/armor/" + ic2Texture + ".png");
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    /** Возвращает false, если загрузилась (так устроен Forge: true — пропустить текстуру). */
    @Override
    public boolean load(IResourceManager manager, ResourceLocation location, Function<ResourceLocation, TextureAtlasSprite> textureGetter) {
        try (IResource ic2 = manager.getResource(source)) {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(NeuroEye.recolor(TextureUtil.readBufferedImage(ic2.getInputStream()), false), "png", png);
            IResource resource = new UuChamberSprite.Translucent(ic2, png.toByteArray());
            loadSprite(PngSizeInfo.makeFromResource(resource), resource.getMetadata("animation") != null);
            loadSpriteFrames(resource, Minecraft.getMinecraft().gameSettings.mipmapLevels + 1);
            return false;
        } catch (IOException | RuntimeException e) {
            MatterRebirth.LOG.error("Quantum neuro helmet icon could not be built from {}", source, e);
            return true;
        }
    }
}
