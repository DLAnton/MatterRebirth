package dlx.matterrebirth.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.PngSizeInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;

/**
 * Текстура UU в камере репликатора (Антон 08.11): сама картинка жидкой материи IC2 со своей анимацией, но почти
 * прозрачная. Собирается при загрузке текстур из файла IC2 (в моде своей копии нет): у каждого пикселя прозрачность
 * снижается до ALPHA. Прозрачность именно в текстуре, а не в вершинах модели: Actinium/Sodium вершинную прозрачность
 * не учитывает (проверка Антона 08.11 — UU получилась непрозрачной).
 */
public class UuChamberSprite extends TextureAtlasSprite {

    public static final String NAME = "matterrebirth:blocks/uu_chamber";
    private static final ResourceLocation SOURCE = new ResourceLocation("ic2", "textures/blocks/fluid/uu_matter_still.png");
    /** Непрозрачность UU, 0..255 (≈60 %: при 25 % не видно, при 40 % бледно и плохо читается — проверки Антона 08.11). */
    private static final int ALPHA = 150;

    public UuChamberSprite() {
        super(NAME);
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    /** Возвращает false, если загрузилась (так устроен Forge: true — пропустить текстуру). */
    @Override
    public boolean load(IResourceManager manager, ResourceLocation location, Function<ResourceLocation, TextureAtlasSprite> textureGetter) {
        try (IResource source = manager.getResource(SOURCE)) {
            BufferedImage image = TextureUtil.readBufferedImage(source.getInputStream());
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    int argb = image.getRGB(x, y);
                    int a = (argb >>> 24) * ALPHA / 255;
                    image.setRGB(x, y, (a << 24) | (argb & 0x00FFFFFF));
                }
            }
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            IResource resource = new Translucent(source, png.toByteArray());
            loadSprite(PngSizeInfo.makeFromResource(resource), resource.getMetadata("animation") != null);
            loadSpriteFrames(resource, Minecraft.getMinecraft().gameSettings.mipmapLevels + 1);
            return false;
        } catch (IOException | RuntimeException e) {
            dlx.matterrebirth.MatterRebirth.LOG.error("UU texture for the replicator chamber could not be built", e);
            return true;
        }
    }

    /** Ресурс с изменённой картинкой и анимацией (метаданными) исходной текстуры IC2. */
    private static final class Translucent implements IResource {
        private final IResource source;
        private final byte[] png;

        Translucent(IResource source, byte[] png) {
            this.source = source;
            this.png = png;
        }

        @Override
        public ResourceLocation getResourceLocation() {
            return source.getResourceLocation();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(png);
        }

        @Override
        public boolean hasMetadata() {
            return source.hasMetadata();
        }

        @Nullable
        @Override
        public <T extends IMetadataSection> T getMetadata(String sectionName) {
            return source.getMetadata(sectionName);
        }

        @Override
        public String getResourcePackName() {
            return source.getResourcePackName();
        }

        @Override
        public void close() {
        }
    }
}
