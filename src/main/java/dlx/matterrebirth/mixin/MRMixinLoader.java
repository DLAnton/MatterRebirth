package dlx.matterrebirth.mixin;

import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Collections;
import java.util.List;

/**
 * Подключает миксины в классы IC2. IC2 — обычный мод, поэтому миксины «поздние»:
 * MixinBooter (встроен в Cleanroom) находит этот класс сам и применяет конфиг после загрузки модов.
 */
public class MRMixinLoader implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.matterrebirth.json");
    }
}
