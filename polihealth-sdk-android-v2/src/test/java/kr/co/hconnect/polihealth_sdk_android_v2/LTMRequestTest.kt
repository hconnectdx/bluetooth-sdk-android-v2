package kr.co.hconnect.polihealth_sdk_android_v2

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.model.LTMModel
import kr.co.hconnect.polihealth_sdk_android_v2.api.daily.withUserWeight
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.request.LTMRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LTMRequestTest {

    private val ltm = LTMModel(
        lux = arrayOf(LTMModel.Lux(3552, "20261026151007")),
        skinTemp = arrayOf(LTMModel.SkinTemp(35.04f, "20261026151007")),
        mets = arrayOf(
            LTMModel.Mets(3, "20261026151007"),
            LTMModel.Mets(4, "20261026150907"),
        )
    )

    @Test
    fun `Protocol 01 요청 - 모든 METs 항목에 mets, time, userWeight가 들어가고 최상위에는 없음`() {
        val request = LTMRequest(reqDate = "20261026151007", userSno = 1, data = ltm.withUserWeight(83))

        // PoliClient의 ContentNegotiation과 같은 기본 설정(encodeDefaults=false)
        val json = Json.parseToJsonElement(Json.encodeToString(request)).jsonObject

        assertFalse(json.containsKey("userWeight"))
        val mets = json["data"]!!.jsonObject["mets"]!!.jsonArray
        assertEquals(2, mets.size)
        mets.forEach { item ->
            val obj = item.jsonObject
            assertEquals(setOf("mets", "time", "userWeight"), obj.keys)
            assertEquals(83, obj["userWeight"]!!.jsonPrimitive.int)
        }
        // 조도/피부온도 항목에는 몸무게가 붙지 않는다
        assertFalse(json["data"]!!.jsonObject["lux"]!!.jsonArray[0].jsonObject.containsKey("userWeight"))
        assertFalse(json["data"]!!.jsonObject["skinTemp"]!!.jsonArray[0].jsonObject.containsKey("userWeight"))
    }

    @Test
    fun `원본 측정값은 바뀌지 않음 - 앱에 전달되는 ltmModel에는 몸무게가 없다`() {
        ltm.withUserWeight(83)

        ltm.mets.forEach { assertNull(it.userWeight) }
    }
}
