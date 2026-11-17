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

/**
 * Уведомления о беде клонов (6д-1): сервер решает всё сам и шлёт игроку готовые карточки, клиент только показывает.
 * Одна карточка — один вид беды: сколько клонов, причина и место ближайшего.
 */
public class CloneAlertMessage implements IMessage {

    /** Больше карточек в одном пакете не бывает (видов беды четыре); защита от испорченного пакета. */
    private static final int MAX_CARDS = 8;

    public static final class Card {
        /** Вид беды: CloneWatch.HEALTH, ROLLBACK, DEAD или LINK_LOST. */
        public final int kind;
        /** Для HEALTH: CloneWatch.NO_UU и/или NO_EU (у нескольких клонов причины складываются). */
        public final int reason;
        /** Сколько клонов в этой беде (1 — один). */
        public final int count;
        /** Измерение ближайшего клона: имя из игры (overworld, the_nether, …), клиент переводит знакомые. */
        public final String dim;
        public final int x;
        public final int y;
        public final int z;

        public Card(int kind, int reason, int count, String dim, int x, int y, int z) {
            this.kind = kind;
            this.reason = reason;
            this.count = count;
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private List<Card> cards = new ArrayList<>();

    public CloneAlertMessage() {
    }

    public CloneAlertMessage(List<Card> cards) {
        this.cards = cards;
    }

    public List<Card> getCards() {
        return Collections.unmodifiableList(cards);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int n = Math.min(cards.size(), MAX_CARDS);
        buf.writeByte(n);
        for (int i = 0; i < n; i++) {
            Card c = cards.get(i);
            buf.writeByte(c.kind);
            buf.writeByte(c.reason);
            buf.writeShort(Math.min(c.count, Short.MAX_VALUE));
            ByteBufUtils.writeUTF8String(buf, c.dim);
            buf.writeInt(c.x);
            buf.writeInt(c.y);
            buf.writeInt(c.z);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int n = Math.min(buf.readUnsignedByte(), MAX_CARDS);
        cards = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int kind = buf.readUnsignedByte();
            int reason = buf.readUnsignedByte();
            int count = buf.readShort();
            String dim = ByteBufUtils.readUTF8String(buf);
            cards.add(new Card(kind, reason, count, dim, buf.readInt(), buf.readInt(), buf.readInt()));
        }
    }

    /** Клиент: карточки уходят в очередь показа (в основном потоке игры, см. ProxyClient). */
    public static class Handler implements IMessageHandler<CloneAlertMessage, IMessage> {
        @Override
        public IMessage onMessage(CloneAlertMessage message, MessageContext ctx) {
            MatterRebirth.proxy.onCloneAlert(message.getCards());
            return null;
        }
    }
}
