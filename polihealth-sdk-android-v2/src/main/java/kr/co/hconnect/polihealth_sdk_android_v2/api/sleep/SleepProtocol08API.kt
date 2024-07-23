package kr.co.hconnect.polihealth_sdk_android.api.sleep

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.util.AttributeKey
import io.ktor.util.InternalAPI
import kotlinx.coroutines.runBlocking
import kr.co.hconnect.polihealth_sdk_android.PoliClient
import kr.co.hconnect.polihealth_sdk_android.api.BaseProtocolHandler
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.SleepResponse
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.toSleepResponse

object SleepProtocol08API : BaseProtocolHandler() {
    /**
     * TODO: Protocol06을 서버로 전송하는 API
     *
     * @param reqDate ex) 20240704054513 (yyyyMMddHHmmss)
     * @param byteArray
     * */
    @OptIn(InternalAPI::class)
    suspend fun requestPost(
        reqDate: String,
        byteArray: ByteArray
    ): SleepResponse {
        val response: SleepResponse =
            PoliClient.client.post("poli/sleep/protocol8") {
                body = MultiPartFormDataContent(
                    formData {
                        append("reqDate", reqDate)
                        append("userSno", PoliClient.userSno)
                        append("sessionId", PoliClient.sessionId)
                        append("file", byteArray, Headers.build {
                            append(
                                HttpHeaders.ContentDisposition,
                                "filename=\"\"",
                            )
                        })
                    }
                )
            }.call.attributes[AttributeKey("body")].toString().toSleepResponse()

        return response
    }


    @OptIn(InternalAPI::class)
    @RequiresApi(Build.VERSION_CODES.Q)
    fun testPost(context: Context) = runBlocking {
        try {
            val byteArray = SleepProtocol06API.readBytesFromDownload(context, "protocol08.bin")

            val response: HttpResponse =
                PoliClient.client.post("https://mapi-stg.health-on.co.kr/poli/sleep/protocol8") {
                    body = MultiPartFormDataContent(
                        formData {
                            append("reqDate", "20240704054513")
                            append("userSno", PoliClient.userSno)
                            append("sessionId", PoliClient.sessionId)
                            append("file", byteArray!!, Headers.build {
                                append(
                                    HttpHeaders.ContentDisposition,
                                    "filename=\"\"",
                                )
                            })
                        }
                    )
                }
            Log.d("SleepProtocol06API", "userSno: ${PoliClient.userSno}")
            Log.d("SleepProtocol06API", "SessionId: ${PoliClient.sessionId}")

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}