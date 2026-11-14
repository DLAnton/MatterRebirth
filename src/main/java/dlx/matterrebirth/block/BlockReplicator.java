package dlx.matterrebirth.block;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.chunk.ReplicatorChunks;
import dlx.matterrebirth.chunk.ReplicatorRegistry;
import dlx.matterrebirth.init.MRConfig;
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
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.util.FakePlayer;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Репликатор клонов: два конструкционных блока, поставленные друг на друга, сами превращаются в эту машину.
 * Нижняя часть главная (в ней TileReplicator: владелец, позже клон, заряд и слоты), верхняя только передаёт подключения.
 * Если сломать любую часть, вторая часть снова становится конструкционным блоком; выпадает конструкционный блок, если ломали
 * ключом, иначе улучшенный корпус машины IC2, как у репликатора IC2 (getDrops).
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

    /*
     * Облик машины (этап 5а, решения Антона 08.11). Свойства LIGHT и UU не хранятся в блоке: их даёт машина
     * (getActualState), значит смена вида не трогает мир, только перерисовку. Стекло всегда светлое; пока в баке есть UU,
     * камера заполнена почти прозрачной жидкой материей IC2 — частью модели в полупрозрачном слое (ReplicatorBakedModel).
     */

    /** Цвет колец пола и излучателя: как цвет строки состояния окна. */
    public static final PropertyEnum<Light> LIGHT = PropertyEnum.create("light", Light.class);
    /** В баке есть UU: камера за стеклом заполнена UU. */
    public static final PropertyBool UU = PropertyBool.create("uu");
    /** Слабый свет машины, пока кольца горят (в 1.12 свет блока бесцветный: цвет дают кольца и ореол). */
    public static final int LIGHT_LEVEL = 5;

    /**
     * Цвета колец: OFF — погасли (нет энергии), IDLE — белые (пусто без шаблона, пауза), GROW — бирюзовые (рост),
     * WAIT — жёлтые (нужно UU, диапауза, мало EU), ALERT — красные (перегрузка, клон теряет здоровье, погиб),
     * OK — зелёные (клон готов и здоров), ROLLBACK — перелив красный ↔ фиолетовый
     * (откат и распад погибшего клона, Антон 14.11; текстуры колец — client/ShimmerSprite).
     * Номер значения передаётся клиенту: новые только в конец.
     */
    public enum Light implements IStringSerializable {
        OFF, IDLE, GROW, WAIT, ALERT, OK, ROLLBACK;

        @Override
        public String getName() {
            return name().toLowerCase(Locale.ROOT);
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
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(PART, Part.LOWER)
                .withProperty(LIGHT, Light.OFF).withProperty(UU, false));
    }

    /**
     * Пробует собрать машину после установки конструкционного блока: второй блок встал на первый
     * (или под него). Машина смотрит лицом на игрока, поставившего второй блок; он же владелец.
     * Если в настройках задан лимит машин на игрока, игрок на лимите машину не соберёт (блоки остаются конструкционными),
     * а без игрока (блок поставила автоматика) машина при включённом лимите не собирается вовсе, иначе лимит легко обойти.
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
        int limit = MRConfig.GENERAL.maxReplicatorsPerPlayer;
        ReplicatorRegistry registry = ReplicatorRegistry.get(world);
        if (limit > 0) {
            if (!(placer instanceof EntityPlayer) || placer instanceof FakePlayer) {
                return; // автоматика (в том числе «поддельные игроки» модов) при включённом лимите не собирает
            }
            if (registry.count(placer.getUniqueID()) >= limit) {
                ((EntityPlayer) placer).sendStatusMessage(new TextComponentTranslation("message.matterrebirth.replicator_limit", limit), false);
                return;
            }
        }
        EnumFacing facing = placer != null ? placer.getHorizontalFacing().getOpposite() : EnumFacing.NORTH;
        IBlockState base = MRContent.replicator.getDefaultState().withProperty(FACING, facing);
        world.setBlockState(lower, base.withProperty(PART, Part.LOWER), 3);
        world.setBlockState(lower.up(), base.withProperty(PART, Part.UPPER), 3);
        TileEntity te = world.getTileEntity(lower);
        if (te instanceof TileReplicator && placer instanceof EntityPlayer) {
            ((TileReplicator) te).setOwner((EntityPlayer) placer);
            registry.add(placer.getUniqueID(), world.provider.getDimension(), lower);
        }
        world.playSound(null, lower.up(), SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 0.6F, 1.4F);
    }

    /** Нижняя (главная) часть машины для любой из двух частей. */
    public static BlockPos lowerPos(IBlockState state, BlockPos pos) {
        return state.getValue(PART) == Part.LOWER ? pos : pos.down();
    }

    /**
     * Порт ли эта грань части машины: задняя грань каждой части, нижняя у нижней и верхняя у верхней.
     * Через порты идут энергия, UU и автоматика.
     */
    public static boolean isPort(IBlockState state, EnumFacing side) {
        if (side == state.getValue(FACING).getOpposite()) {
            return true;
        }
        return side == (state.getValue(PART) == Part.LOWER ? EnumFacing.DOWN : EnumFacing.UP);
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        // Содержимое слотов выпадает, как у машин IC2 (до того, как нижняя часть снова станет блоком и потеряет слоты)
        BlockPos lower = lowerPos(state, pos);
        TileEntity te = world.getTileEntity(lower);
        if (te instanceof TileReplicator) {
            ((TileReplicator) te).dropContents(world, pos);
        }
        // машины больше нет: чанк отпускаем, место в лимите владельца освобождаем (при сломе обеих частей вызов повторный, это безопасно)
        if (!world.isRemote) {
            ReplicatorChunks.release(world, lower);
            ReplicatorRegistry.get(world).remove(world.provider.getDimension(), lower);
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

    /**
     * Слом части как у машин IC2 (Антон 08.11): ключом — конструкционный блок, киркой или взрывом — только улучшенный
     * корпус машины IC2 (BlockReplicatorCasing.dropFor). Вторая часть в любом случае снова становится конструкционным блоком.
     */
    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        drops.add(BlockReplicatorCasing.dropFor(harvesters.get(), state));
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

    /** Сигнал компаратора с любой из двух частей: по состоянию клона (TileReplicator.comparatorSignal, Антон 08.11). */
    @Override
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    @Override
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(lowerPos(state, pos));
        return te instanceof TileReplicator ? ((TileReplicator) te).comparatorSignal() : 0;
    }

    /** События блока (тревога клона) передаются машине: на клиенте она играет звук. */
    @Override
    public boolean eventReceived(IBlockState state, World world, BlockPos pos, int id, int param) {
        TileEntity te = world.getTileEntity(pos);
        return te != null && te.receiveClientEvent(id, param);
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
        return new BlockStateContainer(this, FACING, PART, LIGHT, UU);
    }

    // --- облик (этап 5а) ---

    /**
     * Главная часть машины без создания TileEntity: в потоках отрисовки чанков (ChunkCache) создавать её нельзя,
     * поэтому там она только ищется. Нет её (машина ещё собирается) — null.
     */
    @Nullable
    private static TileReplicator lowerTile(IBlockState state, IBlockAccess world, BlockPos pos) {
        BlockPos lower = lowerPos(state, pos);
        TileEntity te = world instanceof ChunkCache
                ? ((ChunkCache) world).getTileEntity(lower, Chunk.EnumCreateEntityType.CHECK)
                : world.getTileEntity(lower);
        return te instanceof TileReplicator ? (TileReplicator) te : null;
    }

    /** Цвет колец и UU в камере берутся у машины (у обеих частей — у нижней). */
    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileReplicator te = lowerTile(state, world, pos);
        if (te == null) {
            return state;
        }
        return state.withProperty(LIGHT, te.getLookLight()).withProperty(UU, te.getLookUu());
    }

    /** Обе части слабо светятся, пока кольца горят. */
    @Override
    public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (state.getBlock() != this) {
            return super.getLightValue(state, world, pos);
        }
        TileReplicator te = lowerTile(state, world, pos);
        return te != null && te.getLookLight() != Light.OFF ? LIGHT_LEVEL : 0;
    }

    /** Машина полая: соседние грани не прячутся, свет проходит внутрь камеры. */
    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    /**
     * Корпус рисуется в непрозрачном слое, стекло и UU в камере — в полупрозрачном (как ванильное цветное стекло и вода:
     * так их понимают и шейдеры). Какие грани в каком слое, решает модель ReplicatorBakedModel (по текстуре).
     */
    @Override
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.SOLID || layer == BlockRenderLayer.TRANSLUCENT;
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
