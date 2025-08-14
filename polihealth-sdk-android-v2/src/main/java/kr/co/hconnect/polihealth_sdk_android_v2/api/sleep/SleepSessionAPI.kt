package kr.co.hconnect.polihealth_sdk_android_v2.api.sleep

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import kotlinx.coroutines.runBlocking
import kr.co.hconnect.polihealth_sdk_android_v2.DateUtil
import kr.co.hconnect.polihealth_sdk_android_v2.PoliClient
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.SleepEndResponse
import kr.co.hconnect.polihealth_sdk_android.api.dto.response.toSleepEndResponse
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.request.RequestBody
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.SleepResponse
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.toSleepResponse

object SleepSessionAPI {

    /**
     * TODO: 수면 시작 요청 API
     *
     * @return SleepStartResponse (sessionId)
     */
    suspend fun requestSleepStart(context: Context): SleepResponse {
        val requestBody = RequestBody(
            reqDate = DateUtil.getCurrentDateTime(),
            userSno = PoliClient.userSno,
        )
        val response: SleepResponse =
            PoliClient.client.post("/poli/sleep/start") {
                contentType(ContentType.Application.Json)

                setBody(requestBody)
            }
                .call.attributes[AttributeKey("body")].toString()
                .toSleepResponse()

        PoliClient.sessionId = response.data?.sessionId ?: ""

        // SleepSessionId 를 sharedPreference에 저장 함.
        // 앱 종료 후, 백그라운드에서도 계속 사용하기 위함
        saveSleepSessionId(context, PoliClient.sessionId)

        Log.d("SleepSessionAPI", "userSno: $PoliClient.userSno")
        Log.d("SleepSessionAPI", "sessionId: $PoliClient.sessionId")

        return response
    }

    /**
     * SleepSessionId를 SharedPreferences에 저장하는 함수
     */
    private fun saveSleepSessionId(context: Context, sessionId: String) {
        try {
            val sharedPreferences =
                context.getSharedPreferences("sleep_session", Context.MODE_PRIVATE)

            sharedPreferences.edit().apply {
                putString("sleep_session_id", sessionId)
                putLong("save_timestamp", System.currentTimeMillis()) // 저장 시간도 함께 저장
                apply() // 비동기 저장
            }

            Log.d("SleepSessionAPI", "SleepSessionId saved to SharedPreferences: $sessionId")
        } catch (e: Exception) {
            Log.e("SleepSessionAPI", "Failed to save SleepSessionId to SharedPreferences", e)
        }
    }


    /**
     * SharedPreferences에서 SleepSessionId를 불러오는 함수
     */
    fun getSleepSessionId(context: Context): String? {
        return try {
            val sharedPreferences =
                context.getSharedPreferences("sleep_session", Context.MODE_PRIVATE)
            val sessionId = sharedPreferences.getString("sleep_session_id", null)
            val saveTimestamp = sharedPreferences.getLong("save_timestamp", 0L)

            Log.d(
                "SleepSessionAPI",
                "SleepSessionId loaded from SharedPreferences: $sessionId (saved at: $saveTimestamp)"
            )
            sessionId
        } catch (e: Exception) {
            Log.e("SleepSessionAPI", "Failed to load SleepSessionId from SharedPreferences", e)
            null
        }
    }

    /**
     * TODO: 수면 종료 요청 API
     *
     * @return SleepEndResponse (sleepQuality)
     */
    suspend fun requestSleepEnd(context: Context): SleepEndResponse {
        val requestBody = RequestBody(
            reqDate = DateUtil.getCurrentDateTime(),
            userSno = PoliClient.userSno,
            sessionId = getSleepSessionId(context = context)
        )

        val response: SleepEndResponse =
            PoliClient.client.post("/poli/sleep/stop") { setBody(requestBody) }
                .call.attributes[AttributeKey("body")].toString()
                .toSleepEndResponse()

        return response
    }
}