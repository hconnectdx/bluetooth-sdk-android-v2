package kr.co.hconnect.polihealth_sdk_android.api.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class HRSpO2Request(
    val reqDate: String,
    val userSno: Int,
    val sessionId: String? = null,
    val data: Data
) {
    @Serializable
    data class Data(
        val oxygenVal: Int,
        val heartRateVal: Int
    )
}

data class HRSpO2(
    val heartRate: Int,
    val spo2: Int
)