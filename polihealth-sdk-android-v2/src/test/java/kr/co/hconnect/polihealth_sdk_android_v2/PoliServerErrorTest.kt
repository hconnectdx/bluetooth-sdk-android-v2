package kr.co.hconnect.polihealth_sdk_android_v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLHandshakeException

class PoliServerErrorTest {

    @Test
    fun `인증서 만료 - Android TrustManager처럼 감싸인 원인 사슬`() {
        val expired = CertificateExpiredException("NotAfter: Mon Oct 19 23:59:59 GMT 2026")
        val pathError = CertPathValidatorException("timestamp check failed", expired)
        val handshake = SSLHandshakeException("Chain validation failed").apply {
            initCause(CertificateException(pathError))
        }

        assertTrue(handshake.isCertificateFailure())
        assertTrue(PoliServerException(null, handshake.message, handshake).isCertificateExpired)
    }

    @Test
    fun `인증서 문제를 메시지로만 알려주는 TLS 예외`() {
        val tls = IOException("TLS handshake failed: certificate has expired")

        assertTrue(tls.isCertificateFailure())
    }

    @Test
    fun `인증서와 무관한 연결 실패는 인증서 오류가 아님`() {
        val connect = ConnectException("Failed to connect to mapi-stg.health-on.co.kr")

        assertFalse(connect.isCertificateFailure())
        assertFalse(PoliServerException(null, connect.message, connect).isCertificateExpired)
        assertFalse((null as Throwable?).isCertificateFailure())
    }

    @Test
    fun `서버 5xx 응답 본문에 인증서 만료 스택트레이스가 있으면 인증서 오류`() {
        val body = """{"stackTrace":"javax.net.ssl.SSLHandshakeException: PKIX path validation failed: java.security.cert.CertPathValidatorException: validity check failed"}"""

        assertTrue(body.mentionsCertificateFailure())
    }

    @Test
    fun `userWeight 누락 같은 일반 서버 오류 본문은 인증서 오류가 아님`() {
        val body = """{"stackTrace":"[kr.co.hconnect.polihealth.service.phr.UserActivityService.lambda${'$'}saveUserActivityInfo${'$'}0(UserActivityService.java:41)]"}"""

        assertFalse(body.mentionsCertificateFailure())
        assertFalse((null as String?).mentionsCertificateFailure())
    }

    @Test
    fun `서버 오류 응답에 인증서 여부가 전달됨`() {
        val certError = PoliServerException(null, "handshake", SSLHandshakeException("x").apply {
            initCause(CertificateExpiredException("expired"))
        })
        val httpError = PoliServerException(500, "HTTP 500 poli/day/protocol1", isCertificateExpired = false)

        val certResponse = certError.toServerErrorResponse()!!
        assertTrue(certResponse.isCertificateExpired)
        assertNull(certResponse.httpCode)

        val httpResponse = httpError.toServerErrorResponse()!!
        assertFalse(httpResponse.isCertificateExpired)
        assertEquals(500, httpResponse.httpCode)

        // 서버 통신 실패가 아닌 예외(측정 데이터 문제)는 null
        assertNull(IllegalStateException("parse").toServerErrorResponse())
    }

    @Test
    fun `몸무게 미설정이나 0 이하면 Protocol 01 전송 불가`() {
        val original = PoliClient.userWeight
        try {
            PoliClient.userWeight = null
            assertThrowsIllegalState { PoliClient.requireUserWeight() }
            PoliClient.userWeight = 0
            assertThrowsIllegalState { PoliClient.requireUserWeight() }
            PoliClient.userWeight = 72
            assertEquals(72, PoliClient.requireUserWeight())
        } finally {
            PoliClient.userWeight = original
        }
    }

    private fun assertThrowsIllegalState(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalStateException) {
            return
        }
        throw AssertionError("IllegalStateException이 발생해야 합니다")
    }
}
