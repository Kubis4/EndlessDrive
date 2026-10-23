package sk.kubis.endlessdrive.ui.l10n

import sk.kubis.endlessdrive.domain.model.AppLanguage
import sk.kubis.endlessdrive.domain.model.GaugeStyle
import sk.kubis.endlessdrive.domain.repository.GarageUpgrade

class Copy(val language: AppLanguage) {

    internal fun t(en: String, de: String, fr: String, it: String, sk: String, es: String): String =
        when (language) {
            AppLanguage.ENGLISH -> en
            AppLanguage.GERMAN -> de
            AppLanguage.FRENCH -> fr
            AppLanguage.ITALIAN -> it
            AppLanguage.SLOVAK -> sk
            AppLanguage.SPANISH -> es
        }

    // --- Menu --------------------------------------------------------------
    val openRoad get() = t("OPEN ROAD", "OFFENE STRASSE", "ROUTE OUVERTE", "STRADA APERTA", "OTVORENÁ CESTA", "CARRETERA ABIERTA")
    val keepEngineAlive get() = t("KEEP THE ENGINE ALIVE.", "HALTE DEN MOTOR AM LEBEN.", "GARDE LE MOTEUR EN VIE.", "TIENI IL MOTORE ACCESO.", "UDRŽ MOTOR PRI ŽIVOTE.", "MANTÉN EL MOTOR VIVO.")
    val best get() = t("BEST", "BESTE", "MEILLEUR", "RECORD", "REKORD", "MEJOR")
    val relays get() = t("RELAYS", "RELAIS", "RELAIS", "RELAY", "RELÉ", "RELEVOS")
    fun kmValue(km: String) = t("$km KM", "$km KM", "$km KM", "$km KM", "$km KM", "$km KM")
    fun bankedScrap(amount: Int) = t(
        "BANKED SCRAP  $amount",
        "GELAGERTER SCHROTT  $amount",
        "FERRAILLE EN BANQUE  $amount",
        "ROTTAME IN BANCA  $amount",
        "ULOŽENÝ ŠROT  $amount",
        "CHATARRA GUARDADA  $amount"
    )
    val savedRun get() = t("SAVED RUN", "GESPEICHERTE FAHRT", "COURSE SAUVEGARDÉE", "CORSA SALVATA", "ULOŽENÁ JAZDA", "CARRERA GUARDADA")
    val nextExpedition get() = t("YOUR NEXT EXPEDITION", "DEINE NÄCHSTE EXPEDITION", "TA PROCHAINE EXPÉDITION", "LA TUA PROSSIMA SPEDIZIONE", "TVOJA ĎALŠIA EXPEDÍCIA", "TU PRÓXIMA EXPEDICIÓN")
    val roadIsCalling get() = t("THE ROAD IS CALLING.", "DIE STRASSE RUFT.", "LA ROUTE APPELLE.", "LA STRADA CHIAMA.", "CESTA VOLÁ.", "LA CARRETERA LLAMA.")
    fun kmClock(clock: String) = t("KM  ·  $clock", "KM  ·  $clock", "KM  ·  $clock", "KM  ·  $clock", "KM  ·  $clock", "KM  ·  $clock")
    val findParts get() = t(
        "Find parts. Start the engine. Follow the road.",
        "Finde Teile. Starte den Motor. Folge der Straße.",
        "Trouve des pièces. Démarre le moteur. Suis la route.",
        "Trova i pezzi. Avvia il motore. Segui la strada.",
        "Nájdi diely. Naštartuj motor. Choď po ceste.",
        "Encuentra piezas. Arranca el motor. Sigue la carretera."
    )
    val testingOptionsActive get() = t("TESTING OPTIONS ACTIVE", "TESTOPTIONEN AKTIV", "OPTIONS DE TEST ACTIVES", "OPZIONI DI TEST ATTIVE", "TESTOVACIE VOĽBY ZAPNUTÉ", "OPCIONES DE PRUEBA ACTIVAS")
    val continueRun get() = t("CONTINUE", "FORTSETZEN", "CONTINUER", "CONTINUA", "POKRAČOVAŤ", "CONTINUAR")
    val startRun get() = t("START RUN", "FAHRT STARTEN", "LANCER", "INIZIA CORSA", "ŠTART", "EMPEZAR")
    val newRun get() = t("NEW RUN", "NEUE FAHRT", "NOUVELLE COURSE", "NUOVA CORSA", "NOVÁ JAZDA", "NUEVA CARRERA")
    val howToPlay get() = t("HOW TO PLAY", "SPIELANLEITUNG", "COMMENT JOUER", "COME GIOCARE", "AKO HRÁŤ", "CÓMO JUGAR")
    val garage get() = t("GARAGE", "GARAGE", "GARAGE", "OFFICINA", "GARÁŽ", "GARAJE")
    val settings get() = t("SETTINGS", "EINSTELLUNGEN", "PARAMÈTRES", "IMPOSTAZIONI", "NASTAVENIA", "AJUSTES")
    val records get() = t("RECORDS", "REKORDE", "RECORDS", "RECORD", "REKORDY", "RÉCORDS")
    val leaderboard get() = t("LEADERBOARD", "RANGLISTE", "CLASSEMENT", "CLASSIFICA", "REBRÍČEK", "CLASIFICACIÓN")
    val achievements get() = t("ACHIEVEMENTS", "ERFOLGE", "SUCCÈS", "OBIETTIVI", "ÚSPECHY", "LOGROS")
    val replaceRunTitle get() = t("REPLACE THIS RUN?", "DIESE FAHRT ERSETZEN?", "REMPLACER CETTE COURSE ?", "SOSTITUIRE QUESTA CORSA?", "NAHRADIŤ TÚTO JAZDU?", "¿REEMPLAZAR ESTA CARRERA?")
    val replaceRunBody get() = t(
        "Your current car and supplies will be lost. Your personal best stays saved.",
        "Dein aktuelles Auto und die Vorräte gehen verloren. Deine Bestleistung bleibt gespeichert.",
        "Ta voiture et tes provisions actuelles seront perdues. Ton record reste sauvegardé.",
        "L'auto e le scorte attuali andranno perse. Il record personale resta salvato.",
        "Aktuálne auto a zásoby sa stratia. Osobný rekord ostane uložený.",
        "Perderás el coche y las provisiones actuales. Tu récord personal se conserva."
    )
    val keepRun get() = t("KEEP RUN", "BEHALTEN", "GARDER", "TIENI CORSA", "NECHAŤ JAZDU", "CONSERVAR")
    val replaceRun get() = t("REPLACE RUN", "ERSETZEN", "REMPLACER", "SOSTITUISCI", "NAHRADIŤ", "REEMPLAZAR")
    val back get() = t("BACK", "ZURÜCK", "RETOUR", "INDIETRO", "SPÄŤ", "ATRÁS")

    // --- Settings ----------------------------------------------------------
    val languageSection get() = t("LANGUAGE", "SPRACHE", "LANGUE", "LINGUA", "JAZYK", "IDIOMA")
    val languageDetail get() = t(
        "Menus, HUD and in-run messages use this language.",
        "Menüs, HUD und Meldungen während der Fahrt nutzen diese Sprache.",
        "Les menus, le HUD et les messages en course utilisent cette langue.",
        "Menu, HUD e messaggi di guida usano questa lingua.",
        "Menu, HUD aj hlášky počas jazdy používajú tento jazyk.",
        "Los menús, el HUD y los mensajes de la carrera usan este idioma."
    )
    val controls get() = t("CONTROLS", "STEUERUNG", "COMMANDES", "COMANDI", "OVLÁDANIE", "CONTROLES")
    val controlsDetail get() = t(
        "How the right pedal works. Brake stays a hold button.",
        "So funktioniert das rechte Pedal. Die Bremse bleibt ein Halteknopf.",
        "Fonctionnement de la pédale droite. Le frein reste un bouton à maintenir.",
        "Come funziona il pedale destro. Il freno resta un tasto da tenere.",
        "Ako funguje pravý pedál. Brzda ostáva tlačidlo na držanie.",
        "Cómo funciona el pedal derecho. El freno sigue siendo un botón de mantener."
    )
    val throttlePedal get() = t("Throttle pedal", "Gaspedal", "Pédale d'accélérateur", "Pedale dell'acceleratore", "Plyn pedál", "Pedal de acelerador")
    val throttlePedalDetail get() = t(
        "Hold for full power. Same as before.",
        "Halten für volle Leistung. Wie bisher.",
        "Maintenir pour pleine puissance. Comme avant.",
        "Tieni premuto per piena potenza. Come prima.",
        "Drž pre plný výkon. Rovnako ako doteraz.",
        "Mantén pulsado a plena potencia. Igual que antes."
    )
    val throttleSlide get() = t("Throttle slide", "Gas-Schieber", "Glissière d'accélérateur", "Slider acceleratore", "Plyn posuvník", "Deslizador de acelerador")
    val throttleSlideDetail get() = t(
        "Swipe up from the bottom to feather power and cut wheelspin on launch.",
        "Von unten nach oben wischen, um die Leistung zu dosieren und Durchdrehen beim Anfahren zu vermeiden.",
        "Glisse vers le haut pour doser la puissance et limiter le patinage au départ.",
        "Scorri verso l'alto per dosare la potenza e ridurre lo slittamento in partenza.",
        "Potiahni zdola nahor, aby si dávkoval výkon a obmedzil pretáčanie kolies.",
        "Desliza hacia arriba para dosificar la potencia y cortar el patinaje al salir."
    )
    val hud get() = t("HUD", "HUD", "HUD", "HUD", "HUD", "HUD")
    val hudDetail get() = t(
        "Classic analog dials, or Strip bars.",
        "Klassische Analoguhren oder Streifenanzeigen.",
        "Compteurs analogiques classiques, ou barres Strip.",
        "Quadranti analogici classici, oppure barre Strip.",
        "Klasické analogové budíky, alebo pruhy Strip.",
        "Relojes analógicos clásicos, o barras Strip."
    )
    fun gaugeName(style: GaugeStyle) = when (style) {
        GaugeStyle.CLASSIC -> t("Classic", "Klassisch", "Classique", "Classico", "Klasické", "Clásico")
        GaugeStyle.STRIP -> t("Strip", "Streifen", "Barres", "Barre", "Pruhy", "Barras")
    }
    fun gaugeDetail(style: GaugeStyle) = when (style) {
        GaugeStyle.CLASSIC -> t("Analog rings with needles.", "Analoge Ringe mit Zeigern.", "Anneaux analogiques avec aiguilles.", "Anelli analogici con lancette.", "Analógové kruhy s ručičkami.", "Anillos analógicos con agujas.")
        GaugeStyle.STRIP -> t("Minimal digital bars.", "Minimale digitale Balken.", "Barres numériques minimales.", "Barre digitali minimali.", "Minimálne digitálne pruhy.", "Barras digitales mínimas.")
    }
    val display get() = t("DISPLAY", "ANZEIGE", "AFFICHAGE", "SCHERMO", "ZOBRAZENIE", "PANTALLA")
    val displayDetail get() = t(
        "HUD and menu size. Dialogs keep a readable width on large screens.",
        "Größe von HUD und Menüs. Dialoge behalten auf großen Bildschirmen eine lesbare Breite.",
        "Taille du HUD et des menus. Les dialogues gardent une largeur lisible sur grand écran.",
        "Dimensione di HUD e menu. I dialoghi restano leggibili sui schermi grandi.",
        "Veľkosť HUD a menu. Dialógy ostanú čitateľné aj na veľkej obrazovke.",
        "Tamaño del HUD y los menús. Los diálogos siguen siendo legibles en pantallas grandes."
    )
    val uiScale get() = t("UI scale", "UI-Skalierung", "Échelle de l'interface", "Scala interfaccia", "Veľkosť UI", "Escala de interfaz")
    val lightningFlashes get() = t(
        "Lightning flashes", "Blitzlicht", "Éclairs lumineux", "Lampi luminosi",
        "Záblesky bleskov", "Destellos de relámpagos"
    )
    val lightningFlashesDetail get() = t(
        "Turns off only the bright storm flashes. Rain, thunder and gameplay remain enabled.",
        "Schaltet nur die hellen Sturmblitze aus. Regen, Donner und Spiel bleiben aktiv.",
        "Désactive seulement les éclairs lumineux. La pluie, le tonnerre et le jeu restent actifs.",
        "Disattiva solo i lampi luminosi. Pioggia, tuoni e gioco restano attivi.",
        "Vypne iba jasné záblesky búrky. Dážď, hrom aj hra zostanú zapnuté.",
        "Desactiva solo los destellos brillantes. La lluvia, los truenos y el juego siguen activos."
    )
    val flashingWarning get() = t(
        "Photosensitivity warning: storms contain flashing lightning. Disable flashes in Settings.",
        "Hinweis zur Lichtempfindlichkeit: Stürme enthalten Blitzlicht. In den Einstellungen abschaltbar.",
        "Avertissement photosensibilité : les orages comportent des éclairs. Désactivables dans les réglages.",
        "Avviso fotosensibilità: i temporali includono lampi. Disattivabili nelle impostazioni.",
        "Upozornenie na fotosenzitivitu: búrky obsahujú blikajúce blesky. Záblesky vypneš v nastaveniach.",
        "Aviso de fotosensibilidad: las tormentas tienen destellos. Se desactivan en ajustes."
    )
    val sound get() = t("SOUND", "TON", "SON", "SUONO", "ZVUK", "SONIDO")
    val soundDetail get() = t(
        "Tyre sounds are brief traction cues. Set them to zero for silent tyres.",
        "Reifengeräusche sind kurze Traktionshinweise. Auf null für stille Reifen.",
        "Les bruits de pneus sont de brefs indices d'adhérence. À zéro pour les silencieux.",
        "I suoni delle gomme sono brevi indizi di aderenza. A zero per gomme silenziose.",
        "Zvuky pneumatík sú krátke signály priľnavosti. Nula = tiché gumy.",
        "Los sonidos de neumáticos son pistas de agarre. Ponlos a cero para silencio."
    )
    val masterVolume get() = t("Master volume", "Gesamtlautstärke", "Volume principal", "Volume principale", "Hlavná hlasitosť", "Volumen maestro")
    val roadWeather get() = t("Road & weather", "Straße & Wetter", "Route et météo", "Strada e meteo", "Cesta a počasie", "Carretera y clima")
    val tyreSlip get() = t("Tyre slip", "Reifenschlupf", "Glissement des pneus", "Slittamento gomme", "Šmyk pneumatík", "Deslizamiento de neumáticos")
    val resetSound get() = t("RESET SOUND", "TON ZURÜCKSETZEN", "RÉINITIALISER LE SON", "REIMPOSTA SUONO", "RESET ZVUKU", "REINICIAR SONIDO")
    val off get() = t("OFF", "AUS", "OFF", "OFF", "VYP", "OFF")
    val privacy get() = t("PRIVACY", "DATENSCHUTZ", "CONFIDENTIALITÉ", "PRIVACY", "SÚKROMIE", "PRIVACIDAD")
    val privacyDetail get() = t(
        "Change how rewarded advertising uses your data.",
        "Ändere, wie belohnte Werbung deine Daten nutzt.",
        "Modifie l'utilisation de tes données par la publicité récompensée.",
        "Cambia come la pubblicità con ricompensa usa i tuoi dati.",
        "Zmeň, ako odmenená reklama používa tvoje údaje.",
        "Cambia cómo la publicidad recompensada usa tus datos."
    )
    val adPrivacyOptions get() = t("AD PRIVACY OPTIONS", "WERBE-DATENSCHUTZ", "OPTIONS PUB", "OPZIONI PRIVACY ADS", "SÚKROMIE REKLÁM", "PRIVACIDAD DE ANUNCIOS")
    val debugTools get() = t("DEBUG TOOLS", "DEBUG-WERKZEUGE", "OUTILS DE DÉBOGAGE", "STRUMENTI DEBUG", "LADIACE NÁSTROJE", "HERRAMIENTAS DEBUG")
    val hideDebugTools get() = t("HIDE DEBUG TOOLS", "DEBUG AUSBLENDEN", "MASQUER LE DÉBOGAGE", "NASCONDI DEBUG", "SKRYŤ LADENIE", "OCULTAR DEBUG")
    val showDebugTools get() = t("SHOW DEBUG TOOLS", "DEBUG ANZEIGEN", "AFFICHER LE DÉBOGAGE", "MOSTRA DEBUG", "ZOBRAZIŤ LADENIE", "MOSTRAR DEBUG")
    val debugToolsDetail get() = t(
        "These tools are for testing only and apply to the next new run.",
        "Diese Werkzeuge sind nur zum Testen und gelten für die nächste neue Fahrt.",
        "Ces outils sont uniquement pour les tests et s'appliquent à la prochaine course.",
        "Questi strumenti sono solo per i test e valgono per la prossima corsa.",
        "Tieto nástroje sú len na testovanie a platia pre ďalšiu novú jazdu.",
        "Estas herramientas son solo para pruebas y se aplican a la próxima carrera."
    )
    val fpsCounter get() = t("FPS counter", "FPS-Zähler", "Compteur FPS", "Contatore FPS", "Počítadlo FPS", "Contador FPS")
    val fpsCounterDetail get() = t(
        "Show the live frame-rate counter in the driving HUD.",
        "Zeigt die Bildrate im Fahr-HUD.",
        "Affiche le compteur d'images dans le HUD de conduite.",
        "Mostra il contatore dei frame nell'HUD di guida.",
        "Zobrazí živé počítadlo snímok v jazdnom HUD.",
        "Muestra el contador de fotogramas en el HUD."
    )
    val allComponents get() = t("All components", "Alle Komponenten", "Tous les composants", "Tutti i componenti", "Všetky diely", "Todos los componentes")
    val allComponentsDetail get() = t(
        "Fills every empty slot with a basic part, so nothing is missing.",
        "Füllt jeden leeren Slot mit einem Basis-Teil, damit nichts fehlt.",
        "Remplit chaque emplacement vide avec une pièce de base.",
        "Riempie ogni slot vuoto con un pezzo base.",
        "Vyplní každý prázdny slot základným dielom, nič nechýba.",
        "Llena cada hueco vacío con una pieza básica."
    )
    val fullUpgrades get() = t("Full upgrades", "Volle Upgrades", "Améliorations max", "Upgrade completi", "Plné vylepšenia", "Mejoras completas")
    val fullUpgradesDetail get() = t(
        "Best part in every slot — strongest engine, sport tyres, big tank.",
        "Bestes Teil in jedem Slot — stärkster Motor, Sportreifen, großer Tank.",
        "Meilleure pièce partout — moteur le plus fort, pneus sport, grand réservoir.",
        "Il pezzo migliore in ogni slot — motore più forte, gomme sport, serbatoio grande.",
        "Najlepší diel v každom slote — najsilnejší motor, športové gumy, veľká nádrž.",
        "La mejor pieza en cada hueco: motor más fuerte, neumáticos sport, depósito grande."
    )
    val wholeBody get() = t("Whole body", "Komplette Karosserie", "Carrosserie complète", "Carrozzeria completa", "Celá karoséria", "Carrocería completa")
    val wholeBodyDetail get() = t(
        "Doors, bonnet, boot lid, bumpers, lights and seats fitted from the start.",
        "Türen, Motorhaube, Kofferraumdeckel, Stoßstangen, Lichter und Sitze von Beginn an.",
        "Portes, capot, hayon, pare-chocs, feux et sièges dès le départ.",
        "Porte, cofano, bagagliaio, paraurti, luci e sedili già montati.",
        "Dvere, kapota, veko kufra, nárazníky, svetlá a sedadlá od začiatku.",
        "Puertas, capó, maletero, parachoques, luces y asientos desde el inicio."
    )
    val fullFluids get() = t("Full fluids", "Volle Flüssigkeiten", "Fluides pleins", "Liquidi pieni", "Plné kvapaliny", "Líquidos llenos")
    val fullFluidsDetail get() = t(
        "Clean fuel, oil and coolant to the brim, battery charged.",
        "Sauberer Kraftstoff, Öl und Kühlmittel randvoll, Batterie geladen.",
        "Carburant, huile et liquide de refroidissement propres à ras bord, batterie chargée.",
        "Carburante, olio e refrigerante puliti al massimo, batteria carica.",
        "Čisté palivo, olej a chladiaca kvapalina po okraj, batéria nabitá.",
        "Combustible, aceite y refrigerante limpios a tope, batería cargada."
    )
    val testTrack get() = t("Test track", "Teststrecke", "Piste d'essai", "Pista di prova", "Testovacia trať", "Pista de pruebas")
    val testTrackDetail get() = t(
        "Washboard, a sharp bump, a jump, a long climb, dips and growing steps — on a loop, for tuning suspension and handling.",
        "Waschbrett, scharfer Stoß, Sprung, lange Steigung, Senken und Stufen — in einer Schleife zum Abstimmen.",
        "Tôle ondulée, bosse, saut, longue montée, creux et marches — en boucle pour régler suspensions et tenue.",
        "Strada ondulata, dosso, salto, lunga salita, avvallamenti e scalini — in loop per tarare sospensioni.",
        "Vlnitá cesta, ostrý hrboľ, skok, dlhé stúpanie, preliačiny a schody — v slučke na ladenie podvozku.",
        "Badén, salto, subida larga, hondonadas y escalones — en bucle para afinar la suspensión."
    )
    val repairControls get() = t("Repair controls", "Reparatursteuerung", "Commandes de réparation", "Controlli riparazione", "Ovládanie opráv", "Controles de reparación")
    val repairControlsDetail get() = t(
        "Shows the instant REPAIR action in CAR. Debug only; normal runs hide it.",
        "Zeigt die sofortige REPARATUR-Aktion in AUTO. Nur Debug; normale Fahrten verbergen sie.",
        "Affiche l'action RÉPARER instantanée dans VOITURE. Debug uniquement.",
        "Mostra l'azione RIPARA istantanea in AUTO. Solo debug.",
        "Zobrazí okamžitú akciu OPRAVA v aute. Len ladenie; bežné jazdy ju skryjú.",
        "Muestra la acción REPARAR instantánea en COCHE. Solo depuración."
    )
    val fullCarTrack get() = t("FULL CAR + TRACK", "VOLLES AUTO + STRECKE", "VOITURE COMPLÈTE + PISTE", "AUTO COMPLETA + PISTA", "PLNÉ AUTO + TRAŤ", "COCHE COMPLETO + PISTA")
    val turnAllOff get() = t("TURN ALL OFF", "ALLES AUS", "TOUT DÉSACTIVER", "Spegni tutto", "VŠETKO VYP", "APAGAR TODO").uppercase()

    // --- Tutorial ----------------------------------------------------------
    val previous get() = t("PREVIOUS", "ZURÜCK", "PRÉCÉDENT", "PRECEDENTE", "PREDCHÁDZAJÚCE", "ANTERIOR")
    val next get() = t("NEXT", "WEITER", "SUIVANT", "AVANTI", "ĎALEJ", "SIGUIENTE")
    val done get() = t("DONE", "FERTIG", "TERMINÉ", "FATTO", "HOTOVO", "LISTO")
    val startExpedition get() = t("START EXPEDITION", "EXPEDITION STARTEN", "LANCER L'EXPÉDITION", "INIZIA SPEDIZIONE", "ŠTART EXPEDÍCIE", "EMPEZAR EXPEDICIÓN")
    fun tutorialSubtitle(page: Int, total: Int, eyebrow: String) =
        t("$page/$total · $eyebrow", "$page/$total · $eyebrow", "$page/$total · $eyebrow", "$page/$total · $eyebrow", "$page/$total · $eyebrow", "$page/$total · $eyebrow")

    fun tutorialEyebrow(index: Int) = when (index) {
        0 -> t("THE EXPEDITION", "DIE EXPEDITION", "L'EXPÉDITION", "LA SPEDIZIONE", "EXPEDÍCIA", "LA EXPEDICIÓN")
        1 -> t("BEFORE YOU MOVE", "BEVOR DU FÄHRST", "AVANT DE PARTIR", "PRIMA DI PARTIRE", "PRED POHYBOM", "ANTES DE MOVERTE")
        2 -> t("ON THE ROAD", "AUF DER STRASSE", "SUR LA ROUTE", "IN STRADA", "NA CESTE", "EN LA CARRETERA")
        3 -> t("SCAVENGING", "PLÜNDERN", "FOUILLE", "SCAVO", "ZBIERANIE", "SAQUEO")
        4 -> t("SURVIVAL", "ÜBERLEBEN", "SURVIE", "SOPRAVVIVENZA", "PREŽITIE", "SUPERVIVENCIA")
        else -> t("READY", "BEREIT", "PRÊT", "PRONTO", "PRIPRAVENÝ", "LISTO")
    }

    fun tutorialTitle(index: Int) = when (index) {
        0 -> t("KEEP THE CAR ALIVE", "HALTE DAS AUTO AM LEBEN", "GARDE LA VOITURE EN VIE", "TIENI VIVA L'AUTO", "UDRŽ AUTO PRI ŽIVOTE", "MANTÉN EL COCHE VIVO")
        1 -> t("MAKE THE WRECK RUN", "BRING DEN WRACK IN GANG", "FAIS MARCHER L'ÉPAVE", "FAI ANDARE IL RELITTO", "ROZCHOĎ VRAK", "HAZ ANDAR EL NAUFRAGIO")
        2 -> t("DRIVE WITH A PLAN", "FAHRE MIT PLAN", "CONDUIS AVEC UN PLAN", "GUIDA CON UN PIANO", "JAZDI S PLÁNOM", "CONDUCE CON UN PLAN")
        3 -> t("STOP, SEARCH, DECIDE", "STOPPEN, SUCHEN, ENTSCHEIDEN", "STOP, FOUILLE, DÉCIDE", "FERMATI, CERCA, DECIDI", "ZASTAV, PREHĽADAJ, ROZHODNI", "PARA, BUSCA, DECIDE")
        4 -> t("READ THE SYMPTOMS", "LIES DIE SYMPTOME", "LIS LES SYMPTÔMES", "LEGGI I SINTOMI", "ČÍTAJ PRÍZNAKY", "LEE LOS SÍNTOMAS")
        else -> t("THE REST IS YOUR STORY", "DER REST IST DEINE GESCHICHTE", "LE RESTE EST TON HISTOIRE", "IL RESTO È LA TUA STORIA", "ZVYŠOK JE TVOJ PRÍBEH", "EL RESTO ES TU HISTORIA")
    }

    fun tutorialBody(index: Int) = when (index) {
        0 -> t(
            "Your story goal is the safe haven at 100 km. Restore all seven radio relays on the way. At 100 km you can finish the expedition or keep driving in Endless mode.",
            "Dein Story-Ziel ist der sichere Hafen bei 100 km. Stelle unterwegs alle sieben Funkrelais wieder her. Bei 100 km kannst du die Expedition beenden oder im Endlosmodus weiterfahren.",
            "Ton objectif d'histoire est le refuge à 100 km. Restaure les sept relais radio en chemin. À 100 km tu peux terminer l'expédition ou continuer en mode Infini.",
            "L'obiettivo della storia è il rifugio a 100 km. Ripristina i sette relè radio lungo la strada. A 100 km puoi chiudere la spedizione o continuare in modalità Infinita.",
            "Príbehový cieľ je bezpečný prístav na 100 km. Cestou obnov všetkých sedem rádiových relé. Na 100 km môžeš expedíciu ukončiť alebo pokračovať v režime Nekonečno.",
            "Tu objetivo de historia es el refugio a 100 km. Restaura los siete relés de radio por el camino. A 100 km puedes terminar la expedición o seguir en modo Infinito."
        )
        1 -> t(
            "Open CAR to inspect fitted parts and fluids. Open PACK to use supplies. The message at the top guides you through oil, coolant, fuel and starting the engine.",
            "Öffne AUTO, um Teile und Flüssigkeiten zu prüfen. Öffne PACK, um Vorräte zu nutzen. Die Meldung oben führt dich durch Öl, Kühlmittel, Kraftstoff und den Motorstart.",
            "Ouvre VOITURE pour inspecter pièces et fluides. Ouvre SAC pour utiliser les provisions. Le message en haut te guide pour l'huile, le liquide, le carburant et le démarrage.",
            "Apri AUTO per ispezionare pezzi e liquidi. Apri ZAINO per usare le scorte. Il messaggio in alto ti guida tra olio, refrigerante, carburante e avviamento.",
            "Otvor AUTO a skontroluj namontované diely a kvapaliny. Otvor BATOH na použitie zásob. Hláška hore ťa prevedie olejom, chladiacou, palivom a štartom.",
            "Abre COCHE para inspeccionar piezas y líquidos. Abre MOCHILA para usar provisiones. El mensaje de arriba te guía con aceite, refrigerante, combustible y el arranque."
        )
        2 -> t(
            "Use the right control for throttle and the left control for brake. There is one continuous road; the surface and weather change as you go. Slow down for broken pavement, mud, sand, ice and sharp terrain. Release the throttle when wheels spin.",
            "Rechts Gas, links Bremse. Es gibt eine durchgehende Straße; Belag und Wetter ändern sich. Langsamer bei kaputtem Asphalt, Matsch, Sand, Eis und hartem Gelände. Gas weg, wenn die Räder durchdrehen.",
            "Commande droite = accélérateur, gauche = frein. Une seule route continue ; surface et météo changent. Ralentis sur chaussée cassée, boue, sable, glace et terrain dur. Relâche si les roues patinent.",
            "Comando destro = acceleratore, sinistro = freno. C'è una sola strada continua; superficie e meteo cambiano. Rallenta su asfalto rotto, fango, sabbia, ghiaccio e terreno duro. Lascia il gas se le ruote slittano.",
            "Pravým ovládačom plyn, ľavým brzda. Je jedna súvislá cesta; povrch a počasie sa menia. Spomaľ na rozbitom asfalte, blate, piesku, ľade a v ostrom teréne. Pusti plyn, keď sa kolesá točia.",
            "Control derecho = acelerador, izquierdo = freno. Hay una sola carretera continua; superficie y clima cambian. Reduce en asfalto roto, barro, arena, hielo y terreno duro. Suelta si las ruedas patinan."
        )
        3 -> t(
            "Brake beside a lit building or wreck and tap SEARCH. Take useful supplies, fit better parts, or scrap what you do not need. Your pack is small; the boot is available only beside the car.",
            "Bremse neben einem beleuchteten Gebäude oder Wrack und tippe SUCHEN. Nimm Vorräte, baue bessere Teile ein oder verschrotte Unnötiges. Der Rucksack ist klein; der Kofferraum nur am Auto.",
            "Freine près d'un bâtiment allumé ou d'une épave et appuie sur FOUILLER. Prends des provisions, monte de meilleures pièces ou ferraille le reste. Le sac est petit ; le coffre n'est dispo que près de la voiture.",
            "Frena accanto a un edificio illuminato o a un relitto e tocca CERCA. Prendi scorte, monta pezzi migliori o rottama il superfluo. Lo zaino è piccolo; il bagagliaio solo accanto all'auto.",
            "Zabrzdi pri osvetlenej budove alebo vraku a ťukni PREHĽADAŤ. Vezmi zásoby, namontuj lepšie diely alebo zošrotuj, čo nepotrebuješ. Batoh je malý; kufor len pri aute.",
            "Frena junto a un edificio iluminado o un pecio y pulsa BUSCAR. Toma provisiones, monta mejores piezas o chatarriza lo que no necesites. La mochila es pequeña; el maletero solo junto al coche."
        )
        4 -> t(
            "Wrong or dirty fluids, overheating, weak tyres and a flat battery fail in different ways. The CAR panel shows exact condition and fluid quality; the driving HUD only interrupts you when action is needed.",
            "Falsche oder schmutzige Flüssigkeiten, Überhitzung, schwache Reifen und eine tote Batterie fallen unterschiedlich aus. AUTO zeigt Zustand und Qualität; das HUD stört nur, wenn Handeln nötig ist.",
            "Fluides sales ou faux, surchauffe, pneus faibles et batterie plate échouent autrement. VOITURE montre l'état exact ; le HUD n'interrompt que si une action est requise.",
            "Liquidi sporchi o sbagliati, surriscaldamento, gomme deboli e batteria scarica falliscono in modi diversi. AUTO mostra stato e qualità; l'HUD interrompe solo se serve agire.",
            "Zlé alebo špinavé kvapaliny, prehriatie, slabé gumy a vybitá batéria zlyhávajú rôzne. Panel AUTO ukáže stav a kvalitu; HUD ťa preruší, len keď treba konať.",
            "Líquidos sucios o incorrectos, sobrecalentamiento, neumáticos débiles y batería plana fallan de formas distintas. COCHE muestra el estado exacto; el HUD solo interrumpe si hay que actuar."
        )
        else -> t(
            "The first stretch is deliberately gentle and always contains the basic supplies needed for either engine. Explore, rebuild the car, restore the network and decide how far beyond 100 km you can go.",
            "Der erste Abschnitt ist bewusst sanft und enthält immer die Grundvorräte für beide Motoren. Erkunde, baue das Auto auf, stelle das Netz wieder her und entscheide, wie weit du über 100 km hinauskommst.",
            "Le premier tronçon est volontairement doux et contient toujours les provisions de base pour les deux moteurs. Explore, reconstruis, restaure le réseau et vois jusqu'où tu vas au-delà de 100 km.",
            "Il primo tratto è volutamente mite e contiene sempre le scorte base per entrambi i motori. Esplora, ricostruisci l'auto, ripristina la rete e decidi quanto andare oltre i 100 km.",
            "Prvý úsek je zámerne mierny a vždy obsahuje základné zásoby pre oba motory. Preskúmavaj, oprav auto, obnov sieť a rozhodni, ako ďaleko za 100 km zájdeš.",
            "El primer tramo es deliberadamente suave y siempre tiene las provisiones básicas para ambos motores. Explora, reconstruye el coche, restaura la red y decide hasta dónde llegar más allá de 100 km."
        )
    }

    fun tutorialCheck(page: Int, item: Int): String = when (page to item) {
        0 to 0 -> t("Stop at every relay", "An jedem Relais anhalten", "Arrête-toi à chaque relais", "Fermati a ogni relè", "Zastav pri každom relé", "Para en cada relé")
        0 to 1 -> t("Carry a relay module and enough scrap", "Trag ein Relais-Modul und genug Schrott", "Emporte un module relais et assez de ferraille", "Porta un modulo relè e abbastanza rottame", "Nesi modul relé a dosť šrotu", "Lleva un módulo de relé y chatarra suficiente")
        0 to 2 -> t("Distance is saved during the run", "Die Distanz wird während der Fahrt gespeichert", "La distance est sauvegardée pendant la course", "La distanza si salva durante la corsa", "Vzdialenosť sa ukladá počas jazdy", "La distancia se guarda durante la carrera")
        1 to 0 -> t("Correct fuel must match the engine", "Der richtige Kraftstoff muss zum Motor passen", "Le bon carburant doit correspondre au moteur", "Il carburante giusto deve coincidere col motore", "Správne palivo musí sedieť k motoru", "El combustible correcto debe coincidir con el motor")
        1 to 1 -> t("A dry engine or radiator takes damage", "Ein trockener Motor oder Kühler nimmt Schaden", "Un moteur ou un radiateur à sec s'abîme", "Un motore o un radiatore a secco si danneggia", "Suchý motor alebo chladič berie poškodenie", "Un motor o radiador seco se daña")
        1 to 2 -> t("START only works when the car is ready", "START funktioniert nur, wenn das Auto bereit ist", "DÉMARRER ne marche que si la voiture est prête", "AVVIA funziona solo se l'auto è pronta", "ŠTART ide, len keď je auto pripravené", "ARRANCAR solo funciona cuando el coche está listo")
        2 to 0 -> t("Watch temperature, fuel and warning lamps", "Achte auf Temperatur, Kraftstoff und Warnleuchten", "Surveille température, carburant et voyants", "Controlla temperatura, carburante e spie", "Sleduj teplotu, palivo a kontrolky", "Vigila temperatura, combustible y testigos")
        2 to 1 -> t("Ease off when supplies or the car are running low", "Zurücknehmen, wenn Vorräte oder das Auto knapp werden", "Relâche quand les provisions ou la voiture sont bas", "Rallenta se scorte o auto sono al limite", "Uber, keď dochádzajú zásoby alebo auto", "Afloja cuando falten provisiones o el coche")
        2 to 2 -> t("Headlights and roof lights drain the battery", "Scheinwerfer und Dachlichter leeren die Batterie", "Phares et feux de toit vident la batterie", "Fari e luci tetto scaricano la batteria", "Svetlá a strešné svetlá vybíjajú batériu", "Faros y luces de techo drenan la batería")
        3 to 0 -> t("Fuel stations carry petrol and diesel", "Tankstellen haben Benzin und Diesel", "Les stations ont essence et diesel", "Le stazioni hanno benzina e diesel", "Čerpacie stanice majú benzín aj naftu", "Las gasolineras tienen gasolina y diésel")
        3 to 1 -> t("Workshops upgrade parts for scrap", "Werkstätten upgraden Teile gegen Schrott", "Les ateliers améliorent les pièces contre de la ferraille", "Le officine potenziano i pezzi in cambio di rottame", "Dielne vylepšujú diely za šrot", "Los talleres mejoran piezas a cambio de chatarra")
        3 to 2 -> t("Paint and body pieces are optional restoration", "Lack und Karosserieteile sind optionale Restauration", "Peinture et carrosserie sont une restauration optionnelle", "Vernice e carrozzeria sono un restauro opzionale", "Lak a karoséria sú voliteľná obnova", "Pintura y carrocería son restauración opcional")
        4 to 0 -> t("Stop the engine before repairs", "Motor vor Reparaturen abstellen", "Coupe le moteur avant les réparations", "Spegni il motore prima delle riparazioni", "Pred opravou vypni motor", "Apaga el motor antes de reparar")
        4 to 1 -> t("Keep one tyre solution and emergency fluids", "Halte eine Reifenlösung und Notfallflüssigkeiten bereit", "Garde une solution pneu et des fluides d'urgence", "Tieni una soluzione gomme e liquidi d'emergenza", "Maj riešenie na defekt a núdzové kvapaliny", "Guarda una solución de neumático y líquidos de emergencia")
        4 to 2 -> t("Five rewarded assists are available per run", "Fünf belohnte Hilfen pro Fahrt", "Cinq aides récompensées par course", "Cinque assistenze con ricompensa per corsa", "Päť odmenených asistencií na jazdu", "Cinco asistencias con anuncio por carrera")
        5 to 0 -> t("Progress: seven relays by 100 km", "Fortschritt: sieben Relais bis 100 km", "Progression : sept relais d'ici 100 km", "Progresso: sette relè entro 100 km", "Progres: sedem relé do 100 km", "Progreso: siete relés a los 100 km")
        5 to 1 -> t("Failure ends the run, upgrades stay in the garage", "Scheitern beendet die Fahrt, Upgrades bleiben in der Garage", "L'échec termine la course, les améliorations restent au garage", "Il fallimento chiude la corsa, gli upgrade restano in officina", "Zlyhanie ukončí jazdu, vylepšenia ostanú v garáži", "El fallo termina la carrera; las mejoras quedan en el garaje")
        else -> t("You can replay this guide from the main menu", "Du kannst diese Anleitung im Hauptmenü wiederholen", "Tu peux revoir ce guide depuis le menu principal", "Puoi rivedere questa guida dal menu principale", "Tento návod si môžeš znova pustiť z hlavného menu", "Puedes repetir esta guía desde el menú principal")
    }

    // --- Garage ------------------------------------------------------------
    val garageSubtitle get() = t("Permanent preparation for future runs", "Dauerhafte Vorbereitung für künftige Fahrten", "Préparation permanente pour les courses suivantes", "Preparazione permanente per le corse future", "Trvalá príprava na budúce jazdy", "Preparación permanente para futuras carreras")
    val bankedScrapLabel get() = t("BANKED SCRAP", "GELAGERTER SCHROTT", "FERRAILLE EN BANQUE", "ROTTAME IN BANCA", "ULOŽENÝ ŠROT", "CHATARRA GUARDADA")
    fun garageLevel(level: Int, max: Int) = t("LEVEL $level/$max", "STUFE $level/$max", "NIVEAU $level/$max", "LIVELLO $level/$max", "ÚROVEŇ $level/$max", "NIVEL $level/$max")
    val max get() = t("MAX", "MAX", "MAX", "MAX", "MAX", "MÁX")
    fun buyCost(cost: Int) = t("BUY · $cost", "KAUFEN · $cost", "ACHETER · $cost", "COMPRA · $cost", "KÚPIŤ · $cost", "COMPRAR · $cost")
    fun garageTitle(upgrade: GarageUpgrade) = when (upgrade) {
        GarageUpgrade.FUEL_RESERVE -> t("Fuel reserve", "Kraftstoffreserve", "Réserve de carburant", "Riserva carburante", "Palivová rezerva", "Reserva de combustible")
        GarageUpgrade.OIL_RESERVE -> t("Oil cache", "Ölreserve", "Réserve d'huile", "Riserva olio", "Zásoba oleja", "Reserva de aceite")
        GarageUpgrade.RELIABILITY -> t("Workshop care", "Werkstattpflege", "Soin d'atelier", "Cura officina", "Starostlivosť dielne", "Cuidado de taller")
        GarageUpgrade.STORAGE -> t("Field pack", "Feldrucksack", "Sac de terrain", "Zaino da campo", "Poľný batoh", "Mochila de campo")
        GarageUpgrade.STARTER_TYRES -> t("Starter tyres", "Startreifen", "Pneus de départ", "Gomme di partenza", "Štartovné gumy", "Neumáticos de salida")
        GarageUpgrade.ELECTRICS -> t("Charged electrics", "Geladene Elektrik", "Électricité chargée", "Elettrico carico", "Nabité elektro", "Eléctrica cargada")
        GarageUpgrade.SPARE_KIT -> t("Road kit", "Straßenkit", "Kit de route", "Kit stradale", "Cestná sada", "Kit de carretera")
        GarageUpgrade.ENGINE_TUNE -> t("Load tune", "Lastabstimmung", "Réglage de charge", "Taratura carico", "Naladenie na náklad", "Ajuste de carga")
        GarageUpgrade.LOAD_SUSPENSION -> t("Load suspension", "Lastfahrwerk", "Suspension renforcée", "Sospensioni da carico", "Zosilnené pruženie", "Suspensión reforzada")
        GarageUpgrade.BRAKE_PACKAGE -> t("Brake package", "Bremsenpaket", "Pack de freinage", "Pacchetto freni", "Balík bŕzd", "Paquete de frenos")
        GarageUpgrade.CRUISE_EFFICIENCY -> t("Cruise tuning", "Tempomat-Abstimmung", "Réglage régulateur", "Taratura cruise", "Efektivita tempomatu", "Ajuste de crucero")
    }
    fun garageDetail(upgrade: GarageUpgrade) = when (upgrade) {
        GarageUpgrade.FUEL_RESERVE -> t(
            "Start every new run with 3 more litres of the correct fuel per level.",
            "Starte jede neue Fahrt mit 3 Litern richtigem Kraftstoff mehr pro Stufe.",
            "Commence chaque course avec 3 litres de plus du bon carburant par niveau.",
            "Inizia ogni corsa con 3 litri in più del carburante giusto per livello.",
            "Každú novú jazdu začni s 3 litrami správneho paliva navyše za úroveň.",
            "Empieza cada carrera con 3 litros más del combustible correcto por nivel."
        )
        GarageUpgrade.OIL_RESERVE -> t(
            "Start every new run with 1.2 more litres of oil per level.",
            "Starte jede neue Fahrt mit 1,2 Litern Öl mehr pro Stufe.",
            "Commence chaque course avec 1,2 litre d'huile de plus par niveau.",
            "Inizia ogni corsa con 1,2 litri di olio in più per livello.",
            "Každú novú jazdu začni s 1,2 litra oleja navyše za úroveň.",
            "Empieza cada carrera con 1,2 litros más de aceite por nivel."
        )
        GarageUpgrade.RELIABILITY -> t(
            "Starting mechanical parts gain 5% condition per level.",
            "Mechanische Startteile erhalten 5 % Zustand pro Stufe.",
            "Les pièces mécaniques de départ gagnent 5 % d'état par niveau.",
            "I pezzi meccanici iniziali guadagnano il 5% di condizione per livello.",
            "Štartovné mechanické diely získajú 5 % stavu za úroveň.",
            "Las piezas mecánicas iniciales ganan un 5% de estado por nivel."
        )
        GarageUpgrade.STORAGE -> t(
            "Adds one permanent pack slot and 4 kg capacity per level.",
            "Ein permanenter Pack-Slot und 4 kg Kapazität pro Stufe.",
            "Ajoute un emplacement de sac permanent et 4 kg par niveau.",
            "Aggiunge uno slot zaino permanente e 4 kg per livello.",
            "Pridá jeden trvalý slot batohu a 4 kg nosnosti za úroveň.",
            "Añade un hueco permanente de mochila y 4 kg por nivel."
        )
        GarageUpgrade.STARTER_TYRES -> t(
            "Leave in standard rubber instead of worn poor tyres. Level 2 starts them healthier.",
            "Standardreifen statt abgefahrener Schwachreifen. Stufe 2 startet gesünder.",
            "Pneus standard au lieu des pneus usés. Niveau 2 plus sains.",
            "Gomme standard al posto di quelle consumate. Livello 2 più sane.",
            "Štandardné gumy namiesto ojazdených slabých. Úroveň 2 ich nechá zdravšie.",
            "Goma estándar en vez de la gastada. El nivel 2 sale más sana."
        )
        GarageUpgrade.ELECTRICS -> t(
            "Guarantees a battery and starter. Level 2 also fits an alternator and extra charge.",
            "Garantiert Batterie und Anlasser. Stufe 2 setzt eine Lichtmaschine und extra Ladung ein.",
            "Garantie batterie et démarreur. Niveau 2 ajoute un alternateur et plus de charge.",
            "Garantisce batteria e motorino. Livello 2 monta anche l'alternatore e più carica.",
            "Zaručí batériu a štartér. Úroveň 2 namontuje aj alternátor a viac nabitia.",
            "Garantiza batería y motor de arranque. El nivel 2 monta alternador y más carga."
        )
        GarageUpgrade.SPARE_KIT -> t(
            "Pack a puncture kit for every new run. Level 2 also packs a coolant bottle.",
            "Ein Flickzeug in jeden neuen Start. Stufe 2 packt auch Kühlmittel ein.",
            "Un kit anti-crevaison à chaque départ. Niveau 2 ajoute du liquide de refroidissement.",
            "Un kit foratura a ogni partenza. Livello 2 aggiunge anche il refrigerante.",
            "Do každej novej jazdy dá sadu na defekt. Úroveň 2 pridá aj chladiacu kvapalinu.",
            "Mete un kit antipinchazos en cada salida. El nivel 2 añade refrigerante."
        )
        GarageUpgrade.ENGINE_TUNE -> t(
            "Adds 6% engine pull per level, especially useful with a full boot.",
            "Erhöht die Motorkraft pro Stufe um 6 %, besonders bei vollem Kofferraum.",
            "Ajoute 6 % de force moteur par niveau, surtout avec un coffre plein.",
            "Aggiunge il 6% di spinta motore per livello, utile col bagagliaio pieno.",
            "Pridá 6 % ťahu motora za úroveň, najmä pri plnom kufri.",
            "Añade un 6% de empuje del motor por nivel, útil con el maletero lleno."
        )
        GarageUpgrade.LOAD_SUSPENSION -> t(
            "Stiffens springs by 10% and damping by 8% per level.",
            "Verstärkt Federn um 10 % und Dämpfung um 8 % pro Stufe.",
            "Renforce les ressorts de 10 % et l'amortissement de 8 % par niveau.",
            "Irrigidisce le molle del 10% e lo smorzamento dell'8% per livello.",
            "Zosilní pružiny o 10 % a tlmenie o 8 % za úroveň.",
            "Refuerza los muelles un 10% y la amortiguación un 8% por nivel."
        )
        GarageUpgrade.BRAKE_PACKAGE -> t(
            "Adds 10% braking capacity per level so a loaded car stops more safely.",
            "Erhöht die Bremsleistung pro Stufe um 10 %, besonders unter Last.",
            "Ajoute 10 % de capacité de freinage par niveau pour la voiture chargée.",
            "Aggiunge il 10% di capacità frenante per livello sotto carico.",
            "Pridá 10 % brzdnej kapacity za úroveň, aby naložené auto bezpečnejšie zastalo.",
            "Añade un 10% de capacidad de frenado por nivel para el coche cargado."
        )
        GarageUpgrade.CRUISE_EFFICIENCY -> t(
            "Cruise burns 1.5× throttle fuel by default. Each level cuts that waste by 0.15×.",
            "Tempomat verbraucht standardmäßig 1,5× Gasverbrauch. Jede Stufe senkt das um 0,15×.",
            "Le régulateur brûle 1,5× le carburant d'accélérateur. Chaque niveau réduit ce gaspillage de 0,15×.",
            "Il cruise consuma 1,5× il carburante dell'acceleratore. Ogni livello taglia lo spreco di 0,15×.",
            "Tempomat štandardne žerie 1,5× spotrebu plynu. Každá úroveň to zníži o 0,15×.",
            "El crucero gasta 1,5× el combustible del acelerador. Cada nivel reduce ese exceso en 0,15×."
        )
    }

    fun garageCurrentEffect(effect: String) = t(
        "ACTIVE · $effect", "AKTIV · $effect", "ACTIF · $effect",
        "ATTIVO · $effect", "AKTÍVNE · $effect", "ACTIVO · $effect"
    )

    fun garageNextEffect(effect: String) = t(
        "NEXT · $effect", "NÄCHSTE · $effect", "SUIVANT · $effect",
        "PROSSIMO · $effect", "ĎALEJ · $effect", "SIGUIENTE · $effect"
    )

    fun garageEffect(upgrade: GarageUpgrade, level: Int): String {
        val safe = level.coerceIn(0, upgrade.maxLevel)
        return when (upgrade) {
            GarageUpgrade.FUEL_RESERVE -> "+${safe * 3} L"
            GarageUpgrade.OIL_RESERVE -> "+${safe * 1.2f} L"
            GarageUpgrade.RELIABILITY -> "+${safe * 5} %"
            GarageUpgrade.STORAGE -> "+$safe ${t("slots", "Plätze", "places", "slot", "sloty", "huecos")} · +${safe * 4} kg"
            GarageUpgrade.STARTER_TYRES -> when (safe) {
                0 -> t("worn tyres", "abgefahrene Reifen", "pneus usés", "gomme usurate", "ojazdené gumy", "neumáticos gastados")
                1 -> t("standard tyres", "Standardreifen", "pneus standard", "gomme standard", "štandardné gumy", "neumáticos estándar")
                else -> t("standard tyres · 82%+", "Standardreifen · 82 %+", "pneus standard · 82 %+", "gomme standard · 82%+", "štandardné gumy · 82 %+", "neumáticos estándar · 82%+")
            }
            GarageUpgrade.ELECTRICS -> when (safe) {
                0 -> t("random electrics", "zufällige Elektrik", "électricité aléatoire", "elettrico casuale", "náhodné elektro", "eléctrica aleatoria")
                1 -> t("battery + starter", "Batterie + Anlasser", "batterie + démarreur", "batteria + motorino", "batéria + štartér", "batería + arranque")
                else -> t("battery + starter + alternator", "Batterie + Anlasser + Lichtmaschine", "batterie + démarreur + alternateur", "batteria + motorino + alternatore", "batéria + štartér + alternátor", "batería + arranque + alternador")
            }
            GarageUpgrade.SPARE_KIT -> when (safe) {
                0 -> t("no road kit", "kein Straßenkit", "aucun kit", "nessun kit", "bez cestnej sady", "sin kit")
                1 -> t("puncture kit", "Flickzeug", "kit anti-crevaison", "kit foratura", "sada na defekt", "kit antipinchazos")
                else -> t("puncture kit + coolant", "Flickzeug + Kühlmittel", "kit + refroidissement", "kit + refrigerante", "sada na defekt + chladiaca", "kit + refrigerante")
            }
            GarageUpgrade.ENGINE_TUNE -> "+${safe * 6} % ${t("pull", "Zugkraft", "force", "spinta", "ťah", "empuje")}"
            GarageUpgrade.LOAD_SUSPENSION -> "+${safe * 10} % ${t("springs", "Federn", "ressorts", "molle", "pružiny", "muelles")}"
            GarageUpgrade.BRAKE_PACKAGE -> "+${safe * 10} % ${t("brakes", "Bremsen", "freinage", "freni", "brzdy", "frenos")}"
            GarageUpgrade.CRUISE_EFFICIENCY -> {
                val mul = (1.5f - safe * 0.15f).coerceAtLeast(1f)
                "${"%.2f".format(mul)}× ${t("cruise fuel", "Tempomat-Kraftstoff", "carburant régulateur", "carburante cruise", "palivo tempomatu", "combustible crucero")}"
            }
        }
    }

    companion object {
        private val cache = AppLanguage.entries.associateWith { Copy(it) }
        fun of(language: AppLanguage): Copy = cache.getValue(language)
        val English: Copy = of(AppLanguage.ENGLISH)
    }
}
