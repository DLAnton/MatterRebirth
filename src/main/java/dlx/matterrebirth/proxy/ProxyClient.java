package dlx.matterrebirth.proxy;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.init.MRContent;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** Клиент: модели предметов. */
@Mod.EventBusSubscriber(modid = MatterRebirth.MOD_ID, value = Side.CLIENT)
public class ProxyClient extends Proxy {

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        for (Item item : MRContent.ITEMS) {
            ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(item.getRegistryName(), "inventory"));
        }
    }
}
