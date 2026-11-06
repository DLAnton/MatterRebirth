package dlx.matterrebirth.block;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.init.MRContent;
import dlx.matterrebirth.inventory.MRGuiHandler;
import dlx.matterrebirth.tile.TileReplicator;
import dlx.matterrebirth.tile.TileReplicatorUpper;
import ic2.api.tile.IWrenchable;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.EnumPushReaction;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Репликатор клонов: два конструкционных блока, поставленные друг на друга, сами превращаются в эту машину.
 * Нижняя часть главная (в ней TileReplicator: владелец, позже клон, заряд и слоты), верхняя только передаёт подключения.
 * Если сломать любую часть, выпадает конструкционный блок, а вторая часть снова становится конструкционным блоком.
 * Ключи не поворачивают машину: ключ IC2 снимает часть, по которой ударили (как слом), остальные ключи ничего не делают.
 */
public class BlockReplicator extends Block implements IWrenchable {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    public static final PropertyEnum<Part> PART = PropertyEnum.create("part", Part.class);

    public enum Part implements IStringSerializable {
        LOWER, UPPER;

        @Override
        public String getName() {
            return this == LOWER ? "lower" : "upper";
        }
    }

    public BlockReplicator(String name) {
        super(Material.IRON);
        setRegistryName(MatterRebirth.MOD_ID, name);
        setTranslationKey(MatterRebirth.MOD_ID + "." + name);
        setHardness(5.0F);
        setResistance(10.0F);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 1);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(PART, Part.LOWER));
    }

    /**
     * Пробует собрать машину после установки конструкционного блока: второй блок встал на первый
     * (или под него). Машина смотрит лицом на игрока, поставившего второй блок; он же владелец.
     */
    public static void tryForm(World world, BlockPos placed, @Nullable EntityLivingBase placer) {
        if (world.isRemote) {
            return;
        }
        Block casing = MRContent.replicatorCasing;
        BlockPos lower;
        if (world.getBlockState(placed.down()).getBlock() == casing) {
            lower = placed.down();
        } else if (world.getBlockState(placed.up()).getBlock() == casing) {
            lower = placed;
        } else {
            return;
        }
        EnumFacing facing = placer != null ? placer.getHorizontalFacing().getOpposite() : EnumFacing.NORTH;
        IBlockState base = MRContent.replicator.getDefaultState().withProperty(FACING, facing);
        world.setBlockState(lower, base.withProperty(PART, Part.LOWER), 3);
        world.setBlockState(lower.up(), base.withProperty(PART, Part.UPPER), 3);
        TileEntity te = world.getTileEntity(lower);
        if (te instanceof TileReplicator && placer instanceof EntityPlayer) {
            ((TileReplicator) te).setOwner((EntityPlayer) placer);
        }
        world.playSound(null, lower.up(), SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 0.6F, 1.4F);
    }

    /** Нижняя (главная) часть машины для любой из двух частей. */
    public static BlockPos lowerPos(IBlockState state, BlockPos pos) {
        return state.getValue(PART) == Part.LOWER ? pos : pos.down();
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        // Содержимое слотов выпадает, как у машин IC2 (до того, как нижняя часть снова станет блоком и потеряет слоты)
        TileEntity te = world.getTileEntity(lowerPos(state, pos));
        if (te instanceof TileReplicator) {
            ((TileReplicator) te).dropContents(world, pos);
        }
        BlockPos other = state.getValue(PART) == Part.LOWER ? pos.up() : pos.down();
        if (world.getBlockState(other).getBlock() == this) {
            world.setBlockState(other, MRContent.replicatorCasing.getDefaultState(), 3);
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing side, float hitX, float hitY, float hitZ) {
        if (player.isSneaking()) {
            return false;
        }
        if (!world.isRemote && hand == EnumHand.MAIN_HAND) {
            BlockPos lower = lowerPos(state, pos);
            if (world.getTileEntity(lower) instanceof TileReplicator) {
                player.openGui(MatterRebirth.instance, MRGuiHandler.REPLICATOR, world, lower.getX(), lower.getY(), lower.getZ());
            }
        }
        return true;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return Item.getItemFromBlock(MRContent.replicatorCasing);
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return new ItemStack(MRContent.replicatorCasing);
    }

    @Override
    public EnumPushReaction getPushReaction(IBlockState state) {
        return EnumPushReaction.BLOCK;
    }

    // --- Ключи: поворот запрещён (машина смотрит туда, куда её собрали), ключ IC2 снимает часть ---

    @Override
    public boolean rotateBlock(World world, BlockPos pos, EnumFacing axis) {
        return false;
    }

    @Override
    public EnumFacing[] getValidRotations(World world, BlockPos pos) {
        return new EnumFacing[0];
    }

    @Override
    public EnumFacing getFacing(World world, BlockPos pos) {
        return world.getBlockState(pos).getValue(FACING);
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
        return Collections.singletonList(new ItemStack(MRContent.replicatorCasing));
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Nullable
    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return state.getValue(PART) == Part.LOWER ? new TileReplicator() : new TileReplicatorUpper();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, PART);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState()
                .withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3))
                .withProperty(PART, (meta & 4) != 0 ? Part.UPPER : Part.LOWER);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() | (state.getValue(PART) == Part.UPPER ? 4 : 0);
    }
}
