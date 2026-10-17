package com.ngdesigns.intelligentsearch.engine.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculatorTest {
    private fun number(q: String) = Calculator.evaluate(q) as? Answer.Number
    private fun shown(q: String) = number(q)?.formatted
    private fun bits(q: String) = Calculator.evaluate(q) as Answer.Bits
    private fun converted(q: String) = (Calculator.evaluate(q) as Answer.Conversion).toValue

    @Test fun arithmetic() {
        assertEquals("4", shown("2+2"))
        assertEquals("5", shown("10-5"))
        assertEquals("9", shown("3*3"))
        assertEquals("4", shown("8/2"))
        assertEquals("8", shown("2 + 2 * 3"))
        assertEquals("75", shown("100-20-5"))
        assertEquals("2000", shown("1,000*2"))
        assertEquals("14", shown("2(3+4)"))
        assertEquals("6", shown("12 ÷ 2"))
        assertEquals("1024", shown("2^10"))
    }

    @Test fun refusesWhatIsNotACalculation() {
        for (q in listOf("5/0", "10 / 0", "10 % 0", "2+a", "hello", "42", "1,000", "e", "2e", "3 apples", "")) {
            assertNull(q, Calculator.evaluate(q))
        }
    }

    @Test fun phoneNumbersAndDatesAreNotSubtractions() {
        for (q in listOf("555-1234", "1-800-555-1234", "2024-01-01", "01-31-2024", "(555) 123-4567", "12/31/2024")) {
            assertNull(q, Calculator.evaluate(q))
        }
    }

    @Test fun functionsAndConstants() {
        assertEquals(12.0, number("sqrt(144)")!!.value, 1e-9)
        assertEquals(12.0, number("sqrt 144")!!.value, 1e-9)
        assertEquals(120.0, number("5!")!!.value, 1e-9)
        assertEquals(1.0, number("sin(pi/2)")!!.value, 1e-9)
        assertEquals(2.0, number("log(100)")!!.value, 1e-9)
        assertEquals(3.0, number("round(2.5)")!!.value, 0.0)
        assertEquals(-3.0, number("round(-2.5)")!!.value, 0.0)
        assertEquals(Math.PI * 2, number("2pi")!!.value, 1e-9)
    }

    @Test fun percentagesWorkLikeACalculator() {
        assertEquals(1283.75, number("(1024 + 35%) − 98.65")!!.value, 1e-9)
        assertEquals(30.0, number("20% of 150")!!.value, 1e-9)
        assertEquals(1.0, number("10 % 3")!!.value, 1e-9)
        assertEquals(90.0, number("100 - 10%")!!.value, 1e-9)
    }

    @Test fun formatting() {
        assertEquals("1.0000e-18", shown("1e-18"))
        assertEquals("1000000", shown("1e6"))
        assertEquals("0.0003333", shown("1/3000"))
        assertEquals("0.3333", shown("1/3"))
        assertEquals("0.3", shown("0.1+0.2"))
    }

    @Test fun bitwiseAndBases() {
        assertEquals(15L, bits("0xFF AND 0x0F").value)
        assertEquals("0xF", bits("0xFF AND 0x0F").hex)
        assertEquals(15L, bits("0xFFFFFFFFFFFFFFFF AND 0x0F").value)
        assertEquals(256L, bits("1 << 8").value)
        assertEquals(1024L, bits("1 << 10").value)
        assertNull(Calculator.evaluate("1 << 64"))
        assertEquals(15L.inv(), bits("~0x0F").value)
        assertEquals("0xFF", bits("255 to hex").hex)
        assertEquals("0xFFFFFFFFFFFFFFFF to dec = 18446744073709551615", bits("0xFFFFFFFFFFFFFFFF to dec").expression)
        assertEquals("0b1010", bits("10 to binary").binary)
    }

    @Test fun unitConversions() {
        assertEquals(10000.0, converted("10 km to m"), 1e-6)
        assertEquals(0.0, converted("32 f to c"), 1e-6)
        assertEquals(-40.0, converted("-40 f to c"), 1e-6)
        assertEquals(273.15, converted("0 c in kelvin"), 1e-6)
        assertEquals(1.609344, converted("1 mile in km"), 1e-9)
        assertEquals(30.48, converted("1 foot to cm"), 1e-9)
        assertEquals(2.20462, converted("1 kg to lb"), 1e-4)
        assertEquals(3.78541, converted("1 gallon to litres"), 1e-4)
        assertEquals(100.0, converted("62.1371 mph to km/h"), 1e-3)
        assertEquals(1.5, converted("90 minutes to hours"), 1e-9)
        assertEquals(12.5, converted("100 Mb to MB"), 1e-9)
        assertEquals(1000.0, converted("1 MB to kb"), 1e-9)
        assertEquals(1024.0, converted("1 MiB to KiB"), 1e-9)
    }

    @Test fun currencyUsesSuppliedRatesAndMarksFallbackAsApproximate() {
        val fallback = Calculator.evaluate("100 usd to eur") as Answer.Conversion
        assertEquals(92.0, fallback.toValue, 1.0)
        assertTrue(fallback.approximate)
        val fetched = object : CurrencyRates {
            override val approximate = false
            override fun perEuro(code: String) = mapOf("EUR" to 1.0, "USD" to 1.25)[code]
        }
        val exact = Calculator.evaluate("$50 to eur", fetched) as Answer.Conversion
        assertEquals(40.0, exact.toValue, 1e-9)
        assertEquals(false, exact.approximate)
        assertNull(Calculator.evaluate("10 km to kg"))
    }
}
