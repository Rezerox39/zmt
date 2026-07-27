package dev.jyotiraditya.dmt.data.remote.telegram

data class TelegramAudioMessage(
    val messageId: Long,
    val fileId: Long,
    val fileUniqueId: String,
    val title: String,
    val performer: String,
    val durationMs: Long,
    val mimeType: String,
    val fileSize: Long,
    val thumbnailFileId: Long?,
    val date: Long,
)

sealed class TelegramAuthStep {
    data object NeedPhoneNumber : TelegramAuthStep()
    data object NeedCode : TelegramAuthStep()
    data object NeedPassword : TelegramAuthStep()
    data object LoggedIn : TelegramAuthStep()
    data class Error(val message: String) : TelegramAuthStep()
}

data class TelegramAuthState(
    val step: TelegramAuthStep = TelegramAuthStep.NeedPhoneNumber,
    val phoneNumber: String = "",
    val codeHashId: String = "",
)

data class TelegramChannelInfo(
    val id: Long,
    val title: String,
    val username: String?,
)
