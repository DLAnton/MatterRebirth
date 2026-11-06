package dlx.matterrebirth.tile;

import dlx.matterrebirth.energy.ReplicatorSink;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

/**
 * Верхняя часть репликатора. Своего запаса нет: принимает энергию через свои порты (сзади и сверху)
 * и сразу передаёт её нижней части.
 */
public class TileReplicatorUpper extends TileEntity implements ITickable {

    private final ReplicatorSink sink = new ReplicatorSink(this, 0, true);

    @Override
    public void update() {
        sink.update(); // в сеть IC2 встаём на первом тике, когда соседи уже загружены
    }

    @Override
    public void invalidate() {
        sink.invalidate();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        sink.onChunkUnload();
    }
}
