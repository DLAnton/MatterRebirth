package dlx.matterrebirth.proxy;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.compat.BogoSorterCompat;
import dlx.matterrebirth.inventory.MRGuiHandler;
import dlx.matterrebirth.recipe.MRRecipes;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/** Общая часть (сервер и клиент). */
public class Proxy {

    public void preInit(FMLPreInitializationEvent event) {
    }

    public void init(FMLInitializationEvent event) {
        MRRecipes.init();
        NetworkRegistry.INSTANCE.registerGuiHandler(MatterRebirth.instance, new MRGuiHandler());
    }

    public void postInit(FMLPostInitializationEvent event) {
        BogoSorterCompat.init();
    }
}
