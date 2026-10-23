package sk.kubis.endlessdrive.core

/**
 * Centrálne konštanty. Svetové jednotky = metre / sekundy.
 * 3D driving, suspension and mobile rendering settings.
 */
object GameConfig {
    const val FIXED_TIME_STEP = 1f / 60f
    const val MAX_FRAME_TIME = 0.25f
    /** 0.25 s frame potrebuje 15 krokov po 1/60 s; čas sa pri nízkom FPS nesmie stratiť. */
    const val MAX_FIXED_STEPS_PER_FRAME = 15
    const val TARGET_FPS = 60
    const val TARGET_FRAME_NANOS = 1_000_000_000L / TARGET_FPS
    const val FRAME_LOCK_TOLERANCE_NANOS = 2_000_000L

    // --- Segmenty / križovatky (dlhé cesty = veľké nádrže majú zmysel) ---
    /** Regióny sú dosť krátke, aby sa krajina striedala každých pár kilometrov. */
    const val SEGMENT_LENGTH_MIN = 2800f
    const val SEGMENT_LENGTH_MAX = 4600f
    /** Dlhší prvý región dá novému hráčovi priestor naučiť sa jazdu pred prvou voľbou vetvy. */
    const val TUTORIAL_SEGMENT_LENGTH = 1800f
    /** Výber vetvy aj prvý pixel fade-u sa objavia 300 m pred križovatkou. */
    const val JUNCTION_APPROACH = 300f
    /** Plynulé prelínanie dvoch susedných regiónov po zobrazení voľby. */
    const val BIOME_TRANSITION_MIN = JUNCTION_APPROACH
    const val BIOME_TRANSITION_MAX = JUNCTION_APPROACH
    /** Nové kulisy sa začnú presadzovať až za križovatkou. */
    const val JUNCTION_SCENERY_TRANSITION_M = 120f
    /** Prvá budova nie hneď za štartom. */
    const val BUILDING_MIN_GAP_FROM_START = 200f
    /** Väčší odstup – viac budov celkovo cez dlhšie segmenty, nie hustá osada. */
    const val BUILDING_MIN_SPACING = 220f
    const val JUNCTION_ZONE = 22f
    /**
     * Parkovanie pri budove. Po oddialení kamery musí SEARCH naskočiť,
     * keď je budova vedľa auta na obrazovke – nie až keď auto stojí
     * presne na jej stope.
     */
    const val BUILDING_INTERACT_RANGE = 14f
    /**
     * Ťahanie k existujúcemu servisu po reklame za ráfik (m). Ďalej sa
     * neskáče – vedľa auta vznikne nová dielňa, aby sa nepreskočil kopec.
     */
    const val TIRE_RESCUE_TOW_RANGE_M = 600f
    /** Nová dielňa pred autom (m); musí ostať v [BUILDING_INTERACT_RANGE]. */
    const val TIRE_RESCUE_SPAWN_AHEAD_M = 5.5f
    /**
     * Koľko sekúnd bez pohybu pri plyne, kým jazda skončí na asiste.
     * Krátky preklz do kopca nesmie hneď vyhodiť GAME_OVER.
     */
    const val TIRE_RESCUE_STUCK_S = 6f
    /** Hľadanie roviny pre ROAD ASSIST – dopredu, nie skok celého regiónu. */
    const val TIRE_RESCUE_FLAT_AHEAD_M = 220f
    /** Späť len kúsok – úpätie kopca, nie návrat celej jazdy. */
    const val TIRE_RESCUE_FLAT_BEHIND_M = 36f
    /** |sklon| (dy/dx), pod ktorým sa dá rozbehnúť. */
    const val TIRE_RESCUE_FLAT_SLOPE = 0.085f
    /** Existujúci servis sa ťahá len zo sklonu pod týmto. */
    const val TIRE_RESCUE_TOW_SLOPE = 0.12f
    /** Po toľkých sekundách točenia na mieste sa dá asist volať ručne zo STOPPED. */
    const val TIRE_RESCUE_LATCH_S = 0.8f
    /** Preklz, nad ktorým sa točenie berie ako státie, nie krátky šmyk. */
    const val TIRE_RESCUE_SPIN_SLIP = 0.48f
    /** |rýchlosť| (m/s), pod ktorou sa kolesá točia na mieste. */
    const val TIRE_RESCUE_SPIN_MAX_SPEED = 1.25f
    /** Koľko metrov sa smie posunúť počas točenia, kým to ešte nie je únik. */
    const val TIRE_RESCUE_SPIN_MAX_MOVE_M = 2.8f
    /**
     * Núdzové rewarded reklamy za jednu jazdu: game-over záchrana a ručný
     * ROAD ASSIST. Lak ani dvojnásobný šrot sa sem nepočítajú.
     */
    const val HELP_ADS_MAX = 5

    // --- Jazda / fyzika auta (Hill Climb arcade) ---
    const val MAX_SPEED = 42f
    /**
     * Soft referencia pre UI/testy. Samotná fyzika už cúvanie netvrdí oreže –
     * limituje ho [REVERSE_ACCEL] a odpor ako vpred.
     */
    const val REVERSE_MAX_SPEED = 22f
    /**
     * Ťah cúvania (m/s²). Musí utiahnuť krátky kopec, ale ostáva pod [ACCEL],
     * aby na rovine nebolo cúvanie rýchlejšie ako vpred.
     */
    const val REVERSE_ACCEL = 13.2f
    /** Cúvanie berie grip z oboch náprav – RWD inak na hrbolci zhadzuje zadok. */
    const val REVERSE_DRIVE_GRIP = 1.0f
    /**
     * Späť od najďalej dosiahnutého X (HillRush REVERSE_LIMIT).
     *
     * Musí stačiť na poriadny rozbeh: so slabým motorom a zodratými gumami sa
     * prudký kopec z miesta nevyjde a jediná odpoveď je nabrať rýchlosť
     * z rovinky za sebou. Pri 40 m na to nebolo dosť miesta.
     */
    const val REVERSE_LIMIT = 110f
    /** Max ťah motora (m/s²) pri ~70 hp. */
    /**
     * Ťah motora pri powerFactor 1 (m/s²). Musí byť poriadne nad gravitáciou
     * (AIR_GRAVITY = 22), inak auto nemá do kopca čím tlačiť – s 9.5 sa
     * na rovine dostalo na 38 km/h a 25 % stúpanie ho zastavilo.
     */
    const val ACCEL = 14f
    const val BRAKE = 34f
    /**
     * Hydraulický strop bŕzd voči [BRAKE]·hmotnosť. Nižší násobiteľ
     * nezvládol držať auto na strmšom svahu.
     */
    const val BRAKE_HARDWARE_MUL = 0.88f
    /** Valivý odpor (m/s²) – veľká fixná brzda pri nízkej rýchlosti. */
    // Auto si má po pustení plynu zachovať rozbehnutú rýchlosť. Brzdenie
    // odporom ostáva citeľné, ale krátky nájazd už nezmizne pred kopcom.
    const val COAST_DRAG = 0.82f
    const val AERO_DRAG = 0.011f
    const val STOP_SPEED = 1.0f
    /**
     * Spätný chod sa zapne až keď auto skoro stojí. Pri [STOP_SPEED]·1.2
     * brzda na svahu skočila do cúvania a auto sa šmýkalo celým kopcom.
     */
    const val REVERSE_ENGAGE_SPEED = 0.35f
    /** Stojíme na brzde aspoň toľko, kým sa zapne cúvanie (svah inak ujde). */
    const val REVERSE_ARM_DELAY = 0.45f
    /** Voľná výška stredu nad vozovkou pri stlačenom pružení. */
    const val CAR_RIDE_HEIGHT = 0.42f
    /** Vertikálna gravitácia (m/s²). */
    const val AIR_GRAVITY = 22f
    /** Normovaná hmotnosť – sily sú v „m/s² * mass“. */
    const val CAR_MASS = 1f
    /** Holá samonosná karoséria; namontované diely a náklad sa pripočítavajú. */
    const val VEHICLE_BASE_MASS_KG = 720f
    /** Hmotnosť, pri ktorej ostáva pôvodné fyzikálne škálovanie 1.0. */
    const val VEHICLE_REFERENCE_MASS_KG = 1000f
    /** Nad referenčnou hmotnosťou sa rozdiel zosilní, aby bol plný kufor citeľný. */
    const val VEHICLE_WEIGHT_GAMEPLAY_GAIN = 1.55f
    /** Dodatočná spotreba paliva za jednotku hmotnosti nad referenčné auto. */
    const val VEHICLE_WEIGHT_FUEL_GAIN = 0.70f
    /** Dodatočné opotrebenie pneumatík, bŕzd a pruženia pod váhou. */
    const val VEHICLE_WEIGHT_WEAR_GAIN = 0.95f
    /** Hmotnostné rozpätie, cez ktoré vizuálne sadnutie auta prejde 0 → 1. */
    const val VEHICLE_VISUAL_LOAD_RANGE_KG = 260f
    /** Holá karoséria je mierne ťažšia vpredu (podiel na zadnej náprave). */
    const val VEHICLE_BASE_REAR_BIAS = 0.44f
    /** Batoh leží pri predných sedadlách, kufor/nosič za zadnou nápravou. */
    const val PACK_LOAD_REAR_BIAS = 0.38f
    const val BOOT_LOAD_REAR_BIAS = 0.88f
    const val GARAGE_ENGINE_POWER_PER_LEVEL = 0.06f
    const val GARAGE_SPRING_PER_LEVEL = 0.10f
    const val GARAGE_DAMPER_PER_LEVEL = 0.08f
    const val GARAGE_BRAKE_PER_LEVEL = 0.10f
    /**
     * Cruise control žerie viac než držanie plynu. Garážová efektivita
     * znižuje tento násobiteľ o [GARAGE_CRUISE_FUEL_REDUCTION_PER_LEVEL]
     * za úroveň (strop 1.0 = ako ručný plyn).
     */
    const val CRUISE_FUEL_MULT = 1.5f
    const val GARAGE_CRUISE_FUEL_REDUCTION_PER_LEVEL = 0.15f
    /** Zotrvačnosť náklonu. */
    const val PITCH_INERTIA = 2.4f
    /** Tvrdosť pruženia (na 1 m stlačenia) – mäkšie = viditeľný bob. */
    const val SUSP_SPRING = 52f
    /**
     * Aj ojazdené pruženie (~20–30 % zdravia) musí obe nápravy udržať na
     * zemi. Mäkšie by predný motor zaboril nos a zadok by visel vo vzduchu.
     */
    const val SUSP_WORN_SPRING_FLOOR = 0.58f
    /** Front springs support the engine; softer rear springs reduce empty-car rake. */
    const val FRONT_SPRING_MUL = 1.10f
    const val REAR_SPRING_MUL = 0.90f
    /** Tlmenie pruženia. */
    const val SUSP_DAMPER = 7.5f
    /** Ojazdené tlmiče smú byť húpavé; podlaha je nižšia než pri pružinách. */
    const val SUSP_WORN_DAMP_FLOOR = 0.40f
    /** Bez namontovaného pruženia – horšie ako ojazdený kus, nie tajný upgrade. */
    const val SUSP_MISSING_MUL = 0.16f
    const val SUSP_MISSING_SPRING = 0.34f
    const val SUSP_MISSING_DAMP = 0.12f
    /**
     * Extra tlmenie nad stock (susMul 0.9). Šport/lift tak neskáče karosériou
     * a zároveň základné pruženie ostane mäkké.
     */
    const val SUSP_QUALITY_DAMP_BONUS = 0.85f
    /**
     * Krútiaci moment z rozdielu náprav, keď drží len jedna. Bez toho skok
     * strhne nos na jednu stranu, hneď ako predok opustí rampu.
     */
    const val ONE_WHEEL_PITCH_TORQUE = 0.52f
    /** Vo vzduchu náklon rýchlo dohasína – inak sa dopad zíde s celým impulzom. */
    const val AIR_PITCH_DAMPING = 1.85f
    /** Pri dopade zhodíme časť pitch rate, aby jedna náprava nepreklopila auto. */
    const val LANDING_PITCH_BLEED = 0.62f
    /** High-speed blow-off tlmiča: ostrý hrebeň nesmie vystreliť karosériu. */
    const val SUSP_DAMPER_VELOCITY_LIMIT = 5.5f
    /** Max sila jednej nápravy ako násobok celej statickej váhy auta. */
    const val SUSP_AXLE_FORCE_LIMIT = 2.2f
    /** Tvrdý kontakt nesmie premeniť sklon kopca na katapult karosérie. */
    const val ROAD_CONTACT_MAX_UP_SPEED = 3.6f
    /** Max zmena vertikálnej rýchlosti z jedného korekčného kontaktu pri 60 Hz. */
    const val ROAD_CONTACT_MAX_VY_CORRECTION = 0.40f
    /** Minimálna svetlá výška podlahy medzi nápravami na ostrých hrebeňoch. */
    const val ROAD_CHASSIS_CLEARANCE = 0.12f
    /**
     * Jemný aerodynamický prítlak (m/s² na druhú mocninu rýchlosti).
     * Pri 130 km/h pridá približne 4.2 m/s², takže malé vlny auto neodhodia,
     * ale veľký hrebeň stále dovolí normálny skok.
     */
    const val HIGH_SPEED_DOWNFORCE_COEFF = 0.0032f
    const val HIGH_SPEED_DOWNFORCE_MAX = 6.0f
    /** Rozsah rozdielu sklonov medzi nápravami, ktorý označuje krátky ostrý crest. */
    const val SHARP_CREST_SLOPE_DELTA_START = 0.04f
    const val SHARP_CREST_SLOPE_DELTA_FULL = 0.18f
    /** Koľko pružinovej špičky sa na ostrom creste absorbuje v pneumatike/tlmiči. */
    const val SHARP_CREST_FORCE_RELIEF = 0.95f
    /** Max stlačenie pruženia (m). */
    const val SUSP_MAX_TRAVEL = 0.55f
    /** Vizuálne zosilnenie zdvihu pruženia (render). Nad 1 koleso ľahko prelezie blatník. */
    const val SUSP_VISUAL_GAIN = 1.0f
    /** Maximálne statické sadnutie karosérie pod nákladom voči polomeru kolesa. */
    const val VEHICLE_VISUAL_SINK_WHEEL_FRAC = 0.22f
    /** Ako silno pitch sleduje sklon (nižšie = viac voľného náklonu z váhy). */
    const val PITCH_SLOPE_TRACK = 4.4f
    /**
     * Extra vracajúci moment, keď obe nápravy sedia na ceste a náklon
     * ušiel od sklonu. Bez toho predný motor z kopca zaborí nos, kým
     * hráč nestlačí brzdu (tesný clamp).
     */
    const val PITCH_SLOPE_RESTORE = 18f
    /** Dodatočné tlmenie náklonu pri diaľničnej rýchlosti. */
    const val HIGH_SPEED_PITCH_DAMPING = 1.6f
    /** Náklon z plynu na zemi (squat). */
    const val GROUND_PITCH_TORQUE = 3.8f
    /**
     * Nose-dive pri brzde. Váhový prenos v pružení to urobí sám;
     * extra moment má byť jemný, inak zadok odľahčí.
     */
    const val GROUND_BRAKE_DIVE_TORQUE = 0.55f
    /**
     * Cúvanie tlačí zadok, nie predok. Kladná hodnota = nose-up, aby sa
     * kapota pri cúvaní nezaborila do cesty.
     */
    const val GROUND_REVERSE_PITCH_TORQUE = 1.15f
    /**
     * Extra nose-down nad sklon vozovky (rad). 0.038 rad na rázvore 3.5 m
     * je ~7 cm – citeľný ponor, zadná náprava ostane na ceste.
     */
    const val BRAKE_DIVE_PITCH_LIMIT = 0.038f
    /**
     * Voľný jazd / plyn: nos smie ísť max ~0.06 rad pod cestu. Ďaleko
     * tesnejšie než starý 0.16 rad, inak z kopca aj na ojazdenom pružení
     * zadok vybehne z blatníka, kým hráč nedá brzdu.
     */
    const val COAST_DIVE_PITCH_LIMIT = 0.06f
    /** Pri cúvaní držíme karosériu tesne pri sklone cesty. */
    const val REVERSE_PITCH_LIMIT = 0.045f
    /** μ gúm pri grip=1.0 na čistom asfalte. */
    /** Základné μ. Vyššie = o rýchlosti rozhoduje výkon, nie preklz. */
    const val TIRE_MU = 1.20f
    /** Ako rýchlo koleso dorovná povrchovú rýchlosť (1/s). */
    const val WHEEL_RESP = 14f
    /** Preklz: o koľko m/s sa koleso pretáča navyše pri plnom slip. */
    const val SLIP_SPIN_BONUS = 11f
    /** Dopadová rýchlosť (m/s), od ktorej pruženie/gumy berú ranu. */
    const val LANDING_IMPACT = 7f
    const val LANDING_WEAR = 0.04f
    /** Moment plynu/brzdy vo vzduchu (náklon). */
    const val AIR_PITCH_TORQUE = 0.75f
    /** Transfer hmotnosti pri akcelerácii (ovplyvní grip zadnej nápravy). */
    const val WEIGHT_TRANSFER = 0.22f
    /**
     * Pri FWD sa prenos váhy aj vplyv stúpania krátia – inak by predok pod
     * plynom a do kopca neuniesol nič a náhodne pridelený FWD štart by bol
     * výrazne horšia hra než RWD.
     */
    const val FWD_TRANSFER_MUL = 0.5f
    const val FWD_SLOPE_MUL = 0.35f
    /** Koľko váhy prenesie stúpanie na zadnú nápravu (× sin sklonu). */
    const val SLOPE_TRANSFER = 0.34f
    /** Statické trenie navyše pri rozjazde (0 = žiadne, 0.3 = +30 % μ v kľude). */
    const val STATIC_GRIP_BONUS = 0.30f
    /**
     * Koľkonásobok gripu ešte auto prenesie bez pretáčania. Vodič plyn dávkuje,
     * takže mierny prebytok momentu sa chytí; pretáčať začne až nad touto
     * hranicou. Bez toho sa 2WD auto pálilo na mieste a zožralo gumy.
     */
    const val TRACTION_SLACK = 1.82f
    /**
     * Mäkká kontrola trakcie: keď kolesá preklzávajú, vodič uberie. Bez toho
     * sa 2WD auto točilo na mieste a za pár sto metrov zjedlo gumy.
     */
    const val TRACTION_EASE_OFF = 0.55f
    /** Menej ťahu ako toto sa neuberie nikdy – inak sa do kopca nedá vyjsť. */
    const val TRACTION_EASE_FLOOR = 0.72f
    /**
     * Koľko z ťahu nad hranicou gripu ešte prejde na cestu.
     *
     * Guma je strop, ale nie úplný – silnejší motor si aj na klzkom nájde
     * o kúsok viac (nižší prevod, jemnejšie dávkovanie). Bez toho sa výkon
     * nad grip iba zahodil: na zodratých gumách mal slabý motor presne tú
     * istú rýchlosť ako silný a auto sa na piesku nerozbehlo vôbec.
     *
     * Na snehu a ľade to neplatí – tam prebytok ide do preklzu
     * ([SNOW_GRIP_OVERDRIVE]), inak silný motor na letných preletí kraj.
     */
    const val GRIP_OVERDRIVE = 0.32f
    /**
     * Priľnavosť holého ráfika (kov na asfalte). Ďaleko pod defektom – guma
     * ešte drží, disk sa točí a nebrzdí.
     */
    const val RIM_GRIP = 0.08f
    /** Statické trenie na disku je slabé; guma má [STATIC_GRIP_BONUS]. */
    const val RIM_STATIC_GRIP_MUL = 0.18f
    /** Prebytok ťahu na ráfiku takmer nechytí cestu (guma [GRIP_OVERDRIVE]). */
    const val RIM_GRIP_OVERDRIVE = 0.07f
    /** Disk sa pretáča hneď, bez rezervy ako guma ([TRACTION_SLACK]). */
    const val RIM_TRACTION_SLACK = 1.04f
    /** Koleso na ráfiku sa pri preklze točí rýchlejšie (kov, nie guma). */
    const val RIM_SLIP_SPIN_BONUS = 16f

    /** Lakovačka v dielni – do tejto vzdialenosti je servis len opravy. */
    const val PAINT_SHOP_UNLOCK_M = 10_000f
    /** Prvá dielňa a odstup medzi ďalšími (m). */
    const val AUTO_SHOP_FIRST_M = 20_000f
    const val AUTO_SHOP_SPACING_M = 20_000f
    /**
     * Prvá lakovačka o 10 km neskôr než dielňa, aby sa nestretli na tom istom
     * úseku; potom rovnaký odstup ~20 km.
     */
    const val PAINT_SHOP_FIRST_M = 30_000f
    const val PAINT_SHOP_SPACING_M = 20_000f
    /**
     * Scrapová oprava má ostať drahšia než nájdený diel. Násobiteľ
     * existujúceho vzorca (základ × 0.045 za 20 % zdravia).
     */
    const val SCRAP_REPAIR_COST_MUL = 10f
    /**
     * Katalógový upgrade v dielni. Násobiteľ existujúceho vzorca
     * (rozdiel hodnoty + 12 % cieľa, minimum 18).
     */
    const val SCRAP_UPGRADE_COST_MUL = 3f

    // --- Zima ---
    /** Od akej vzdialenosti sa môže objaviť zasnežená vetva (m). */
    const val SNOW_START_M = 11000f
    /** Odkiaľ sa dá nájsť zimná výbava – skôr, než bude treba. */
    const val WINTER_GEAR_FROM_M = 7000f
    /** Reťaze na snehu / na holom asfalte. */
    const val CHAINS_SNOW_BONUS = 1.65f
    /**
     * Aj na letnej alebo slabej gume reťaze chytia sneh. Nie ako zimná zmes,
     * ale dosť na to, aby boli nástroj – nie kozmetika.
     */
    const val CHAINS_SNOW_FLOOR = 0.62f
    /**
     * Ľad × snehová vozovka inak zomelie aj zimnú+reťaze. Reťaze dvíhajú
     * povrch ľadu a podlahu dláždenia, aby kopec ostal riešením výbavy.
     */
    const val ICE_CHAINS_SURFACE_MUL = 0.62f
    /** Zimná bez reťazí na ľade – plazenie, nie výjazd. */
    const val ICE_WINTER_SURFACE_MUL = 0.46f
    const val ICE_CHAINS_PAVING_FLOOR = 0.70f
    const val CHAINS_TARMAC_PENALTY = 0.88f
    /**
     * Na snehu prebytok výkonu ide do pretáčania kolies, nie do rýchlosti.
     * Zimná guma ešte kúsok chytí; letná takmer nič.
     */
    const val SNOW_GRIP_OVERDRIVE = 0.05f
    /**
     * Reťaze na snehu/ľade už nie sú letný 0.05 – prebytok ide do ťahu,
     * nie do obrovského pretáčania.
     */
    const val CHAINS_SNOW_OVERDRIVE = 0.22f
    /** Letná guma na snehu pretáča hneď – bez rezervy ako na asfalte. */
    const val SNOW_TRACTION_SLACK = 1.08f
    /** S reťazami je rezerva bližšie k suchu – kolesá sa chytia skôr. */
    const val CHAINS_TRACTION_SLACK = 1.68f
    /** Viditeľný aj fyzický preklz s reťazami na zimnom povrchu. */
    const val CHAINS_SLIP_SPIN_MUL = 0.42f
    const val CHAINS_SLIP_VISUAL = 0.40f
    /** Preklz na letných v zime uberie ťah výraznejšie, inak sa kopec vyjde výkonom. */
    const val SNOW_TRACTION_EASE_FLOOR = 0.38f
    /** Strop rýchlosti s reťazami (m/s) – nad tým sa ničia. */
    const val CHAINS_MAX_SPEED = 12f
    /** Pod týmto zdravím je guma roztrhaná a ide sa na disku. */
    const val BLOWN_TYRE_HEALTH = 0.12f
    /** Scrap za záplatu defektu, keď hráč nemá puncture kit. */
    const val PUNCTURE_PATCH_SCRAP = 8
    /** Koľko zdravia gumy doplní puncture kit (aj scrap záplata). */
    const val PUNCTURE_KIT_HEAL = 0.10f
    /** Strop rýchlosti na roztrhanej gume (m/s). */
    const val BLOWN_TYRE_MAX_SPEED = 7f
    /** O koľko °C nižšie drží motor v mraze. */
    const val COLD_TEMP_DROP = 26f
    /** Studený motor žerie viac a batéria dáva menej. */
    const val COLD_FUEL_PENALTY = 0.35f
    const val COLD_BATTERY_DRAIN = 0.60f
    /** Zamrznutá voda v chladiči trhá motor. */
    const val ENGINE_WEAR_FROZEN = 0.016f

    // --- Spotreba (litre za sekundu jazdy) ---
    // Cieľ: ~10 L vystačí zhruba na 2 km, aby mala jazda priestor na progres
    // a nájdené vylepšenia sa stihli prejaviť.
    const val FUEL_IDLE = 0.011f
    const val FUEL_THROTTLE = 0.042f
    const val OIL_DRAIN = 0.0015f
    const val COOLANT_DRAIN = 0.0013f
    const val OVERHEAT_THRESHOLD = 114f
    /** Prázdny okruh: odtiaľto kvapalina prestane chladiť a motor sa začne variť. */
    const val DRY_COOLANT_WEAR_START = 120f
    /** Prázdny okruh: motor zhasne, aby stuhol — až po pásme poškodenia. */
    const val DRY_COOLANT_CUTOUT = 130f
    const val NORMAL_TEMP = 82f
    /**
     * Prázdny okruh: koľko °C pridá chýbajúca kvapalina k cieľovej teplote.
     * Musí pretlačiť prah prehriatia aj na rovinke s dobrým chladičom.
     */
    const val EMPTY_COOLANT_HEAT = 58f
    /** Pod touto náplňou (0–1 kapacity) už chladenie slábne. */
    const val COOLANT_EFFECTIVE_RATIO = 0.38f
    /** Ako rýchlo teplota stúpa/klesá (nižšie = stabilnejšie). */
    const val TEMP_RESPONSE = 0.12f
    const val ENGINE_WEAR_LOW_OIL = 0.012f
    const val ENGINE_WEAR_OVERHEAT = 0.012f
    /** Opotrebenie motora bez kvapaliny v pásme 120–130 °C (za sekundu pri 130). */
    const val ENGINE_WEAR_DRY_COOLANT = 0.038f
    /** Opotrebenie motora zo znečisteného paliva (na plnú „vodu“, za sekundu). */
    const val ENGINE_WEAR_BAD_FUEL = 0.0030f
    /** Nesprávny druh paliva ničí motor výrazne rýchlejšie než nečistoty. */
    const val ENGINE_WEAR_WRONG_FUEL = 0.012f
    /** Opotrebenie motora z riedeného oleja. */
    const val ENGINE_WEAR_BAD_OIL = 0.0035f
    /**
     * Nad touto čistotou kvapalina motoru neškodí. Musí ostať pod priemerom
     * toho, čo sa dá nájsť v dome (~0.64) – dolievanie totiž čistotu len mieša,
     * takže s vyšším prahom by hráč nemal ako sa z poškodzovania dostať a
     * jazda by vždy skončila na zodratom motore.
     */
    const val PURITY_SAFE_OIL = 0.60f
    const val PURITY_SAFE_FUEL = 0.58f
    /** O koľko °C zhorší plne znečistená chladiaca kvapalina cieľovú teplotu. */
    const val BAD_COOLANT_HEAT = 11f
    /** Ťah gravitácie po svahu — necháme na fyzike (g·sinθ). */
    const val FUEL_SLOPE_UP = 3.2f
    const val FUEL_SLOPE_DOWN = 1.6f
    /** Opotrebenie pneumatík/pruženia na rozbitej ceste (za sekundu pri plnej rýchlosti). */
    const val TIRE_WEAR_BROKEN = 0.045f

    // --- Dlhodobý progres ---
    // Krivky náročnosti nemajú tvrdý strop (MathX.growth), len takto obmedzený
    // dosah – inak by terén na 30. km bol nezjazdný.
    const val TERRAIN_MAX_DIFFICULTY = 2.4f
    /**
     * Strop sklonu terénu. Musí ostať pod tým, čo utiahnu slušné gumy
     * (μ × podiel hnanej nápravy ≈ 0.6), inak vzniknú steny, ktoré sa
     * nedajú vyjsť ani s najlepším autom – len s rozbehom.
     */
    const val TERRAIN_MAX_STEEP = 0.55f
    // --- Priebežné opotrebenie (za sekundu jazdy) ---
    /** Gumy: zodierajú sa rýchlosťou, preklzom a hrboľatosťou. */
    const val WEAR_TIRES = 0.00022f
    /** Mäkká zimná zmes sa na teplej/suchej ceste zoderie rýchlejšie. */
    const val WINTER_TIRE_DRY_WEAR_MULTIPLIER = 1.75f
    /** Brzdy: len keď sa brzdí. */
    const val WEAR_BRAKES = 0.00060f
    /** Pruženie: podľa hrbolatosti a dopadov. */
    const val WEAR_SUSPENSION = 0.00020f
    /** Motor: bežný chod, aj keď je všetko čisté. */
    const val WEAR_ENGINE_IDLE = 0.00007f
    /** Elektrika a chladič starnú najpomalšie. */
    const val WEAR_AUX = 0.00005f
    /** Nad touto rýchlosťou rastie opotrebenie nelineárne. */
    const val HIGH_SPEED_WEAR_START = 28f
    const val HIGH_SPEED_WEAR_GAIN = 0.055f

    // --- Bezpečný štart ---
    const val START_FUEL_MIN = 10f
    const val START_FUEL_MAX = 20f
    const val START_OIL_MIN = 1.0f
    const val START_COOLANT_MIN = 1.8f

    // --- Nečakané udalosti ---
    /** Rozsah pauzy medzi udalosťami (s). */
    const val EVENT_GAP_MIN = 26f
    const val EVENT_GAP_MAX = 58f
    /** Únik paliva / chladiacej za sekundu, kým udalosť trvá. */
    const val EVENT_FUEL_LEAK = 0.055f
    const val EVENT_COOLANT_LEAK = 0.022f
    /** Pauza medzi ďalšími zásahmi kameňom pri protivetru (s). */
    const val EVENT_HEADWIND_STRIKE_GAP = 10f
    /** Šanca na extra zásah predku po každej pauze. */
    const val EVENT_HEADWIND_STRIKE_CHANCE = 0.18f
    /** Extra metre po +X pri protivetru – kameň čitateľne bije do predku. */
    const val EVENT_HEADWIND_ROCK_EXTRA_M = 0.40f
    /** Rovnaký rozsah ako ROCK_STRIKE na chladiči – škrabance, nie vrak. */
    const val EVENT_ROCK_CHIP_MIN = 0.15f
    const val EVENT_ROCK_CHIP_MAX = 0.30f
    const val EVENT_ROCK_CHIP_FLOOR = 0.08f
    /**
     * Iskry a SFX pri misfire. Starý cyklus 3.7 Hz blikal ako stroboskop;
     * motor má stále strácať výkon, len pop nech nie je každých ~270 ms.
     */
    const val MISFIRE_FX_HZ = 0.42f
    /** Podiel cyklu, kedy je iskra viditeľná. */
    const val MISFIRE_FX_DUTY = 0.16f

    // --- Úseky trate ---
    const val FEATURE_MIN_LENGTH = 40f

    // --- Denný cyklus ---
    /** Dĺžka celého dňa v sekundách herného času (dlhší deň = neskôr tma). */
    const val DAY_LENGTH = 960f
    /** Štart jazdy ~ 7:00 (0 = polnoc, 0.5 = poludnie). */
    const val DAY_START = 0.29f
    /**
     * Odber batérie svetlami pri vypnutom motore (podiel/s).
     * 100 % SoC má vydržať rozumnú nočnú jazdu, nie minúty.
     */
    const val HEADLIGHT_DRAIN_OFF = 0.0014f
    /** Odber svetlami pri bežiacom motore (zdravý alternátor to pokryje). */
    const val HEADLIGHT_DRAIN_ON = 0.0009f
    /** Diaľkové svetlá majú dva silnejšie okruhy a väčší odber. */
    const val HIGH_BEAM_DRAIN_MULTIPLIER = 1.40f
    /** Strešný reflektor – slabší okruh než stretávacie. */
    const val ROOF_LIGHT_DRAIN_MULTIPLIER = 0.42f
    /** Nabíjanie alternátorom počas jazdy. */
    const val ALTERNATOR_CHARGE = 0.015f
    /** Zapaľovanie, čerpadlá a palubná elektrika pri bežiacom motore. */
    const val RUNNING_ELECTRICAL_DRAIN = 0.002f
    /** Pod touto hodnotou denného svetla treba svetlá (iba skutočná tma). */
    const val NIGHT_THRESHOLD = 0.22f
    // --- Benzínová pumpa ---
    // Opustený stojan je čiastočne vyčerpaný. Hodnota je spoločná zásoba,
    // ktorá sa následne rozdelí medzi benzín a diesel.
    const val PUMP_FUEL_MIN = 6f
    const val PUMP_FUEL_MAX = 32f

    // --- Inventár ---
    /**
     * Batoh na chrbte – to, čo hráč unesie k budove a späť. Zámerne malý,
     * aby mal zmysel kufor a rozhodovanie, čo si vezmem so sebou.
     */
    const val INVENTORY_SLOTS = 8
    const val INVENTORY_MAX_WEIGHT = 60f
    /**
     * Kufor auta – väčší, ale dostupný len keď stojíme pri aute. Nosnosť musí
     * bezpečne presiahnuť najťažší diel (motor 160 kg), inak by sa nájdený
     * motor nedal ani odviezť, ani vymeniť.
     */
    const val BOOT_SLOTS = 10
    const val BOOT_MAX_WEIGHT = 230f

    // --- 3D render buffer and UI density ---
    const val MAX_SCENE_WIDTH = 1920f
    const val MAX_SCENE_HEIGHT = 1080f
    const val UI_DESIGN_SHORT_DP = 720f
    const val UI_DENSITY_SCALE_MIN = 0.85f
    const val UI_DENSITY_SCALE_MAX = 1.25f
    const val UI_DENSITY_GROWTH = 0.40f
    const val UI_USER_SCALE_MIN = 0.80f
    const val UI_USER_SCALE_MAX = 1.30f
    const val UI_USER_SCALE_DEFAULT = 1f

    /** Sneh na aute v zasneženom regióne – hneď jemná vrstva, potom pribúda. */
    const val CAR_SNOW_AMBIENT = 0.22f
    const val CAR_SNOW_GAIN = 0.085f
    const val CAR_SNOW_MELT = 0.048f
    /** Blato / prach na prahoch a blatníkoch. */
    const val CAR_DIRT_GAIN = 0.12f
    const val CAR_DIRT_WASH = 0.18f
    /** Pomalý návrat špiny/hrdze aj mimo blata – po prelakovaní. */
    const val CAR_DIRT_AMBIENT = 0.0045f
    /** Pevná cena prelakovania v lakovačke (šrot). */
    const val PAINT_SHOP_COST = 100

    // --- Sedan (z HillRush HATCHBACK) ---
    const val WHEEL_RADIUS = 0.48f
    /** Polovičný rázvor – (frontWheelFx - rearWheelFx) / 2 × worldWidthM. */
    const val WHEEL_OFFSET_X = 1.764f
    /** Vizuálny zdvih karosérie nad kolesá (m) – blatníky nesmú sedieť na gume. */
    const val BODY_VISUAL_LIFT = 0.24f
    const val HEAD_LOCAL_X = 0.45f
    const val HEAD_LOCAL_Y = 0.87f

    /**
     * Koľkokrát zmenšiť SurfaceView voči oknu, aby scéna neprekročila
     * [MAX_SCENE_WIDTH] × [MAX_SCENE_HEIGHT]. Full HD aj 2K 16:9 dajú 1920×1080.
     */
    fun sceneBufferScale(viewWidthPx: Float, viewHeightPx: Float): Float {
        if (viewWidthPx <= 0f || viewHeightPx <= 0f) return 1f
        return minOf(1f, MAX_SCENE_WIDTH / viewWidthPx, MAX_SCENE_HEIGHT / viewHeightPx)
    }

    fun sceneBufferHeight(viewWidthPx: Float, viewHeightPx: Float): Float =
        viewHeightPx * sceneBufferScale(viewWidthPx, viewHeightPx)

    /**
     * HUD a menu rastú na veľkej obrazovke len mierne. Škála ide z krátkej
     * strany v dp, takže systémové DPI sa nezapočíta dvakrát. [userScale]
     * je posuvník z nastavení.
     */
    fun uiDensityScale(
        widthDp: Int,
        heightDp: Int,
        userScale: Float = UI_USER_SCALE_DEFAULT
    ): Float {
        val shortSide = minOf(widthDp, heightDp).toFloat().coerceAtLeast(1f)
        val linear = shortSide / UI_DESIGN_SHORT_DP
        val auto = (1f + (linear - 1f) * UI_DENSITY_GROWTH)
            .coerceIn(UI_DENSITY_SCALE_MIN, UI_DENSITY_SCALE_MAX)
        val user = userScale.coerceIn(UI_USER_SCALE_MIN, UI_USER_SCALE_MAX)
        return auto * user
    }
}
