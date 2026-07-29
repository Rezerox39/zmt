package dev.jyotiraditya.dmt.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.core.common.Caption
import dev.jyotiraditya.dmt.core.common.TuiKey
import dev.jyotiraditya.dmt.core.common.tuiClickable
import dev.jyotiraditya.dmt.domain.model.DmtSettings
import dev.jyotiraditya.dmt.domain.model.SourceMode
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.presentation.player.DmtView
import dev.jyotiraditya.dmt.ui.theme.LocalTuiColors
import dev.jyotiraditya.dmt.ui.theme.GlassDivider

private data class SourceDescriptor(
    val mode: SourceMode,
    val label: String,
    val subtitle: (DmtSettings) -> String,
    val requiresAuth: Boolean,
    val connected: (DmtSettings) -> Boolean = { true },
    val logout: (DmtSettings) -> DmtSettings = { it },
)

private val SOURCE_REGISTRY = listOf(
    SourceDescriptor(
        mode = SourceMode.LOCAL,
        label = SourceMode.LOCAL.label,
        subtitle = { "files on this device" },
        requiresAuth = false,
    ),
    SourceDescriptor(
        mode = SourceMode.JELLYFIN,
        label = SourceMode.JELLYFIN.label,
        subtitle = { it.jellyfinUrl ?: "not connected" },
        requiresAuth = true,
        connected = { !it.jellyfinToken.isNullOrBlank() },
        logout = {
            it.copy(
                sourceMode = SourceMode.LOCAL,
                jellyfinUrl = null,
                jellyfinUserId = null,
                jellyfinToken = null,
            )
        },
    ),
    SourceDescriptor(
        mode = SourceMode.TELEGRAM,
        label = SourceMode.TELEGRAM.label,
        subtitle = { settings ->
            if (settings.telegramChannelName != null) {
                settings.telegramChannelName
            } else if (settings.telegramChannelId != null) {
                "channel #${settings.telegramChannelId}"
            } else {
                "not connected"
            }
        },
        requiresAuth = true,
        connected = { it.telegramChannelId != null },
        logout = {
            it.copy(
                sourceMode = SourceMode.LOCAL,
                telegramChannelId = null,
                telegramChannelName = null,
                telegramAuthState = null,
            )
        },
    ),
    SourceDescriptor(
        mode = SourceMode.YOUTUBE,
        label = SourceMode.YOUTUBE.label,
        subtitle = { "stream from YouTube Music" },
        requiresAuth = false,
    ),
)

@Composable
fun SourcesPane(state: DmtState, dispatch: (DmtAction) -> Unit) {
    val p = LocalTuiColors.current
    val settings = state.settings

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Caption(stringResource(R.string.sources_title))

        SOURCE_REGISTRY.forEach { source ->
            val connected = source.connected(settings)
            val needsGrant = source.mode == SourceMode.LOCAL && !state.hasPermission
            SourceRow(
                label = source.label,
                subtitle = source.subtitle(settings),
                active = settings.sourceMode == source.mode,
                needsLogin = source.requiresAuth && !connected,
                onGrant = if (needsGrant) {
                    { dispatch(DmtAction.Show(DmtView.PERMISSIONS)) }
                } else {
                    null
                },
                onSelect = {
                    if (source.requiresAuth && !connected) {
                        dispatch(DmtAction.ShowLogin(source.mode))
                    } else {
                        dispatch(DmtAction.Config(settings.copy(sourceMode = source.mode)))
                    }
                },
                onLogout = if (source.requiresAuth && connected) {
                    { dispatch(DmtAction.Config(source.logout(settings))) }
                } else {
                    null
                },
            )
        }

        Text(
            text = stringResource(R.string.sources_hint),
            style = MaterialTheme.typography.labelSmall,
            color = p.faint,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun SourceRow(
    label: String,
    subtitle: String,
    active: Boolean,
    needsLogin: Boolean,
    onSelect: () -> Unit,
    onLogout: (() -> Unit)?,
    onGrant: (() -> Unit)?,
) {
    val p = LocalTuiColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .tuiClickable(onSelect),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            Text(
                text = if (active) "(*) " else "( ) ",
                style = MaterialTheme.typography.bodyLarge,
                color = if (active) p.accent else p.faint,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (active) p.bright else p.fg,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = p.dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            onGrant?.let {
                TuiKey(
                    label = stringResource(R.string.grant),
                    onClick = it,
                )
            }
            if (needsLogin) {
                TuiKey(
                    label = "[ ${stringResource(R.string.source_login)} ]",
                    onClick = onSelect,
                )
            }
            onLogout?.let {
                TuiKey(
                    label = "[ ${stringResource(R.string.source_logout)} ]",
                    onClick = it,
                )
            }
        }
        dev.jyotiraditya.dmt.ui.theme.GlassDivider()
    }
}
