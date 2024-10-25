package kr.co.hconnect.polihealth_sdk_android_v2.api.daily

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.util.AttributeKey
import kr.co.hconnect.polihealth_sdk_android.DateUtil
import kr.co.hconnect.polihealth_sdk_android.PoliClient
import kr.co.hconnect.polihealth_sdk_android.api.dto.request.LTMRequest
import kr.co.hconnect.polihealth_sdk_android_v2.api.SaveUtil
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.model.LTMModel
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.Daily1Response
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.toDaily1Response
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object DailyProtocol01API {
    var ltmModel: LTMModel? = null
    private val lstLux = mutableListOf<LTMModel.Lux>()
    private val lstSkinTemp = mutableListOf<LTMModel.SkinTemp>()
    private val lstMets = mutableListOf<LTMModel.Mets>()
    private var _byteArray: ByteArray = byteArrayOf()

    val byteArray: ByteArray
        get() = _byteArray

    // addByte 함수: 바이트 배열을 추가
    fun collectBytes(byteArray: ByteArray) {
        _byteArray += byteArray // 기존의 _byteArray에 새로운 byteArray를 추가
    }

    fun clearCollectedBytes() {
        _byteArray = byteArrayOf()
    }

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


        // 다음 데이터를 위해 초기화
        clearData()

        return response
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun parseLTMData(data: ByteArray, context: Context? = null): LTMModel {
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
                metsList.add(LTMModel.Mets(metsValue / 1000, metsTime)) // metLoopCnt 분씩 감소 해야함.
                metLoopCnt += 1
            }

            // Temp 데이터 추출 (4Bytes)
            val tempValue =
                ((data[offset + 10].toInt() and 0xFF shl 24) or (data[offset + 11].toInt() and 0xFF shl 16) or (data[offset + 12].toInt() and 0xFF shl 8) or (data[offset + 13].toInt() and 0xFF))

            val tempTime = DateUtil.getCurrentDateTime(minusMin = i * 5L)
            skinTempList.add(
                LTMModel.SkinTemp(
                    Float.fromBits(tempValue),
                    tempTime
                )
            ) // i*5 분씩 감소 해야 함.

            // Lux 데이터 추출 (2Bytes)
            val luxValue =
                (data[offset + 14].toInt() and 0xFF shl 8) or (data[offset + 15].toInt() and 0xFF)

            val luxTime = DateUtil.getCurrentDateTime(minusMin = i * 5L)
            luxList.add(LTMModel.Lux(luxValue, luxTime))

            // 오프셋 증가
            offset += sampleSize
        }

        val ltmModel = LTMModel(
            lux = luxList.toTypedArray(),
            skinTemp = skinTempList.toTypedArray(),
            mets = metsList.toTypedArray()
        )

        if (context != null) {
            SaveUtil.saveStringToFile(
                context,
                ltmModel.toString(),
                "protocol1 ${DateUtil.getCurrentDateTime()}.txt"
            )
        } // 클론한 데이터를 파일로 저장

        return ltmModel
    }

    fun createLTMModel() {

        val currentTime = DateUtil.getCurrentDateTime()

        val lstLuxWithTime = lstLux.mapIndexed { index, lux ->
            lux.time = DateUtil.adjustDateTime(currentTime, minusMin = (5 * index).toLong())
            lux
        }

        val lstSkinWithTime = lstSkinTemp.mapIndexed { index, skinTemp ->
            skinTemp.time = DateUtil.adjustDateTime(currentTime, minusMin = (5 * index).toLong())
            skinTemp
        }

        val lstMetsWithTime = lstMets.mapIndexed { index, mets ->
            mets.time = DateUtil.adjustDateTime(currentTime, minusMin = index.toLong())
            mets
        }

        val ltmModel = LTMModel(
            lux = lstLuxWithTime.toTypedArray(),
            skinTemp = lstSkinWithTime.toTypedArray(),
            mets = lstMetsWithTime.toTypedArray()
        )

        DailyProtocol01API.ltmModel = ltmModel
    }

    private fun clearData() {
        lstLux.clear()
        lstSkinTemp.clear()
        lstMets.clear()
        ltmModel = null
    }

    /**
     * TODO: 데이터를 카테고리화하여 저장
     * TODO: 카테고리화 된 데이터들은 계속 저장되어, 전송할때 취합하여 전송
     * lstMets, lstSkinTemp, lstLux
     * @param bytes
     */
    fun categorizeData(bytes: ByteArray) {
        val sampleSize = 16
        val totalSamples = 12
        var offset = 2 // Skip header and dataNum

        for (i in 0 until totalSamples) {

            // METs 데이터 추출 (2Bytes * 5EA)
            for (j in 0 until 5) {
                val metsValue =
                    ((bytes[offset + 2 * j].toUInt() and 0xFFu shl 8) or (bytes[offset + 2 * j + 1].toUInt() and 0xFFu))

                Log.d("DailyProtocol01API", "metsValue toUInt: $metsValue")
                Log.d("DailyProtocol01API", "metsValue toInt: ${metsValue.toInt()}")

                lstMets.add(LTMModel.Mets(metsValue.toInt() / 1000))
            }

            // Temp 데이터 추출 (4Bytes)
            val tempValue =
                ((bytes[offset + 10].toInt() and 0xFF shl 24) or (bytes[offset + 11].toInt() and 0xFF shl 16) or (bytes[offset + 12].toInt() and 0xFF shl 8) or (bytes[offset + 13].toInt() and 0xFF))

            lstSkinTemp.add(
                LTMModel.SkinTemp(
                    Float.fromBits(tempValue)
                )
            ) // i*5 분씩 감소 해야 함.

            // Lux 데이터 추출 (2Bytes)
            val luxValue =
                (bytes[offset + 14].toInt() and 0xFF shl 8) or (bytes[offset + 15].toInt() and 0xFF)

            lstLux.add(LTMModel.Lux(luxValue))

            // 오프셋 증가
            offset += sampleSize
        }
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