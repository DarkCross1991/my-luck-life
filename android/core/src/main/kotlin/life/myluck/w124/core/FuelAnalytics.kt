package life.myluck.w124.core

import kotlin.math.abs

object FuelAnalytics {
    const val NORMAL_MIN = 9.0
    const val NORMAL_MAX = 13.0

    fun report(fuel: List<FuelEntry>): FuelReport {
        val intervals = intervals(fuel)
        if (intervals.isEmpty()) {
            return FuelReport(
                intervals = emptyList(),
                last = null,
                average = null,
                median = null,
                trendPercent = null,
                verdict = ThirstVerdict.INSUFFICIENT_DATA,
                summaryRu = "Расход появится после второй полной заправки.",
            )
        }
        val values = intervals.map { it.litersPer100km }
        val last = values.last()
        val average = values.average()
        val median = median(values)
        val trendPercent = if (intervals.size >= 2) {
            val prevMedian = median(values.dropLast(1))
            if (prevMedian > 0) (last - prevMedian) / prevMedian * 100.0 else null
        } else {
            null
        }
        val verdict = verdict(intervals)
        return FuelReport(
            intervals = intervals,
            last = last,
            average = average,
            median = median,
            trendPercent = trendPercent,
            verdict = verdict,
            summaryRu = summary(verdict, last, trendPercent, intervals.last()),
        )
    }

    fun intervals(fuel: List<FuelEntry>): List<FuelInterval> {
        val fills = fuel
            .filter { !it.deleted }
            .sortedWith(compareBy({ it.odometer }, { it.date }, { it.id }))
        val fullIdx = fills.indices.filter { fills[it].full }
        val out = mutableListOf<FuelInterval>()
        for (i in 0 until fullIdx.size - 1) {
            val a = fullIdx[i]
            val b = fullIdx[i + 1]
            val start = fills[a]
            val end = fills[b]
            val km = end.odometer - start.odometer
            if (km <= 0) continue
            val slice = fills.subList(a + 1, b + 1)
            val liters = slice.sumOf { it.liters }
            if (liters <= 0.0) continue
            out += FuelInterval(
                fromOdometer = start.odometer,
                toOdometer = end.odometer,
                fromDate = start.date,
                toDate = end.date,
                km = km,
                liters = liters,
                litersPer100km = liters / km * 100.0,
                tripTypes = slice.map { it.tripType },
                fillIds = slice.map { it.id },
            )
        }
        return out
    }

    private fun verdict(intervals: List<FuelInterval>): ThirstVerdict {
        if (intervals.size < 2) return ThirstVerdict.INSUFFICIENT_DATA
        val last = intervals.last()
        val prevMedian = median(intervals.dropLast(1).map { it.litersPer100km })
        val lastL = last.litersPer100km
        val shortTrips = last.km < 280 && lastL > 12.5 ||
            last.tripTypes.all { it == TripType.SHORT || it == TripType.CITY } && lastL > 12.5
        val rising = lastL > prevMedian * 1.12 && (lastL - prevMedian) > 0.8
        val high = lastL > 13.5
        return when {
            shortTrips -> ThirstVerdict.SHORT_TRIPS
            rising && lastL > 11.5 -> ThirstVerdict.RISING
            high -> ThirstVerdict.HIGH
            else -> ThirstVerdict.NORMAL
        }
    }

    private fun summary(
        verdict: ThirstVerdict,
        last: Double,
        trendPercent: Double?,
        lastInterval: FuelInterval,
    ): String {
        val l = format1(last)
        val trend = trendPercent?.let { " (" + signed(it) + "% к медиане прошлых)" } ?: ""
        return when (verdict) {
            ThirstVerdict.INSUFFICIENT_DATA ->
                "Нужно ещё минимум две полные заправки, чтобы судить о расходе."
            ThirstVerdict.NORMAL ->
                "Последний расход $l л/100 км$trend — в норме для 200E M102 (ориентир 10–12 смешанный, 12–14 город)."
            ThirstVerdict.SHORT_TRIPS ->
                "Последний расход $l л/100 км на ${lastInterval.km} км. Похоже на город/короткие поездки, не на поломку."
            ThirstVerdict.RISING ->
                "Расход растёт: $l л/100 км$trend. При том же стиле езды это признак прожорливости мотора (свечи, провода, смесь, подсос)."
            ThirstVerdict.HIGH ->
                "Расход высокий: $l л/100 км. Если это не чистый город — смотреть мотор."
        }
    }

    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 0) (s[m - 1] + s[m]) / 2.0 else s[m]
    }

    fun format1(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

    private fun signed(percent: Double): String {
        val v = String.format(java.util.Locale.US, "%.0f", abs(percent))
        return if (percent >= 0) "+$v" else "-$v"
    }
}
