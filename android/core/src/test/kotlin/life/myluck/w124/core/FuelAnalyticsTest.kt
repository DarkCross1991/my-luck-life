package life.myluck.w124.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelAnalyticsTest {
    private fun fill(
        id: String,
        date: String,
        odo: Int,
        liters: Double,
        full: Boolean = true,
        trip: String = TripType.MIXED,
    ) = FuelEntry(
        id = id,
        date = date,
        odometer = odo,
        liters = liters,
        full = full,
        tripType = trip,
        updatedAt = "${date}T12:00:00Z",
    )

    @Test
    fun emptyNeedsTwoFullTanks() {
        val report = FuelAnalytics.report(emptyList())
        assertEquals(ThirstVerdict.INSUFFICIENT_DATA, report.verdict)
        assertTrue(report.intervals.isEmpty())
    }

    @Test
    fun fullToFullIncludesPartialInBetween() {
        val fuel = listOf(
            fill("a", "2026-08-01", 322200, 40.0, full = true),
            fill("b", "2026-08-10", 322450, 12.0, full = false),
            fill("c", "2026-08-20", 322700, 38.0, full = true),
        )
        val report = FuelAnalytics.report(fuel)
        assertEquals(1, report.intervals.size)
        val interval = report.intervals.single()
        assertEquals(500, interval.km)
        assertEquals(50.0, interval.liters, 0.001)
        assertEquals(10.0, interval.litersPer100km, 0.001)
        assertEquals(ThirstVerdict.INSUFFICIENT_DATA, report.verdict)
    }

    @Test
    fun risingThirstOnHighwayPattern() {
        val fuel = listOf(
            fill("1", "2026-05-01", 320000, 40.0),
            fill("2", "2026-05-20", 320400, 40.0, trip = TripType.HIGHWAY),
            fill("3", "2026-06-10", 320800, 40.0, trip = TripType.HIGHWAY),
            fill("4", "2026-07-01", 321200, 40.0, trip = TripType.HIGHWAY),
            fill("5", "2026-08-01", 321500, 48.0, trip = TripType.HIGHWAY),
        )
        val report = FuelAnalytics.report(fuel)
        assertTrue(report.intervals.size >= 3)
        assertEquals(ThirstVerdict.RISING, report.verdict)
        assertTrue(report.last!! > 15.0)
    }

    @Test
    fun shortTripsAreNotEngineThirst() {
        val fuel = listOf(
            fill("1", "2026-07-01", 322000, 30.0),
            fill("2", "2026-07-10", 322400, 40.0, trip = TripType.MIXED),
            fill("3", "2026-07-20", 322580, 28.0, trip = TripType.SHORT),
        )
        val report = FuelAnalytics.report(fuel)
        assertEquals(ThirstVerdict.SHORT_TRIPS, report.verdict)
    }

    @Test
    fun addFuelBumpsOdometer() {
        val state = GarageJson.decodeState(
            javaClass.classLoader!!.getResource("seed-state.json")!!.readText(),
        )
        val updated = GarageMutations.addFuel(
            state,
            fill("f1", "2026-08-26", 322500, 42.5),
        )
        assertEquals(322500, updated.odometer.km)
        assertTrue(updated.fuel.any { it.id == "f1" })
    }
}
