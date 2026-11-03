package io.github.mello13256.fotos

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import io.github.mello13256.fotos.data.CollectionRepository
import io.github.mello13256.fotos.data.MediaRepository
import io.github.mello13256.fotos.data.SettingsRepository
import io.github.mello13256.fotos.data.db.FotosDatabase
import io.github.mello13256.fotos.data.media.MediaSync
import io.github.mello13256.fotos.data.media.TrashRepository
import io.github.mello13256.fotos.image.MediaThumbnailFetcher
import io.github.mello13256.fotos.image.MediaThumbnailKeyer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FotosApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(MediaThumbnailFetcher.Factory(contentResolver))
                add(MediaThumbnailKeyer())
            }
            .build()
}

/** Dependências partilhadas pela app inteira. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database = FotosDatabase.build(app)
    val settings = SettingsRepository(app)
    val media = MediaRepository(database)
    val collections = CollectionRepository(database)
    val trash = TrashRepository(app)
    val mediaSync = MediaSync(app.contentResolver, database, scope)

    init {
        scope.launch { collections.ensureFavorites(app.getString(R.string.favorites)) }
    }
}
