package kr.co.hconnect.polihealth_sdk_android.service.sleep

import android.content.Context
import kr.co.hconnect.polihealth_sdk_android.DateUtil
import kr.co.hconnect.polihealth_sdk_android.api.dto.request.HRSpO2
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.SleepResponse
import kr.co.hconnect.polihealth_sdk_android.api.sleep.SleepProtocol06API
import kr.co.hconnect.polihealth_sdk_android.api.sleep.SleepProtocol07API
import kr.co.hconnect.polihealth_sdk_android.api.sleep.SleepProtocol08API
import kr.co.hconnect.polihealth_sdk_android.api.sleep.SleepProtocol09API
import kr.co.hconnect.polihealth_sdk_android.api.sleep.SleepSessionAPI

class SleepApiService {
    private val TAG = "SleepApiService"

    suspend fun sendStartSleep(): SleepResponse.SleepCommResponse {
        return SleepSessionAPI.requestSleepStart()
    }

    suspend fun sendEndSleep(): SleepResponse.SleepResultResponse {
        return SleepSessionAPI.requestSleepEnd()
    }

    /**
     * TODO: Protocol06 전송
     *
     * @param context : 전송 시, bin 파일을 저장하기 위한 컨텍스트. null일 경우, bin 파일 저장 X
     * @return SleepCommResponse
     */
    suspend fun sendProtocol06(context: Context? = null): SleepResponse.SleepCommResponse? {
        val protocol6Bytes = SleepProtocol06API.flush(context)
        if (protocol6Bytes.isNotEmpty()) {
            val response: SleepResponse.SleepCommResponse =
                SleepProtocol06API.requestPost(
                    DateUtil.getCurrentDateTime(),
                    protocol6Bytes
                )
            return response
        } else {
            return null
        }
    }

    /**
     * TODO: Protocol07 전송
     *
     * @param context : 전송 시, bin 파일을 저장하기 위한 컨텍스트. null일 경우, bin 파일 저장 X
     * @return SleepCommResponse
     */
    suspend fun sendProtocol07(context: Context?): SleepResponse.SleepCommResponse? {
        val protocol7Bytes = SleepProtocol07API.flush(context)
        if (protocol7Bytes.isNotEmpty()) {
            val response: SleepResponse.SleepCommResponse =
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
    suspend fun sendProtocol08(context: Context? = null): SleepResponse.SleepCommResponse? {
        val protocol8Bytes = SleepProtocol08API.flush(context)
        if (protocol8Bytes.isNotEmpty()) {
            val response: SleepResponse.SleepCommResponse =
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
    suspend fun sendProtocol09(hrSpo2: HRSpO2): SleepResponse.SleepCommResponse {
        val response: SleepResponse.SleepCommResponse = SleepProtocol09API.requestPost(
            DateUtil.getCurrentDateTime(),
            hrSpo2
        )
        return response
    }
}