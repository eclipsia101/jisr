package org.jisr.app.server

import org.jisr.app.data.StremioMapper
import org.jisr.app.data.StreamResolver

class AddonRouter(
    private val resolver: StreamResolver,
) {
    private val manifest = StremioMapper.manifest().toByteArray(Charsets.UTF_8)

    suspend fun handle(method: String, path: String): HttpReply {
        if (method == "OPTIONS") return HttpReply(204)

        val route = path.split('/').filter { it.isNotEmpty() }
        if (route == listOf("manifest.json")) return HttpReply(200, manifest)

        if (route.size == 3 && route[0] == "stream" && (route[1] == "movie" || route[1] == "series") &&
            route[2].endsWith(".json")
        ) {
            val streams = resolver.resolve(route[1], route[2].removeSuffix(".json"))
            return HttpReply(200, StremioMapper.streams(streams).toByteArray(Charsets.UTF_8))
        }
        return NOT_FOUND
    }

    private companion object {
        val NOT_FOUND = HttpReply(404)
    }
}
