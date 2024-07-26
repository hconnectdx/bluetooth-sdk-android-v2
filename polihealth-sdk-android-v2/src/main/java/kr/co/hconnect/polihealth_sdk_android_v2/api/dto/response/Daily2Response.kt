package kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response

import kr.co.hconnect.polihealth_sdk_android.api.dto.response.BaseResponse
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.PoliResponse
import org.json.JSONException
import org.json.JSONObject

data class Daily2Response(
    var data: Data? = null
) : BaseResponse(), PoliResponse {
    data class Data(
        val userSystolic: Int,
        val userDiastolic: Int,
        val userStress: Int,
        val userHighGlucose: Int,
    )
}

fun String.toDaily2Response(): Daily2Response {
    val jsonObject = JSONObject(this)

    val retCd = jsonObject.optString("retCd")
    val retMsg = jsonObject.optString("retMsg")
    val resDate = jsonObject.optString("resDate")

    try {
        val dataObject: JSONObject? = jsonObject.getJSONObject("data")
        dataObject?.let {
            val userSystolic = it.getInt("userSystolic")
            val userDiastolic = it.getInt("userDiastolic")
            val userStress = it.getInt("userStress")
            val userHighGlucose = it.getInt("userHighGlucose")

            val data = Daily2Response.Data(
                userSystolic = userSystolic,
                userDiastolic = userDiastolic,
                userStress = userStress,
                userHighGlucose = userHighGlucose
            )
            return Daily2Response(data).apply {
                this.retCd = retCd
                this.retMsg = retMsg
                this.resDate = resDate
            }
        }
            ?: return Daily2Response(null).apply {
                this.retCd = retCd
                this.retMsg = retMsg
                this.resDate = resDate
            }
    } catch (e: JSONException) {
        return Daily2Response(null).apply {
            this.retCd = retCd
            this.retMsg = retMsg
            this.resDate = resDate
        }
    }
}