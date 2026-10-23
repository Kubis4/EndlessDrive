package sk.kubis.endlessdrive.domain.model

import sk.kubis.endlessdrive.core.GameConfig

/** Physical dimensions shared by suspension and the modular 3D vehicle. */
object SedanSpec {
    val wheelRadius = GameConfig.WHEEL_RADIUS
    val wheelOffsetX = GameConfig.WHEEL_OFFSET_X
}
