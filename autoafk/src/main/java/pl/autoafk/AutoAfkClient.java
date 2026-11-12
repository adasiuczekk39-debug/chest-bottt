package pl.autoafk;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Random;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class AutoAfkClient implements ClientModInitializer {

    enum State { FISHING, HUB_SENT, HUB_DELAY, WAIT_GUI, CLICK, WAIT_ARRIVAL, SETTLE, RECHECK, TRASH, DISCONNECT }

    static final Random RND = new Random();
    static Config cfg;
    static KeyBinding toggleKey;

    static boolean running = false;
    static State state = State.FISHING;
    static int timer = 0;
    static ClientWorld worldRef = null;

    // fishing
    static int reelTimer = 0, recastTimer = 0, idle = 0;

    // trash / books
    static final ArrayDeque<Integer> trashQueue = new ArrayDeque<>();
    static int fullCheck = 0, bookCheck = 0;

    // mouse
    static int mouseTotal = 0, mouseElapsed = 0, mouseCooldown = 40;
    static float mousePrevS = 0, mouseDYaw = 0, mouseDPitch = 0, baseYaw = 0, basePitch = 0;

    @Override
    public void onInitializeClient() {
        cfg = Config.load();

        toggleKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("AutoAFK wlacz/wylacz", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, "AutoAFK"));

        ClientTickEvents.END_CLIENT_TICK.register(AutoAfkClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> running = false);
        registerCommands();
    }

    // ------------------------------------------------------------ start/stop
    static void start(MinecraftClient mc) {
        running = true;
        state = State.FISHING;
        reelTimer = 0; recastTimer = 10; idle = 0;
        mouseTotal = 0; mouseCooldown = 40;
        if (mc.player != null) { baseYaw = mc.player.getYaw(); basePitch = mc.player.getPitch(); }
        msg(mc, "§aAutoAFK WLACZONY");
    }

    static void stop(MinecraftClient mc) {
        running = false;
        msg(mc, "§cAutoAFK WYLACZONY");
    }

    static void msg(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal(s), false);
    }

    // ------------------------------------------------------------ main tick
    static void tick(MinecraftClient mc) {
        while (toggleKey.wasPressed()) {
            if (running) stop(mc); else start(mc);
        }
        if (!running) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // ksiazka z szukanym enchantem -> ESC + disconnect (dziala w kazdym stanie)
        if (state != State.DISCONNECT && cfg.bookWatch && ++bookCheck % 4 == 0) {
            String found = findWantedBook(mc);
            if (found != null) {
                msg(mc, "§6Mam ksiazke: " + found + " -> ESC + disconnect");
                trashQueue.clear();
                mc.setScreen(new GameMenuScreen(true));     // "klika escape"
                state = State.DISCONNECT;
                timer = 10 + RND.nextInt(15);
                return;
            }
        }

        switch (state) {
            case FISHING -> fishingTick(mc);

            case DISCONNECT -> {
                if (--timer <= 0) {
                    running = false;
                    mc.world.disconnect();                    // to samo co przycisk "Disconnect"
                    mc.disconnect();
                    mc.setScreen(new MultiplayerScreen(new TitleScreen()));
                }
            }

            case TRASH -> {
                if (--timer > 0) return;
                Integer id = trashQueue.poll();
                if (id == null) {                             // koniec wyrzucania
                    state = State.FISHING;
                    reelTimer = 0; recastTimer = 0; idle = 0;
                    return;
                }
                mc.interactionManager.clickSlot(0, id, 1, SlotActionType.THROW, mc.player); // caly stack
                timer = 2 + RND.nextInt(4);
            }

            case HUB_SENT -> {
                timer++;
                if (mc.world != worldRef) {                 // weszlismy na hub
                    state = State.HUB_DELAY;
                    timer = Math.max(1, cfg.hubDelaySeconds * 20);
                } else if (timer > 200) {                   // /hub nie zadzialalo -> ponow
                    startHub(mc);
                }
            }

            case HUB_DELAY -> {
                if (--timer <= 0) {
                    // wybierz slot zegarka, PPM
                    mc.player.getInventory().selectedSlot = cfg.watchSlot - 1;
                    use(mc);
                    state = State.WAIT_GUI;
                    timer = 0;
                }
            }

            case WAIT_GUI -> {
                timer++;
                if (mc.currentScreen instanceof HandledScreen<?>) {
                    state = State.CLICK;
                    timer = 6 + RND.nextInt(8);             // chwila "na zastanowienie"
                } else if (timer > 60) {                    // menu sie nie otworzylo -> sprobuj ponownie
                    state = State.HUB_DELAY;
                    timer = 10;
                }
            }

            case CLICK -> {
                if (!(mc.currentScreen instanceof HandledScreen<?> screen)) {
                    state = State.HUB_DELAY; timer = 10; return;
                }
                if (--timer > 0) return;
                ScreenHandler h = screen.getScreenHandler();
                Slot target = findMenuSlot(h);
                if (target != null) {
                    // LPM na przedmiocie
                    mc.interactionManager.clickSlot(h.syncId, target.id, 0, SlotActionType.PICKUP, mc.player);
                    worldRef = mc.world;
                    state = State.WAIT_ARRIVAL;
                    timer = 0;
                } else {
                    timer = -1;                              // szukaj dalej (itemy moga sie doladowac)
                    if (++idle > 60) {                       // nie ma itemu -> zamknij i ponow
                        idle = 0;
                        mc.player.closeHandledScreen();
                        state = State.HUB_DELAY; timer = 20;
                    } else {
                        timer = 1;
                    }
                }
            }

            case WAIT_ARRIVAL -> {
                timer++;
                if (mc.world != worldRef) {
                    state = State.SETTLE;
                    timer = 40 + RND.nextInt(10);            // ~2s zeby encje sie zaladowaly
                } else if (timer > 300) {
                    state = State.HUB_DELAY; timer = 20;
                }
            }

            case SETTLE -> {
                if (--timer > 0) return;
                AbstractClientPlayerEntity n = nearby(mc);
                if (n != null) {
                    msg(mc, "§eNadal tam jest: " + n.getGameProfile().getName() + " -> /hub za chwile");
                    state = State.RECHECK;
                    timer = cfg.recheckSeconds * 20;
                } else {
                    msg(mc, "§aPuste -> wracam do lowienia");
                    resumeFishing(mc);
                }
            }

            case RECHECK -> {
                if (--timer <= 0) startHub(mc);
            }
        }
    }

    // ------------------------------------------------------------ fishing
    static void fishingTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;

        if (cfg.autoLogout) {
            AbstractClientPlayerEntity n = nearby(mc);
            if (n != null) {
                msg(mc, "§cGracz " + n.getGameProfile().getName() + " blisko -> /hub");
                startHub(mc);
                return;
            }
        }

        if (cfg.autoTrash && ++fullCheck % 10 == 0 && inventoryFull(p)) {
            if (startTrash(mc)) return;
        }

        if (cfg.randomMouse) mouseTick(p);
        if (!cfg.autoFish) return;

        if (reelTimer > 0) {
            if (--reelTimer == 0) {
                use(mc);                                   // zwin
                recastTimer = 10 + RND.nextInt(25);        // losowa przerwa przed rzutem
            }
            return;
        }
        if (recastTimer > 0) {
            if (--recastTimer == 0) cast(mc);
            return;
        }
        if (p.fishHook == null) {                          // zabezpieczenie: brak splawika
            if (++idle > 30 + RND.nextInt(30)) { idle = 0; cast(mc); }
        } else {
            idle = 0;
        }
    }

    /** Wywolywane z mixina gdy przyjdzie dzwiek. */
    public static void onSound(PlaySoundS2CPacket packet) {
        if (!running || state != State.FISHING || cfg == null || !cfg.autoFish) return;
        if (packet.getSound().value() != SoundEvents.ENTITY_FISHING_BOBBER_SPLASH) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        FishingBobberEntity hook = mc.player.fishHook;
        if (hook == null) return;
        if (hook.squaredDistanceTo(packet.getX(), packet.getY(), packet.getZ()) > 4.0) return;
        if (reelTimer > 0 || recastTimer > 0) return;
        reelTimer = 4 + RND.nextInt(10);                   // 0.2-0.7 s "czasu reakcji"
    }

    static void cast(MinecraftClient mc) {
        mc.player.getInventory().selectedSlot = cfg.rodSlot - 1;
        use(mc);
    }

    static void use(MinecraftClient mc) {
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    static void resumeFishing(MinecraftClient mc) {
        state = State.FISHING;
        reelTimer = 0;
        recastTimer = 15 + RND.nextInt(20);
        idle = 0;
        mouseTotal = 0;
        mouseCooldown = 30;
        baseYaw = mc.player.getYaw();
        basePitch = mc.player.getPitch();
        mc.player.getInventory().selectedSlot = cfg.rodSlot - 1;
    }

    // ------------------------------------------------------------ random mouse
    static void mouseTick(ClientPlayerEntity p) {
        if (mouseTotal > 0) {
            mouseElapsed++;
            float t = Math.min(1f, mouseElapsed / (float) mouseTotal);
            float s = t * t * (3 - 2 * t);                 // smoothstep -> plynny ruch
            float ds = s - mousePrevS;
            mousePrevS = s;
            p.setYaw(p.getYaw() + mouseDYaw * ds);
            p.setPitch(MathHelper.clamp(p.getPitch() + mouseDPitch * ds, -85f, 85f));
            if (mouseElapsed >= mouseTotal) {
                mouseTotal = 0;
                mouseCooldown = 30 + RND.nextInt(150);     // 1.5 - 9 s przerwy
            }
        } else if (--mouseCooldown <= 0) {
            mouseTotal = 6 + RND.nextInt(10);
            mouseElapsed = 0;
            mousePrevS = 0;
            float dy = (RND.nextFloat() * 2 - 1) * 7f;
            float dp = (RND.nextFloat() * 2 - 1) * 3f;
            // trzymaj sie blisko poczatkowego kierunku (zeby nie "odplywac")
            dy = MathHelper.clamp(dy, (baseYaw - 25f) - p.getYaw(), (baseYaw + 25f) - p.getYaw());
            dp = MathHelper.clamp(dp, (basePitch - 8f) - p.getPitch(), (basePitch + 8f) - p.getPitch());
            mouseDYaw = dy;
            mouseDPitch = dp;
        }
    }

    // ------------------------------------------------------------ hub
    static void startHub(MinecraftClient mc) {
        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand("hub");
        worldRef = mc.world;
        state = State.HUB_SENT;
        timer = 0;
        idle = 0;
        reelTimer = 0;
        recastTimer = 0;
    }

    static Slot findMenuSlot(ScreenHandler h) {
        String needle = cfg.menuItemName.toLowerCase(Locale.ROOT);
        int containerSize = h.slots.size() > 36 ? h.slots.size() - 36 : h.slots.size();
        for (int i = 0; i < containerSize; i++) {
            Slot s = h.slots.get(i);
            if (s.hasStack() && s.getStack().getName().getString().toLowerCase(Locale.ROOT).contains(needle)) {
                return s;
            }
        }
        return null;
    }

    static AbstractClientPlayerEntity nearby(MinecraftClient mc) {
        double r2 = cfg.radius * cfg.radius;
        for (AbstractClientPlayerEntity other : mc.world.getPlayers()) {
            if (other == mc.player || other.isSpectator()) continue;
            String name = other.getGameProfile().getName().toLowerCase(Locale.ROOT);
            if (cfg.friends.contains(name)) continue;
            if (mc.player.squaredDistanceTo(other) <= r2) return other;
        }
        return null;
    }

    // ------------------------------------------------------------ trash + books
    static boolean inventoryFull(ClientPlayerEntity p) {
        for (ItemStack st : p.getInventory().main) if (st.isEmpty()) return false;
        return true;
    }

    /** slot w PlayerScreenHandler (syncId 0): hotbar 0-8 -> 36-44, reszta 9-35 -> 9-35 */
    static int screenSlot(int invIndex) {
        return invIndex < 9 ? 36 + invIndex : invIndex;
    }

    static boolean startTrash(MinecraftClient mc) {
        PlayerInventory inv = mc.player.getInventory();
        trashQueue.clear();
        for (int i = 0; i < inv.main.size(); i++) {
            ItemStack st = inv.main.get(i);
            if (st.isEmpty()) continue;
            if (i == cfg.rodSlot - 1 || i == cfg.watchSlot - 1) continue;   // wedka i zegarek zostaja
            String id = Registries.ITEM.getId(st.getItem()).toString();
            if (cfg.keepItems.contains(id)) continue;                        // mieso itp. zostaje
            trashQueue.add(screenSlot(i));
        }
        if (trashQueue.isEmpty()) return false;
        msg(mc, "§eEQ pelne -> wyrzucam " + trashQueue.size() + " stackow");
        state = State.TRASH;
        timer = 3;
        return true;
    }

    static String findWantedBook(MinecraftClient mc) {
        if (cfg.wantedBooks.isEmpty()) return null;
        for (ItemStack st : mc.player.getInventory().main) {
            if (!st.isOf(Items.ENCHANTED_BOOK)) continue;
            ItemEnchantmentsComponent c = st.get(DataComponentTypes.STORED_ENCHANTMENTS);
            if (c == null) continue;
            for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e : c.getEnchantmentEntries()) {
                String name = e.getKey().getKey().map(k -> k.getValue().getPath()).orElse("");
                String key = name + ":" + e.getIntValue();
                if (cfg.wantedBooks.contains(key)) return key;
            }
        }
        return null;
    }

    static String bookKey(String alias) {
        return switch (alias.toLowerCase(Locale.ROOT)) {
            case "depth1" -> "depth_strider:1";
            case "depth2" -> "depth_strider:2";
            case "depth3" -> "depth_strider:3";
            case "mending" -> "mending:1";
            default -> null;
        };
    }

    // ------------------------------------------------------------ commands
    static void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> dispatcher.register(
            literal("afk")
                .then(literal("start").executes(c -> { start(MinecraftClient.getInstance()); return 1; }))
                .then(literal("stop").executes(c -> { stop(MinecraftClient.getInstance()); return 1; }))
                .then(literal("fish").then(argument("on", BoolArgumentType.bool()).executes(c -> {
                    cfg.autoFish = BoolArgumentType.getBool(c, "on"); cfg.save();
                    msg(MinecraftClient.getInstance(), "AutoFish: " + cfg.autoFish); return 1; })))
                .then(literal("logout").then(argument("on", BoolArgumentType.bool()).executes(c -> {
                    cfg.autoLogout = BoolArgumentType.getBool(c, "on"); cfg.save();
                    msg(MinecraftClient.getInstance(), "AutoLogout: " + cfg.autoLogout); return 1; })))
                .then(literal("mouse").then(argument("on", BoolArgumentType.bool()).executes(c -> {
                    cfg.randomMouse = BoolArgumentType.getBool(c, "on"); cfg.save();
                    msg(MinecraftClient.getInstance(), "Losowa mysz: " + cfg.randomMouse); return 1; })))
                .then(literal("radius").then(argument("kratki", DoubleArgumentType.doubleArg(1, 128)).executes(c -> {
                    cfg.radius = DoubleArgumentType.getDouble(c, "kratki"); cfg.save();
                    msg(MinecraftClient.getInstance(), "Zasieg: " + cfg.radius + " kratek"); return 1; })))
                .then(literal("hubdelay").then(argument("sekundy", IntegerArgumentType.integer(0, 300)).executes(c -> {
                    cfg.hubDelaySeconds = IntegerArgumentType.getInteger(c, "sekundy"); cfg.save();
                    msg(MinecraftClient.getInstance(), "Czekanie na hubie: " + cfg.hubDelaySeconds + "s"); return 1; })))
                .then(literal("recheck").then(argument("sekundy", IntegerArgumentType.integer(1, 60)).executes(c -> {
                    cfg.recheckSeconds = IntegerArgumentType.getInteger(c, "sekundy"); cfg.save();
                    msg(MinecraftClient.getInstance(), "Ponowny /hub po: " + cfg.recheckSeconds + "s"); return 1; })))
                .then(literal("watchslot").then(argument("slot", IntegerArgumentType.integer(1, 9)).executes(c -> {
                    cfg.watchSlot = IntegerArgumentType.getInteger(c, "slot"); cfg.save();
                    msg(MinecraftClient.getInstance(), "Slot zegarka: " + cfg.watchSlot); return 1; })))
                .then(literal("rodslot").then(argument("slot", IntegerArgumentType.integer(1, 9)).executes(c -> {
                    cfg.rodSlot = IntegerArgumentType.getInteger(c, "slot"); cfg.save();
                    msg(MinecraftClient.getInstance(), "Slot wedki: " + cfg.rodSlot); return 1; })))
                .then(literal("item").then(argument("nazwa", StringArgumentType.greedyString()).executes(c -> {
                    cfg.menuItemName = StringArgumentType.getString(c, "nazwa"); cfg.save();
                    msg(MinecraftClient.getInstance(), "Item w menu: " + cfg.menuItemName); return 1; })))
                .then(literal("friend")
                    .then(literal("add").then(argument("nick", StringArgumentType.word()).executes(c -> {
                        cfg.friends.add(StringArgumentType.getString(c, "nick").toLowerCase(Locale.ROOT)); cfg.save();
                        msg(MinecraftClient.getInstance(), "Dodano przyjaciela"); return 1; })))
                    .then(literal("remove").then(argument("nick", StringArgumentType.word()).executes(c -> {
                        cfg.friends.remove(StringArgumentType.getString(c, "nick").toLowerCase(Locale.ROOT)); cfg.save();
                        msg(MinecraftClient.getInstance(), "Usunieto"); return 1; })))
                    .then(literal("list").executes(c -> {
                        msg(MinecraftClient.getInstance(), "Przyjaciele: " + String.join(", ", cfg.friends)); return 1; })))
                .then(literal("trash").then(argument("on", BoolArgumentType.bool()).executes(c -> {
                    cfg.autoTrash = BoolArgumentType.getBool(c, "on"); cfg.save();
                    msg(MinecraftClient.getInstance(), "Auto-kosz: " + cfg.autoTrash); return 1; })))
                .then(literal("keep")
                    .then(literal("add").then(argument("item", StringArgumentType.greedyString()).executes(c -> {
                        String id = StringArgumentType.getString(c, "item").trim().toLowerCase(Locale.ROOT);
                        if (!id.contains(":")) id = "minecraft:" + id;
                        cfg.keepItems.add(id); cfg.save();
                        msg(MinecraftClient.getInstance(), "Zostaje: " + id); return 1; })))
                    .then(literal("remove").then(argument("item", StringArgumentType.greedyString()).executes(c -> {
                        String id = StringArgumentType.getString(c, "item").trim().toLowerCase(Locale.ROOT);
                        if (!id.contains(":")) id = "minecraft:" + id;
                        cfg.keepItems.remove(id); cfg.save();
                        msg(MinecraftClient.getInstance(), "Usunieto z listy: " + id); return 1; })))
                    .then(literal("list").executes(c -> {
                        msg(MinecraftClient.getInstance(), "Nie wyrzucam: " + String.join(", ", cfg.keepItems)); return 1; })))
                .then(literal("book")
                    .then(literal("on").executes(c -> { cfg.bookWatch = true; cfg.save();
                        msg(MinecraftClient.getInstance(), "Wykrywanie ksiazek: ON"); return 1; }))
                    .then(literal("off").executes(c -> { cfg.bookWatch = false; cfg.save();
                        msg(MinecraftClient.getInstance(), "Wykrywanie ksiazek: OFF"); return 1; }))
                    .then(literal("add").then(argument("enchant", StringArgumentType.word()).executes(c -> {
                        String k = bookKey(StringArgumentType.getString(c, "enchant"));
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (k == null) { msg(mc, "Dostepne: depth1, depth2, depth3, mending"); return 0; }
                        cfg.wantedBooks.add(k); cfg.save(); msg(mc, "Szukam: " + k); return 1; })))
                    .then(literal("remove").then(argument("enchant", StringArgumentType.word()).executes(c -> {
                        String k = bookKey(StringArgumentType.getString(c, "enchant"));
                        MinecraftClient mc = MinecraftClient.getInstance();
                        if (k == null) { msg(mc, "Dostepne: depth1, depth2, depth3, mending"); return 0; }
                        cfg.wantedBooks.remove(k); cfg.save(); msg(mc, "Nie szukam: " + k); return 1; })))
                    .then(literal("list").executes(c -> {
                        msg(MinecraftClient.getInstance(), "Szukane: " + String.join(", ", cfg.wantedBooks)); return 1; })))
                .then(literal("status").executes(c -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    msg(mc, "running=" + running + " state=" + state + " fish=" + cfg.autoFish
                            + " logout=" + cfg.autoLogout + " radius=" + cfg.radius
                            + " hubdelay=" + cfg.hubDelaySeconds + "s watch=" + cfg.watchSlot
                            + " rod=" + cfg.rodSlot + " item=" + cfg.menuItemName);
                    return 1; }))
        ));
    }
}
