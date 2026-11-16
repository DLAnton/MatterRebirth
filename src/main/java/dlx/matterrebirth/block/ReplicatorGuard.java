package dlx.matterrebirth.block;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.init.MRContent;
import dlx.matterrebirth.tile.TileReplicator;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import javax.annotation.Nullable;

/**
 * Защита машины с клоном (этап 6г, Антон 09.11: «как личный сейф IC2»). Пока в камере что-то есть
 * (TileReplicator.isPrivate), окно, кнопки, слом и снятие ключом — только донору крови шаблона и администраторам
 * сервера; чужому игроку над панелью быстрого доступа пишется «Машина клона игрока X», смотреть можно по экранчику на лицевой стороне.
 * Взрывы, питание, подача UU и трубы не защищаются (внешняя беда, как у любой машины). Сборщик машины важен только
 * для лимита машин.
 * <p>
 * Слом закрыт в трёх местах: событие слома (его проверяют и многие инструменты модов), removedByPlayer на обеих
 * сторонах (творческий режим и инструменты без события; клиент не убирает блок сам у себя) и нулевая скорость
 * копания в выживании.
 */
@Mod.EventBusSubscriber(modid = MatterRebirth.MOD_ID)
public final class ReplicatorGuard {

    private ReplicatorGuard() {
    }

    /**
     * Машина этой части (у обеих частей — нижняя), если игроку ею распоряжаться нельзя; иначе null. Без игрока
     * (моды иногда так зовут ключ) машина с клоном считается чужой.
     */
    @Nullable
    static TileReplicator lockedFor(World world, BlockPos pos, IBlockState state, @Nullable EntityPlayer player) {
        if (state.getBlock() != MRContent.replicator) {
            return null;
        }
        TileEntity te = world.getTileEntity(BlockReplicator.lowerPos(state, pos));
        if (!(te instanceof TileReplicator)) {
            return null;
        }
        TileReplicator tile = (TileReplicator) te;
        boolean allowed = player != null ? tile.mayControl(player) : !tile.isPrivate();
        return allowed ? null : tile;
    }

    /** Сообщение чужому игроку (только на сервере; «поддельные игроки» сообщений не получают). */
    static void tell(@Nullable EntityPlayer player, TileReplicator tile) {
        if (player != null && !player.world.isRemote) {
            String name = tile.getPatternName();
            player.sendStatusMessage(new TextComponentTranslation("message.matterrebirth.replicator_private",
                    name != null ? name : "?"), true); // над панелью: при зажатой кнопке не засыпает чат (Антон 16.11)
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        EntityPlayer player = event.getPlayer();
        if (player == null || event.getWorld().isRemote) {
            return;
        }
        TileReplicator tile = lockedFor(event.getWorld(), event.getPos(), event.getState(), player);
        if (tile != null) {
            event.setCanceled(true); // Forge сам вернёт клиенту блок и данные машины
            tell(player, tile);
        }
    }
}
