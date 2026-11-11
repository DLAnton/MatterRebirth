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

    @Config.Name("balance")
    @Config.LangKey("matterrebirth.config.balance")
    public static final Balance BALANCE = new Balance();

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

    /**
     * Цены клона (5в+, Антон 11.11). Числа сервера; читаются при запуске мира (TileReplicator.loadBalance), поэтому
     * новые значения действуют после перезахода в мир. UU и время делятся по стадиям в постоянной пропорции
     * 20 / 25 / 15 / 30 / 10 % (скелет, нервы, сосуды, мышцы, кожа).
     */
    public static class Balance {

        @Config.Name("cloneUu")
        @Config.LangKey("matterrebirth.config.balance.cloneUu")
        @Config.Comment("UU-matter for growing one clone, in mB. Split between the stages 20/25/15/30/10 % (skeleton, nerves, vessels, muscles, skin). 16000 is a full replicator tank. Applies after re-entering the world.")
        @Config.RangeInt(min = 20, max = 16000)
        @Config.RequiresWorldRestart
        public int cloneUu = 2000;

        @Config.Name("cloneGrowMinutes")
        @Config.LangKey("matterrebirth.config.balance.cloneGrowMinutes")
        @Config.Comment("Time to grow one clone without overclockers, in minutes. Split between the stages in the same proportion as the UU-matter. Applies after re-entering the world.")
        @Config.RangeInt(min = 1, max = 120)
        @Config.RequiresWorldRestart
        public int cloneGrowMinutes = 20;

        @Config.Name("growEu")
        @Config.LangKey("matterrebirth.config.balance.growEu")
        @Config.Comment("Energy for growth without overclockers, EU/t. 2048 is the replicator's input limit (HV). Applies after re-entering the world.")
        @Config.RangeInt(min = 1, max = 2048)
        @Config.RequiresWorldRestart
        public int growEu = 512;

        @Config.Name("holdEu")
        @Config.LangKey("matterrebirth.config.balance.holdEu")
        @Config.Comment("Energy that keeps the grown layers from rolling back while growth stops (pause, no UU-matter, low energy), EU/t. Applies after re-entering the world.")
        @Config.RangeInt(min = 1, max = 512)
        @Config.RequiresWorldRestart
        public int holdEu = 16;

        @Config.Name("lifeEu")
        @Config.LangKey("matterrebirth.config.balance.lifeEu")
        @Config.Comment("Energy that keeps a grown clone alive, EU/t. Applies after re-entering the world.")
        @Config.RangeInt(min = 1, max = 2048)
        @Config.RequiresWorldRestart
        public int lifeEu = 32;

        @Config.Name("lifeUuPerDay")
        @Config.LangKey("matterrebirth.config.balance.lifeUuPerDay")
        @Config.Comment("UU-matter that keeps a grown clone alive, mB per in-game day. Pipes leave 10 days of it in the tank. Applies after re-entering the world.")
        @Config.RangeInt(min = 1, max = 1000)
        @Config.RequiresWorldRestart
        public int lifeUuPerDay = 24;
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
