package kr.co.hconnect.polihealth_sdk_android.service.sleep

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kr.co.hconnect.polihealth_sdk_android.DateUtil
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.SleepEndResponse
import kr.co.hconnect.polihealth_sdk_android.api.sleep.SleepProtocol06API
import kr.co.hconnect.polihealth_sdk_android.api.sleep.SleepProtocol07API
import kr.co.hconnect.polihealth_sdk_android.api.sleep.SleepProtocol08API
import kr.co.hconnect.polihealth_sdk_android.api.sleep.SleepProtocol09API
import kr.co.hconnect.polihealth_sdk_android.api.sleep.SleepSessionAPI
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.model.HRSpO2
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.SleepResponse

class SleepApiService {
    private val TAG = "SleepApiService"

    suspend fun sendStartSleep(): SleepResponse {
        return SleepSessionAPI.requestSleepStart()
    }

    suspend fun sendEndSleep(): SleepEndResponse {
        return SleepSessionAPI.requestSleepEnd()
    }

    /**
     * TODO: Protocol06 전송
     *
     * @param context : 전송 시, bin 파일을 저장하기 위한 컨텍스트. null일 경우, bin 파일 저장 X
     * @return SleepCommResponse
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun sendProtocol06(context: Context? = null): SleepResponse? {
        try {
            val protocol6Bytes = SleepProtocol06API.flush(context)
            if (protocol6Bytes.isNotEmpty()) {
                val response: SleepResponse =
                    SleepProtocol06API.requestPost(
                        DateUtil.getCurrentDateTime(),
                        protocol6Bytes
                    )
                return response
            } else {
                return null
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null

    }

    /**
     * TODO: Protocol07 전송
     *
     * @param context : 전송 시, bin 파일을 저장하기 위한 컨텍스트. null일 경우, bin 파일 저장 X
     * @return SleepCommResponse
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun sendProtocol07(context: Context?): SleepResponse? {
        val protocol7Bytes = SleepProtocol07API.flush(context)
        if (protocol7Bytes.isNotEmpty()) {
            val response: SleepResponse =
                SleepProtocol07API.requestPost(
                    DateUtil.getCurrentDateTime(),
                    protocol7Bytes
                )
            return response
        } else {
            return null
        }
    }

    /**
     * TODO: Protocol08 전송
     *
     * @param context : 전송 시, bin 파일을 저장하기 위한 컨텍스트. null일 경우, bin 파일 저장 X
     * @return SleepCommResponse
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun sendProtocol08(context: Context? = null): SleepResponse? {
        val protocol8Bytes = SleepProtocol08API.flush(context)
        if (protocol8Bytes.isNotEmpty()) {
            val response: SleepResponse =
                SleepProtocol08API.requestPost(
                    DateUtil.getCurrentDateTime(),
                    protocol8Bytes
                )
            return response
        } else {
            return null
        }
    }

    /**
     * TODO: Protocol09 전송
     *
     * @param hrSpo2
     * @return SleepCommResponse
     */
    suspend fun sendProtocol09(hrSpo2: HRSpO2): SleepResponse {
        val response: SleepResponse = SleepProtocol09API.requestPost(
            DateUtil.getCurrentDateTime(),
            hrSpo2
        )
        return response
    }
}