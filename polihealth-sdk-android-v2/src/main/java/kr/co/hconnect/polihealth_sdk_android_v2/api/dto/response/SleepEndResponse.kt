package kr.co.hconnect.polihealth_sdk_android.api.dto.response

import org.json.JSONException
import org.json.JSONObject

data class SleepEndResponse(
    val data: Data? = null
) : BaseResponse(), PoliResponse {

    data class Data(
        val sleepQuality: Int
    )
}

fun String.toSleepEndResponse(): SleepEndResponse {
    val jsonObject = JSONObject(this)

    val retCd = jsonObject.optString("retCd")
    val retMsg = jsonObject.optString("retMsg")
    val resDate = jsonObject.optString("resDate")

    try {
        val dataObject: JSONObject? = jsonObject.getJSONObject("data")
        dataObject?.let {
            val sleepQuality = it.getInt("sleepQuality")
            val data = SleepEndResponse.Data(sleepQuality = sleepQuality)
            return SleepEndResponse(data).apply {
                this.retCd = retCd
                this.retMsg = retMsg
                this.resDate = resDate
            }
        }
            ?: return SleepEndResponse(null).apply {
                this.retCd = retCd
                this.retMsg = retMsg
                this.resDate = resDate
            }
    } catch (e: JSONException) {
        return SleepEndResponse(null).apply {
            this.retCd = retCd
            this.retMsg = retMsg
            this.resDate = resDate
        }
    }
}