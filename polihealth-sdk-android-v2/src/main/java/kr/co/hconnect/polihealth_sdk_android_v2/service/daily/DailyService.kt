package kr.co.hconnect.polihealth_sdk_android_app.service.sleep

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kr.co.hconnect.polihealth_sdk_android.DateUtil
import kr.co.hconnect.polihealth_sdk_android.api.daily.DailyProtocol01API
import kr.co.hconnect.polihealth_sdk_android.api.daily.DailyProtocol03API
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.BaseResponse
import kr.co.hconnect.polihealth_sdk_android_app.api.dto.request.LTMModel
import kr.co.hconnect.polihealth_sdk_android_app.api.sleep.DailyProtocol02API
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.model.HRSpO2
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.Protocol2Response

class DailyApiService {
    private val TAG = "DailyApiService"

    /**
     * TODO: Protocol01 전송
     *
     * @param ltmModel
     * @return SleepCommResponse
     */
    suspend fun sendProtocol01(ltmModel: LTMModel): BaseResponse {
        val response: BaseResponse = DailyProtocol01API.requestPost(
            DateUtil.getCurrentDateTime(),
            ltmModel
        )
        return response
    }

    /**
     * TODO: Protocol02 전송
     *
     * @param context : 전송 시, bin 파일을 저장하기 위한 컨텍스트. null일 경우, bin 파일 저장 X
     * @return SleepCommResponse
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun sendProtocol02(context: Context? = null): Protocol2Response? {
        val protocol2Bytes = DailyProtocol02API.flush(context)
        if (protocol2Bytes.isNotEmpty()) {
            val response: Protocol2Response =
                DailyProtocol02API.requestPost(
                    DateUtil.getCurrentDateTime(),
                    protocol2Bytes
                )
            return response
        } else {
            return null
        }
    }

    /**
     * TODO: Protocol03 전송
     *
     * @param hrSpo2
     * @return SleepCommResponse
     */
    suspend fun sendProtocol03(hrSpo2: HRSpO2): BaseResponse {
        val response: BaseResponse = DailyProtocol03API.requestPost(
            DateUtil.getCurrentDateTime(),
            hrSpo2
        )
        return response
    }
}