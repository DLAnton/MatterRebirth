package dlx.matterrebirth.client;

import dlx.matterrebirth.item.NeuroLink;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.Collections;
import java.util.List;

/**
 * Модель значка квантового шлема IC2, которая для шлема с отметкой «Нейросвязь» отдаёт модель с фиолетовым глазом
 * (matterrebirth:neuro_quantum_helmet), а для обычного — модель IC2 как есть. Модель IC2 не меняется, только оборачивается.
 */
public class NeuroHelmetModel implements IBakedModel {

    private final IBakedModel ic2;
    private final IBakedModel neuro;
    private final ItemOverrideList overrides = new ItemOverrideList(Collections.emptyList()) {
        @Override
        public IBakedModel handleItemState(IBakedModel original, ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity) {
            IBakedModel model = NeuroLink.isNeuroHelmet(stack) ? neuro : ic2;
            return model.getOverrides().handleItemState(model, stack, world, entity);
        }
    };

    public NeuroHelmetModel(IBakedModel ic2, IBakedModel neuro) {
        this.ic2 = ic2;
        this.neuro = neuro;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        return ic2.getQuads(state, side, rand);
    }

    @Override
    public boolean isAmbientOcclusion() {
        return ic2.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return ic2.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return ic2.isBuiltInRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return ic2.getParticleTexture();
    }

    @SuppressWarnings("deprecation")
    @Override
    public ItemCameraTransforms getItemCameraTransforms() {
        return ic2.getItemCameraTransforms();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return overrides;
    }

    @Override
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
        return ic2.handlePerspective(type);
    }
}
