package dlx.matterrebirth.mixin.early;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Заплатка от вылета (Антон 15.11). Forge-метод Entity.serializeNBT пишет в NBT «id» — название существа. У игрока
 * (и у любого незарегистрированного существа) названия нет, и игра падает с «Null string not allowed». Так делают
 * моды, которые читают данные любого умершего существа: Enhanced Mob Spawners 1.4.4 в LivingDropsEvent ронял игру
 * при смерти игрока. Для существа без названия «id» просто не пишется, остальные данные — как обычно.
 * Срабатывает сама и только там, где игра упала бы (без названия исходный код падает всегда), поэтому настройки
 * у заплатки нет (Антон 15.11). Для существ с названием ничего не меняется.
 */
@Mixin(Entity.class)
public abstract class EntitySerializeMixin {

    @Inject(method = "serializeNBT", at = @At("HEAD"), cancellable = true, remap = false)
    private void matterrebirth$serializeWithoutId(CallbackInfoReturnable<NBTTagCompound> cir) {
        Entity self = (Entity) (Object) this;
        if (EntityList.getKey(self) == null) {
            cir.setReturnValue(self.writeToNBT(new NBTTagCompound()));
        }
    }
}
