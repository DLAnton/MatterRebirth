package dlx.matterrebirth.compat;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.inventory.ContainerReplicator;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;
import java.util.function.BiConsumer;

/**
 * Inventory Bogo Sorter: регистрируем окно репликатора, чтобы над инвентарём игрока появились кнопки сортировки.
 * Слоты самой машины мод не сортирует (пустое правило). Вызов через отражение, поэтому Bogo Sorter не нужен для сборки.
 */
public final class BogoSorterCompat {

    private BogoSorterCompat() {
    }

    public static void init() {
        if (!Loader.isModLoaded("bogosorter")) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName("com.cleanroommc.bogosorter.api.IBogoSortAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Method addCompat = apiClass.getMethod("addCompat", Class.class, BiConsumer.class);
            BiConsumer<Object, Object> noMachineSlots = (container, builder) -> { };
            addCompat.invoke(api, ContainerReplicator.class, noMachineSlots);
        } catch (Throwable e) {
            MatterRebirth.LOG.warn("Bogo Sorter compat failed", e);
        }
    }
}
