package dlx.matterrebirth.client;

import dlx.matterrebirth.MatterRebirth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Синхронизатор на голове (этап 5е, лист v3 по концепту 05.11, Антон 14.11): обруч вокруг затылка, корпуса на висках,
 * веер усиков с сенсорами, крючок за ухом. Геометрия — коробки по 1/3 пикселя из файла *.boxes (генератор
 * stage5e/gen_sync_boxes.py), каждая своего цвета; рисуется гранями с цветом вершин на белой текстуре, так что
 * свет и шейдеры работают как у обычной модели. Оси — модели головы (y вниз, перед -z), начало — шея.
 * Модель одна на оба варианта (Антон 14.11): на кожаном шлеме те же детали того же размера сдвинуты наружу на пиксель
 * (шапка на пиксель шире головы во все стороны): всё, что за гранью головы, уходит за грань шапки, а коробки, которые
 * переходят через ребро головы (углы усиков, обруч), удлиняются на пиксель, чтобы углы сошлись. Увеличение всей модели
 * в 10/8 Антон отклонил — громоздко. Коробки с меткой hook (крючок за ухом) на шапке не рисуются.
 */
final class SyncHeadModel {

    private static final float VOXEL = 1 / 16F / 3; // 3 вокселя на пиксель
    private static final float HEAD_CENTER_Y = -4 / 16F; // центр головы (начало осей — шея)
    private static final float SHRINK = 0.995F;          // шапка 5 пикселей от центра → на 1/40 пикселя внутрь
    private static ResourceLocation white;

    private final ResourceLocation source;
    private static final int HALF = 12;  // полголовы в вокселях (грани x, z = ±12, макушка y = -24)
    // Сдвиг на кожаной шапке: она на пиксель (3 вокселя) шире головы во все стороны, а все детали модели лежат прямо
    // на поверхности головы — на шапке они так же ложатся прямо на неё.
    private static final int CAP_SIDE = 3, CAP_BACK = 3, CAP_TOP = 3, CAP_FRONT = 3;
    private float[][] boxes; // x, y, z, dx, dy, dz, r, g, b, крючок (1/0)
    private float[][] capBoxes;
    private boolean failed;

    SyncHeadModel(String name) {
        this.source = new ResourceLocation(MatterRebirth.MOD_ID, "models/armor/" + name + ".boxes");
    }

    /** Рисует в текущих осях головы (после bipedHead.postRender): на голове или на кожаной шапке (без крючка). */
    void render(boolean onCap) {
        float[][] bs = onCap ? capBoxes() : boxes();
        if (bs.length == 0) {
            return;
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(white());
        GlStateManager.pushMatrix();
        // Чуть уже головы (Антон 14.11): корпуса и усики лежат прямо на коже или шапке, и их внутренние грани были
        // в одной плоскости с гранями головы — с шейдером это мерцало. Сжатие к центру головы на 1/40 пикселя утапливает
        // эти грани в голову: плоскости больше не совпадают, а зазора нет (расширение на 1/10 давало видимый зазор).
        // Все детали толщиной от 1/3 пикселя, так что снаружи ничего не теряется; волосы второго слоя скина
        // (1/2 пикселя) по-прежнему внутри корпусов и усиков.
        GlStateManager.translate(0, HEAD_CENTER_Y, 0);
        GlStateManager.scale(SHRINK, SHRINK, SHRINK);
        GlStateManager.translate(0, -HEAD_CENTER_Y, 0);
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL);
        for (float[] b : bs) {
            if (!onCap || b[9] == 0) {
                box(buf, b);
            }
        }
        Tessellator.getInstance().draw();
        GlStateManager.popMatrix();
    }

    /** Те же коробки для кожаной шапки: каждая грань за пределами головы сдвинута наружу на пиксель. */
    private float[][] capBoxes() {
        if (capBoxes == null) {
            float[][] bs = boxes();
            float[][] out = new float[bs.length][];
            for (int i = 0; i < bs.length; i++) {
                float[] b = bs[i].clone();
                for (int axis = 0; axis < 3; axis++) {
                    float lo = b[axis], hi = b[axis] + b[axis + 3];
                    lo = push(axis, lo);
                    hi = push(axis, hi);
                    b[axis] = lo;
                    b[axis + 3] = hi - lo;
                }
                out[i] = b;
            }
            if (bs.length > 0) {
                capBoxes = out; // пока модель не прочитана, не запоминать пустой список
            }
            return out;
        }
        return capBoxes;
    }

    /** Координата за гранью головы уходит наружу на сдвиг этой стороны (оси головы: y вниз, перед -z). */
    private static float push(int axis, float c) {
        switch (axis) {
            case 0:
                return c >= HALF ? c + CAP_SIDE : c <= -HALF ? c - CAP_SIDE : c;
            case 1:
                return c <= -2 * HALF ? c - CAP_TOP : c; // шею не трогаем
            default:
                return c >= HALF ? c + CAP_BACK : c <= -HALF ? c - CAP_FRONT : c;
        }
    }

    private static void box(BufferBuilder buf, float[] b) {
        float x0 = b[0] * VOXEL, y0 = b[1] * VOXEL, z0 = b[2] * VOXEL;
        float x1 = x0 + b[3] * VOXEL, y1 = y0 + b[4] * VOXEL, z1 = z0 + b[5] * VOXEL;
        float r = b[6], g = b[7], bl = b[8];
        face(buf, r, g, bl, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
        face(buf, r, g, bl, -1, 0, 0, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0);
        face(buf, r, g, bl, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        face(buf, r, g, bl, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        face(buf, r, g, bl, 0, 0, 1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
        face(buf, r, g, bl, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
    }

    private static void face(BufferBuilder buf, float r, float g, float b, float nx, float ny, float nz, float... p) {
        for (int i = 0; i < 12; i += 3) {
            buf.pos(p[i], p[i + 1], p[i + 2]).tex(0.5, 0.5).color(r, g, b, 1).normal(nx, ny, nz).endVertex();
        }
    }

    private float[][] boxes() {
        if (boxes == null && !failed) {
            List<float[]> list = new ArrayList<>();
            try (IResource res = Minecraft.getMinecraft().getResourceManager().getResource(source);
                 BufferedReader in = new BufferedReader(new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    String[] f = line.split("\\s+");
                    int rgb = Integer.parseInt(f[6], 16);
                    float[] b = new float[10];
                    b[9] = f.length > 7 && "hook".equals(f[7]) ? 1 : 0;
                    for (int i = 0; i < 6; i++) {
                        b[i] = Integer.parseInt(f[i]);
                    }
                    b[6] = (rgb >> 16 & 255) / 255F;
                    b[7] = (rgb >> 8 & 255) / 255F;
                    b[8] = (rgb & 255) / 255F;
                    list.add(b);
                }
                boxes = list.toArray(new float[0][]);
            } catch (Exception e) {
                MatterRebirth.LOG.error("Neurosynchronizer head model {} could not be read", source, e);
                failed = true;
            }
        }
        return boxes != null ? boxes : new float[0][];
    }

    /** Белая текстура 1×1: цвет дают вершины. */
    private static ResourceLocation white() {
        if (white == null) {
            DynamicTexture tex = new DynamicTexture(1, 1);
            Arrays.fill(tex.getTextureData(), 0xFFFFFFFF);
            tex.updateDynamicTexture();
            white = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("matterrebirth_white", tex);
        }
        return white;
    }
}
