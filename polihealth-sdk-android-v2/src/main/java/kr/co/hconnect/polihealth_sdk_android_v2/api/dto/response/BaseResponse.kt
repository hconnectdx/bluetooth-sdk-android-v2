package kr.co.hconnect.polihealth_sdk_android.api.dto.response

import org.json.JSONException
import org.json.JSONObject

interface PoliResponse
open class BaseResponse(
    var retCd: String? = null,
    var retMsg: String? = null,
    var resDate: String? = null
) : PoliResponse

fun String.toBaseResponse(): BaseResponse {
    val jsonObject = JSONObject(this)

    val retCd = jsonObject.optString("retCd")
    val retMsg = jsonObject.optString("retMsg")
    val resDate = jsonObject.optString("resDate")

    try {
        val dataObject: JSONObject? = jsonObject.getJSONObject("data")
        dataObject?.let {
            return BaseResponse().apply {
                this.retCd = retCd
                this.retMsg = retMsg
                this.resDate = resDate
            }
        }
            ?: return BaseResponse().apply {
                this.retCd = retCd
                this.retMsg = retMsg
                this.resDate = resDate
            }
    } catch (e: JSONException) {
        return BaseResponse().apply {
            this.retCd = retCd
            this.retMsg = retMsg
            this.resDate = resDate
        }
    }
}