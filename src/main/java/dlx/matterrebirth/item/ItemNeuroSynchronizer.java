package dlx.matterrebirth.item;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.init.MRContent;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Нейросинхронизатор: носится в слоте шлема. Металлические шлемы с ним несовместимы — он сам занимает слот.
 * Вариант в кожаном шлеме сделан на материале LEATHER: 1 очко брони и покраска работают штатно.
 * Не изнашивается и не зачаровывается.
 */
public class ItemNeuroSynchronizer extends ItemArmor {

    public static final ArmorMaterial SYNC_MATERIAL = EnumHelper.addArmorMaterial("MATTERREBIRTH_SYNCHRONIZER",
            MatterRebirth.MOD_ID + ":neuro_synchronizer", 0, new int[]{0, 0, 0, 0}, 0, SoundEvents.ITEM_ARMOR_EQUIP_GENERIC, 0.0F);

    private final boolean leather;

    public ItemNeuroSynchronizer(String name, boolean leather) {
        super(leather ? ArmorMaterial.LEATHER : SYNC_MATERIAL, 0, EntityEquipmentSlot.HEAD);
        this.leather = leather;
        setRegistryName(MatterRebirth.MOD_ID, name);
        setTranslationKey(MatterRebirth.MOD_ID + "." + name);
        setCreativeTab(MRContent.TAB);
        setMaxDamage(0);   // не изнашивается
        setMaxStackSize(1);
    }

    public boolean isLeather() {
        return leather;
    }

    /** Без брони у обычного синхронизатора нет и пустой строки «Когда надето на голову:» в подсказке. */
    @Override
    public Multimap<String, AttributeModifier> getItemAttributeModifiers(EntityEquipmentSlot slot) {
        return leather ? super.getItemAttributeModifiers(slot) : HashMultimap.create();
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.RARE;
    }

    @Override
    public int getItemEnchantability() {
        return 0;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return false;
    }

    @Nullable
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EntityEquipmentSlot slot, String type) {
        // Кожаный вариант: штатная текстура кожаного шлема (null). Собственный вид на голове — этап «Облик».
        return leather ? null : MatterRebirth.MOD_ID + ":textures/models/armor/neuro_synchronizer_layer_1.png";
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        ItemBase.addTooltipLines(getTranslationKey(), tooltip);
    }
}
