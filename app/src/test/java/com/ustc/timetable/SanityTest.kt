package com.ustc.timetable

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

@Serializable
data class SerializationProbeDto(val name: String, val value: Int)

class SanityTest {
    @Test fun testRunnerWired() { assertEquals(4, 2 + 2) }

    @Test fun serializationPluginWired() {
        val encoded = Json.encodeToString(SerializationProbeDto("probe", 1))
        assertEquals(SerializationProbeDto("probe", 1), Json.decodeFromString<SerializationProbeDto>(encoded))
    }
}
