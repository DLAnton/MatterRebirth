package dlx.matterrebirth.mind;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.init.MRConfig;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.SPacketSetExperience;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Вещи и опыт при переносе сознания (этап 6б, Антон 15.11). Всё, что было на игроке, выпадает на месте смерти,
 * как при обычной смерти: инвентарь, броня (и сам синхронизатор или шлем), вторая рука, предмет под курсором
 * и сетка крафта. Предметы с «Печатью души» из настройки остаются на своих местах, с «Проклятием утраты» пропадают
 * (проклятие сильнее печати, как в игре). Правило keepInventory — ничего не выпадает, опыт весь.
 * <p>
 * Опыт: с квантовым нейрошлемом остаётся весь, с синхронизатором половина. Из потерянного на месте смерти выпадают
 * шарики по правилу игры (7 очков за уровень, не больше 100), остальное пропадает.
 * <p>
 * Событие «вещи умершего» (PlayerDropsEvent) не вызывается: CoFH на нём забирает предметы с «Печатью души», чтобы
 * вернуть их новому телу при возрождении, а при переносе тело то же и возрождения нет — предметы пропали бы.
 */
final class DeathDrops {

    private DeathDrops() {
    }

    /** Где умер игрок и что у него было на голове: снимается до перемещения. */
    static final class Death {
        final WorldServer world;
        final double x;
        final double y;
        final double z;
        final boolean keepAll;
        final boolean fullXp;

        Death(EntityPlayerMP player, boolean fullXp) {
            this.world = player.getServerWorld();
            this.x = player.posX;
            this.y = player.posY + player.getEyeHeight() - 0.3; // как у выброшенных игроком вещей
            this.z = player.posZ;
            this.keepAll = world.getGameRules().getBoolean("keepInventory") || player.isSpectator(); // как в игре
            this.fullXp = fullXp;
        }
    }

    /** После удачного перемещения: вещи и шарики опыта — на место смерти, у игрока остаётся положенное. */
    static void apply(EntityPlayerMP player, Death death) {
        if (death.keepAll) {
            MatterRebirth.LOG.info("Mind transfer of {}: keepInventory is on, items and experience stay", player.getName());
            return;
        }
        List<Enchantment> soulbound = soulbound();
        List<ItemStack> drops = new ArrayList<>();
        int kept = 0;
        int vanished = 0;

        // предмет под курсором: забрать до закрытия окна, иначе окно бросит его уже у машины
        InventoryPlayer inv = player.inventory;
        ItemStack cursor = inv.getItemStack();
        if (!cursor.isEmpty()) {
            inv.setItemStack(ItemStack.EMPTY);
            if (EnchantmentHelper.hasVanishingCurse(cursor)) {
                vanished++;
            } else if (has(cursor, soulbound)) {
                inv.placeItemBackInInventory(player.world, cursor); // некуда — падает у игрока, он уже жив; в счёт ниже
            } else {
                drops.add(cursor);
            }
        }
        // игрок уже жив: окно возвращает сетку крафта (свою 2×2 и верстака) в инвентарь, дальше она выпадает со всем
        player.closeScreen();

        for (NonNullList<ItemStack> list : Arrays.asList(inv.mainInventory, inv.armorInventory, inv.offHandInventory)) {
            for (int i = 0; i < list.size(); i++) {
                ItemStack stack = list.get(i);
                if (stack.isEmpty()) {
                    continue;
                }
                if (EnchantmentHelper.hasVanishingCurse(stack)) {
                    list.set(i, ItemStack.EMPTY);
                    vanished++;
                } else if (has(stack, soulbound)) {
                    kept++;
                } else {
                    list.set(i, ItemStack.EMPTY);
                    drops.add(stack);
                }
            }
        }
        inv.markDirty();

        Random rand = player.getRNG();
        int lost = 0;
        for (ItemStack stack : drops) {
            if (!spawnItem(death, stack, rand)) {
                lost++;
            }
        }

        int level = player.experienceLevel;
        int total = points(level, player.experience);
        int keep = death.fullXp ? total : total / 2;
        int orbs = Math.min(total - keep, Math.min(Math.max(0, Math.min(level, 100)) * 7, 100)); // как в игре: 7 за уровень, ≤ 100
        int orbTotal = orbs;
        if (keep != total) {
            setPoints(player, keep);
        }
        while (orbs > 0) {
            int part = EntityXPOrb.getXPSplit(orbs);
            orbs -= part;
            death.world.spawnEntity(new EntityXPOrb(death.world, death.x, death.y, death.z, part));
        }

        MatterRebirth.LOG.info("Mind transfer of {}: {} stacks dropped at {} {} {} (dimension {}){}, {} kept (soulbound), "
                        + "{} vanished; experience {} of {} points kept, {} in orbs",
                player.getName(), drops.size(), (int) Math.floor(death.x), (int) Math.floor(death.y),
                (int) Math.floor(death.z), death.world.provider.getDimension(),
                lost > 0 ? " (" + lost + " refused by the world)" : "", kept, vanished, keep, total, orbTotal);
    }

    /** Как EntityPlayer.dropItem(stack, true, false): вещь разлетается вокруг места смерти. */
    private static boolean spawnItem(Death death, ItemStack stack, Random rand) {
        EntityItem item = new EntityItem(death.world, death.x, death.y, death.z, stack);
        item.setPickupDelay(40);
        float speed = rand.nextFloat() * 0.5F;
        float angle = rand.nextFloat() * (float) Math.PI * 2F;
        item.motionX = -MathHelper.sin(angle) * speed;
        item.motionZ = MathHelper.cos(angle) * speed;
        item.motionY = 0.2;
        return death.world.spawnEntity(item);
    }

    /** Зачарования «Печать души» из настройки; неизвестные id пропускаются (мода нет в сборке). */
    private static List<Enchantment> soulbound() {
        List<Enchantment> out = new ArrayList<>();
        for (String id : MRConfig.GENERAL.soulboundEnchantments) {
            if (id == null || id.trim().isEmpty()) {
                continue;
            }
            Enchantment e = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(id.trim()));
            if (e != null) {
                out.add(e);
            }
        }
        return out;
    }

    /** Зачарование на самом предмете (книга с ним не в счёт — как у CoFH и у «Проклятия утраты»). */
    private static boolean has(ItemStack stack, List<Enchantment> list) {
        for (Enchantment e : list) {
            if (EnchantmentHelper.getEnchantmentLevel(e, stack) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Очки опыта на полоске уровня level (как EntityPlayer.xpBarCap). */
    private static int barCap(int level) {
        return level >= 30 ? 112 + (level - 30) * 9 : level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
    }

    /** Все очки игрока по уровню и заполнению полоски (experienceTotal в игре расходится после чар). */
    private static int points(int level, float bar) {
        if (level > 100000) {
            return Integer.MAX_VALUE; // такие уровни бывают только от команды; дальше счёт переполнился бы
        }
        double l = Math.max(0, level);
        // сумма полосок всех прошлых уровней — формулой (уровень командой может быть огромным)
        double sum = l <= 16 ? l * l + 6 * l : l <= 31 ? 2.5 * l * l - 40.5 * l + 360 : 4.5 * l * l - 162.5 * l + 2220;
        sum += Math.round(MathHelper.clamp(bar, 0, 1) * (double) barCap(level));
        return (int) Math.min(Integer.MAX_VALUE, Math.round(sum));
    }

    /** Поставить игроку ровно points очков без начисления счёта и сообщить клиенту. */
    private static void setPoints(EntityPlayerMP player, int points) {
        int level = 0;
        int left = points;
        while (left >= barCap(level)) {
            left -= barCap(level);
            level++;
        }
        player.experienceLevel = level;
        player.experience = left / (float) barCap(level);
        player.experienceTotal = points;
        player.connection.sendPacket(new SPacketSetExperience(player.experience, player.experienceTotal, player.experienceLevel));
    }
}
