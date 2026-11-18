package dlx.matterrebirth.network;

import dlx.matterrebirth.MatterRebirth;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Клоны игрока для панели «Нейросвязь» (6д-2): какие клоны видны и что с ними, решает сервер (CloneWatch), клиент
 * только раскладывает и рисует. Пустой список — клонов нет или нейрошлем снят.
 */
public class CloneListMessage implements IMessage {

    /** Больше клонов в пакете не бывает; защита от испорченного пакета. */
    public static final int MAX_CLONES = 64;

    /** Состояние «нет связи»: якорь машины в другом измерении без энергии, о клоне ничего не известно. */
    public static final int NO_LINK = -1;

    public static final class Entry {
        /** Номер TileReplicator.Status (как видит игрок у машины) или NO_LINK. */
        public final int status;
        /** Клон погиб (распад и после него). */
        public final boolean dead;
        /** Выращено стадий (0..5). */
        public final int stage;
        /** Прогресс текущей стадии, %. */
        public final int stagePercent;
        /** Общий рост за все стадии, ‰ (для кольца вокруг ядра). */
        public final int growth;
        /** Здоровье готового клона, единиц (0..20, как у игрока). */
        public final int health;
        /** В машине стоит якорь. */
        public final boolean anchor;
        /** Номер измерения машины и его имя из игры (overworld, the_nether, …). */
        public final int dim;
        public final String dimName;
        public final int x;
        public final int y;
        public final int z;

        public Entry(int status, boolean dead, int stage, int stagePercent, int growth, int health, boolean anchor,
                     int dim, String dimName, int x, int y, int z) {
            this.status = status;
            this.dead = dead;
            this.stage = stage;
            this.stagePercent = stagePercent;
            this.growth = growth;
            this.health = health;
            this.anchor = anchor;
            this.dim = dim;
            this.dimName = dimName;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Entry)) {
                return false;
            }
            Entry e = (Entry) o;
            return status == e.status && dead == e.dead && stage == e.stage && stagePercent == e.stagePercent
                    && growth == e.growth && health == e.health && anchor == e.anchor && dim == e.dim && x == e.x
                    && y == e.y && z == e.z && dimName.equals(e.dimName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(status, dead, stage, stagePercent, growth, health, anchor, dim, dimName, x, y, z);
        }
    }

    private List<Entry> clones = new ArrayList<>();

    public CloneListMessage() {
    }

    public CloneListMessage(List<Entry> clones) {
        this.clones = clones;
    }

    public List<Entry> getClones() {
        return Collections.unmodifiableList(clones);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int n = Math.min(clones.size(), MAX_CLONES);
        buf.writeByte(n);
        for (int i = 0; i < n; i++) {
            Entry e = clones.get(i);
            buf.writeByte(e.status);
            buf.writeBoolean(e.dead);
            buf.writeByte(e.stage);
            buf.writeByte(e.stagePercent);
            buf.writeShort(e.growth);
            buf.writeByte(e.health);
            buf.writeBoolean(e.anchor);
            buf.writeInt(e.dim);
            ByteBufUtils.writeUTF8String(buf, e.dimName);
            buf.writeInt(e.x);
            buf.writeInt(e.y);
            buf.writeInt(e.z);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int n = Math.min(buf.readUnsignedByte(), MAX_CLONES);
        clones = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int status = buf.readByte(); // со знаком: NO_LINK = -1
            boolean dead = buf.readBoolean();
            int stage = buf.readUnsignedByte();
            int stagePercent = buf.readUnsignedByte();
            int growth = buf.readShort();
            int health = buf.readUnsignedByte();
            boolean anchor = buf.readBoolean();
            int dim = buf.readInt();
            String dimName = ByteBufUtils.readUTF8String(buf);
            clones.add(new Entry(status, dead, stage, stagePercent, growth, health, anchor, dim, dimName,
                    buf.readInt(), buf.readInt(), buf.readInt()));
        }
    }

    /** Клиент: список уходит панели (в основном потоке игры, см. ProxyClient). */
    public static class Handler implements IMessageHandler<CloneListMessage, IMessage> {
        @Override
        public IMessage onMessage(CloneListMessage message, MessageContext ctx) {
            MatterRebirth.proxy.onCloneList(message.getClones());
            return null;
        }
    }
}
