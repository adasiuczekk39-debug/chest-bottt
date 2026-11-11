# ChestBot (Fabric 1.21.1)

Mod kliencki: auto-zbieranie skrzynek + auto `/sell all` + `/spawn` po końcu ochrony + Chest ESP + GUI.

## Budowanie
1. Potrzebujesz JDK 21 oraz Gradle 8.8+ (albo skopiuj folder `src` + pliki gradle do oficjalnego szablonu Fabric / otwórz w IntelliJ).
2. W folderze projektu: `gradle wrapper --gradle-version 8.8` (jednorazowo), potem `./gradlew build`.
3. Gotowy plik: `build/libs/chestbot-1.0.0.jar` -> do `.minecraft/mods` razem z Fabric API 0.105.0+1.21.1.

## Użycie
- Right Shift - menu, J - start/stop bota, H - Chest ESP (klawisze zmienisz w Sterowaniu).
- Config zapisuje się w `config/chestbot.json`.
- Zakładka "Komendy i ochrona" pokazuje ostatni tekst nad hotbarem - wpisz jego fragment (np. "ochron") jako słowo kluczowe.
