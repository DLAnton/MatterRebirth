package dlx.matterrebirth.uu;

import dlx.matterrebirth.init.MRConfig;
import dlx.matterrebirth.init.MRContent;
import dlx.matterrebirth.item.ItemBloodSample;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Генетический шаблон: образец крови с владельцем, записанный сканером IC2.
 * Вызывается из миксина UuGraphMixin (сам миксин не должен содержать логики).
 */
public final class BloodPatterns {

    /** Внутренние единицы IC2: UuIndex.getInBuckets = UuGraph.get × 1e-5, то есть 1 mB = 100. */
    private static final double UNITS_PER_MB = 100.0;

    private BloodPatterns() {
    }

    /** Образец крови, у которого записан владелец. Пустой образец сканер не принимает. */
    public static boolean isBloodPattern(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() == MRContent.bloodSample
                && ItemBloodSample.getOwnerId(stack) != null;
    }

    /** Шаблон для сканера: один образец только с владельцем (без имени из наковальни и прочего NBT). */
    @Nullable
    public static ItemStack toPattern(ItemStack stack) {
        return isBloodPattern(stack) ? ItemBloodSample.cleanCopy(stack) : null;
    }

    /** Цена шаблона в UU (она же цена копии образца в репликаторе IC2). */
    public static double uuValue() {
        return MRConfig.GENERAL.bloodSampleUu * UNITS_PER_MB;
    }
}
