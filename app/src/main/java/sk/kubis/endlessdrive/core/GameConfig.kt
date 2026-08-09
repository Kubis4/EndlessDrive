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
    const val SEGMENT_LENGTH_MIN = 520f
    const val SEGMENT_LENGTH_MAX = 780f
    const val TUTORIAL_SEGMENT_LENGTH = 640f
    const val BUILDING_MIN_GAP_FROM_START = 160f
    const val BUILDING_MIN_SPACING = 140f
    const val JUNCTION_ZONE = 22f
    const val BUILDING_INTERACT_RANGE = 7f

    // --- Jazda (arcade) ---
    const val MAX_SPEED = 26f
    const val REVERSE_MAX_SPEED = 8f
    const val REVERSE_ACCEL = 6f
    /** Späť od najďalej dosiahnutého X (HillRush REVERSE_LIMIT). */
    const val REVERSE_LIMIT = 40f
    const val ACCEL = 8.5f
    const val BRAKE = 18f
    const val COAST_DRAG = 2.4f
    const val STOP_SPEED = 1.0f
    const val BODY_PITCH_SMOOTH = 8f
    const val CAR_RIDE_HEIGHT = 0.42f

    // --- Spotreba (metre/sekundy jazdy) ---
    const val FUEL_IDLE = 0.022f
    const val FUEL_THROTTLE = 0.095f
    const val OIL_DRAIN = 0.0015f
    const val COOLANT_DRAIN = 0.0013f
    const val OVERHEAT_THRESHOLD = 110f
    const val NORMAL_TEMP = 82f
    /** Ako rýchlo teplota stúpa/klesá (nižšie = stabilnejšie). */
    const val TEMP_RESPONSE = 0.12f
    const val ENGINE_WEAR_LOW_OIL = 0.012f
    const val ENGINE_WEAR_OVERHEAT = 0.018f

    // --- Inventár ---
    const val INVENTORY_SLOTS = 16
    const val INVENTORY_MAX_WEIGHT = 160f

    // --- Kamera (bočný pohľad, HillRush-like) ---
    const val CAMERA_BASE_PPM = 56f
    const val CAMERA_MIN_PPM = 42f
    const val CAMERA_LOOK_AHEAD = 0.55f
    const val CAMERA_SMOOTH = 7.0f
    const val CAMERA_Y_BIAS = 0.15f
    const val CAMERA_SPEED_ZOOM = 0.010f

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
    const val WHEEL_OFFSET_X = 1.721f
    const val WHEEL_OFFSET_Y = -0.39f
    const val HEAD_LOCAL_X = 0.45f
    const val HEAD_LOCAL_Y = 0.87f
    const val HEAD_RADIUS = 0.23f
}
