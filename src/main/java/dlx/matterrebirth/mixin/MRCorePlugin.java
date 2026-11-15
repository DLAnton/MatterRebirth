package dlx.matterrebirth.mixin;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Раннее подключение мода (Антон 15.11): миксины в классы самой игры (mixins.matterrebirth.early.json) нужно применить
 * до их загрузки, поздний MRMixinLoader для этого опаздывает. Своих преобразователей классов у мода нет.
 * Класс загружается до игры: ссылаться на классы Minecraft и мода здесь нельзя.
 */
@IFMLLoadingPlugin.Name("MatterRebirthCore")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class MRCorePlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.matterrebirth.early.json");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Nullable
    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Nullable
    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
