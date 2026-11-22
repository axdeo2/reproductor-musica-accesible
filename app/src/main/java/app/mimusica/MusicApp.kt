package app.mimusica

import android.app.Application
import app.mimusica.di.AppContainer
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.video.VideoFrameDecoder

/**
 * Coil solo con decodificador de cuadros de video (miniaturas de la carpeta del celular).
 * Sin módulo de red: el modo niña nunca carga imágenes de internet.
 */
class MusicApp : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
