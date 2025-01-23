package kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response

import org.json.JSONException
import org.json.JSONObject

data class SleepResponse(
    var data: Data? = null
) : BaseResponse(), PoliResponse {
    data class Data(
        val sessionId: String
    )
}

fun String.toSleepResponse(): SleepResponse {
    val jsonObject = JSONObject(this)

    val retCd = jsonObject.optString("retCd")
    val retMsg = jsonObject.optString("retMsg")
    val resDate = jsonObject.optString("resDate")

    try {
        val dataObject: JSONObject? = jsonObject.getJSONObject("data")
        dataObject?.let {
            val sessionId = it.getString("sessionId")

            val data = SleepResponse.Data(
                sessionId = sessionId,
            )
            return SleepResponse(data).apply {
                this.retCd = retCd
                this.retMsg = retMsg
                this.resDate = resDate
            }
        }
            ?: return SleepResponse(null).apply {
                this.retCd = retCd
                this.retMsg = retMsg
                this.resDate = resDate
            }
    } catch (e: JSONException) {
        return SleepResponse(null).apply {
            this.retCd = retCd
            this.retMsg = retMsg
            this.resDate = resDate
        }
    }
}