package com.tristan.spotifyfast.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tristan.spotifyfast.ui.components.MediaArtwork
import com.tristan.spotifyfast.ui.components.SpotifyFilterChipsRow
import com.tristan.spotifyfast.ui.icons.SpotifyIcons
import com.tristan.spotifyfast.ui.theme.SpotifyTokens

private val BrowseTiles = listOf(
    "Électro" to Color(0xFF1E3264),
    "Pop" to Color(0xFFE13300),
    "Hip-Hop" to Color(0xFFBA5D07),
    "Podcasts" to Color(0xFF006450),
    "Livres audio" to Color(0xFF503750),
    "Focus" to Color(0xFF1DB954),
    "Rock" to Color(0xFFE91429),
    "Jazz" to Color(0xFF477D95),
)

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onPlayUri: (uri: String) -> Unit,
    onOpenUri: (uri: String, title: String) -> Unit,
    bottomPadding: PaddingValues,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyTokens.Background)
            .statusBarsPadding(),
    ) {
        Text(
            text = "Rechercher",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(horizontal = SpotifyTokens.ScreenHorizontalPadding, vertical = 12.dp),
        )

        // Spotify white search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpotifyTokens.ScreenHorizontalPadding)
                .height(48.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = SpotifyIcons.Search,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (state.query.isEmpty()) {
                    Text(
                        text = "Que souhaitez-vous écouter ?",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color(0xFF535353),
                    )
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.Black,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(SpotifyTokens.AccentGreen),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.query.isNotEmpty()) {
                IconButton(onClick = { viewModel.onQueryChange("") }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = SpotifyIcons.Close,
                        contentDescription = "Effacer",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        if (state.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                color = SpotifyTokens.AccentGreen,
                trackColor = SpotifyTokens.Background,
            )
        }

        if (state.query.isBlank()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = bottomPadding.calculateBottomPadding() + 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (history.isNotEmpty()) {
                    item(key = "history_title") {
                        Text(
                            text = "Recherches récentes",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = SpotifyTokens.ScreenHorizontalPadding),
                        )
                    }
                    items(history, key = { "hist_${it.query}" }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onQueryChange(item.query) }
                                .padding(horizontal = SpotifyTokens.ScreenHorizontalPadding, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = SpotifyIcons.Search,
                                contentDescription = null,
                                tint = SpotifyTokens.TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = item.query,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { viewModel.removeHistoryItem(item.query) },
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    imageVector = SpotifyIcons.Close,
                                    contentDescription = "Supprimer",
                                    tint = SpotifyTokens.TextSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                item(key = "browse_title") {
                    Text(
                        text = "Parcourir tout",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = SpotifyTokens.ScreenHorizontalPadding),
                    )
                }

                item(key = "browse_grid") {
                    Column(
                        modifier = Modifier.padding(horizontal = SpotifyTokens.ScreenHorizontalPadding),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BrowseTiles.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                pair.forEach { (title, bgColor) ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(88.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(bgColor)
                                            .clickable { viewModel.onQueryChange(title) }
                                            .padding(12.dp),
                                    ) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.height(10.dp))
            SpotifyFilterChipsRow(
                items = listOf(
                    SearchCategory.ALL to "Tout",
                    SearchCategory.TRACKS to "Titres",
                    SearchCategory.ARTISTS to "Artistes",
                    SearchCategory.ALBUMS to "Albums",
                    SearchCategory.PLAYLISTS to "Playlists",
                    SearchCategory.PODCASTS to "Podcasts",
                    SearchCategory.AUDIOBOOKS to "Livres audio",
                ),
                selected = state.selectedCategory,
                onSelected = viewModel::setCategory,
            )

            val res = state.results
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 12.dp,
                    bottom = bottomPadding.calculateBottomPadding() + 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val cat = state.selectedCategory
                if ((cat == SearchCategory.ALL || cat == SearchCategory.TRACKS) && res?.tracks?.items?.isNotEmpty() == true) {
                    items(res.tracks.items, key = { "tr_${it.uri}" }) { track ->
                        SearchResultRow(
                            title = track.name,
                            subtitle = "Titre • ${track.primarySubtitle}",
                            artworkUrl = track.artworkUrl,
                            onClick = { onPlayUri(track.uri) },
                        )
                    }
                }
                if ((cat == SearchCategory.ALL || cat == SearchCategory.ARTISTS) && res?.artists?.items?.isNotEmpty() == true) {
                    items(res.artists.items, key = { "ar_${it.id}" }) { artist ->
                        SearchResultRow(
                            title = artist.name,
                            subtitle = "Artiste",
                            artworkUrl = artist.images.firstOrNull()?.url,
                            onClick = { onOpenUri(artist.uri, artist.name) },
                        )
                    }
                }
                if ((cat == SearchCategory.ALL || cat == SearchCategory.ALBUMS) && res?.albums?.items?.isNotEmpty() == true) {
                    items(res.albums.items, key = { "al_${it.id}" }) { album ->
                        SearchResultRow(
                            title = album.name,
                            subtitle = "Album • ${album.artists.joinToString(", ") { it.name }}",
                            artworkUrl = album.images.firstOrNull()?.url,
                            onClick = { onOpenUri(album.uri, album.name) },
                        )
                    }
                }
                if ((cat == SearchCategory.ALL || cat == SearchCategory.PLAYLISTS) && res?.playlists?.items?.isNotEmpty() == true) {
                    items(res.playlists.items, key = { "pl_${it.id}" }) { playlist ->
                        SearchResultRow(
                            title = playlist.name,
                            subtitle = "Playlist • ${playlist.owner?.displayName ?: "Spotify"}",
                            artworkUrl = playlist.images.firstOrNull()?.url,
                            onClick = { onOpenUri(playlist.uri, playlist.name) },
                        )
                    }
                }
                if ((cat == SearchCategory.ALL || cat == SearchCategory.PODCASTS) && res?.shows?.items?.isNotEmpty() == true) {
                    items(res.shows.items, key = { "sh_${it.id}" }) { show ->
                        SearchResultRow(
                            title = show.name,
                            subtitle = "Podcast • ${show.publisher.orEmpty()}",
                            artworkUrl = show.images.firstOrNull()?.url,
                            onClick = { onOpenUri(show.uri, show.name) },
                        )
                    }
                }
                if ((cat == SearchCategory.ALL || cat == SearchCategory.AUDIOBOOKS) && res?.audiobooks?.items?.isNotEmpty() == true) {
                    items(res.audiobooks.items, key = { "ab_${it.id}" }) { book ->
                        SearchResultRow(
                            title = book.name,
                            subtitle = "Livre audio • ${book.authors.joinToString(", ") { it.name }}",
                            artworkUrl = book.images.firstOrNull()?.url,
                            onClick = { onOpenUri(book.uri, book.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SpotifyTokens.ScreenHorizontalPadding, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaArtwork(url = artworkUrl, contentDescription = title, size = 52.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
