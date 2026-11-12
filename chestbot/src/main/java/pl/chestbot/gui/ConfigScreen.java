package pl.chestbot.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import pl.chestbot.Bot;
import pl.chestbot.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Menu konfiguracji (domyslnie Right Shift). */
public class ConfigScreen extends Screen {
    private static final int W = 392, H = 262, COLW = 178, GAP = 12;
    private static final int ACCENT = 0xFF4ADE80;
    private static final String[] TABS = {"Bot", "Komendy i ochrona", "Chest ESP", "Kolory ESP"};
    private static int lastTab = 0;

    private int tab = lastTab;
    private int px, py;
    private final List<Label> labels = new ArrayList<>();

    private record Label(int x, int y, String text, int color) {}

    public ConfigScreen() { super(Text.literal("ChestBot")); }

    private int cx(int col) { return px + 16 + col * (COLW + GAP); }
    private int ry(double row) { return py + 56 + (int) (row * 24); }

    @Override
    protected void init() {
        labels.clear();
        px = (width - W) / 2;
        py = (height - H) / 2;

        int tw = (W - 32 - 9) / 4;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            addDrawableChild(new FlatButton(px + 16 + i * (tw + 3), py + 28, tw, 18, TABS[i], b -> {
                tab = idx;
                lastTab = idx;
                clearChildren();
                init();
            }).selected(() -> tab == idx));
        }
        addDrawableChild(new FlatButton(px + W - 22, py + 4, 16, 14, "X", b -> close()));

        switch (tab) {
            case 0 -> botTab();
            case 1 -> cmdTab();
            case 2 -> espTab();
            default -> colorTab();
        }
    }

    // ---------------------------------------------------------------- zakladki

    private void botTab() {
        Config c = Config.get();
        addDrawableChild(new FlatButton(cx(0), ry(0), COLW, 20, "Auto-zbieranie", b -> Bot.toggle()).toggle(Bot::isEnabled));
        addDrawableChild(new FlatButton(cx(1), ry(0), COLW, 20, "Wyczyść odwiedzone skrzynki", b -> Bot.clearVisited()));

        slider(0, 1, "Zasięg szukania", 8, 128, 4, c.searchRadius, " m", v -> c.searchRadius = (int) Math.round(v));
        slider(1, 1, "Zasięg otwierania", 2.5, 4.8, 0.1, c.reach, " m", v -> c.reach = v);
        slider(0, 2, "Opóźnienie zabierania", 1, 10, 1, c.lootDelay, " t", v -> c.lootDelay = (int) Math.round(v));
        slider(1, 2, "Cooldown skrzynki", 10, 1800, 10, c.chestCooldownSec, " s", v -> c.chestCooldownSec = (int) Math.round(v));
        toggle(0, 3, "Skrzynki", () -> c.useChests, v -> c.useChests = v);
        toggle(1, 3, "Beczki", () -> c.useBarrels, v -> c.useBarrels = v);
        toggle(0, 4, "Sprint", () -> c.sprint, v -> c.sprint = v);
        toggle(1, 4, "Eksploracja bez skrzynek", () -> c.explore, v -> c.explore = v);
        toggle(0, 5, "Auto-sprzedaż", () -> c.autoSell, v -> c.autoSell = v);
        slider(1, 5, "Min. wolnych slotów", 0, 8, 1, c.minFreeSlots, "", v -> c.minFreeSlots = (int) Math.round(v));
    }

    private void cmdTab() {
        Config c = Config.get();
        field(0, 0, "Komenda sprzedaży (bez /)", c.sellCommand, s -> c.sellCommand = s);
        field(1, 0, "Komenda spawn (bez /)", c.spawnCommand, s -> c.spawnCommand = s);
        field(0, 1.5, "Słowo kluczowe ochrony (puste = dowolny tekst)", c.protectionKeyword, s -> c.protectionKeyword = s);
        toggle(1, 1.5, "Kończ po ochronie", () -> c.requireProtection, v -> c.requireProtection = v);
        slider(0, 3, "Ochrona zniknęła po", 1, 15, 0.5, c.protectionTimeoutSec, " s", v -> c.protectionTimeoutSec = v);
        slider(1, 3, "Czekaj na ochronę przy starcie", 1, 30, 1, c.protectionStartGraceSec, " s", v -> c.protectionStartGraceSec = v);
        slider(0, 4, "Czekanie po /sell", 10, 100, 5, c.sellWaitTicks, " t", v -> c.sellWaitTicks = (int) Math.round(v));
        slider(1, 4, "/spawn gdy zostało", 1, 30, 1, c.spawnAtSeconds, " s", v -> c.spawnAtSeconds = v);
        slider(0, 5, "Stój po /spawn", 3, 30, 1, c.spawnWaitSec, " s", v -> c.spawnWaitSec = v);
    }

    private void espTab() {
        Config c = Config.get();
        toggle(0, 0, "Chest ESP", () -> c.espEnabled, v -> c.espEnabled = v);
        toggle(1, 0, "Tracery (linie)", () -> c.espTracers, v -> c.espTracers = v);
        toggle(0, 1, "Skrzynki", () -> c.espChests, v -> c.espChests = v);
        toggle(1, 1, "Ender chesty", () -> c.espEnder, v -> c.espEnder = v);
        toggle(0, 2, "Beczki", () -> c.espBarrels, v -> c.espBarrels = v);
        toggle(1, 2, "Shulkery", () -> c.espShulkers, v -> c.espShulkers = v);
        toggle(0, 3, "Wypełnienie", () -> c.espFill, v -> c.espFill = v);
        toggle(1, 3, "Obrys", () -> c.espOutline, v -> c.espOutline = v);
        slider(0, 4, "Zasięg ESP", 16, 256, 8, c.espRange, " m", v -> c.espRange = (int) Math.round(v));
        slider(1, 4, "Grubość linii", 1, 6, 0.5, c.espLineWidth, "", v -> c.espLineWidth = v);
        slider(0, 5, "Przezroczystość wypełn.", 5, 80, 5, c.espFillAlpha, " %", v -> c.espFillAlpha = (int) Math.round(v));
        toggle(1, 5, "Odwiedzone innym kolorem", () -> c.espHighlightVisited, v -> c.espHighlightVisited = v);
    }

    private void colorTab() {
        Config c = Config.get();
        labels.add(new Label(cx(0), ry(0) + 4, "Kolor skrzynek", ACCENT));
        labels.add(new Label(cx(1), ry(0) + 4, "Kolor odwiedzonych", ACCENT));
        slider(0, 1, "R", 0, 255, 1, (c.espColor >> 16) & 255, "", v -> c.espColor = (c.espColor & 0x00FFFF) | ((int) Math.round(v) << 16));
        slider(0, 2, "G", 0, 255, 1, (c.espColor >> 8) & 255, "", v -> c.espColor = (c.espColor & 0xFF00FF) | ((int) Math.round(v) << 8));
        slider(0, 3, "B", 0, 255, 1, c.espColor & 255, "", v -> c.espColor = (c.espColor & 0xFFFF00) | (int) Math.round(v));
        slider(1, 1, "R", 0, 255, 1, (c.espVisitedColor >> 16) & 255, "", v -> c.espVisitedColor = (c.espVisitedColor & 0x00FFFF) | ((int) Math.round(v) << 16));
        slider(1, 2, "G", 0, 255, 1, (c.espVisitedColor >> 8) & 255, "", v -> c.espVisitedColor = (c.espVisitedColor & 0xFF00FF) | ((int) Math.round(v) << 8));
        slider(1, 3, "B", 0, 255, 1, c.espVisitedColor & 255, "", v -> c.espVisitedColor = (c.espVisitedColor & 0xFFFF00) | (int) Math.round(v));
    }

    // ---------------------------------------------------------------- helpery

    private void toggle(int col, double row, String label, BooleanSupplier get, Consumer<Boolean> set) {
        addDrawableChild(new FlatButton(cx(col), ry(row), COLW, 20, label, b -> set.accept(!get.getAsBoolean())).toggle(get));
    }

    private void slider(int col, double row, String label, double min, double max, double step, double cur,
                        String unit, Consumer<Double> set) {
        addDrawableChild(new FlatSlider(cx(col), ry(row), COLW, 20, label, min, max, step, cur, unit, set));
    }

    private void field(int col, double row, String label, String value, Consumer<String> set) {
        labels.add(new Label(cx(col), ry(row), label, 0xFFAAAAAA));
        TextFieldWidget tf = new TextFieldWidget(textRenderer, cx(col), ry(row) + 11, COLW, 16, Text.literal(label));
        tf.setMaxLength(64);
        tf.setText(value == null ? "" : value);
        tf.setChangedListener(set);
        addDrawableChild(tf);
    }

    // ---------------------------------------------------------------- rysowanie

    /** Rysujemy tlo + panel tutaj, zeby widgety (rysowane po tym) byly na wierzchu. */
    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        Config c = Config.get();
        ctx.fill(0, 0, width, height, 0x88000000);
        ctx.fill(px - 1, py - 1, px + W + 1, py + H + 1, ACCENT);
        ctx.fill(px, py, px + W, py + H, 0xF0101418);
        ctx.fill(px, py, px + W, py + 24, 0xFF0B0E11);
        ctx.drawTextWithShadow(textRenderer, "ChestBot", px + 16, py + 8, ACCENT);
        ctx.drawTextWithShadow(textRenderer, "1.21.1 Fabric", px + 16 + textRenderer.getWidth("ChestBot") + 6, py + 8, 0xFF777777);

        for (Label l : labels) ctx.drawTextWithShadow(textRenderer, l.text, l.x, l.y, l.color);

        int sy = py + H - 52;
        if (tab == 0) {
            ctx.fill(px + 16, sy - 4, px + W - 16, sy - 3, 0xFF26303A);
            ctx.drawTextWithShadow(textRenderer, "Status: " + Bot.stateLabel() + (Bot.statusText().isEmpty() ? "" : "  -  " + Bot.statusText()), px + 16, sy, 0xFFFFFFFF);
            String free = client != null && client.player != null ? String.valueOf(Bot.freeSlots(client.player)) : "?";
            ctx.drawTextWithShadow(textRenderer, "Skrzynki: " + Bot.chestsLooted + "   Sprzedaże: " + Bot.sells + "   Wolne sloty: " + free, px + 16, sy + 12, 0xFFAAAAAA);
            ctx.drawTextWithShadow(textRenderer, "Klawisze: Right Shift = menu, J = bot, H = ESP (zmienisz w Sterowaniu)", px + 16, sy + 28, 0xFF666666);
        } else if (tab == 1) {
            ctx.fill(px + 16, sy - 4, px + W - 16, sy - 3, 0xFF26303A);
            String o = Bot.lastOverlay.isEmpty() ? "(jeszcze nic nie odebrano)" : Bot.lastOverlay;
            if (o.length() > 52) o = o.substring(0, 52) + "...";
            ctx.drawTextWithShadow(textRenderer, "Ostatni tekst nad hotbarem: " + o, px + 16, sy, 0xFFFFFFFF);
            ctx.drawTextWithShadow(textRenderer, "Wykryta ochrona: " + (Bot.protectionSeenNow() ? "§aTAK" : "§cNIE") + "  §7(" + Bot.protectionLabel() + ")", px + 16, sy + 12, 0xFFAAAAAA);
            ctx.drawTextWithShadow(textRenderer, "Skopiuj fragment tekstu ochrony do pola 'słowo kluczowe'.", px + 16, sy + 28, 0xFF666666);
        } else if (tab == 3) {
            ctx.fill(cx(0), ry(4.2), cx(0) + COLW, ry(4.2) + 24, 0xFF000000 | c.espColor);
            ctx.fill(cx(1), ry(4.2), cx(1) + COLW, ry(4.2) + 24, 0xFF000000 | c.espVisitedColor);
        }
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void removed() { Config.save(); }
}
