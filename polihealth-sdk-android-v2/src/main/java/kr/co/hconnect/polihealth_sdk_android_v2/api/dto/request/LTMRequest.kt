package kr.co.hconnect.polihealth_sdk_android.api.dto.request

import kotlinx.serialization.Serializable
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.model.LTMModel

@Serializable
data class LTMRequest(
    val reqDate: String,
    val userSno: Int,
    val data: LTMModel
)