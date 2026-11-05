package dlx.matterrebirth.item;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.init.MRContent;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/** Простой предмет мода: имя реестра, ключ перевода matterrebirth.<имя>, вкладка мода, подсказка из lang. */
public class ItemBase extends Item {

    private EnumRarity rarity = EnumRarity.COMMON;

    public ItemBase(String name, int stackSize) {
        setRegistryName(MatterRebirth.MOD_ID, name);
        setTranslationKey(MatterRebirth.MOD_ID + "." + name);
        setCreativeTab(MRContent.TAB);
        setMaxStackSize(stackSize);
    }

    public ItemBase setRarity(EnumRarity rarity) {
        this.rarity = rarity;
        return this;
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return rarity;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        addTooltipLines(getTranslationKey(), tooltip);
    }

    /** Строки подсказки из lang: <ключ>.tip1, <ключ>.tip2 ... (серым). */
    @SideOnly(Side.CLIENT)
    public static void addTooltipLines(String key, List<String> tooltip) {
        for (int i = 1; I18n.hasKey(key + ".tip" + i); i++) {
            tooltip.add("§7" + I18n.format(key + ".tip" + i));
        }
    }
}
