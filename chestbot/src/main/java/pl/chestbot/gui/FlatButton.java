package pl.chestbot.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/** Plaski przycisk: zwykly, przelacznik (ON/OFF) albo zakladka. */
public class FlatButton extends ButtonWidget {
    private final String label;
    private BooleanSupplier state;
    private BooleanSupplier selected;

    public FlatButton(int x, int y, int w, int h, String label, PressAction onPress) {
        super(x, y, w, h, Text.literal(label), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.label = label;
    }

    public FlatButton toggle(BooleanSupplier s) { this.state = s; return this; }
    public FlatButton selected(BooleanSupplier s) { this.selected = s; return this; }

    @Override
    protected void renderWidget(DrawContext ctx, int mx, int my, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hov = isHovered();
        int bg, bar;
        if (state != null) {
            boolean on = state.getAsBoolean();
            bg = on ? (hov ? 0xFF266B47 : 0xFF1F5A3C) : (hov ? 0xFF3A2A30 : 0xFF2B2025);
            bar = on ? 0xFF4ADE80 : 0xFFE5484D;
        } else if (selected != null) {
            boolean sel = selected.getAsBoolean();
            bg = sel ? 0xFF1E3A5F : (hov ? 0xFF222C38 : 0xFF182028);
            bar = sel ? 0xFF4ADE80 : 0;
        } else {
            bg = hov ? 0xFF2A3A4A : 0xFF212B36;
            bar = 0xFF5B9BD5;
        }
        ctx.fill(x, y, x + w, y + h, bg);
        if (bar != 0) ctx.fill(x, y, x + 2, y + h, bar);
        String text = state != null ? label + ": " + (state.getAsBoolean() ? "§aON" : "§cOFF") : label;
        ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, text, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }
}
