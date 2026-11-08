package dlx.matterrebirth.client;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.properties.Property;
import dlx.matterrebirth.MatterRebirth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Скин для кожи клона (этап 5б): скин того, чья кровь в шаблоне.
 * 1) Донор в сети и сервер знает его скин (или донор — сам этот игрок) — скин из списка игроков, как у самого игрока.
 * 2) Скин, запомненный образцом при заборе крови (Антон 08.11: интернета может не быть, например в локальной сети):
 *    запрос к Mojang не нужен, картинка берётся из кэша скинов игры на диске (если её там нет — скачивается).
 *    Свой скин игрок получает из своего сеанса — тоже без запросов.
 * 3) Старый образец без скина — один раз за сеанс игры запрос профиля к Mojang по UUID в отдельном потоке.
 * Пока скин грузится или если его нет (нет интернета и кэша, сервер без проверки входа) — стандартный Стив или Алекс
 * по UUID, как Minecraft выбирает его сам (так же игра покажет и самого донора).
 */
public final class CloneSkins {

    public static final class Skin {
        public final ResourceLocation texture;
        public final boolean slim;

        Skin(ResourceLocation texture, boolean slim) {
            this.texture = texture;
            this.slim = slim;
        }
    }

    private static final class Loaded {
        volatile Skin skin;
    }

    private static final Map<String, Loaded> CACHE = new HashMap<>();
    private static final Skin STEVE = new Skin(DefaultPlayerSkin.getDefaultSkinLegacy(), false);

    private CloneSkins() {
    }

    /** skinTextures — значение свойства «textures» из образца (ItemBloodSample.getSkin) или null. */
    public static Skin get(@Nullable UUID id, @Nullable String name, @Nullable String skinTextures) {
        if (id == null) {
            return STEVE;
        }
        Minecraft mc = Minecraft.getMinecraft();
        NetHandlerPlayClient conn = mc.getConnection();
        NetworkPlayerInfo info = conn != null ? conn.getPlayerInfo(id) : null;
        boolean self = id.equals(mc.getSession().getProfile().getId());
        if (info != null && (self || info.getGameProfile().getProperties().containsKey("textures"))) {
            return new Skin(info.getLocationSkin(), "slim".equals(info.getSkinType()));
        }
        String textures = self ? null : skinTextures;
        String key = id + "|" + textures; // новый образец со скином не ждёт неудачной загрузки по старому
        Loaded loaded = CACHE.get(key);
        if (loaded == null) {
            loaded = new Loaded();
            CACHE.put(key, loaded);
            request(mc, id, name, textures, loaded);
        }
        Skin skin = loaded.skin;
        return skin != null ? skin : new Skin(DefaultPlayerSkin.getDefaultSkin(id), "slim".equals(DefaultPlayerSkin.getSkinType(id)));
    }

    private static void request(Minecraft mc, UUID id, @Nullable String name, @Nullable String skinTextures, Loaded loaded) {
        if (skinTextures != null && !skinTextures.isEmpty()) {
            GameProfile profile = new GameProfile(id, name);
            profile.getProperties().put("textures", new Property("textures", skinTextures));
            load(mc, profile, loaded);
            return;
        }
        Thread thread = new Thread(() -> {
            GameProfile profile = new GameProfile(id, name);
            try {
                // свой профиль — из сеанса (это делает сама загрузка скина), чужой — с сервера профилей
                if (!id.equals(mc.getSession().getProfile().getId())) {
                    profile = mc.getSessionService().fillProfileProperties(profile, false);
                }
            } catch (Exception e) {
                MatterRebirth.LOG.debug("Clone skin: profile of {} not loaded", id, e);
                return;
            }
            GameProfile filled = profile;
            mc.addScheduledTask(() -> load(mc, filled, loaded));
        }, "Matter Rebirth clone skin");
        thread.setDaemon(true);
        thread.start();
    }

    /** Картинку скина игра берёт из своего кэша на диске или скачивает (в своём потоке), ответ — в главном потоке. */
    private static void load(Minecraft mc, GameProfile profile, Loaded loaded) {
        mc.getSkinManager().loadProfileTextures(profile, (type, location, texture) -> {
            if (type == MinecraftProfileTexture.Type.SKIN) {
                loaded.skin = new Skin(location, "slim".equals(texture.getMetadata("model")));
            }
        }, false);
    }
}
