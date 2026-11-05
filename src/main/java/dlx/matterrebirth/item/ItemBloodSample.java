package dlx.matterrebirth.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Образец крови: шприц с полной пробиркой. Хранит владельца (UUID и имя на момент забора).
 * Клон воскрешает только владельца образца.
 */
public class ItemBloodSample extends ItemBase {

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_NAME = "Name";

    public ItemBloodSample(String name) {
        super(name, 1);
    }

    public ItemStack create(EntityPlayer player) {
        ItemStack stack = new ItemStack(this);
        NBTTagCompound owner = new NBTTagCompound();
        owner.setUniqueId("Id", player.getUniqueID());
        owner.setString(TAG_NAME, player.getName());
        stack.setTagInfo(TAG_OWNER, owner);
        return stack;
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

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        String owner = getOwnerName(stack);
        tooltip.add("§e" + I18n.format("item.matterrebirth.blood_sample.owner",
                owner != null ? owner : I18n.format("item.matterrebirth.blood_sample.no_owner")));
        super.addInformation(stack, world, tooltip, flag);
    }
}
