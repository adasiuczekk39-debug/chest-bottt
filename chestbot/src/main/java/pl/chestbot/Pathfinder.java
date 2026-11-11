package pl.chestbot;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.function.Predicate;

/**
 * Prosty A* po siatce blokow: chodzenie, wskakiwanie o 1 w gore, spadanie do 3 blokow.
 * Zwraca liste pozycji "stop" (blok w ktorym stoja nogi gracza).
 */
public final class Pathfinder {
    private static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private static final class Node {
        final BlockPos pos; final Node parent; final double g, f;
        Node(BlockPos pos, Node parent, double g, double f) { this.pos = pos; this.parent = parent; this.g = g; this.f = f; }
    }

    private Pathfinder() {}

    public static List<BlockPos> find(ClientWorld w, BlockPos start, Predicate<BlockPos> goal,
                                      Vec3d hTarget, double slack, double yWeight, int maxNodes) {
        Map<Long, Node> best = new HashMap<>();
        Set<Long> closed = new HashSet<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));

        Node s = new Node(start, null, 0, h(start, hTarget, slack, yWeight));
        best.put(start.asLong(), s);
        open.add(s);
        int expanded = 0;

        while (!open.isEmpty() && expanded < maxNodes) {
            Node cur = open.poll();
            long key = cur.pos.asLong();
            if (best.get(key) != cur || closed.contains(key)) continue;
            closed.add(key);
            expanded++;

            if (goal.test(cur.pos)) return build(cur);

            for (Direction d : DIRS) {
                BlockPos n = step(w, cur.pos, d);
                if (n == null) continue;
                if (Math.abs(n.getX() - start.getX()) > 90 || Math.abs(n.getZ() - start.getZ()) > 90) continue;
                long nk = n.asLong();
                if (closed.contains(nk)) continue;
                int dy = n.getY() - cur.pos.getY();
                double cost = 1 + (dy > 0 ? 1.0 : dy < 0 ? -dy * 0.5 : 0);
                double g = cur.g + cost;
                Node old = best.get(nk);
                if (old != null && old.g <= g) continue;
                Node nn = new Node(n, cur, g, g + h(n, hTarget, slack, yWeight));
                best.put(nk, nn);
                open.add(nn);
            }
        }
        return null;
    }

    private static double h(BlockPos p, Vec3d t, double slack, double yWeight) {
        double dx = p.getX() + 0.5 - t.x;
        double dy = (p.getY() - t.y) * yWeight;
        double dz = p.getZ() + 0.5 - t.z;
        return Math.max(0, Math.sqrt(dx * dx + dy * dy + dz * dz) - slack);
    }

    private static List<BlockPos> build(Node end) {
        List<BlockPos> out = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) out.add(n.pos);
        Collections.reverse(out);
        if (!out.isEmpty()) out.remove(0);
        return out;
    }

    private static BlockPos step(ClientWorld w, BlockPos p, Direction d) {
        BlockPos n = p.offset(d);
        if (passable(w, n) && passable(w, n.up())) {
            if (ground(w, n.down())) return n;
            for (int k = 1; k <= 3; k++) {            // spadanie (max 3 bloki = bez obrazen)
                BlockPos t = n.down(k);
                if (!passable(w, t)) break;
                if (ground(w, t.down())) return t;
            }
            return null;
        }
        // wskakiwanie o 1 blok
        if (passable(w, p.up(2)) && passable(w, n.up()) && passable(w, n.up(2)) && ground(w, n)) {
            return n.up();
        }
        return null;
    }

    private static boolean loaded(ClientWorld w, BlockPos p) {
        if (p.getY() < w.getBottomY() || p.getY() >= w.getTopY()) return false;
        return w.getChunkManager().getWorldChunk(p.getX() >> 4, p.getZ() >> 4) != null;
    }

    private static boolean passable(ClientWorld w, BlockPos p) {
        if (!loaded(w, p)) return false;
        BlockState s = w.getBlockState(p);
        if (!s.getFluidState().isEmpty()) return false;
        if (!s.getCollisionShape(w, p).isEmpty()) return false;
        Block b = s.getBlock();
        return b != Blocks.FIRE && b != Blocks.SOUL_FIRE && b != Blocks.COBWEB
                && b != Blocks.SWEET_BERRY_BUSH && b != Blocks.POWDER_SNOW && b != Blocks.WITHER_ROSE;
    }

    private static boolean ground(ClientWorld w, BlockPos p) {
        if (!loaded(w, p)) return false;
        BlockState s = w.getBlockState(p);
        if (s.getCollisionShape(w, p).isEmpty()) return false;
        if (s.getCollisionShape(w, p).getMax(Direction.Axis.Y) > 1.0) return false; // plotki itp.
        Block b = s.getBlock();
        return b != Blocks.MAGMA_BLOCK && b != Blocks.CACTUS && b != Blocks.CAMPFIRE
                && b != Blocks.SOUL_CAMPFIRE && b != Blocks.HONEY_BLOCK;
    }
}
