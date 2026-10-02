package kr.co.hconnect.polihealth_sdk_android_v2

import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.model.HRSpO2

object HRSpO2Parser {
    // 헥사값을 ASCII로 변환하는 함수
    fun hexToAscii(byteArray: ByteArray): String {
        val output = StringBuilder()
        for (byte in byteArray) {
            val hex = String.format("%02x", byte)
            val decimal = hex.toInt(16)
            output.append(decimal.toChar())
        }
        return output.toString()
    }


    // ByteArray를 받아서 ASCII 문자열로 변환하고, HRSpO2 객체를 생성하는 함수
    fun asciiToHRSpO2(byteArray: ByteArray): HRSpO2 {
        val ascii = hexToAscii(byteArray)
        val parts = ascii.split(":", ",")
        // 공백/널 문자 등이 섞여 와도 숫자만 취한다. 형식이 다르면 원본을 메시지에 남겨 원인 추적이 가능하게 한다.
        val heartRate = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull()
        val spo2 = parts.getOrNull(2)?.filter { it.isDigit() }?.toIntOrNull()
        if (parts.size != 3 || heartRate == null || spo2 == null) {
            val hex = byteArray.joinToString(" ") { String.format("%02X", it) }
            throw IllegalArgumentException("Invalid ASCII string format: \"$ascii\" [$hex]")
        }
        return HRSpO2(heartRate, spo2)
    }
}