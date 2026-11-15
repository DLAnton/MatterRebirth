package dlx.matterrebirth.recipe;

import dlx.matterrebirth.item.NeuroLink;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;

/**
 * Квантовый нейрошлем (Антон 09.11): рецепт синхронизатора, где вместо железной оболочки в центре квантовый шлем IC2.
 * Выходит тот же шлем — заряд, цвет и прочее NBT переносятся — с отметкой «Нейросвязь». Уже отмеченный шлем рецепт
 * не принимает.
 */
public class RecipeNeuroHelmet extends ShapedOreRecipe {

    public RecipeNeuroHelmet(ResourceLocation group, Item quantumHelmet, ItemStack glassCable, ItemStack crystal) {
        super(group, marked(new ItemStack(quantumHelmet)), "WWW", "CQC", "e e",
                'W', glassCable, 'C', "circuitAdvanced", 'Q', new ItemStack(quantumHelmet, 1, OreDictionary.WILDCARD_VALUE),
                'e', crystal);
    }

    private static ItemStack marked(ItemStack stack) {
        NeuroLink.mark(stack);
        return stack;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        return super.matches(inv, world) && !helmet(inv).isEmpty() && !NeuroLink.isNeuroHelmet(helmet(inv));
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        ItemStack helmet = helmet(inv);
        if (helmet.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = helmet.copy();
        out.setCount(1);
        NeuroLink.mark(out);
        return out;
    }

    private static ItemStack helmet(InventoryCrafting inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (NeuroLink.isQuantumHelmet(s)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }
}
