package life.myluck.w124.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {
    @Test
    fun sberStyleReceipt() {
        val text = """
            Успешно
            26.08.2026 14:03
            АЗС Роснефть
            2 345,50 ₽
            42,31 л
            55,44 ₽/л
        """.trimIndent()
        val parsed = ReceiptParser.parse(text)
        assertEquals(42.31, parsed.liters!!, 0.001)
        assertEquals(55.44, parsed.pricePerLiter!!, 0.01)
        assertEquals(2345.50, parsed.totalCost!!, 0.05)
        assertEquals("2026-08-26", parsed.date)
        assertTrue(parsed.station!!.contains("Роснефть", ignoreCase = true))
    }

    @Test
    fun tinkoffTextShare() {
        val text = """
            Покупка
            Газпромнефть АЗС 42
            1980.00 RUB
            Объем 35,6 л
        """.trimIndent()
        val parsed = ReceiptParser.parse(text)
        assertEquals(35.6, parsed.liters!!, 0.001)
        assertEquals(1980.00, parsed.totalCost!!, 0.05)
        assertEquals(55.62, parsed.pricePerLiter!!, 0.05)
    }

    @Test
    fun litersFromPriceAndTotal() {
        val text = """
            55,20 ₽/л
            2 208 ₽
        """.trimIndent()
        val parsed = ReceiptParser.parse(text)
        assertEquals(40.0, parsed.liters!!, 0.05)
        assertEquals(55.20, parsed.pricePerLiter!!, 0.01)
        assertEquals(2208.0, parsed.totalCost!!, 0.2)
    }

    @Test
    fun ignoresBarePriceAsLiters() {
        val text = "Чек 55,40 ₽"
        val parsed = ReceiptParser.parse(text)
        assertNull(parsed.liters)
    }

    @Test
    fun apkNameRoundtrip() {
        val name = AppVersion.apkFileName(2, "0.2.0")
        assertEquals("bortzhurnal-2-0.2.0.apk", name)
        assertEquals(2 to "0.2.0", AppVersion.parseApkName(name))
    }
}
