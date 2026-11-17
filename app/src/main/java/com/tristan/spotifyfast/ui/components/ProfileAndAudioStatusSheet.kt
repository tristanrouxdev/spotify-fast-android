package com.tristan.spotifyfast.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tristan.spotifyfast.BuildConfig
import com.tristan.spotifyfast.auth.PkceAuthManager
import com.tristan.spotifyfast.data.sync.SyncStatus
import com.tristan.spotifyfast.ui.theme.SpotifyTokens
import com.tristan.spotifyfast.update.UpdateStatus
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileAndAudioStatusSheet(
    profileName: String,
    productTier: String,
    clientId: String,
    isDemoMode: Boolean,
    isAppRemoteConnected: Boolean,
    needsSsoAuthorization: Boolean = false,
    syncStatus: SyncStatus = SyncStatus.Idle,
    lastAudioError: String?,
    ssoFeedback: String? = null,
    githubRepo: String,
    updateStatus: UpdateStatus,
    onCheckAndInstallUpdate: (repoSlug: String, force: Boolean) -> Unit,
    onInstallReadyApk: (File) -> Unit,
    onReconnectSpotify: () -> Unit,
    onSyncNow: () -> Unit,
    onConfirmLogout: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var confirmLogoutStep by remember { mutableStateOf(false) }
    var editingRepo by remember { mutableStateOf(false) }
    var repoInput by remember(githubRepo) { mutableStateOf(githubRepo) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SpotifyTokens.SurfaceCard,
        contentColor = SpotifyTokens.TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = profileName.ifBlank { "Compte Spotify" },
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = if (isDemoMode) {
                    "Session Mode Démo hors-ligne active"
                } else {
                    "Session persistante active (${productTier.ifBlank { "Premium" }}) • Client ID : ${clientId.take(8)}..."
                },
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyTokens.AccentGreen,
            )

            // Library & Playlists Sync Card (kept open to display live progress & counts)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SpotifyTokens.SurfaceElevated)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Bibliothèque & Playlists",
                    style = MaterialTheme.typography.titleMedium,
                )
                when (syncStatus) {
                    SyncStatus.Idle -> {
                        Text(
                            text = "Appuyez ci-dessous pour actualiser vos playlists, titres likés, albums et artistes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyTokens.TextSecondary,
                        )
                    }
                    SyncStatus.Syncing -> {
                        Text(
                            text = "Synchronisation en cours...",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyTokens.AccentGreen,
                        )
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = SpotifyTokens.AccentGreen,
                            trackColor = SpotifyTokens.SurfaceHighlight,
                        )
                    }
                    is SyncStatus.Success -> {
                        Text(
                            text = "Synchronisé : ${syncStatus.playlists} playlists • ${syncStatus.likedTracks} titres likés • ${syncStatus.albums} albums • ${syncStatus.artists} artistes",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyTokens.AccentGreen,
                        )
                    }
                    is SyncStatus.Error -> {
                        Text(
                            text = "Erreur de synchronisation : ${syncStatus.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyTokens.ErrorRed,
                        )
                    }
                }
                Button(
                    onClick = onSyncNow,
                    enabled = syncStatus !is SyncStatus.Syncing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotifyTokens.AccentGreen,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(
                        text = if (syncStatus is SyncStatus.Syncing) "Synchronisation..." else "Synchroniser playlists & bibliothèque",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // GitHub Auto-Update Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SpotifyTokens.SurfaceElevated)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Mises à jour GitHub (v${BuildConfig.VERSION_NAME})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = { editingRepo = !editingRepo }) {
                        Text(
                            text = if (editingRepo) "OK" else "Modifier dépôt",
                            color = SpotifyTokens.AccentGreen,
                        )
                    }
                }

                if (editingRepo) {
                    OutlinedTextField(
                        value = repoInput,
                        onValueChange = { repoInput = it },
                        label = { Text("Dépôt GitHub (utilisateur/depot)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SpotifyTokens.AccentGreen,
                            unfocusedBorderColor = SpotifyTokens.SurfaceHighlight,
                            focusedTextColor = SpotifyTokens.TextPrimary,
                            unfocusedTextColor = SpotifyTokens.TextPrimary,
                        ),
                    )
                } else {
                    Text(
                        text = "Source : github.com/$repoInput",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpotifyTokens.TextSecondary,
                    )
                }

                when (updateStatus) {
                    UpdateStatus.Idle -> Unit
                    UpdateStatus.Checking -> {
                        Text("Vérification sur GitHub...", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = SpotifyTokens.AccentGreen,
                            trackColor = SpotifyTokens.SurfaceHighlight,
                        )
                    }
                    is UpdateStatus.UpdateAvailable -> {
                        Text(
                            text = "Nouvelle version ${updateStatus.tagName} détectée ! Téléchargement...",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyTokens.AccentGreen,
                        )
                    }
                    is UpdateStatus.Downloading -> {
                        Text(
                            text = "Téléchargement de l'APK : ${updateStatus.percent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyTokens.AccentGreen,
                        )
                        LinearProgressIndicator(
                            progress = { updateStatus.percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = SpotifyTokens.AccentGreen,
                            trackColor = SpotifyTokens.SurfaceHighlight,
                        )
                    }
                    is UpdateStatus.ReadyToInstall -> {
                        Text(
                            text = "Mise à jour ${updateStatus.tagName} prête à installer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyTokens.AccentGreen,
                        )
                        Button(
                            onClick = { onInstallReadyApk(updateStatus.apkFile) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpotifyTokens.AccentGreen,
                                contentColor = Color.Black,
                            ),
                        ) {
                            Text("Installer maintenant (${updateStatus.tagName})", fontWeight = FontWeight.Bold)
                        }
                    }
                    is UpdateStatus.UpToDate -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Déjà à jour (v${updateStatus.currentVersion})",
                                style = MaterialTheme.typography.bodySmall,
                                color = SpotifyTokens.AccentGreen,
                            )
                            TextButton(onClick = { onCheckAndInstallUpdate(repoInput, true) }) {
                                Text("Réinstaller", color = SpotifyTokens.TextSecondary)
                            }
                        }
                    }
                    is UpdateStatus.Error -> {
                        Text(
                            text = updateStatus.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = SpotifyTokens.ErrorRed,
                        )
                    }
                }

                val isBusy = updateStatus is UpdateStatus.Checking || updateStatus is UpdateStatus.Downloading
                Button(
                    onClick = {
                        editingRepo = false
                        onCheckAndInstallUpdate(repoInput, false)
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotifyTokens.AccentGreen,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(
                        text = if (isBusy) "Mise à jour en cours..." else "Mettre à jour depuis GitHub",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Spotify Audio Engine Card (Connect Web API active + optional App Remote IPC)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SpotifyTokens.SurfaceElevated)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Moteur Audio Spotify",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = when {
                            isDemoMode -> "Audio Démo"
                            isAppRemoteConnected -> "Connecté (App Remote IPC)"
                            else -> "Connecté (Spotify Connect)"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = SpotifyTokens.AccentGreen,
                    )
                }

                if (!isDemoMode && !isAppRemoteConnected) {
                    Text(
                        text = "Lecture audio et contrôles actifs via Spotify Connect Web API & MediaSession.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SpotifyTokens.TextSecondary,
                    )
                }

                val diagnosticMessage = ssoFeedback ?: lastAudioError
                if (!diagnosticMessage.isNullOrBlank()) {
                    Text(
                        text = diagnosticMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = SpotifyTokens.ErrorRed,
                    )
                }

                if (!isDemoMode && !isAppRemoteConnected) {
                    val appSha1 = remember(context) { PkceAuthManager.readAppSigningSha1(context) }
                    Text(
                        text = "Optionnel App Remote IPC — Package : ${context.packageName} • SHA-1 : $appSha1",
                        style = MaterialTheme.typography.labelSmall,
                        color = SpotifyTokens.TextSecondary,
                    )
                    OutlinedButton(
                        onClick = onReconnectSpotify,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (needsSsoAuthorization) {
                                "Associer aussi App Remote IPC (optionnel)"
                            } else {
                                "Vérifier le pont App Remote IPC (optionnel)"
                            },
                            color = SpotifyTokens.TextPrimary,
                        )
                    }
                } else if (!isDemoMode) {
                    OutlinedButton(
                        onClick = onReconnectSpotify,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Vérifier / Actualiser le pont audio", color = SpotifyTokens.TextPrimary)
                    }
                }

                if (!isDemoMode) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val launch = context.packageManager.getLaunchIntentForPackage("com.spotify.music")
                                if (launch != null) {
                                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(launch)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Ouvrir Spotify officiel (pour réveiller/autoriser)", color = SpotifyTokens.TextPrimary)
                    }
                }
            }

            HorizontalDivider(color = SpotifyTokens.Divider)

            if (!confirmLogoutStep) {
                TextButton(
                    onClick = { confirmLogoutStep = true },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = "Changer de Client ID / Se déconnecter",
                        color = SpotifyTokens.TextSecondary,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { confirmLogoutStep = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Annuler", color = SpotifyTokens.TextPrimary)
                    }
                    Button(
                        onClick = {
                            confirmLogoutStep = false
                            onConfirmLogout()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyTokens.ErrorRed,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text("Confirmer déconnexion")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
