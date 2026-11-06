package dlx.matterrebirth.chunk;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.block.BlockReplicator;
import dlx.matterrebirth.init.MRContent;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Собранный репликатор сам держит свой чанк загруженным: у каждой машины свой билет Forge (тип NORMAL),
 * в билете записано место нижней части. Обе части стоят в одном чанке, поэтому чанк один.
 * После перезахода Forge возвращает билеты в ticketsLoaded: живым машинам чанк снова загружается, остальные билеты отпускаются.
 * Билет отпускается, когда машину ломают (BlockReplicator.breakBlock); при выгрузке мира билеты не трогаем, их хранит Forge.
 */
@Mod.EventBusSubscriber(modid = MatterRebirth.MOD_ID)
public final class ReplicatorChunks implements ForgeChunkManager.LoadingCallback {

    /** Билеты машин по измерениям; заполняется при загрузке мира и при сборке машины. */
    private static final Map<Integer, Map<BlockPos, Ticket>> TICKETS = new HashMap<>();

    private ReplicatorChunks() {
    }

    public static void init() {
        ForgeChunkManager.setForcedChunkLoadingCallback(MatterRebirth.instance, new ReplicatorChunks());
    }

    @Override
    public void ticketsLoaded(List<Ticket> tickets, World world) {
        for (Ticket ticket : tickets) {
            NBTTagCompound data = ticket.getModData();
            BlockPos pos = new BlockPos(data.getInteger("x"), data.getInteger("y"), data.getInteger("z"));
            Map<BlockPos, Ticket> map = map(world);
            if (data.hasKey("x") && isLowerPart(world, pos) && !map.containsKey(pos)) {
                ForgeChunkManager.forceChunk(ticket, new ChunkPos(pos));
                map.put(pos, ticket);
            } else {
                ForgeChunkManager.releaseTicket(ticket); // машины уже нет (или второй билет на то же место)
            }
        }
    }

    /** Машина работает: если у неё ещё нет билета, взять. Билетов у Forge может не хватить, тогда сообщаем владельцу. */
    public static void ensure(World world, BlockPos pos, @Nullable UUID owner) {
        if (world.isRemote) {
            return;
        }
        Map<BlockPos, Ticket> map = map(world);
        if (map.containsKey(pos)) {
            return;
        }
        Ticket ticket = ForgeChunkManager.requestTicket(MatterRebirth.instance, world, ForgeChunkManager.Type.NORMAL);
        if (ticket == null) {
            MatterRebirth.LOG.warn("No chunk loading ticket left for the clone replicator at {} in dimension {} (see forgeChunkLoading.cfg)",
                    pos, world.provider.getDimension());
            tell(world, owner, new TextComponentTranslation("message.matterrebirth.no_chunk_ticket", pos.getX(), pos.getY(), pos.getZ()));
            return;
        }
        ticket.getModData().setInteger("x", pos.getX());
        ticket.getModData().setInteger("y", pos.getY());
        ticket.getModData().setInteger("z", pos.getZ());
        ForgeChunkManager.forceChunk(ticket, new ChunkPos(pos));
        map.put(pos.toImmutable(), ticket);
    }

    /** Машину разобрали: отпустить билет. Повторный вызов ничего не делает. */
    public static void release(World world, BlockPos pos) {
        if (world.isRemote) {
            return;
        }
        Ticket ticket = map(world).remove(pos);
        if (ticket != null) {
            ForgeChunkManager.releaseTicket(ticket);
        }
    }

    private static boolean isLowerPart(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        return state.getBlock() == MRContent.replicator && state.getValue(BlockReplicator.PART) == BlockReplicator.Part.LOWER;
    }

    private static Map<BlockPos, Ticket> map(World world) {
        return TICKETS.computeIfAbsent(world.provider.getDimension(), d -> new HashMap<>());
    }

    static void tell(World world, @Nullable UUID player, TextComponentTranslation message) {
        MinecraftServer server = world.getMinecraftServer();
        if (player == null || server == null) {
            return;
        }
        EntityPlayerMP online = server.getPlayerList().getPlayerByUUID(player);
        if (online != null) {
            online.sendMessage(message);
        }
    }

    /** Мир выгружается: объекты билетов больше недействительны, при следующей загрузке Forge вернёт их заново. */
    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) {
            TICKETS.remove(event.getWorld().provider.getDimension());
        }
    }
}
