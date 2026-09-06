package sk.kubis.endlessdrive.game.audio

/**
 * Jednorazové zvuky, ktoré engine vloží do fronty a UI ich prehrá.
 * Loopy (motor, dážď, vietor) rieši [GameAudio] podľa stavu, nie cez frontu.
 */
enum class GameSfx {
    ENGINE_START,
    ENGINE_STOP,
    ENGINE_STALL,
    ENGINE_FAIL_START,
    BRAKE,
    SKID,
    BLOWOUT,
    ROCK,
    MISFIRE,
    BELT,
    FUEL_LEAK,
    COOLANT_LEAK,
    OIL_SPLASH,
    RAIN,
    MUD,
    DEBRIS,
    TAILWIND,
    CLEAR_ROAD,
    FIND,
    RADIO,
    ANIMAL,
    TRACKS
}
