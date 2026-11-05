package dlx.matterrebirth.proxy;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.client.IC2LangPatch;
import dlx.matterrebirth.init.MRContent;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Клиент: модели предметов, переводы для IC2. */
@Mod.EventBusSubscriber(modid = MatterRebirth.MOD_ID, value = Side.CLIENT)
public class ProxyClient extends Proxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        IC2LangPatch.register();
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
