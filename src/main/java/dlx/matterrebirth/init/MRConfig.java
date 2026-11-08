package dlx.matterrebirth.init;

import dlx.matterrebirth.MatterRebirth;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Настройки мода (config/matterrebirth.cfg). Окно настроек Forge строится по этому классу само:
 * Моды → Matter Rebirth → Настроить. Подписи и подсказки берутся из lang-файлов.
 * Все числа стартовые (итоговый концепт 05.11.2026), уточняются при игре.
 */
@Config(modid = MatterRebirth.MOD_ID)
@Config.LangKey("matterrebirth.config.title")
public class MRConfig {

    @Config.Name("general")
    @Config.LangKey("matterrebirth.config.general")
    public static final General GENERAL = new General();

    @Config.Name("client")
    @Config.LangKey("matterrebirth.config.client")
    public static final Client CLIENT = new Client();

    public static class Client {

        // ореол колец рисует отрисовщик машины; с шейдерами может выглядеть иначе — тогда выключить (Антон 08.11)
        @Config.Name("ringGlow")
        @Config.LangKey("matterrebirth.config.client.ringGlow")
        @Config.Comment("Soft colored glow around the clone replicator's floor and emitter rings. Turn it off if it looks wrong with a shader pack; the rings keep their color.")
        public boolean ringGlow = true;
    }

    public static class General {

        @Config.Name("maxReplicatorsPerPlayer")
        @Config.LangKey("matterrebirth.config.general.maxReplicatorsPerPlayer")
        @Config.Comment("Maximum clone replicators per player (each keeps its chunk loaded). 0 = no limit.")
        @Config.RangeInt(min = 0, max = 1000)
        public int maxReplicatorsPerPlayer = 0;

        @Config.Name("soulboundEnchantments")
        @Config.LangKey("matterrebirth.config.general.soulboundEnchantments")
        @Config.Comment("Enchantment ids that keep an item with the player on clone transfer.")
        public String[] soulboundEnchantments = {"cofhcore:soulbound"};

        @Config.Name("bloodSampleUu")
        @Config.LangKey("matterrebirth.config.general.bloodSampleUu")
        @Config.Comment("UU-matter value of a blood sample pattern, in mB. The IC2 Scanner needs it, and the IC2 Replicator charges it for a copy of the sample.")
        @Config.RangeInt(min = 1, max = 1000)
        public int bloodSampleUu = 10;

        // TODO(релиз): ОТЛАДКА — убрать перед выпуском (просьба Антона 07.11). Ускоряет репликатор для проверок отката и т.п.
        @Config.Name("debugReplicatorSpeed")
        @Config.LangKey("matterrebirth.config.general.debugReplicatorSpeed")
        @Config.Comment("DEBUG, will be removed before release: replicator runs this many of its own ticks per game tick (growth, keeping, rollback). 1 = normal.")
        @Config.RangeInt(min = 1, max = 100)
        public int debugReplicatorSpeed = 1;
    }

    @Mod.EventBusSubscriber(modid = MatterRebirth.MOD_ID)
    public static class Events {

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (MatterRebirth.MOD_ID.equals(event.getModID())) {
                ConfigManager.sync(MatterRebirth.MOD_ID, Config.Type.INSTANCE);
            }
        }
    }
}
