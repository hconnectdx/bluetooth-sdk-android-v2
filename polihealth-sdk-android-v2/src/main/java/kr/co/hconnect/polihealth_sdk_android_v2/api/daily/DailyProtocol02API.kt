package kr.co.hconnect.polihealth_sdk_android_v2.api.daily

import android.content.Context
import android.util.Log
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.post
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.util.AttributeKey
import io.ktor.util.InternalAPI
import kr.co.hconnect.polihealth_sdk_android_v2.DateUtil
import kr.co.hconnect.polihealth_sdk_android_v2.PoliBLE.removeFrontTwoBytes
import kr.co.hconnect.polihealth_sdk_android_v2.PoliClient
import kr.co.hconnect.polihealth_sdk_android_v2.api.SaveUtil
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.Daily2Response
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.toDaily2Response

object DailyProtocol02API {

    var prevByte: Byte = 0x00
    var byteArray: ByteArray = byteArrayOf()
    final val TAG = "DailyProtocol02API"


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

    fun addByte2(byteArray: ByteArray) {
        // 인덱스 0부터 인덱스 233 까지 처리
        val dataSize = minOf(byteArray.size, 237)  // 처리할 최대 인덱스는 236
        val processedValues = IntArray(144)

        // 오프셋 바이트 추출 (인덱스 234, 235, 236)
        var offsetValue = 0
        if (dataSize > 236) {
            offsetValue = ((byteArray[236].toInt() and 0xFF) shl 16) or
                    ((byteArray[235].toInt() and 0xFF) shl 8) or
                    (byteArray[234].toInt() and 0xFF)

            Log.d(
                TAG,
                "오프셋 값: ${offsetValue} (0x${offsetValue.toString(16)}, 이진수=${
                    offsetValue.toBinaryString().padStart(24, '0')
                })"
            )
        }

        // 바이트 순서대로 처리
        var bitPosition = 0
        var currentValue = 0
        var chunkIndex = 0

        // 인덱스 0부터 233까지 처리
        val processingSize = minOf(dataSize, 234)

        for (bytePos in 0 until processingSize) {
            val currentByte = byteArray[bytePos].toInt() and 0xFF

            // 현재 바이트의 각 비트를 처리
            for (bitInByte in 0 until 8) {
                // 현재 비트 값 추출
                val bitValue = (currentByte shr bitInByte) and 1

                // 현재 값에 비트 추가
                currentValue = currentValue or (bitValue shl bitPosition)
                bitPosition++

                // 13비트가 채워지면 처리
                if (bitPosition == 13) {
                    val originalValue = currentValue
                    var finalValue = currentValue

                    if (chunkIndex % 2 == 0) {
                        // 짝수 청크: 원래 값 + 오프셋
                        finalValue = (finalValue + offsetValue)
                    } else {
                        // 홀수 청크: 3비트 쉬프트 후 int16_t로 변환(16비트로 제한)
                        finalValue = (finalValue shl 3)
                        // int16_t로 변환 (16비트 부호 있는 정수로 제한)
                        finalValue = finalValue.toShort().toInt()
                    }

                    processedValues[chunkIndex] = finalValue

                    // 로그 출력
                    val binaryOriginal = originalValue.toBinaryString().padStart(13, '0')
                    val binaryFinal = finalValue.toBinaryString()
                        .padStart(if (chunkIndex % 2 == 0) 24 else 16, '0')

                    val chunkType = if (chunkIndex % 2 == 0) "짝수" else "홀수"
                    Log.d(
                        TAG,
                        "$chunkType Chunk[$chunkIndex]: 원래값=${originalValue} (0x${
                            originalValue.toString(16)
                        }, 이진수=${binaryOriginal}), " +
                                "최종값=${finalValue} (0x${finalValue.toString(16)}, 이진수=${binaryFinal})"
                    )

                    // 다음 청크 준비
                    currentValue = 0
                    bitPosition = 0
                    chunkIndex++

                    // 최대 144개 청크 처리 후 종료
                    if (chunkIndex >= 144) break
                }
            }

            // 최대 청크 수에 도달하면 루프 종료
            if (chunkIndex >= 144) break
        }

        // 남은 비트가 있으면 마지막 청크 처리
        if (bitPosition > 0 && chunkIndex < 144) {
            val originalValue = currentValue
            var finalValue = currentValue

            if (chunkIndex % 2 == 0) {
                // 짝수 청크: 원래 값 + 오프셋
                finalValue = (finalValue + offsetValue)
            } else {
                // 홀수 청크: 3비트 쉬프트 후 int16_t로 변환
                finalValue = (finalValue shl 3)
                finalValue = finalValue.toShort().toInt()
            }

            processedValues[chunkIndex] = finalValue

            val binaryOriginal = originalValue.toBinaryString().padStart(bitPosition, '0')
            val binaryFinal =
                finalValue.toBinaryString().padStart(if (chunkIndex % 2 == 0) 24 else 16, '0')

            val chunkType = if (chunkIndex % 2 == 0) "짝수" else "홀수"
            Log.d(
                TAG,
                "마지막 $chunkType Chunk[$chunkIndex]: 원래값=${originalValue} (0x${
                    originalValue.toString(16)
                }, 이진수=${binaryOriginal}), " +
                        "최종값=${finalValue} (0x${finalValue.toString(16)}, 이진수=${binaryFinal})"
            )
        }

        // Store the processed values for later use
//        storeProcessedData(processedValues)

        // Add the original byteArray to the cumulative storage
        this.byteArray += byteArray
    }

    // Int를 이진수 문자열로 변환하는 확장 함수 추가
    fun Int.toBinaryString(): String {
        return Integer.toBinaryString(this)
    }

    fun addByte(byteArray: ByteArray) {

        // 인덱스 0부터  인덱스 233 까지 13비트로 쪼갠다.

        // 144개의 데이터가 나온다.

        // 이 144개의 데이터를 빅앤드? 로 변경한다.

        // 이 144개의 데이터 각각에 3번 왼쪽 쉬프트를 한다.

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