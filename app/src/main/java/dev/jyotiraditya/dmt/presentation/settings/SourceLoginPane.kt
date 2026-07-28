package dev.jyotiraditya.dmt.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.jyotiraditya.dmt.R
import dev.jyotiraditya.dmt.core.common.Caption
import dev.jyotiraditya.dmt.core.common.TuiKey
import dev.jyotiraditya.dmt.domain.model.SourceMode
import dev.jyotiraditya.dmt.presentation.player.DmtAction
import dev.jyotiraditya.dmt.presentation.player.DmtState
import dev.jyotiraditya.dmt.presentation.player.DmtView
import dev.jyotiraditya.dmt.ui.theme.LocalTuiColors

@Composable
fun SourceLoginPane(mode: SourceMode, state: DmtState, dispatch: (DmtAction) -> Unit) {
    val p = LocalTuiColors.current
    when (mode) {
        SourceMode.TELEGRAM -> TelegramLoginPane(state, dispatch)
        else -> JellyfinLoginPane(mode, dispatch)
    }
}

@Composable
private fun TelegramLoginPane(state: DmtState, dispatch: (DmtAction) -> Unit) {
    val p = LocalTuiColors.current
    var phoneNumber by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var channelInput by remember { mutableStateOf("") }
    var showMissing by remember { mutableStateOf(false) }

    val authStep = state.telegramAuthStep
    val isScanning = state.scanning

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Caption(stringResource(R.string.source_login_title, SourceMode.TELEGRAM.label))

        when {
            authStep.isEmpty() || authStep == "phone" -> {
                LoginField(
                    label = stringResource(R.string.telegram_phone_label),
                    value = phoneNumber,
                    hint = stringResource(R.string.telegram_phone_hint),
                    onValueChange = { phoneNumber = it },
                    keyboardType = KeyboardType.Phone,
                )

                Row(modifier = Modifier.padding(top = 18.dp)) {
                    TuiKey(
                        label = "[ ${stringResource(R.string.source_login_cancel)} ]",
                        onClick = { dispatch(DmtAction.Show(DmtView.SOURCES)) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TuiKey(
                        label = if (isScanning) "[ connecting... ]" else "[ ${stringResource(R.string.source_connect)} ]",
                        bright = !isScanning,
                        onClick = {
                            if (isScanning) return@TuiKey
                            if (phoneNumber.isBlank()) {
                                showMissing = true
                            } else {
                                showMissing = false
                                dispatch(DmtAction.TelegramSendPhone(phoneNumber.trim()))
                            }
                        },
                    )
                }

                if (isScanning) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        CircularProgressIndicator(
                            color = p.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.height(16.dp).width(16.dp),
                        )
                        Text(
                            text = " connecting to telegram...",
                            style = MaterialTheme.typography.labelSmall,
                            color = p.dim,
                        )
                    }
                }
            }
            authStep == "code" -> {
                Text(
                    text = stringResource(R.string.telegram_code_sent),
                    style = MaterialTheme.typography.labelSmall,
                    color = p.dim,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LoginField(
                    label = stringResource(R.string.telegram_code_label),
                    value = code,
                    hint = stringResource(R.string.telegram_code_hint),
                    onValueChange = { code = it },
                    keyboardType = KeyboardType.Number,
                )

                Row(modifier = Modifier.padding(top = 18.dp)) {
                    TuiKey(
                        label = "[ ${stringResource(R.string.source_login_cancel)} ]",
                        onClick = { dispatch(DmtAction.Show(DmtView.SOURCES)) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TuiKey(
                        label = "[ ${stringResource(R.string.source_connect)} ]",
                        bright = true,
                        onClick = {
                            if (code.isBlank()) {
                                showMissing = true
                            } else {
                                showMissing = false
                                dispatch(DmtAction.TelegramSubmitCode(code.trim()))
                            }
                        },
                    )
                }
            }
            authStep == "password" -> {
                Text(
                    text = "2FA password required",
                    style = MaterialTheme.typography.labelSmall,
                    color = p.dim,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LoginField(
                    label = stringResource(R.string.telegram_password_label),
                    value = password,
                    hint = stringResource(R.string.telegram_password_hint),
                    onValueChange = { password = it },
                    keyboardType = KeyboardType.Password,
                    mask = true,
                )

                Row(modifier = Modifier.padding(top = 18.dp)) {
                    TuiKey(
                        label = "[ ${stringResource(R.string.source_login_cancel)} ]",
                        onClick = { dispatch(DmtAction.Show(DmtView.SOURCES)) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TuiKey(
                        label = "[ ${stringResource(R.string.source_connect)} ]",
                        bright = true,
                        onClick = {
                            if (password.isBlank()) {
                                showMissing = true
                            } else {
                                showMissing = false
                                dispatch(DmtAction.TelegramSubmitPassword(password.trim()))
                            }
                        },
                    )
                }
            }
            authStep == "channel" -> {
                Text(
                    text = "enter channel username or id",
                    style = MaterialTheme.typography.labelSmall,
                    color = p.dim,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LoginField(
                    label = "channel",
                    value = channelInput,
                    hint = "@channel or numeric id",
                    onValueChange = { channelInput = it },
                )

                Row(modifier = Modifier.padding(top = 18.dp)) {
                    TuiKey(
                        label = "[ ${stringResource(R.string.source_login_cancel)} ]",
                        onClick = { dispatch(DmtAction.Show(DmtView.SOURCES)) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TuiKey(
                        label = if (state.telegramSyncing) "[ syncing... ]" else "[ ${stringResource(R.string.source_connect)} ]",
                        bright = !state.telegramSyncing,
                        onClick = {
                            if (state.telegramSyncing) return@TuiKey
                            if (channelInput.isBlank()) {
                                showMissing = true
                            } else {
                                showMissing = false
                                dispatch(DmtAction.TelegramResolveChannel(channelInput.trim()))
                            }
                        },
                    )
                }

                if (state.telegramSyncing) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 12.dp),
                    ) {
                        CircularProgressIndicator(
                            color = p.accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.height(16.dp).width(16.dp),
                        )
                        Text(
                            text = " syncing channel...",
                            style = MaterialTheme.typography.labelSmall,
                            color = p.dim,
                        )
                    }
                }
            }
            authStep == "logged_in" -> {
                Text(
                    text = "logged in!",
                    style = MaterialTheme.typography.labelMedium,
                    color = p.accent,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LoginField(
                    label = "channel",
                    value = channelInput,
                    hint = "@channel or numeric id",
                    onValueChange = { channelInput = it },
                )

                Row(modifier = Modifier.padding(top = 18.dp)) {
                    TuiKey(
                        label = "[ ${stringResource(R.string.source_login_cancel)} ]",
                        onClick = { dispatch(DmtAction.Show(DmtView.SOURCES)) },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TuiKey(
                        label = "[ ${stringResource(R.string.source_connect)} ]",
                        bright = true,
                        onClick = {
                            if (channelInput.isBlank()) {
                                showMissing = true
                            } else {
                                showMissing = false
                                dispatch(DmtAction.TelegramResolveChannel(channelInput.trim()))
                            }
                        },
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                TuiKey(
                    label = "[ logout ]",
                    onClick = { dispatch(DmtAction.TelegramLogout) },
                )
            }
            authStep.startsWith("error") -> {
                Text(
                    text = authStep.removePrefix("error: "),
                    style = MaterialTheme.typography.labelSmall,
                    color = p.accent,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                TuiKey(
                    label = "[ try again ]",
                    onClick = { dispatch(DmtAction.Show(DmtView.SOURCES)) },
                )
            }
        }

        if (showMissing) {
            Text(
                text = stringResource(R.string.source_login_missing),
                style = MaterialTheme.typography.labelSmall,
                color = p.accent,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        state.error?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.labelSmall,
                color = p.accent,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun JellyfinLoginPane(mode: SourceMode, dispatch: (DmtAction) -> Unit) {
    val p = LocalTuiColors.current
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showMissing by remember { mutableStateOf(false) }

    val submitLogin = {
        dispatch(DmtAction.SourceLogin(mode, url.trim(), username.trim(), password))
    }

    Column {
        Caption(stringResource(R.string.source_login_title, mode.label))

        LoginField(
            label = stringResource(R.string.source_url_label),
            value = url,
            hint = stringResource(R.string.source_url_hint),
            onValueChange = { url = it },
        )
        LoginField(
            label = stringResource(R.string.source_user_label),
            value = username,
            hint = stringResource(R.string.source_user_hint),
            onValueChange = { username = it },
        )
        LoginField(
            label = stringResource(R.string.source_pass_label),
            value = password,
            hint = stringResource(R.string.source_pass_hint),
            onValueChange = { password = it },
            keyboardType = KeyboardType.Password,
            mask = true,
        )

        Row(modifier = Modifier.padding(top = 18.dp)) {
            TuiKey(
                label = "[ ${stringResource(R.string.source_login_cancel)} ]",
                onClick = { dispatch(DmtAction.Show(DmtView.SOURCES)) },
            )
            Spacer(modifier = Modifier.width(8.dp))
            TuiKey(
                label = "[ ${stringResource(R.string.source_connect)} ]",
                bright = true,
                onClick = {
                    if (url.isBlank() || username.isBlank()) {
                        showMissing = true
                    } else {
                        showMissing = false
                        submitLogin()
                    }
                },
            )
        }

        if (showMissing) {
            Text(
                text = stringResource(R.string.source_login_missing),
                style = MaterialTheme.typography.labelSmall,
                color = p.accent,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun LoginField(
    label: String,
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    mask: Boolean = false,
) {
    val p = LocalTuiColors.current
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
        ) {
            Text(
                text = label.padEnd(6),
                style = MaterialTheme.typography.labelMedium,
                color = p.dim,
            )
            Text(
                text = " > ",
                style = MaterialTheme.typography.bodyLarge,
                color = p.accent,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = p.fg,
                    fontFeatureSettings = if (mask) "calt off" else null,
                ),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = if (mask) {
                    PasswordVisualTransformation('*')
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = p.dim.copy(alpha = 0.55f),
                        )
                    }
                    inner()
                },
            )
        }
        HorizontalDivider(color = p.line)
    }
}
