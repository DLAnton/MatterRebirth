package dlx.matterrebirth.init;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.block.BlockReplicator;
import dlx.matterrebirth.block.BlockReplicatorCasing;
import dlx.matterrebirth.item.ItemBase;
import dlx.matterrebirth.item.ItemBloodSample;
import dlx.matterrebirth.item.ItemNeuroSynchronizer;
import dlx.matterrebirth.item.ItemVacuumSyringe;
import dlx.matterrebirth.tile.TileReplicator;
import dlx.matterrebirth.tile.TileReplicatorUpper;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Регистрация блоков и предметов.
 */
@Mod.EventBusSubscriber(modid = MatterRebirth.MOD_ID)
public final class MRContent {

    public static final List<Item> ITEMS = new ArrayList<>();

    public static final CreativeTabs TAB = new CreativeTabs(MatterRebirth.MOD_ID) {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(bloodSample);
        }
    };

    public static Block replicatorCasing;
    public static Block replicator;

    public static Item vacuumSyringe;
    public static Item bloodSample;
    public static Item neuroSynchronizer;
    public static Item neuroSynchronizerLeather;
    public static Item resonantMixture;
    public static Item resonantCrystal;
    public static Item dimensionalAnchor;

    private MRContent() {
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        replicatorCasing = new BlockReplicatorCasing("replicator_casing");
        replicator = new BlockReplicator("replicator");
        event.getRegistry().register(replicatorCasing);
        event.getRegistry().register(replicator);
        GameRegistry.registerTileEntity(TileReplicator.class, new ResourceLocation(MatterRebirth.MOD_ID, "replicator"));
        GameRegistry.registerTileEntity(TileReplicatorUpper.class, new ResourceLocation(MatterRebirth.MOD_ID, "replicator_upper"));
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        vacuumSyringe = add(new ItemVacuumSyringe("vacuum_syringe"));
        bloodSample = add(new ItemBloodSample("blood_sample"));
        neuroSynchronizer = add(new ItemNeuroSynchronizer("neuro_synchronizer", false));
        neuroSynchronizerLeather = add(new ItemNeuroSynchronizer("neuro_synchronizer_leather", true));
        resonantMixture = add(new ItemBase("resonant_mixture", 64));
        resonantCrystal = add(new ItemBase("resonant_crystal", 64));
        dimensionalAnchor = add(new ItemBase("dimensional_anchor", 1).setRarity(EnumRarity.RARE));
        Item casing = new ItemBlock(replicatorCasing).setRegistryName(replicatorCasing.getRegistryName());
        add(casing);
        for (Item item : ITEMS) {
            event.getRegistry().register(item);
        }
    }

    private static Item add(Item item) {
        ITEMS.add(item);
        return item;
    }
}
