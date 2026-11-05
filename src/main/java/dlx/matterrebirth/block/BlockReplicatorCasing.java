package dlx.matterrebirth.block;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.init.MRContent;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;

/**
 * Конструкционный блок репликатора. Пока обычный блок корпуса; сборка двух блоков в репликатор
 * клонов (мультиблок) добавляется на этапе «Репликатор».
 */
public class BlockReplicatorCasing extends Block {

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
}
