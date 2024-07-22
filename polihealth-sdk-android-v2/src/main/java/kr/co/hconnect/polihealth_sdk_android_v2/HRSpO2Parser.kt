package kr.co.hconnect.polihealth_sdk_android

import kr.co.hconnect.polihealth_sdk_android.api.dto.request.HRSpO2

object HRSpO2Parser {
    // 헥사값을 ASCII로 변환하는 함수
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
        if (parts.size == 3) {
            val heartRate = parts[1].toInt()
            val spo2 = parts[2].toInt()
            return HRSpO2(heartRate, spo2)
        } else {
            throw IllegalArgumentException("Invalid ASCII string format")
        }
    }
}