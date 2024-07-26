package kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response

import kr.co.hconnect.polihealth_sdk_android.api.dto.response.BaseResponse
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.PoliResponse
import kr.co.hconnect.polihealth_sdk_android_app.api.dto.request.LTMModel
import org.json.JSONException
import org.json.JSONObject

data class Daily1Response(
    var ltmModel: LTMModel? = null
) : BaseResponse(), PoliResponse

fun String.toDaily1Response(ltmData: LTMModel): Daily1Response {
    val jsonObject = JSONObject(this)

    val retCd = jsonObject.optString("retCd")
    val retMsg = jsonObject.optString("retMsg")
    val resDate = jsonObject.optString("resDate")

    try {
        return Daily1Response(ltmData).apply {
            this.retCd = retCd
            this.retMsg = retMsg
            this.resDate = resDate
        }


    } catch (e: JSONException) {
        return Daily1Response(null).apply {
            this.retCd = retCd
            this.retMsg = retMsg
            this.resDate = resDate
        }
    }
}