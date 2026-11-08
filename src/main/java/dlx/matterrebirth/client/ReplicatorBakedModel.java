package dlx.matterrebirth.client;

import dlx.matterrebirth.block.BlockReplicator;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.BakedModelWrapper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Модель репликатора по слоям отрисовки (этап 5а). Непрозрачный слой — корпус. Полупрозрачный слой (как ванильное
 * цветное стекло и вода, так их понимают и шейдеры) — стекло (текстура replicator_glass*) и, пока в баке есть UU,
 * жидкая материя в камере (картинка жидкости IC2 со своей анимацией; Антон 08.11: почти прозрачная, стекло не красится).
 * Прозрачность UU — в самой текстуре (UuChamberSprite): вершинную прозрачность Actinium не учитывает.
 * Вне отрисовки чанка (трещины слома) слоя нет — отдаются корпус и стекло, UU только если она есть в баке.
 */
public class ReplicatorBakedModel extends BakedModelWrapper<IBakedModel> {

    public ReplicatorBakedModel(IBakedModel original) {
        super(original);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        List<BakedQuad> all = originalModel.getQuads(state, side, rand);
        BlockRenderLayer layer = MinecraftForgeClient.getRenderLayer();
        boolean uu = state != null && state.getPropertyKeys().contains(BlockReplicator.UU) && state.getValue(BlockReplicator.UU);
        boolean translucent = layer == BlockRenderLayer.TRANSLUCENT;
        List<BakedQuad> out = new ArrayList<>(all.size());
        for (BakedQuad quad : all) {
            if (isUu(quad)) {
                // UU — только пока она есть в баке, в том числе вне слоёв: иначе трещины слома ложились бы на пустую камеру
                if (uu && (layer == null || translucent)) {
                    out.add(quad);
                }
            } else if (layer == null || isGlass(quad) == translucent) {
                out.add(quad);
            }
        }
        return out;
    }

    private static boolean isGlass(BakedQuad quad) {
        return quad.getSprite() != null && quad.getSprite().getIconName().contains("replicator_glass");
    }

    private static boolean isUu(BakedQuad quad) {
        return quad.getSprite() != null && UuChamberSprite.NAME.equals(quad.getSprite().getIconName());
    }
}
