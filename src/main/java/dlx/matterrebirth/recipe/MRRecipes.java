package dlx.matterrebirth.recipe;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.init.MRContent;
import ic2.api.item.IC2Items;
import ic2.api.recipe.Recipes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Рецепты мода (итоговый концепт 05.11.2026, стартовые — уточняются в тестах).
 * Рецепты с деталями IC2 регистрируются через рецепт-менеджер IC2 (как у самого IC2: ингредиенты IC2 сравниваются
 * правильно, строка = словарь руд). Объединение и разборка синхронизатора с кожаным шлемом — свои рецепты.
 */
@Mod.EventBusSubscriber(modid = MatterRebirth.MOD_ID)
public final class MRRecipes {

    private static final ResourceLocation GROUP_SYNC = new ResourceLocation(MatterRebirth.MOD_ID, "sync_leather");

    private MRRecipes() {
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        event.getRegistry().register(new RecipeSyncLeather.Combine(GROUP_SYNC)
                .setRegistryName(MatterRebirth.MOD_ID, "neuro_synchronizer_leather"));
        event.getRegistry().register(new RecipeSyncLeather.Split(GROUP_SYNC)
                .setRegistryName(MatterRebirth.MOD_ID, "neuro_synchronizer_split"));
    }

    /** Вызывается в init: к этому моменту предметы и менеджеры рецептов IC2 готовы. */
    public static void init() {
        ItemStack enderPearlDust = ic2("dust", "ender_pearl");
        ItemStack energiumDust = ic2("dust", "energium");
        ItemStack lithiumDust = ic2("dust", "lithium");
        ItemStack ironCasing = ic2("casing", "iron");
        ItemStack copperCable = ic2("cable", "type:copper,insulation:1");
        ItemStack carbonPlate = ic2("crafting", "carbon_plate");
        ItemStack iridiumReflector = ic2("iridium_reflector", null);
        ItemStack thickReflector = ic2("thick_neutron_reflector", null);
        ItemStack teleporter = ic2("te", "teleporter");
        ItemStack hvTransformer = ic2("te", "hv_transformer");
        ItemStack reinforcedGlass = ic2("glass", "reinforced");

        ItemStack crystal = new ItemStack(MRContent.resonantCrystal);

        // Вакуумный шприц: пробирка из стеклянных панелей, пробка из резины, игла из железной оболочки
        shaped(new ItemStack(MRContent.vacuumSyringe),
                "  O", " R ", "FF ", 'O', ironCasing, 'R', "itemRubber", 'F', "paneGlass");

        // Резонирующая смесь: 4 пыли жемчуга Края, 4 энергетические, литиевая (без формы) → 9 смесей (жемчуг, 06.11)
        if (enderPearlDust != null && energiumDust != null && lithiumDust != null) {
            Recipes.advRecipes.addShapelessRecipe(new ItemStack(MRContent.resonantMixture, 9),
                    enderPearlDust, enderPearlDust, enderPearlDust, enderPearlDust,
                    energiumDust, energiumDust, energiumDust, energiumDust, lithiumDust);
        }

        // Резонирующий кристалл: компрессор IC2, 9 смесей
        Recipes.compressor.addRecipe(Recipes.inputFactory.forStack(new ItemStack(MRContent.resonantMixture), 9),
                null, false, crystal.copy());

        // Нейросинхронизатор (вариант Антона): провода / схема, углепластина, схема / кристалл, -, кристалл
        shaped(new ItemStack(MRContent.neuroSynchronizer),
                "WWW", "CBC", "e e", 'W', copperCable, 'C', "circuitBasic", 'B', carbonPlate, 'e', crystal);

        // Межпространственный якорь: иридиевый отражатель, 4 кристалла, 4 усовершенствованные схемы
        shaped(new ItemStack(MRContent.dimensionalAnchor),
                "AeA", "eMe", "AeA", 'A', "circuitAdvanced", 'e', crystal, 'M', iridiumReflector);

        // Конструкционный блок репликатора (по мотивам репликатора IC2)
        shaped(new ItemStack(MRContent.replicatorCasing),
                "ZEZ", "VeV", "JEJ", 'Z', thickReflector, 'E', teleporter, 'V', reinforcedGlass, 'e', crystal, 'J', hvTransformer);
    }

    private static void shaped(ItemStack output, Object... recipe) {
        for (Object o : recipe) {
            if (o == null) {
                MatterRebirth.LOG.error("Recipe for {} skipped: an IC2 ingredient is missing", output.getItem().getRegistryName());
                return;
            }
        }
        Recipes.advRecipes.addRecipe(output, recipe);
    }

    private static ItemStack ic2(String name, String variant) {
        ItemStack stack = variant == null ? IC2Items.getItem(name) : IC2Items.getItem(name, variant);
        if (stack == null || stack.isEmpty()) {
            MatterRebirth.LOG.error("IC2 item not found: {}#{}", name, variant);
            return null;
        }
        return stack;
    }
}
