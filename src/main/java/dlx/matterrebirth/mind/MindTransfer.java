package dlx.matterrebirth.mind;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.block.BlockReplicator;
import dlx.matterrebirth.init.MRContent;
import dlx.matterrebirth.item.NeuroLink;
import dlx.matterrebirth.tile.TileReplicator;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Перенос сознания (этап 6а, Антон 15.11). Игрок с синхронизатором или квантовым нейрошлемом на голове не умирает,
 * а переходит в готовый клон из своей крови. Тотем бессмертия ванильный и срабатывает раньше: до LivingDeathEvent
 * дело тогда не доходит.
 * <p>
 * Выбор клона: сначала своё измерение — ближайший к месту смерти; иначе машины с работающим якорем в других
 * измерениях: Верхний мир первым (координаты пересчитываются, как у порталов: Незер 1:8), потом остальные измерения
 * в случайном порядке, в каждом — ближайший. Игрок выходит перед стеклом спиной к машине; занято — сбоку, сзади,
 * в крайнем случае на крыше. Машина без свободного места пропускается.
 * <p>
 * Вещи, опыт, сытость, эффекты и Хардкор — шаги 6б и 6в; пока вещи остаются у игрока.
 */
@Mod.EventBusSubscriber(modid = MatterRebirth.MOD_ID)
public final class MindTransfer {

    private MindTransfer() {
    }

    /** LOW: моды, которые сами спасают от смерти и отменяют событие, идут раньше; отменённое мы не получаем. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntityLiving() instanceof EntityPlayerMP) || event.getEntityLiving() instanceof FakePlayer) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
        if (!NeuroLink.linksMind(player.getItemStackFromSlot(EntityEquipmentSlot.HEAD))) {
            return;
        }
        for (TileReplicator machine : candidates(player)) {
            World world = machine.getWorld();
            BlockPos pos = machine.getPos();
            Spot spot = findSpot(world, pos);
            if (spot == null) {
                MatterRebirth.LOG.info("Clone of {} at {} {} {} (dimension {}) skipped: no free place around the replicator",
                        player.getName(), pos.getX(), pos.getY(), pos.getZ(), world.provider.getDimension());
                continue;
            }
            if (!moveTo(player, (WorldServer) world, spot, machine.cloneHealth())) {
                MatterRebirth.LOG.info("Mind transfer of {} refused: another mod cancelled the move to dimension {}",
                        player.getName(), world.provider.getDimension());
                return; // перемещение запретил другой мод: смерть идёт как обычно, клон остаётся в машине
            }
            machine.takeClone(player.getName());
            event.setCanceled(true);
            String dim = world.provider.getDimensionType().getName();
            player.sendMessage(new TextComponentTranslation("message.matterrebirth.mind_transferred",
                    dim, pos.getX(), pos.getY(), pos.getZ()));
            MatterRebirth.LOG.info("Mind of {} transferred to the clone at {} {} {} ({})",
                    player.getName(), pos.getX(), pos.getY(), pos.getZ(), dim);
            return;
        }
    }

    /** Подходящие машины по порядку выбора (см. описание класса). */
    private static List<TileReplicator> candidates(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        int home = player.dimension;
        List<TileReplicator> same = new ArrayList<>();
        Map<Integer, List<TileReplicator>> other = new TreeMap<>();
        // машины всегда держат свой чанк (ReplicatorChunks), поэтому все они в загруженных тайлах своего мира
        for (WorldServer world : DimensionManager.getWorlds()) {
            int dim = world.provider.getDimension();
            for (TileEntity te : world.loadedTileEntityList) {
                if (!(te instanceof TileReplicator) || te.isInvalid()) {
                    continue;
                }
                TileReplicator machine = (TileReplicator) te;
                if (!machine.hasReadyCloneOf(id)) {
                    continue;
                }
                if (dim == home) {
                    same.add(machine);
                } else if (machine.isAnchorWorking()) {
                    other.computeIfAbsent(dim, k -> new ArrayList<>()).add(machine);
                }
            }
        }
        List<TileReplicator> out = new ArrayList<>();
        sortByDistance(same, player, 1);
        out.addAll(same);
        List<Integer> order = new ArrayList<>(other.keySet());
        order.remove(Integer.valueOf(0));
        Collections.shuffle(order, player.getRNG());
        if (other.containsKey(0)) {
            order.add(0, 0); // Верхний мир первым
        }
        double fromFactor = player.world.provider.getMovementFactor();
        for (int dim : order) {
            List<TileReplicator> list = other.get(dim);
            sortByDistance(list, player, fromFactor / list.get(0).getWorld().provider.getMovementFactor());
            out.addAll(list);
        }
        return out;
    }

    /** По расстоянию до места смерти; по горизонтали координаты умножаются на factor, как у порталов. */
    private static void sortByDistance(List<TileReplicator> list, EntityPlayerMP player, double factor) {
        double x = player.posX * factor;
        double y = player.posY;
        double z = player.posZ * factor;
        list.sort(Comparator.comparingDouble(m -> m.getPos().distanceSqToCenter(x, y, z)));
    }

    /** Место выхода у машины или null, если вокруг всё занято. */
    @Nullable
    private static Spot findSpot(World world, BlockPos lower) {
        IBlockState state = world.getBlockState(lower);
        if (state.getBlock() != MRContent.replicator) {
            return null;
        }
        EnumFacing front = state.getValue(BlockReplicator.FACING);
        for (EnumFacing side : new EnumFacing[]{front, front.rotateY(), front.rotateYCCW(), front.getOpposite()}) {
            BlockPos feet = lower.offset(side);
            if (free(world, feet)) {
                return new Spot(feet, side.getHorizontalAngle()); // спиной к машине
            }
        }
        BlockPos roof = lower.up(2);
        return free(world, roof) ? new Spot(roof, front.getHorizontalAngle()) : null;
    }

    /** Игрок помещается: два блока без столкновения, без лавы и огня, в пределах высоты мира. */
    private static boolean free(World world, BlockPos feet) {
        return feet.getY() >= 0 && feet.getY() + 1 < world.getHeight() && passable(world, feet) && passable(world, feet.up());
    }

    private static boolean passable(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        Material material = state.getMaterial();
        return state.getCollisionBoundingBox(world, pos) == null && material != Material.LAVA && material != Material.FIRE;
    }

    /**
     * Переместить игрока к машине со здоровьем клона. false — другой мод запретил переход в другое измерение;
     * тогда здоровье возвращается как было.
     */
    private static boolean moveTo(EntityPlayerMP player, WorldServer to, Spot spot, float health) {
        float before = player.getHealth();
        player.setHealth(health); // до перехода: в новом мире игрок должен быть живым
        if (player.isPlayerSleeping()) {
            player.wakeUpPlayer(true, false, false);
        }
        player.dismountRidingEntity();
        player.removePassengers();
        int dim = to.provider.getDimension();
        if (player.dimension != dim) {
            player.changeDimension(dim, spot);
            if (player.dimension != dim) {
                player.setHealth(before);
                return false;
            }
        }
        player.connection.setPlayerLocation(spot.x, spot.y, spot.z, spot.yaw, 0);
        player.motionX = 0;
        player.motionY = 0;
        player.motionZ = 0;
        player.fallDistance = 0;
        player.extinguish();
        player.setAir(300);
        return true;
    }

    /** Место выхода; он же телепортер для перехода между измерениями. */
    private static final class Spot implements ITeleporter {
        final double x;
        final double y;
        final double z;
        final float yaw;

        Spot(BlockPos feet, float yaw) {
            this.x = feet.getX() + 0.5;
            this.y = feet.getY();
            this.z = feet.getZ() + 0.5;
            this.yaw = yaw;
        }

        @Override
        public void placeEntity(World world, Entity entity, float entityYaw) {
            entity.setLocationAndAngles(x, y, z, yaw, 0);
            entity.motionX = 0;
            entity.motionY = 0;
            entity.motionZ = 0;
        }
    }
}
