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
 * <p>
 * С 14.11 (5д) поверх UU в каждом кадре впечатано стекло своей части машины (replicator_glass_lower / _upper): пока в баке
 * есть UU, отдельные грани стекла не рисуются (ReplicatorBakedModel), и на каждой стороне колбы ровно один полупрозрачный
 * слой. Иначе Actinium в одних направлениях рисовал стекло под UU, в других над ней, и колба на запад и восток выходила
 * темнее и насыщеннее, чем на север и юг (Антон 14.11). Плоскости UU лежат там же, где стекло, и развёртка у них та же.
 */
public class UuChamberSprite extends TextureAtlasSprite {

    /** Начало имён текстур UU камеры: по нему ReplicatorBakedModel узнаёт грани UU. */
    public static final String PREFIX = "matterrebirth:blocks/uu_chamber";
    public static final String LOWER = PREFIX + "_lower", UPPER = PREFIX + "_upper";
    private static final ResourceLocation SOURCE = new ResourceLocation("ic2", "textures/blocks/fluid/uu_matter_still.png");
    /**
     * Непрозрачность UU, 0..255. 08.11: 150 (≈60 %; при 25 % не видно, при 40 % бледно). 14.11: 185 (≈73 %) — с 5д взгляд
     * снаружи проходит один слой UU, а не два (грани только наружные), и при 150 колба выглядела почти пустой (Антон 14.11).
     */
    private static final int ALPHA = 185;

    /** Стекло, впечатываемое поверх UU (текстура блока), или null. */
    @Nullable
    private final ResourceLocation glass;

    public UuChamberSprite(String name, @Nullable String glassTexture) {
        super(name);
        this.glass = glassTexture == null ? null
                : new ResourceLocation("matterrebirth", "textures/blocks/" + glassTexture + ".png");
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
            if (glass != null) {
                try (IResource glassResource = manager.getResource(glass)) {
                    image = withGlass(image, TextureUtil.readBufferedImage(glassResource.getInputStream()));
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

    /**
     * Каждый квадратный кадр UU (лента кадров сверху вниз) со стеклом поверх, как если бы стекло лежало на жидкости.
     * Размер кадра — больший из двух (меньшая картинка растягивается по пикселям), чтобы тонкие блики стекла не терялись.
     */
    private static BufferedImage withGlass(BufferedImage uu, BufferedImage glass) {
        int w = uu.getWidth(), frames = Math.max(1, uu.getHeight() / w), gw = glass.getWidth(), gh = glass.getHeight();
        int size = Math.max(w, gw);
        BufferedImage out = new BufferedImage(size, size * frames, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < frames; f++) {
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    int under = uu.getRGB(x * w / size, f * w + y * w / size);
                    int over = glass.getRGB(x * gw / size, y * gh / size);
                    out.setRGB(x, f * size + y, over(over, under));
                }
            }
        }
        return out;
    }

    /** Цвет over поверх under (обычное смешивание по прозрачности). */
    private static int over(int over, int under) {
        float ao = (over >>> 24) / 255F, au = (under >>> 24) / 255F;
        float a = ao + au * (1 - ao);
        if (a <= 0) {
            return 0;
        }
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            float c = ((over >> shift & 255) * ao + (under >> shift & 255) * au * (1 - ao)) / a;
            rgb |= Math.min(255, Math.round(c)) << shift;
        }
        return Math.round(a * 255) << 24 | rgb;
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
