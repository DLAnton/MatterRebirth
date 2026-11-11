package dlx.matterrebirth;

import dlx.matterrebirth.proxy.Proxy;
import dlx.matterrebirth.tile.TileReplicator;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerAboutToStartEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Matter Rebirth — аддон к IndustrialCraft 2 Experimental.
 * Выращивает неактивные клоны игрока из жидкой UU-материи; при смерти сознание переходит в готовый клон.
 * Итоговый концепт утверждён 05.11.2026.
 */
@Mod(modid = MatterRebirth.MOD_ID, name = MatterRebirth.MOD_NAME, version = MatterRebirth.VERSION,
        dependencies = MatterRebirth.DEPENDENCIES)
public class MatterRebirth {

    public static final String MOD_ID = "matterrebirth";
    public static final String MOD_NAME = "Matter Rebirth";
    public static final String VERSION = "0.1.0";
    public static final String DEPENDENCIES = "required-after:ic2;after:jei;after:cofhcore;after:baubles";

    public static final Logger LOG = LogManager.getLogger(MOD_ID);

    @Mod.Instance(MOD_ID)
    public static MatterRebirth instance;

    @SidedProxy(clientSide = "dlx.matterrebirth.proxy.ProxyClient", serverSide = "dlx.matterrebirth.proxy.Proxy")
    public static Proxy proxy;

    /** CoFH Core стоит: учитываем его «Печать души» при переносе. */
    public static boolean cofhLoaded;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        cofhLoaded = Loader.isModLoaded("cofhcore");
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    /** Запуск мира (и одиночного, и сервера): цены клона из настроек — новые значения действуют после перезахода (5в+). */
    @Mod.EventHandler
    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        TileReplicator.loadBalance();
    }
}
