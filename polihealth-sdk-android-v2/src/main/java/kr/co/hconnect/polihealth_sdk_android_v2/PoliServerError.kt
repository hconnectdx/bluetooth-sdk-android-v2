package kr.co.hconnect.polihealth_sdk_android_v2

import kotlinx.coroutines.CancellationException
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.PoliResponse

/**
 * 폴리헬스 서버와 통신하지 못함 — 연결/TLS 실패(인증서 만료 등), 2xx가 아닌 응답, 응답 해석 실패.
 *
 * @property httpCode 서버가 응답한 HTTP 상태 코드. 연결/TLS 단계에서 실패해 응답이 없으면 null
 */
class PoliServerException(
    val httpCode: Int?,
    message: String?,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * 서버 통신 실패로 `PROTOCOL_X_ERROR`를 보낼 때 `onReceive`의 response로 함께 전달된다.
 *
 * 밴드 데이터 자체 문제(파싱 실패, 데이터 부족 등)로 인한 ERROR는 지금처럼 response가 null이므로,
 * 앱은 `response is PoliServerErrorResponse`로 "서버 오류"와 "측정 오류"를 구분해 안내할 수 있다.
 *
 * @property httpCode 서버가 응답한 HTTP 상태 코드. 연결/TLS 단계에서 실패했으면 null
 */
class PoliServerErrorResponse(
    val httpCode: Int?,
    val message: String?,
) : PoliResponse

/**
 * 서버 요청~응답 해석 구간을 감싸서, 그 안에서 난 예외를 모두 [PoliServerException]으로 바꾼다.
 * 밴드 데이터 파싱은 이 블록 밖에서 해야 측정 오류와 서버 오류가 섞이지 않는다.
 */
internal suspend fun <T> poliServerCall(block: suspend () -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: PoliServerException) {
        throw e
    } catch (e: Exception) {
        throw PoliServerException(null, e.message, e)
    }

/** 서버 통신 실패면 [PoliServerErrorResponse], 그 외(측정 데이터 문제)는 null */
internal fun Exception.toServerErrorResponse(): PoliServerErrorResponse? =
    (this as? PoliServerException)?.let { PoliServerErrorResponse(it.httpCode, it.message) }
