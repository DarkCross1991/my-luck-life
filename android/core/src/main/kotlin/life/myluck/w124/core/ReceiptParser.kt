package life.myluck.w124.core

data class ParsedReceipt(
    val liters: Double? = null,
    val pricePerLiter: Double? = null,
    val totalCost: Double? = null,
    val date: String? = null,
    val station: String? = null,
) {
    val hasFuelData: Boolean get() = liters != null || (pricePerLiter != null && totalCost != null)

    fun resolvedLiters(): Double? {
        liters?.let { return it }
        val price = pricePerLiter
        val total = totalCost
        if (price != null && total != null && price > 0.0) return total / price
        return null
    }

    fun resolvedPrice(): Double? {
        pricePerLiter?.let { return it }
        val l = resolvedLiters()
        val total = totalCost
        if (l != null && total != null && l > 0.0) return total / l
        return null
    }

    fun resolvedTotal(): Double? {
        totalCost?.let { return it }
        val l = resolvedLiters()
        val price = resolvedPrice()
        if (l != null && price != null) return l * price
        return null
    }
}

object ReceiptParser {
    private val stations = listOf(
        "газпромнефть", "газпром", "роснефть", "лукойл", "татнефть", "башнефть",
        "сургутнефтегаз", "shell", "bp", "ека", "eka", "птк", "ирбис",
        "нефтьмагистраль", "яндекс", "yandex", "азс",
    )

    fun parse(raw: String): ParsedReceipt {
        val text = compactDigitSpaces(raw)
        val liters = findLiters(text)
        val price = findPricePerLiter(text)
        val total = findTotal(text, price)
        val date = findDate(text)
        val station = findStation(raw)
        val resolved = ParsedReceipt(
            liters = liters,
            pricePerLiter = price,
            totalCost = total,
            date = date,
            station = station,
        )
        return resolved.copy(
            liters = resolved.resolvedLiters()?.let { round3(it) },
            pricePerLiter = resolved.resolvedPrice()?.let { round2(it) },
            totalCost = resolved.resolvedTotal()?.let { round2(it) },
        )
    }

    internal fun compactDigitSpaces(text: String): String {
        var t = text.replace('\u00A0', ' ').replace('\u202F', ' ')
        val thousands = Regex("""(?<!\d)(\d{1,3})[ ](\d{3})(?!\d)""")
        while (thousands.containsMatchIn(t)) {
            t = thousands.replace(t, "$1$2")
        }
        return t
    }

    private fun findLiters(text: String): Double? {
        val labeled = Regex(
            """(\d+(?:[.,]\d{1,3})?)\s*(?:л|литр(?:а|ов)?|l)(?!\p{L})""",
            RegexOption.IGNORE_CASE,
        )
        labeled.findAll(text).mapNotNull { it.groupValues[1].toDotDouble() }
            .firstOrNull { it in 3.0..80.0 }
            ?.let { return it }

        val afterWord = Regex(
            """(?:объем|объём|залито|количество)\D{0,12}(\d+(?:[.,]\d{1,3})?)""",
            RegexOption.IGNORE_CASE,
        )
        return afterWord.find(text)?.groupValues?.get(1)?.toDotDouble()?.takeIf { it in 3.0..80.0 }
    }

    private fun findPricePerLiter(text: String): Double? {
        val labeled = Regex(
            """(\d+(?:[.,]\d{1,2})?)\s*(?:₽|руб(?:лей)?|р\.?|rub)?\s*/\s*(?:л|l)(?!\p{L})""",
            RegexOption.IGNORE_CASE,
        )
        labeled.findAll(text).mapNotNull { it.groupValues[1].toDotDouble() }
            .firstOrNull { it in 20.0..150.0 }
            ?.let { return it }
        return null
    }

    private fun findTotal(text: String, price: Double?): Double? {
        val labeled = Regex(
            """(\d+(?:[.,]\d{1,2})?)\s*(?:₽|руб(?:лей)?|р\.?|rub)(?!\p{L})""",
            RegexOption.IGNORE_CASE,
        )
        val money = labeled.findAll(text).mapNotNull { it.groupValues[1].toDotDouble() }
            .filter { it in 150.0..20_000.0 }
            .filter { price == null || kotlin.math.abs(it - price) > 1.0 }
            .toList()
        return money.maxOrNull()
    }

    private fun findDate(text: String): String? {
        val regex = Regex("""(?<![\d])(\d{1,2})[./](\d{1,2})[./](\d{2,4})(?![\d])""")
        for (m in regex.findAll(text)) {
            val d = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            var year = m.groupValues[3].toInt()
            if (year < 100) year += 2000
            if (d in 1..31 && month in 1..12 && year in 2000..2100) {
                return "%04d-%02d-%02d".format(year, month, d)
            }
        }
        return null
    }

    private fun findStation(raw: String): String? {
        val lines = raw.lines().map { it.trim() }.filter { it.length in 3..48 }
        for (line in lines) {
            val lower = line.lowercase()
            if (stations.any { lower.contains(it) }) {
                return line.replace(Regex("""\s+"""), " ").take(48)
            }
        }
        return null
    }

    private fun String.toDotDouble(): Double? = replace(',', '.').toDoubleOrNull()

    private fun round2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0
    private fun round3(value: Double): Double = kotlin.math.round(value * 1000.0) / 1000.0
}
