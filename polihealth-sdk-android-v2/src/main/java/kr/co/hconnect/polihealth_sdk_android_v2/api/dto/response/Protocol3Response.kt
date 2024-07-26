package kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response

import kr.co.hconnect.polihealth_sdk_android.api.dto.response.BaseResponse
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.PoliResponse
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.model.HRSpO2
import org.json.JSONException
import org.json.JSONObject

data class Protocol3Response(
    var hrSpO2: HRSpO2? = null
) : BaseResponse(), PoliResponse

fun String.toProtocol3Response(hrSpO2: HRSpO2): Protocol3Response {
    val jsonObject = JSONObject(this)

    val retCd = jsonObject.optString("retCd")
    val retMsg = jsonObject.optString("retMsg")
    val resDate = jsonObject.optString("resDate")

    try {
        return Protocol3Response(hrSpO2).apply {
            this.retCd = retCd
            this.retMsg = retMsg
            this.resDate = resDate
        }


    } catch (e: JSONException) {
        return Protocol3Response(null).apply {
            this.retCd = retCd
            this.retMsg = retMsg
            this.resDate = resDate
        }
    }
}