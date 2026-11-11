package pl.chestbot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/** Maly panel statusu w lewym gornym rogu gdy bot dziala. */
public final class Hud {
    private Hud() {}

    public static void render(DrawContext ctx, RenderTickCounter tc) {
        if (!Bot.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden || mc.player == null) return;
        TextRenderer tr = mc.textRenderer;
        Config c = Config.get();

        String l1 = "ChestBot: " + Bot.stateLabel();
        String l2 = "Skrzynki: " + Bot.chestsLooted + "   Sprzedaże: " + Bot.sells + "   Wolne: " + Bot.freeSlots(mc.player);
        String l3 = c.requireProtection ? "Ochrona: " + Bot.protectionLabel() : "Ochrona: ignorowana";
        int w = Math.max(tr.getWidth(l1), Math.max(tr.getWidth(l2), tr.getWidth(l3))) + 12;
        ctx.fill(4, 4, 4 + w, 44, 0xA0101418);
        ctx.fill(4, 4, 6, 44, 0xFF4ADE80);
        ctx.drawTextWithShadow(tr, l1, 10, 8, 0xFF4ADE80);
        ctx.drawTextWithShadow(tr, l2, 10, 20, 0xFFFFFFFF);
        ctx.drawTextWithShadow(tr, l3, 10, 32, 0xFFAAAAAA);
    }
}
