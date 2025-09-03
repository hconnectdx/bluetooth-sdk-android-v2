package kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response

import org.json.JSONException
import org.json.JSONObject

data class Daily2Response(
    var data: Data? = null
) : BaseResponse(), PoliResponse {
    data class Data(
        val userSystolic: Int?,      // Int? 로 변경
        val userDiastolic: Int?,     // Int? 로 변경
        val userStress: Int?,        // Int? 로 변경
        val userHighGlucose: Int?,   // Int? 로 변경
    )
}

fun String.toDaily2Response(): Daily2Response {
    val jsonObject = JSONObject(this)

    val retCd = jsonObject.optString("retCd")
    val retMsg = jsonObject.optString("retMsg")
    val resDate = jsonObject.optString("resDate")

    try {
        val dataObject: JSONObject? = jsonObject.optJSONObject("data")
        dataObject?.let {
            // null 체크 후 값 추출
            val userSystolic = if (it.isNull("userSystolic")) null else it.optInt("userSystolic")
            val userDiastolic = if (it.isNull("userDiastolic")) null else it.optInt("userDiastolic")
            val userStress = if (it.isNull("userStress")) null else it.optInt("userStress")
            val userHighGlucose =
                if (it.isNull("userHighGlucose")) null else it.optInt("userHighGlucose")

            val data = Daily2Response.Data(
                userSystolic = userSystolic,        // null 허용
                userDiastolic = userDiastolic,      // null 허용
                userStress = userStress,            // null 허용
                userHighGlucose = userHighGlucose   // null 허용
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