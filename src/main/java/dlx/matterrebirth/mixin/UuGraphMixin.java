package dlx.matterrebirth.mixin;

import dlx.matterrebirth.uu.BloodPatterns;
import ic2.core.uu.UuGraph;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * UU-справочник IC2. Сканер, хранилище шаблонов, кристалл памяти и репликатор IC2
 * узнают шаблон и его цену только через UuGraph.find и UuGraph.get.
 * Для образца крови с владельцем:
 *  - find возвращает сам образец (с владельцем), а не «чистый» предмет без NBT;
 *  - get возвращает цену из настроек, иначе IC2 считает предмет несканируемым.
 * Остальные предметы IC2 обрабатывает как обычно.
 */
@Mixin(value = UuGraph.class, remap = false)
public abstract class UuGraphMixin {

    @Inject(method = "find", at = @At("HEAD"), cancellable = true)
    private static void matterrebirth$find(ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack pattern = BloodPatterns.toPattern(stack);
        if (pattern != null) {
            cir.setReturnValue(pattern);
        }
    }

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private static void matterrebirth$get(ItemStack stack, CallbackInfoReturnable<Double> cir) {
        if (BloodPatterns.isBloodPattern(stack)) {
            cir.setReturnValue(BloodPatterns.uuValue());
        }
    }
}
