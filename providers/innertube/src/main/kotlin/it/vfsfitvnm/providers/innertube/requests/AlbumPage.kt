package it.vfsfitvnm.providers.innertube.requests

import it.vfsfitvnm.providers.innertube.Innertube
import it.vfsfitvnm.providers.innertube.models.NavigationEndpoint
import it.vfsfitvnm.providers.innertube.models.bodies.BrowseBody
import io.ktor.http.Url

suspend fun Innertube.albumPage(body: BrowseBody) =
    playlistPage(body)
        ?.map { album ->
            album.url?.let {
                playlistPage(body = BrowseBody(browseId = "VL${Url(it).parameters["list"]}"))
                    ?.getOrNull()
                    ?.let { playlist -> album.copy(songsPage = playlist.songsPage) }
            } ?: album
        }
        ?.map { album ->
            album.copy(
                songsPage = album.songsPage?.copy(
                    items = album.songsPage.items?.map { song ->
                        song.copy(
                            authors = song.authors ?: album.authors,
                            album = Innertube.Info(
                                name = album.title,
                                endpoint = NavigationEndpoint.Endpoint.Browse(
                                    browseId = body.browseId,
                                    params = body.params
                                )
                            ),
                            thumbnail = album.thumbnail
                        )
                    }
                )
            )
        }
