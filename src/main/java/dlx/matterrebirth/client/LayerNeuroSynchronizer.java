package dlx.matterrebirth.client;

import dlx.matterrebirth.init.MRContent;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;

/**
 * Синхронизатор на голове (своя модель, SyncHeadModel) — у игрока и на стойке для брони. Слой идёт после брони:
 * у варианта в кожаном шлеме шапку рисует сама игра, а синхронизатор — та же модель, сдвинутая на пиксель наружу.
 * Положение головы — как у ванильной головы-трофея: при приседании ниже, у детской модели (и малой стойки) меньше.
 */
public class LayerNeuroSynchronizer<T extends EntityLivingBase> implements LayerRenderer<T> {

    private static final SyncHeadModel MODEL = new SyncHeadModel("neuro_synchronizer");

    private final RenderLivingBase<T> renderer;

    /** Главная модель renderer должна быть ModelBiped (игрок, стойка) — проверяет NeuroHelmetClient. */
    public LayerNeuroSynchronizer(RenderLivingBase<T> renderer) {
        this.renderer = renderer;
    }

    @Override
    public void doRenderLayer(T entity, float limbSwing, float limbSwingAmount, float partialTicks,
                              float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        Item item = entity.getItemStackFromSlot(EntityEquipmentSlot.HEAD).getItem();
        boolean leather = item == MRContent.neuroSynchronizerLeather;
        if (!leather && item != MRContent.neuroSynchronizer) {
            return;
        }
        GlStateManager.pushMatrix();
        if (entity.isChild()) {
            GlStateManager.translate(0.0F, 0.5F * scale, 0.0F);
            GlStateManager.scale(0.7F, 0.7F, 0.7F);
            GlStateManager.translate(0.0F, 16.0F * scale, 0.0F);
        }
        if (entity.isSneaking()) {
            GlStateManager.translate(0.0F, 0.2F, 0.0F);
        }
        ((ModelBiped) renderer.getMainModel()).bipedHead.postRender(0.0625F);
        GlStateManager.color(1, 1, 1, 1);
        MODEL.render(leather); // в кожаном — те же детали, сдвинутые на пиксель наружу (на шапку), без крючка
        GlStateManager.popMatrix();
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
