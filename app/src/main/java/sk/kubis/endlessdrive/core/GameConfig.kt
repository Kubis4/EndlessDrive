package sk.kubis.endlessdrive.core

/**
 * Centrálne konštanty. Svetové jednotky = metre / sekundy.
 * Bočný 2.5D pohľad (HillRush štýl) + arcade jazda po výškovom profile.
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
    const val TUTORIAL_SEGMENT_LENGTH = 1300f
    /** Plynulé prelínanie dvoch susedných regiónov. */
    const val BIOME_TRANSITION_MIN = 700f
    const val BIOME_TRANSITION_MAX = 1400f
    /** Prvá budova nie hneď za štartom. */
    const val BUILDING_MIN_GAP_FROM_START = 200f
    /** Väčší odstup – viac budov celkovo cez dlhšie segmenty, nie hustá osada. */
    const val BUILDING_MIN_SPACING = 220f
    const val JUNCTION_ZONE = 22f
    /** Ako dlho pred rozcestim sa da vetva volit za jazdy (m). */
    const val JUNCTION_APPROACH = 220f
    const val BUILDING_INTERACT_RANGE = 7f

    // --- Jazda / fyzika auta (Hill Climb arcade) ---
    const val MAX_SPEED = 42f
    const val REVERSE_MAX_SPEED = 8f
    /**
     * Ťah cúvania (m/s²). Musí utiahnuť krátky kopec ako na screenshote,
     * ale ostáva pod [ACCEL], aby na rovine nebolo cúvanie rýchlejšie ako vpred.
     */
    const val REVERSE_ACCEL = 11.5f
    /** Cúvanie berie grip z oboch náprav – RWD inak na hrbolci zhadzuje zadok. */
    const val REVERSE_DRIVE_GRIP = 0.92f
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
    const val BRAKE = 18f
    /** Valivý odpor (m/s²) – veľká fixná brzda pri nízkej rýchlosti. */
    const val COAST_DRAG = 1.15f
    const val AERO_DRAG = 0.014f
    const val STOP_SPEED = 1.0f
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
    /** Holá karoséria je mierne ťažšia vpredu (podiel na zadnej náprave). */
    const val VEHICLE_BASE_REAR_BIAS = 0.44f
    /** Batoh leží pri predných sedadlách, kufor/nosič za zadnou nápravou. */
    const val PACK_LOAD_REAR_BIAS = 0.38f
    const val BOOT_LOAD_REAR_BIAS = 0.88f
    /** Zotrvačnosť náklonu. */
    const val PITCH_INERTIA = 2.4f
    /** Tvrdosť pruženia (na 1 m stlačenia) – mäkšie = viditeľný bob. */
    const val SUSP_SPRING = 52f
    /** Front springs support the engine; softer rear springs reduce empty-car rake. */
    const val FRONT_SPRING_MUL = 1.10f
    const val REAR_SPRING_MUL = 0.90f
    /** Tlmenie pruženia. */
    const val SUSP_DAMPER = 7.5f
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
    const val ROAD_CONTACT_MAX_VY_CORRECTION = 0.70f
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
    const val SHARP_CREST_SLOPE_DELTA_START = 0.08f
    const val SHARP_CREST_SLOPE_DELTA_FULL = 0.28f
    /** Koľko pružinovej špičky sa na ostrom creste absorbuje v pneumatike/tlmiči. */
    const val SHARP_CREST_FORCE_RELIEF = 0.82f
    /** Max stlačenie pruženia (m). */
    const val SUSP_MAX_TRAVEL = 0.55f
    /** Vizuálne zosilnenie zdvihu pruženia (render). Nad 1 koleso ľahko prelezie blatník. */
    const val SUSP_VISUAL_GAIN = 1.0f
    /** Ako silno pitch sleduje sklon (nižšie = viac voľného náklonu z váhy). */
    const val PITCH_SLOPE_TRACK = 3.2f
    /** Dodatočné tlmenie náklonu pri diaľničnej rýchlosti. */
    const val HIGH_SPEED_PITCH_DAMPING = 1.6f
    /** Náklon z plynu/brzdy na zemi (squat / dive). */
    const val GROUND_PITCH_TORQUE = 3.8f
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

    // --- Zima ---
    /** Od akej vzdialenosti sa môže objaviť zasnežená vetva (m). */
    const val SNOW_START_M = 11000f
    /** Odkiaľ sa dá nájsť zimná výbava – skôr, než bude treba. */
    const val WINTER_GEAR_FROM_M = 7000f
    /** Reťaze na snehu / na holom asfalte. */
    const val CHAINS_SNOW_BONUS = 1.55f
    const val CHAINS_TARMAC_PENALTY = 0.88f
    /** Strop rýchlosti s reťazami (m/s) – nad tým sa ničia. */
    const val CHAINS_MAX_SPEED = 12f
    /** Pod týmto zdravím je guma roztrhaná a ide sa na disku. */
    const val BLOWN_TYRE_HEALTH = 0.12f
    /** Scrap za záplatu defektu, keď hráč nemá puncture kit. */
    const val PUNCTURE_PATCH_SCRAP = 8
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
    const val NORMAL_TEMP = 82f
    /** Ako rýchlo teplota stúpa/klesá (nižšie = stabilnejšie). */
    const val TEMP_RESPONSE = 0.12f
    const val ENGINE_WEAR_LOW_OIL = 0.012f
    const val ENGINE_WEAR_OVERHEAT = 0.012f
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

    // --- Nečakané udalosti ---
    /** Rozsah pauzy medzi udalosťami (s). */
    const val EVENT_GAP_MIN = 26f
    const val EVENT_GAP_MAX = 58f
    /** Únik paliva / chladiacej za sekundu, kým udalosť trvá. */
    const val EVENT_FUEL_LEAK = 0.055f
    const val EVENT_COOLANT_LEAK = 0.022f

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
    /**
     * Max SoC, ktoré vie alternátor udržať, je približne jeho zdravie.
     * Malý slack, aby 56 % diel nesadol presne na 56,000 %.
     */
    const val ALTERNATOR_SOC_SLACK = 0.04f
    /** Zapaľovanie, čerpadlá a palubná elektrika pri bežiacom motore. */
    const val RUNNING_ELECTRICAL_DRAIN = 0.002f
    /** Pod touto hodnotou denného svetla treba svetlá (iba skutočná tma). */
    const val NIGHT_THRESHOLD = 0.22f
    // --- Benzínová pumpa ---
    const val PUMP_FUEL_MIN = 18f
    const val PUMP_FUEL_MAX = 70f

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

    // --- Kamera (bočný pohľad, HillRush-like) ---
    const val CAMERA_BASE_PPM = 66f
    const val CAMERA_MIN_PPM = 50f
    const val CAMERA_LOOK_AHEAD = 0.55f
    const val CAMERA_SMOOTH = 7.0f
    /**
     * Kamera mieri nad auto – väčší bias posunie cestu nižšie a ubere zeminy.
     * Pod cestou nie je čo ukazovať, len hnedý pás, kým hore je kreslené
     * pozadie. Preto ide cesta až do spodnej štvrtiny obrazovky.
     */
     const val CAMERA_Y_BIAS = 3.75f
    /** Kde na šírke obrazovky sedí auto (0 = vľavo, 0.5 = stred). */
    const val CAR_SCREEN_X = 0.35f
    const val CAMERA_SPEED_ZOOM = 0.010f
    /** Maximálny rozkmit kamery na rozbitej ceste (m). */
    const val CAMERA_SHAKE = 0.09f

    // --- 2.5D depth (z HillRush) ---
    const val DEPTH_X = 0.15f
    const val DEPTH_Y = 0.48f
    const val DEPTH_FOCAL = 9.5f
    const val DEPTH_PITCH_MAX = 0.55f
    const val DEPTH_PITCH_SMOOTH = 5.5f
    const val DEPTH_PITCH_VP_X = 0.08f
    const val DEPTH_PITCH_VP_Y = 0.55f
    const val DEPTH_PITCH_SHEAR_X = 0.35f
    const val DEPTH_PITCH_SHEAR_Y = 0.28f
    const val DEPTH_PITCH_FOCAL = 0.45f
    const val ROAD_DEPTH = 2.55f
    const val VERGE_DEPTH = 0.38f
    const val RUT_NEAR_DEPTH = 0.92f
    const val RUT_FAR_DEPTH = 1.88f

    // --- Sedan (z HillRush HATCHBACK) ---
    const val WHEEL_RADIUS = 0.48f
    /** Polovičný rázvor – (frontWheelFx - rearWheelFx) / 2 × worldWidthM. */
    const val WHEEL_OFFSET_X = 1.764f
    /** Vizuálny zdvih karosérie nad kolesá (m) – blatníky nesmú sedieť na gume. */
    const val BODY_VISUAL_LIFT = 0.24f
    const val HEAD_LOCAL_X = 0.45f
    const val HEAD_LOCAL_Y = 0.87f
}
