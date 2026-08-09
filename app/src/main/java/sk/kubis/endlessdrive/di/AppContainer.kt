package sk.kubis.endlessdrive.di

import android.content.Context
import sk.kubis.endlessdrive.data.DataStorePlayerRepository
import sk.kubis.endlessdrive.domain.repository.PlayerRepository

class AppContainer(context: Context) {
    val playerRepository: PlayerRepository = DataStorePlayerRepository(context)
}
