package kr.co.hconnect.polihealth_sdk_android.api.dto.request

import kotlinx.serialization.Serializable
import kr.co.hconnect.polihealth_sdk_android_app.api.dto.request.LTMModel

@Serializable
data class LTMRequest(
    val reqDate: String,
    val userSno: Int,
    val data: LTMModel
)