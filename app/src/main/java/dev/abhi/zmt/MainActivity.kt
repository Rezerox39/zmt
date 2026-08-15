package dev.abhi.zmt

import android.content.Intent
import android.graphics.Color
import android.media.audiofx.AudioEffect
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.Modifier
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
class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()

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
