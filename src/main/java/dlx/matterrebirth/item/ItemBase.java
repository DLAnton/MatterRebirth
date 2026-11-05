package dlx.matterrebirth.item;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.init.MRContent;
import net.minecraft.item.Item;

/** Простой предмет мода: имя реестра, ключ перевода matterrebirth.<имя>, вкладка мода. */
public class ItemBase extends Item {

    public ItemBase(String name, int stackSize) {
        setRegistryName(MatterRebirth.MOD_ID, name);
        setTranslationKey(MatterRebirth.MOD_ID + "." + name);
        setCreativeTab(MRContent.TAB);
        setMaxStackSize(stackSize);
    }
}
