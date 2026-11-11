package pl.chestbot;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Chest ESP - podswietla skrzynki przez sciany. */
public final class ChestEsp {
    private record Entry(Box box, int rgb, Vec3d center) {}

    private ChestEsp() {}

    public static void render(WorldRenderContext ctx) {
        Config c = Config.get();
        if (!c.espEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld w = mc.world;
        MatrixStack ms = ctx.matrixStack();
        if (w == null || mc.player == null || ms == null) return;

        Vec3d cam = ctx.camera().getPos();
        double maxSq = (double) c.espRange * c.espRange;

        List<Entry> list = new ArrayList<>();
        for (ChestScanner.Target t : ChestScanner.getTargets()) {
            if (!enabled(t.kind(), c)) continue;
            Vec3d center = Vec3d.ofCenter(t.pos());
            if (cam.squaredDistanceTo(center) > maxSq) continue;
            BlockState st = w.getBlockState(t.pos());
            VoxelShape shape = st.getOutlineShape(w, t.pos());
            Box box = shape.isEmpty() ? new Box(t.pos()) : shape.getBoundingBox().offset(t.pos());
            boolean visited = c.espHighlightVisited && Bot.isVisited(t.pos());
            list.add(new Entry(box.expand(0.002), visited ? c.espVisitedColor : c.espColor, center));
        }
        if (list.isEmpty()) return;

        Matrix4f m = ms.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        if (c.espFill) {
            float a = c.espFillAlpha / 100f;
            BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (Entry e : list) fillBox(bb, m, e.box, cam, e.rgb, a);
            BufferRenderer.drawWithGlobalProgram(bb.end());
        }
        if (c.espOutline) {
            RenderSystem.lineWidth((float) c.espLineWidth);
            BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            for (Entry e : list) lineBox(bb, m, e.box, cam, e.rgb);
            BufferRenderer.drawWithGlobalProgram(bb.end());
        }
        if (c.espTracers) {
            RenderSystem.lineWidth((float) c.espLineWidth);
            BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            Vec3d start = Vec3d.fromPolar(ctx.camera().getPitch(), ctx.camera().getYaw()).multiply(0.4);
            for (Entry e : list) {
                Vec3d end = e.center.subtract(cam);
                float r = ((e.rgb >> 16) & 255) / 255f, g = ((e.rgb >> 8) & 255) / 255f, b = (e.rgb & 255) / 255f;
                bb.vertex(m, (float) start.x, (float) start.y, (float) start.z).color(r, g, b, 0.9f);
                bb.vertex(m, (float) end.x, (float) end.y, (float) end.z).color(r, g, b, 0.9f);
            }
            BufferRenderer.drawWithGlobalProgram(bb.end());
        }

        RenderSystem.lineWidth(1f);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private static boolean enabled(ChestScanner.Kind k, Config c) {
        return switch (k) {
            case CHEST, TRAPPED -> c.espChests;
            case ENDER -> c.espEnder;
            case BARREL -> c.espBarrels;
            case SHULKER -> c.espShulkers;
        };
    }

    private static void v(BufferBuilder bb, Matrix4f m, double x, double y, double z, float r, float g, float b, float a) {
        bb.vertex(m, (float) x, (float) y, (float) z).color(r, g, b, a);
    }

    private static void fillBox(BufferBuilder bb, Matrix4f m, Box box, Vec3d cam, int rgb, float a) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        double x1 = box.minX - cam.x, y1 = box.minY - cam.y, z1 = box.minZ - cam.z;
        double x2 = box.maxX - cam.x, y2 = box.maxY - cam.y, z2 = box.maxZ - cam.z;
        // dol
        v(bb, m, x1, y1, z1, r, g, b, a); v(bb, m, x2, y1, z1, r, g, b, a); v(bb, m, x2, y1, z2, r, g, b, a); v(bb, m, x1, y1, z2, r, g, b, a);
        // gora
        v(bb, m, x1, y2, z1, r, g, b, a); v(bb, m, x1, y2, z2, r, g, b, a); v(bb, m, x2, y2, z2, r, g, b, a); v(bb, m, x2, y2, z1, r, g, b, a);
        // polnoc
        v(bb, m, x1, y1, z1, r, g, b, a); v(bb, m, x1, y2, z1, r, g, b, a); v(bb, m, x2, y2, z1, r, g, b, a); v(bb, m, x2, y1, z1, r, g, b, a);
        // poludnie
        v(bb, m, x1, y1, z2, r, g, b, a); v(bb, m, x2, y1, z2, r, g, b, a); v(bb, m, x2, y2, z2, r, g, b, a); v(bb, m, x1, y2, z2, r, g, b, a);
        // zachod
        v(bb, m, x1, y1, z1, r, g, b, a); v(bb, m, x1, y1, z2, r, g, b, a); v(bb, m, x1, y2, z2, r, g, b, a); v(bb, m, x1, y2, z1, r, g, b, a);
        // wschod
        v(bb, m, x2, y1, z1, r, g, b, a); v(bb, m, x2, y2, z1, r, g, b, a); v(bb, m, x2, y2, z2, r, g, b, a); v(bb, m, x2, y1, z2, r, g, b, a);
    }

    private static void lineBox(BufferBuilder bb, Matrix4f m, Box box, Vec3d cam, int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f, a = 1f;
        double x1 = box.minX - cam.x, y1 = box.minY - cam.y, z1 = box.minZ - cam.z;
        double x2 = box.maxX - cam.x, y2 = box.maxY - cam.y, z2 = box.maxZ - cam.z;
        double[][] e = {
                {x1, y1, z1, x2, y1, z1}, {x2, y1, z1, x2, y1, z2}, {x2, y1, z2, x1, y1, z2}, {x1, y1, z2, x1, y1, z1},
                {x1, y2, z1, x2, y2, z1}, {x2, y2, z1, x2, y2, z2}, {x2, y2, z2, x1, y2, z2}, {x1, y2, z2, x1, y2, z1},
                {x1, y1, z1, x1, y2, z1}, {x2, y1, z1, x2, y2, z1}, {x2, y1, z2, x2, y2, z2}, {x1, y1, z2, x1, y2, z2}
        };
        for (double[] l : e) {
            v(bb, m, l[0], l[1], l[2], r, g, b, a);
            v(bb, m, l[3], l[4], l[5], r, g, b, a);
        }
    }
}
