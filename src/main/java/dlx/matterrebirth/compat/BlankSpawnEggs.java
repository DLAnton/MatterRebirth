package dlx.matterrebirth.compat;

import dlx.matterrebirth.MatterRebirth;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Вторая половина заплатки EntitySerializeMixin (Антон 15.11). Без вылета моды, которые кладут умершему существу яйцо
 * призыва по его названию (Enhanced Mob Spawners), кладут игроку яйцо с пустым названием — из него никто не появляется.
 * Такие яйца (есть EntityTag, но «id» пустой) убираются из вещей умершего игрока. Обычные яйца игрока не трогаются.
 */
@Mod.EventBusSubscriber(modid = MatterRebirth.MOD_ID)
public final class BlankSpawnEggs {

    private BlankSpawnEggs() {
    }

    /** LOWEST: после всех, кто добавляет вещи. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }
        event.getDrops().removeIf(drop -> isBlank(drop));
    }

    private static boolean isBlank(EntityItem drop) {
        ItemStack stack = drop.getItem();
        if (stack.getItem() != Items.SPAWN_EGG) {
            return false;
        }
        NBTTagCompound entity = stack.getSubCompound("EntityTag");
        return entity != null && entity.getString("id").isEmpty();
    }
}
