package dev.abhi.zmt

import android.content.Intent
import android.graphics.Color
import android.media.audiofx.AudioEffect
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.abhi.zmt.ui.theme.TuiDim
import androidx.compose.runtime.mutableStateOf
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import dev.abhi.zmt.presentation.settings.FingerprintAuthManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import dev.abhi.zmt.presentation.settings.FingerprintAuthManager
import dagger.hilt.android.AndroidEntryPoint
import dev.abhi.zmt.presentation.main.DmtScreen
import dev.abhi.zmt.presentation.main.SetupScreen
import dev.abhi.zmt.presentation.player.DmtAction
import dev.abhi.zmt.presentation.player.PlayerEffect
import dev.abhi.zmt.presentation.player.PlayerViewModel
import dev.abhi.zmt.ui.theme.DMTTheme
import dev.abhi.zmt.ui.theme.TuiAccent
import dev.abhi.zmt.ui.theme.TuiThemeProvider
import dev.abhi.zmt.ui.theme.toColor

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()
    private val fingerprintAuth = FingerprintAuthManager()
    private var authenticated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            val state by playerViewModel.state.collectAsState()
            LaunchedEffect(state.settings.accent) {
                TuiAccent = state.settings.accent.toColor()
            }
            DMTTheme(theme = state.settings.theme) {
                TuiThemeProvider(theme = state.settings.theme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val writeLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartIntentSenderForResult(),
                    ) { result ->
                        playerViewModel.onIntent(
                            DmtAction.EmbedLyrics(result.resultCode == RESULT_OK),
                        )
                    }

                    LaunchedEffect(Unit) {
                        playerViewModel.effects.collect { effect ->
                            when (effect) {
                                is PlayerEffect.OpenEqualizer -> openEqualizer(
                                    effect.audioSessionId,
                                )

                                is PlayerEffect.RequestWrite -> writeLauncher.launch(
                                    IntentSenderRequest.Builder(effect.intentSender).build(),
                                )
                            }
                        }
                    }

                    when {
                        !state.settingsLoaded -> Unit
                        !state.settings.setupDone -> SetupScreen(
                            state = state,
                            dispatch = playerViewModel::onIntent,
                        )

                        state.settings.fingerprintLock && !authenticated -> {
                            LaunchedEffect(Unit) {
                                if (fingerprintAuth.canAuthenticate(this@MainActivity)) {
                                    fingerprintAuth.authenticate(
                                        this@MainActivity,
                                        onSuccess = { authenticated = true },
                                        onError = { /* stay locked */ },
                                    )
                                } else {
                                    // No biometric available — let user through
                                    authenticated = true
                                }
                            }
                            if (authenticated) {
                                DmtScreen(
                                    state = state,
                                    dispatch = playerViewModel::onIntent,
                                    art = playerViewModel::homeArt,
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.background,
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        androidx.compose.material3.Text(
                                            text = "tap to unlock",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TuiDim,
                                            modifier = Modifier.clickable {
                                                fingerprintAuth.authenticate(
                                                    this@MainActivity,
                                                    onSuccess = { authenticated = true },
                                                    onError = { },
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        else -> DmtScreen(
                            state = state,
                            dispatch = playerViewModel::onIntent,
                            art = playerViewModel::homeArt,
                        )
                    }
                }
                }
            }
        }
    }

    private fun openEqualizer(audioSessionId: Int) {
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        }
        val handler = packageManager.queryIntentActivities(intent, 0)
            .firstOrNull { it.activityInfo.packageName != "com.android.musicfx" }
        if (handler != null) {
            intent.setClassName(handler.activityInfo.packageName, handler.activityInfo.name)
            startActivity(intent)
        } else {
            playerViewModel.onIntent(DmtAction.NoEqualizer)
        }
    }
}
