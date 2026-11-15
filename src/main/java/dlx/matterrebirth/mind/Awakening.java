package dlx.matterrebirth.mind;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.FoodStats;

/**
 * Пробуждение в клоне (этап 6в, концепт и Антон 15.11). Тело новое: эффекты старого снимаются все, сытость на половине
 * шкалы. Перенос — сильнейший стресс для организма: сразу Слепота 3 с и Тошнота 8 с, на 60 с Слабость II, Замедление I
 * и Усталость II; с квантовым нейрошлемом все сроки вдвое короче. Молоко снимает их, как любые эффекты (Антон 15.11).
 * Огонь, падение, воздух и торчащие стрелы тоже сбрасываются.
 * Здоровье клона ставится ещё до перемещения (MindTransfer.moveTo); Хардкор не срабатывает, потому что смерть отменена.
 */
final class Awakening {

    private static final int SECOND = 20;

    private Awakening() {
    }

    static void apply(EntityPlayerMP player, boolean quantum) {
        resetBody(player, quantum);

        // setFoodSaturationLevel в игре только клиентский, поэтому через запись сытости: уровень 10 из 20, насыщения
        // и усталости нет; клиенту игра сама пришлёт новые значения на следующем тике
        FoodStats food = player.getFoodStats();
        NBTTagCompound tag = new NBTTagCompound();
        food.writeNBT(tag);
        tag.setInteger("foodLevel", 10);
        tag.setInteger("foodTickTimer", 0);
        tag.setFloat("foodSaturationLevel", 0);
        tag.setFloat("foodExhaustionLevel", 0);
        food.readNBT(tag);
    }

    /**
     * Всё, что тело несло с собой, снимается, и ставятся эффекты переноса. Зовётся сразу при переносе и ещё раз
     * в конце тика игрока (MindTransfer.onPlayerTick): многое игра или моды накладывают уже ПОСЛЕ смертельного удара
     * в том же тике — поджог от лавы, иссушение черепа Иссушителя и скелета-иссушителя, яд пещерного паука, эффекты
     * стрелы с зельем (Антон 15.11: при смерти снимаются все возможные эффекты).
     */
    static void resetBody(EntityPlayerMP player, boolean quantum) {
        player.clearActivePotions(); // и поглощение от золотого яблока: оно держится эффектом
        player.extinguish();
        player.fallDistance = 0;
        player.setArrowCountInEntity(0); // стрелы, торчащие из тела
        player.setAir(300);

        int div = quantum ? 2 : 1;
        add(player, MobEffects.BLINDNESS, 3 * SECOND / div, 0);
        add(player, MobEffects.NAUSEA, 8 * SECOND / div, 0);
        add(player, MobEffects.WEAKNESS, 60 * SECOND / div, 1);
        add(player, MobEffects.SLOWNESS, 60 * SECOND / div, 0);
        add(player, MobEffects.MINING_FATIGUE, 60 * SECOND / div, 1);
    }

    private static void add(EntityPlayerMP player, Potion potion, int ticks, int amplifier) {
        player.addPotionEffect(new PotionEffect(potion, ticks, amplifier));
    }
}
