package dlx.matterrebirth.client;

import dlx.matterrebirth.MatterRebirth;
import dlx.matterrebirth.item.ItemBloodSample;
import dlx.matterrebirth.tile.TileReplicator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.opengl.GL11;

import java.util.UUID;

/**
 * Клон в камере (этап 5б, лист v9 утверждён Антоном 08.11). Слои по стадиям: 1 скелет (ванильный, «свежий» —
 * FreshSkeletonTexture), 2 нервы и 3 сосуды (тонкие оболочки чуть больше костей, на сосудах нарисованы и нервы),
 * 4 мышцы, 5 кожа — скин того, чья кровь в шаблоне (CloneSkins). Растущий слой виден от ступней до доли роста стадии,
 * при откате тает сверху. Клон ~1,4 блока, лицом к стеклу, едва заметно покачивается («парит» в UU).
 * Погибший клон — мертвецки бледный (PaleSkins), обмякшая поза; около минуты распадается сверху вниз, срез переливается
 * красным и фиолетовым (5г, Антон 14.11), потом камера пуста.
 * Координаты клона — пиксели модели игрока: y от ступней вверх (0..32), перед — +z, x вправо от клона — к −x.
 * Обрезка роста — в самих коробках (не плоскостью отсечения: с шейдерами она ненадёжна).
 * На линии роста у срезанных коробок — светящаяся крышка (5б+, лист clone_cut_v1, вариант А утверждён Антоном 08.11):
 * поле с текстурой «сетка печати» и яркая кромка, цвет как у линии печати в окне, на полной яркости; откат и распад —
 * перелив красный ↔ фиолетовый (Shimmer: клон разрушается, UU возвращается в бак; Антон 14.11).
 */
final class CloneRenderer {

    private static final ResourceLocation NERVES = tex("clone_nerves");
    private static final ResourceLocation VESSELS = tex("clone_vessels");
    private static final ResourceLocation MUSCLE = tex("clone_muscle");
    /** Срез: плитка 16x16 в оттенках серого на 4x4 пикселя клона, кладётся по координатам клона (узор не тянется). */
    private static final ResourceLocation CUT = tex("clone_cut");
    private static final float CUT_TILE = 4;
    /** Кромка среза, пиксели клона. */
    private static final float RIM = 0.25F;
    /** Режимы коробок: обычные грани, поле среза, кромка среза. */
    private static final int FACES = 0, CAP = 1, CAP_RIM = 2;

    /**
     * Высота клона, блоки; модель — 32 пикселя. 1,5625 (камера выше, 5д, Антон 14.11: «не больше») — почти вся высота
     * камеры: макушка со вторым слоем скина в верхней точке покачивания — на 29,4/16, излучатель в нише — на 30/16.
     */
    private static final float HEIGHT = 1.5625F;
    /**
     * Ступни: клон парит в 1,5 пикселя над полом колодца (пол — 2/16 блока), покачивание: размах и период в тиках
     * (5д: ±0,5 пикселя, заметнее прежнего; подобрать в игре).
     */
    private static final float FEET = 3.5F / 16, BOB = 0.5F / 16, BOB_TICKS = 80;
    /** Центр площадки в модели машины (1/16 блока), как у кольца пола (было 7,5 по глубине — на полпикселя к лицу, Антон 08.11). */
    private static final float CENTER_X = 8, CENTER_Z = 8;
    /** Погибший клон сдвинут назад на столько пикселей клона: середина его наклонённой фигуры — над центром площадки. */
    private static final float DEAD_SHIFT = 3;
    private static final float[] NO_TINT = {1, 1, 1};
    /** Высота погибшего клона для распада, пиксели клона: макушка со вторым слоем скина. */
    private static final float DECAY_TOP = 32.5F;
    private static final float ALL = 1000;

    private enum Part { HEAD, BODY, ARM_R, ARM_L, LEG_R, LEG_L }

    private CloneRenderer() {
    }

    private static ResourceLocation tex(String name) {
        return new ResourceLocation(MatterRebirth.MOD_ID, "textures/entity/" + name + ".png");
    }

    /**
     * Нарисовать клона. Вызывается отрисовщиком машины в координатах модели (1/16 блока, лицо машины — z = 0),
     * до пузырьков и до полупрозрачного слоя (стекло и UU ложатся поверх).
     */
    static void render(RenderReplicator renderer, TileReplicator te, float partialTicks) {
        boolean dead = te.isDecaying();
        int st = te.getStage();
        float f = te.getShownGrowth(partialTicks);
        // меньше 1/20 пикселя клона не рисуется: иначе на площадке видны донца коробок ступней
        boolean growing = growing(te, f);
        if (!dead && st == 0 && !growing) {
            return;
        }
        GlStateManager.pushMatrix();
        float time = te.getWorld().getTotalWorldTime() % 24000 + partialTicks + (te.getPos().hashCode() & 63);
        float feet = FEET + BOB * (float) Math.sin(Math.PI * 2 * time / BOB_TICKS);
        GlStateManager.translate(CENTER_X, feet * 16, CENTER_Z);
        GlStateManager.rotate(180, 0, 1, 0); // перед клона (+z) — к лицу машины (−z модели)
        float s = HEIGHT * 16 / 32;
        GlStateManager.scale(s, s, s);

        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        int light = light(te);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, light & 0xFFFF, light >>> 16);
        if (dead) {
            // обмякший клон наклонён вперёд (голова выходит пикселей на 9 перед ступнями, затылок — на 3 назад):
            // сдвинуть назад, чтобы он был посередине камеры (Антон 08.11)
            GlStateManager.translate(0, 0, -DEAD_SHIFT);
        }
        body(te, f, growing, te.getDecayLeft(partialTicks));
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GlStateManager.popMatrix();
    }

    /**
     * Клон в окне машины на месте пунктирного каркаса (5б+, Антон 08.11): те же слои и срез, что в камере, в проекции
     * макетов окна (поворот 30°, наклон 25°), без покачивания. x, y — экранная точка начала координат каркаса
     * (ступни, левый передний угол фигуры в его системе), unit — пикселей окна на пиксель клона, z — над фоном окна.
     * f — доля роста текущей стадии (окно сглаживает её так же, как линию печати, чтобы срез и линия шли вместе).
     * Погибший клон — как в камере: бледный и обмякший (Антон 08.11, вместо красного каркаса). Возвращает true,
     * если что-то нарисовано.
     */
    static boolean renderGui(TileReplicator te, float f, float x, float y, float z, float unit) {
        int st = te.getStage();
        boolean growing = growing(te, f);
        boolean dead = te.isDecaying();
        if (!dead && st == 0 && !growing) {
            return false;
        }
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), lit = GL11.glIsEnabled(GL11.GL_LIGHTING);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.scale(unit, unit, unit);
        // проекция каркаса (GuiReplicator.project): экран x = x1, вниз = −y2, к зрителю = −глубина
        GlStateManager.rotate(180, 1, 0, 0);
        GlStateManager.rotate(-25, 1, 0, 0);
        GlStateManager.rotate(30, 0, 1, 0);
        // каркас стоит как клон в камере: перед фигуры к −z, тело x 0..8, z 0..4
        GlStateManager.translate(4, 0, 2);
        GlStateManager.rotate(180, 0, 1, 0);
        if (dead) {
            GlStateManager.translate(0, 0, -DEAD_SHIFT); // как в камере: обмякшая фигура посередине
        }
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        body(te, f, growing, te.getDecayLeft(Minecraft.getMinecraft().getRenderPartialTicks()));
        // окно рисуется дальше плоским (линия печати, подтверждение корзины): глубина клона им не мешает
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        if (!depth) {
            GlStateManager.disableDepth();
        }
        if (!lit) {
            GlStateManager.disableLighting();
        }
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
        return true;
    }

    /** Растёт ли слой текущей стадии настолько, что его видно (меньше 1/20 пикселя клона — нет). */
    private static boolean growing(TileReplicator te, float f) {
        return !te.isDecaying() && te.getStage() < TileReplicator.STAGES && f * 32 >= 0.05F;
    }

    /**
     * Сам клон в его координатах (пиксели модели, ступни y = 0, перед +z): слои, срез; погибший — обмякший,
     * распадается сверху: left — сколько тела осталось (0..1).
     */
    private static void body(TileReplicator te, float f, boolean growing, float left) {
        boolean dead = te.isDecaying();
        int st = te.getStage();
        int g = st + 1;
        CloneSkins.Skin skin = null;
        if (dead || st + (growing ? 1 : 0) >= 4) { // скин нужен и мышцам: при тонком скине руки тонкие
            ItemStack pattern = te.getPattern();
            UUID id = pattern.isEmpty() ? null : ItemBloodSample.getOwnerId(pattern);
            skin = id != null ? CloneSkins.get(id, ItemBloodSample.getOwnerName(pattern), ItemBloodSample.getSkin(pattern))
                    : CloneSkins.get(te.getOwnerId(), te.getOwnerName(), null);
        }
        boolean slim = skin != null && skin.slim;

        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.color(1, 1, 1, 1);

        if (dead) {
            // бледная копия скина (PaleSkins); распад срезает сверху (в позе до наклона: срез наклонён вместе с телом)
            float clip = left >= 1 ? ALL : left * DECAY_TOP;
            layer(5, -ALL, clip, slim, skin, NO_TINT, true);
            if (left < 1) {
                cut(5, clip, slim, skin, Shimmer.color(te.getWorld()), true); // распад — перелив, как кольца (Антон 14.11)
            }
        } else {
            // растущий слой — ниже clip; мышцы закрывают оболочки (они шире мышц головы и тела) — кости и оболочки
            // под растущими мышцами рисуются только выше clip
            float clip = growing ? (f >= 1 ? ALL : f * 32) : -ALL;
            float under = growing && g == 4 ? clip : -ALL;
            if (st >= 5) {
                layer(5, -ALL, ALL, slim, skin, NO_TINT, false);
            } else if (st == 4) {
                layer(4, -ALL, ALL, slim, skin, NO_TINT, false);
            } else {
                if (st >= 1) {
                    layer(1, under, ALL, slim, skin, NO_TINT, false);
                }
                if (st == 2 || st == 3) {
                    layer(st, under, ALL, slim, skin, NO_TINT, false);
                }
            }
            if (growing) {
                layer(g, -ALL, clip, slim, skin, NO_TINT, false);
                if (f < 1) {
                    cut(g, clip, slim, skin, cutColor(te), false);
                }
            }
        }
        GlStateManager.enableCull();
        GlStateManager.enableLighting();
    }

    private static void bind(ResourceLocation texture) {
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
    }

    /** Свет в камере: как у блоков машины (берётся ярче из двух), не темнее 8 по свету блоков — UU слегка светится. */
    private static int light(TileReplicator te) {
        BlockPos pos = te.getPos();
        int a = te.getWorld().getCombinedLight(pos, 0), b = te.getWorld().getCombinedLight(pos.up(), 0);
        int block = Math.max(8 << 4, Math.max(a & 0xFFFF, b & 0xFFFF));
        int sky = Math.max(a >>> 16, b >>> 16);
        return sky << 16 | block;
    }

    /**
     * Цвет среза растущего слоя — как у линии печати в окне: рост бирюзовый, откат — перелив красный ↔ фиолетовый
     * (Shimmer, Антон 14.11), перегрузка красная, ожидание (UU, энергия) жёлтое, пауза серая.
     */
    private static int cutColor(TileReplicator te) {
        if (te.getLookRollback()) {
            return Shimmer.color(te.getWorld());
        }
        switch (te.getLookLight()) {
            case ROLLBACK:
                return Shimmer.color(te.getWorld());
            case GROW:
                return 0x5CC8C8;
            case ALERT:
                return 0xFF0000; // перегрузка — чистый красный (Антон 14.11)
            case WAIT:
                return 0xFFD84A;
            default: // пауза
                return 0x969696;
        }
    }

    /**
     * Светящийся срез слоя на высоте clip: поле с текстурой, затем кромка без текстуры, на полной яркости.
     * slump — срез обмякшего погибшего клона (в его позе).
     */
    private static void cut(int layer, float clip, boolean slim, CloneSkins.Skin skin, int rgb, boolean slump) {
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
        layerMode(layer, -ALL, clip, slim, skin, lighten(rgb, 0.25F), slump, CAP);
        GlStateManager.disableTexture2D();
        layerMode(layer, -ALL, clip, slim, skin, lighten(rgb, 0.45F), slump, CAP_RIM);
        GlStateManager.enableTexture2D();
    }

    /** Цвет, подмешанный к белому на долю k (срез светлее, чем линия печати: он на тёмной UU). */
    private static float[] lighten(int rgb, float k) {
        float[] c = new float[3];
        for (int i = 0; i < 3; i++) {
            float v = (rgb >> (16 - 8 * i) & 255) / 255F;
            c[i] = v + (1 - v) * k;
        }
        return c;
    }

    /** Слой 1..5 по частям тела; видна часть коробок с yLo <= y <= yHi (пиксели клона). */
    private static void layer(int layer, float yLo, float yHi, boolean slim,
                              CloneSkins.Skin skin, float[] tint, boolean slump) {
        layerMode(layer, yLo, yHi, slim, skin, tint, slump, FACES);
    }

    private static void layerMode(int layer, float yLo, float yHi, boolean slim,
                                  CloneSkins.Skin skin, float[] tint, boolean slump, int mode) {
        switch (mode == CAP ? 0 : mode == CAP_RIM ? -1 : layer) {
            case 0:
                bind(CUT);
                break;
            case -1:
                break; // кромка без текстуры
            case 1:
                bind(FreshSkeletonTexture.LOCATION);
                break;
            case 2:
                bind(NERVES);
                break;
            case 3:
                bind(VESSELS);
                break;
            case 4:
                bind(MUSCLE);
                break;
            default:
                // погибший (обмякший) — мертвецки бледный, вместо прежнего серо-зелёного (Антон 08.11)
                bind(slump ? PaleSkins.get(skin.texture) : skin.texture);
        }
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        for (Part part : Part.values()) {
            GlStateManager.pushMatrix();
            if (slump) {
                slump(part);
            }
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
            Box b = new Box(buf, yLo, yHi, tint, mode);
            switch (layer) {
                case 1:
                    skeleton(b, part);
                    break;
                case 2:
                    shell(b, part, 0);
                    break;
                case 3:
                    shell(b, part, 0.02F); // поверх нервов той же оболочки
                    break;
                case 4:
                    muscle(b, part, slim);
                    break;
                default:
                    skin(b, part, slim);
            }
            tess.draw();
            GlStateManager.popMatrix();
        }
    }

    /**
     * Обмякшая поза погибшего клона (Антон 08.11): корпус чуть вперёд от таза, голова свешена на грудь,
     * руки висят отвесно и чуть врозь.
     */
    private static void slump(Part part) {
        if (part == Part.LEG_R || part == Part.LEG_L) {
            return;
        }
        pivot(0, 12, 0, 6, 1, 0, 0);
        if (part == Part.HEAD) {
            pivot(0, 24, 0, 20, 1, 0, 0);
        } else if (part == Part.ARM_R) {
            pivot(-5, 22, 0, -6, 1, 0, 0);
            pivot(-5, 22, 0, -4, 0, 0, 1);
        } else if (part == Part.ARM_L) {
            pivot(5, 22, 0, -6, 1, 0, 0);
            pivot(5, 22, 0, 4, 0, 0, 1);
        }
    }

    private static void pivot(float x, float y, float z, float angle, float ax, float ay, float az) {
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate(angle, ax, ay, az);
        GlStateManager.translate(-x, -y, -z);
    }

    // --- коробки слоёв (как на листе v9: stage5b/gen5b2.py) ---

    /** Ванильная модель скелета, развёртка 64x32; левые руки и ноги зеркальные, как в игре. */
    private static void skeleton(Box b, Part part) {
        b.tex(64, 64 / 2F);
        switch (part) {
            case HEAD:
                b.box(-4, 24, -4, 8, 8, 8, 0, 0, 8, 8, 8, 0, false);
                break;
            case BODY:
                b.box(-4, 12, -2, 8, 12, 4, 16, 16, 8, 12, 4, 0, false);
                break;
            case ARM_R:
                b.box(-6, 12, -1, 2, 12, 2, 40, 16, 2, 12, 2, 0, false);
                break;
            case ARM_L:
                b.box(4, 12, -1, 2, 12, 2, 40, 16, 2, 12, 2, 0, true);
                break;
            case LEG_R:
                b.box(-3, 0, -1, 2, 12, 2, 0, 16, 2, 12, 2, 0, false);
                break;
            default:
                b.box(1, 0, -1, 2, 12, 2, 0, 16, 2, 12, 2, 0, true);
        }
    }

    /** Оболочки нервов и сосудов: на 0,25 пикселя больше костей, своя развёртка 256x256, 4 текселя на пиксель. */
    private static void shell(Box b, Part part, float inflate) {
        b.tex(256, 256);
        switch (part) {
            case HEAD:
                b.box4(-4.25F, 23.75F, -4.25F, 8.5F, 8.5F, 8.5F, 0, 0, inflate);
                break;
            case BODY:
                b.box4(-4.25F, 11.75F, -2.25F, 8.5F, 12.5F, 4.5F, 0, 68, inflate);
                break;
            case ARM_R:
                b.box4(-6.25F, 12, -1.25F, 2.5F, 12, 2.5F, 104, 68, inflate);
                break;
            case ARM_L:
                b.box4(3.75F, 12, -1.25F, 2.5F, 12, 2.5F, 144, 68, inflate);
                break;
            case LEG_R:
                b.box4(-3.25F, 0, -1.25F, 2.5F, 12, 2.5F, 184, 68, inflate);
                break;
            default:
                b.box4(0.75F, 0, -1.25F, 2.5F, 12, 2.5F, 0, 136, inflate);
        }
    }

    /** Мышцы: коробки игрока, развёртка скина x4 (256x256). При тонком скине руки тоже тонкие (иначе вылезут из кожи). */
    private static void muscle(Box b, Part part, boolean slim) {
        b.tex(256, 256);
        switch (part) {
            case HEAD:
                b.box(-4, 24, -4, 8, 8, 8, 0, 0, 32, 32, 32, 0, false);
                break;
            case BODY:
                b.box(-4, 12, -2, 8, 12, 4, 64, 64, 32, 48, 16, 0, false);
                break;
            case ARM_R:
                b.box(slim ? -7 : -8, slim ? 11.5F : 12, -2, slim ? 3 : 4, 12, 4, 160, 64, 16, 48, 16, 0, false);
                break;
            case ARM_L:
                b.box(4, slim ? 11.5F : 12, -2, slim ? 3 : 4, 12, 4, 128, 192, 16, 48, 16, 0, false);
                break;
            case LEG_R:
                b.box(-4, 0, -2, 4, 12, 4, 0, 64, 16, 48, 16, 0, false);
                break;
            default:
                b.box(0, 0, -2, 4, 12, 4, 64, 192, 16, 48, 16, 0, false);
        }
    }

    /**
     * Кожа: скин 64x64 на коробках игрока (поверх мышц на 0,05 пикселя) и второй слой скина (шляпа, куртка, рукава,
     * штанины), как у игрока. Тонкие руки — 3 пикселя.
     */
    private static void skin(Box b, Part part, boolean slim) {
        b.tex(64, 64);
        float in = 0.05F;
        int aw = slim ? 3 : 4;
        float ax = slim ? -7 : -8, ay = slim ? 11.5F : 12;
        switch (part) {
            case HEAD:
                b.box(-4, 24, -4, 8, 8, 8, 0, 0, 8, 8, 8, in, false);
                b.box(-4, 24, -4, 8, 8, 8, 32, 0, 8, 8, 8, in + 0.5F, false);
                break;
            case BODY:
                b.box(-4, 12, -2, 8, 12, 4, 16, 16, 8, 12, 4, in, false);
                b.box(-4, 12, -2, 8, 12, 4, 16, 32, 8, 12, 4, in + 0.25F, false);
                break;
            case ARM_R:
                b.box(ax, ay, -2, aw, 12, 4, 40, 16, aw, 12, 4, in, false);
                b.box(ax, ay, -2, aw, 12, 4, 40, 32, aw, 12, 4, in + 0.25F, false);
                break;
            case ARM_L:
                b.box(4, ay, -2, aw, 12, 4, 32, 48, aw, 12, 4, in, false);
                b.box(4, ay, -2, aw, 12, 4, 48, 48, aw, 12, 4, in + 0.25F, false);
                break;
            case LEG_R:
                b.box(-4, 0, -2, 4, 12, 4, 0, 16, 4, 12, 4, in, false);
                b.box(-4, 0, -2, 4, 12, 4, 0, 32, 4, 12, 4, in + 0.25F, false);
                break;
            default:
                b.box(0, 0, -2, 4, 12, 4, 16, 48, 4, 12, 4, in, false);
                b.box(0, 0, -2, 4, 12, 4, 0, 48, 4, 12, 4, in + 0.25F, false);
        }
    }

    /**
     * Коробка с развёрткой как у ModelBox Minecraft: полоса граней «−x, перед, +x, зад» под верхом и низом.
     * Видна часть по высоте yLo..yHi; у срезанной коробки нет верха (или низа), боковые грани укорочены вместе с картинкой.
     * Затенение граней — как у блоков (верх 1, перед и зад 0,8, бока 0,6, низ 0,5).
     */
    private static final class Box {
        private final BufferBuilder buf;
        private final float yLo, yHi;
        private final float[] tint;
        private final int mode;
        private float texW, texH;

        Box(BufferBuilder buf, float yLo, float yHi, float[] tint, int mode) {
            this.buf = buf;
            this.yLo = yLo;
            this.yHi = yHi;
            this.tint = tint;
            this.mode = mode;
        }

        void tex(float w, float h) {
            texW = w;
            texH = h;
        }

        /** Оболочка: размеры в пикселях, развёртка в текселях по 4 на пиксель. */
        void box4(float x, float y, float z, float w, float h, float d, float u, float v, float inflate) {
            box(x, y, z, w, h, d, u, v, w * 4, h * 4, d * 4, inflate, false);
        }

        /**
         * x, y, z, w, h, d — коробка в пикселях клона; u, v — угол развёртки, tw, th, td — размеры коробки в развёртке
         * (в пикселях картинки); inflate — раздуть во все стороны (картинка та же); mirror — зеркально по x, как в игре.
         */
        void box(float x, float y, float z, float w, float h, float d, float u, float v, float tw, float th, float td,
                 float inflate, boolean mirror) {
            float x0 = x - inflate, y0 = y - inflate, z0 = z - inflate;
            float x1 = x + w + inflate, y1 = y + h + inflate, z1 = z + d + inflate;
            float lo = Math.max(y0, yLo), hi = Math.min(y1, yHi);
            if (hi <= lo) {
                return;
            }
            if (mode != FACES) {
                if (yHi < y1) { // срез только у коробок, через которые проходит линия роста
                    // у вложенных коробок (кожа и второй слой) крышка внешней чуть выше: без мерцания на общей высоте
                    cap(x0, z0, x1, z1, hi + inflate * 0.02F);
                }
                return;
            }
            float vt = (v + td + (y1 - hi) / (y1 - y0) * th) / texH, vb = (v + td + (y1 - lo) / (y1 - y0) * th) / texH;
            float xa = mirror ? x1 : x0, xb = mirror ? x0 : x1; // где у «перед» левый край картинки и правый
            float nxSide = mirror ? 1 : -1; // «−x» грань развёртки у зеркальной коробки — справа
            // перед (+z)
            float u0 = (u + td) / texW, u1 = (u + td + tw) / texW;
            quad(xa, lo, z1, u0, vb, xb, lo, z1, u1, vb, xb, hi, z1, u1, vt, xa, hi, z1, u0, vt, 0, 0, 1, 0.8F);
            // зад (−z)
            u0 = (u + td * 2 + tw) / texW;
            u1 = (u + td * 2 + tw * 2) / texW;
            quad(xb, lo, z0, u0, vb, xa, lo, z0, u1, vb, xa, hi, z0, u1, vt, xb, hi, z0, u0, vt, 0, 0, -1, 0.8F);
            // «−x» грань развёртки: от зада к переду
            u0 = u / texW;
            u1 = (u + td) / texW;
            quad(xa, lo, z0, u0, vb, xa, lo, z1, u1, vb, xa, hi, z1, u1, vt, xa, hi, z0, u0, vt, nxSide, 0, 0, 0.6F);
            // «+x» грань развёртки: от переда к заду
            u0 = (u + td + tw) / texW;
            u1 = (u + td * 2 + tw) / texW;
            quad(xb, lo, z1, u0, vb, xb, lo, z0, u1, vb, xb, hi, z0, u1, vt, xb, hi, z1, u0, vt, -nxSide, 0, 0, 0.6F);
            float ua = (u + td) / texW, ub = (u + td + tw) / texW;
            float va = v / texH, vz = (v + td) / texH;
            if (yHi >= y1) { // верх: u по x, v от зада к переду
                quad(xa, y1, z0, ua, va, xa, y1, z1, ua, vz, xb, y1, z1, ub, vz, xb, y1, z0, ub, va, 0, 1, 0, 1F);
            }
            if (yLo <= y0) { // низ
                float uc = (u + td + tw) / texW, ud = (u + td + tw * 2) / texW;
                quad(xa, y0, z0, uc, va, xa, y0, z1, uc, vz, xb, y0, z1, ud, vz, xb, y0, z0, ud, va, 0, -1, 0, 0.5F);
            }
        }

        /** Крышка среза на высоте y: поле внутри кромки (текстура по координатам клона) или сама кромка — 4 полосы. */
        private void cap(float x0, float z0, float x1, float z1, float y) {
            float r = Math.min(RIM, Math.min(x1 - x0, z1 - z0) / 4);
            float ix0 = x0 + r, ix1 = x1 - r, iz0 = z0 + r, iz1 = z1 - r;
            if (mode == CAP) {
                flat(ix0, iz0, ix1, iz1, y, true);
            } else {
                flat(x0, z0, x1, iz0, y, false);
                flat(x0, iz1, x1, z1, y, false);
                flat(x0, iz0, ix0, iz1, y, false);
                flat(ix1, iz0, x1, iz1, y, false);
            }
        }

        private void flat(float x0, float z0, float x1, float z1, float y, boolean tex) {
            float u0 = tex ? x0 / CUT_TILE : 0, u1 = tex ? x1 / CUT_TILE : 0;
            float v0 = tex ? z0 / CUT_TILE : 0, v1 = tex ? z1 / CUT_TILE : 0;
            quad(x0, y, z0, u0, v0, x0, y, z1, u0, v1, x1, y, z1, u1, v1, x1, y, z0, u1, v0, 0, 1, 0, 1F);
        }

        private void quad(float x0, float y0, float z0, float u0, float v0, float x1, float y1, float z1, float u1, float v1,
                          float x2, float y2, float z2, float u2, float v2, float x3, float y3, float z3, float u3, float v3,
                          float nx, float ny, float nz, float shade) {
            float r = tint[0] * shade, g = tint[1] * shade, b = tint[2] * shade;
            buf.pos(x0, y0, z0).tex(u0, v0).color(r, g, b, 1F).normal(nx, ny, nz).endVertex();
            buf.pos(x1, y1, z1).tex(u1, v1).color(r, g, b, 1F).normal(nx, ny, nz).endVertex();
            buf.pos(x2, y2, z2).tex(u2, v2).color(r, g, b, 1F).normal(nx, ny, nz).endVertex();
            buf.pos(x3, y3, z3).tex(u3, v3).color(r, g, b, 1F).normal(nx, ny, nz).endVertex();
        }
    }
}
