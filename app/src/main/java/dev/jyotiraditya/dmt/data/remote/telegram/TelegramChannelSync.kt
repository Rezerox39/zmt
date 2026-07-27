package dev.jyotiraditya.dmt.data.remote.telegram

import android.net.Uri
import dev.jyotiraditya.dmt.domain.model.Track
import dev.jyotiraditya.dmt.domain.model.TrackSource
import javax.inject.Inject
import javax.inject.Singleton

private const val SCHEME_TG = "tg"
private const val AUTHORITY_AUDIO = "audio"
private const val SYNC_BATCH_SIZE = 100
private const val MAX_MESSAGES = 5000

@Singleton
class TelegramChannelSync @Inject constructor(
    private val client: TelegramClient,
) {

    suspend fun scanChannel(channelId: Long): List<Track> {
        val allMessages = mutableListOf<TelegramAudioMessage>()
        var fromMessageId: Long = 0
        var totalFetched = 0

        while (totalFetched < MAX_MESSAGES) {
            val batch = client.getChannelAudioMessages(
                channelId = channelId,
                limit = SYNC_BATCH_SIZE,
                fromMessageId = fromMessageId,
            )

            if (batch.isEmpty()) break

            allMessages.addAll(batch)
            totalFetched += batch.size

            fromMessageId = batch.last().messageId
        }

        return allMessages.map { it.toTrack(channelId) }
    }

    private fun TelegramAudioMessage.toTrack(channelId: Long): Track {
        val tgUri = Uri.Builder()
            .scheme(SCHEME_TG)
            .authority(AUTHORITY_AUDIO)
            .appendPath(fileId.toString())
            .build()

        return Track(
            id = messageId,
            uri = tgUri,
            title = title,
            artist = performer,
            album = "Telegram",
            path = "tg://channel/$channelId/$messageId",
            durationMs = durationMs,
            mime = mimeType,
            bitrate = if (fileSize > 0 && durationMs > 0) {
                ((fileSize * 8) / (durationMs / 1000)).toInt()
            } else {
                0
            },
            size = fileSize,
            trackNumber = 0,
            dateAdded = date,
            dateModified = date,
            coverUri = thumbnailFileId?.let { thumbId ->
                Uri.Builder()
                    .scheme(SCHEME_TG)
                    .authority("thumbnail")
                    .appendPath(thumbId.toString())
                    .build()
            },
            source = TrackSource.TELEGRAM,
            remoteId = fileId.toString(),
        )
    }

    suspend fun resolveThumbnail(thumbnailFileId: Long): ByteArray? {
        return try {
            client.downloadFile(thumbnailFileId, priority = 1)
            client.readRemoteFile(thumbnailFileId, 0, 1024 * 1024)
        } catch (_: Exception) {
            null
        }
    }
}
