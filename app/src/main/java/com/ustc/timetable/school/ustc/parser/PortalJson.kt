package com.ustc.timetable.school.ustc.parser

import com.ustc.timetable.school.ustc.dto.UstcEndpointId
import com.ustc.timetable.school.ustc.dto.UstcPortalPage
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.SyncFailure
import com.ustc.timetable.sync.asFailure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

internal val PORTAL_JSON = Json { ignoreUnknownKeys = true }

internal inline fun <T> parsePortalPayload(block: () -> T): T = try {
    block()
} catch (failure: SyncFailure) {
    throw failure
} catch (failure: Exception) {
    throw SyncError.ParseFailed.asFailure(failure)
}

internal fun parseFailure(): Nothing = throw SyncError.ParseFailed.asFailure()

internal fun UstcPortalPage.requiredJson(endpoint: UstcEndpointId): JsonElement {
    val response = xhr[endpoint] ?: parseFailure()
    if (response.body.isBlank()) parseFailure()
    return PORTAL_JSON.parseToJsonElement(response.body)
}

internal fun JsonElement.requiredObject(): JsonObject = this as? JsonObject ?: parseFailure()

internal fun JsonElement.requiredArray(): JsonArray = this as? JsonArray ?: parseFailure()

internal fun JsonObject.requiredObject(name: String): JsonObject = get(name)?.requiredObject() ?: parseFailure()

internal fun JsonObject.requiredArray(name: String): JsonArray = get(name)?.requiredArray() ?: parseFailure()

internal fun JsonObject.requiredString(name: String): String = optionalString(name)?.takeIf(String::isNotEmpty)
    ?: parseFailure()

internal fun JsonObject.optionalString(name: String): String? {
    val element = get(name) ?: return null
    if (element is JsonNull) return null
    val primitive = element as? JsonPrimitive ?: parseFailure()
    if (!primitive.isString) parseFailure()
    return primitive.contentOrNull?.trim()?.ifEmpty { null }
}

internal fun JsonObject.requiredInt(name: String): Int {
    val primitive = get(name) as? JsonPrimitive ?: parseFailure()
    return primitive.intOrNull ?: parseFailure()
}

internal fun JsonObject.optionalDouble(name: String): Double? {
    val element = get(name) ?: return null
    if (element is JsonNull) return null
    val value = (element as? JsonPrimitive)?.doubleOrNull ?: parseFailure()
    if (!value.isFinite()) parseFailure()
    return value
}

internal fun JsonObject.requiredResultObject(): JsonObject = requiredObject("result")
