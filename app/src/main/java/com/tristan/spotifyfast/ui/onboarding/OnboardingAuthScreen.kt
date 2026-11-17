package com.tristan.spotifyfast.ui.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tristan.spotifyfast.auth.EncryptedTokenStore
import com.tristan.spotifyfast.auth.PkceAuthManager
import com.tristan.spotifyfast.data.sync.OfflineFirstSyncEngine
import com.tristan.spotifyfast.ui.theme.SpotifyTokens
import kotlinx.coroutines.launch

@Composable
fun OnboardingAuthScreen(
    initialClientId: String,
    pkceAuthManager: PkceAuthManager,
    tokenStore: EncryptedTokenStore,
    syncEngine: OfflineFirstSyncEngine,
    onAuthenticated: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var clientId by remember(initialClientId) { mutableStateOf(initialClientId) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val sha1 = remember { PkceAuthManager.readAppSigningSha1(context) }
    val redirectUri = remember { pkceAuthManager.redirectUri() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyTokens.Background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Spotify Fast",
            style = MaterialTheme.typography.headlineLarge,
            color = SpotifyTokens.AccentGreen,
        )
        Text(
            text = "Client natif ultra-léger (< 300 ms). Connectez votre application Spotify Developer avec votre compte Premium.",
            style = MaterialTheme.typography.bodyMedium,
            color = SpotifyTokens.TextSecondary,
        )

        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = clientId,
            onValueChange = {
                clientId = it
                errorMessage = null
            },
            label = { Text("Spotify Developer Client ID") },
            placeholder = { Text("ex: 8f93a12b4c5d...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SpotifyTokens.AccentGreen,
                unfocusedBorderColor = SpotifyTokens.SurfaceHighlight,
                focusedLabelColor = SpotifyTokens.AccentGreen,
                unfocusedLabelColor = SpotifyTokens.TextSecondary,
                focusedTextColor = SpotifyTokens.TextPrimary,
                unfocusedTextColor = SpotifyTokens.TextPrimary,
                cursorColor = SpotifyTokens.AccentGreen,
            ),
        )

        SetupCopyCard(
            title = "Redirect URI (OAuth 2.1 Loopback)",
            value = redirectUri,
            onCopy = { copyToClipboard(context, "Redirect URI", redirectUri) },
        )

        SetupCopyCard(
            title = "Package Android & Empreinte SHA-1 (App Remote)",
            value = "com.tristan.spotifyfast\n$sha1",
            onCopy = { copyToClipboard(context, "SHA-1", sha1) },
        )

        errorMessage?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = SpotifyTokens.ErrorRed,
            )
        }

        Button(
            onClick = {
                if (clientId.isBlank()) {
                    errorMessage = "Veuillez saisir votre Client ID Spotify Developer."
                    return@Button
                }
                isLoading = true
                errorMessage = null
                scope.launch {
                    val result = pkceAuthManager.authenticateWithCustomTabs(context, clientId)
                    isLoading = false
                    result.onSuccess {
                        syncEngine.syncAll()
                        onAuthenticated()
                    }.onFailure { ex ->
                        errorMessage = ex.message ?: "Échec de l'authentification Spotify."
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SpotifyTokens.AccentGreen,
                contentColor = Color.Black,
            ),
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp)
            } else {
                Text(
                    text = "Se connecter avec Spotify Premium",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black,
                )
            }
        }

        OutlinedButton(
            onClick = {
                isLoading = true
                scope.launch {
                    tokenStore.enableDemoMode(clientId.ifBlank { "demo-client" })
                    syncEngine.seedDemoDataIfNeeded()
                    isLoading = false
                    onAuthenticated()
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Text(
                text = "Tester en mode hors-ligne (Démo)",
                color = SpotifyTokens.TextPrimary,
            )
        }
    }
}

@Composable
private fun SetupCopyCard(
    title: String,
    value: String,
    onCopy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SpotifyTokens.SurfaceCard)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = SpotifyTokens.TextSecondary,
            )
            TextButton(onClick = onCopy) {
                Text("Copier", color = SpotifyTokens.AccentGreen)
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = SpotifyTokens.TextPrimary,
        )
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "$label copié", Toast.LENGTH_SHORT).show()
}
