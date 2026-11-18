package dlx.matterrebirth.network;

import dlx.matterrebirth.MatterRebirth;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/** Свой сетевой канал мода (6д): уведомления о беде клона (6д-1) и список клонов для панели (6д-2), с сервера игроку. */
public final class MRNetwork {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(MatterRebirth.MOD_ID);

    private MRNetwork() {
    }

    /** Регистрация пакетов (preInit, на сервере и на клиенте одинаково: номера пакетов должны совпадать). */
    public static void init() {
        CHANNEL.registerMessage(CloneAlertMessage.Handler.class, CloneAlertMessage.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(CloneListMessage.Handler.class, CloneListMessage.class, 1, Side.CLIENT);
    }
}
