package kr.co.hconnect.polihealth_sdk_android.api.dto.request

import kotlinx.serialization.Serializable

@Serializable
open class BaseRequest(
    open val reqDate: String? = null,
    open val userSno: Int? = null,
    val sessionId: String? = null
)