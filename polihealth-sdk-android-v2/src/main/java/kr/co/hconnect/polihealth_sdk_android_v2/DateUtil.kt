package kr.co.hconnect.polihealth_sdk_android

import android.os.Build
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

object DateUtil {
    fun getCurrentDateTime(plusMin: Long = 0, minusMin: Long = 0): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val current = LocalDateTime.now().minusMinutes(minusMin).plusMinutes(plusMin)
            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            current.format(formatter)
        } else {
            val current = Calendar.getInstance().time
            val formatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
            formatter.format(current)
        }
    }
}