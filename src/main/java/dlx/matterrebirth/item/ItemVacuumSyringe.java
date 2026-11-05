package dlx.matterrebirth.item;

import dlx.matterrebirth.init.MRContent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;

/** Вакуумный шприц: ПКМ берёт кровь у самого игрока и превращается в образец крови с его именем. */
public class ItemVacuumSyringe extends ItemBase {

    public ItemVacuumSyringe(String name) {
        super(name, 16);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack held = player.getHeldItem(hand);
        world.playSound(null, player.posX, player.posY, player.posZ, SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.PLAYERS, 0.8F, 1.4F);
        if (!world.isRemote) {
            ItemStack sample = ((ItemBloodSample) MRContent.bloodSample).create(player);
            if (!player.capabilities.isCreativeMode) {
                held.shrink(1);
            }
            if (held.isEmpty()) {
                return new ActionResult<>(EnumActionResult.SUCCESS, sample);
            }
            if (!player.inventory.addItemStackToInventory(sample)) {
                player.dropItem(sample, false);
            }
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, held);
    }
}
