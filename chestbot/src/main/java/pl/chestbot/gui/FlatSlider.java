package pl.chestbot.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.Locale;
import java.util.function.Consumer;

public class FlatSlider extends SliderWidget {
    private final String label, unit;
    private final double min, max, step;
    private final Consumer<Double> setter;

    public FlatSlider(int x, int y, int w, int h, String label, double min, double max, double step,
                      double cur, String unit, Consumer<Double> setter) {
        super(x, y, w, h, Text.empty(), MathHelper.clamp((cur - min) / (max - min), 0, 1));
        this.label = label;
        this.min = min;
        this.max = max;
        this.step = step;
        this.unit = unit;
        this.setter = setter;
        updateMessage();
    }

    private double real() {
        double v = min + value * (max - min);
        if (step > 0) v = Math.round(v / step) * step;
        return MathHelper.clamp(v, min, max);
    }

    private String fmt(double v) {
        return step >= 1 ? String.valueOf((int) Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
    }

    @Override
    protected void updateMessage() {
        setMessage(Text.literal(label + ": " + fmt(real()) + unit));
    }

    @Override
    protected void applyValue() {
        setter.accept(real());
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mx, int my, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        ctx.fill(x, y, x + w, y + h, isHovered() ? 0xFF2A3440 : 0xFF212B36);
        double frac = MathHelper.clamp((real() - min) / (max - min), 0, 1);
        int fw = (int) (w * frac);
        ctx.fill(x, y, x + fw, y + h, 0x663ECF8E);
        ctx.fill(x + Math.max(0, fw - 2), y, x + Math.max(2, fw), y + h, 0xFF4ADE80);
        ctx.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }
}
