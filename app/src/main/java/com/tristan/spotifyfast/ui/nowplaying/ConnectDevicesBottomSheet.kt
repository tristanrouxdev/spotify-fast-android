package com.tristan.spotifyfast.ui.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tristan.spotifyfast.player.ConnectDevice
import com.tristan.spotifyfast.ui.components.EqualizerIndicator
import com.tristan.spotifyfast.ui.icons.SpotifyIcons
import com.tristan.spotifyfast.ui.theme.SpotifyTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectDevicesBottomSheet(
    devices: List<ConnectDevice>,
    activeDevice: ConnectDevice?,
    isPlaying: Boolean,
    onSelectDevice: (String) -> Unit,
    onVolumeChange: (Int, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SpotifyTokens.SurfaceCard,
        contentColor = SpotifyTokens.TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Appareil d'écoute",
                style = MaterialTheme.typography.headlineMedium,
            )

            val currentTarget = activeDevice ?: devices.firstOrNull { it.isActive }
            if (currentTarget != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SpotifyTokens.SurfaceElevated)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    EqualizerIndicator(isPlaying = isPlaying, size = 18.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Écoute en cours sur",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyTokens.AccentGreen,
                        )
                        Text(
                            text = "${currentTarget.name} (${currentTarget.type})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = SpotifyTokens.AccentGreen,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SpotifyTokens.SurfaceElevated)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = SpotifyIcons.Devices,
                        contentDescription = null,
                        tint = SpotifyTokens.AccentGreen,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Cet appareil (App Remote local)",
                        style = MaterialTheme.typography.titleMedium,
                        color = SpotifyTokens.AccentGreen,
                    )
                }
            }

            Text(
                text = "Sélectionner un appareil Spotify Connect",
                style = MaterialTheme.typography.labelMedium,
                color = SpotifyTokens.TextSecondary,
            )

            if (devices.isEmpty()) {
                Text(
                    text = "Aucun autre appareil Spotify Connect détecté actuellement. Ouvrez Spotify sur votre PC, TV ou enceinte pour le voir apparaître ici.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SpotifyTokens.TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(devices, key = { it.id }) { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectDevice(device.id) }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = SpotifyIcons.Devices,
                                contentDescription = device.type,
                                tint = if (device.isActive) SpotifyTokens.AccentGreen else SpotifyTokens.TextSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (device.isActive) SpotifyTokens.AccentGreen else SpotifyTokens.TextPrimary,
                                )
                                Text(
                                    text = device.type,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            if (device.isActive) {
                                EqualizerIndicator(isPlaying = isPlaying, size = 14.dp)
                            }
                        }
                    }
                }
            }

            if (currentTarget != null) {
                var sliderVolume by remember(currentTarget.id, currentTarget.volumePercent) {
                    mutableFloatStateOf(currentTarget.volumePercent.toFloat())
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Volume distant", style = MaterialTheme.typography.labelMedium)
                        Text("${sliderVolume.toInt()}%", style = MaterialTheme.typography.bodySmall)
                    }
                    Slider(
                        value = sliderVolume,
                        onValueChange = { sliderVolume = it },
                        onValueChangeFinished = {
                            onVolumeChange(sliderVolume.toInt(), currentTarget.id)
                        },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = SpotifyTokens.AccentGreen,
                            inactiveTrackColor = SpotifyTokens.SurfaceHighlight,
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
