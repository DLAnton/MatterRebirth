package dlx.matterrebirth.client;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.item.NeuroLink;
import ic2.core.item.ItemIC2;
import ic2.core.ref.ItemName;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelArmorStandArmor;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderArmorStand;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Квантовый нейрошлем на клиенте (этап 5е): строка «Нейросвязь» в подсказке, значок и глаз на голове фиолетовые.
 * Всё через свои модели, текстуры и слой — в IC2 не вмешиваемся.
 */
@Mod.EventBusSubscriber(modid = MatterRebirth.MOD_ID, value = Side.CLIENT)
public final class NeuroHelmetClient {

    private static final ResourceLocation MODEL = new ResourceLocation(MatterRebirth.MOD_ID, "neuro_quantum_helmet");

    private NeuroHelmetClient() {
    }

    /**
     * Вызывать в init клиента: глаз нейрошлема и синхронизатор на голове — слои у обоих видов игрока (Стив и Алекс)
     * и у стойки для брони (Антон 14.11). Глаз рисуется на модели шлема той же формы, что у брони этого существа.
     */
    public static void init() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().loadTexture(NeuroEyeTexture.LOCATION, new NeuroEyeTexture());
        for (RenderPlayer render : mc.getRenderManager().getSkinMap().values()) {
            addLayers(render, new ModelBiped(1.0F));
        }
        Render<EntityArmorStand> stand = mc.getRenderManager().getEntityClassRenderObject(EntityArmorStand.class);
        if (stand instanceof RenderArmorStand) {
            addLayers((RenderArmorStand) stand, new ModelArmorStandArmor(1.0F));
        } else {
            MatterRebirth.LOG.warn("Armor stand renderer is replaced ({}), neuro helmet layers not added to it", stand);
        }
    }

    private static <T extends EntityLivingBase> void addLayers(RenderLivingBase<T> render, ModelBiped helmetModel) {
        if (!(render.getMainModel() instanceof ModelBiped)) {
            MatterRebirth.LOG.warn("Neuro helmet layers skipped for {}: model is not biped", render);
            return;
        }
        render.addLayer(new LayerNeuroEye<T>(render, helmetModel));
        render.addLayer(new LayerNeuroSynchronizer<T>(render)); // синхронизатор на голове (своя модель)
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (NeuroLink.isNeuroHelmet(event.getItemStack())) {
            event.getToolTip().add(Math.min(1, event.getToolTip().size()),
                    TextFormatting.LIGHT_PURPLE + I18n.format("matterrebirth.neuro_link.tooltip"));
        }
    }

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        if ("textures".equals(event.getMap().getBasePath())) {
            event.getMap().setTextureEntry(new NeuroHelmetSprite(NeuroHelmetSprite.BASE, "quantum_helmet"));
            event.getMap().setTextureEntry(new NeuroHelmetSprite(NeuroHelmetSprite.OVERLAY, "quantum_helmet_overlay"));
        }
    }

    @SubscribeEvent
    public static void onModelRegistry(ModelRegistryEvent event) {
        Item helmet = NeuroLink.quantumHelmet();
        if (helmet != null) {
            ModelBakery.registerItemVariants(helmet, MODEL); // загрузить и нашу модель значка
        }
    }

    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        ModelResourceLocation ic2Key = ItemIC2.getModelLocation(ItemName.quantum_helmet, null);
        IBakedModel ic2 = event.getModelRegistry().getObject(ic2Key);
        IBakedModel neuro = event.getModelRegistry().getObject(new ModelResourceLocation(MODEL, "inventory"));
        if (ic2 == null || neuro == null) {
            MatterRebirth.LOG.error("Quantum neuro helmet icon not set up: model {} {}", ic2 == null ? ic2Key : MODEL,
                    "is missing");
            return;
        }
        event.getModelRegistry().putObject(ic2Key, new NeuroHelmetModel(ic2, neuro));
    }
}
