package sk.kubis.endlessdrive.ui.game

/** Geometry shared by every biome backdrop. Values are screen pixels. */
internal object BackdropLayout {
    /** Stable screen-space join below the horizon; hills move in front of it. */
    private const val HORIZON_DROP = 0.025f
    private const val NEAR_DROP = 0.012f
    private const val MID_RISE_INFLUENCE = 0.10f
    // Najvyššie koruny živého lesa siahajú približne po 0.28 výšky obrazu.
    // Kotúč pustíme až nad ne; nižšia hranica ho ukázala cez medzery medzi
    // stromami skôr, než naozaj vyšiel nad les.
    private const val FOREST_CELESTIAL_OCCLUSION = 0.29f

    fun anchorForHorizon(horizonY: Float, screenHeight: Float): Float =
        horizonY + screenHeight * HORIZON_DROP

    fun midBase(anchorY: Float, screenHeight: Float, authoredRise: Float): Float =
        anchorY - screenHeight * authoredRise.coerceIn(0f, 0.08f) * MID_RISE_INFLUENCE

    fun nearBase(midBaseY: Float, screenHeight: Float): Float =
        midBaseY + screenHeight * NEAR_DROP

    fun celestialClipBottom(horizonY: Float, screenHeight: Float, forestArtwork: Boolean): Float =
        if (forestArtwork) horizonY - screenHeight * FOREST_CELESTIAL_OCCLUSION else horizonY
}
