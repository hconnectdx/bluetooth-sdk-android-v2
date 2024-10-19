package kr.co.hconnect.polihealth_sdk_android_app.service.sleep

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kr.co.hconnect.polihealth_sdk_android.DateUtil
import kr.co.hconnect.polihealth_sdk_android.api.daily.DailyProtocol01API
import kr.co.hconnect.polihealth_sdk_android.api.daily.DailyProtocol03API
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.model.LTMModel
import kr.co.hconnect.polihealth_sdk_android_app.api.sleep.DailyProtocol02API
import kr.co.hconnect.polihealth_sdk_android_v2.BuildConfig
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

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun sendProtocol01New(context: Context? = null): Daily1Response {

        context?.let {
            // bin 파일 저장
            DailyProtocol01API.saveToBinFile(
                context,
                DailyProtocol01API.byteArray,
                "bin_protocol01_${DateUtil.getCurrentDateTime()}.bin"
            )
            // txt 파일 저장
            DailyProtocol01API.saveStringToFile(
                context,
                DailyProtocol01API.ltmModel.toString(),
                "txt_protocol01${DateUtil.getCurrentDateTime()}.txt"
            )
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
    suspend fun sendProtocol02(context: Context? = null): Daily2Response? {
        val protocol2Bytes = DailyProtocol02API.flush(context)
        if (protocol2Bytes.isNotEmpty()) {
            return try {
                val response: Daily2Response =
                    DailyProtocol02API.requestPost(
                        DateUtil.getCurrentDateTime(),
                        protocol2Bytes
                    )
                response
            } catch (e: Exception) {
                Log.e(TAG, "sendProtocol02: ${e.message}")
                return Daily2Response().apply {
                    retCd = "500"
                    retMsg = e.message ?: "Unknown error"
                    resDate = DateUtil.getCurrentDateTime()
                }
            }
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
    suspend fun sendProtocol03(hrSpo2: HRSpO2): Daily3Response {

        return try {
            DailyProtocol03API.requestPost(
                DateUtil.getCurrentDateTime(),
                hrSpo2
            )
        } catch (e: Exception) {
            Log.e(TAG, "sendProtocol03: ${e.message}")
            return Daily3Response().apply {
                retCd = "500"
                retMsg = e.message ?: "Unknown error"
                resDate = DateUtil.getCurrentDateTime()
            }
        }
    }
}