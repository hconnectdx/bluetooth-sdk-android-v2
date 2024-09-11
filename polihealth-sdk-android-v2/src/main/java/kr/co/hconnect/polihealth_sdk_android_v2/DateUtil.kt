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

    // 주어진 날짜 문자열에 대해 1분씩 더하거나 빼는 함수
    fun adjustDateTime(dateTimeStr: String, plusMin: Long = 0, minusMin: Long = 0): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // API 26 이상에서는 LocalDateTime 사용
            val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            val dateTime = LocalDateTime.parse(dateTimeStr, formatter)
            // 분 조정
            val adjustedDateTime = dateTime.minusMinutes(minusMin).plusMinutes(plusMin)
            // 다시 문자열로 변환
            adjustedDateTime.format(formatter)
        } else {
            // API 26 미만에서는 Calendar 및 SimpleDateFormat 사용
            val formatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
            val date =
                formatter.parse(dateTimeStr) ?: throw IllegalArgumentException("잘못된 날짜 형식입니다.")
            val calendar = Calendar.getInstance()
            calendar.time = date
            // 분 조정
            calendar.add(Calendar.MINUTE, plusMin.toInt() - minusMin.toInt())
            // 다시 문자열로 변환
            formatter.format(calendar.time)
        }
    }
}