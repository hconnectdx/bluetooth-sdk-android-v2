package kr.co.hconnect.polihealth_sdk_android

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpSendPipeline
import io.ktor.client.request.header
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.KotlinxSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.AttributeKey
import io.ktor.util.toByteArray
import kotlinx.serialization.json.Json

object PoliClient {
    private lateinit var _client: HttpClient
    val client: HttpClient get() = _client

    private lateinit var _baseUrl: String
    val baseUrl: String get() = _baseUrl

    private var _userAge: Int = 49
    var userAge: Int
        get() = _userAge
        set(value) {
            _userAge = value
        }


    var sessionId: String = ""
    var userSno: Int = 12

    fun init(
        baseUrl: String,
        clientId: String,
        clientSecret: String
    ) {
        _baseUrl = baseUrl
        _client = HttpClient(CIO) {

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
        }

        addLoggerInterceptor()
    }


    private fun addLoggerInterceptor() {
        _client.sendPipeline.intercept(HttpSendPipeline.Before) {
            println()
            println("[Request]")
            println("Method: ${this.context.method.value}")
            println("URL: https://${this.context.url.host}${this.context.url.encodedPath}")
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
    fun prettyPrintJson(jsonString: String): String {
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