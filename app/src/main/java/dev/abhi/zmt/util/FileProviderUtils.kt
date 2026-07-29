package dev.abhi.zmt.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object FileProviderUtils {
    private const val AUTHORITY = "dev.abhi.zmt.fileprovider"

    fun getShareUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, AUTHORITY, file)
    }
}
