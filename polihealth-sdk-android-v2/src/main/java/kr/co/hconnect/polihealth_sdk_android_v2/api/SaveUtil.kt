package kr.co.hconnect.polihealth_sdk_android_v2.api

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import kr.co.hconnect.polihealth_sdk_android_v2.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object SaveUtil {

    // 2024-10-25(금)
    // 밴드가 전시회장에 있어서 잘 저장 되는지 테스트 못해봄
    // 자세히 해봐야 함.
    fun saveStringToFile(context: Context, data: String?, fileName: String) {

        if (!BuildConfig.IS_DEBUG) return

        if (data == null) {
            Log.d("StringController", "No data to save")
            return
        }

        try {
            val outputStream = createOutputStream(context, fileName)
            outputStream?.use {
                it.write(data.toByteArray())  // String을 ByteArray로 변환하여 저장
                Log.d("StringController", "Data saved to file: $fileName in Download folder")
            } ?: run {
                Log.e("StringController", "Failed to create OutputStream")
            }
        } catch (e: Exception) {
            Log.e("StringController", "Error saving data to file", e)
        }
    }

    private fun createContentValues(fileName: String): ContentValues {
        return ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")  // MIME type 변경
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/poli_log")
        }
    }

    private fun createOutputStream(context: Context, fileName: String): OutputStream? {
        val contentValues = createContentValues(fileName)

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 이상에서는 Downloads 디렉토리에 저장
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        } else {
            // Android 10 이하에서는 Files URI를 사용
            val externalUri = MediaStore.Files.getContentUri("external")
            context.contentResolver.insert(externalUri, contentValues)
        }

        return uri?.let { context.contentResolver.openOutputStream(it) }
    }

    // 2024-10-25(금)
    // 밴드가 전시회장에 있어서 잘 저장 되는지 테스트 못해봄
    // 자세히 해봐야 함.
    fun saveToBinFile(context: Context?, data: ByteArray?, fileName: String) {

        if (!BuildConfig.IS_DEBUG) return

        if (context == null) {
            Log.e("ByteController", "Context is null")
            return
        }

        if (data == null) {
            Log.d("ByteController", "No data to save")
            return
        }

        try {
            val outputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                createOutputStreamForNewerVersions(context, fileName)
            } else {
                createOutputStreamForOlderVersions(fileName)
            }

            outputStream?.use {
                it.write(data)
                Log.d("ByteController", "Data saved to file: $fileName")
            } ?: run {
                Log.e("ByteController", "Failed to create OutputStream")
            }
        } catch (e: Exception) {
            Log.e("ByteController", "Error saving data to file", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createOutputStreamForNewerVersions(
        context: Context,
        fileName: String
    ): OutputStream? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/poli_log")
        }

        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            contentValues
        )
        return uri?.let { context.contentResolver.openOutputStream(it) }
    }

    private fun createOutputStreamForOlderVersions(fileName: String): OutputStream? {
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetFile = File(downloadsDir, "poli_log/$fileName")

        targetFile.parentFile?.let {
            if (!it.exists()) {
                it.mkdirs()
            }
        } ?: Log.e("ByteController", "Failed to create parent directories, parentFile is null")


        return FileOutputStream(targetFile)
    }
}