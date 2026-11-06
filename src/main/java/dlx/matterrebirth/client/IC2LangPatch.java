package dlx.matterrebirth.client;

import dlx.matterrebirth.MatterRebirth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * У пыли жемчуга Края и пыли ока Эндера IC2 (ic2:dust#ender_pearl, #ender_eye) нет перевода, кроме английского. IC2 держит свои переводы в отдельных
 * файлах и после каждой загрузки ресурсов заново пишет их в таблицы перевода игры. Поэтому мы подписываемся после
 * IC2 и дописываем недостающие ключи из своего lang-файла (matterrebirth.ic2.<ключ> → ic2.<ключ>).
 */
@SideOnly(Side.CLIENT)
public final class IC2LangPatch implements IResourceManagerReloadListener {

    private static final String[] KEYS = {"dust.ender_pearl", "dust.ender_eye"};

    private IC2LangPatch() {
    }

    public static void register() {
        if (Minecraft.getMinecraft().getResourceManager() instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(new IC2LangPatch());
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onResourceManagerReload(IResourceManager resourceManager) {
        try {
            Class<?> loc = Class.forName("ic2.core.init.Localization");
            Method localeMap = loc.getDeclaredMethod("getLocaleMap");
            Method languageMap = loc.getDeclaredMethod("getLanguageMapMap");
            localeMap.setAccessible(true);
            languageMap.setAccessible(true);
            Map<String, String> locale = (Map<String, String>) localeMap.invoke(null);
            Map<String, String> language = (Map<String, String>) languageMap.invoke(null);
            for (String key : KEYS) {
                String ours = MatterRebirth.MOD_ID + ".ic2." + key;
                if (!I18n.hasKey(ours)) {
                    continue;
                }
                String value = I18n.format(ours);
                if (locale != null) {
                    locale.put("ic2." + key, value);
                }
                if (language != null) {
                    language.put("ic2." + key, value);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            MatterRebirth.LOG.warn("Could not add translations for IC2 items: {}", e.toString());
        }
    }
}
