package pl.chestbot;

import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/** Skanuje zaladowane chunki w poszukiwaniu skrzynek / beczek itp. (wspolne dla bota i ESP). */
public final class ChestScanner {
    public enum Kind { CHEST, TRAPPED, ENDER, BARREL, SHULKER }

    public record Target(BlockPos pos, Kind kind) {}

    private static List<Target> targets = List.of();
    private static int lastScan = -1000;

    private ChestScanner() {}

    public static List<Target> getTargets() { return targets; }

    public static void update(MinecraftClient mc, int tick, int radiusBlocks) {
        if (tick - lastScan < 10) return;
        lastScan = tick;
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        if (w == null || p == null) return;

        int cr = (radiusBlocks >> 4) + 1;
        int pcx = p.getBlockX() >> 4;
        int pcz = p.getBlockZ() >> 4;
        List<Target> out = new ArrayList<>();
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dz = -cr; dz <= cr; dz++) {
                WorldChunk ch = w.getChunkManager().getWorldChunk(pcx + dx, pcz + dz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    Kind k = kindOf(be);
                    if (k != null) out.add(new Target(be.getPos().toImmutable(), k));
                }
            }
        }
        targets = out;
    }

    private static Kind kindOf(BlockEntity be) {
        if (be instanceof TrappedChestBlockEntity) return Kind.TRAPPED;
        if (be instanceof ChestBlockEntity) return Kind.CHEST;
        if (be instanceof EnderChestBlockEntity) return Kind.ENDER;
        if (be instanceof BarrelBlockEntity) return Kind.BARREL;
        if (be instanceof ShulkerBoxBlockEntity) return Kind.SHULKER;
        return null;
    }

    public static void clear() { targets = List.of(); }
}
