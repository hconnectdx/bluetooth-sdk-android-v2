package kr.co.hconnect.polihealth_sdk_android_v2

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpSendPipeline
import io.ktor.client.request.header
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.bodyAsText
import io.ktor.http.content.OutgoingContent
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import io.ktor.util.toByteArray
import kotlinx.serialization.json.Json

object PoliClient {
    lateinit var client: HttpClient
    private var baseUrl: String = ""
    var userAge: Int = 0
    var userSno: Int = 0

    /**
     * 사용자 몸무게(kg, 정수). Protocol 01(일상측정 LTM) 전송 시 서버 필수값 — 활동량(METs) 칼로리 계산에 쓰인다.
     * 설정하지 않았거나 0 이하이면 Protocol 01을 서버로 보내지 않고 PROTOCOL_1_ERROR(response=null)로 전달한다.
     */
    var userWeight: Int? = null

    /** Protocol 01 전송 전에 몸무게가 설정됐는지 확인한다. 없으면 [IllegalStateException] */
    internal fun requireUserWeight(): Int {
        val weight = userWeight
        check(weight != null && weight > 0) {
            "PoliClient.userWeight가 설정되지 않았습니다 (현재값=$weight). Protocol 01 전송 전에 사용자 몸무게(kg)를 설정하세요."
        }
        return weight
    }
    var sessionId: String = ""


    /**
     * TODO PoliClient 초기화
     *
     * @param baseUrl
     * @param clientId
     * @param clientSecret
     */
    fun init(
        baseUrl: String,
        clientId: String,
        clientSecret: String
    ) {
        PoliClient.baseUrl = baseUrl
        client = HttpClient(CIO) {

            defaultRequest {
                header("accept", "application/json")
                header("content-type", "application/json")
                header("ClientId", clientId)
                header("ClientSecret", clientSecret)
                url(baseUrl)
            }
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }

            engine {
                pipelining = true
            }

            // 상태 코드를 검사하지 않으면 5xx 오류 페이지(HTML)를 응답 객체로 바꾸다 실패해서,
            // 서버 장애가 밴드 측정 오류와 구분되지 않았다 (2026-10 인증서 만료 장애).
            HttpResponseValidator {
                validateResponse { response ->
                    if (!response.status.isSuccess()) {
                        // 서버가 다른 서버를 호출하다 인증서 문제로 실패해 5xx를 주는 경우도 인증서 오류로 구분한다
                        val body = response.call.attributes.getOrNull(AttributeKey<String>("body"))
                            ?: runCatching { response.bodyAsText() }.getOrNull()
                        throw PoliServerException(
                            httpCode = response.status.value,
                            message = "HTTP ${response.status.value} ${response.call.request.url.encodedPath}",
                            isCertificateExpired = body.mentionsCertificateFailure()
                        )
                    }
                }
            }
        }

        addLoggerInterceptor()
    }


    /**
     * TODO 로그를 찍어주는 Interceptor
     *
     * 1. Request 정보를 찍어준다.
     * 2. Response 정보를 찍어준다.
     */
    private fun addLoggerInterceptor() {
        if (PoliClient::client.isInitialized.not()) {
            throw IllegalStateException("PoliClient is not initialized")
        }
        client.sendPipeline.intercept(HttpSendPipeline.Before) {
            println()
            println("[Request]")
            println("Method: ${this.context.method.value}")
            println("URL: https://${this.context.url.host}/${this.context.url.encodedPath}")
            println("Parameter: ${this.context.url.parameters.entries()}")
            println("Header: ${this.context.headers.entries()}")

            val requestBody = context.body
            if (requestBody is OutgoingContent.ByteArrayContent) {
                println("Body: ${String(requestBody.bytes())}")
            } else if (requestBody is OutgoingContent.ReadChannelContent) {
                val byteArray = requestBody.readFrom().toByteArray()
                println("Body: ${String(byteArray)}")
            } else {
                println("Body: ${context.body}")
            }

            proceedWith(this.subject)
        }

        client.receivePipeline.intercept(HttpReceivePipeline.After) { response ->
            println()
            println("[Response]")
            println("Status: ${response.status}")
            println("Method: ${response.call.request.method.value}")
            println("URL: ${response.call.request.url}")
            println("Header ${response.headers.entries()}")
            val bodyText = prettyPrintJson(response.bodyAsText())
            println("Body: $bodyText")
            response.call.attributes.put(AttributeKey("body"), bodyText)
        }
    }

    /**
     * TODO 한줄로 쌩자로 오는 json을 읽기 쉽게 pretty하게 변환하는 함수
     *
     * @param jsonString
     * @return prettyJson
     */
    private fun prettyPrintJson(jsonString: String): String {
        var indentLevel = 0
        val indentSpace = 4
        val prettyJson = StringBuilder()
        var inQuotes = false

        for (char in jsonString) {
            when (char) {
                '{', '[' -> {
                    prettyJson.append(char)
                    if (!inQuotes) {
                        prettyJson.append('\n')
                        indentLevel++
                        prettyJson.append(" ".repeat(indentLevel * indentSpace))
                    }
                }

                '}', ']' -> {
                    if (!inQuotes) {
                        prettyJson.append('\n')
                        indentLevel--
                        prettyJson.append(" ".repeat(indentLevel * indentSpace))
                    }
                    prettyJson.append(char)
                }

                ',' -> {
                    prettyJson.append(char)
                    if (!inQuotes) {
                        prettyJson.append('\n')
                        prettyJson.append(" ".repeat(indentLevel * indentSpace))
                    }
                }

                '"' -> {
                    prettyJson.append(char)
                    if (char == '"') {
                        inQuotes = !inQuotes
                    }
                }

                else -> {
                    prettyJson.append(char)
                }
            }
        }
        return prettyJson.toString()
    }


}