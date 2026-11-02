package cl.medidorwifi

import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    private fun radio(
        b: String = "aa:bb:cc:dd:ee:01",
        r: Int = -80,
        t: Long = 0,
        ssid: String = "Tienda",
    ) = Radio(b, ssid, r, 2412, t)

    @Test
    fun changesOnlyOnBssid() {
        val t = RoamingTracker()
        assertNull(t.observe(radio(), 0))
        assertNull(t.observe(radio(r = -60), 1000))
        val e = t.observe(radio("aa:bb:cc:dd:ee:02", -50), 2000)!!
        assertEquals(-60, e.from.rssi)
        assertEquals(2000L, e.dwell)
        assertEquals(1000L, e.interval)
        assertNull(e.gap)
    }

    @Test
    fun resumeDoesNotInventRoam() {
        val t = RoamingTracker()
        t.observe(radio(), 0)
        t.reset()
        assertNull(t.observe(radio("aa:bb:cc:dd:ee:02"), 100000))
    }

    @Test
    fun missingObservationIsNotExactRoamingTime() {
        val t = RoamingTracker()
        t.observe(radio(), 0)
        t.observe(null, 1000)
        val e = t.observe(radio("aa:bb:cc:dd:ee:02"), 3000)!!
        assertEquals(2000L, e.gap)
        assertEquals(3000L, e.interval)
    }

    @Test
    fun alertNeedsFreshPersistentSameSsid() {
        val a = LateRoaming()
        val b = radio("aa:bb:cc:dd:ee:02", -50)
        assertNull(a.observe(radio(), listOf(b), 0, -75, 15, 10000))
        assertNotNull(a.observe(radio(t = 10000), listOf(b), 10000, -75, 15, 10000))
        assertNull(a.observe(radio(t = 11000), listOf(b), 11000, -75, 15, 10000))
        assertNull(a.observe(radio(t = 20000), listOf(b), 20000, -75, 15, 10000))
    }

    @Test
    fun otherSsidAndStaleReadingsCannotTrigger() {
        val a = LateRoaming()
        assertNull(
            a.observe(
                radio(),
                listOf(radio("aa:bb:cc:dd:ee:02", -40, ssid = "Otra")),
                0,
                -75,
                15,
                0,
            )
        )
        assertNull(
            a.observe(radio(t = 20000), listOf(radio("aa:bb:cc:dd:ee:02", -40)), 20000, -75, 15, 0)
        )
    }

    @Test
    fun channelAndQualityBoundaries() {
        assertEquals(1, radio().channel)
        assertEquals(14, radio().copy(frequency = 2484).channel)
        assertEquals(1, radio().copy(frequency = 5955).channel)
        assertEquals("Intermedia", quality(-75))
        assertEquals("Buena", quality(-65))
        assertEquals("Débil", quality(-76))
        assertEquals("Sin lectura reciente", quality(null))
    }

    @Test
    fun redactedBssidRejected() {
        assertFalse(validBssid("02:00:00:00:00:00"))
        assertFalse(validBssid(null))
        assertTrue(validBssid("aa:bb:cc:dd:ee:01"))
    }

    @Test
    fun csvEscapesQuotesAndFormulas() {
        assertEquals("\"a\"\"b\"", csvCell("a\"b"))
        assertEquals("\"'=1+1\"", csvCell("=1+1"))
    }
}
