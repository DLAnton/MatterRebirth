package dlx.matterrebirth.block;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.init.MRContent;
import ic2.api.tile.IWrenchable;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import dlx.matterrebirth.item.ItemBase;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Конструкционный блок репликатора. Один — обычный блок корпуса; второй, поставленный на первый (или под него),
 * сам собирает оба в репликатор клонов (BlockReplicator.tryForm). Ключ IC2 снимает блок, как машины IC2.
 */
public class BlockReplicatorCasing extends Block implements IWrenchable {

    public BlockReplicatorCasing(String name) {
        super(Material.IRON);
        setRegistryName(MatterRebirth.MOD_ID, name);
        setTranslationKey(MatterRebirth.MOD_ID + "." + name);
        setCreativeTab(MRContent.TAB);
        setHardness(5.0F);
        setResistance(10.0F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 1);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        BlockReplicator.tryForm(world, pos, placer);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        ItemBase.addTooltipLines(getTranslationKey(), tooltip);
    }

    /** Поршень не двигает даже отдельный конструкционный блок. */
    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    // --- Ключ IC2 снимает блок; поворачивать у блока нечего ---

    @Override
    public EnumFacing getFacing(World world, BlockPos pos) {
        return EnumFacing.NORTH;
    }

    @Override
    public boolean canSetFacing(World world, BlockPos pos, EnumFacing newDirection, EntityPlayer player) {
        return false;
    }

    @Override
    public boolean setFacing(World world, BlockPos pos, EnumFacing newDirection, EntityPlayer player) {
        return false;
    }

    @Override
    public boolean wrenchCanRemove(World world, BlockPos pos, EntityPlayer player) {
        return true;
    }

    @Override
    public List<ItemStack> getWrenchDrops(World world, BlockPos pos, IBlockState state, TileEntity te, EntityPlayer player, int fortune) {
        return Collections.singletonList(new ItemStack(this));
    }
}
