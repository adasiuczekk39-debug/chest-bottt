package pl.chestbot;

import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** Glowna logika bota: szukaj skrzynki -> idz -> otworz -> zabierz wszystko -> (pelny EQ -> /sell all) -> ... -> koniec ochrony -> /spawn */
public final class Bot {
    public enum State { IDLE, SEARCHING, WALKING, EXPLORING, OPENING, LOOTING, SELLING }

    public static int tick = 0;
    public static int chestsLooted = 0;
    public static int sells = 0;
    public static String lastOverlay = "";

    private static boolean enabled = false;
    private static State state = State.IDLE;
    private static String status = "";

    // ochrona
    private static int lastProtectionTick = -100000;
    private static boolean protectionSeen = false;
    private static int startTick = 0;

    // odwiedzone skrzynki: pos -> tick wygasniecia
    private static final Map<Long, Integer> visited = new HashMap<>();

    private static ChestScanner.Target target;
    private static List<BlockPos> path;
    private static int pathIdx;
    private static boolean keysHeld;
    private static Vec3d stuckPos = Vec3d.ZERO;
    private static int stuckCheck, stuckCount;
    private static int exploreCooldown;

    private static int openStart;
    private static int lootStart, nextClick, lastClicked;
    private static final Set<Integer> skip = new HashSet<>();
    private static int sellStage, sellTick, sellTries;
    private static final Random RANDOM = new Random();

    private Bot() {}

    // ------------------------------------------------------------------ API

    public static boolean isEnabled() { return enabled; }

    public static void toggle() { if (enabled) stop("Wyłączony ręcznie"); else start(); }

    public static void start() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Config c = Config.get();
        enabled = true;
        state = State.SEARCHING;
        startTick = tick;
        target = null;
        path = null;
        chestsLooted = 0;
        sells = 0;
        sellTries = 0;
        stuckCount = 0;
        protectionSeen = (tick - lastProtectionTick) <= c.protectionTimeoutSec * 20;
        setStatus("Start");
        chat("§aBot włączony.");
    }

    public static void stop(String reason) {
        MinecraftClient mc = MinecraftClient.getInstance();
        release(mc);
        enabled = false;
        state = State.IDLE;
        path = null;
        target = null;
        if (mc.player != null && mc.player.currentScreenHandler != mc.player.playerScreenHandler
                && mc.currentScreen instanceof GenericContainerScreen) {
            mc.player.closeHandledScreen();
        }
        if (reason != null) chat("§cBot zatrzymany: §f" + reason);
    }

    public static void clearVisited() { visited.clear(); }

    public static void onDisconnect() {
        enabled = false;
        state = State.IDLE;
        keysHeld = false;
        visited.clear();
        ChestScanner.clear();
    }

    public static boolean isVisited(BlockPos pos) {
        Integer e = visited.get(pos.asLong());
        if (e == null) return false;
        if (tick >= e) { visited.remove(pos.asLong()); return false; }
        return true;
    }

    /** Wywolywane dla kazdej wiadomosci systemowej; overlay == true to tekst nad hotbarem. */
    public static void onGameMessage(Text msg, boolean overlay) {
        if (!overlay) return;
        String s = msg.getString();
        if (s.isBlank()) return;
        lastOverlay = s;
        Config c = Config.get();
        String kw = c.protectionKeyword == null ? "" : c.protectionKeyword.trim().toLowerCase(Locale.ROOT);
        if (kw.isEmpty() || s.toLowerCase(Locale.ROOT).contains(kw)) {
            lastProtectionTick = tick;
            protectionSeen = true;
        }
    }

    public static String stateLabel() {
        if (!enabled) return "WYŁĄCZONY";
        return switch (state) {
            case IDLE -> "bezczynny";
            case SEARCHING -> "szuka skrzynki";
            case WALKING -> "idzie do skrzynki";
            case EXPLORING -> "eksploruje";
            case OPENING -> "otwiera skrzynkę";
            case LOOTING -> "zabiera przedmioty";
            case SELLING -> "sprzedaje";
        };
    }

    public static String statusText() { return status; }

    public static String protectionLabel() {
        if (!protectionSeen) return "czekam na tekst...";
        return String.format(Locale.ROOT, "aktywna (%.1fs temu)", (tick - lastProtectionTick) / 20.0);
    }

    public static boolean protectionSeenNow() { return protectionSeen; }

    public static int freeSlots(ClientPlayerEntity p) {
        int n = 0;
        for (ItemStack s : p.getInventory().main) if (s.isEmpty()) n++;
        return n;
    }

    // ------------------------------------------------------------------ TICK

    public static void onTick(MinecraftClient mc) {
        tick++;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            if (enabled) { enabled = false; state = State.IDLE; keysHeld = false; }
            return;
        }
        Config c = Config.get();

        int rad = 0;
        if (c.espEnabled) rad = Math.max(rad, c.espRange);
        if (enabled) rad = Math.max(rad, c.searchRadius);
        if (rad > 0) ChestScanner.update(mc, tick, rad);

        if (!enabled) return;
        if (p.isDead()) { stop("Gracz nie żyje"); return; }

        // ---- ochrona (tekst nad hotbarem) ----
        if (c.requireProtection) {
            if (protectionSeen) {
                if (tick - lastProtectionTick > c.protectionTimeoutSec * 20) {
                    goSpawn(mc, p, "Ochrona dobiegła końca");
                    return;
                }
            } else if (tick - startTick > c.protectionStartGraceSec * 20) {
                goSpawn(mc, p, "Nie wykryto ochrony nad hotbarem");
                return;
            }
        }

        switch (state) {
            case SEARCHING -> doSearch(mc, p, c);
            case WALKING, EXPLORING -> doWalk(mc, p, c);
            case OPENING -> doOpen(mc, p, c);
            case LOOTING -> doLoot(mc, p, c);
            case SELLING -> doSell(mc, p, c);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ STANY

    private static void doSearch(MinecraftClient mc, ClientPlayerEntity p, Config c) {
        release(mc);
        if (p.currentScreenHandler != p.playerScreenHandler) {
            p.closeHandledScreen();
            return;
        }
        if (freeSlots(p) <= c.minFreeSlots) {
            if (c.autoSell) beginSell();
            else stop("Ekwipunek pełny");
            return;
        }

        ChestScanner.Target best = findNearest(mc, p, c);
        if (best == null) {
            setStatus("Brak skrzynek w zasięgu");
            if (c.explore) startExplore(mc, p);
            return;
        }
        target = best;
        if (canReach(p, best.pos(), c)) { beginOpen(); return; }

        Vec3d ctr = Vec3d.ofCenter(best.pos());
        double reach = c.reach;
        List<BlockPos> pth = Pathfinder.find(mc.world, p.getBlockPos(),
                n -> new Vec3d(n.getX() + 0.5, n.getY() + 1.62, n.getZ() + 0.5).squaredDistanceTo(ctr) <= reach * reach,
                ctr, reach, 1.0, 12000);
        if (pth == null) {
            markVisited(mc.world, best.pos(), 20 * 30);
            setStatus("Brak ścieżki do skrzynki - pomijam");
            return;
        }
        beginWalk(p, pth, State.WALKING);
        setStatus("Idę do skrzynki (" + (int) Math.sqrt(p.getPos().squaredDistanceTo(ctr)) + " m)");
    }

    private static void startExplore(MinecraftClient mc, ClientPlayerEntity p) {
        if (tick < exploreCooldown) return;
        exploreCooldown = tick + 10;
        double ang = RANDOM.nextDouble() * Math.PI * 2;
        double dist = 14 + RANDOM.nextDouble() * 14;
        double tx = p.getX() + Math.cos(ang) * dist, tz = p.getZ() + Math.sin(ang) * dist;
        Vec3d tv = new Vec3d(tx, p.getY(), tz);
        List<BlockPos> pth = Pathfinder.find(mc.world, p.getBlockPos(), n -> {
            double dx = n.getX() + 0.5 - tx, dz = n.getZ() + 0.5 - tz;
            return dx * dx + dz * dz <= 6.25;
        }, tv, 2.0, 0.0, 4000);
        if (pth != null && !pth.isEmpty()) {
            beginWalk(p, pth, State.EXPLORING);
            setStatus("Eksploruję w poszukiwaniu skrzynek");
        }
    }

    private static void beginWalk(ClientPlayerEntity p, List<BlockPos> pth, State s) {
        path = pth;
        pathIdx = 0;
        state = s;
        stuckPos = p.getPos();
        stuckCheck = tick;
    }

    private static void doWalk(MinecraftClient mc, ClientPlayerEntity p, Config c) {
        if (path == null || pathIdx >= path.size()) { release(mc); state = State.SEARCHING; return; }

        if (state == State.WALKING) {
            if (target == null || !isTargetBlock(mc.world, target.pos())) {
                release(mc); target = null; state = State.SEARCHING; return;
            }
            if (canReach(p, target.pos(), c)) { release(mc); beginOpen(); return; }
        } else if (tick % 20 == 0 && findNearest(mc, p, c) != null) {
            release(mc); state = State.SEARCHING; return;
        }

        // wykrywanie zablokowania
        if (tick - stuckCheck >= 30) {
            if (p.getPos().squaredDistanceTo(stuckPos) < 0.2) {
                stuckCount++;
                release(mc);
                if (stuckCount >= 3) {
                    if (target != null) markVisited(mc.world, target.pos(), 20 * 60);
                    stuckCount = 0;
                    target = null;
                }
                state = State.SEARCHING;
                return;
            }
            stuckCount = 0;
            stuckPos = p.getPos();
            stuckCheck = tick;
        }
        steer(mc, p, c);
    }

    private static void steer(MinecraftClient mc, ClientPlayerEntity p, Config c) {
        BlockPos n = path.get(pathIdx);
        double dx = n.getX() + 0.5 - p.getX();
        double dz = n.getZ() + 0.5 - p.getZ();
        double hd = Math.sqrt(dx * dx + dz * dz);

        if (hd < 0.35 && Math.abs(p.getY() - n.getY()) < 1.1) {
            pathIdx++;
            if (pathIdx >= path.size()) { release(mc); state = State.SEARCHING; }
            return;
        }

        float want = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f;
        float diff = MathHelper.wrapDegrees(want - p.getYaw());
        p.setYaw(p.getYaw() + MathHelper.clamp(diff, -40f, 40f));
        p.setPitch(MathHelper.lerp(0.3f, p.getPitch(), 8f));

        boolean forward = Math.abs(diff) < 50f;
        boolean jump = p.isOnGround() && ((n.getY() > p.getY() + 0.1 && hd < 1.4) || (p.horizontalCollision && forward));
        mc.options.forwardKey.setPressed(forward);
        mc.options.sprintKey.setPressed(forward && c.sprint);
        mc.options.jumpKey.setPressed(jump);
        keysHeld = true;
    }

    private static void beginOpen() {
        state = State.OPENING;
        openStart = tick;
        stuckCount = 0;
        setStatus("Otwieram skrzynkę");
    }

    private static void doOpen(MinecraftClient mc, ClientPlayerEntity p, Config c) {
        if (target == null) { state = State.SEARCHING; return; }

        if (mc.currentScreen instanceof GenericContainerScreen && p.currentScreenHandler instanceof GenericContainerScreenHandler) {
            state = State.LOOTING;
            lootStart = tick;
            nextClick = tick + 3;      // chwila na zsynchronizowanie zawartosci
            lastClicked = -1;
            skip.clear();
            setStatus("Zabieram przedmioty");
            return;
        }
        if (!canReach(p, target.pos(), c)) { state = State.SEARCHING; return; }

        int dt = tick - openStart;
        Vec3d eye = p.getEyePos();
        Vec3d ctr = Vec3d.ofCenter(target.pos());
        if (dt <= 1) {
            double dx = ctr.x - eye.x, dy = ctr.y - eye.y, dz = ctr.z - eye.z;
            double hd = Math.sqrt(dx * dx + dz * dz);
            p.setYaw((float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90f);
            p.setPitch((float) -(Math.atan2(dy, hd) * 180.0 / Math.PI));
        }
        if (dt == 2 || dt == 25) {
            Direction face = Direction.getFacing(eye.x - ctr.x, eye.y - ctr.y, eye.z - ctr.z);
            Vec3d hit = ctr.add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, new BlockHitResult(hit, face, target.pos(), false));
            p.swingHand(Hand.MAIN_HAND);
        }
        if (dt > 50) {      // skrzynka nie chce sie otworzyc (cooldown / zablokowana)
            markVisited(mc.world, target.pos(), c.chestCooldownSec * 20);
            target = null;
            state = State.SEARCHING;
        }
    }

    private static void doLoot(MinecraftClient mc, ClientPlayerEntity p, Config c) {
        if (!(mc.currentScreen instanceof GenericContainerScreen)
                || !(p.currentScreenHandler instanceof GenericContainerScreenHandler)) {
            finishChest(mc, p, c, false, false);
            return;
        }
        GenericContainerScreenHandler h = (GenericContainerScreenHandler) p.currentScreenHandler;
        if (tick < nextClick) return;

        int size = h.getRows() * 9;
        if (lastClicked >= 0 && h.getSlot(lastClicked).hasStack()) skip.add(lastClicked); // nie zmiescilo sie
        lastClicked = -1;

        int idx = -1;
        for (int i = 0; i < size; i++) {
            if (h.getSlot(i).hasStack() && !skip.contains(i)) { idx = i; break; }
        }
        if (idx < 0 || tick - lootStart > 20 * 30) {
            finishChest(mc, p, c, true, !skip.isEmpty());
            return;
        }
        mc.interactionManager.clickSlot(h.syncId, idx, 0, SlotActionType.QUICK_MOVE, p);
        lastClicked = idx;
        nextClick = tick + Math.max(1, c.lootDelay);
    }

    private static void finishChest(MinecraftClient mc, ClientPlayerEntity p, Config c, boolean close, boolean leftover) {
        if (close && p.currentScreenHandler != p.playerScreenHandler) p.closeHandledScreen();
        if (target != null) {
            boolean full = freeSlots(p) <= c.minFreeSlots;
            // jesli cos zostalo bo EQ pelny - wrocimy po sprzedazy; w innym wypadku blokujemy skrzynke
            if (!leftover || !full) markVisited(mc.world, target.pos(), c.chestCooldownSec * 20);
            if (!leftover) chestsLooted++;
        }
        target = null;
        state = State.SEARCHING;
    }

    private static void beginSell() {
        state = State.SELLING;
        sellStage = 0;
        sellTick = tick;
    }

    private static void doSell(MinecraftClient mc, ClientPlayerEntity p, Config c) {
        if (sellStage == 0) {
            if (p.currentScreenHandler != p.playerScreenHandler) p.closeHandledScreen();
            p.networkHandler.sendChatCommand(stripSlash(c.sellCommand));
            sellStage = 1;
            sellTick = tick;
            setStatus("Sprzedaję: /" + stripSlash(c.sellCommand));
            return;
        }
        if (tick - sellTick < c.sellWaitTicks) return;
        if (p.currentScreenHandler != p.playerScreenHandler) p.closeHandledScreen();

        if (freeSlots(p) > c.minFreeSlots) {
            sells++;
            sellTries = 0;
            state = State.SEARCHING;
        } else {
            sellTries++;
            if (sellTries >= 3) stop("Sprzedaż nie zwolniła miejsca w ekwipunku (3 próby)");
            else sellStage = 0;
        }
    }

    private static void goSpawn(MinecraftClient mc, ClientPlayerEntity p, String reason) {
        release(mc);
        if (p.currentScreenHandler != p.playerScreenHandler) p.closeHandledScreen();
        String cmd = stripSlash(Config.get().spawnCommand);
        chat("§e" + reason + " §7-> §f/" + cmd);
        p.networkHandler.sendChatCommand(cmd);
        enabled = false;
        state = State.IDLE;
        path = null;
        target = null;
        chat("§cBot zatrzymany (zakończono). Zebrane skrzynki: §f" + chestsLooted + "§c, sprzedaże: §f" + sells);
    }

    // ------------------------------------------------------------------ POMOCNICZE

    private static ChestScanner.Target findNearest(MinecraftClient mc, ClientPlayerEntity p, Config c) {
        ChestScanner.Target best = null;
        double bd = Double.MAX_VALUE;
        double r2 = (double) c.searchRadius * c.searchRadius;
        Vec3d pp = p.getPos();
        for (ChestScanner.Target t : ChestScanner.getTargets()) {
            if (!allowed(t.kind(), c)) continue;
            if (isVisited(t.pos())) continue;
            double d = pp.squaredDistanceTo(Vec3d.ofCenter(t.pos()));
            if (d > r2 || d >= bd) continue;
            BlockPos up = t.pos().up();
            if (mc.world.getBlockState(up).isSolidBlock(mc.world, up)) continue; // zablokowana od gory
            best = t;
            bd = d;
        }
        return best;
    }

    private static boolean allowed(ChestScanner.Kind k, Config c) {
        return switch (k) {
            case CHEST, TRAPPED -> c.useChests;
            case BARREL -> c.useBarrels;
            default -> false;
        };
    }

    private static boolean isTargetBlock(ClientWorld w, BlockPos pos) {
        var b = w.getBlockState(pos).getBlock();
        return b instanceof ChestBlock || b instanceof BarrelBlock;
    }

    private static boolean canReach(ClientPlayerEntity p, BlockPos pos, Config c) {
        return p.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) <= c.reach * c.reach;
    }

    private static void markVisited(ClientWorld w, BlockPos pos, int ticks) {
        visited.put(pos.asLong(), tick + ticks);
        BlockState st = w.getBlockState(pos);
        if (st.getBlock() instanceof ChestBlock && st.contains(ChestBlock.CHEST_TYPE)
                && st.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            visited.put(pos.offset(ChestBlock.getFacing(st)).asLong(), tick + ticks);   // druga polowa duzej skrzyni
        }
    }

    private static void release(MinecraftClient mc) {
        if (keysHeld) {
            mc.options.forwardKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);
            keysHeld = false;
        }
    }

    private static String stripSlash(String s) {
        s = s == null ? "" : s.trim();
        while (s.startsWith("/")) s = s.substring(1);
        return s;
    }

    private static void setStatus(String s) { status = s; }

    public static void chat(String msg) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(Text.literal("§8[§aChestBot§8] §r" + msg));
    }
}
