package kr.co.hconnect.polihealth_sdk_android_app.api.dto.request

import kotlinx.serialization.Serializable

@Serializable
data class LTMModel(
    val lux: Array<Lux>,
    val skinTemp: Array<SkinTemp>,
    val mets: Array<Mets>
) {
    @Serializable
    data class Lux(
        val time: String,
        val lux: Int
    )

    @Serializable
    data class SkinTemp(
        val time: String,
        val skinTemp: Int
    )

    @Serializable
    data class Mets(
        val time: String,
        val mets: Int
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
}

