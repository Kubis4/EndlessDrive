package sk.kubis.endlessdrive.di

import android.content.Context
import sk.kubis.endlessdrive.data.DataStorePlayerRepository
import sk.kubis.endlessdrive.domain.repository.PlayerRepository
import sk.kubis.endlessdrive.ui.game.GameAssets

class AppContainer(context: Context) {
    private val app = context.applicationContext

    val playerRepository: PlayerRepository = DataStorePlayerRepository(app)

    /**
     * Sprite auta a pozadia sa spracúvajú (flood fill, orezanie) pri prvom
     * použití a potom žijú s procesom – po otočení displeja alebo návrate
     * z menu sa už nedekódujú znova.
     */
    val gameAssets: GameAssets by lazy { GameAssets(app) }
}
