package dlx.matterrebirth.proxy;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.client.IC2LangPatch;
import dlx.matterrebirth.client.RenderReplicator;
import dlx.matterrebirth.client.ReplicatorBakedModel;
import dlx.matterrebirth.client.UuChamberSprite;
import dlx.matterrebirth.init.MRContent;
import dlx.matterrebirth.tile.TileReplicator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Клиент: модели предметов и машины, отрисовщик машины, пузырьки, переводы для IC2. */
@Mod.EventBusSubscriber(modid = MatterRebirth.MOD_ID, value = Side.CLIENT)
public class ProxyClient extends Proxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        IC2LangPatch.register();
        ClientRegistry.bindTileEntitySpecialRenderer(TileReplicator.class, new RenderReplicator());
    }

    /**
     * Пузырьки в камере (идея Антона 08.11, как у песка душ под водой). Свои, а не частицы Minecraft: частицы рисуются
     * поверх стекла (с шейдерами — после полупрозрачного слоя), а эти рисует отрисовщик машины до стекла и UU, и они видны
     * сквозь них. Чаще при «Все частицы», реже при «Меньше», нет при «Минимум»; далеко от игрока новые не появляются.
     * Пузырёк: x, y, z, прежние x, y, z (для плавности), скорость x, y, z, размер, возраст; координаты в блоках от нижней части.
     */
    @Override
    public void tickBubbles(World world, BlockPos pos, List<float[]> bubbles, boolean spawn) {
        float top = 1 + 12.5F / 16 - 0.05F; // под излучателем
        for (Iterator<float[]> it = bubbles.iterator(); it.hasNext(); ) {
            float[] b = it.next();
            b[3] = b[0];
            b[4] = b[1];
            b[5] = b[2];
            // как ванильный пузырь: всплывает с ускорением и сопротивлением, слегка покачивается
            b[7] += 0.002F;
            b[6] += (world.rand.nextFloat() - 0.5F) * 0.002F;
            b[8] += (world.rand.nextFloat() - 0.5F) * 0.002F;
            b[0] = Math.max(0.12F, Math.min(0.88F, b[0] + b[6])); // не дальше стёкол и задней стенки при любом повороте
            b[1] += b[7];
            b[2] = Math.max(0.12F, Math.min(0.88F, b[2] + b[8]));
            b[6] *= 0.85F;
            b[7] *= 0.85F;
            b[8] *= 0.85F;
            if (++b[10] > 300 || b[1] >= top) {
                it.remove();
            }
        }
        Minecraft mc = Minecraft.getMinecraft();
        int setting = mc.gameSettings.particleSetting;
        if (!spawn || setting >= 2 || mc.player == null || bubbles.size() >= 40
                || world.rand.nextInt(setting == 0 ? 2 : 6) != 0 || mc.player.getDistanceSq(pos) > 32 * 32) {
            return;
        }
        double a = world.rand.nextDouble() * Math.PI * 2;
        double r = Math.sqrt(world.rand.nextDouble()) * 0.2; // по кругу площадки, равномерно
        float x = (float) (0.5 + Math.cos(a) * r), z = (float) (0.5 + Math.sin(a) * r), y = 5 / 16F + 0.02F;
        float size = 0.025F + world.rand.nextFloat() * 0.025F; // половина стороны, блоки (поперёк 0,8–1,6 пикселя блока)
        bubbles.add(new float[]{x, y, z, x, y, z, 0, 0.01F + world.rand.nextFloat() * 0.01F, 0, size, 0});
    }

    /** Почти прозрачная UU для камеры машины: собирается из картинки IC2 при загрузке текстур блоков. */
    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        if ("textures".equals(event.getMap().getBasePath())) {
            event.getMap().setTextureEntry(new UuChamberSprite());
        }
    }

    /** Модели машины: стекло отдельно от корпуса (корпус в непрозрачном слое, стекло в полупрозрачном). */
    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        List<ModelResourceLocation> keys = new ArrayList<>();
        for (ModelResourceLocation key : event.getModelRegistry().getKeys()) {
            if (MatterRebirth.MOD_ID.equals(key.getNamespace()) && "replicator".equals(key.getPath())) {
                keys.add(key);
            }
        }
        for (ModelResourceLocation key : keys) {
            IBakedModel model = event.getModelRegistry().getObject(key);
            if (model != null) {
                event.getModelRegistry().putObject(key, new ReplicatorBakedModel(model));
            }
        }
    }

    /** Кожаный вариант синхронизатора: слой 0 (шлем) окрашивается цветом шлема, слой 1 (накладка) — нет. */
    @SubscribeEvent
    public static void registerItemColors(ColorHandlerEvent.Item event) {
        event.getItemColors().registerItemColorHandler((stack, tintIndex) ->
                tintIndex == 0 ? ((ItemArmor) stack.getItem()).getColor(stack) : -1, MRContent.neuroSynchronizerLeather);
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        for (Item item : MRContent.ITEMS) {
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
    }
}
