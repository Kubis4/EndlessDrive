package sk.kubis.endlessdrive.core

/**
 * Centrálne konštanty. Svetové jednotky = metre / sekundy.
 * Bočný 2.5D pohľad (HillRush štýl) + arcade jazda po výškovom profile.
 */
object GameConfig {
    const val FIXED_TIME_STEP = 1f / 60f
    const val MAX_FRAME_TIME = 0.25f
    const val TARGET_FPS = 60
    const val TARGET_FRAME_NANOS = 1_000_000_000L / TARGET_FPS
    const val FRAME_LOCK_TOLERANCE_NANOS = 2_000_000L

    // --- Segmenty / križovatky (dlhé cesty = veľké nádrže majú zmysel) ---
    const val SEGMENT_LENGTH_MIN = 720f
    const val SEGMENT_LENGTH_MAX = 980f
    const val TUTORIAL_SEGMENT_LENGTH = 820f
    /** Prvá budova nie hneď za štartom. */
    const val BUILDING_MIN_GAP_FROM_START = 200f
    /** Väčší odstup – viac budov celkovo cez dlhšie segmenty, nie hustá osada. */
    const val BUILDING_MIN_SPACING = 220f
    const val JUNCTION_ZONE = 22f
    /** Ako dlho pred rozcestim sa da vetva volit za jazdy (m). */
    const val JUNCTION_APPROACH = 220f
    const val BUILDING_INTERACT_RANGE = 7f

    // --- Jazda / fyzika auta (Hill Climb arcade) ---
    const val MAX_SPEED = 26f
    const val REVERSE_MAX_SPEED = 8f
    const val REVERSE_ACCEL = 6f
    /** Späť od najďalej dosiahnutého X (HillRush REVERSE_LIMIT). */
    const val REVERSE_LIMIT = 40f
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
    const val AERO_DRAG = 0.028f
    const val STOP_SPEED = 1.0f
    const val BODY_PITCH_SMOOTH = 8f
    /** Voľná výška stredu nad vozovkou pri stlačenom pružení. */
    const val CAR_RIDE_HEIGHT = 0.42f
    /** Vertikálna gravitácia (m/s²). */
    const val AIR_GRAVITY = 22f
    /** Normovaná hmotnosť – sily sú v „m/s² * mass“. */
    const val CAR_MASS = 1f
    /** Zotrvačnosť náklonu. */
    const val PITCH_INERTIA = 2.4f
    /** Tvrdosť pruženia (na 1 m stlačenia) – mäkšie = viditeľný bob. */
    const val SUSP_SPRING = 52f
    /** Tlmenie pruženia. */
    const val SUSP_DAMPER = 7.5f
    /** Max stlačenie pruženia (m). */
    const val SUSP_MAX_TRAVEL = 0.55f
    /** Vizuálne zosilnenie zdvihu pruženia (render). */
    const val SUSP_VISUAL_GAIN = 1.15f
    /** Ako silno pitch sleduje sklon (nižšie = viac voľného náklonu z váhy). */
    const val PITCH_SLOPE_TRACK = 3.2f
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
    const val AIR_PITCH_TORQUE = 2.2f
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
    const val TRACTION_SLACK = 1.30f
    /**
     * Statické rozloženie váhy na zadnú nápravu podľa pohonu. FWD auto má
     * motor nad hnanými kolesami – bez toho by predok neuniesol nič a auto
     * by sa len pretáčalo.
     */
    const val REAR_BIAS_FWD = 0.32f
    const val REAR_BIAS_RWD = 0.52f
    const val REAR_BIAS_AWD = 0.46f
    /**
     * Mäkká kontrola trakcie: keď kolesá preklzávajú, vodič uberie. Bez toho
     * sa 2WD auto točilo na mieste a za pár sto metrov zjedlo gumy.
     */
    const val TRACTION_EASE_OFF = 0.55f
    /** Menej ťahu ako toto sa neuberie nikdy – inak sa do kopca nedá vyjsť. */
    const val TRACTION_EASE_FLOOR = 0.72f

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
    const val OVERHEAT_THRESHOLD = 110f
    const val NORMAL_TEMP = 82f
    /** Ako rýchlo teplota stúpa/klesá (nižšie = stabilnejšie). */
    const val TEMP_RESPONSE = 0.12f
    const val ENGINE_WEAR_LOW_OIL = 0.012f
    const val ENGINE_WEAR_OVERHEAT = 0.018f
    /** Opotrebenie motora zo znečisteného paliva (na plnú „vodu“, za sekundu). */
    const val ENGINE_WEAR_BAD_FUEL = 0.0045f
    /** Opotrebenie motora z riedeného oleja. */
    const val ENGINE_WEAR_BAD_OIL = 0.007f
    /** Nad touto čistotou kvapalina motoru neškodí – bežný nález ho neubije. */
    const val PURITY_SAFE_OIL = 0.75f
    const val PURITY_SAFE_FUEL = 0.70f
    /** O koľko °C zhorší plne znečistená chladiaca kvapalina cieľovú teplotu. */
    const val BAD_COOLANT_HEAT = 18f
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
    /** Vzdialenosť medzi garantovanými depami (m). */
    const val LANDMARK_SPACING = 5000f

    // --- Priebežné opotrebenie (za sekundu jazdy) ---
    /** Gumy: zodierajú sa rýchlosťou, preklzom a hrboľatosťou. */
    const val WEAR_TIRES = 0.00022f
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
    const val FEATURE_MAX_LENGTH = 160f

    // --- Denný cyklus ---
    /** Dĺžka celého dňa v sekundách herného času (dlhší deň = neskôr tma). */
    const val DAY_LENGTH = 960f
    /** Štart jazdy ~ 7:00 (0 = polnoc, 0.5 = poludnie). */
    const val DAY_START = 0.29f
    /** Odber batérie svetlami pri vypnutom motore (podiel/s). */
    const val HEADLIGHT_DRAIN_OFF = 0.007f
    /** Odber svetlami pri bežiacom motore (alternátor to väčšinou pokryje). */
    const val HEADLIGHT_DRAIN_ON = 0.004f
    /** Nabíjanie alternátorom počas jazdy. */
    const val ALTERNATOR_CHARGE = 0.015f
    /** Pod touto hodnotou denného svetla treba svetlá (iba skutočná tma). */
    const val NIGHT_THRESHOLD = 0.22f
    /** Bez svetiel v noci nevidíš – rýchlosť je zastropovaná. */
    const val NIGHT_BLIND_SPEED = 7f

    // --- Benzínová pumpa ---
    const val PUMP_FUEL_MIN = 18f
    const val PUMP_FUEL_MAX = 70f

    // --- Inventár ---
    const val INVENTORY_SLOTS = 16
    const val INVENTORY_MAX_WEIGHT = 160f

    // --- Kamera (bočný pohľad, HillRush-like) ---
    const val CAMERA_BASE_PPM = 66f
    const val CAMERA_MIN_PPM = 50f
    const val CAMERA_LOOK_AHEAD = 0.55f
    const val CAMERA_SMOOTH = 7.0f
    /** Kamera mieri nad auto → auto sedí nižšie a nezaberá toľko hliny. */
    /** Kamera mieri nad auto – väčší bias posunie cestu nižšie a ubere zeminy. */
    const val CAMERA_Y_BIAS = 2.10f
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
    const val DEPTH_AIR_PITCH_BLEND = 0.2f
    const val ROAD_DEPTH = 2.55f
    const val VERGE_DEPTH = 0.38f
    const val TRACK_EDGE_WOBBLE = 0.03f
    const val RUT_NEAR_DEPTH = 0.92f
    const val RUT_FAR_DEPTH = 1.88f
    const val CAR_NEAR_DEPTH = 0.95f
    const val CAR_DEPTH = 1.05f
    const val WHEEL_DEPTH = 0.26f

    // --- Sedan (z HillRush HATCHBACK) ---
    const val WHEEL_RADIUS = 0.48f
    /** Polovičný rázvor – (frontWheelFx - rearWheelFx) / 2 × worldWidthM. */
    const val WHEEL_OFFSET_X = 1.764f
    const val WHEEL_OFFSET_Y = -0.39f
    /** Vizuálny zdvih karosérie nad kolesá (m) – blatníky nesmú sedieť na gume. */
    const val BODY_VISUAL_LIFT = 0.24f
    const val HEAD_LOCAL_X = 0.45f
    const val HEAD_LOCAL_Y = 0.87f
    const val HEAD_RADIUS = 0.23f
}
