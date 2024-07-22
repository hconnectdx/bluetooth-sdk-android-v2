package kr.co.hconnect.polihealth_sdk_android.api.dto.response

import org.json.JSONException
import org.json.JSONObject

sealed class SleepResponse : BaseResponse() {

    data class SleepCommResponse(
        val data: Data?
    ) : SleepResponse() {
        data class Data(
            val sessionId: String
        )
    }

    data class SleepResultResponse(
        val data: Data?
    ) : SleepResponse() {
        data class Data(
            val sleepQuality: String
        )
    }
}

fun String.toSleepCommResponse(): SleepResponse.SleepCommResponse {
    val jsonObject = JSONObject(this)

    val retCd = jsonObject.optString("retCd")
    val retMsg = jsonObject.optString("retMsg")
    val resDate = jsonObject.optString("resDate")

    try {
        val dataObject: JSONObject? = jsonObject.getJSONObject("data")
        dataObject?.let {
            val sessionId = it.getString("sessionId")
            val data = SleepResponse.SleepCommResponse.Data(sessionId = sessionId)
            return SleepResponse.SleepCommResponse(data).apply {
                this.retCd = retCd
                this.retMsg = retMsg
                this.resDate = resDate
            }
        }
            ?: return SleepResponse.SleepCommResponse(null).apply {
                this.retCd = retCd
                this.retMsg = retMsg
                this.resDate = resDate
            }
    } catch (e: JSONException) {
        return SleepResponse.SleepCommResponse(null).apply {
            this.retCd = retCd
            this.retMsg = retMsg
            this.resDate = resDate
        }
    }
}

fun String.toSleepEndResponse(): SleepResponse.SleepResultResponse {
    val jsonObject = JSONObject(this)

    val retCd = jsonObject.optString("retCd")
    val retMsg = jsonObject.optString("retMsg")
    val resDate = jsonObject.optString("resDate")

    try {
        val dataObject: JSONObject? = jsonObject.getJSONObject("data")
        dataObject?.let {
            val sleepQuality = it.getString("sleepQuality")
            val data = SleepResponse.SleepResultResponse.Data(sleepQuality = sleepQuality)
            return SleepResponse.SleepResultResponse(data).apply {
                this.retCd = retCd
                this.retMsg = retMsg
                this.resDate = resDate
            }
        }
            ?: return SleepResponse.SleepResultResponse(null).apply {
                this.retCd = retCd
                this.retMsg = retMsg
                this.resDate = resDate
            }
    } catch (e: JSONException) {
        return SleepResponse.SleepResultResponse(null).apply {
            this.retCd = retCd
            this.retMsg = retMsg
            this.resDate = resDate
        }
    }
}