package pl.chestbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Konfiguracja zapisywana w .minecraft/config/chestbot.json */
public class Config {
    private static Config INSTANCE = new Config();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ---------- BOT ----------
    public int searchRadius = 48;          // jak daleko szukac skrzynek (bloki)
    public double reach = 4.0;             // z jakiej odleglosci otwierac skrzynke
    public int lootDelay = 2;              // opoznienie miedzy zabieraniem slotow (ticki)
    public int chestCooldownSec = 600;     // po ilu sekundach wolno wrocic do tej samej skrzynki
    public boolean useChests = true;
    public boolean useBarrels = true;
    public boolean sprint = true;
    public boolean explore = true;         // chodz losowo gdy nie ma skrzynek w poblizu
    public boolean autoSell = true;
    public int minFreeSlots = 0;           // ekwipunek "pelny" gdy wolnych slotow <= tej wartosci

    // ---------- KOMENDY / OCHRONA ----------
    public String sellCommand = "sell all";
    public String spawnCommand = "spawn";
    public int sellWaitTicks = 30;
    public boolean autoConfirmSell = true;       // gdy serwer prosi o potwierdzenie (np. >10k) -> wpisz komende potwierdzenia
    public String sellConfirmKeyword = "sell confirm"; // fragment wiadomosci na czacie, ktory oznacza prosbe o potwierdzenie
    public String sellConfirmCommand = "sell confirm";  // komenda potwierdzajaca (bez /)
    public boolean requireProtection = true;
    public String protectionKeyword = "ochron";   // pusty = dowolny tekst nad hotbarem
    public double protectionTimeoutSec = 3.0;     // po ilu s braku tekstu uznajemy ze ochrona sie skonczyla
    public double protectionStartGraceSec = 8.0;  // ile s czekac na pojawienie sie ochrony po starcie
    public double spawnAtSeconds = 8.0;           // wyslij /spawn gdy do konca ochrony zostalo <= tyle sekund
    public double spawnWaitSec = 12.0;            // ile s stac nieruchomo po /spawn (teleport sie anuluje przy ruchu)

    // ---------- ESP ----------
    public boolean espEnabled = false;
    public boolean espChests = true;
    public boolean espEnder = true;
    public boolean espBarrels = true;
    public boolean espShulkers = true;
    public boolean espFill = true;
    public boolean espOutline = true;
    public boolean espTracers = false;
    public boolean espHighlightVisited = true;
    public int espRange = 128;
    public int espFillAlpha = 30;      // %
    public double espLineWidth = 2.0;
    public int espColor = 0x2BFF7A;
    public int espVisitedColor = 0xFF4444;

    public static Config get() { return INSTANCE; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("chestbot.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), Config.class);
                if (c != null) INSTANCE = c;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        save();
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(INSTANCE), StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
