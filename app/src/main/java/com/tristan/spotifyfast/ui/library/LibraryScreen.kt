package com.tristan.spotifyfast.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tristan.spotifyfast.ui.components.MediaArtwork
import com.tristan.spotifyfast.ui.components.SpotifyFilterChipsRow
import com.tristan.spotifyfast.ui.icons.SpotifyIcons
import com.tristan.spotifyfast.ui.theme.SpotifyTokens

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenItem: (LibraryItemUi) -> Unit,
    bottomPadding: PaddingValues,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showLocalSearch by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyTokens.Background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SpotifyTokens.ScreenHorizontalPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Bibliothèque",
                style = MaterialTheme.typography.headlineLarge,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(
                        imageVector = SpotifyIcons.Add,
                        contentDescription = "Créer une playlist",
                        tint = SpotifyTokens.TextPrimary,
                    )
                }
                IconButton(onClick = { showLocalSearch = !showLocalSearch }) {
                    Icon(
                        imageVector = SpotifyIcons.Search,
                        contentDescription = "Filtrer la bibliothèque",
                        tint = SpotifyTokens.TextPrimary,
                    )
                }
                IconButton(onClick = viewModel::toggleGridView) {
                    Icon(
                        imageVector = if (state.isGridView) SpotifyIcons.List else SpotifyIcons.Grid,
                        contentDescription = "Changer d'affichage",
                        tint = SpotifyTokens.TextPrimary,
                    )
                }
            }
        }

        if (showLocalSearch) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SpotifyTokens.ScreenHorizontalPadding, vertical = 4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(SpotifyTokens.SurfaceElevated)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (state.searchQuery.isEmpty()) {
                        Text(
                            text = "Rechercher dans votre bibliothèque...",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    BasicTextField(
                        value = state.searchQuery,
                        onValueChange = viewModel::setSearchQuery,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        cursorBrush = SolidColor(SpotifyTokens.AccentGreen),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = SpotifyIcons.Close,
                            contentDescription = "Effacer",
                            tint = SpotifyTokens.TextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        SpotifyFilterChipsRow(
            items = listOf(
                LibraryFilter.ALL to "Tout",
                LibraryFilter.PLAYLISTS to "Playlists",
                LibraryFilter.ARTISTS to "Artistes",
                LibraryFilter.ALBUMS to "Albums",
                LibraryFilter.PODCASTS_AUDIOBOOKS to "Podcasts & Livres",
            ),
            selected = state.filter,
            onSelected = viewModel::setFilter,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = viewModel::cycleSort)
                .padding(horizontal = SpotifyTokens.ScreenHorizontalPadding, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tri : ${state.sort.label}",
                style = MaterialTheme.typography.labelMedium,
                color = SpotifyTokens.TextSecondary,
            )
        }

        if (state.isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = SpotifyTokens.ScreenHorizontalPadding,
                    end = SpotifyTokens.ScreenHorizontalPadding,
                    top = 8.dp,
                    bottom = bottomPadding.calculateBottomPadding() + 20.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(state.items, key = { "${it.kind}_${it.uri}" }) { item ->
                    Column(
                        modifier = Modifier.clickable { onOpenItem(item) },
                    ) {
                        MediaArtwork(
                            url = item.artworkUrl,
                            contentDescription = item.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            isLikedCollection = item.kind == LibraryItemKind.LIKED_TRACKS,
                            fallbackColorArgb = item.dominantColorArgb,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = 4.dp,
                    bottom = bottomPadding.calculateBottomPadding() + 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { "${it.kind}_${it.uri}" }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenItem(item) }
                            .padding(horizontal = SpotifyTokens.ScreenHorizontalPadding, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MediaArtwork(
                            url = item.artworkUrl,
                            contentDescription = item.title,
                            size = 64.dp,
                            cornerRadius = if (item.kind == LibraryItemKind.ARTIST) 32.dp else 4.dp,
                            isLikedCollection = item.kind == LibraryItemKind.LIKED_TRACKS,
                            fallbackColorArgb = item.dominantColorArgb,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = if (item.kind == LibraryItemKind.LIKED_TRACKS) SpotifyTokens.AccentGreen else SpotifyTokens.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (item.isPinned) "Épinglé • ${item.subtitle}" else item.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (item.kind == LibraryItemKind.PLAYLIST) {
                            IconButton(onClick = { viewModel.togglePin(item) }) {
                                Icon(
                                    imageVector = if (item.isPinned) SpotifyIcons.Check else SpotifyIcons.Add,
                                    contentDescription = "Épingler",
                                    tint = if (item.isPinned) SpotifyTokens.AccentGreen else SpotifyTokens.TextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = SpotifyTokens.SurfaceCard,
            titleContentColor = SpotifyTokens.TextPrimary,
            textContentColor = SpotifyTokens.TextPrimary,
            title = { Text("Nouvelle playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Nom de la playlist") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty()) {
                            viewModel.createPlaylist(name)
                            newPlaylistName = ""
                            showCreateDialog = false
                        }
                    },
                ) {
                    Text("Créer", color = SpotifyTokens.AccentGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Annuler", color = SpotifyTokens.TextSecondary)
                }
            },
        )
    }
}
