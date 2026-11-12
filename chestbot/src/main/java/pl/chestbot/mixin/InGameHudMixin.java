package pl.chestbot.mixin;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.chestbot.Bot;

/** Podsluchuje tekst wyswietlany nad hotbarem (action bar), niezaleznie od tego jak serwer go wysyla. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"))
    private void chestbot$onOverlay(Text message, boolean tinted, CallbackInfo ci) {
        Bot.onOverlay(message == null ? null : message.getString());
    }
}
