package org.jisr.app.server

import org.jisr.app.data.SettingsRepository
import org.jisr.app.data.StreamResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.BindException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

sealed interface ServerState {
    data object Stopped : ServerState
    data object Starting : ServerState

    data object MissingKey : ServerState
    data class Failed(val portInUse: Boolean) : ServerState
    data class Running(
        val host: String,
        val port: Int,
        val localNetwork: Boolean,
    ) : ServerState {
        val url: String get() = "http://$host:$port/manifest.json"
    }
}

class ServerController(
    private val settings: SettingsRepository,
    private val resolver: StreamResolver,
) {
    private val mutable = MutableStateFlow<ServerState>(ServerState.Stopped)
    val state: StateFlow<ServerState> = mutable.asStateFlow()

    private val lock = Mutex()
    private var server: LocalHttpServer? = null

    suspend fun start(): Boolean = lock.withLock {
        if (server != null) return true
        val config = settings.current()
        if (config.tmdbKey.isEmpty()) {
            mutable.value = ServerState.MissingKey
            return false
        }
        mutable.value = ServerState.Starting
        val bind = if (config.localNetwork) InetAddress.getByName("0.0.0.0") else InetAddress.getLoopbackAddress()
        val candidate = LocalHttpServer(bind, config.port, AddonRouter(resolver)::handle)
        try {
            candidate.start()
        } catch (e: BindException) {
            mutable.value = ServerState.Failed(portInUse = true)
            return false
        } catch (e: Exception) {
            mutable.value = ServerState.Failed(portInUse = false)
            return false
        }
        server = candidate
        mutable.value = ServerState.Running(hostFor(config.localNetwork), config.port, config.localNetwork)
        true
    }

    suspend fun stop() {
        lock.withLock {
            server?.stop()
            server = null
            if (mutable.value !is ServerState.Failed && mutable.value !is ServerState.MissingKey) mutable.value = ServerState.Stopped
        }
    }

    suspend fun restart(): Boolean {
        stop()
        return start()
    }

    fun isRunning(): Boolean = mutable.value is ServerState.Running

    fun refreshHost() {
        val current = mutable.value as? ServerState.Running ?: return
        val host = hostFor(current.localNetwork)
        if (host != current.host) mutable.value = current.copy(host = host)
    }

    private fun hostFor(localNetwork: Boolean): String =
        if (localNetwork) lanAddress() ?: LOOPBACK else LOOPBACK

    private fun lanAddress(): String? = runCatching {
        val addresses = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
        (addresses.firstOrNull { it.isSiteLocalAddress } ?: addresses.firstOrNull())?.hostAddress
    }.getOrNull()

    private companion object {
        const val LOOPBACK = "127.0.0.1"
    }
}
