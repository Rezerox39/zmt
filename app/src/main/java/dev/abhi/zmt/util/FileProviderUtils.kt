package dev.abhi.zmt.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object FileProviderUtils {

    fun getShareUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }
}
