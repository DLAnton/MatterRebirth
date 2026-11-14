package dlx.matterrebirth.client;

import dlx.matterrebirth.tile.TileReplicator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.world.World;

/**
 * Кольца отката (пол и излучатель): анимированная текстура, кадр N — цвет перелива в тик N периода (картинка из
 * gen_textures5a.py, по кадру на тик). Обычная анимация Minecraft считает тики сама, от загрузки текстур, и не совпала
 * бы с окном и тревогой; здесь кадр выбирается по времени мира, как в Shimmer. Без мира — обычная анимация.
 */
public class ShimmerSprite extends TextureAtlasSprite {

    public static final String FLOOR = "matterrebirth:blocks/replicator_floor_rollback";
    public static final String CEILING = "matterrebirth:blocks/replicator_ceiling_rollback";

    private int shown = -1;

    public ShimmerSprite(String name) {
        super(name);
    }

    @Override
    public void updateAnimation() {
        World world = Minecraft.getMinecraft().world;
        int frames = getFrameCount();
        if (world == null || frames <= 1) {
            super.updateAnimation();
            return;
        }
        int frame = (int) (world.getTotalWorldTime() % TileReplicator.SHIMMER_TICKS * frames / TileReplicator.SHIMMER_TICKS);
        if (frame != shown) {
            shown = frame;
            TextureUtil.uploadTextureMipmap(getFrameTextureData(frame), getIconWidth(), getIconHeight(), getOriginX(), getOriginY(),
                    false, false);
        }
    }
}
