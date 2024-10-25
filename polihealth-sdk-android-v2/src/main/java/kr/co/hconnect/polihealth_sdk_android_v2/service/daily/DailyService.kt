package kr.co.hconnect.polihealth_sdk_android_app.service.sleep

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kr.co.hconnect.polihealth_sdk_android.DateUtil
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.DailyProtocol01API
import kr.co.hconnect.polihealth_sdk_android.api.daily.DailyProtocol03API
import kr.co.hconnect.polihealth_sdk_android_v2.BuildConfig
import kr.co.hconnect.polihealth_sdk_android_v2.api.SaveUtil
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.model.LTMModel
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.DailyProtocol02API
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.model.HRSpO2
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.Daily1Response
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.Daily2Response
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.Daily3Response

class DailyApiService {
    private val TAG = "DailyApiService"

    /**
     * TODO: Protocol01 전송
     *
     * @param ltmModel
     * @return SleepCommResponse
     */
    suspend fun sendProtocol01(ltmModel: LTMModel): Daily1Response {
        return DailyProtocol01API.requestPost(
            DateUtil.getCurrentDateTime(),
            ltmModel
        )
    }

    suspend fun sendProtocol01New(context: Context? = null): Daily1Response {

        if (BuildConfig.IS_DEBUG) {
            context?.let {
                // bin 파일 저장
                SaveUtil.saveToBinFile(
                    context,
                    DailyProtocol01API.byteArray,
                    "bin_protocol01_${DateUtil.getCurrentDateTime()}.bin"
                )
                // txt 파일 저장
                SaveUtil.saveStringToFile(
                    context,
                    DailyProtocol01API.ltmModel.toString(),
                    "txt_protocol01${DateUtil.getCurrentDateTime()}.txt"
                )
            }
        }

        return try {
            DailyProtocol01API.requestPost(
                DateUtil.getCurrentDateTime(),
                DailyProtocol01API.ltmModel ?: LTMModel(
                    arrayOf(),
                    arrayOf(),
                    arrayOf()
                ) // 내일 바로 테스트
            )
        } catch (e: Exception) {
            Log.e(TAG, "sendProtocol01New: ${e.message}")
            return Daily1Response().apply {
                retCd = "500"
                retMsg = e.message ?: "Unknown error"
                resDate = DateUtil.getCurrentDateTime()
            }
        }
    }

    /**
     * TODO: Protocol02 전송
     *
     * @param context : 전송 시, bin 파일을 저장하기 위한 컨텍스트. null일 경우, bin 파일 저장 X
     * @return SleepCommResponse
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun sendProtocol02(context: Context? = null): Daily2Response {
        val protocol2Bytes = DailyProtocol02API.flush(context)
        if (protocol2Bytes.isNotEmpty()) {

            return DailyProtocol02API.requestPost(
                DateUtil.getCurrentDateTime(),
                protocol2Bytes
            )
        } else {
            throw Exception("Protocol02 bytes is empty")
        }
    }

    /**
     * TODO: Protocol03 전송
     *
     * @param hrSpo2
     * @return SleepCommResponse
     */
    suspend fun sendProtocol03(hrSpo2: HRSpO2): Daily3Response {
        return DailyProtocol03API.requestPost(
            DateUtil.getCurrentDateTime(),
            hrSpo2
        )
    }
}