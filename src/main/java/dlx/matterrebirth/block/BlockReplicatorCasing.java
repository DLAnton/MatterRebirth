package dlx.matterrebirth.block;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.init.MRContent;
import ic2.api.tile.IWrenchable;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import dlx.matterrebirth.item.ItemBase;
import ic2.api.item.IC2Items;
import ic2.core.init.MainConfig;
import ic2.core.util.ConfigUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Конструкционный блок репликатора. Один — обычный блок корпуса; второй, поставленный на первый (или под него),
 * сам собирает оба в репликатор клонов (BlockReplicator.tryForm). Ключ IC2 снимает блок, как машины IC2.
 * Слом без ключа — как у репликатора IC2 (Антон 08.11): выпадает только улучшенный корпус машины IC2 (dropFor).
 * ПКМ по двум уже стоящим друг на друге блокам тоже собирает машину (например, если при установке мешал лимит,
 * а потом его подняли); Shift+ПКМ ничего не делает, чтобы можно было ставить блоки рядом.
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
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing side, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) {
            return false;
        }
        boolean pair = world.getBlockState(pos.down()).getBlock() == this || world.getBlockState(pos.up()).getBlock() == this;
        if (!pair) {
            return false; // одиночный блок: клик проходит дальше (можно поставить второй блок сверху)
        }
        if (!world.isRemote && hand == EnumHand.MAIN_HAND) {
            BlockReplicator.tryForm(world, pos, player); // те же проверки лимита и сообщение, что при установке
        }
        return true; // клик по паре съеден обеими руками, иначе из второй руки поставился бы блок
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

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        drops.add(dropFor(harvesters.get(), state));
    }

    /**
     * Что выпадает при сломе конструкционного блока или части машины — правило машин IC2 (TileEntityBlock.adjustDrop,
     * у репликатора и сканера IC2 «AdvMachine»): ломали ключом (инструмент с классом «wrench») или в настройках IC2
     * включено balance/ignoreWrenchRequirement — сам блок; иначе (кирка, взрыв, без игрока) — улучшенный корпус машины IC2.
     * Снятие ключом по ПКМ идёт через getWrenchDrops и всегда отдаёт сам блок.
     */
    static ItemStack dropFor(@Nullable EntityPlayer player, IBlockState state) {
        ItemStack self = new ItemStack(MRContent.replicatorCasing);
        if (ConfigUtil.getBool(MainConfig.get(), "balance/ignoreWrenchRequirement")) {
            return self;
        }
        if (player != null) {
            ItemStack tool = player.getHeldItemMainhand();
            if (!tool.isEmpty() && tool.getItem().getHarvestLevel(tool, "wrench", player, state) >= 0) {
                return self;
            }
        }
        ItemStack casing = IC2Items.getItem("resource", "advanced_machine");
        return casing == null || casing.isEmpty() ? self : casing.copy();
    }
}
