package kr.co.hconnect.polihealth_sdk_android.api.daily

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.util.AttributeKey
import kr.co.hconnect.polihealth_sdk_android.DateUtil
import kr.co.hconnect.polihealth_sdk_android.PoliClient
import kr.co.hconnect.polihealth_sdk_android.api.dto.request.LTMRequest
import kr.co.hconnect.polihealth_sdk_android_app.api.dto.request.LTMModel
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.Daily1Response
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.toDaily1Response

object DailyProtocol01API {
    /**
     * TODO: 조도값, 피부온도값, 활동량값을 서버로 전송하는 API
     *
     * @param reqDate ex) 20240704054513 (yyyyMMddHHmmss)
     * @param LTMModel
     *
     * @return Protocol1Response
     * */
    suspend fun requestPost(
        reqDate: String,
        ltmModel: LTMModel
    ): Daily1Response {

        val requestBody = LTMRequest(
            reqDate = reqDate,
            userSno = PoliClient.userSno,
            data = ltmModel
        )

        val response = PoliClient.client.post("poli/day/protocol1") {
            setBody(requestBody)
        }.call.attributes[AttributeKey("body")].toString().toDaily1Response(ltmModel)

        return response
    }

    fun parseLTMData(data: ByteArray): LTMModel {
        val header = data[0]
        val dataNum = data[1]

        val sampleSize = 16
        val totalSamples = 12
        var offset = 2 // Skip header and dataNum

        val luxList = mutableListOf<LTMModel.Lux>()
        val skinTempList = mutableListOf<LTMModel.SkinTemp>()
        val metsList = mutableListOf<LTMModel.Mets>()

        var metLoopCnt = 0L // 현재 시간에서 1분씩 감소 시킬 값
        for (i in 0 until totalSamples) {

            // METs 데이터 추출 (2Bytes * 5EA)
            for (j in 0 until 5) {
                val metsTime = DateUtil.getCurrentDateTime(minusMin = metLoopCnt)
                val metsValue =
                    ((data[offset + 2 * j].toInt() and 0xFF shl 8) or (data[offset + 2 * j + 1].toInt() and 0xFF)).toShort()
                        .toInt()
                metsList.add(LTMModel.Mets(metsTime, metsValue / 1000)) // metLoopCnt 분씩 감소 해야함.
                metLoopCnt += 1
            }

            // Temp 데이터 추출 (4Bytes)
            val tempValue =
                ((data[offset + 10].toInt() and 0xFF shl 24) or (data[offset + 11].toInt() and 0xFF shl 16) or (data[offset + 12].toInt() and 0xFF shl 8) or (data[offset + 13].toInt() and 0xFF))

            val tempTime = DateUtil.getCurrentDateTime(minusMin = i * 5L)
            skinTempList.add(
                LTMModel.SkinTemp(
                    tempTime,
                    Float.fromBits(tempValue)
                )
            ) // i*5 분씩 감소 해야 함.

            // Lux 데이터 추출 (2Bytes)
            val luxValue =
                (data[offset + 14].toInt() and 0xFF shl 8) or (data[offset + 15].toInt() and 0xFF)

            val luxTime = DateUtil.getCurrentDateTime(minusMin = i * 5L)
            luxList.add(LTMModel.Lux(luxTime, luxValue))

            // 오프셋 증가
            offset += sampleSize
        }

        val ltmModel = LTMModel(
            lux = luxList.toTypedArray(),
            skinTemp = skinTempList.toTypedArray(),
            mets = metsList.toTypedArray()
        )

        return ltmModel
    }

    val testRawData = byteArrayOf(
        0x01,
        0x00,
        0x75,
        0x30,
        0x3a,
        0x98.toByte(),
        0x00,
        0x00,
        0x75,
        0x30,
        0x3a,
        0x98.toByte(),
        0x42,
        0x12,
        0x00,
        0x00,
        0xff.toByte(),
        0xff.toByte(),
        0x00,
        0x00,
        0x75,
        0x30,
        0x3a,
        0x98.toByte(),
        0x00,
        0x00,
        0x75,
        0x30,
        0x41,
        0xc8.toByte(),
        0x00,
        0x00,
        0x80.toByte(),
        0x00,
        0x3a,
        0x98.toByte(),
        0x00,
        0x00,
        0x75,
        0x30,
        0x3a,
        0x98.toByte(),
        0x00,
        0x00,
        0x42,
        0x34,
        0x00,
        0x00,
        0x00,
        0x00,
        0x75,
        0x30,
        0x3a,
        0x98.toByte(),
        0x00,
        0x00,
        0x75,
        0x30,
        0x3a,
        0x98.toByte(),
        0x42,
        0x12,
        0x00,
        0x00,
        0xff.toByte(),
        0xff.toByte(),
        0x00,
        0x00,
        0x75,
        0x30,
        0x3a,
        0x98.toByte(),
        0x00,
        0x00,
        0x75,
        0x30,
        0x41,
        0xc8.toByte(),
        0x00,
        0x00,
        0x80.toByte(),
        0x00,
        0x3a,
        0x98.toByte(),
        0x00,
        0x00,
        0x75,
        0x30,
        0x3a,
        0x98.toByte(),
        0x00,
        0x00,
        0x42,
        0x34,
        0x00,
        0x00,
        0x00,
        0x00,
        0x75,
        0x30,
        0x3a,
        0x98.toByte(),
        0x00,
        0x00,
        0x75,
        0x30,
        0x3a,
        0x98.toByte(),
        0x42,
        0x12,
        0x00,
        0x00,
        0xff.toByte(),
        0xff.toByte(),
        0x00,
        0x00,
        0x75,
        0x30,
        0x3a,
        0x98.toByte(),
        0x00,
        0x00,
        0x75,
        0x30,
        0x41,
        0xc8.toByte(),
        0x00,
        0x00,
        0x80.toByte(),
        0x00,
        0x3a,
        0x98.toByte(),
        0x00,
        0x00,
        0x75,
        0x30,
        0x3a,
        0x98.toByte(),
        0x00,
        0x00,
        0x42,
        0x34,
        0x00,
        0x00,
        0x00,
        0x00,
        0x75,
        0x30,
        0x3a,
        0x98.toByte(),
        0x00,
        0x00,
        0x75,
        0x30,
        0x3a,
        0x98.toByte(),
        0x42,
        0x12,
        0x00,
        0x00,
        0xff.toByte(),
        0xff.toByte(),
        0x00,
        0x00,
        0x75,
        0x30,
        0x3a,
        0x98.toByte(),
        0x00,
        0x00,
        0x75,
        0x30,
        0x41,
        0xc8.toByte(),
        0x00,
        0x00,
        0x80.toByte(),
        0x00,
        0x3a,
        0x98.toByte(),
        0x00,
        0x00,
        0x75,
        0x30,
        0x3a,
        0x98.toByte(),
        0x00,
        0x00,
        0x42,
        0x34,
        0x00,
        0x00,
        0x00,
        0x00
    )
}