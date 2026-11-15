package dlx.matterrebirth.item;

import dlx.matterrebirth.init.MRContent;
import ic2.api.item.IC2Items;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Нейросвязь (Антон 09.11): головной убор, через который сознание переходит в клон, — нейросинхронизатор (сам или в
 * кожаном шлеме) или квантовый нейрошлем. Квантовый нейрошлем — это сам квантовый шлем IC2 с отметкой TAG в NBT
 * (рецепт RecipeNeuroHelmet), отдельного предмета нет; обычный квантовый шлем с клонами не работает.
 */
public final class NeuroLink {

    /** Отметка «Нейросвязь» на квантовом шлеме IC2. */
    public static final String TAG = "MatterRebirthNeuroLink";

    @Nullable
    private static Item quantumHelmet;
    private static boolean looked;

    private NeuroLink() {
    }

    /** Квантовый шлем IC2 или null, если IC2 его не отдала. */
    @Nullable
    public static Item quantumHelmet() {
        if (!looked) {
            ItemStack stack = IC2Items.getItem("quantum_helmet");
            quantumHelmet = stack == null || stack.isEmpty() ? null : stack.getItem();
            looked = quantumHelmet != null; // пока IC2 не готова, спросить ещё раз позже
        }
        return quantumHelmet;
    }

    public static boolean isQuantumHelmet(ItemStack stack) {
        Item q = quantumHelmet();
        return q != null && !stack.isEmpty() && stack.getItem() == q;
    }

    /** Квантовый шлем IC2 с отметкой «Нейросвязь». */
    public static boolean isNeuroHelmet(ItemStack stack) {
        if (!isQuantumHelmet(stack)) {
            return false;
        }
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null && tag.getBoolean(TAG);
    }

    /** Ставит отметку «Нейросвязь» (заряд и остальное NBT шлема не трогаются). */
    public static void mark(ItemStack stack) {
        stack.setTagInfo(TAG, new NBTTagByte((byte) 1));
    }

    /**
     * Готовые квантовые нейрошлемы для нашей вкладки креатива: те же варианты, что IC2 кладёт в свою вкладку (пустой
     * и заряженный), с отметкой. Если IC2 вариантов не дала — один пустой шлем; без шлема IC2 — ничего.
     */
    @SideOnly(Side.CLIENT)
    public static List<ItemStack> creativeStacks() {
        List<ItemStack> out = new ArrayList<>();
        Item q = quantumHelmet();
        if (q == null) {
            return out;
        }
        NonNullList<ItemStack> variants = NonNullList.create();
        CreativeTabs tab = q.getCreativeTab();
        if (tab != null) {
            q.getSubItems(tab, variants);
        }
        if (variants.isEmpty()) {
            variants.add(new ItemStack(q));
        }
        for (ItemStack s : variants) {
            if (isQuantumHelmet(s)) {
                ItemStack c = s.copy();
                mark(c);
                out.add(c);
            }
        }
        return out;
    }

    /** Переносит ли этот головной убор сознание в клон (для этапа 6). */
    public static boolean linksMind(ItemStack head) {
        return !head.isEmpty() && (head.getItem() == MRContent.neuroSynchronizer
                || head.getItem() == MRContent.neuroSynchronizerLeather || isNeuroHelmet(head));
    }
}
