package dlx.matterrebirth.recipe;

import dlx.matterrebirth.init.MRContent;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * Синхронизатор + кожаный шлем ⇄ синхронизатор в кожаном шлеме (верстак, без формы).
 * При объединении зачарования шлема снимаются, цвет и износ шлема сохраняются; при разборке шлем возвращается.
 */
public final class RecipeSyncLeather {

    private static final String TAG_HELMET_DAMAGE = "HelmetDamage";

    private RecipeSyncLeather() {
    }

    /** Объединение. */
    public static class Combine extends ShapelessOreRecipe {

        public Combine(ResourceLocation group) {
            super(group, new ItemStack(MRContent.neuroSynchronizerLeather), MRContent.neuroSynchronizer,
                    new ItemStack(Items.LEATHER_HELMET, 1, OreDictionary.WILDCARD_VALUE));
        }

        @Override
        public ItemStack getCraftingResult(InventoryCrafting inv) {
            ItemStack out = new ItemStack(MRContent.neuroSynchronizerLeather);
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s.getItem() == Items.LEATHER_HELMET) {
                    ItemArmor helmet = (ItemArmor) s.getItem();
                    if (helmet.hasColor(s)) {
                        ((ItemArmor) out.getItem()).setColor(out, helmet.getColor(s));
                    }
                    if (s.getItemDamage() > 0) {
                        out.setTagInfo(TAG_HELMET_DAMAGE, new net.minecraft.nbt.NBTTagInt(s.getItemDamage()));
                    }
                }
            }
            return out;
        }
    }

    /** Разборка: синхронизатор в результат, кожаный шлем остаётся в сетке. */
    public static class Split extends ShapelessOreRecipe {

        public Split(ResourceLocation group) {
            super(group, new ItemStack(MRContent.neuroSynchronizer), MRContent.neuroSynchronizerLeather);
        }

        @Override
        public NonNullList<ItemStack> getRemainingItems(InventoryCrafting inv) {
            NonNullList<ItemStack> rest = ForgeHooks.defaultRecipeGetRemainingItems(inv);
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                ItemStack s = inv.getStackInSlot(i);
                if (s.getItem() == MRContent.neuroSynchronizerLeather) {
                    ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
                    ItemArmor sync = (ItemArmor) s.getItem();
                    if (sync.hasColor(s)) {
                        Items.LEATHER_HELMET.setColor(helmet, sync.getColor(s));
                    }
                    if (s.getTagCompound() != null && s.getTagCompound().hasKey(TAG_HELMET_DAMAGE)) {
                        helmet.setItemDamage(s.getTagCompound().getInteger(TAG_HELMET_DAMAGE));
                    }
                    rest.set(i, helmet);
                }
            }
            return rest;
        }
    }
}
