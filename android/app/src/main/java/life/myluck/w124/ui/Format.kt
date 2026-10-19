package life.myluck.w124.ui

import life.myluck.w124.core.FuelAnalytics
import life.myluck.w124.core.NodeStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ru = Locale("ru", "RU")
private val dateOut = DateTimeFormatter.ofPattern("d.MM.yyyy")

fun km(value: Int): String = NodeStatus.formatKm(value)

fun l100(value: Double): String = FuelAnalytics.format1(value).replace('.', ',')

fun liters(value: Double): String = FuelAnalytics.format1(value).replace('.', ',')

fun dateRu(iso: String): String = runCatching {
    LocalDate.parse(iso).format(dateOut)
}.getOrDefault(iso)

fun money(value: Double): String = String.format(ru, "%,.0f ₽", value)
