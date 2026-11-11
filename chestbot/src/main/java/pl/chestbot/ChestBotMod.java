package pl.chestbot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import pl.chestbot.gui.ConfigScreen;

public class ChestBotMod implements ClientModInitializer {
    public static KeyBinding KEY_GUI, KEY_BOT, KEY_ESP;

    @Override
    public void onInitializeClient() {
        Config.load();

        KEY_GUI = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chestbot.gui",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.chestbot"));
        KEY_BOT = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chestbot.bot",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, "category.chestbot"));
        KEY_ESP = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.chestbot.esp",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.chestbot"));

        ClientTickEvents.START_CLIENT_TICK.register(Bot::onTick);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (KEY_GUI.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new ConfigScreen());
            }
            while (KEY_BOT.wasPressed()) {
                if (mc.player != null) Bot.toggle();
            }
            while (KEY_ESP.wasPressed()) {
                Config.get().espEnabled = !Config.get().espEnabled;
                Config.save();
                Bot.chat("Chest ESP: " + (Config.get().espEnabled ? "§aON" : "§cOFF"));
            }
        });

        ClientReceiveMessageEvents.GAME.register(Bot::onGameMessage);
        WorldRenderEvents.LAST.register(ChestEsp::render);
        HudRenderCallback.EVENT.register(Hud::render);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Bot.onDisconnect());
    }
}
