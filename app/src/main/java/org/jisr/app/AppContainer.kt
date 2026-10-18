package org.jisr.app

import android.app.Application
import android.content.Context
import org.jisr.app.data.PluginRepository
import org.jisr.app.data.SettingsRepository
import org.jisr.app.data.StreamResolver
import org.jisr.app.data.TmdbClient
import org.jisr.app.server.ServerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jisr.app.net.DohProvider
import org.jisr.app.net.DohResolver

class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(context)
    val plugins = PluginRepository(context.applicationContext, settings)
    val tmdb = TmdbClient(settings)
    val resolver = StreamResolver(plugins, tmdb, settings)
    val server = ServerController(settings, resolver)

    init {
        DohResolver.configure(DohProvider.Cloudflare, "")
        appScope.launch {
            settings.settings.map { it.dohProvider to it.dohCustomUrl }.distinctUntilChanged().collect { (id, url) ->
                DohResolver.configure(DohProvider.fromId(id), url)
            }
        }
    }
}

class JisrApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
