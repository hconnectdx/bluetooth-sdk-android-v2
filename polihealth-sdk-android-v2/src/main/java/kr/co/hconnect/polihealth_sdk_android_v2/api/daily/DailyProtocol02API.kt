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

    /**
     * 바이트 배열을 처리하여 13비트 청크로 분할하고 처리합니다.
     */
    fun addByteNew(byteArray: ByteArray, isLast: Boolean = false) {
        // 상수 정의
        val maxChunks = if (isLast) 48 else 144 // FF일 경우 PPG ECG 24쌍, 평상시에는 72쌍
        val offsetIndexStart = if (isLast) 78 else 234

        // 1. 입력 데이터 준비 및 검증
        val dataSize = byteArray.size
        val processingSize = minOf(dataSize, offsetIndexStart)

        // 2. 오프셋 값 추출
        val offsetValue = extractOffsetValue(byteArray, isLast)

        // 3. 13비트 청크 추출 및 처리
        val intChunks = extractAndProcessChunks(byteArray, processingSize, offsetValue, maxChunks)

        // IntArray를 ByteArray로 변환
        val byteChunks = intChunks.flatMap { intToByteArray(it).toList() }.toByteArray()
        // 4. 원본 바이트 배열 저장
        this.byteArray += byteChunks
    }


    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),   // 최상위 바이트
            ((value shr 16) and 0xFF).toByte(),   // 두 번째 바이트
            ((value shr 8) and 0xFF).toByte(),    // 세 번째 바이트
            (value and 0xFF).toByte()             // 최하위 바이트
        )
    }

    /**
     * 바이트 배열에서 오프셋 값을 추출합니다.
     */
    private fun extractOffsetValue(byteArray: ByteArray, isLast: Boolean): Int {

        val offsetValue =
            if (isLast) {
                ((byteArray[80].toInt() and 0xFF) shl 16) or
                        ((byteArray[79].toInt() and 0xFF) shl 8) or
                        (byteArray[78].toInt() and 0xFF)
            } else {
                ((byteArray[236].toInt() and 0xFF) shl 16) or
                        ((byteArray[235].toInt() and 0xFF) shl 8) or
                        (byteArray[234].toInt() and 0xFF)
            }

        Log.d(
            TAG, "오프셋 값: ${offsetValue} (0x${offsetValue.toString(16)}, " +
                    "이진수=${offsetValue.toBinaryString().padStart(24, '0')})"
        )

        return offsetValue
    }

    /**
     * 바이트 배열에서 13비트 청크를 추출하고 처리
     */
    private fun extractAndProcessChunks(
        byteArray: ByteArray,
        processingSize: Int,
        offsetValue: Int,
        maxChunks: Int
    ): IntArray {
        val processedChunks = IntArray(maxChunks)
        var bitPosition = 0
        var currentValue = 0
        var chunkIndex = 0

        // 바이트 배열 처리
        for (bytePos in 0 until processingSize) {
            val currentByte = byteArray[bytePos].toInt() and 0xFF

            // 각 비트 처리
            for (bitInByte in 0 until 8) {
                // 비트 추출 및 값 구성
                val bitValue = (currentByte shr bitInByte) and 1
                currentValue = currentValue or (bitValue shl bitPosition)
                bitPosition++

                // 13비트가 모이면 청크 처리
                if (bitPosition == 13) {
                    processedChunks[chunkIndex] =
                        processChunk(currentValue, chunkIndex, offsetValue)

                    // 다음 청크 준비
                    currentValue = 0
                    bitPosition = 0
                    chunkIndex++

                    if (chunkIndex >= maxChunks) break
                }
            }

            if (chunkIndex >= maxChunks) break
        }

        // 남은 비트가 있으면 마지막 청크 처리
        if (bitPosition > 0 && chunkIndex < maxChunks) {
            processedChunks[chunkIndex] = processChunk(currentValue, chunkIndex, offsetValue)
        }

        return processedChunks
    }

    /**
     * 하나의 13비트 청크를 처리
     */
    private fun processChunk(originalValue: Int, chunkIndex: Int, offsetValue: Int): Int {
        val isEvenChunk = chunkIndex % 2 == 0
        val finalValue = when (isEvenChunk) {
            true -> (originalValue + offsetValue) and 0xFFFFFFFF.toInt()   // 짝수 청크: 원래 값 + 오프셋
            false -> ((originalValue shl 3) and 0xFFFF) // 16비트만 유지하고 상위 비트는 0으로
        }

        // 로그 출력
        logChunkProcessing(originalValue, finalValue, chunkIndex)

        return finalValue
    }

    /**
     * 청크 처리 과정 로그 출력
     */
    private fun logChunkProcessing(originalValue: Int, finalValue: Int, chunkIndex: Int) {
        val isEvenChunk = chunkIndex % 2 == 0
        val chunkType = if (isEvenChunk) "짝수" else "홀수"
        val binaryOriginal = originalValue.toBinaryString().padStart(13, '0')
        val binaryFinal = finalValue.toBinaryString().padStart(if (isEvenChunk) 24 else 16, '0')

        Log.d(
            TAG, "$chunkType Chunk[$chunkIndex]: " +
                    "원래값=${originalValue} (0x${originalValue.toString(16)}, 이진수=${binaryOriginal}), " +
                    "최종값=${finalValue} (0x${finalValue.toString(16)}, 이진수=${binaryFinal})"
        )
    }

    // Int를 이진수 문자열로 변환하는 확장 함수
    private fun Int.toBinaryString(): String {
        return Integer.toBinaryString(this)
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