package kr.co.hconnect.polihealth_sdk_android_v2.api.daily.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.ExperimentalSerializationApi


@Serializable
data class LTMModel(
    val lux: Array<Lux>,
    val skinTemp: Array<SkinTemp>,
    val mets: Array<Mets>
) {
    @Serializable
    data class Lux(
        val lux: Int,
        var time: String? = null
    )

    @Serializable
    data class SkinTemp(
        val skinTemp: Float,
        var time: String? = null
    )

    @Serializable
    data class Mets(
        val mets: Int,
        var time: String? = null
    )

    // 자동 생성 코드
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LTMModel

        if (!lux.contentEquals(other.lux)) return false
        if (!skinTemp.contentEquals(other.skinTemp)) return false
        if (!mets.contentEquals(other.mets)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lux.contentHashCode()
        result = 31 * result + skinTemp.contentHashCode()
        result = 31 * result + mets.contentHashCode()
        return result
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun toString(): String {
        // lazy 키워드를 사용해 jsonFormatter를 필요할 때 한 번만 생성
        val jsonFormatter by lazy {
            Json {
                prettyPrint = true // 예쁘게 출력
                prettyPrintIndent = "  " // 들여쓰기 설정
            }
        }

        // 객체를 Json으로 변환
        return jsonFormatter.encodeToString(this)
    }
}

