package sk.kubis.endlessdrive.ui.game


internal interface GameSceneSurface {
    fun requestGameRender()
    fun shutdown()
    fun shutdownThen(action: () -> Unit)
    fun setSceneActive(active: Boolean)
}
