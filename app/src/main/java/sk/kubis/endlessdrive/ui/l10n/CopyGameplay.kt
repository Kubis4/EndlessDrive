package sk.kubis.endlessdrive.ui.l10n

import sk.kubis.endlessdrive.domain.model.BiomeType
import sk.kubis.endlessdrive.domain.model.BranchStyle
import sk.kubis.endlessdrive.domain.model.BuildingType
import sk.kubis.endlessdrive.domain.model.ComponentCondition
import sk.kubis.endlessdrive.domain.model.ComponentSlot
import sk.kubis.endlessdrive.domain.model.DriveLayout
import sk.kubis.endlessdrive.domain.model.EndReason
import sk.kubis.endlessdrive.domain.model.FluidGrade
import sk.kubis.endlessdrive.domain.model.FluidType
import sk.kubis.endlessdrive.domain.model.FuelKind
import sk.kubis.endlessdrive.domain.model.ItemDef
import sk.kubis.endlessdrive.domain.model.ItemCatalog
import sk.kubis.endlessdrive.domain.model.RescueOffer
import sk.kubis.endlessdrive.domain.model.RoadSurface
import sk.kubis.endlessdrive.domain.model.VehiclePaint
import sk.kubis.endlessdrive.game.PrepStep
import sk.kubis.endlessdrive.game.car.TireInjury
import sk.kubis.endlessdrive.game.car.WearCause
import sk.kubis.endlessdrive.game.event.RoadEvent
import sk.kubis.endlessdrive.game.event.PursuitStormKind
import sk.kubis.endlessdrive.ui.leaderboard.LeaderboardTab

val Copy.pack: String get() = t("PACK", "PACK", "SAC", "ZAINO", "BATOH", "MOCHILA")
val Copy.car: String get() = t("CAR", "AUTO", "VOITURE", "AUTO", "AUTO", "COCHE")
val Copy.leave: String get() = t("LEAVE", "VERLASSEN", "QUITTER", "ESCI", "ODÍSŤ", "SALIR")
val Copy.search: String get() = t("SEARCH", "SUCHEN", "FOUILLER", "CERCA", "PREHĽADAŤ", "BUSCAR")
val Copy.sleep: String get() = t("SLEEP", "SCHLAFEN", "DORMIR", "DORMI", "SPÁNOK", "DORMIR")
val Copy.start: String get() = t("START", "START", "DÉMARRER", "AVVIA", "ŠTART", "ARRANCAR")
val Copy.drive: String get() = t("DRIVE", "FAHREN", "ROULER", "GUIDA", "JAZDA", "CONDUCIR")
val Copy.temp: String get() = t("TEMP", "TEMP", "TEMP", "TEMP", "TEPL", "TEMP")
val Copy.fuel: String get() = t("FUEL", "KRAFT", "CARB.", "CARB.", "PALIVO", "COMB.")
val Copy.condition: String get() = t("CONDITION", "ZUSTAND", "ÉTAT", "STATO", "STAV", "ESTADO")
val Copy.reverse: String get() = t("REVERSE", "RÜCKWÄRTS", "MARCHE ARRIÈRE", "RETROMARCIA", "SPÄŤ", "MARCHA ATRÁS")
val Copy.kmh: String get() = t("km/h", "km/h", "km/h", "km/h", "km/h", "km/h")
val Copy.park: String get() = t("PARK", "PARKEN", "PARKER", "PARCHEGGIO", "PARKOVANIE", "APARCAR")
val Copy.pause: String get() = t("Pause", "Pause", "Pause", "Pausa", "Pauza", "Pausa")
val Copy.resume: String get() = t("Resume", "Fortsetzen", "Reprendre", "Riprendi", "Pokračovať", "Reanudar")
val Copy.lightsLowOn: String get() = t("Switch on low beams", "Abblendlicht einschalten", "Allumer les codes", "Accendi anabbaglianti", "Zapni stretávacie", "Enciende cruce")
val Copy.lightsHighOn: String get() = t("Switch on high beams", "Fernlicht einschalten", "Allumer les pleins phares", "Accendi abbaglianti", "Zapni diaľkové", "Enciende largas")
val Copy.lightsOff: String get() = t("Switch headlights off", "Scheinwerfer ausschalten", "Éteindre les phares", "Spegni i fari", "Vypni svetlá", "Apaga los faros")
val Copy.cruiseOn: String get() = t("Switch cruise control on", "Tempomat einschalten", "Activer le régulateur", "Attiva cruise control", "Zapni tempomat", "Activa el control de crucero")
val Copy.cruiseOff: String get() = t("Switch cruise control off", "Tempomat ausschalten", "Désactiver le régulateur", "Disattiva cruise control", "Vypni tempomat", "Desactiva el control de crucero")
val Copy.roofLightsOn: String get() = t("Switch roof lights on", "Dachlichter einschalten", "Allumer les feux de toit", "Accendi luci tetto", "Zapni strešné svetlá", "Enciende luces de techo")
val Copy.roofLightsOff: String get() = t("Switch roof lights off", "Dachlichter ausschalten", "Éteindre les feux de toit", "Spegni luci tetto", "Vypni strešné svetlá", "Apaga luces de techo")
val Copy.brakeReverse: String get() = t("Brake / reverse", "Bremse / Rückwärts", "Frein / marche arrière", "Freno / retromarcia", "Brzda / cúvanie", "Freno / marcha atrás")
val Copy.throttle: String get() = t("Throttle", "Gas", "Accélérateur", "Acceleratore", "Plyn", "Acelerador")
val Copy.parkAndSearch: String get() = t("Park and search", "Parken und suchen", "Garer et fouiller", "Parcheggia e cerca", "Zastav a prehľadaj", "Aparca y busca")
val Copy.parkLabel: String get() = t("Park", "Parken", "Garer", "Parcheggia", "Zastav", "Aparca")
val Copy.parkToSearch: String get() = t("Park to search building", "Parken, um das Gebäude zu durchsuchen", "Garer pour fouiller le bâtiment", "Parcheggia per cercare nell'edificio", "Zastav a prehľadaj budovu", "Aparca para registrar el edificio")
val Copy.parkingBrake: String get() = t("Parking brake", "Feststellbremse", "Frein de parking", "Freno di stazionamento", "Parkovacia brzda", "Freno de estacionamiento")
val Copy.throttleSlideA11y: String get() = t("Throttle slide", "Gas-Schieber", "Glissière d'accélérateur", "Slider acceleratore", "Posuvník plynu", "Deslizador de acelerador")
val Copy.gaugeCluster: String get() = t("Gauge cluster", "Instrumententafel", "Tableau de bord", "Quadro strumenti", "Prístrojovka", "Cuadro de instrumentos")
fun Copy.relayNetwork(nodes: Int, total: Int) = t(
    "RELAY NETWORK  $nodes/$total",
    "RELAISNETZ  $nodes/$total",
    "RÉSEAU RELAIS  $nodes/$total",
    "RETE RELÈ  $nodes/$total",
    "SIEŤ RELÉ  $nodes/$total",
    "RED DE RELÉS  $nodes/$total"
)
fun Copy.cantStart(reason: String) = t("CAN'T START: $reason", "KEIN START: $reason", "DÉMARRAGE IMPOSSIBLE : $reason", "NON SI AVVIA: $reason", "NEŠTARTUJE: $reason", "NO ARRANCA: $reason")
fun Copy.cantMove(reason: String) = t("CAN'T MOVE: $reason", "KEINE FAHRT: $reason", "IMMOBILE : $reason", "NON SI MUOVE: $reason", "NEHÝBE SA: $reason", "NO SE MUEVE: $reason")
val Copy.snowChainsOn: String get() = t("SNOW · CHAINS ON", "SCHNEE · KETTEN AN", "NEIGE · CHAÎNES", "NEVE · CATENE ON", "SNEH · REŤAZE", "NIEVE · CADENAS")
val Copy.snowNoChains: String get() = t("SNOW · NO CHAINS", "SCHNEE · KEINE KETTEN", "NEIGE · SANS CHAÎNES", "NEVE · SENZA CATENE", "SNEH · BEZ REŤAZÍ", "NIEVE · SIN CADENAS")
val Copy.chainsOnDry: String get() = t("CHAINS ON DRY ROAD", "KETTEN AUF TROCKENER STRASSE", "CHAÎNES SUR ROUTE SÈCHE", "CATENE SU STRADA ASCIUTTA", "REŤAZE NA SUCHEJ CESTE", "CADENAS EN SECO")
fun Copy.surfaceInMeters(chip: String, meters: Int) = t("$chip IN $meters m", "$chip IN $meters m", "$chip DANS $meters m", "$chip TRA $meters m", "$chip O $meters m", "$chip EN $meters m")
val Copy.wheelsLocked: String get() = t("WHEELS LOCKED", "RÄDER BLOCKIERT", "ROUES BLOQUÉES", "RUOTE BLOCCATE", "KOLESÁ ZABLOKOVANÉ", "RUEDAS BLOQUEADAS")
val Copy.wheelspin: String get() = t("WHEELSPIN", "DURCHDREHEN", "PATINAGE", "SLITTAMENTO", "PRETÁČANIE", "PATINAJE")
val Copy.onRimPuncture: String get() = t("ON RIM · PUNCTURE", "AUF FELGE · PLATT", "SUR JANTE · CREVAISON", "SUL CERCHIO · FORATURA", "NA RÁFIKU · DEFEKT", "EN LLANTA · PINCHAZO")
val Copy.runningOnRim: String get() = t("RUNNING ON RIM", "AUF DER FELGE", "SUR LA JANTE", "SUL CERCHIO", "NA RÁFIKU", "EN LLANTA")
val Copy.puncture: String get() = t("PUNCTURE", "PLATTEN", "CREVAISON", "FORATURA", "DEFEKT", "PINCHAZO")
val Copy.frontPuncture: String get() = t("FRONT PUNCTURE", "PLATTEN VORNE", "CREVAISON AVANT", "FORATURA ANT.", "PREDný DEFEKT", "PINCHAZO DELANTERO")
val Copy.rearPuncture: String get() = t("REAR PUNCTURE", "PLATTEN HINTEN", "CREVAISON ARRIÈRE", "FORATURA POST.", "ZADNÝ DEFEKT", "PINCHAZO TRASERO")
val Copy.overheating: String get() = t("OVERHEATING", "ÜBERHITZUNG", "SURCHAUFFE", "SURRISCALDAMENTO", "PREHRIEVANIE", "SOBRECALENTAMIENTO")
val Copy.engineHot: String get() = t("ENGINE HOT", "MOTOR HEISS", "MOTEUR CHAUD", "MOTORE CALDO", "MOTOR HORÚCI", "MOTOR CALIENTE")
val Copy.fuelCritical: String get() = t("FUEL CRITICAL", "KRAFTSTOFF KRITISCH", "CARBURANT CRITIQUE", "CARBURANTE CRITICO", "PALIVO KRITICKÉ", "COMBUSTIBLE CRÍTICO")
val Copy.lowFuel: String get() = t("LOW FUEL", "WENIG KRAFTSTOFF", "CARBURANT BAS", "CARBURANTE BASSO", "MÁLO PALIVA", "POCO COMBUSTIBLE")
val Copy.lowOil: String get() = t("LOW OIL", "WENIG ÖL", "HUILE BASSE", "OLIO BASSO", "MÁLO OLEJA", "POCO ACEITE")
val Copy.lowCoolant: String get() = t("LOW COOLANT", "WENIG KÜHLMITTEL", "LIQUIDE BAS", "REFRIGERANTE BASSO", "MÁLO CHLADIACEJ", "POCO REFRIGERANTE")
val Copy.dirtyOil: String get() = t("DIRTY OIL", "SCHMUTZIGES ÖL", "HUILE SALE", "OLIO SPORCO", "ŠPINAVÝ OLEJ", "ACEITE SUCIO")
val Copy.wrongFuel: String get() = t("WRONG FUEL", "FALSCHER KRAFTSTOFF", "MAUVAIS CARBURANT", "CARBURANTE SBAGLIATO", "Zlé PALIVO", "COMBUSTIBLE INCORRECTO")
val Copy.badFuel: String get() = t("BAD FUEL", "SCHLECHTER KRAFTSTOFF", "CARBURANT MAUVAIS", "CARBURANTE CATTIVO", "Zlé PALIVO", "MAL COMBUSTIBLE")
val Copy.alternatorWeak: String get() = t("ALTERNATOR WEAK", "LICHTMASCHINE SCHWACH", "ALTERNATEUR FAIBLE", "ALTERNATORE DEBOLE", "SLABÝ ALTERNÁTOR", "ALTERNADOR DÉBIL")
val Copy.batteryWontHold: String get() = t(
    "The battery will not hold a charge — fit another.",
    "Die Batterie hält keine Ladung — baue eine andere ein.",
    "La batterie ne tient pas la charge — monte-en une autre.",
    "La batteria non tiene la carica — montane un'altra.",
    "Batéria nedrží nabitie — daj inú.",
    "La batería no retiene la carga: monta otra."
)
val Copy.notCharging: String get() = t("NOT CHARGING", "LÄDT NICHT", "NE CHARGE PAS", "NON CARICA", "NENABÍJA", "NO CARGA")
val Copy.batteryLow: String get() = t("BATTERY LOW", "BATTERIE SCHWACH", "BATTERIE FAIBLE", "BATTERIA BASSA", "SLABÁ BATÉRIA", "BATERÍA BAJA")
val Copy.noHeadlights: String get() = t("NO HEADLIGHTS", "KEINE SCHEINWERFER", "PAS DE PHARES", "NIENTE FARI", "BEZ SVETIEL", "SIN FAROS")
val Copy.carFallingApart: String get() = t("CAR FALLING APART", "AUTO FÄLLT AUSEINANDER", "VOITURE EN PIÈCES", "AUTO A PEZZI", "AUTO SA ROZPADÁ", "COCHE DESMORONÁNDOSE")
val Copy.paused: String get() = t("PAUSED", "PAUSE", "PAUSE", "PAUSA", "PAUZA", "PAUSA")
val Copy.pausedSubtitle: String get() = t("Time and fuel use are on hold.", "Zeit und Kraftstoffverbrauch stehen still.", "Le temps et la conso sont en pause.", "Tempo e consumo sono in pausa.", "Čas a spotreba paliva stoja.", "El tiempo y el consumo están en pausa.")
val Copy.menu: String get() = t("MENU", "MENÜ", "MENU", "MENU", "MENU", "MENÚ")
fun Copy.endRunKm(km: String) = t("END RUN · $km km", "FAHRT BEENDEN · $km km", "TERMINER · $km km", "TERMINA CORSA · $km km", "UKONČIŤ JAZDU · $km km", "TERMINAR · $km km")
val Copy.restartRun: String get() = t("RESTART RUN", "FAHRT NEU STARTEN", "RECOMMENCER", "RICOMMINCIA", "REŠTART JAZDY", "REINICIAR CARRERA")
val Copy.safeHavenTitle: String get() = t("SAFE HAVEN REACHED", "SICHERER HAFEN ERREICHT", "REFUGE ATTEINT", "RIFUGIO RAGGIUNTO", "BEZPEČNÝ PRÍSTAV", "REFUGIO ALCANZADO")
val Copy.safeHavenSubtitle: String get() = t("100 km · all seven relays online", "100 km · alle sieben Relais online", "100 km · sept relais en ligne", "100 km · tutti e sette i relè online", "100 km · všetkých sedem relé online", "100 km · los siete relés en línea")
val Copy.safeHavenBody: String get() = t(
    "The expedition has a complete ending here. You can record the arrival now, or keep this car and continue beyond the marked route.",
    "Die Expedition hat hier ein vollständiges Ende. Du kannst die Ankunft jetzt speichern oder mit diesem Auto über die markierte Strecke hinausfahren.",
    "L'expédition a ici une fin complète. Tu peux enregistrer l'arrivée ou continuer avec cette voiture au-delà de l'itinéraire.",
    "La spedizione ha qui un finale completo. Puoi registrare l'arrivo o continuare con quest'auto oltre il percorso.",
    "Expedícia tu má úplný koniec. Môžeš zapísať príchod, alebo s týmto autom pokračovať za vyznačenú trasu.",
    "La expedición tiene aquí un final completo. Puedes registrar la llegada o seguir con este coche más allá de la ruta."
)
val Copy.continueEndless: String get() = t("CONTINUE INTO ENDLESS", "WEITER IM ENDLOS", "CONTINUER EN INFINI", "CONTINUA INFINITO", "POKRAČOVAŤ DO NEKONEČNA", "CONTINUAR EN INFINITO")
val Copy.finishExpedition: String get() = t("FINISH EXPEDITION", "EXPEDITION BEENDEN", "TERMINER L'EXPÉDITION", "CONCLUDI SPEDIZIONE", "UKONČIŤ EXPEDÍCIU", "TERMINAR EXPEDICIÓN")
val Copy.journeyComplete: String get() = t("JOURNEY COMPLETE", "REISE ABGESCHLOSSEN", "VOYAGE TERMINÉ", "VIAGGIO COMPLETATO", "CESTA DOKONČENÁ", "VIAJE COMPLETO")
val Copy.runOver: String get() = t("RUN OVER", "FAHRT VORBEI", "COURSE TERMINÉE", "CORSA FINITA", "JAZDA SKONČILA", "CARRERA TERMINADA")
val Copy.newRecord: String get() = t("NEW RECORD", "NEUER REKORD", "NOUVEAU RECORD", "NUOVO RECORD", "NOVÝ REKORD", "NUEVO RÉCORD")
val Copy.retry: String get() = t("RETRY", "NOCHMAL", "RÉESSAYER", "RIPROVA", "ZNOVA", "REINTENTAR")
val Copy.scrapX2Ready: String get() = t("SCRAP x2 READY", "SCHROTT x2 BEREIT", "FERRAILLE x2 PRÊTE", "ROTTAME x2 PRONTO", "ŠROT x2 PRIPRAVENÝ", "CHATARRA x2 LISTA")
fun Copy.bankX2(scrap: Int) = t("BANK x2 · $scrap SCRAP", "BANK x2 · $scrap SCHROTT", "BANQUE x2 · $scrap FERRAILLE", "BANCA x2 · $scrap ROTTAME", "BANK x2 · $scrap ŠROT", "BANCO x2 · $scrap CHATARRA")
val Copy.noScrapToDouble: String get() = t("NO SCRAP TO DOUBLE", "KEIN SCHROTT ZUM VERDOPPELN", "PAS DE FERRAILLE À DOUBLER", "NIENTE ROTTAME DA RADDOPPIARE", "ŽIADNY ŠROT NA ZDVOJNÁSOBENIE", "SIN CHATARRA QUE DUPLICAR")
val Copy.cashOutHint: String get() = t("Cash out this run. Does not repair the car.", "Diese Fahrt auszahlen. Repariert das Auto nicht.", "Encaisser cette course. Ne répare pas la voiture.", "Incassa questa corsa. Non ripara l'auto.", "Vyber túto jazdu. Auto neopraví.", "Cobra esta carrera. No repara el coche.")
val Copy.distance: String get() = t("DISTANCE", "DISTANZ", "DISTANCE", "DISTANZA", "VZDIALENOSŤ", "DISTANCIA")
val Copy.burned: String get() = t("BURNED", "VERBRAUCHT", "BRÛLÉ", "BRUCIATO", "SPOTREBA", "QUEMADO")
val Copy.loot: String get() = t("LOOT", "BEUTE", "BUTIN", "BOTTINO", "KORISŤ", "BOTÍN")
val Copy.buildings: String get() = t("BUILDINGS", "GEBÄUDE", "BÂTIMENTS", "EDIFICI", "BUDOVY", "EDIFICIOS")
val Copy.time: String get() = t("TIME", "ZEIT", "TEMPS", "TEMPO", "ČAS", "TIEMPO")
val Copy.onYou: String get() = t("ON YOU", "BEI DIR", "SUR TOI", "SU DI TE", "NA SEBE", "ENCIMA")
val Copy.packEmpty: String get() = t("The pack is empty.", "Der Rucksack ist leer.", "Le sac est vide.", "Lo zaino è vuoto.", "Batoh je prázdny.", "La mochila está vacía.")
val Copy.pourIn: String get() = t("POUR IN", "EINFÜLLEN", "VERSER", "VERSA", "NALIAŤ", "VERTER")
val Copy.use: String get() = t("USE", "NUTZEN", "UTILISER", "USA", "POUŽIŤ", "USAR")
val Copy.forRelay: String get() = t("FOR RELAY", "FÜR RELAIS", "POUR RELAIS", "PER RELÈ", "PRE RELÉ", "PARA RELÉ")
val Copy.fit: String get() = t("FIT", "EINBAUEN", "MONTER", "MONTA", "NAMONTOVAŤ", "MONTAR")
val Copy.hitch: String get() = t("HITCH", "ANKUPPELN", "ATTELER", "AGGANCIA", "PRIPOJIŤ", "ENGANCHAR")
val Copy.trailerAlreadyAttached: String get() = t(
    "A trailer is already attached", "Ein Anhänger ist bereits angekuppelt",
    "Une remorque est déjà attelée", "Un rimorchio è già agganciato",
    "Príves je už pripojený", "Ya hay un remolque enganchado"
)
fun Copy.trailerAttached(slots: Int): String = t(
    "Utility trailer attached · +$slots cargo slots", "Nutzanhänger angekuppelt · +$slots Ladeplätze",
    "Remorque attelée · +$slots places", "Rimorchio agganciato · +$slots spazi cargo",
    "Úžitkový príves pripojený · +$slots miest", "Remolque enganchado · +$slots espacios"
)

fun Copy.stormName(kind: PursuitStormKind): String = when (kind) {
    PursuitStormKind.SAND -> t("SANDSTORM", "SANDSTURM", "TEMPÊTE DE SABLE", "TEMPESTA DI SABBIA", "PIESOČNÁ BÚRKA", "TORMENTA DE ARENA")
    PursuitStormKind.DUST -> t("DUST STORM", "STAUBSTURM", "TEMPÊTE DE POUSSIÈRE", "TEMPESTA DI POLVERE", "PRAŠNÁ BÚRKA", "TORMENTA DE POLVO")
    PursuitStormKind.THUNDER -> t("THUNDERSTORM", "GEWITTER", "ORAGE", "TEMPORALE", "BÚRKA", "TORMENTA")
    PursuitStormKind.BLIZZARD -> t("BLIZZARD", "SCHNEESTURM", "BLIZZARD", "BUFERA", "SNEHOVÁ BÚRKA", "VENTISCA")
}
fun Copy.stormBehind(kind: PursuitStormKind): String = t(
    "${stormName(kind)} BEHIND · KEEP MOVING", "${stormName(kind)} HINTER DIR · WEITERFAHREN",
    "${stormName(kind)} DERRIÈRE · CONTINUEZ", "${stormName(kind)} DIETRO · CONTINUA",
    "${stormName(kind)} ZA TEBOU · NEZASTAVUJ", "${stormName(kind)} DETRÁS · SIGUE"
)
fun Copy.stormLost(kind: PursuitStormKind, reward: Int): String = t(
    "${stormName(kind)} LOST · +$reward SCRAP", "${stormName(kind)} ABGEHÄNGT · +$reward SCHROTT",
    "${stormName(kind)} SEMÉE · +$reward FERRAILLE", "${stormName(kind)} SEMINATA · +$reward ROTTAMI",
    "${stormName(kind)} STRATENÁ · +$reward ŠROT", "${stormName(kind)} PERDIDA · +$reward CHATARRA"
)
fun Copy.stormHud(kind: PursuitStormKind, gapM: Int, caught: Boolean): String = if (caught) {
    t("${stormName(kind)} · INSIDE FRONT", "${stormName(kind)} · IM FRONT",
        "${stormName(kind)} · DANS LE FRONT", "${stormName(kind)} · NEL FRONTE",
        "${stormName(kind)} · SI V BÚRKE", "${stormName(kind)} · DENTRO DEL FRENTE")
} else {
    t("${stormName(kind)} · $gapM m BEHIND", "${stormName(kind)} · $gapM m HINTER DIR",
        "${stormName(kind)} · À $gapM m", "${stormName(kind)} · $gapM m DIETRO",
        "${stormName(kind)} · $gapM m ZA TEBOU", "${stormName(kind)} · $gapM m DETRÁS")
}
val Copy.stormEscapeHint: String get() = t(
    "RUN AWAY OR FIND SHELTER", "FLIEH ODER SUCHE SCHUTZ", "FUIS OU TROUVE UN ABRI",
    "SCAPPA O TROVA RIPARO", "UTEČ ALEBO NÁJDI ÚKRYT", "HUYE O BUSCA REFUGIO"
)
val Copy.stormShelterTitle: String get() = t(
    "STORM SHELTER", "STURMSCHUTZ", "ABRI ANTI-TEMPÊTE", "RIPARO DALLA TEMPESTA",
    "ÚKRYT PRED BÚRKOU", "REFUGIO DE LA TORMENTA"
)
val Copy.stormShelterBody: String get() = t(
    "Wait inside until the moving front passes. Escaping by car gives scrap; sheltering is safe but gives no reward.",
    "Warte drinnen, bis die Front vorbeizieht. Flucht per Auto gibt Schrott; Schutz ist sicher, aber ohne Belohnung.",
    "Attends à l'intérieur que le front passe. La fuite en voiture donne de la ferraille ; l'abri est sûr mais sans récompense.",
    "Aspetta dentro che il fronte passi. La fuga in auto dà rottami; il riparo è sicuro ma senza ricompensa.",
    "Počkaj vnútri, kým pohybujúci sa front prejde. Útek autom dá šrot; úkryt je bezpečný, ale bez odmeny.",
    "Espera dentro hasta que pase el frente. Escapar en coche da chatarra; refugiarse es seguro pero sin recompensa."
)
val Copy.findShelter: String get() = t("TAKE SHELTER", "SCHUTZ SUCHEN", "SE METTRE À L'ABRI", "RIPARATI", "UKRYŤ SA", "REFUGIARSE")
fun Copy.shelteredFromStorm(kind: PursuitStormKind, building: String) = t(
    "${stormName(kind)} passed while you sheltered in the $building",
    "${stormName(kind)} zog vorbei, während du im $building Schutz gesucht hast",
    "${stormName(kind)} est passée pendant que tu étais à l'abri dans $building",
    "${stormName(kind)} è passata mentre eri al riparo in $building",
    "${stormName(kind)} prešla, kým si bol ukrytý v: $building",
    "${stormName(kind)} pasó mientras te refugiabas en $building"
)
val Copy.toBoot: String get() = t("TO BOOT", "IN KOFFERRAUM", "AU COFFRE", "NEL BAGAGLIAIO", "DO KUFRU", "AL MALETERO")
val Copy.take: String get() = t("TAKE", "NEHMEN", "PRENDRE", "PRENDI", "VZIAŤ", "COGER")
val Copy.boot: String get() = t("BOOT", "KOFFERRAUM", "COFFRE", "BAGAGLIAIO", "KUFOR", "MALETERO")
val Copy.bootStopAtCar: String get() = t("BOOT — STOP AT THE CAR", "KOFFERRAUM — AM AUTO HALTEN", "COFFRE — ARRÊTE-TOI À LA VOITURE", "BAGAGLIAIO — FERMATI ALL'AUTO", "KUFOR — ZASTAV PRI AUTE", "MALETERO — PARA EN EL COCHE")
val Copy.bootEmpty: String get() = t("The boot is empty.", "Der Kofferraum ist leer.", "Le coffre est vide.", "Il bagagliaio è vuoto.", "Kufor je prázdny.", "El maletero está vacío.")
fun Copy.packSubtitle(scrap: Int, packUsed: Int, packSlots: Int, packW: Int, packMax: Int, bootUsed: Int, bootSlots: Int, bootW: Int, bootMax: Int) = t(
    "Scrap $scrap · On you $packUsed/$packSlots · $packW/$packMax kg   ·   Boot $bootUsed/$bootSlots · $bootW/$bootMax kg",
    "Schrott $scrap · Bei dir $packUsed/$packSlots · $packW/$packMax kg   ·   Kofferraum $bootUsed/$bootSlots · $bootW/$bootMax kg",
    "Ferraille $scrap · Sur toi $packUsed/$packSlots · $packW/$packMax kg   ·   Coffre $bootUsed/$bootSlots · $bootW/$bootMax kg",
    "Rottame $scrap · Su di te $packUsed/$packSlots · $packW/$packMax kg   ·   Bagagliaio $bootUsed/$bootSlots · $bootW/$bootMax kg",
    "Šrot $scrap · Na sebe $packUsed/$packSlots · $packW/$packMax kg   ·   Kufor $bootUsed/$bootSlots · $bootW/$bootMax kg",
    "Chatarra $scrap · Encima $packUsed/$packSlots · $packW/$packMax kg   ·   Maletero $bootUsed/$bootSlots · $bootW/$bootMax kg"
)
val Copy.inTheCar: String get() = t("IN THE CAR", "IM AUTO", "DANS LA VOITURE", "IN AUTO", "V AUTE", "EN EL COCHE")
val Copy.parts: String get() = t("PARTS", "TEILE", "PIÈCES", "PEZZI", "DIELY", "PIEZAS")
val Copy.permanent: String get() = t("PERMANENT", "DAUERHAFT", "PERMANENT", "PERMANENTE", "TRVALÉ", "PERMANENTE")
val Copy.oil: String get() = t("OIL", "ÖL", "HUILE", "OLIO", "OLEJ", "ACEITE")
val Copy.coolant: String get() = t("COOLANT", "KÜHLMITTEL", "LIQUIDE", "REFRIGERANTE", "CHLADIACA", "REFRIGERANTE")
val Copy.scrap: String get() = t("SCRAP", "SCHROTT", "FERRAILLE", "ROTTAME", "ŠROT", "CHATARRA")
val Copy.paint: String get() = t("PAINT", "LACK", "PEINTURE", "VERNICE", "LAK", "PINTURA")
val Copy.rear: String get() = t("REAR", "HINTEN", "ARRIÈRE", "POSTERIORE", "ZADOK", "TRASERO")
val Copy.front: String get() = t("FRONT", "VORNE", "AVANT", "ANTERIORE", "PREDOK", "DELANTERO")
val Copy.middle: String get() = t("MIDDLE", "MITTE", "MILIEU", "CENTRO", "STRED", "CENTRO")
val Copy.bodyShell: String get() = t("Body", "Karosserie", "Carrosserie", "Carrozzeria", "Karoséria", "Carrocería")
val Copy.bodyShellHint: String get() = t(
    "Dirt and rust from the road — respray clears it.",
    "Schmutz und Rost von der Straße — Neulackierung entfernt es.",
    "Saleté et rouille de la route — une peinture l'enlève.",
    "Sporcizia e ruggine dalla strada — la riverniciatura le toglie.",
    "Špina a hrdza z cesty — prelakovanie to zmyje.",
    "Suciedad y óxido del camino: repintar lo limpia."
)
val Copy.takeAll: String get() = t("TAKE ALL", "ALLES NEHMEN", "TOUT PRENDRE", "PRENDI TUTTO", "VZIAŤ VŠETKO", "COGER TODO")
val Copy.scrapAll: String get() = t("SCRAP ALL", "ALLES VERSCHROTTEN", "TOUT FERRAILLER", "ROTTAMA TUTTO", "ZOŠROTOVAŤ VŠETKO", "CHATARRIZAR TODO")
val Copy.relayStation: String get() = t("RELAY STATION", "RELAISSTATION", "STATION RELAIS", "STAZIONE RELÈ", "STANICA RELÉ", "ESTACIÓN DE RELÉ")
val Copy.relayRestored: String get() = t("RELAY RESTORED", "RELAIS WIEDERHERGESTELLT", "RELAIS RESTAURÉ", "RELÈ RIPRISTINATO", "RELÉ OBNOVENÉ", "RELÉ RESTAURADO")
val Copy.relayCheckpoint: String get() = t("RELAY CHECKPOINT", "RELAIS-CHECKPOINT", "POINT RELAIS", "CHECKPOINT RELÈ", "KONTROLNÝ BOD RELÉ", "PUNTO DE RELÉ")
val Copy.restoreRelay: String get() = t("RESTORE RELAY", "RELAIS WIEDERHERSTELLEN", "RESTAURER LE RELAIS", "RIPRISTINA RELÈ", "OBNOVIŤ RELÉ", "RESTAURAR RELÉ")
val Copy.restorePreviousFirst: String get() = t("RESTORE PREVIOUS RELAY FIRST", "ERST VORHERIGES RELAIS", "RESTAURE D'ABORD LE RELAIS PRÉCÉDENT", "PRIMA IL RELÈ PRECEDENTE", "NAJPRV OBNOV PREDCHÁDZAJÚCE", "RESTAURA ANTES EL ANTERIOR")
fun Copy.lootableWreck(meters: Int) = t("LOOTABLE WRECK · $meters m", "PLÜNDERBARES WRACK · $meters m", "ÉPAVE À FOUILLER · $meters m", "RELITTO SACCHEGGIABILE · $meters m", "VRAK NA PREHĽADANIE · $meters m", "PECIO SAQUEABLE · $meters m")
val Copy.upgrades: String get() = t("UPGRADES", "UPGRADES", "AMÉLIORATIONS", "UPGRADE", "VYLEPŠENIA", "MEJORAS")
val Copy.soldOut: String get() = t("SOLD OUT", "AUSVERKAUFT", "ÉPUISÉ", "ESAURITO", "VYPREDANÉ", "AGOTADO")
val Copy.watchAd: String get() = t("WATCH AD", "WERBUNG SEHEN", "VOIR PUB", "GUARDA ADS", "POZRI REKLAMU", "VER ANUNCIO")
val Copy.adLoading: String get() = t("AD LOADING", "WERBUNG LÄDT", "PUB EN CHARGEMENT", "ADS IN CARICAMENTO", "REKLAMA SA NAČÍTAVA", "ANUNCIO CARGANDO")
val Copy.alreadyOnTheCar: String get() = t("ALREADY ON THE CAR", "SCHON AM AUTO", "DÉJÀ SUR LA VOITURE", "GIÀ SULL'AUTO", "UŽ NA AUTE", "YA EN EL COCHE")
fun Copy.resprayCost(cost: Int) = t("RESPRAY −$cost", "NEULACKIEREN −$cost", "REPEINDRE −$cost", "RIVERNICIA −$cost", "PRELAKOVAŤ −$cost", "REPINAR −$cost")
val Copy.service: String get() = t("SERVICE", "SERVICE", "SERVICE", "SERVIZIO", "SERVIS", "SERVICIO")
val Copy.cancel: String get() = t("CANCEL", "ABBRECHEN", "ANNULER", "ANNULLA", "ZRUŠIŤ", "CANCELAR")
val Copy.drainOil: String get() = t("DRAIN OIL", "ÖL ABLASSEN", "VIDANGER HUILE", "SCARICA OLIO", "VYPUSTIŤ OLEJ", "VACIAR ACEITE")
val Copy.drainFuel: String get() = t("DRAIN FUEL", "KRAFTSTOFF ABLASSEN", "VIDANGER CARBURANT", "SCARICA CARBURANTE", "VYPUSTIŤ PALIVO", "VACIAR COMBUSTIBLE")
val Copy.drainCoolant: String get() = t("DRAIN COOLANT", "KÜHLMITTEL ABLASSEN", "VIDANGER LIQUIDE", "SCARICA REFRIGERANTE", "VYPUSTIŤ CHLADIACU", "VACIAR REFRIGERANTE")
fun Copy.drainFluid(name: String) = t("DRAIN ${name.uppercase()}", "${name.uppercase()} ABLASSEN", "VIDANGER ${name.uppercase()}", "SCARICA ${name.uppercase()}", "VYPUSTIŤ ${name.uppercase()}", "VACIAR ${name.uppercase()}")
val Copy.patchKit: String get() = t("PATCH KIT", "FLICKENSET", "KIT CREVAISON", "KIT FORATURA", "SADA NA DEFEKT", "KIT PINCHAZO")
val Copy.swap: String get() = t("SWAP", "TAUSCHEN", "ÉCHANGER", "SCAMBIA", "VYMĚNIŤ", "CAMBIAR")
val Copy.atWorkshop: String get() = t("AT WORKSHOP", "IN DER WERKSTATT", "À L'ATELIER", "IN OFFICINA", "V DIELNI", "EN EL TALLER")
val Copy.tapAgain: String get() = t("TAP AGAIN", "NOCHMAL TIPPEN", "TAPER ENCORE", "TOCCA DI NUOVO", "ŤUKNI ZNOVA", "TOCA OTRA VEZ")
val Copy.remove: String get() = t("REMOVE", "AUSBAUEN", "RETIRER", "RIMUOVI", "VYMONTOVAŤ", "QUITAR")
val Copy.debug: String get() = t("DEBUG", "DEBUG", "DEBUG", "DEBUG", "LADENIE", "DEBUG")
val Copy.swapTyres: String get() = t("SWAP TYRES", "REIFEN TAUSCHEN", "ÉCHANGER PNEUS", "SCAMBIA GOMME", "VYMĚNIŤ GUMY", "CAMBIAR NEUMÁTICOS")
val Copy.onRim: String get() = t("ON RIM", "AUF FELGE", "SUR JANTE", "SUL CERCHIO", "NA RÁFIKU", "EN LLANTA")
val Copy.flat: String get() = t("FLAT", "PLATT", "À PLAT", "SFORATA", "PRÁZDNA", "DESINCHADO")
val Copy.patchesAFlat: String get() = t("PATCHES A FLAT", "FLICKT EINEN PLATTEN", "RÉPARE UNE CREVAISON", "RIPARA UNA FORATURA", "ZÁPLATA NA DEFEKT", "REPARA UN PINCHAZO")
fun Copy.fillKind(kind: String) = t("FILL ${kind.uppercase()}", "${kind.uppercase()} TANKEN", "REMPLIR ${kind.uppercase()}", "RIEMPI ${kind.uppercase()}", "NATANKOVAŤ ${kind.uppercase()}", "LLENAR ${kind.uppercase()}")
fun Copy.yourEngine(kind: String) = t("${kind.uppercase()} · YOUR ENGINE", "${kind.uppercase()} · DEIN MOTOR", "${kind.uppercase()} · TON MOTEUR", "${kind.uppercase()} · IL TUO MOTORE", "${kind.uppercase()} · TVOJ MOTOR", "${kind.uppercase()} · TU MOTOR")
fun Copy.bodyPaint(name: String) = t("Body paint: $name", "Karosserielack: $name", "Peinture : $name", "Vernice: $name", "Lak karosérie: $name", "Pintura: $name")
fun Copy.quality(name: String) = t("Quality: $name", "Qualität: $name", "Qualité : $name", "Qualità: $name", "Kvalita: $name", "Calidad: $name")
fun Copy.nothingLeft(): String = t("Nothing left here.", "Hier ist nichts mehr.", "Plus rien ici.", "Non resta niente.", "Už tu nič nie je.", "No queda nada.")
fun Copy.thingsToTake(n: Int) = t("$n things to take", "$n Dinge zum Mitnehmen", "$n choses à prendre", "$n cose da prendere", "$n vecí na vzatie", "$n cosas para coger")
fun Copy.paintAfter10(scrap: Int) = t("Paint after 10 km · $scrap scrap", "Lackieren nach 10 km · $scrap Schrott", "Peinture après 10 km · $scrap ferraille", "Vernice dopo 10 km · $scrap rottame", "Lak po 10 km · $scrap šrotu", "Pintura tras 10 km · $scrap chatarra")
fun Copy.resprayScrap(scrap: Int) = t("Respray · $scrap scrap", "Neulackieren · $scrap Schrott", "Repeindre · $scrap ferraille", "Rivernicia · $scrap rottame", "Prelakovať · $scrap šrotu", "Repintar · $scrap chatarra")
fun Copy.upgradesScrap(scrap: Int) = t("Upgrades · $scrap scrap", "Upgrades · $scrap Schrott", "Améliorations · $scrap ferraille", "Upgrade · $scrap rottame", "Vylepšenia · $scrap šrotu", "Mejoras · $scrap chatarra")
val Copy.adLoadingRetry: String get() = t("AD LOADING · TAP TO RETRY", "WERBUNG LÄDT · NOCHMAL TIPPEN", "PUB EN CHARGEMENT · RÉESSAYER", "ADS IN CARICAMENTO · RIPROVA", "REKLAMA SA NAČÍTAVA · ŤUKNI ZNOVA", "ANUNCIO CARGANDO · TOCA PARA REINTENTAR")
fun Copy.roadAssistWatchAd(quota: String) = t("ROAD ASSIST · WATCH AD  $quota", "STRASSENHILFE · WERBUNG  $quota", "ASSISTANCE · VOIR PUB  $quota", "ASSISTENZA · GUARDA ADS  $quota", "ASISTENCIA · POZRI REKLAMU  $quota", "ASISTENCIA · VER ANUNCIO  $quota")
fun Copy.batteryWatchAd(quota: String) = t("BATTERY · WATCH AD  $quota", "BATTERIE · WERBUNG  $quota", "BATTERIE · VOIR PUB  $quota", "BATTERIA · GUARDA ADS  $quota", "BATÉRIA · POZRI REKLAMU  $quota", "BATERÍA · VER ANUNCIO  $quota")
fun Copy.adLoadingQuota(quota: String) = "$adLoadingRetry  $quota"

val Copy.driver: String get() = t("DRIVER", "FAHRER", "CONDUCTEUR", "GUIDATORE", "VODIČ", "CONDUCTOR")
val Copy.personalBest: String get() = t("PERSONAL BEST", "PERSÖNLICHE BESTE", "RECORD PERSONNEL", "RECORD PERSONALE", "OSOBNÝ REKORD", "RÉCORD PERSONAL")
val Copy.player: String get() = t("PLAYER", "SPIELER", "JOUEUR", "GIOCATORE", "HRÁČ", "JUGADOR")
val Copy.editProfile: String get() = t("EDIT PROFILE", "PROFIL BEARBEITEN", "MODIFIER LE PROFIL", "MODIFICA PROFILO", "UPRAVIŤ PROFIL", "EDITAR PERFIL")
val Copy.leaderboardSubtitle: String get() = t("Distance first · time shown for every run", "Distanz zuerst · Zeit für jede Fahrt", "Distance d'abord · temps pour chaque course", "Prima la distanza · tempo per ogni corsa", "Najprv vzdialenosť · čas pri každej jazde", "Primero distancia · tiempo de cada carrera")
val Copy.playGamesUnavailable: String get() = t("Play Games sign-in unavailable", "Play Games-Anmeldung nicht verfügbar", "Connexion Play Games indisponible", "Accesso Play Games non disponibile", "Prihlásenie do Play Games nedostupné", "Inicio de Play Games no disponible")
val Copy.playGamesSignIn: String get() = t("SIGN IN TO PLAY GAMES", "BEI PLAY GAMES ANMELDEN", "CONNEXION PLAY GAMES", "ACCEDI A PLAY GAMES", "PRIHLÁSIŤ DO PLAY GAMES", "INICIAR SESIÓN EN PLAY GAMES")
val Copy.playGamesConnecting: String get() = t("CONNECTING PLAY GAMES…", "PLAY GAMES WIRD VERBUNDEN…", "CONNEXION PLAY GAMES…", "COLLEGAMENTO PLAY GAMES…", "PRIPÁJAM PLAY GAMES…", "CONECTANDO PLAY GAMES…")
fun Copy.playGamesSignedInAs(name: String) = t(
    "Play Games · $name",
    "Play Games · $name",
    "Play Games · $name",
    "Play Games · $name",
    "Play Games · $name",
    "Play Games · $name"
)
val Copy.playGamesSignedIn: String get() = t("Play Games · signed in", "Play Games · angemeldet", "Play Games · connecté", "Play Games · connesso", "Play Games · prihlásený", "Play Games · conectado")
val Copy.friendsHint: String get() = t(
    "Friends scores are loaded from Play Games when the service is connected.",
    "Freundeswerte kommen von Play Games, wenn der Dienst verbunden ist.",
    "Les scores des amis viennent de Play Games lorsque le service est connecté.",
    "I punteggi degli amici arrivano da Play Games quando il servizio è collegato.",
    "Skóre priateľov sa načítajú z Play Games, keď je služba pripojená.",
    "Las puntuaciones de amigos se cargan de Play Games cuando el servicio está conectado."
)
val Copy.countryHint: String get() = t(
    "Country rankings need a country-aware backend; Play Games offers Friends and Public collections.",
    "Länderrankings brauchen ein länderbewusstes Backend; Play Games bietet Freunde und Öffentlich.",
    "Le classement pays exige un backend par pays ; Play Games propose Amis et Public.",
    "Le classifiche nazionali richiedono un backend per paese; Play Games offre Amici e Pubblico.",
    "Krajiny potrebujú backend podľa krajiny; Play Games ponúka Priateľov a Verejné.",
    "Las clasificaciones por país necesitan un backend; Play Games ofrece Amigos y Público."
)
val Copy.worldHint: String get() = t(
    "World scores are loaded from the public Play Games leaderboard when configured.",
    "Weltwerte kommen von der öffentlichen Play-Games-Rangliste, wenn konfiguriert.",
    "Les scores mondiaux viennent du classement public Play Games s'il est configuré.",
    "I punteggi mondiali arrivano dalla classifica pubblica Play Games se configurata.",
    "Svetové skóre sa načítajú z verejného rebríčka Play Games, ak je nastavený.",
    "Las puntuaciones mundiales se cargan de la clasificación pública de Play Games."
)
fun Copy.tabLabel(tab: LeaderboardTab) = when (tab) {
    LeaderboardTab.FRIENDS -> t("FRIENDS", "FREUNDE", "AMIS", "AMICI", "PRIATELIA", "AMIGOS")
    LeaderboardTab.COUNTRY -> t("COUNTRY", "LAND", "PAYS", "PAESE", "KRAJINA", "PAÍS")
    LeaderboardTab.WORLD -> t("WORLD", "WELT", "MONDE", "MONDO", "SVET", "MUNDO")
}
val Copy.driverProfile: String get() = t("DRIVER PROFILE", "FAHRERPROFIL", "PROFIL CONDUCTEUR", "PROFILO GUIDATORE", "PROFIL VODIČA", "PERFIL DE CONDUCTOR")
val Copy.profileRequired: String get() = t("Set your identity before the first run.", "Lege deine Identität vor der ersten Fahrt fest.", "Définis ton identité avant la première course.", "Imposta la tua identità prima della prima corsa.", "Nastav identitu pred prvou jazdou.", "Define tu identidad antes de la primera carrera.")
val Copy.profileOptional: String get() = t("Your name and country shown on the road.", "Dein Name und Land auf der Straße.", "Ton nom et ton pays sur la route.", "Il tuo nome e paese sulla strada.", "Tvoje meno a krajina na ceste.", "Tu nombre y país en la carretera.")
val Copy.nickname: String get() = t("NICKNAME", "SPITZNAME", "PSEUDO", "NICKNAME", "PREZÝVKA", "APODO")
val Copy.nicknameHint: String get() = t("e.g. ROADRUNNER", "z. B. ROADRUNNER", "ex. ROADRUNNER", "es. ROADRUNNER", "napr. ROADRUNNER", "p. ej. ROADRUNNER")
val Copy.usePlayGamesName: String get() = t("USE PLAY GAMES NAME", "PLAY-GAMES-NAMEN NUTZEN", "UTILISER LE NOM PLAY GAMES", "USA NOME PLAY GAMES", "POUŽIŤ MENO Z PLAY GAMES", "USAR NOMBRE DE PLAY GAMES")
val Copy.country: String get() = t("COUNTRY", "LAND", "PAYS", "PAESE", "KRAJINA", "PAÍS")
val Copy.tapFlag: String get() = t("Tap the flag to choose a country", "Tippe die Flagge, um ein Land zu wählen", "Touche le drapeau pour choisir un pays", "Tocca la bandiera per scegliere un paese", "Ťukni na vlajku a vyber krajinu", "Toca la bandera para elegir un país")
val Copy.playGamesNameHint: String get() = t(
    "Play Games can supply the display name after the online service is connected. Until then, this local profile is used.",
    "Play Games kann den Anzeigenamen liefern, sobald der Onlinedienst verbunden ist. Bis dahin gilt dieses lokale Profil.",
    "Play Games peut fournir le nom après connexion. En attendant, ce profil local est utilisé.",
    "Play Games può fornire il nome dopo la connessione. Fino ad allora si usa questo profilo locale.",
    "Play Games môže dodať meno po pripojení online služby. Dovtedy platí tento miestny profil.",
    "Play Games puede dar el nombre tras conectar. Hasta entonces se usa este perfil local."
)
val Copy.saveProfile: String get() = t("SAVE PROFILE", "PROFIL SPEICHERN", "ENREGISTRER LE PROFIL", "SALVA PROFILO", "ULOŽIŤ PROFIL", "GUARDAR PERFIL")
val Copy.selectCountry: String get() = t("SELECT COUNTRY", "LAND WÄHLEN", "CHOISIR UN PAYS", "SCEGLI PAESE", "VYBER KRAJINU", "ELEGIR PAÍS")
fun Copy.countryName(code: String) = when (code) {
    "SK" -> t("Slovakia", "Slowakei", "Slovaquie", "Slovacchia", "Slovensko", "Eslovaquia")
    "CZ" -> t("Czechia", "Tschechien", "Tchéquie", "Cechia", "Česko", "Chequia")
    "PL" -> t("Poland", "Polen", "Pologne", "Polonia", "Poľsko", "Polonia")
    "AT" -> t("Austria", "Österreich", "Autriche", "Austria", "Rakúsko", "Austria")
    "HU" -> t("Hungary", "Ungarn", "Hongrie", "Ungheria", "Maďarsko", "Hungría")
    "DE" -> t("Germany", "Deutschland", "Allemagne", "Germania", "Nemecko", "Alemania")
    "CH" -> t("Switzerland", "Schweiz", "Suisse", "Svizzera", "Švajčiarsko", "Suiza")
    "SI" -> t("Slovenia", "Slowenien", "Slovénie", "Slovenia", "Slovinsko", "Eslovenia")
    "HR" -> t("Croatia", "Kroatien", "Croatie", "Croazia", "Chorvátsko", "Croacia")
    "RS" -> t("Serbia", "Serbien", "Serbie", "Serbia", "Srbsko", "Serbia")
    "UA" -> t("Ukraine", "Ukraine", "Ukraine", "Ucraina", "Ukrajina", "Ucrania")
    "RO" -> t("Romania", "Rumänien", "Roumanie", "Romania", "Rumunsko", "Rumanía")
    "BG" -> t("Bulgaria", "Bulgarien", "Bulgarie", "Bulgaria", "Bulharsko", "Bulgaria")
    "FR" -> t("France", "Frankreich", "France", "Francia", "Francúzsko", "Francia")
    "IT" -> t("Italy", "Italien", "Italie", "Italia", "Taliansko", "Italia")
    "ES" -> t("Spain", "Spanien", "Espagne", "Spagna", "Španielsko", "España")
    "GB" -> t("United Kingdom", "Vereinigtes Königreich", "Royaume-Uni", "Regno Unito", "Spojené kráľovstvo", "Reino Unido")
    "IE" -> t("Ireland", "Irland", "Irlande", "Irlanda", "Írsko", "Irlanda")
    "NL" -> t("Netherlands", "Niederlande", "Pays-Bas", "Paesi Bassi", "Holandsko", "Países Bajos")
    "BE" -> t("Belgium", "Belgien", "Belgique", "Belgio", "Belgicko", "Bélgica")
    "DK" -> t("Denmark", "Dänemark", "Danemark", "Danimarca", "Dánsko", "Dinamarca")
    "SE" -> t("Sweden", "Schweden", "Suède", "Svezia", "Švédsko", "Suecia")
    "NO" -> t("Norway", "Norwegen", "Norvège", "Norvegia", "Nórsko", "Noruega")
    "FI" -> t("Finland", "Finnland", "Finlande", "Finlandia", "Fínsko", "Finlandia")
    "US" -> t("United States", "Vereinigte Staaten", "États-Unis", "Stati Uniti", "Spojené štáty", "Estados Unidos")
    "CA" -> t("Canada", "Kanada", "Canada", "Canada", "Kanada", "Canadá")
    "AU" -> t("Australia", "Australien", "Australie", "Australia", "Austrália", "Australia")
    "BR" -> t("Brazil", "Brasilien", "Brésil", "Brasile", "Brazília", "Brasil")
    "JP" -> t("Japan", "Japan", "Japon", "Giappone", "Japonsko", "Japón")
    "IN" -> t("India", "Indien", "Inde", "India", "India", "India")
    else -> code
}

fun Copy.slotName(slot: ComponentSlot): String = when (slot) {
    ComponentSlot.ENGINE -> t("Engine", "Motor", "Moteur", "Motore", "Motor", "Motor")
    ComponentSlot.ALTERNATOR -> t("Alternator", "Lichtmaschine", "Alternateur", "Alternatore", "Alternátor", "Alternador")
    ComponentSlot.STARTER -> t("Starter", "Anlasser", "Démarreur", "Motorino", "Štartér", "Motor de arranque")
    ComponentSlot.RADIATOR -> t("Radiator", "Kühler", "Radiateur", "Radiatore", "Chladič", "Radiador")
    ComponentSlot.BATTERY -> t("Battery", "Batterie", "Batterie", "Batteria", "Batéria", "Batería")
    ComponentSlot.FUEL_TANK -> t("Fuel tank", "Kraftstofftank", "Réservoir", "Serbatoio", "Palivová nádrž", "Depósito")
    ComponentSlot.BRAKES -> t("Brakes", "Bremsen", "Freins", "Freni", "Brzdy", "Frenos")
    ComponentSlot.TIRE_FRONT -> t("Front tyre", "Vorderreifen", "Pneu avant", "Gomma anteriore", "Predná pneumatika", "Neumático delantero")
    ComponentSlot.TIRE_REAR -> t("Rear tyre", "Hinterreifen", "Pneu arrière", "Gomma posteriore", "Zadná pneumatika", "Neumático trasero")
    ComponentSlot.DRIVETRAIN -> t("Drivetrain", "Antrieb", "Transmission", "Trasmissione", "Pohon", "Transmisión")
    ComponentSlot.SUSPENSION -> t("Suspension", "Federung", "Suspension", "Sospensioni", "Pruženie", "Suspensión")
    ComponentSlot.DOOR_FRONT -> t("Front door", "Vordertür", "Porte avant", "Porta anteriore", "Predné dvere", "Puerta delantera")
    ComponentSlot.DOOR_REAR -> t("Rear door", "Hintertür", "Porte arrière", "Porta posteriore", "Zadné dvere", "Puerta trasera")
    ComponentSlot.HOOD -> t("Hood", "Motorhaube", "Capot", "Cofano", "Kapota", "Capó")
    ComponentSlot.SEAT_FRONT -> t("Front seat", "Vordersitz", "Siège avant", "Sedile anteriore", "Predné sedadlo", "Asiento delantero")
    ComponentSlot.SEAT_REAR -> t("Rear seat", "Rücksitz", "Siège arrière", "Sedile posteriore", "Zadné sedadlo", "Asiento trasero")
    ComponentSlot.TRUNK_LID -> t("Boot lid", "Kofferraumdeckel", "Hayon", "Portellone", "Veko kufra", "Portón")
    ComponentSlot.HEADLIGHT -> t("Headlight", "Scheinwerfer", "Phare", "Faro", "Svetlomet", "Faro")
    ComponentSlot.TAILLIGHT -> t("Tail light", "Rücklicht", "Feu arrière", "Faro posteriore", "Zadné svetlo", "Piloto")
    ComponentSlot.FRONT_BUMPER -> t("Front bumper", "Frontstoßstange", "Pare-chocs avant", "Paraurti anteriore", "Predný nárazník", "Parachoques delantero")
    ComponentSlot.REAR_BUMPER -> t("Rear bumper", "Heckstoßstange", "Pare-chocs arrière", "Paraurti posteriore", "Zadný nárazník", "Parachoques trasero")
    ComponentSlot.CHAINS -> t("Snow chains", "Schneeketten", "Chaînes à neige", "Catene da neve", "Snehové reťaze", "Cadenas de nieve")
    ComponentSlot.CARGO -> t("Cargo box", "Ladekiste", "Caisse cargo", "Cassa da carico", "Nákladná debna", "Caja de carga")
    ComponentSlot.ROOF_RACK -> t("Roof rack", "Dachträger", "Galerie", "Portapacchi", "Strešný nosič", "Baca")
}

fun Copy.itemName(def: ItemDef): String = itemName(def.id) ?: def.name
fun Copy.itemName(id: String): String? = when (id) {
    ItemCatalog.UTILITY_TRAILER.id -> t("Utility trailer", "Nutzanhänger", "Remorque utilitaire", "Rimorchio", "Úžitkový príves", "Remolque utilitario")
    ItemCatalog.MISSION_PACKAGE.id -> t("Mission package", "Missionspaket", "Colis de mission", "Pacco missione", "Balík pre misiu", "Paquete de misión")
    "fuel_can" -> t("Petrol", "Benzin", "Essence", "Benzina", "Benzín", "Gasolina")
    "diesel_can" -> t("Diesel", "Diesel", "Diesel", "Diesel", "Nafta", "Diésel")
    "oil" -> t("Engine oil", "Motoröl", "Huile moteur", "Olio motore", "Motorový olej", "Aceite de motor")
    "puncture_kit" -> t("Puncture kit", "Flickenset", "Kit crevaison", "Kit foratura", "Sada na defekt", "Kit antipinchazos")
    "relay_module" -> t("Relay module", "Relais-Modul", "Module relais", "Modulo relè", "Modul relé", "Módulo de relé")
    "water" -> t("Water", "Wasser", "Eau", "Acqua", "Voda", "Agua")
    "coolant" -> t("Coolant", "Kühlmittel", "Liquide de refroidissement", "Refrigerante", "Chladiaca kvapalina", "Refrigerante")
    "tire_poor" -> t("Tyre (poor)", "Reifen (schwach)", "Pneu (faible)", "Gomma (scarsa)", "Pneumatika (slabá)", "Neumático (flojo)")
    "tire_std" -> t("Tyre (standard)", "Reifen (Standard)", "Pneu (standard)", "Gomma (standard)", "Pneumatika (štandard)", "Neumático (estándar)")
    "tire_sport" -> t("Tyre (sport)", "Reifen (Sport)", "Pneu (sport)", "Gomma (sport)", "Pneumatika (šport)", "Neumático (sport)")
    "tire_winter" -> t("Tyre (winter)", "Reifen (Winter)", "Pneu (hiver)", "Gomma (inverno)", "Pneumatika (zimná)", "Neumático (invierno)")
    "snow_chains" -> t("Snow chains", "Schneeketten", "Chaînes à neige", "Catene da neve", "Snehové reťaze", "Cadenas de nieve")
    "tire_offroad" -> t("Tyre (off-road)", "Reifen (Gelände)", "Pneu (tout-terrain)", "Gomma (fuoristrada)", "Pneumatika (terénna)", "Neumático (todoterreno)")
    "battery_std" -> t("Battery 45Ah", "Batterie 45Ah", "Batterie 45Ah", "Batteria 45Ah", "Batéria 45Ah", "Batería 45Ah")
    "battery_good" -> t("Battery 70Ah", "Batterie 70Ah", "Batterie 70Ah", "Batteria 70Ah", "Batéria 70Ah", "Batería 70Ah")
    "engine_a" -> t("1.4 petrol · 92 hp", "1.4 Benzin · 92 PS", "1.4 essence · 92 ch", "1.4 benzina · 92 cv", "1.4 benzín · 92 k", "1.4 gasolina · 92 cv")
    "engine_d" -> t("1.6 diesel · 110 hp", "1.6 Diesel · 110 PS", "1.6 diesel · 110 ch", "1.6 diesel · 110 cv", "1.6 nafta · 110 k", "1.6 diésel · 110 cv")
    "engine_b" -> t("2.0 petrol · 135 hp", "2.0 Benzin · 135 PS", "2.0 essence · 135 ch", "2.0 benzina · 135 cv", "2.0 benzín · 135 k", "2.0 gasolina · 135 cv")
    "engine_e" -> t("2.5 V6 · 180 hp", "2.5 V6 · 180 PS", "2.5 V6 · 180 ch", "2.5 V6 · 180 cv", "2.5 V6 · 180 k", "2.5 V6 · 180 cv")
    "engine_c" -> t("2.2 turbodiesel · 230 hp", "2.2 Turbodiesel · 230 PS", "2.2 turbodiesel · 230 ch", "2.2 turbodiesel · 230 cv", "2.2 turbodiesel · 230 k", "2.2 turbodiésel · 230 cv")
    "radiator" -> t("Radiator", "Kühler", "Radiateur", "Radiatore", "Chladič", "Radiador")
    "radiator_good" -> t("Radiator XL", "Kühler XL", "Radiateur XL", "Radiatore XL", "Chladič XL", "Radiador XL")
    "radiator_hd" -> t("Heavy-duty radiator", "Schwerlastkühler", "Radiateur renforcé", "Radiatore rinforzato", "Ťažký chladič", "Radiador reforzado")
    "brakes" -> t("Brakes", "Bremsen", "Freins", "Freni", "Brzdy", "Frenos")
    "brakes_good" -> t("Sport brakes", "Sportbremsen", "Freins sport", "Freni sport", "Športové brzdy", "Frenos sport")
    "tank_std" -> t("Fuel tank 45 L", "Tank 45 L", "Réservoir 45 L", "Serbatoio 45 L", "Nádrž 45 L", "Depósito 45 L")
    "tank_big" -> t("Fuel tank 70 L", "Tank 70 L", "Réservoir 70 L", "Serbatoio 70 L", "Nádrž 70 L", "Depósito 70 L")
    "tank_long" -> t("Long-range tank 110 L", "Langstreckentank 110 L", "Réservoir longue 110 L", "Serbatoio lungo 110 L", "Diaľková nádrž 110 L", "Depósito largo 110 L")
    "alternator_basic" -> t("Alternator", "Lichtmaschine", "Alternateur", "Alternatore", "Alternátor", "Alternador")
    "starter_basic" -> t("Starter", "Anlasser", "Démarreur", "Motorino", "Štartér", "Arranque")
    "suspension_basic" -> t("Suspension (stock)", "Federung (Serie)", "Suspension (origine)", "Sospensioni (serie)", "Pruženie (sériové)", "Suspensión (serie)")
    "suspension_low" -> t("Suspension (lowered)", "Federung (tiefer)", "Suspension (abaissée)", "Sospensioni (ribassate)", "Pruženie (znižené)", "Suspensión (rebajada)")
    "suspension_good" -> t("Sport suspension", "Sportfederung", "Suspension sport", "Sospensioni sport", "Športové pruženie", "Suspensión sport")
    "suspension_lift" -> t("Suspension (lifted)", "Federung (höher)", "Suspension (relevée)", "Sospensioni (rialzate)", "Pruženie (zvýšené)", "Suspensión (elevada)")
    "drive_rwd" -> t("Drivetrain RWD", "Antrieb Heckantrieb", "Transmission propulsion", "Trasmissione posteriore", "Pohon zadný", "Transmisión trasera")
    "drive_fwd" -> t("Drivetrain FWD", "Antrieb Frontantrieb", "Transmission traction", "Trasmissione anteriore", "Pohon predný", "Transmisión delantera")
    "drive_awd" -> t("Drivetrain 4×4", "Antrieb 4×4", "Transmission 4×4", "Trasmissione 4×4", "Pohon 4×4", "Transmisión 4×4")
    "backpack" -> t("Hiking backpack", "Wanderrucksack", "Sac de randonnée", "Zaino da trekking", "Turistický batoh", "Mochila de trekking")
    "boot_crate" -> t("Boot crate", "Kofferraumkiste", "Caisse de coffre", "Cassa bagagliaio", "Debna do kufra", "Caja de maletero")
    "roof_rack" -> t("Roof rack", "Dachträger", "Galerie", "Portapacchi", "Strešný nosič", "Baca")
    "roof_rack_expedition" -> t("Expedition kit", "Expeditions-Kit", "Kit expédition", "Kit spedizione", "Expedičná sada", "Kit de expedición")
    "door_front" -> t("Front door", "Vordertür", "Porte avant", "Porta anteriore", "Predné dvere", "Puerta delantera")
    "door_rear" -> t("Rear door", "Hintertür", "Porte arrière", "Porta posteriore", "Zadné dvere", "Puerta trasera")
    "hood" -> t("Hood", "Motorhaube", "Capot", "Cofano", "Kapota", "Capó")
    "bumper_f" -> t("Front bumper", "Frontstoßstange", "Pare-chocs avant", "Paraurti anteriore", "Predný nárazník", "Parachoques delantero")
    "bumper_r" -> t("Rear bumper", "Heckstoßstange", "Pare-chocs arrière", "Paraurti posteriore", "Zadný nárazník", "Parachoques trasero")
    "seat_front" -> t("Front seat", "Vordersitz", "Siège avant", "Sedile anteriore", "Predné sedadlo", "Asiento delantero")
    "seat_rear" -> t("Rear seat", "Rücksitz", "Siège arrière", "Sedile posteriore", "Zadné sedadlo", "Asiento trasero")
    "trunk_lid" -> t("Boot lid", "Kofferraumdeckel", "Hayon", "Portellone", "Veko kufra", "Portón")
    "headlight" -> t("Headlight", "Scheinwerfer", "Phare", "Faro", "Svetlomet", "Faro")
    "taillight" -> t("Tail light", "Rücklicht", "Feu arrière", "Faro posteriore", "Zadné svetlo", "Piloto")
    "scrap_pile" -> t("Scrap", "Schrott", "Ferraille", "Rottame", "Šrot", "Chatarra")
    else -> null
}

/** Krátky popis dielu do HUD – bez opakovaného mena kategórie. */
fun Copy.itemHudLabel(def: ItemDef): String {
    val full = itemName(def)
    val stripped = full
        .replace(
            Regex(
                "^(Tyres?|Reifen|Pneus?|Gomme?|Pneumatika|Neumáticos?)\\s+",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        .replace(
            Regex(
                "^(Drivetrain|Antrieb|Transmission|Trasmissione|Pohon|Transmisión)\\s+",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        .replace(
            Regex(
                "^(Suspension|Federung|Sospensioni|Pruženie|Suspensión)\\s+",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        .replace(
            Regex("^(Engine|Motor|Moteur|Motore)\\s+", RegexOption.IGNORE_CASE),
            ""
        )
        .trim()
    return stripped.trim('(', ')', ' ').ifBlank { full }
}

fun Copy.itemSlotDetail(def: ItemDef, slot: ComponentSlot): String {
    val noise = (slotName(slot).split(' ', '-', '·') + slot.displayName.split(' ', '-'))
        .map { it.lowercase() }
        .filter { it.isNotBlank() }
        .toSet()
    val kept = itemHudLabel(def)
        .split(' ')
        .filter { it.trim('(', ')', ',', '·').lowercase() !in noise }
    return kept.joinToString(" ").trim('(', ')', ' ', '·')
}

fun Copy.fittedHudLabel(car: sk.kubis.endlessdrive.game.car.Car, slot: ComponentSlot): String =
    car.parts[slot]?.def?.let { itemHudLabel(it) } ?: "—"

fun Copy.fluidName(type: FluidType) = when (type) {
    FluidType.FUEL -> t("Fuel", "Kraftstoff", "Carburant", "Carburante", "Palivo", "Combustible")
    FluidType.OIL -> t("Engine oil", "Motoröl", "Huile moteur", "Olio motore", "Motorový olej", "Aceite de motor")
    FluidType.COOLANT -> t("Coolant", "Kühlmittel", "Liquide de refroidissement", "Refrigerante", "Chladiaca kvapalina", "Refrigerante")
    FluidType.BRAKE_FLUID -> t("Brake fluid", "Bremsflüssigkeit", "Liquide de frein", "Liquido freni", "Brzdová kvapalina", "Líquido de frenos")
}
fun Copy.fuelKindName(kind: FuelKind) = when (kind) {
    FuelKind.PETROL -> t("Petrol", "Benzin", "Essence", "Benzina", "Benzín", "Gasolina")
    FuelKind.DIESEL -> t("Diesel", "Diesel", "Diesel", "Diesel", "Nafta", "Diésel")
}
fun Copy.fluidGradeName(grade: FluidGrade) = when (grade) {
    FluidGrade.PURE -> t("Clean", "Sauber", "Propre", "Pulito", "Čisté", "Limpio")
    FluidGrade.SLIGHTLY_MIXED -> t("Slightly diluted", "Leicht verdünnt", "Légèrement dilué", "Leggermente diluito", "Mierne riedené", "Ligeramente diluido")
    FluidGrade.WATERED -> t("Watered down", "Verwässert", "Mouillé", "Annacquato", "Zriedené", "Aguado")
    FluidGrade.SLUDGE -> t("Mostly water / sludge", "Meist Wasser / Schlamm", "Surtout eau / boue", "Soprattutto acqua / fango", "Väčšinou voda / kal", "Sobre todo agua / lodo")
}
fun Copy.conditionName(c: ComponentCondition) = when (c) {
    ComponentCondition.NEW -> t("New", "Neu", "Neuf", "Nuovo", "Nové", "Nuevo")
    ComponentCondition.USED -> t("Used", "Gebraucht", "Usagé", "Usato", "Použité", "Usado")
    ComponentCondition.DAMAGED -> t("Damaged", "Beschädigt", "Endommagé", "Danneggiato", "Poškodené", "Dañado")
    ComponentCondition.CRITICAL -> t("Critical", "Kritisch", "Critique", "Critico", "Kritické", "Crítico")
}
fun Copy.paintName(paint: VehiclePaint) = when (paint) {
    VehiclePaint.ORANGE -> t("Amber", "Bernstein", "Ambre", "Ambra", "Jantárová", "Ámbar")
    VehiclePaint.RED -> t("Crimson", "Karmesin", "Cramoisi", "Cremisi", "Karmínová", "Carmesí")
    VehiclePaint.BLUE -> t("Midnight blue", "Mitternachtsblau", "Bleu nuit", "Blu notte", "Polnočná modrá", "Azul medianoche")
    VehiclePaint.CYAN -> t("Turquoise", "Türkis", "Turquoise", "Turchese", "Tyrkysová", "Turquesa")
    VehiclePaint.GREEN -> t("Forest green", "Waldgrün", "Vert forêt", "Verde bosco", "Lesná zelená", "Verde bosque")
    VehiclePaint.OLIVE -> t("Olive", "Oliv", "Olive", "Oliva", "Olivová", "Oliva")
    VehiclePaint.CREAM -> t("Ivory", "Elfenbein", "Ivoire", "Avorio", "Slonovinová", "Marfil")
    VehiclePaint.SILVER -> t("Silver", "Silber", "Argent", "Argento", "Strieborná", "Plata")
    VehiclePaint.CHARCOAL -> t("Charcoal", "Anthrazit", "Charbon", "Antracite", "Antracitová", "Carbón")
    VehiclePaint.VIOLET -> t("Violet", "Violett", "Violet", "Viola", "Fialová", "Violeta")
}
fun Copy.buildingName(type: BuildingType) = when (type) {
    BuildingType.HOUSE -> t("House", "Haus", "Maison", "Casa", "Dom", "Casa")
    BuildingType.GARAGE -> t("Garage", "Garage", "Garage", "Garage", "Garáž", "Garaje")
    BuildingType.GAS_STATION -> t("Fuel station", "Tankstelle", "Station-service", "Stazione di servizio", "Čerpacia stanica", "Gasolinera")
    BuildingType.AUTO_SHOP -> t("Workshop", "Werkstatt", "Atelier", "Officina", "Dielňa", "Taller")
    BuildingType.WRECK -> t("Abandoned car", "Verlassenes Auto", "Voiture abandonnée", "Auto abbandonata", "Opuštěné auto", "Coche abandonado")
    BuildingType.PAINT_SHOP -> t("Paint shop", "Lackiererei", "Atelier peinture", "Carrozzeria", "Lakovačka", "Taller de pintura")
}
fun Copy.surfaceChip(surface: RoadSurface): String? = when (surface) {
    RoadSurface.ASPHALT -> null
    RoadSurface.ICE -> t("ICE", "EIS", "GLACE", "GHIACCIO", "ĽAD", "HIELO")
    RoadSurface.SLUSH -> t("SLUSH", "MATSCH", "NEIGE FONDUE", "NEVE FRADICIA", "KAŠOVITÝ SNEH", "NIEVE FUNDIDA")
    RoadSurface.GRAVEL -> t("GRAVEL", "SCHOTTER", "GRAVIER", "GHIAIA", "ŠTRK", "GRAVA")
    RoadSurface.SAND -> t("SAND", "SAND", "SABLE", "SABBIA", "PIESOK", "ARENA")
    RoadSurface.MUD -> t("MUD", "SCHLAMM", "BOUE", "FANGO", "BLATO", "BARRO")
    RoadSurface.WATER -> t("WATER", "WASSER", "EAU", "ACQUA", "VODA", "AGUA")
}
fun Copy.driveLayoutName(layout: DriveLayout) = when (layout) {
    DriveLayout.RWD -> t("RWD", "Heckantrieb", "Propulsion", "Posteriore", "Zadný pohon", "Trasera")
    DriveLayout.FWD -> t("FWD", "Frontantrieb", "Traction", "Anteriore", "Predný pohon", "Delantera")
    DriveLayout.AWD -> t("4×4", "4×4", "4×4", "4×4", "4×4", "4×4")
}
fun Copy.biomeName(biome: BiomeType) = when (biome) {
    BiomeType.RURAL -> t("Countryside", "Land", "Campagne", "Campagna", "Vidiek", "Campo")
    BiomeType.INDUSTRIAL -> t("Industry", "Industrie", "Industrie", "Industria", "Priemysel", "Industria")
    BiomeType.WASTELAND -> t("Wasteland", "Ödland", "Terres désolées", "Terra desolata", "Pustatina", "Páramo")
    BiomeType.DESERT -> t("Desert", "Wüste", "Désert", "Deserto", "Púšť", "Desierto")
    BiomeType.DESERT_DUSK -> t("Mesa", "Mesa", "Mesa", "Mesa", "Mesa", "Mesa")
    BiomeType.FOREST -> t("Dead forest", "Toter Wald", "Forêt morte", "Foresta morta", "Mŕtvy les", "Bosque muerto")
    BiomeType.FOREST_ALIVE -> t("Living forest", "Lebendiger Wald", "Forêt vivante", "Foresta viva", "Živý les", "Bosque vivo")
    BiomeType.SANDSTORM -> t("Sandstorm", "Sandsturm", "Tempête de sable", "Tempesta di sabbia", "Piesočná búrka", "Tormenta de arena")
    BiomeType.DUST_STORM -> t("Dust storm", "Staubsturm", "Tempête de poussière", "Tempesta di polvere", "Prachová búrka", "Tormenta de polvo")
    BiomeType.ALPINE -> t("Snowy mountains", "Schneebedeckte Berge", "Montagnes enneigées", "Montagne innevate", "Zasnežené hory", "Montañas nevadas")
}
fun Copy.branchLabel(style: BranchStyle) = biomeName(style.biome)
fun Copy.endReasonMessage(reason: EndReason) = when (reason) {
    EndReason.OUT_OF_FUEL -> t("Ran out of fuel", "Kraftstoff alle", "Plus de carburant", "Carburante finito", "Došlo palivo", "Sin combustible")
    EndReason.ENGINE_DESTROYED -> t("Engine destroyed", "Motor zerstört", "Moteur détruit", "Motore distrutto", "Motor zničený", "Motor destruido")
    EndReason.OVERHEAT -> t("Engine overheated", "Motor überhitzt", "Moteur en surchauffe", "Motore surriscaldato", "Motor prehriaty", "Motor sobrecalentado")
    EndReason.BATTERY_DEAD -> t("The battery is dead", "Die Batterie ist tot", "La batterie est morte", "La batteria è morta", "Batéria je mŕtva", "La batería está muerta")
    EndReason.SHREDDED_TIRE -> t("Tyre shredded to the rim", "Reifen bis auf die Felge zerfetzt", "Pneu déchiré jusqu'à la jante", "Gomma a brandelli sul cerchio", "Pneumatika roztrhnutá na ráfik", "Neumático destrozado hasta la llanta")
    EndReason.STUCK_TIRE -> t("Stuck with no traction", "Fest ohne Traktion", "Bloqué sans adhérence", "Bloccato senza aderenza", "Zaseknutý bez priľnavosti", "Atascado sin tracción")
    EndReason.ARRIVED -> t("Reached the safe haven", "Sicherer Hafen erreicht", "Refuge atteint", "Rifugio raggiunto", "Dorazil si do bezpečného prístavu", "Llegaste al refugio")
    EndReason.MANUAL -> t("Run over", "Fahrt beendet", "Course terminée", "Corsa finita", "Jazda skončená", "Carrera terminada")
}
fun Copy.rescueAd(offer: RescueOffer) = when (offer) {
    RescueOffer.FUEL -> t("FUEL +12 L · WATCH AD", "KRAFTSTOFF +12 L · WERBUNG", "CARBURANT +12 L · PUB", "CARBURANTE +12 L · ADS", "PALIVO +12 L · REKLAMA", "COMBUSTIBLE +12 L · ANUNCIO")
    RescueOffer.BATTERY -> t("BATTERY CHARGE +50 % · WATCH AD", "BATTERIE +50 % · WERBUNG", "BATTERIE +50 % · PUB", "BATTERIA +50 % · ADS", "BATÉRIA +50 % · REKLAMA", "BATERÍA +50 % · ANUNCIO")
    RescueOffer.BATTERY_FIT -> t("BATTERY +50 % · WATCH AD", "BATTERIE +50 % · WERBUNG", "BATTERIE +50 % · PUB", "BATTERIA +50 % · ADS", "BATÉRIA +50 % · REKLAMA", "BATERÍA +50 % · ANUNCIO")
    RescueOffer.RADIATOR -> t("RADIATOR + COOLANT · WATCH AD", "KÜHLER + KÜHLMITTEL · WERBUNG", "RADIATEUR + LIQUIDE · PUB", "RADIATORE + REFRIGERANTE · ADS", "CHLADIČ + CHLADIACA · REKLAMA", "RADIADOR + REFRIGERANTE · ANUNCIO")
    RescueOffer.COOLANT -> t("COOLANT +3 L · WATCH AD", "KÜHLMITTEL +3 L · WERBUNG", "LIQUIDE +3 L · PUB", "REFRIGERANTE +3 L · ADS", "CHLADIACA +3 L · REKLAMA", "REFRIGERANTE +3 L · ANUNCIO")
    RescueOffer.OIL -> t("OIL +2 L · WATCH AD", "ÖL +2 L · WERBUNG", "HUILE +2 L · PUB", "OLIO +2 L · ADS", "OLEJ +2 L · REKLAMA", "ACEITE +2 L · ANUNCIO")
    RescueOffer.ENGINE -> t("ENGINE +50 % · WATCH AD", "MOTOR +50 % · WERBUNG", "MOTEUR +50 % · PUB", "MOTORE +50 % · ADS", "MOTOR +50 % · REKLAMA", "MOTOR +50 % · ANUNCIO")
    RescueOffer.TOW_SHOP -> t("TOW TO SHOP · WATCH AD", "ABSCHLEPPEN · WERBUNG", "REMORQUAGE · PUB", "CARRO ATTREZZI · ADS", "ODTAH DO DIELNE · REKLAMA", "REMOLQUE AL TALLER · ANUNCIO")
    RescueOffer.ROADSIDE_SHOP -> t("ROADSIDE GARAGE · WATCH AD", "STRASSENGARAGE · WERBUNG", "GARAGE ROUTIER · PUB", "OFFICINA STRADALE · ADS", "CESTNÁ GARÁŽ · REKLAMA", "TALLER DE CARRETERA · ANUNCIO")
}
fun Copy.prepHint(step: PrepStep) = when (step) {
    PrepStep.CHECK_ENGINE -> t("Check the engine", "Prüfe den Motor", "Vérifie le moteur", "Controlla il motore", "Skontroluj motor", "Revisa el motor")
    PrepStep.REFILL_OIL -> t("Top up the engine oil", "Fülle Motoröl nach", "Complète l'huile moteur", "Rabbocca l'olio", "Doplň motorový olej", "Rellena el aceite")
    PrepStep.REFILL_COOLANT -> t("Top up the coolant", "Fülle Kühlmittel nach", "Complète le liquide", "Rabbocca il refrigerante", "Doplň chladiacu kvapalinu", "Rellena el refrigerante")
    PrepStep.CHECK_FUEL -> t("Check the fuel", "Prüfe den Kraftstoff", "Vérifie le carburant", "Controlla il carburante", "Skontroluj palivo", "Revisa el combustible")
    PrepStep.START_ENGINE -> t("Start the engine", "Starte den Motor", "Démarre le moteur", "Avvia il motore", "Naštartuj motor", "Arranca el motor")
    PrepStep.DONE -> t("Ready to drive", "Bereit zur Fahrt", "Prêt à rouler", "Pronto a partire", "Pripravený na jazdu", "Listo para conducir")
}
fun Copy.wearWarning(cause: WearCause) = when (cause) {
    WearCause.FROZEN_COOLANT -> t("Water in the cooling system is freezing — the engine is cracking", "Wasser im Kühlsystem gefriert — der Motor reißt", "L'eau du refroidissement gèle — le moteur se fend", "L'acqua nel raffreddamento gela — il motore si spacca", "Voda v chladení mrzne — motor praská", "El agua del refrigerante se hiela: el motor se agrieta")
    WearCause.LOW_OIL -> t("Oil is low — the engine is seizing!", "Öl ist niedrig — der Motor frisst sich fest!", "L'huile est basse — le moteur grippe !", "L'olio è basso — il motore si grippa!", "Oleja je málo — motor sa zadiera!", "El aceite está bajo: ¡el motor se agarrota!")
    WearCause.DIRTY_OIL -> t("Dirty oil is grinding the engine", "Schmutziges Öl schleift den Motor", "L'huile sale use le moteur", "L'olio sporco consuma il motore", "Špinavý olej brúsi motor", "El aceite sucio desgasta el motor")
    WearCause.DIRTY_FUEL -> t("Watered fuel is killing the engine", "Verwässerter Kraftstoff tötet den Motor", "Le carburant mouillé tue le moteur", "Il carburante annacquato uccide il motore", "Zriedené palivo ničí motor", "El combustible aguado mata el motor")
    WearCause.WRONG_FUEL -> t("Wrong fuel is damaging the engine — drain the tank", "Falscher Kraftstoff schadet dem Motor — Tank leeren", "Mauvais carburant — vide le réservoir", "Carburante sbagliato — svuota il serbatoio", "Zlé palivo kazí motor — vypusť nádrž", "Combustible incorrecto: vacía el depósito")
    WearCause.NO_RADIATOR -> t("No radiator — coolant alone can't cool the engine", "Kein Kühler — Kühlmittel allein kühlt nicht", "Pas de radiateur — le liquide ne suffit pas", "Niente radiatore — il refrigerante non basta", "Bez chladiča — kvapalina sama motor neochladí", "Sin radiador: el refrigerante no basta")
    WearCause.BAD_RADIATOR -> t("Radiator is failing — engine is overheating", "Kühler fällt aus — Motor überhitzt", "Le radiateur lâche — le moteur surchauffe", "Il radiatore sta cedendo — il motore surriscalda", "Chladič zlyháva — motor sa prehrieva", "El radiador falla: el motor se calienta")
    WearCause.LOW_COOLANT -> t("Coolant is low — engine is overheating", "Kühlmittel niedrig — Motor überhitzt", "Liquide bas — le moteur surchauffe", "Refrigerante basso — il motore surriscalda", "Málo chladiacej — motor sa prehrieva", "Poco refrigerante: el motor se calienta")
    WearCause.OVERHEAT -> t("Overheating is destroying the engine", "Überhitzung zerstört den Motor", "La surchauffe détruit le moteur", "Il surriscaldamento distrugge il motore", "Prehriatie ničí motor", "El sobrecalentamiento destruye el motor")
}
fun Copy.wearFatal(cause: WearCause) = when (cause) {
    WearCause.FROZEN_COOLANT -> t("cracked by frozen water in the cooling system", "gerissen durch gefrorenes Wasser im Kühlsystem", "fendu par l'eau gelée du refroidissement", "spaccato dall'acqua gelata", "praskol od zamrznutej vody v chladení", "agrietado por agua helada en el refrigerante")
    WearCause.LOW_OIL -> t("seized without oil", "festgefressen ohne Öl", "grippé sans huile", "grippato senza olio", "zadrel sa bez oleja", "agarrotado sin aceite")
    WearCause.DIRTY_OIL -> t("worn out by dirty oil", "verschlissen durch schmutziges Öl", "usé par l'huile sale", "consumato dall'olio sporco", "opotrebovaný špinavým olejom", "desgastado por aceite sucio")
    WearCause.DIRTY_FUEL -> t("ruined by watered fuel", "ruiniert durch verwässerten Kraftstoff", "ruiné par le carburant mouillé", "rovinato dal carburante annacquato", "zničený zriedeným palivom", "arruinado por combustible aguado")
    WearCause.WRONG_FUEL -> t("destroyed by the wrong fuel", "zerstört durch falschen Kraftstoff", "détruit par le mauvais carburant", "distrutto dal carburante sbagliato", "zničený zlým palivom", "destruido por el combustible incorrecto")
    WearCause.NO_RADIATOR -> t("cooked without a radiator", "gekocht ohne Kühler", "cuit sans radiateur", "cotto senza radiatore", "uvarený bez chladiča", "cocido sin radiador")
    WearCause.BAD_RADIATOR -> t("cooked by a failing radiator", "gekocht durch einen defekten Kühler", "cuit par un radiateur défaillant", "cotto da un radiatore in avaria", "uvarený zlyhávajúcim chladičom", "cocido por un radiador fallido")
    WearCause.LOW_COOLANT -> t("cooked with no coolant", "gekocht ohne Kühlmittel", "cuit sans liquide", "cotto senza refrigerante", "uvarený bez chladiacej", "cocido sin refrigerante")
    WearCause.OVERHEAT -> t("burnt out by overheating", "durchgebrannt durch Überhitzung", "brûlé par la surchauffe", "bruciato dal surriscaldamento", "spálený prehriatím", "quemado por sobrecalentamiento")
}
fun Copy.roadEventMessage(e: RoadEvent) = when (e) {
    RoadEvent.FLAT_TYRE -> t("Blowout — the tyre is shredded.", "Platzer — der Reifen ist zerfetzt.", "Éclatement — le pneu est déchiré.", "Scoppio — la gomma è a brandelli.", "Výbuch — pneumatika je roztrhnutá.", "Reventón: el neumático está destrozado.")
    RoadEvent.ROCK_STRIKE -> t("A rock hit the radiator.", "Ein Stein traf den Kühler.", "Une pierre a frappé le radiateur.", "Una pietra ha colpito il radiatore.", "Kameň trafil chladič.", "Una piedra golpeó el radiador.")
    RoadEvent.FUEL_LEAK -> t("Fuel leak — tank is dropping.", "Kraftstoffleck — Tank fällt.", "Fuite de carburant — le réservoir baisse.", "Perdita carburante — il serbatoio scende.", "Únik paliva — nádrž klesá.", "Fuga de combustible: el depósito baja.")
    RoadEvent.COOLANT_LEAK -> t("Coolant leak — level is dropping.", "Kühlmittelleck — Stand fällt.", "Fuite de liquide — le niveau baisse.", "Perdita refrigerante — il livello scende.", "Únik chladiacej — hladina klesá.", "Fuga de refrigerante: el nivel baja.")
    RoadEvent.MISFIRE -> t("Engine misfire — less power.", "Zündaussetzer — weniger Leistung.", "Ratés moteur — moins de puissance.", "Mancata accensione — meno potenza.", "Vynechávanie motora — menej výkonu.", "Fallos de encendido: menos potencia.")
    RoadEvent.BELT_SNAPPED -> t("Alternator belt snapped — battery not charging.", "Lichtmaschinenriemen gerissen — Batterie lädt nicht.", "Courroie d'alternateur cassée — batterie ne charge pas.", "Cinghia alternatore rotta — batteria non carica.", "Remeň alternátora praskol — batéria sa nenabíja.", "Correa del alternador rota: la batería no carga.")
    RoadEvent.OIL_SPLASH -> t("Dirty puddle — oil purity dropped.", "Schmutzige Pfütze — Ölreinheit gesunken.", "Flaque sale — pureté d'huile en baisse.", "Pozzanghera sporca — olio meno puro.", "Špinavá kaluž — čistota oleja klesla.", "Charco sucio: bajó la pureza del aceite.")
    RoadEvent.RAIN -> t("Rain — road is slippery.", "Regen — Straße ist glatt.", "Pluie — route glissante.", "Pioggia — strada scivolosa.", "Dážď — cesta je šmykľavá.", "Lluvia: carretera resbaladiza.")
    RoadEvent.FOG -> t("Fog — poor visibility.", "Nebel — schlechte Sicht.", "Brouillard — mauvaise visibilité.", "Nebbia — scarsa visibilità.", "Hmla — zlá viditeľnosť.", "Niebla: mala visibilidad.")
    RoadEvent.STORM -> t("Thunderstorm — heavy rain and wind.", "Gewitter — starker Regen und Wind.", "Orage — pluie forte et vent.", "Temporale — pioggia forte e vento.", "Búrka — silný dážď a vietor.", "Tormenta: lluvia fuerte y viento.")
    RoadEvent.DEBRIS -> t("Debris on the road — tyre risk.", "Trümmer auf der Straße — Reifenrisiko.", "Débris sur la route — risque pneus.", "Detriti in strada — rischio gomme.", "Suť na ceste — riziko pre gumy.", "Escombros en la carretera: riesgo de neumáticos.")
    RoadEvent.MUD -> t("Mud — wheels digging in.", "Schlamm — Räder graben sich ein.", "Boue — les roues s'enfoncent.", "Fango — le ruote affondano.", "Blato — kolesá sa zarezávajú.", "Barro: las ruedas se hunden.")
    RoadEvent.HEADWIND -> t("Headwind — slower and using more fuel.", "Gegenwind — langsamer, mehr Verbrauch.", "Vent de face — plus lent, plus de conso.", "Vento contrario — più lento, più consumo.", "Protivietor — pomalšie a viac paliva.", "Viento de cara: más lento y más consumo.")
    RoadEvent.TAILWIND -> t("Tailwind — faster and using less fuel.", "Rückenwind — schneller, weniger Verbrauch.", "Vent arrière — plus vite, moins de conso.", "Vento a favore — più veloce, meno consumo.", "Zadný vietor — rýchlejšie a menej paliva.", "Viento de cola: más rápido y menos consumo.")
    RoadEvent.CLEAR_ROAD -> t("Smooth road — using less fuel.", "Glatte Straße — weniger Verbrauch.", "Route lisse — moins de conso.", "Strada liscia — meno consumo.", "Hladká cesta — menej paliva.", "Carretera lisa: menos consumo.")
    RoadEvent.ROADSIDE_STASH -> t("Supplies ahead — stop to pick them up.", "Vorräte voraus — anhalten und mitnehmen.", "Provisions plus loin — arrête-toi.", "Scorte più avanti — fermati a prenderle.", "Zásoby vpredu — zastav a zober ich.", "Provisiones adelante: para a recogerlas.")
    RoadEvent.ABANDONED_WRECK -> t("Wreck ahead — scrap and parts.", "Wrack voraus — Schrott und Teile.", "Épave plus loin — ferraille et pièces.", "Relitto più avanti — rottame e pezzi.", "Vrak vpredu — šrot a diely.", "Pecio adelante: chatarra y piezas.")
}
fun Copy.roadEventChip(e: RoadEvent): String? = when (e.chip) {
    "FUEL LEAK" -> t("FUEL LEAK", "KRAFTSTOFFLECK", "FUITE CARB.", "PERDITA CARB.", "ÚNIK PALIVA", "FUGA COMB.")
    "COOLANT LEAK" -> t("COOLANT LEAK", "KÜHLMITTELLECK", "FUITE LIQUIDE", "PERDITA REFR.", "ÚNIK CHLADIACEJ", "FUGA REFR.")
    "MISFIRE" -> t("MISFIRE", "AUSSETZER", "RATÉS", "MANCATA ACC.", "VYNECHÁVANIE", "FALLOS")
    "NO CHARGE" -> t("NO CHARGE", "KEINE LADUNG", "PAS DE CHARGE", "NO CARICA", "NENABÍJA", "SIN CARGA")
    "SLIPPERY" -> t("SLIPPERY", "GLATT", "GLISSANT", "SCIVOLOSO", "ŠMYKĽAVÉ", "RESBALADIZO")
    "FOG" -> t("FOG", "NEBEL", "BROUILLARD", "NEBBIA", "HMLA", "NIEBLA")
    "STORM" -> t("STORM", "GEWITTER", "ORAGE", "TEMPORALE", "BÚRKA", "TORMENTA")
    "DEBRIS" -> t("DEBRIS", "TRÜMMER", "DÉBRIS", "DETRITI", "SUŤ", "ESCOMBROS")
    "MUD" -> t("MUD", "SCHLAMM", "BOUE", "FANGO", "BLATO", "BARRO")
    "HEADWIND" -> t("HEADWIND", "GEGENWIND", "VENT FACE", "VENTO CONTRARIO", "PROTIVIETOR", "VIENTO DE CARA")
    "TAILWIND" -> t("TAILWIND", "RÜCKENWIND", "VENT ARRIÈRE", "VENTO FAVORE", "ZADNÝ VIETOR", "VIENTO DE COLA")
    "SMOOTH" -> t("SMOOTH", "GLATT", "LISSE", "LISCIA", "HLADKÁ", "LISA")
    else -> e.chip
}

fun Copy.missingParts(names: String) = t("Missing $names", "Fehlt: $names", "Manque : $names", "Manca: $names", "Chýba: $names", "Falta: $names")
fun Copy.msgRoofLights(on: Boolean) = if (on) t("Roof lights on", "Dachlichter an", "Feux de toit allumés", "Luci tetto accese", "Strešné svetlá zapnuté", "Luces de techo encendidas") else t("Roof lights off", "Dachlichter aus", "Feux de toit éteints", "Luci tetto spente", "Strešné svetlá vypnuté", "Luces de techo apagadas")
val Copy.searchTheGarage: String get() = t(
    "Search the garage for missing parts, fuel, oil and coolant. Then start the engine.",
    "Durchsuche die Garage nach fehlenden Teilen, Kraftstoff, Öl und Kühlmittel. Dann starte den Motor.",
    "Fouille le garage pour pièces, carburant, huile et liquide. Puis démarre le moteur.",
    "Cerca in garage pezzi, carburante, olio e refrigerante. Poi avvia il motore.",
    "Prehľadaj garáž kvôli chýbajúcim dielom, palivu, oleju a chladiacej. Potom naštartuj.",
    "Registra el garaje por piezas, combustible, aceite y refrigerante. Luego arranca."
)
val Copy.gettingDark: String get() = t("Getting dark — switch the headlights on", "Es wird dunkel — Scheinwerfer an", "Ça sombre — allume les phares", "Si fa buio — accendi i fari", "Stmieva sa — zapni svetlá", "Oscurece: enciende los faros")
val Copy.noAlternator: String get() = t("No alternator fitted — battery won't recharge", "Keine Lichtmaschine — Batterie lädt nicht", "Pas d'alternateur — la batterie ne recharge pas", "Niente alternatore — la batteria non ricarica", "Bez alternátora — batéria sa nenabije", "Sin alternador: la batería no recarga")
val Copy.noHeadlightFitted: String get() = t("No headlight fitted — find one first", "Kein Scheinwerfer — finde zuerst einen", "Pas de phare — trouves-en un d'abord", "Niente faro — trovalo prima", "Bez svetlometu — najprv ho nájdi", "Sin faro: encuentra uno primero")
val Copy.headlightsOff: String get() = t("Headlights off", "Scheinwerfer aus", "Phares éteints", "Fari spenti", "Svetlá vypnuté", "Faros apagados")
val Copy.highBeamsOn: String get() = t("High beams on", "Fernlicht an", "Pleins phares allumés", "Abbaglianti accesi", "Diaľkové zapnuté", "Largas encendidas")
val Copy.lowBeamsOn: String get() = t("Low beams on", "Abblendlicht an", "Codes allumés", "Anabbaglianti accesi", "Stretávacie zapnuté", "Cruce encendido")
fun Copy.beamsNoAlt(high: Boolean) = t(
    "${if (high) "High beams" else "Low beams"} — no alternator, battery will drain",
    "${if (high) "Fernlicht" else "Abblendlicht"} — keine Lichtmaschine, Batterie entlädt sich",
    "${if (high) "Pleins phares" else "Codes"} — pas d'alternateur, la batterie se vide",
    "${if (high) "Abbaglianti" else "Anabbaglianti"} — niente alternatore, la batteria si scarica",
    "${if (high) "Diaľkové" else "Stretávacie"} — bez alternátora, batéria sa vybíja",
    "${if (high) "Largas" else "Cruce"} — sin alternador, la batería se agota"
)
fun Copy.beamsWeakBattery(high: Boolean) = t(
    "${if (high) "High beams" else "Low beams"} — careful, the battery is weak",
    "${if (high) "Fernlicht" else "Abblendlicht"} — vorsicht, die Batterie ist schwach",
    "${if (high) "Pleins phares" else "Codes"} — attention, batterie faible",
    "${if (high) "Abbaglianti" else "Anabbaglianti"} — attenzione, batteria debole",
    "${if (high) "Diaľkové" else "Stretávacie"} — opatrne, batéria je slabá",
    "${if (high) "Largas" else "Cruce"} — cuidado, la batería está débil"
)
fun Copy.fluidsDirty(grade: String) = t(
    "Fluids are $grade — find cleaner fuel, oil or coolant.",
    "Flüssigkeiten sind $grade — finde saubereren Kraftstoff, Öl oder Kühlmittel.",
    "Les fluides sont $grade — trouve du carburant, de l'huile ou du liquide plus propre.",
    "I liquidi sono $grade — trova carburante, olio o refrigerante più puliti.",
    "Kvapaliny sú $grade — nájdi čistejšie palivo, olej alebo chladiacu.",
    "Los líquidos están $grade: busca combustible, aceite o refrigerante más limpios."
)
val Copy.engineRunningPressDrive: String get() = t("Engine running. Press DRIVE.", "Motor läuft. Drücke FAHREN.", "Moteur allumé. Appuie sur ROULER.", "Motore acceso. Premi GUIDA.", "Motor beží. Stlač JAZDA.", "Motor en marcha. Pulsa CONDUCIR.")
val Copy.safeHavenReachedMsg: String get() = t(
    "Safe haven reached — finish the expedition or continue into Endless mode",
    "Sicherer Hafen erreicht — Expedition beenden oder im Endlosmodus weiterfahren",
    "Refuge atteint — termine l'expédition ou continue en Infini",
    "Rifugio raggiunto — concludi la spedizione o continua in Infinito",
    "Bezpečný prístav — ukonči expedíciu alebo pokračuj v Nekonečne",
    "Refugio alcanzado: termina la expedición o sigue en Infinito"
)
val Copy.safeHavenOffline: String get() = t(
    "The safe haven relay is still offline — restore it before finishing",
    "Das Relais des sicheren Hafens ist noch offline — stelle es vor dem Ende wieder her",
    "Le relais du refuge est encore hors ligne — restaure-le avant de finir",
    "Il relè del rifugio è ancora offline — ripristinalo prima di finire",
    "Relé bezpečného prístavu je ešte offline — obnov ho pred koncom",
    "El relé del refugio sigue fuera de línea: restáuralo antes de terminar"
)
val Copy.relayOnlineFuel: String get() = t("RELAY ONLINE — fuel and parts", "RELAIS ONLINE — Kraftstoff und Teile", "RELAIS EN LIGNE — carburant et pièces", "RELÈ ONLINE — carburante e pezzi", "RELÉ ONLINE — palivo a diely", "RELÉ EN LÍNEA — combustible y piezas")
val Copy.relayStationHint: String get() = t("RELAY STATION — fuel, parts and signal", "RELAISSTATION — Kraftstoff, Teile und Signal", "STATION RELAIS — carburant, pièces et signal", "STAZIONE RELÈ — carburante, pezzi e segnale", "STANICA RELÉ — palivo, diely a signál", "ESTACIÓN DE RELÉ — combustible, piezas y señal")
fun Copy.stopAndSearch(name: String) = t("$name — stop and search it", "$name — anhalten und durchsuchen", "$name — arrête-toi et fouille", "$name — fermati e cerca", "$name — zastav a prehľadaj", "$name — para y regístralo")
fun Copy.engineAboutToDie(pct: Int, warning: String) = t("ENGINE ABOUT TO DIE: $pct % — $warning", "MOTOR STIRBT: $pct % — $warning", "MOTEUR PRESQUE MORT : $pct % — $warning", "MOTORE QUASI MORTO: $pct % — $warning", "MOTOR SKORO ZOMRIE: $pct % — $warning", "MOTOR A PUNTO DE MORIR: $pct % — $warning")
fun Copy.engineBadlyWorn(pct: Int) = t("Engine is badly worn ($pct %)", "Motor ist stark verschlissen ($pct %)", "Le moteur est très usé ($pct %)", "Il motore è molto consumato ($pct %)", "Motor je silno opotrebovaný ($pct %)", "El motor está muy desgastado ($pct %)")
fun Copy.engineWearing(pct: Int) = t("Engine is wearing down ($pct %)", "Motor verschleißt ($pct %)", "Le moteur s'use ($pct %)", "Il motore si sta consumando ($pct %)", "Motor sa opotrebúva ($pct %)", "El motor se está desgastando ($pct %)")
val Copy.fixIt: String get() = t("fix it", "repariere es", "répare-le", "sistemalo", "oprav to", "arréglalo")
val Copy.engineIsOff: String get() = t("Engine is off — press START", "Motor ist aus — drücke START", "Moteur éteint — appuie sur DÉMARRER", "Motore spento — premi AVVIA", "Motor je vypnutý — stlač ŠTART", "Motor apagado: pulsa ARRANCAR")
val Copy.outOfFuel: String get() = t("Out of fuel", "Kein Kraftstoff", "Plus de carburant", "Senza carburante", "Bez paliva", "Sin combustible")
val Copy.releaseBrake: String get() = t("Release the brake", "Bremse lösen", "Relâche le frein", "Lascia il freno", "Pusti brzdu", "Suelta el freno")
val Copy.noTractionAir: String get() = t("No traction in the air", "Keine Traktion in der Luft", "Pas d'adhérence en l'air", "Niente aderenza in aria", "Vo vzduchu nie je priľnavosť", "Sin tracción en el aire")
val Copy.onTheRimAssist: String get() = t("On the rim — stop for ROAD ASSIST", "Auf der Felge — anhalten für STRASSENHILFE", "Sur la jante — arrête-toi pour ASSISTANCE", "Sul cerchio — fermati per ASSISTENZA", "Na ráfiku — zastav pre ASISTENCIU", "En llanta: para por ASISTENCIA")
val Copy.climbTooSteep: String get() = t("Climb too steep for these tyres — need grip or a run-up", "Steigung zu steil für diese Reifen — mehr Grip oder Anlauf", "Montée trop raide pour ces pneus — plus d'adhérence ou d'élan", "Salita troppo ripida per queste gomme — serve grip o slancio", "Stúpanie je pre tieto gumy príkré — treba grip alebo rozbeh", "Cuesta demasiado para estos neumáticos: más agarre o impulso")
val Copy.tyresSpinning: String get() = t("Tyres spinning — ease off or find better rubber", "Reifen drehen durch — Gas weg oder bessere Reifen", "Pneus qui patinent — relâche ou trouve de meilleures gommes", "Gomme che slittano — togli gas o trova gomme migliori", "Gumy sa točia — uber alebo nájdi lepšie", "Neumáticos patinando: afloja o busca mejor goma")
val Copy.noTractionAssist: String get() = t("No traction — stop for ROAD ASSIST", "Keine Traktion — anhalten für STRASSENHILFE", "Pas d'adhérence — arrête-toi pour ASSISTANCE", "Niente aderenza — fermati per ASSISTENZA", "Bez priľnavosti — zastav pre ASISTENCIU", "Sin tracción: para por ASISTENCIA")
fun Copy.engineTooWeak(hp: Int) = t("Engine too weak ($hp hp)", "Motor zu schwach ($hp PS)", "Moteur trop faible ($hp ch)", "Motore troppo debole ($hp cv)", "Motor je slabý ($hp k)", "Motor demasiado débil ($hp cv)")
val Copy.carWontMove: String get() = t("Car won't move", "Auto bewegt sich nicht", "La voiture ne bouge pas", "L'auto non si muove", "Auto sa nepohne", "El coche no se mueve")
val Copy.noFuelInTank: String get() = t("No fuel in the tank", "Kein Kraftstoff im Tank", "Pas de carburant dans le réservoir", "Niente carburante nel serbatoio", "V nádrži nie je palivo", "No hay combustible en el depósito")
val Copy.noOilInEngine: String get() = t("No oil in the engine", "Kein Öl im Motor", "Pas d'huile dans le moteur", "Niente olio nel motore", "V motore nie je olej", "No hay aceite en el motor")
val Copy.radiatorEmpty: String get() = t("Radiator is empty — pour coolant", "Kühler ist leer — Kühlmittel einfüllen", "Radiateur vide — verse du liquide", "Radiatore vuoto — versa refrigerante", "Chladič je prázdny — naliať chladiacu", "El radiador está vacío: echa refrigerante")
fun Copy.wrongFuelDrain(kind: String) = t(
    "Wrong fuel — drain the tank and fill with $kind",
    "Falscher Kraftstoff — Tank leeren und mit $kind füllen",
    "Mauvais carburant — vide et remplis avec $kind",
    "Carburante sbagliato — svuota e riempi con $kind",
    "Zlé palivo — vypusť nádrž a daj $kind",
    "Combustible incorrecto: vacía y llena con $kind"
)
val Copy.batteryIsFlat: String get() = t("Battery is flat", "Batterie ist leer", "Batterie à plat", "Batteria scarica", "Batéria je vybitá", "La batería está agotada")
val Copy.engineBeyondSaving: String get() = t("Engine is beyond saving", "Motor ist nicht mehr zu retten", "Le moteur est irrécupérable", "Il motore è irrecuperabile", "Motor sa nedá zachrániť", "El motor no tiene arreglo")
val Copy.slowDownFirst: String get() = t("Slow down first", "Zuerst abbremsen", "Ralentis d'abord", "Rallenta prima", "Najprv spomaľ", "Frena primero")
val Copy.stopped: String get() = t("Stopped", "Angehalten", "Arrêté", "Fermo", "Zastavené", "Detenido")
val Copy.driving: String get() = t("Driving", "Fahren", "En route", "In marcia", "Jazda", "Conduciendo")
val Copy.stopAtBuilding: String get() = t("Stop at the building first", "Zuerst am Gebäude halten", "Arrête-toi d'abord au bâtiment", "Fermati prima all'edificio", "Najprv zastav pri budove", "Para primero en el edificio")
val Copy.noBuildingNearby: String get() = t("No building nearby", "Kein Gebäude in der Nähe", "Pas de bâtiment à proximité", "Nessun edificio vicino", "Žiadna budova nablízku", "No hay ningún edificio cerca")
fun Copy.insideBuilding(name: String) = t("Inside: $name", "Drinnen: $name", "Dedans : $name", "Dentro: $name", "Vnútri: $name", "Dentro: $name")
fun Copy.searchingBuilding(name: String) = t("Searching: $name", "Durchsuche: $name", "Fouille : $name", "Cerco: $name", "Prehľadávam: $name", "Registrando: $name")
val Copy.enterRelayFirst: String get() = t("Enter a relay station first", "Zuerst eine Relaisstation betreten", "Entre d'abord dans une station relais", "Entra prima in una stazione relè", "Najprv vojdite do stanice relé", "Entra primero en una estación de relé")
val Copy.networkFullyRestored: String get() = t("The radio network is fully restored", "Das Funknetz ist vollständig wiederhergestellt", "Le réseau radio est entièrement restauré", "La rete radio è completamente ripristinata", "Rádiová sieť je úplne obnovená", "La red de radio está completamente restaurada")
val Copy.relayAlreadyOnline: String get() = t("This relay is already online", "Dieses Relais ist schon online", "Ce relais est déjà en ligne", "Questo relè è già online", "Toto relé je už online", "Este relé ya está en línea")
val Copy.restorePreviousRelay: String get() = t("Restore the previous relay first", "Stelle zuerst das vorherige Relais wieder her", "Restaure d'abord le relais précédent", "Ripristina prima il relè precedente", "Najprv obnov predchádzajúce relé", "Restaura primero el relé anterior")
fun Copy.needScrap(cost: Int, have: Int) = t("Need $cost scrap — you have $have", "Brauche $cost Schrott — du hast $have", "Il faut $cost ferraille — tu as $have", "Servono $cost rottame — ne hai $have", "Treba $cost šrotu — máš $have", "Necesitas $cost de chatarra: tienes $have")
val Copy.findRelayModule: String get() = t("Find a relay module before restoring this station", "Finde ein Relais-Modul, bevor du diese Station wiederherstellst", "Trouve un module relais avant de restaurer cette station", "Trova un modulo relè prima di ripristinare questa stazione", "Nájdi modul relé pred obnovením tejto stanice", "Encuentra un módulo de relé antes de restaurar esta estación")
fun Copy.relayRestoredMsg(index: Int, total: Int) = t("Relay $index/$total restored — signal back online", "Relais $index/$total wiederhergestellt — Signal wieder online", "Relais $index/$total restauré — signal de retour", "Relè $index/$total ripristinato — segnale di nuovo online", "Relé $index/$total obnovené — signál je späť", "Relé $index/$total restaurado: señal de nuevo en línea")
val Copy.adNotReady: String get() = t("Rewarded ad is not ready yet. Try again in a moment.", "Belohnte Werbung ist noch nicht bereit. Versuch es gleich nochmal.", "La pub récompensée n'est pas encore prête. Réessaie dans un instant.", "L'annuncio con ricompensa non è ancora pronto. Riprova tra poco.", "Odmenená reklama ešte nie je pripravená. Skús to o chvíľu.", "El anuncio recompensado aún no está listo. Prueba en un momento.")
val Copy.fitFuelTankFirst: String get() = t("Fit a fuel tank before using the pump", "Baue zuerst einen Tank ein", "Monte un réservoir avant d'utiliser la pompe", "Monta un serbatoio prima di usare la pompa", "Pred pumpou namontuj nádrž", "Monta un depósito antes de usar la bomba")
val Copy.enterFuelStation: String get() = t("Enter a fuel station first", "Zuerst eine Tankstelle betreten", "Entre d'abord dans une station", "Entra prima in una stazione di servizio", "Najprv vojdite na čerpaciu stanicu", "Entra primero en una gasolinera")
val Copy.noPumpHere: String get() = t("There is no pump here", "Hier gibt es keine Pumpe", "Il n'y a pas de pompe ici", "Qui non c'è una pompa", "Tu nie je pumpa", "Aquí no hay bomba")
val Copy.tankIsFull: String get() = t("The tank is full", "Der Tank ist voll", "Le réservoir est plein", "Il serbatoio è pieno", "Nádrž je plná", "El depósito está lleno")
val Copy.stopTheCarFirst: String get() = t("Stop the car first", "Zuerst das Auto anhalten", "Arrête d'abord la voiture", "Ferma prima l'auto", "Najprv zastav auto", "Para el coche primero")
val Copy.noTyresToSwap: String get() = t("No tyres to swap", "Keine Reifen zum Tauschen", "Pas de pneus à échanger", "Niente gomme da scambiare", "Žiadne gumy na výmenu", "No hay neumáticos que cambiar")
val Copy.swappedTyres: String get() = t("Swapped front and rear tyres", "Vorder- und Hinterreifen getauscht", "Pneus avant et arrière échangés", "Gomme anteriori e posteriori scambiate", "Predné a zadné gumy vymenené", "Neumáticos delantero y trasero intercambiados")
val Copy.backAtTheCar: String get() = t("Back at the car", "Zurück am Auto", "De retour à la voiture", "Di nuovo all'auto", "Späť pri aute", "De vuelta al coche")
fun Copy.salvagedScrap(n: Int) = t("Salvaged +$n scrap", "Geborgen +$n Schrott", "Récupéré +$n ferraille", "Recuperato +$n rottame", "Získané +$n šrotu", "Recuperado +$n de chatarra")
val Copy.packFull: String get() = t("Pack is full or too heavy", "Rucksack voll oder zu schwer", "Sac plein ou trop lourd", "Zaino pieno o troppo pesante", "Batoh je plný alebo ťažký", "La mochila está llena o pesa demasiado")
fun Copy.pickedUp(name: String) = t("Picked up: $name", "Aufgehoben: $name", "Ramassé : $name", "Raccolto: $name", "Zobrané: $name", "Recogido: $name")
val Copy.tookEverything: String get() = t("Took everything that fit", "Alles mitgenommen, was passte", "Tout pris ce qui rentrait", "Preso tutto ciò che stava", "Zobral som všetko, čo sa zmestilo", "Cogí todo lo que cabía")
fun Copy.scrappedLeftovers(n: Int) = t("Scrapped the leftovers: +$n scrap", "Reste verschrottet: +$n Schrott", "Restes ferraillés : +$n", "Rottamato il resto: +$n", "Zvyšok zošrotovaný: +$n šrotu", "Chatarrizado el resto: +$n")
fun Copy.nothingFitted(slot: String) = t("$slot: nothing is fitted", "$slot: nichts eingebaut", "$slot : rien n'est monté", "$slot: niente è montato", "$slot: nič nie je namontované", "$slot: no hay nada montado")
val Copy.mustStayFitted: String get() = t("That has to stay fitted — replace it with another part", "Das muss eingebaut bleiben — ersetze es durch ein anderes Teil", "Ça doit rester monté — remplace-le par une autre pièce", "Deve restare montato — sostituiscilo con un altro pezzo", "To musí ostať namontované — vymeň ho za iný diel", "Eso debe seguir montado: sustitúyelo por otra pieza")
val Copy.switchEngineOffFirst: String get() = t("Switch the engine off first", "Zuerst den Motor abstellen", "Coupe d'abord le moteur", "Spegni prima il motore", "Najprv vypni motor", "Apaga el motor primero")
val Copy.tapAgainToRemove: String get() = t("Tap again to remove", "Nochmal tippen zum Ausbauen", "Tape encore pour retirer", "Tocca di nuovo per rimuovere", "Ťukni znova na vymontovanie", "Toca otra vez para quitar")
val Copy.emptyItFirst: String get() = t("Empty it first — it would not all fit", "Zuerst leeren — es würde nicht alles reinpassen", "Vide-le d'abord — tout ne rentrerait pas", "Svuotalo prima — non ci starebbe tutto", "Najprv ho vyprázdni — všetko by sa nezmestilo", "Vacía primero: no cabría todo")
val Copy.noRoomForIt: String get() = t("No room for it", "Kein Platz dafür", "Pas de place", "Niente spazio", "Nie je na to miesto", "No hay sitio")
fun Copy.removedSlot(slot: String) = t("Removed: $slot", "Ausgebaut: $slot", "Retiré : $slot", "Rimosso: $slot", "Vymontované: $slot", "Quitado: $slot")
fun Copy.droppedItem(name: String) = t("Dropped: $name", "Weggeworfen: $name", "Jeté : $name", "Lasciato: $name", "Odhodené: $name", "Tirado: $name")
val Copy.stopAtCarFirst: String get() = t("Stop at the car first", "Zuerst am Auto halten", "Arrête-toi d'abord à la voiture", "Fermati prima all'auto", "Najprv zastav pri aute", "Para primero en el coche")
val Copy.stopBeforeDismantling: String get() = t("Stop before dismantling parts", "Anhalten, bevor du Teile ausbaust", "Arrête-toi avant de démonter", "Fermati prima di smontare", "Zastav pred demontážou dielov", "Para antes de desmontar piezas")
fun Copy.scrappedItem(name: String, n: Int) = t("Scrapped $name: +$n scrap", "$name verschrottet: +$n Schrott", "$name ferraillé : +$n", "Rottamato $name: +$n", "Zošrotované $name: +$n šrotu", "Chatarrizado $name: +$n")
fun Copy.noRoomIn(where: String) = t("No room in the $where", "Kein Platz im $where", "Pas de place dans $where", "Niente spazio in $where", "Nie je miesto v: $where", "No hay sitio en $where")
fun Copy.movedTo(where: String, name: String) = t("Moved to the $where: $name", "In $where gelegt: $name", "Déplacé dans $where : $name", "Spostato in $where: $name", "Presunuté do $where: $name", "Movido a $where: $name")
val Copy.wherePack: String get() = t("pack", "Rucksack", "sac", "zaino", "batohu", "mochila")
val Copy.whereBoot: String get() = t("boot", "Kofferraum", "coffre", "bagagliaio", "kufri", "maletero")
fun Copy.fitBeforeFluid(part: String) = t("Fit the $part before using this fluid", "Baue $part ein, bevor du diese Flüssigkeit nutzt", "Monte le $part avant d'utiliser ce fluide", "Monta il $part prima di usare questo liquido", "Namontuj $part pred použitím tejto kvapaliny", "Monta el $part antes de usar este líquido")
fun Copy.alreadyFull(fluid: String) = t("Already full ($fluid)", "Schon voll ($fluid)", "Déjà plein ($fluid)", "Già pieno ($fluid)", "Už plné ($fluid)", "Ya está lleno ($fluid)")
fun Copy.removedNoRoom(name: String) = t("Removed $name left by the road — no room", "$name an der Straße gelassen — kein Platz", "$name laissé au bord — pas de place", "$name lasciato in strada — niente spazio", "$name nechané pri ceste — nie je miesto", "$name dejado en la carretera: no hay sitio")
fun Copy.fittedTo(name: String, slot: String, note: String) = t("Fitted: $name → $slot$note", "Eingebaut: $name → $slot$note", "Monté : $name → $slot$note", "Montato: $name → $slot$note", "Namontované: $name → $slot$note", "Montado: $name → $slot$note")
val Copy.thatCannotBeUsed: String get() = t("That cannot be used", "Das kann nicht genutzt werden", "Ça ne peut pas être utilisé", "Non si può usare", "Toto sa nedá použiť", "Eso no se puede usar")
val Copy.stopFirst: String get() = t("Stop first", "Zuerst anhalten", "Arrête-toi d'abord", "Fermati prima", "Najprv zastav", "Para primero")
val Copy.turnEngineOffDrain: String get() = t("Turn the engine off before draining fluids", "Motor abstellen, bevor du Flüssigkeiten ablässt", "Coupe le moteur avant de vidanger", "Spegni il motore prima di scaricare i liquidi", "Pred vypustením kvapalín vypni motor", "Apaga el motor antes de vaciar líquidos")
val Copy.nothingToDrain: String get() = t("Nothing to drain", "Nichts abzulassen", "Rien à vidanger", "Niente da scaricare", "Nie je čo vypustiť", "No hay nada que vaciar")
fun Copy.drained(amount: String, fluid: String, extra: String) = t("Drained $amount L $fluid$extra", "$amount L $fluid abgelassen$extra", "Vidangé $amount L $fluid$extra", "Scaricati $amount L $fluid$extra", "Vypustené $amount L $fluid$extra", "Vaciados $amount L $fluid$extra")
val Copy.tyreShredded: String get() = t("That tyre is shredded — fit a new one", "Dieser Reifen ist zerfetzt — baue einen neuen ein", "Ce pneu est déchiré — monte-en un neuf", "Questa gomma è a brandelli — montane una nuova", "Tá pneumatika je roztrhnutá — daj novú", "Ese neumático está destrozado: monta uno nuevo")
fun Copy.permanentUpgrade(name: String) = t("$name is a permanent upgrade", "$name ist ein dauerhaftes Upgrade", "$name est une amélioration permanente", "$name è un upgrade permanente", "$name je trvalé vylepšenie", "$name es una mejora permanente")
val Copy.thatOneIsFine: String get() = t("That one is fine", "Der ist in Ordnung", "Celui-là va bien", "Quello sta bene", "Tento je v poriadku", "Ese está bien")
fun Copy.repairedSlot(slot: String) = t("Repaired: $slot", "Repariert: $slot", "Réparé : $slot", "Riparato: $slot", "Opravené: $slot", "Reparado: $slot")
fun Copy.roughPatch(slot: String) = t("Rough patch-up: $slot", "Notreparatur: $slot", "Réparation de fortune : $slot", "Riparazione di fortuna: $slot", "Provizórna oprava: $slot", "Apaño: $slot")
fun Copy.doesNotNeedRepair(slot: String) = t("$slot does not need repair", "$slot braucht keine Reparatur", "$slot n'a pas besoin de réparation", "$slot non serve riparazione", "$slot nepotrebuje opravu", "$slot no necesita reparación")
fun Copy.repairedCost(slot: String, cost: Int) = t("Repaired $slot: -$cost scrap", "$slot repariert: -$cost Schrott", "$slot réparé : -$cost ferraille", "$slot riparato: -$cost rottame", "Opravené $slot: -$cost šrotu", "Reparado $slot: -$cost chatarra")
val Copy.parkTheCarFirst: String get() = t("Park the car first", "Zuerst das Auto parken", "Gare d'abord la voiture", "Parcheggia prima l'auto", "Najprv zaparkuj auto", "Aparca el coche primero")
val Copy.thatIsNotATyre: String get() = t("That is not a tyre", "Das ist kein Reifen", "Ce n'est pas un pneu", "Non è una gomma", "Toto nie je pneumatika", "Eso no es un neumático")
val Copy.tyreNotFlat: String get() = t("That tyre is not flat", "Dieser Reifen ist nicht platt", "Ce pneu n'est pas à plat", "Questa gomma non è forata", "Tá pneumatika nie je prázdna", "Ese neumático no está deshinchado")
fun Copy.patchedSlot(slot: String) = t("Patched the $slot", "$slot geflickt", "$slot réparé", "$slot rattoppato", "Záplatovaná $slot", "Parcheado el $slot")
fun Copy.needKitOrScrap(cost: Int) = t("Need a puncture kit or $cost scrap", "Brauche ein Flickenset oder $cost Schrott", "Il faut un kit crevaison ou $cost ferraille", "Serve un kit foratura o $cost rottame", "Treba sadu na defekt alebo $cost šrotu", "Necesitas un kit antipinchazos o $cost de chatarra")
fun Copy.patchedCost(slot: String, cost: Int) = t("Patched the $slot: -$cost scrap", "$slot geflickt: -$cost Schrott", "$slot réparé : -$cost", "$slot rattoppato: -$cost", "Záplatovaná $slot: -$cost šrotu", "Parcheado el $slot: -$cost chatarra")
val Copy.noFlatToPatch: String get() = t("No flat tyre to patch", "Kein platter Reifen zum Flicken", "Pas de pneu à plat à réparer", "Nessuna gomma forata da rattoppare", "Žiadna prázdna guma na záplatu", "No hay neumático deshinchado que parchear")
fun Copy.noDirectUpgrade(slot: String) = t("No direct upgrade for $slot", "Kein direktes Upgrade für $slot", "Pas d'amélioration directe pour $slot", "Nessun upgrade diretto per $slot", "Žiadne priame vylepšenie pre $slot", "No hay mejora directa para $slot")
fun Copy.alreadyHasPaint(name: String) = t("The car already has $name paint", "Das Auto hat schon $name-Lack", "La voiture a déjà la peinture $name", "L'auto ha già la vernice $name", "Auto už má lak $name", "El coche ya tiene pintura $name")
fun Copy.needScrapRepaint(cost: Int, have: Int) = t("Need $cost scrap to repaint — you have $have", "Brauche $cost Schrott zum Lackieren — du hast $have", "Il faut $cost ferraille pour repeindre — tu as $have", "Servono $cost rottame per riverniciare — ne hai $have", "Na prelakovanie treba $cost šrotu — máš $have", "Necesitas $cost de chatarra para pintar: tienes $have")
fun Copy.repaintedAd(name: String) = t("Repainted the car $name — ad reward", "Auto $name lackiert — Werbebelohnung", "Voiture repeinte $name — récompense pub", "Auto riverniciata $name — ricompensa ads", "Auto prelakované $name — odmena za reklamu", "Coche repintado $name: recompensa de anuncio")
fun Copy.repaintedCost(name: String, cost: Int) = t("Repainted the car $name: -$cost scrap", "Auto $name lackiert: -$cost Schrott", "Voiture repeinte $name : -$cost", "Auto riverniciata $name: -$cost", "Auto prelakované $name: -$cost šrotu", "Coche repintado $name: -$cost chatarra")
val Copy.engineAlreadyRunning: String get() = t("Engine is already running", "Motor läuft bereits", "Le moteur tourne déjà", "Il motore è già acceso", "Motor už beží", "El motor ya está en marcha")
val Copy.engineRunningHold: String get() = t("Engine running — hold the throttle", "Motor läuft — Gas halten", "Moteur allumé — tiens l'accélérateur", "Motore acceso — tieni l'acceleratore", "Motor beží — drž plyn", "Motor en marcha: mantén el acelerador")
val Copy.fuelIsLow: String get() = t("Fuel is low", "Kraftstoff ist niedrig", "Carburant bas", "Carburante basso", "Paliva je málo", "Queda poco combustible")
val Copy.oilIsLow: String get() = t("Oil is low", "Öl ist niedrig", "Huile basse", "Olio basso", "Oleja je málo", "Queda poco aceite")
val Copy.coolantIsLow: String get() = t("Coolant is low", "Kühlmittel ist niedrig", "Liquide bas", "Refrigerante basso", "Chladiacej je málo", "Queda poco refrigerante")
val Copy.engineWouldNotStart: String get() = t("The engine would not start", "Der Motor sprang nicht an", "Le moteur n'a pas démarré", "Il motore non è partito", "Motor nenaštartoval", "El motor no arrancó")
val Copy.engineSwitchedOff: String get() = t("Engine switched off", "Motor ausgeschaltet", "Moteur coupé", "Motore spento", "Motor vypnutý", "Motor apagado")
val Copy.stillLightOut: String get() = t("It is still light out", "Es ist noch hell", "Il fait encore jour", "È ancora giorno", "Ešte je svetlo", "Todavía hay luz")
val Copy.sleepNotWreck: String get() = t("You can only sleep beside a building, not a wreck", "Schlafen nur neben einem Gebäude, nicht am Wrack", "Tu ne peux dormir que près d'un bâtiment, pas d'une épave", "Puoi dormire solo accanto a un edificio, non a un relitto", "Spať môžeš len pri budove, nie pri vraku", "Solo puedes dormir junto a un edificio, no un pecio")
val Copy.sleepBesideBuilding: String get() = t("You can only sleep beside a building", "Schlafen nur neben einem Gebäude", "Tu ne peux dormir que près d'un bâtiment", "Puoi dormire solo accanto a un edificio", "Spať môžeš len pri budove", "Solo puedes dormir junto a un edificio")
val Copy.cannotSleepHere: String get() = t("You cannot sleep here", "Hier kannst du nicht schlafen", "Tu ne peux pas dormir ici", "Non puoi dormire qui", "Tu nemôžeš spať", "No puedes dormir aquí")
val Copy.dawnBattery: String get() = t("Dawn. Battery slightly lower.", "Morgengrauen. Batterie etwas schwächer.", "Aube. Batterie un peu plus basse.", "Alba. Batteria un po' più bassa.", "Úsvit. Batéria trochu klesla.", "Amanecer. Batería un poco más baja.")
val Copy.dawnBatteryEmptyRad: String get() = t("Dawn. Battery slightly lower. Radiator is still empty.", "Morgengrauen. Batterie etwas schwächer. Kühler noch leer.", "Aube. Batterie un peu plus basse. Radiateur encore vide.", "Alba. Batteria un po' più bassa. Radiatore ancora vuoto.", "Úsvit. Batéria trochu klesla. Chladič je stále prázdny.", "Amanecer. Batería un poco más baja. El radiador sigue vacío.")
val Copy.ranDry: String get() = t("Ran dry — top the tank up from your pack", "Leergefahren — tanke aus dem Rucksack nach", "À sec — remplis depuis le sac", "A secco — rabbocca dallo zaino", "Došlo — doliaj z batohu", "Se secó: rellena desde la mochila")
val Copy.batteryFlatFit: String get() = t("Battery is flat — fit a charged one", "Batterie leer — baue eine geladene ein", "Batterie à plat — monte-en une chargée", "Batteria scarica — montane una carica", "Batéria je vybitá — daj nabitú", "Batería agotada: monta una cargada")
val Copy.coolingShot: String get() = t("Cooling is shot — fit a radiator before it cooks the engine", "Kühlung hinüber — baue einen Kühler ein, bevor der Motor kocht", "Refroidissement HS — monte un radiateur avant que ça cuise le moteur", "Raffreddamento ko — monta un radiatore prima che cuocia il motore", "Chladenie je fuč — daj chladič, kým motor neuvarí", "La refrigeración está rota: monta un radiador antes de que cueza el motor")
val Copy.engineCutHeat: String get() = t("Engine cut out from the heat — let it cool", "Motor ist wegen Hitze ausgegangen — abkühlen lassen", "Moteur calé par la chaleur — laisse-le refroidir", "Motore spento per il caldo — lascialo raffreddare", "Motor zhasol od tepla — nechaj ho vychladnúť", "El motor se paró por el calor: déjalo enfriar")
val Copy.engineFinished: String get() = t("The engine is finished — swap it out", "Der Motor ist hinüber — tausche ihn", "Le moteur est fini — remplace-le", "Il motore è finito — sostituiscilo", "Motor je fuč — vymeň ho", "El motor está acabado: cámbialo")
fun Copy.causeFatal(fatal: String) = t("Cause: $fatal.", "Ursache: $fatal.", "Cause : $fatal.", "Causa: $fatal.", "Príčina: $fatal.", "Causa: $fatal.")
val Copy.outOfFuelDot: String get() = t("Out of fuel.", "Kein Kraftstoff.", "Plus de carburant.", "Senza carburante.", "Došlo palivo.", "Sin combustible.")
val Copy.batteryDrained: String get() = t("Battery drained. Engine would not start.", "Batterie leer. Motor sprang nicht an.", "Batterie à plat. Le moteur n'a pas démarré.", "Batteria scarica. Il motore non è partito.", "Batéria vybitá. Motor nenaštartoval.", "Batería agotada. El motor no arrancó.")
val Copy.shreddedEnd: String get() = t("The tyre is shredded to the rim. No spare, nowhere to fit a new one.", "Der Reifen ist bis auf die Felge zerfetzt. Kein Ersatz, nirgends ein neuer.", "Le pneu est déchiré jusqu'à la jante. Pas de rechange.", "La gomma è a brandelli sul cerchio. Niente ricambio.", "Pneumatika je roztrhnutá na ráfik. Žiadna náhrada.", "El neumático está destrozado hasta la llanta. Sin recambio.")
val Copy.stuckEnd: String get() = t("Wheels spinning in place. No spare that would get you moving.", "Räder drehen auf der Stelle. Kein Ersatz, der dich bewegt.", "Roues qui patinent. Pas de rechange pour bouger.", "Ruote che slittano. Niente ricambio per muoverti.", "Kolesá sa točia na mieste. Žiadna náhrada, ktorá ťa pohne.", "Ruedas patinando. Sin recambio que te mueva.")
val Copy.arrivedEnd: String get() = t("The final relay is online. The route is complete.", "Das letzte Relais ist online. Die Route ist fertig.", "Le dernier relais est en ligne. L'itinéraire est complet.", "L'ultimo relè è online. Il percorso è completo.", "Posledné relé je online. Trasa je hotová.", "El último relé está en línea. La ruta está completa.")
val Copy.endlessModeMsg: String get() = t("Endless mode — the story is complete, but the road continues", "Endlosmodus — die Geschichte ist fertig, aber die Straße geht weiter", "Mode Infini — l'histoire est finie, mais la route continue", "Modalità Infinito — la storia è finita, ma la strada continua", "Režim Nekonečno — príbeh je hotový, ale cesta pokračuje", "Modo Infinito: la historia terminó, pero la carretera sigue")
fun Copy.runRestored(km: String) = t("Run restored — you were at $km km.", "Fahrt wiederhergestellt — du warst bei $km km.", "Course restaurée — tu étais à $km km.", "Corsa ripristinata — eri a $km km.", "Jazda obnovená — bol si na $km km.", "Carrera restaurada: estabas en $km km.")
val Copy.workshopUnlocksLater: String get() = t("This workshop upgrade unlocks further down the road", "Dieses Werkstatt-Upgrade schaltet sich weiter vorne frei", "Cette amélioration d'atelier se débloque plus loin", "Questo upgrade officina si sblocca più avanti", "Toto vylepšenie dielne sa odomkne neskôr na ceste", "Esta mejora de taller se desbloquea más adelante")
val Copy.engineOffToRespray: String get() = t("Switch the engine off to respray.", "Motor abstellen zum Lackieren.", "Coupe le moteur pour repeindre.", "Spegni il motore per riverniciare.", "Na prelakovanie vypni motor.", "Apaga el motor para repintar.")
val Copy.engineOffToUpgrade: String get() = t("Switch the engine off to upgrade.", "Motor abstellen zum Upgraden.", "Coupe le moteur pour améliorer.", "Spegni il motore per l'upgrade.", "Na vylepšenie vypni motor.", "Apaga el motor para mejorar.")
val Copy.engineOffToService: String get() = t("Switch the engine off to respray or upgrade.", "Motor abstellen zum Lackieren oder Upgraden.", "Coupe le moteur pour repeindre ou améliorer.", "Spegni il motore per riverniciare o fare upgrade.", "Na prelakovanie alebo vylepšenie vypni motor.", "Apaga el motor para repintar o mejorar.")
val Copy.takeCarToPaint: String get() = t("Take the car to a paint shop", "Bring das Auto in eine Lackiererei", "Amène la voiture dans un atelier peinture", "Porta l'auto in carrozzeria", "Zaveď auto do lakovačky", "Lleva el coche a un taller de pintura")
val Copy.paintAfter10km: String get() = t("Paint service after 10 km", "Lackierservice nach 10 km", "Service peinture après 10 km", "Servizio vernice dopo 10 km", "Lakovačka po 10 km", "Pintura después de 10 km")
val Copy.fitNewTyre: String get() = t("Fit a new tyre", "Neuen Reifen einbauen", "Monte un pneu neuf", "Monta una gomma nuova", "Daj novú pneumatiku", "Monta un neumático nuevo")
val Copy.patchKitInBag: String get() = t("Patch kit in the bag", "Flickenset im Rucksack", "Kit crevaison dans le sac", "Kit foratura nello zaino", "Sada na defekt v batohu", "Kit antipinchazos en la mochila")
val Copy.patchThePuncture: String get() = t("Patch the puncture", "Platten flicken", "Répare la crevaison", "Rattoppa la foratura", "Záplataj defekt", "Parchea el pinchazo")
val Copy.upgradeAtWorkshop: String get() = t("Upgrade at the workshop", "In der Werkstatt upgraden", "Améliore à l'atelier", "Upgrade in officina", "Vylepši v dielni", "Mejora en el taller")
fun Copy.gripLine(now: String, max: String, snow: String) = t("Grip $now/$max · Snow $snow%", "Grip $now/$max · Schnee $snow%", "Adhérence $now/$max · Neige $snow%", "Grip $now/$max · Neve $snow%", "Grip $now/$max · Sneh $snow%", "Agarre $now/$max · Nieve $snow%")
fun Copy.rideHeight(cm: String, travel: String) = t("Ride height $cm cm · travel $travel cm", "Bodenfreiheit $cm cm · Federweg $travel cm", "Garde au sol $cm cm · débattement $travel cm", "Altezza $cm cm · corsa $travel cm", "Svetlá výška $cm cm · zdvih $travel cm", "Altura $cm cm · recorrido $travel cm")
fun Copy.layoutLine(name: String) = t("Layout $name", "Antrieb $name", "Architecture $name", "Schema $name", "Usporiadanie $name", "Esquema $name")
fun Copy.chargingLine(out: Int, cap: Int) = t("Charging output $out % · battery capacity $cap %", "Ladeleistung $out % · Batteriekapazität $cap %", "Charge $out % · capacité batterie $cap %", "Carica $out % · capacità batteria $cap %", "Nabíjanie $out % · kapacita batérie $cap %", "Carga $out % · capacidad $cap %")
fun Copy.electricalLine(soc: Int, health: Int) = t("Electrical charge $soc % · health $health %", "Ladung $soc % · Zustand $health %", "Charge $soc % · état $health %", "Carica $soc % · salute $health %", "Nabitie $soc % · stav $health %", "Carga $soc % · salud $health %")
fun Copy.cargoBonus(slots: Int, kg: Int, drag: Boolean) = t(
    "+$slots slots · +$kg kg" + if (drag) " · adds aerodynamic drag" else "",
    "+$slots Slots · +$kg kg" + if (drag) " · mehr Luftwiderstand" else "",
    "+$slots emplacements · +$kg kg" + if (drag) " · plus de traînée" else "",
    "+$slots slot · +$kg kg" + if (drag) " · più resistenza aerodinamica" else "",
    "+$slots slotov · +$kg kg" + if (drag) " · pridáva odpor vzduchu" else "",
    "+$slots huecos · +$kg kg" + if (drag) " · añade resistencia aerodinámica" else ""
)
fun Copy.chainsHint(snow: String, dry: String, kmh: Int) = t(
    "On snow ×$snow grip · on dry tarmac ×$dry, heavy drag above $kmh km/h",
    "Auf Schnee ×$snow Grip · auf trockenem Asphalt ×$dry, starker Widerstand über $kmh km/h",
    "Sur neige ×$snow · sur asphalte sec ×$dry, forte traînée au-dessus de $kmh km/h",
    "Su neve ×$snow · su asfalto asciutto ×$dry, forte resistenza sopra $kmh km/h",
    "Na snehu ×$snow grip · na suchom asfalte ×$dry, silný odpor nad $kmh km/h",
    "En nieve ×$snow · en asfalto seco ×$dry, mucha resistencia sobre $kmh km/h"
)
fun Copy.tireInjuryLabel(injury: TireInjury, hasKit: Boolean) = when (injury) {
    TireInjury.SHREDDED -> fitNewTyre
    TireInjury.PUNCTURED -> if (hasKit) patchKitInBag else patchThePuncture
    else -> null
}

fun Copy.taking(label: String) = t("Taking $label", "Fahre $label", "Prise de $label", "Prendo $label", "Beriem $label", "Tomando $label")
val Copy.startTheEngine: String get() = t("Start the engine", "Starte den Motor", "Démarre le moteur", "Avvia il motore", "Naštartuj motor", "Arranca el motor")
fun Copy.pumpEmpty(kind: String) = t("The $kind pump is empty", "Die $kind-Pumpe ist leer", "La pompe $kind est vide", "La pompa $kind è vuota", "Pumpa $kind je prázdna", "La bomba de $kind está vacía")
fun Copy.filledUp(moved: String, kind: String, grade: String, remaining: String) = t(
    "Filled up +$moved L $kind · $grade (pump has $remaining L)",
    "Getankt +$moved L $kind · $grade (Pumpe hat $remaining L)",
    "Plein +$moved L $kind · $grade (pompe : $remaining L)",
    "Rifornito +$moved L $kind · $grade (pompa: $remaining L)",
    "Natankované +$moved L $kind · $grade (pumpa má $remaining L)",
    "Llenado +$moved L $kind · $grade (la bomba tiene $remaining L)"
)
fun Copy.itemAhead(name: String) = t("$name ahead — stop to pick it up", "$name voraus — anhalten und mitnehmen", "$name plus loin — arrête-toi", "$name più avanti — fermati a prenderlo", "$name vpredu — zastav a zober", "$name adelante: para a recogerlo")
val Copy.wreckAhead: String get() = t("Wreck ahead — scrap and parts", "Wrack voraus — Schrott und Teile", "Épave plus loin — ferraille et pièces", "Relitto più avanti — rottame e pezzi", "Vrak vpredu — šrot a diely", "Pecio adelante: chatarra y piezas")
fun Copy.toppedUp(amount: String, label: String) = t("Topped up +$amount L $label", "Nachgefüllt +$amount L $label", "Complété +$amount L $label", "Rabboccato +$amount L $label", "Doplnené +$amount L $label", "Rellenado +$amount L $label")
fun Copy.toppedUpGrade(amount: String, grade: String) = t("Topped up +$amount L — $grade", "Nachgefüllt +$amount L — $grade", "Complété +$amount L — $grade", "Rabboccato +$amount L — $grade", "Doplnené +$amount L — $grade", "Rellenado +$amount L — $grade")
fun Copy.withFluidInIt(amount: String) = t(" — with $amount L still in it", " — noch $amount L drin", " — encore $amount L dedans", " — ancora $amount L dentro", " — ešte $amount L vnútri", " — aún $amount L dentro")
fun Copy.dryNeeds(fluid: String) = t(" — dry, needs $fluid", " — trocken, braucht $fluid", " — vide, il faut $fluid", " — vuoto, serve $fluid", " — suché, treba $fluid", " — seco, necesita $fluid")
fun Copy.litresRemain(amount: String) = t(" — $amount L remains", " — $amount L bleiben", " — il reste $amount L", " — restano $amount L", " — ostáva $amount L", " — quedan $amount L")
val Copy.emptySuffix: String get() = t(" — empty", " — leer", " — vide", " — vuoto", " — prázdne", " — vacío")
val Copy.takeToWorkshop: String get() = t("Take the car to a workshop before upgrading", "Bring das Auto in eine Werkstatt vor dem Upgrade", "Amène la voiture à l'atelier avant d'améliorer", "Porta l'auto in officina prima dell'upgrade", "Pred vylepšením zaveď auto do dielne", "Lleva el coche a un taller antes de mejorar")
val Copy.workshopAlreadyUsedAd: String get() = t("This workshop already used its free ad", "Diese Werkstatt hat die Gratiswerbung schon genutzt", "Cet atelier a déjà utilisé sa pub gratuite", "Questa officina ha già usato l'ads gratis", "Táto dielňa už použila voľnú reklamu", "Este taller ya usó su anuncio gratis")
val Copy.upgradeNotAdOffer: String get() = t("That upgrade is not the ad offer in this workshop", "Das ist nicht das Werbe-Upgrade dieser Werkstatt", "Ce n'est pas l'offre pub de cet atelier", "Questo non è l'upgrade ads di questa officina", "Toto nie je reklamná ponuka tejto dielne", "Esa no es la oferta de anuncio de este taller")
fun Copy.upgradedAd(slot: String, name: String) = t("Upgraded $slot: $name — ad reward", "$slot aufgerüstet: $name — Werbebelohnung", "$slot amélioré : $name — récompense pub", "$slot aggiornato: $name — ricompensa ads", "Vylepšené $slot: $name — odmena za reklamu", "Mejorado $slot: $name: recompensa de anuncio")
fun Copy.upgradedCost(slot: String, name: String, cost: Int) = t("Upgraded $slot: $name (-$cost scrap)", "$slot aufgerüstet: $name (-$cost Schrott)", "$slot amélioré : $name (-$cost ferraille)", "$slot aggiornato: $name (-$cost rottame)", "Vylepšené $slot: $name (-$cost šrotu)", "Mejorado $slot: $name (-$cost chatarra)")
val Copy.shopAlreadyUsedAd: String get() = t("This shop already used its free ad", "Dieser Laden hat die Gratiswerbung schon genutzt", "Cette boutique a déjà utilisé sa pub gratuite", "Questo negozio ha già usato l'ads gratis", "Tento obchod už použil voľnú reklamu", "Esta tienda ya usó su anuncio gratis")
fun Copy.roadsideFuelDrained(kind: String) = t(
    "Roadside help drained the tank and delivered 12 L of $kind",
    "Straßenhilfe hat den Tank geleert und 12 L $kind geliefert",
    "L'assistance a vidé le réservoir et livré 12 L de $kind",
    "L'assistenza ha svuotato il serbatoio e consegnato 12 L di $kind",
    "Cestná pomoc vypustila nádrž a doniesla 12 L $kind",
    "La asistencia vació el depósito y entregó 12 L de $kind"
)
val Copy.roadsideFuel12: String get() = t("Roadside help delivered 12 L of fuel", "Straßenhilfe lieferte 12 L Kraftstoff", "L'assistance a livré 12 L de carburant", "L'assistenza ha consegnato 12 L di carburante", "Cestná pomoc doniesla 12 L paliva", "La asistencia entregó 12 L de combustible")
val Copy.roadsideBattery50: String get() = t("Roadside help restored 50 % battery charge", "Straßenhilfe stellte 50 % Batterieladung wieder her", "L'assistance a restauré 50 % de batterie", "L'assistenza ha ripristinato il 50 % della batteria", "Cestná pomoc obnovila 50 % nabitia batérie", "La asistencia restauró el 50 % de batería")
val Copy.roadsideBatteryFit: String get() = t("Roadside help fitted a battery and charged it", "Straßenhilfe hat eine Batterie eingebaut und geladen", "L'assistance a monté et chargé une batterie", "L'assistenza ha montato e caricato una batteria", "Cestná pomoc namontovala batériu a nabila ju", "La asistencia montó una batería y la cargó")
val Copy.roadsideRadiator: String get() = t("Roadside help repaired the radiator, topped coolant and cooled the engine", "Straßenhilfe hat den Kühler repariert, Kühlmittel nachgefüllt und den Motor gekühlt", "L'assistance a réparé le radiateur, complété le liquide et refroidi le moteur", "L'assistenza ha riparato il radiatore, rabboccato il refrigerante e raffreddato il motore", "Cestná pomoc opravila chladič, doliala chladiacu a schladila motor", "La asistencia reparó el radiador, rellenó refrigerante y enfrió el motor")
val Copy.roadsideCoolant: String get() = t("Roadside help delivered 3 L of coolant and cooled the engine", "Straßenhilfe lieferte 3 L Kühlmittel und kühlte den Motor", "L'assistance a livré 3 L de liquide et refroidi le moteur", "L'assistenza ha consegnato 3 L di refrigerante e raffreddato il motore", "Cestná pomoc doniesla 3 L chladiacej a schladila motor", "La asistencia entregó 3 L de refrigerante y enfrió el motor")
val Copy.roadsideOilDrained: String get() = t("Roadside help drained the sump and delivered 2 L of oil", "Straßenhilfe hat die Ölwanne geleert und 2 L Öl geliefert", "L'assistance a vidangé et livré 2 L d'huile", "L'assistenza ha scaricato e consegnato 2 L di olio", "Cestná pomoc vypustila vaňu a doniesla 2 L oleja", "La asistencia vació el cárter y entregó 2 L de aceite")
val Copy.roadsideOil: String get() = t("Roadside help delivered 2 L of oil", "Straßenhilfe lieferte 2 L Öl", "L'assistance a livré 2 L d'huile", "L'assistenza ha consegnato 2 L di olio", "Cestná pomoc doniesla 2 L oleja", "La asistencia entregó 2 L de aceite")
val Copy.roadsideEngine50: String get() = t("Roadside help restored 50 % engine health", "Straßenhilfe stellte 50 % Motorzustand wieder her", "L'assistance a restauré 50 % du moteur", "L'assistenza ha ripristinato il 50 % del motore", "Cestná pomoc obnovila 50 % zdravia motora", "La asistencia restauró el 50 % del motor")
val Copy.towedToShop: String get() = t("Towed to the repair shop — loot a tyre and fit it", "Abgeschleppt zur Werkstatt — Reifen plündern und einbauen", "Remorqué à l'atelier — prends un pneu et monte-le", "Trainato in officina — prendi una gomma e montala", "Odťahované do dielne — zober gumu a namontuj", "Remolcado al taller: coge un neumático y móntalo")
val Copy.roadsideGaragePulledUp: String get() = t("A roadside garage pulled up on firmer ground — loot a tyre and fit it", "Eine Straßengarage hielt auf festem Boden — Reifen plündern und einbauen", "Un garage routier s'est arrêté sur sol ferme — prends un pneu", "Un'officina stradale si è fermata su terreno solido — prendi una gomma", "Cestná garáž zastala na pevnejšom povrchu — zober gumu a namontuj", "Un taller de carretera se detuvo en suelo firme: coge un neumático")
val Copy.inTheHouseHere: String get() = t("In the house right here", "Im Haus direkt hier", "Dans la maison ici", "Nella casa proprio qui", "V dome hneď tu", "En la casa justo aquí")
val Copy.inTheGarageHere: String get() = t("In the garage right here", "In der Garage direkt hier", "Dans le garage ici", "Nel garage proprio qui", "V garáži hneď tu", "En el garaje justo aquí")
val Copy.atThePumpHere: String get() = t("At the pump right here", "An der Pumpe direkt hier", "À la pompe ici", "Alla pompa proprio qui", "Pri pumpe hneď tu", "En la bomba justo aquí")
val Copy.inTheWorkshopHere: String get() = t("In the workshop right here", "In der Werkstatt direkt hier", "Dans l'atelier ici", "In officina proprio qui", "V dielni hneď tu", "En el taller justo aquí")
val Copy.inThePaintShopHere: String get() = t("In the paint shop right here", "In der Lackiererei direkt hier", "Dans l'atelier peinture ici", "In carrozzeria proprio qui", "V lakovačke hneď tu", "En el taller de pintura justo aquí")
val Copy.inTheWreckHere: String get() = t("In the wreck right here", "Im Wrack direkt hier", "Dans l'épave ici", "Nel relitto proprio qui", "Vo vraku hneď tu", "En el pecio justo aquí")
fun Copy.stashFound(where: String, items: String) = t("$where — $items. Check BUILDING.", "$where — $items. Prüfe GEBÄUDE.", "$where — $items. Vois BÂTIMENT.", "$where — $items. Controlla EDIFICIO.", "$where — $items. Pozri BUDOVU.", "$where — $items. Mira EDIFICIO.")
fun Copy.dugOutOfBoot(items: String) = t("Dug out of the boot: $items. Check PACK.", "Aus dem Kofferraum: $items. Prüfe PACK.", "Sorti du coffre : $items. Vois SAC.", "Trovato nel bagagliaio: $items. Controlla ZAINO.", "Vybraté z kufra: $items. Pozri BATOH.", "Sacado del maletero: $items. Mira MOCHILA.")
fun Copy.branchHint(style: BranchStyle) = when (style) {
    BranchStyle.SAFE_RURAL -> t("Smoother road · more buildings", "Glattere Straße · mehr Gebäude", "Route plus lisse · plus de bâtiments", "Strada più liscia · più edifici", "Hladšia cesta · viac budov", "Carretera más lisa · más edificios")
    BranchStyle.FOREST -> t("Hills · fewer buildings", "Hügel · weniger Gebäude", "Collines · moins de bâtiments", "Colline · meno edifici", "Kopce · menej budov", "Colinas · menos edificios")
    BranchStyle.FOREST_ALIVE -> t("Hills · moderate loot", "Hügel · mittlerer Loot", "Collines · butin moyen", "Colline · bottino medio", "Kopce · stredná korisť", "Colinas · botín medio")
    BranchStyle.INDUSTRIAL -> t("More parts · higher fuel use", "Mehr Teile · höherer Verbrauch", "Plus de pièces · plus de conso", "Più pezzi · più consumo", "Viac dielov · vyššia spotreba", "Más piezas · más consumo")
    BranchStyle.DESERT -> t("Fewer buildings · more fuel use", "Weniger Gebäude · mehr Verbrauch", "Moins de bâtiments · plus de conso", "Meno edifici · più consumo", "Menej budov · viac paliva", "Menos edificios · más consumo")
    BranchStyle.DESERT_DUSK -> t("Few buildings · more fuel use", "Wenig Gebäude · mehr Verbrauch", "Peu de bâtiments · plus de conso", "Pochi edifici · più consumo", "Málo budov · viac paliva", "Pocos edificios · más consumo")
    BranchStyle.SHORTCUT_RISK -> t("Rougher road · more loot", "Rauhere Straße · mehr Loot", "Route plus rude · plus de butin", "Strada più dura · più bottino", "Drsnejšia cesta · viac koristi", "Carretera más dura · más botín")
    BranchStyle.SANDSTORM -> t("Sand · high fuel use · more loot", "Sand · hoher Verbrauch · mehr Loot", "Sable · forte conso · plus de butin", "Sabbia · alto consumo · più bottino", "Piesok · vysoká spotreba · viac koristi", "Arena · alto consumo · más botín")
    BranchStyle.DUST_STORM -> t("Low visibility · more buildings", "Schlechte Sicht · mehr Gebäude", "Faible visibilité · plus de bâtiments", "Bassa visibilità · più edifici", "Zlá viditeľnosť · viac budov", "Baja visibilidad · más edificios")
    BranchStyle.ALPINE -> t("Snow · steep · winter tyres", "Schnee · steil · Winterreifen", "Neige · raide · pneus hiver", "Neve · ripido · gomme invernali", "Sneh · strmé · zimné gumy", "Nieve · empinado · neumáticos de invierno")
}
val Copy.junction: String get() = t("JUNCTION", "KREUZUNG", "CARREFOUR", "INCROCIO", "RÁZCESTIE", "CRUCE")
val Copy.junctionSubtitle: String get() = t("The road continues. Hints show fuel, loot and terrain.", "Die Straße geht weiter. Hinweise zeigen Kraftstoff, Loot und Gelände.", "La route continue. Les indices montrent carburant, butin et terrain.", "La strada continua. I suggerimenti mostrano carburante, bottino e terreno.", "Cesta pokračuje. Nápovedy ukazujú palivo, korisť a terén.", "La carretera sigue. Las pistas muestran combustible, botín y terreno.")
val Copy.takeIt: String get() = t("TAKE IT", "NEHMEN", "PRENDRE", "PRENDI", "BRAŤ", "COGER")
val Copy.paintRespray: String get() = t("PAINT / RESPRAY", "LACK / NEULACKIEREN", "PEINTURE / REPEINDRE", "VERNICE / RIVERNICIA", "LAK / PRELAKOVAŤ", "PINTURA / REPINAR")
fun Copy.nowPaint(name: String) = t("Now $name", "Jetzt $name", "Maintenant $name", "Ora $name", "Teraz $name", "Ahora $name")
fun Copy.alreadyOnCarName(name: String) = t("$name is already on the car", "$name ist schon am Auto", "$name est déjà sur la voiture", "$name è già sull'auto", "$name už je na aute", "$name ya está en el coche")
fun Copy.resprayWhole(cost: Int, have: Int) = t("Respray the whole car · $cost scrap · you have $have", "Ganzes Auto lackieren · $cost Schrott · du hast $have", "Repeindre toute la voiture · $cost ferraille · tu as $have", "Rivernicia tutta l'auto · $cost rottame · ne hai $have", "Prelakovať celé auto · $cost šrotu · máš $have", "Repintar todo el coche · $cost chatarra · tienes $have")
fun Copy.needScrapWatchAdPaint(cost: Int, have: Int) = t("Need $cost scrap — you have $have. Watch an ad for one free paint.", "Brauche $cost Schrott — du hast $have. Werbung für einen Gratis-Lack.", "Il faut $cost ferraille — tu as $have. Une pub pour une peinture gratuite.", "Servono $cost rottame — ne hai $have. Un ads per una vernice gratis.", "Treba $cost šrotu — máš $have. Reklama za jeden voľný lak.", "Necesitas $cost de chatarra: tienes $have. Un anuncio por una pintura gratis.")
fun Copy.remainingLitres(n: String) = t("remaining $n L", "noch $n L", "reste $n L", "restano $n L", "ostáva $n L", "quedan $n L")
val Copy.emptyLower: String get() = t("empty", "leer", "vide", "vuoto", "prázdne", "vacío")
fun Copy.pctFlat(pct: Int) = t("$pct % · FLAT", "$pct % · PLATT", "$pct % · À PLAT", "$pct % · FORATA", "$pct % · PRÁZDNA", "$pct % · DESINCHADO")
fun Copy.pctRim(pct: Int) = t("$pct % · RIM", "$pct % · FELGE", "$pct % · JANTE", "$pct % · CERCHIO", "$pct % · RÁFIK", "$pct % · LLANTA")
fun Copy.massLine(kg: Int, front: Int, rear: Int) = t("Mass $kg kg · front $front% / rear $rear%", "Masse $kg kg · vorne $front% / hinten $rear%", "Masse $kg kg · avant $front% / arrière $rear%", "Massa $kg kg · ant. $front% / post. $rear%", "Hmotnosť $kg kg · predok $front% / zadok $rear%", "Masa $kg kg · delantero $front% / trasero $rear%")
fun Copy.tankMix(petrol: Int, diesel: Int, wrong: Boolean): String {
    val base = t(
        "Tank mix: $petrol% petrol · $diesel% diesel",
        "Tankmix: $petrol% Benzin · $diesel% Diesel",
        "Mélange : $petrol% essence · $diesel% diesel",
        "Miscela: $petrol% benzina · $diesel% diesel",
        "Zmes v nádrži: $petrol% benzín · $diesel% nafta",
        "Mezcla: $petrol% gasolina · $diesel% diésel"
    )
    return if (wrong) "$base — ${t("WRONG FOR THIS ENGINE", "FALSCH FÜR DIESEN MOTOR", "MAUVAIS POUR CE MOTEUR", "SBAGLIATO PER QUESTO MOTORE", "Zlé PRE TENTO MOTOR", "INCORRECTO PARA ESTE MOTOR")}" else base
}
val Copy.missingChip: String get() = t("missing", "fehlt", "manque", "manca", "chýba", "falta")
fun Copy.healthFlat(pct: Int) = t("$pct % · FLAT", "$pct % · PLATT", "$pct % · À PLAT", "$pct % · FORATA", "$pct % · PRÁZDNA", "$pct % · DESINCHADO")
fun Copy.healthRim(pct: Int) = t("$pct % · RIM", "$pct % · FELGE", "$pct % · JANTE", "$pct % · CERCHIO", "$pct % · RÁFIK", "$pct % · LLANTA")
val Copy.drainTheFuelTank: String get() = t("the fuel tank", "den Tank", "le réservoir", "il serbatoio", "palivovú nádrž", "el depósito")
val Copy.drainTheEngine: String get() = t("the engine", "den Motor", "le moteur", "il motore", "motor", "el motor")
val Copy.drainTheRadiator: String get() = t("the radiator", "den Kühler", "le radiateur", "il radiatore", "chladič", "el radiador")
val Copy.drainTheBrakes: String get() = t("the brake system", "das Bremssystem", "le circuit de frein", "l'impianto frenante", "brzdový systém", "el sistema de frenos")
val Copy.relaySignalOnline: String get() = t("The signal is online. This station is already part of the restored network.", "Das Signal ist online. Diese Station gehört schon zum Netz.", "Le signal est en ligne. Cette station fait déjà partie du réseau.", "Il segnale è online. Questa stazione è già nella rete.", "Signál je online. Táto stanica už je v obnovenej sieti.", "La señal está en línea. Esta estación ya forma parte de la red.")
val Copy.restorePreviousBody: String get() = t("Restore the previous relay before activating this checkpoint.", "Stelle zuerst das vorherige Relais wieder her.", "Restaure le relais précédent avant ce point.", "Ripristina il relè precedente prima di questo checkpoint.", "Pred týmto bodom obnov predchádzajúce relé.", "Restaura el relé anterior antes de este punto.")
fun Copy.restoreRelayCost(cost: Int, scrap: Int, modules: Int) = t(
    "Restore this relay with $cost scrap and 1 relay module. You have $scrap scrap and $modules module(s).",
    "Stelle dieses Relais mit $cost Schrott und 1 Modul wieder her. Du hast $scrap Schrott und $modules Modul(e).",
    "Restaure ce relais avec $cost ferraille et 1 module. Tu as $scrap ferraille et $modules module(s).",
    "Ripristina questo relè con $cost rottame e 1 modulo. Hai $scrap rottame e $modules modulo/i.",
    "Obnov toto relé za $cost šrotu a 1 modul. Máš $scrap šrotu a $modules modul(ov).",
    "Restaura este relé con $cost de chatarra y 1 módulo. Tienes $scrap de chatarra y $modules módulo(s)."
)

fun Copy.languageEnglishName(lang: sk.kubis.endlessdrive.domain.model.AppLanguage) = when (lang) {
    sk.kubis.endlessdrive.domain.model.AppLanguage.ENGLISH -> t("English", "Englisch", "Anglais", "Inglese", "Angličtina", "Inglés")
    sk.kubis.endlessdrive.domain.model.AppLanguage.GERMAN -> t("German", "Deutsch", "Allemand", "Tedesco", "Nemčina", "Alemán")
    sk.kubis.endlessdrive.domain.model.AppLanguage.FRENCH -> t("French", "Französisch", "Français", "Francese", "Francúzština", "Francés")
    sk.kubis.endlessdrive.domain.model.AppLanguage.ITALIAN -> t("Italian", "Italienisch", "Italien", "Italiano", "Taliančina", "Italiano")
    sk.kubis.endlessdrive.domain.model.AppLanguage.SLOVAK -> t("Slovak", "Slowakisch", "Slovaque", "Slovacco", "Slovenčina", "Eslovaco")
    sk.kubis.endlessdrive.domain.model.AppLanguage.SPANISH -> t("Spanish", "Spanisch", "Espagnol", "Spagnolo", "Španielčina", "Español")
}

val Copy.startMission: String get() = t("START MISSION", "MISSION STARTEN", "DÉMARRER LA MISSION", "AVVIA MISSIONE", "ZAČAŤ MISIU", "INICIAR MISIÓN")
fun Copy.sideMissionTitle(kind: sk.kubis.endlessdrive.game.SideMissionKind): String = when (kind) {
    sk.kubis.endlessdrive.game.SideMissionKind.DELIVERY -> t("DELIVERY", "LIEFERUNG", "LIVRAISON", "CONSEGNA", "DORUČENIE", "ENTREGA")
    sk.kubis.endlessdrive.game.SideMissionKind.EXPRESS_DELIVERY -> t("EXPRESS DELIVERY", "EXPRESSLIEFERUNG", "LIVRAISON EXPRESS", "CONSEGNA ESPRESSA", "EXPRESNÉ DORUČENIE", "ENTREGA EXPRÉS")
    sk.kubis.endlessdrive.game.SideMissionKind.FRAGILE_DELIVERY -> t("FRAGILE CARGO", "ZERBRECHLICHE FRACHT", "CARGAISON FRAGILE", "CARICO FRAGILE", "KREHKÝ NÁKLAD", "CARGA FRÁGIL")
    sk.kubis.endlessdrive.game.SideMissionKind.SALVAGE -> t("SALVAGE RUN", "BERGUNG", "RÉCUPÉRATION", "RECUPERO", "ZBER ŠROTU", "RECUPERACIÓN")
}
fun Copy.deliveryMissionOffer(destination: String, meters: Int, reward: Int) = t(
    "Load a package and deliver it to $destination, exactly $meters m ahead. Reward: $reward scrap.",
    "Lade ein Paket und bringe es zu $destination, genau $meters m voraus. Belohnung: $reward Schrott.",
    "Charge un colis et livre-le à $destination, exactement $meters m plus loin. Récompense : $reward ferraille.",
    "Carica un pacco e consegnalo a $destination, esattamente $meters m più avanti. Ricompensa: $reward rottami.",
    "Nalož balík a doruč ho do: $destination, presne $meters m pred tebou. Odmena: $reward šrotu.",
    "Carga un paquete y entrégalo en $destination, exactamente a $meters m. Recompensa: $reward de chatarra."
)
fun Copy.salvageMissionOffer(count: Int, reward: Int) = t(
    "Recover $count pieces of loot from roadside stops. Reward: $reward scrap.",
    "Berge $count Beutestücke an Straßenstopps. Belohnung: $reward Schrott.",
    "Récupère $count objets aux arrêts routiers. Récompense : $reward ferraille.",
    "Recupera $count oggetti dalle soste. Ricompensa: $reward rottami.",
    "Získaj $count kusy koristi zo zastávok pri ceste. Odmena: $reward šrotu.",
    "Recupera $count objetos en paradas de carretera. Recompensa: $reward de chatarra."
)
fun Copy.expressMissionOffer(destination: String, meters: Int, seconds: Int, reward: Int, bonus: Int) = t(
    "Take the package to $destination, $meters m ahead. Deliver within $seconds s for $reward + $bonus bonus scrap.",
    "Bringe das Paket $meters m weit zu $destination. In $seconds s: $reward + $bonus Bonus-Schrott.",
    "Livre le colis à $destination, à $meters m. En moins de $seconds s : $reward + $bonus ferraille bonus.",
    "Porta il pacco a $destination, a $meters m. Entro $seconds s: $reward + $bonus rottami bonus.",
    "Doruč balík do: $destination, vzdialeného $meters m. Do $seconds s získaš $reward + $bonus bonusového šrotu.",
    "Lleva el paquete a $destination, a $meters m. En $seconds s: $reward + $bonus de chatarra extra."
)
fun Copy.fragileMissionOffer(destination: String, meters: Int, reward: Int) = t(
    "Deliver a fragile package to $destination, $meters m ahead. Hard landings and bumps reduce the $reward scrap reward.",
    "Liefere ein zerbrechliches Paket $meters m weit zu $destination. Harte Landungen und Unebenheiten mindern die $reward Schrott Belohnung.",
    "Livre un colis fragile à $destination, à $meters m. Les chocs réduisent la récompense de $reward ferraille.",
    "Consegna un pacco fragile a $destination, a $meters m. Urti e salti riducono i $reward rottami.",
    "Doruč krehký balík do: $destination, vzdialeného $meters m. Tvrdé dopady a hrbole znižujú odmenu $reward šrotu.",
    "Entrega un paquete frágil en $destination, a $meters m. Los golpes reducen la recompensa de $reward chatarra."
)
val Copy.pickUpMissionPackage: String get() = t(
    "Pick up the mission package from this building.", "Nimm das Missionspaket in diesem Gebäude auf.",
    "Récupère le colis de mission dans ce bâtiment.", "Prendi il pacco della missione in questo edificio.",
    "Zober balík pre misiu z tejto budovy.", "Recoge el paquete de misión de este edificio."
)
val Copy.packageLoaded: String get() = t(
    "Package loaded · head to the destination", "Paket geladen · fahre zum Ziel",
    "Colis chargé · va à destination", "Pacco caricato · vai a destinazione",
    "Balík naložený · vyraz do cieľa", "Paquete cargado · ve al destino"
)
fun Copy.deliveryMissionProgress(destination: String, meters: Int, reward: Int) = t(
    "$destination · $meters m · +$reward scrap", "$destination · $meters m · +$reward Schrott",
    "$destination · $meters m · +$reward ferraille", "$destination · $meters m · +$reward rottami",
    "$destination · $meters m · +$reward šrotu", "$destination · $meters m · +$reward chatarra"
)
fun Copy.expressMissionProgress(destination: String, meters: Int, seconds: Int, reward: Int) = t(
    "$destination · $meters m · ${seconds}s · up to +$reward scrap", "$destination · $meters m · ${seconds}s · bis +$reward Schrott",
    "$destination · $meters m · ${seconds}s · jusqu'à +$reward ferraille", "$destination · $meters m · ${seconds}s · fino a +$reward rottami",
    "$destination · $meters m · ${seconds}s · až +$reward šrotu", "$destination · $meters m · ${seconds}s · hasta +$reward chatarra"
)
fun Copy.fragileMissionProgress(destination: String, meters: Int, integrity: Int, reward: Int) = t(
    "$destination · $meters m · cargo $integrity% · up to +$reward scrap", "$destination · $meters m · Fracht $integrity% · bis +$reward Schrott",
    "$destination · $meters m · cargaison $integrity% · jusqu'à +$reward ferraille", "$destination · $meters m · carico $integrity% · fino a +$reward rottami",
    "$destination · $meters m · náklad $integrity% · až +$reward šrotu", "$destination · $meters m · carga $integrity% · hasta +$reward chatarra"
)
fun Copy.salvageMissionProgress(done: Int, total: Int, reward: Int) = t(
    "$done/$total loot recovered · +$reward scrap", "$done/$total Beute geborgen · +$reward Schrott",
    "$done/$total objets récupérés · +$reward ferraille", "$done/$total oggetti recuperati · +$reward rottami",
    "$done/$total koristi získaných · +$reward šrotu", "$done/$total objetos recuperados · +$reward chatarra"
)
fun Copy.sideMissionStarted(kind: sk.kubis.endlessdrive.game.SideMissionKind) = t(
    if (kind != sk.kubis.endlessdrive.game.SideMissionKind.SALVAGE) "Mission accepted · pick up the package" else "Mission started · collect roadside loot",
    if (kind != sk.kubis.endlessdrive.game.SideMissionKind.SALVAGE) "Mission angenommen · Paket aufnehmen" else "Mission gestartet · Beute sammeln",
    if (kind != sk.kubis.endlessdrive.game.SideMissionKind.SALVAGE) "Mission acceptée · récupère le colis" else "Mission démarrée · récupère du butin",
    if (kind != sk.kubis.endlessdrive.game.SideMissionKind.SALVAGE) "Missione accettata · prendi il pacco" else "Missione avviata · raccogli bottino",
    if (kind != sk.kubis.endlessdrive.game.SideMissionKind.SALVAGE) "Misia prijatá · zober balík" else "Misia začala · zbieraj korisť pri ceste",
    if (kind != sk.kubis.endlessdrive.game.SideMissionKind.SALVAGE) "Misión aceptada · recoge el paquete" else "Misión iniciada · recoge objetos"
)
fun Copy.sideMissionCompleted(reward: Int) = t(
    "Mission complete · +$reward scrap", "Mission erfüllt · +$reward Schrott",
    "Mission terminée · +$reward ferraille", "Missione completata · +$reward rottami",
    "Misia splnená · +$reward šrotu", "Misión completada · +$reward chatarra"
)
val Copy.missionCompleteTitle: String get() = t("MISSION COMPLETE", "MISSION ERFÜLLT", "MISSION TERMINÉE", "MISSIONE COMPLETATA", "MISIA SPLNENÁ", "MISIÓN COMPLETADA")
fun Copy.missionRewardBody(reward: Int, bonus: Int) = t(
    "You earned $reward scrap${if (bonus > 0) " including a $bonus bonus" else ""}.",
    "Du hast $reward Schrott${if (bonus > 0) " inklusive $bonus Bonus" else ""} erhalten.",
    "Tu as gagné $reward ferraille${if (bonus > 0) ", dont $bonus bonus" else ""}.",
    "Hai guadagnato $reward rottami${if (bonus > 0) ", incluso $bonus bonus" else ""}.",
    "Získal si $reward šrotu${if (bonus > 0) ", vrátane bonusu $bonus" else ""}.",
    "Ganaste $reward chatarra${if (bonus > 0) ", incluido un bono de $bonus" else ""}."
)
fun Copy.doubleMissionReward(reward: Int) = t(
    "DOUBLE TO ${reward * 2} · WATCH AD", "AUF ${reward * 2} VERDOPPELN · WERBUNG",
    "DOUBLER À ${reward * 2} · VOIR PUB", "RADDOPPIA A ${reward * 2} · GUARDA ADS",
    "ZDVOJNÁSOBIŤ NA ${reward * 2} · REKLAMA", "DUPLICAR A ${reward * 2} · VER ANUNCIO"
)
val Copy.collectMissionReward: String get() = t("COLLECT", "EINSAMMELN", "RÉCUPÉRER", "RITIRA", "PREVZIAŤ", "COBRAR")
fun Copy.fragileCargoDamaged(integrity: Int) = t(
    "Fragile cargo damaged · $integrity%", "Zerbrechliche Fracht beschädigt · $integrity%",
    "Cargaison fragile endommagée · $integrity%", "Carico fragile danneggiato · $integrity%",
    "Krehký náklad sa poškodil · $integrity%", "Carga frágil dañada · $integrity%"
)

val Copy.noCatalogUpgrades: String get() = t(
    "No catalog upgrades for fitted parts.",
    "Keine Katalog-Upgrades für eingebaute Teile.",
    "Pas d'améliorations catalogue pour les pièces montées.",
    "Nessun upgrade catalogo per i pezzi montati.",
    "Žiadne katalógové vylepšenia pre namontované diely.",
    "No hay mejoras de catálogo para piezas montadas."
)
fun Copy.noFurtherUpgrade(name: String) = t(
    "$name — no further upgrade",
    "$name — kein weiteres Upgrade",
    "$name — pas d'amélioration suivante",
    "$name — nessun altro upgrade",
    "$name — žiadne ďalšie vylepšenie",
    "$name — sin más mejora"
)
fun Copy.upgradeArrow(from: String, to: String) = "$from → $to"
fun Copy.scrapYouHave(cost: Int, have: Int) = t(
    "$cost scrap · you have $have",
    "$cost Schrott · du hast $have",
    "$cost ferraille · tu as $have",
    "$cost rottame · ne hai $have",
    "$cost šrotu · máš $have",
    "$cost chatarra · tienes $have"
)
fun Copy.upgradeMinus(cost: Int) = t(
    "UPGRADE −$cost",
    "UPGRADE −$cost",
    "AMÉLIORER −$cost",
    "UPGRADE −$cost",
    "VYLEPŠIŤ −$cost",
    "MEJORAR −$cost"
)
fun Copy.upgradedFromTo(slot: String, from: String, to: String, cost: Int) = t(
    "Upgraded $slot: $from → $to (−$cost scrap)",
    "$slot aufgerüstet: $from → $to (−$cost Schrott)",
    "$slot amélioré : $from → $to (−$cost ferraille)",
    "$slot aggiornato: $from → $to (−$cost rottame)",
    "Vylepšené $slot: $from → $to (−$cost šrotu)",
    "Mejorado $slot: $from → $to (−$cost chatarra)"
)
val Copy.pickAPartInZone: String get() = t(
    "Pick a part in this zone.",
    "Wähle ein Teil in dieser Zone.",
    "Choisis une pièce dans cette zone.",
    "Scegli un pezzo in questa zona.",
    "Vyber diel v tejto zóne.",
    "Elige una pieza en esta zona."
)
val Copy.emptyFindAndFit: String get() = t(
    "Empty — find one and fit it",
    "Leer — finde eines und baue es ein",
    "Vide — trouve-en un et monte-le",
    "Vuoto — trovanelo e montalo",
    "Prázdne — nájdi jeden a namontuj",
    "Vacío — encuentra uno y móntalo"
)
fun Copy.drainLitres(amount: String) = t(
    "DRAIN $amount L",
    "$amount L ABLASSEN",
    "VIDANGER $amount L",
    "SCARICA $amount L",
    "VYPUSTIŤ $amount L",
    "VACIAR $amount L"
)
fun Copy.drainAllLitres(amount: String) = t(
    "DRAIN ALL $amount L",
    "ALLES ABLASSEN $amount L",
    "TOUT VIDANGER $amount L",
    "SCARICA TUTTO $amount L",
    "VYPUSTIŤ VŠETKO $amount L",
    "VACIAR TODO $amount L"
)
fun Copy.discardedIn(amount: String, where: String) = t(
    "$amount L in $where — discarded.",
    "$amount L in $where — verworfen.",
    "$amount L dans $where — jeté.",
    "$amount L in $where — scartato.",
    "$amount L v $where — vyhodené.",
    "$amount L en $where — descartado."
)
fun Copy.carCompareNoneBetter() = t(
    "car: none → this is better",
    "Auto: keines → das ist besser",
    "voiture : rien → c'est mieux",
    "auto: niente → è meglio",
    "auto: nič → toto je lepšie",
    "coche: nada → esto es mejor"
)
fun Copy.carCompareSlots(from: Int, to: Int) = t(
    "car +$from slots → +$to slots",
    "Auto +$from Plätze → +$to Plätze",
    "voiture +$from places → +$to places",
    "auto +$from slot → +$to slot",
    "auto +$from slotov → +$to slotov",
    "coche +$from huecos → +$to huecos"
)
fun Copy.carCompareSmallerStorage(from: Int) = t(
    "car +$from slots → smaller storage",
    "Auto +$from Plätze → weniger Stauraum",
    "voiture +$from places → moins de place",
    "auto +$from slot → meno spazio",
    "auto +$from slotov → menší priestor",
    "coche +$from huecos → menos espacio"
)
val Copy.permanentStorageUpgrade: String get() = t(
    "permanent storage upgrade",
    "dauerhaftes Stauraum-Upgrade",
    "amélioration de stockage permanente",
    "upgrade di stiva permanente",
    "trvalé vylepšenie úložiska",
    "mejora permanente de almacenamiento"
)
fun Copy.carCompareBetter(pct: Int) = t(
    "car $pct % → this is better",
    "Auto $pct % → das ist besser",
    "voiture $pct % → c'est mieux",
    "auto $pct % → è meglio",
    "auto $pct % → toto je lepšie",
    "coche $pct % → esto es mejor"
)
fun Copy.carCompareWorse(pct: Int) = t(
    "car $pct % → this is worse",
    "Auto $pct % → das ist schlechter",
    "voiture $pct % → c'est pire",
    "auto $pct % → è peggio",
    "auto $pct % → toto je horšie",
    "coche $pct % → esto es peor"
)
fun Copy.carCompareSame(pct: Int) = t(
    "car $pct % → about the same",
    "Auto $pct % → ungefähr gleich",
    "voiture $pct % → à peu près pareil",
    "auto $pct % → più o meno uguale",
    "auto $pct % → približne rovnaké",
    "coche $pct % → más o menos igual"
)
fun Copy.scrapPlus(n: Int) = t(
    "SCRAP +$n",
    "SCHROTT +$n",
    "FERRAILLE +$n",
    "ROTTAME +$n",
    "ŠROT +$n",
    "CHATARRA +$n"
)
fun Copy.repaintedPrefix(name: String) = t(
    "Repainted the car $name",
    "Auto $name lackiert",
    "Voiture repeinte $name",
    "Auto riverniciata $name",
    "Auto prelakované $name",
    "Coche repintado $name"
)
