package kr.co.hconnect.polihealth_sdk_android_v2.api.daily

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.util.AttributeKey
import io.ktor.util.InternalAPI
import kr.co.hconnect.polihealth_sdk_android.DateUtil
import kr.co.hconnect.polihealth_sdk_android.PoliClient
import kr.co.hconnect.polihealth_sdk_android_v2.api.SaveUtil
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.Daily2Response
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.toDaily2Response
import java.io.OutputStream

object DailyProtocol02API {

    var prevByte: Byte = 0x00
    var byteArray: ByteArray = byteArrayOf()


    /**
     * TODO: Protocol02을 서버로 전송하는 API
     *
     * @param reqDate ex) 20240704054513 (yyyyMMddHHmmss)
     * @param byteArray
     * */
    @OptIn(InternalAPI::class)
    suspend fun requestPost(
        reqDate: String,
        byteArray: ByteArray
    ): Daily2Response {
        val response: Daily2Response =
            PoliClient.client.post("poli/day/protocol2") {
                body = MultiPartFormDataContent(
                    formData {
                        append("reqDate", reqDate)
                        append("userSno", PoliClient.userSno)
                        append("userAge", PoliClient.userAge)
                        append("file", byteArray, Headers.build {
                            append(
                                HttpHeaders.ContentDisposition,
                                "filename=\"\"", // 필수 헤더
                            )
                        })
                    }
                )
            }.call.attributes[AttributeKey("body")].toString().toDaily2Response()

        return response
    }

    fun clearByteArray() {
        byteArray = byteArrayOf()
    }

    fun addByte(byteArray: ByteArray) {
        this.byteArray += byteArray // 기존의 _byteArray에 새로운 byteArray를 추가
    }

    // flush 함수: 데이터를 반환하고 _byteArray를 비움

    fun flush(context: Context?): ByteArray {

        if (byteArray.isEmpty()) {
            return byteArrayOf()
        }

        val tempByteArray = byteArray.clone() // 현재 _byteArray를 클론

        byteArray = byteArrayOf()
        context?.let {
            SaveUtil.saveToBinFile(
                it,
                tempByteArray,
                "protocol ${DateUtil.getCurrentDateTime()}.bin"
            )
        } // 클론한 데이터를 파일로 저장

        return tempByteArray // 클론한 데이터를 반환
    }
}