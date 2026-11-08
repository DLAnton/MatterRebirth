package dlx.matterrebirth.item;

import com.mojang.authlib.properties.Property;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Образец крови: шприц с полной пробиркой. Хранит владельца (UUID и имя на момент забора) и его скин, если сервер его знает.
 * Клон воскрешает только владельца образца.
 */
public class ItemBloodSample extends ItemBase {

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_NAME = "Name";
    /** Скин донора на момент забора: свойство «textures» его профиля (адрес картинки скина), без подписи. */
    private static final String TAG_SKIN = "Skin";

    public ItemBloodSample(String name) {
        super(name, 1);
    }

    public ItemStack create(EntityPlayer player) {
        ItemStack stack = new ItemStack(this);
        NBTTagCompound owner = new NBTTagCompound();
        owner.setUniqueId("Id", player.getUniqueID());
        owner.setString(TAG_NAME, player.getName());
        // скин для кожи клона (Антон 08.11): так клону не нужен запрос к Mojang, картинка берётся из кэша скинов игры.
        // В одиночной игре и на сервере без проверки входа у профиля этого свойства нет — тогда не пишется
        for (Property p : player.getGameProfile().getProperties().get("textures")) {
            owner.setString(TAG_SKIN, p.getValue());
            break;
        }
        stack.setTagInfo(TAG_OWNER, owner);
        return stack;
    }

    /** Скин донора, запомненный при заборе (значение свойства «textures» профиля), или null. */
    @Nullable
    public static String getSkin(ItemStack stack) {
        NBTTagCompound owner = stack.getSubCompound(TAG_OWNER);
        return owner != null && owner.hasKey(TAG_SKIN) ? owner.getString(TAG_SKIN) : null;
    }

    /** Копия образца: один предмет, в NBT только владелец. Так выглядит шаблон в сканере IC2. */
    public static ItemStack cleanCopy(ItemStack stack) {
        ItemStack copy = new ItemStack(stack.getItem());
        NBTTagCompound owner = stack.getSubCompound(TAG_OWNER);
        if (owner != null) {
            copy.setTagInfo(TAG_OWNER, owner.copy());
        }
        return copy;
    }

    @Nullable
    public static UUID getOwnerId(ItemStack stack) {
        NBTTagCompound owner = stack.getSubCompound(TAG_OWNER);
        return owner != null && owner.hasUniqueId("Id") ? owner.getUniqueId("Id") : null;
    }

    @Nullable
    public static String getOwnerName(ItemStack stack) {
        NBTTagCompound owner = stack.getSubCompound(TAG_OWNER);
        return owner != null && owner.hasKey(TAG_NAME) ? owner.getString(TAG_NAME) : null;
    }

    /**
     * Во вкладке творческого режима (и в списке HEI) образца нет: без владельца он бесполезен,
     * настоящий образец берут шприцем. Иконкой вкладки он остаётся.
     */
    @Override
    public void getSubItems(CreativeTabs tab, NonNullList<ItemStack> items) {
    }

    /** Имя владельца в названии: его видно в окнах IC2 (сканер, хранилище) и в подсказке кристалла памяти. */
    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String name = super.getItemStackDisplayName(stack);
        String owner = getOwnerName(stack);
        return owner != null ? name + " (" + owner + ")" : name;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        String owner = getOwnerName(stack);
        tooltip.add("§e" + I18n.format("item.matterrebirth.blood_sample.owner",
                owner != null ? owner : I18n.format("item.matterrebirth.blood_sample.no_owner")));
        super.addInformation(stack, world, tooltip, flag);
    }
}
