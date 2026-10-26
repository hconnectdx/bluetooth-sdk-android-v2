package kr.co.hconnect.polihealth_sdk_android_v2

import kotlinx.coroutines.CancellationException
import kr.co.hconnect.polihealth_sdk_android_v2.api.dto.response.PoliResponse
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * 폴리헬스 서버와 통신하지 못함 — 연결/TLS 실패(인증서 만료 등), 2xx가 아닌 응답, 응답 해석 실패.
 *
 * @property httpCode 서버가 응답한 HTTP 상태 코드. 연결/TLS 단계에서 실패해 응답이 없으면 null
 * @property isCertificateExpired 서버 인증서가 유효하지 않아(만료 등) 실패했는지
 */
class PoliServerException(
    val httpCode: Int?,
    message: String?,
    cause: Throwable? = null,
    val isCertificateExpired: Boolean = cause.isCertificateFailure(),
) : Exception(message, cause)

/**
 * 서버 통신 실패로 `PROTOCOL_X_ERROR`를 보낼 때 `onReceive`의 response로 함께 전달된다.
 *
 * 밴드 데이터 자체 문제(파싱 실패, 데이터 부족 등)로 인한 ERROR는 지금처럼 response가 null이므로,
 * 앱은 `response is PoliServerErrorResponse`로 "서버 오류"와 "측정 오류"를 구분해 안내할 수 있다.
 *
 * @property httpCode 서버가 응답한 HTTP 상태 코드. 연결/TLS 단계에서 실패했으면 null
 * @property isCertificateExpired 서버 인증서가 유효하지 않아(만료 등) 실패했는지. 앱은 이때
 *           재측정 안내 대신 "서버 인증서가 만료되었습니다. 관리자에게 문의하세요."를 보여준다.
 */
class PoliServerErrorResponse(
    val httpCode: Int?,
    val message: String?,
    val isCertificateExpired: Boolean = false,
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
    (this as? PoliServerException)?.let {
        PoliServerErrorResponse(it.httpCode, it.message, it.isCertificateExpired)
    }

/** 인증서 검증 실패를 가리키는 예외 메시지/서버 오류 응답 문구 (Android TrustManager, Ktor CIO TLS, 서버 스택트레이스) */
private val CERTIFICATE_FAILURE_MARKERS = listOf(
    "CertificateExpired",
    "certificate expired",
    "certificate has expired",
    "certificate_expired",
    "CertPathValidator",
    "PKIX path",
)

internal fun String?.mentionsCertificateFailure(): Boolean =
    this != null && CERTIFICATE_FAILURE_MARKERS.any { contains(it, ignoreCase = true) }

/**
 * 연결/TLS 단계 예외의 원인을 따라가며 서버 인증서 검증 실패(만료 포함)인지 확인한다.
 * 인증서 만료는 Android에서 SSLHandshakeException → CertificateException → CertPathValidatorException
 * (→ CertificateExpiredException)처럼 감싸여 오므로 원인 사슬 전체를 본다.
 */
internal fun Throwable?.isCertificateFailure(): Boolean {
    var current = this
    var depth = 0
    while (current != null && depth < 10) {
        if (current is CertificateException ||
            current is CertPathValidatorException ||
            current is SSLPeerUnverifiedException ||
            current.message.mentionsCertificateFailure()
        ) return true
        current = current.cause
        depth++
    }
    return false
}
