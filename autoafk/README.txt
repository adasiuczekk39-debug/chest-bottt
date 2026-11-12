AutoAFK - Fabric 1.21.1 (client-side)

BUDOWANIE
 1. Java 21 + Gradle 8.8+ (albo wygeneruj wrapper: `gradle wrapper`).
 2. W folderze projektu: `gradle build`  (albo ./gradlew build)
 3. Gotowy jar: build/libs/autoafk-1.0.0.jar  -> do .minecraft/mods (+ Fabric API 0.102.0+1.21.1)

STEROWANIE
 Klawisz K          - start/stop (zmienisz w ustawieniach klawiszy, kategoria AutoAFK)
 /afk start|stop|status
 /afk fish <true|false>       autofish
 /afk logout <true|false>     auto /hub gdy ktos blisko
 /afk mouse <true|false>      losowe ruchy myszka
 /afk radius <kratki>         ile kratek od gracza -> /hub
 /afk hubdelay <sekundy>      ile czekac na hubie zanim uzyje zegarka
 /afk watchslot <1-9>         slot zegarka
 /afk rodslot <1-9>           slot wedki
 /afk recheck <sekundy>       po ilu sek. znowu /hub jak gosc dalej stoi (domyslnie 3)
 /afk item <nazwa>            nazwa itemu w menu (domyslnie chestpvp)
 /afk friend add|remove|list <nick>
 /afk trash <true|false>      auto-kosz gdy EQ pelne (wyrzuca wszystko poza keep, wedka z rodslot, zegarek z watchslot)
 /afk keep add|remove|list <item>   lista rzeczy ktorych NIE wyrzuca (domyslnie mieso i ryby)
 /afk book on|off             wykrywanie ksiazek z enchantem
 /afk book add|remove <depth1|depth2|depth3|mending>   ktore enchanty szukasz
 /afk book list

PRZEBIEG
 Lowisz -> ktos wchodzi w zasieg -> /hub -> czekasz hubdelay -> slot zegarka + PPM
 -> LPM na itemie "chestpvp" -> po wejsciu sprawdza czy gracz dalej jest w zasiegu
 -> jest: po 3s /hub i od nowa; nie ma: wraca do lowienia.
Config: .minecraft/config/autoafk.json

KSIAZKI
 Mod co kilka tickow sprawdza ksiazki w EQ. Jesli ma szukany enchant (domyslnie depth strider 3 i mending):
 otwiera menu ESC, po chwili robi Disconnect i zatrzymuje sie.
