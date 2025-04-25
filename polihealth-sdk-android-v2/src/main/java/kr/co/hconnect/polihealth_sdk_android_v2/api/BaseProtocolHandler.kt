package kr.co.hconnect.polihealth_sdk_android_v2.api

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import kr.co.hconnect.polihealth_sdk_android_v2.DateUtil
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

open class BaseProtocolHandler {
    var _byteArray: ByteArray = byteArrayOf()

    // addByte 함수: 바이트 배열을 추가
    fun addByte(byteArray: ByteArray) {
        _byteArray += byteArray // 기존의 _byteArray에 새로운 byteArray를 추가
    }

    // flush 함수: 데이터를 반환하고 _byteArray를 비움

    fun flush(context: Context?): ByteArray {

        if (_byteArray.isEmpty()) {
            return byteArrayOf()
        }

        val tempByteArray = _byteArray.clone() // 현재 _byteArray를 클론

        _byteArray = byteArrayOf()
        context?.let {
            saveToFile(
                it,
                tempByteArray,
                "protocol ${DateUtil.getCurrentDateTime()}.bin"
            )
        } // 클론한 데이터를 파일로 저장

        return tempByteArray // 클론한 데이터를 반환
    }

    // 바이트 배열을 16진수 문자열로 변환하는 헬퍼 함수
    fun ByteArray.toHexString(): String =
        joinToString(separator = " ") { byte -> "%02x".format(byte) }

    // flush 결과를 파일로 저장하는 함수
    private fun saveToFile(context: Context, data: ByteArray?, fileName: String) {
        data?.let {
            try {
                // Android 10(Q) 이상에서는 새로운 MediaStore API 사용
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveToFileUsingMediaStore(context, it, fileName)
                }
                // Android 9(P) 이하에서는 직접 파일 시스템에 접근
                else {
                    saveToFileUsingLegacyMethod(context, it, fileName)
                }
            } catch (e: Exception) {
                Log.e("ByteController", "Error saving data to file", e)
            }
        } ?: run {
            Log.d("ByteController", "No data to save")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveToFileUsingMediaStore(context: Context, data: ByteArray, fileName: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/poli_log")
        }

        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            contentValues
        )
        val outputStream = uri?.let { context.contentResolver.openOutputStream(it) }

        if (outputStream != null) {
            outputStream.write(data)
            outputStream.close()
            Log.d("ByteController", "Data saved to file: $fileName in Download folder (MediaStore)")
        } else {
            Log.e("ByteController", "Failed to create OutputStream")
        }
    }

    private fun saveToFileUsingLegacyMethod(context: Context, data: ByteArray, fileName: String) {
        // 외부 저장소의 다운로드 디렉토리 경로
        val downloadsDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val poliLogDir = File(downloadsDir, "poli_log")

        // poli_log 디렉토리가 없으면 생성
        if (!poliLogDir.exists()) {
            poliLogDir.mkdirs()
        }

        val file = File(poliLogDir, fileName)
        val outputStream = FileOutputStream(file)
        outputStream.write(data)
        outputStream.close()

        Log.d("ByteController", "Data saved to file: ${file.absolutePath} (Legacy method)")
    }
}
