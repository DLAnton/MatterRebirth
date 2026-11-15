package dlx.matterrebirth.client;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.item.NeuroLink;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Фиолетовый глаз квантового нейрошлема на голове игрока и стойки для брони. Шлем рисует сама игра по картинке IC2
 * (в IC2 не вмешиваемся), а этот слой идёт после брони и кладёт поверх только глаз — на ТОЙ ЖЕ модели шлема, которой
 * рисовалась броня: её берём у слоя брони этого существа вместе с подменой Forge (getArmorModelHook). Так глаз совпадает
 * со шлемом пиксель в пиксель и тогда, когда слой брони заменён другим модом: Mo' Bends ставит свой слой со своей,
 * сгибающейся моделью шлема (Антон 14.11: со своей отдельной ModelBiped глаз уезжал на макушку, спереди оставался зелёный).
 * Если слой брони найти не удалось — своя модель той же формы, что у ванильной брони этого существа.
 */
public class LayerNeuroEye<T extends EntityLivingBase> implements LayerRenderer<T> {

    private static Field layersField;
    private static Method hookMethod;
    private static boolean reflectionFailed;

    private final RenderLivingBase<T> renderer;
    private final ModelBiped fallback;

    /** fallback — модель шлема той же формы, что у ванильного слоя брони этого renderer (на случай, если слоя нет). */
    public LayerNeuroEye(RenderLivingBase<T> renderer, ModelBiped fallback) {
        this.renderer = renderer;
        this.fallback = fallback;
    }

    @Override
    public void doRenderLayer(T entity, float limbSwing, float limbSwingAmount, float partialTicks,
                              float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        ItemStack head = entity.getItemStackFromSlot(EntityEquipmentSlot.HEAD);
        if (!NeuroLink.isNeuroHelmet(head)) {
            return;
        }
        ModelBiped model = helmetModel(entity, head);
        model.setModelAttributes(renderer.getMainModel());
        model.setLivingAnimations(entity, limbSwing, limbSwingAmount, partialTicks);
        model.setVisible(false);
        model.bipedHead.showModel = true;
        model.bipedHeadwear.showModel = true;
        renderer.bindTexture(NeuroEyeTexture.LOCATION);
        GlStateManager.color(1, 1, 1, 1);
        model.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
    }

    /** Модель шлема, которой слой брони рисовал этот шлем (ищется каждый кадр: Mo' Bends меняет слои на ходу). */
    private ModelBiped helmetModel(T entity, ItemStack head) {
        if (!reflection()) {
            return fallback;
        }
        try {
            @SuppressWarnings("unchecked")
            List<LayerRenderer<T>> layers = (List<LayerRenderer<T>>) layersField.get(renderer);
            for (LayerRenderer<T> layer : layers) {
                if (layer instanceof LayerArmorBase) {
                    ModelBase model = ((LayerArmorBase<?>) layer).getModelFromSlot(EntityEquipmentSlot.HEAD);
                    model = (ModelBase) hookMethod.invoke(layer, entity, head, EntityEquipmentSlot.HEAD, model);
                    return model instanceof ModelBiped ? (ModelBiped) model : fallback;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            MatterRebirth.LOG.error("Quantum neuro helmet eye: armor layer model not available, using own model", e);
            reflectionFailed = true;
        }
        return fallback;
    }

    private static boolean reflection() {
        if (layersField == null && !reflectionFailed) {
            try {
                layersField = ObfuscationReflectionHelper.findField(RenderLivingBase.class, "field_177097_h"); // layerRenderers
                hookMethod = LayerArmorBase.class.getDeclaredMethod("getArmorModelHook", EntityLivingBase.class,
                        ItemStack.class, EntityEquipmentSlot.class, ModelBase.class); // метод Forge, имя не шифруется
                hookMethod.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                MatterRebirth.LOG.error("Quantum neuro helmet eye: armor layer lookup failed, using own model", e);
                layersField = null;
                reflectionFailed = true;
            }
        }
        return !reflectionFailed;
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
