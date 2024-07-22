package kr.co.hconnect.polihealth_sdk_android.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

object JsonUtil {
    fun jsonToMap(jsonString: String): Map<String, Any> {
        val jsonElement = Json.parseToJsonElement(jsonString)
        return jsonElementToMap(jsonElement)
    }

    fun jsonElementToMap(jsonElement: JsonElement): Map<String, Any> {
        if (jsonElement !is JsonObject) throw IllegalArgumentException("JsonElement is not a JsonObject")

        val map = mutableMapOf<String, Any>()
        for ((key, value) in jsonElement) {
            map[key] = when {
                value is JsonObject -> jsonElementToMap(value)
                value is JsonArray -> value.map { jsonElementToMap(it) }
                value.jsonPrimitive.isString -> value.jsonPrimitive.content
                value.jsonPrimitive.booleanOrNull != null -> value.jsonPrimitive.boolean
                value.jsonPrimitive.longOrNull != null -> value.jsonPrimitive.long
                value.jsonPrimitive.doubleOrNull != null -> value.jsonPrimitive.double
                else -> value.jsonPrimitive.content
            }
        }
        return map
    }
}