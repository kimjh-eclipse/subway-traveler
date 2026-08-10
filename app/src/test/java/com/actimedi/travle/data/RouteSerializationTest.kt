package com.actimedi.travle.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** The store keeps routes as JSON, so the model has to survive a round trip. */
class RouteSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `the seed route round-trips unchanged`() {
        val encoded = json.encodeToString(listOf(SeoulOneDayRoute))
        val decoded = json.decodeFromString<List<Route>>(encoded)
        assertEquals(listOf(SeoulOneDayRoute), decoded)
    }

    @Test
    fun `both segment kinds survive`() {
        val route = SeoulOneDayRoute
        val decoded = json.decodeFromString<List<Route>>(json.encodeToString(listOf(route))).first()
        assertEquals(
            route.segments.count { it is RouteSegment.Move },
            decoded.segments.count { it is RouteSegment.Move },
        )
        assertEquals(
            route.segments.count { it is RouteSegment.Stay },
            decoded.segments.count { it is RouteSegment.Stay },
        )
        assertEquals(route.summarize(), decoded.summarize())
    }

    @Test
    fun `an unreadable payload does not throw`() {
        val result = runCatching { json.decodeFromString<List<Route>>("{ not json ]") }
        assertEquals(true, result.isFailure)
    }
}
