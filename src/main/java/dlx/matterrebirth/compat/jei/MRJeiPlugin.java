package dlx.matterrebirth.compat.jei;

import dlx.matterrebirth.init.MRContent;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.IFocus;
import net.minecraft.item.ItemStack;

/**
 * Связь с HEI/JEI. Класс загружается только при установленном HEI/JEI (окно машины проверяет Loader.isModLoaded("jei")).
 * Наполнение (рецепт по стадиям и страница с описанием работы машины) — этап 7, когда вся логика машины проверена
 * (решение Антона 07.11); пока кнопка «i» открывает рецепты самой машины.
 */
@JEIPlugin
public class MRJeiPlugin implements IModPlugin {

    private static IJeiRuntime runtime;

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    /** Открыть в HEI страницу машины. false — HEI ещё не готов. */
    public static boolean showMachine() {
        if (runtime == null || MRContent.replicatorCasing == null) {
            return false;
        }
        ItemStack machine = new ItemStack(MRContent.replicatorCasing);
        runtime.getRecipesGui().show(runtime.getRecipeRegistry().createFocus(IFocus.Mode.OUTPUT, machine));
        return true;
    }
}
