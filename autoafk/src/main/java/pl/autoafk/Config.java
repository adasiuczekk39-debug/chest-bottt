package pl.autoafk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public class Config {
    public boolean autoFish = true;
    public boolean autoLogout = true;
    public boolean randomMouse = true;
    public double radius = 20;          // ile kratek od gracza -> /hub
    public int hubDelaySeconds = 5;     // ile czekac po wejsciu na hub zanim uzyje zegarka
    public int watchSlot = 9;           // slot zegarka (1-9)
    public int rodSlot = 1;             // slot wedki (1-9)
    public int recheckSeconds = 3;      // po ilu sek. znowu /hub jesli gracz nadal jest
    public String menuItemName = "chestpvp";
    public Set<String> friends = new LinkedHashSet<>();

    // auto-kosz (wyrzuca wszystko poza keepItems, wedka ze slotu rodSlot i zegarek ze slotu watchSlot)
    public boolean autoTrash = true;
    public Set<String> keepItems = new LinkedHashSet<>(java.util.List.of(
            "minecraft:cooked_beef", "minecraft:beef",
            "minecraft:cooked_porkchop", "minecraft:porkchop",
            "minecraft:cooked_chicken", "minecraft:chicken",
            "minecraft:cooked_mutton", "minecraft:mutton",
            "minecraft:cooked_rabbit", "minecraft:rabbit",
            "minecraft:cooked_cod", "minecraft:cod",
            "minecraft:cooked_salmon", "minecraft:salmon"));

    // wykrywanie ksiazek: klucze typu "depth_strider:3", "mending:1"
    public boolean bookWatch = true;
    public Set<String> wantedBooks = new LinkedHashSet<>(java.util.List.of(
            "depth_strider:3", "mending:1"));

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("autoafk.json");

    public static Config load() {
        try {
            if (Files.exists(PATH)) {
                Config c = GSON.fromJson(Files.readString(PATH), Config.class);
                if (c != null) return c;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Config c = new Config();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.writeString(PATH, GSON.toJson(this));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
