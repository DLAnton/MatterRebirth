package dlx.matterrebirth.proxy;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.chunk.ReplicatorChunks;
import dlx.matterrebirth.compat.BogoSorterCompat;
import dlx.matterrebirth.inventory.MRGuiHandler;
import dlx.matterrebirth.recipe.MRRecipes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import java.util.List;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/** Общая часть (сервер и клиент). */
public class Proxy {

    public void preInit(FMLPreInitializationEvent event) {
    }

    public void init(FMLInitializationEvent event) {
        MRRecipes.init();
        ReplicatorChunks.init(); // до загрузки миров: Forge вернёт сохранённые билеты машин
        NetworkRegistry.INSTANCE.registerGuiHandler(MatterRebirth.instance, new MRGuiHandler());
    }

    public void postInit(FMLPostInitializationEvent event) {
        BogoSorterCompat.init();
    }

    /** Пузырьки UU в камере репликатора (только клиент; на сервере ничего). */
    public void tickBubbles(World world, BlockPos pos, List<float[]> bubbles, boolean spawn) {
    }
}
