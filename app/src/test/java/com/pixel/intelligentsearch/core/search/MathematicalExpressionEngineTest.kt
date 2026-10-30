package com.pixel.intelligentsearch.core.search

import org.junit.Assert.*
import org.junit.Test

class MathematicalExpressionEngineTest {

    private fun computed(expression: String): String? =
        (MathematicalExpressionEngine.evaluate(expression) as? MathematicalExpressionEngine.MathEvaluationResult.Computation)?.formattedResult

    @Test
    fun testFourOperationsAndErrors() {
        assertEquals("4", computed("2+2"))
        assertEquals("5", computed("10-5"))
        assertEquals("9", computed("3*3"))
        assertEquals("4", computed("8/2"))
        assertNull(computed("5/0"))
        assertNull(computed("2+a"))
    }

    @Test
    fun testBasicArithmetic() {
        val res = MathematicalExpressionEngine.evaluate("2 + 2 * 3")
        assertTrue(res is MathematicalExpressionEngine.MathEvaluationResult.Computation)
        val comp = res as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals(8.0, comp.numericValue, 0.001)
        assertEquals("8", comp.formattedResult)
    }

    @Test
    fun testScientificFunctions() {
        val resSqrt = MathematicalExpressionEngine.evaluate("sqrt(144)")
        assertTrue(resSqrt is MathematicalExpressionEngine.MathEvaluationResult.Computation)
        assertEquals(12.0, (resSqrt as MathematicalExpressionEngine.MathEvaluationResult.Computation).numericValue, 0.001)

        val resFact = MathematicalExpressionEngine.evaluate("5!")
        assertTrue(resFact is MathematicalExpressionEngine.MathEvaluationResult.Computation)
        assertEquals(120.0, (resFact as MathematicalExpressionEngine.MathEvaluationResult.Computation).numericValue, 0.001)

        val resTrig = MathematicalExpressionEngine.evaluate("sin(pi/2)")
        assertTrue(resTrig is MathematicalExpressionEngine.MathEvaluationResult.Computation)
        assertEquals(1.0, (resTrig as MathematicalExpressionEngine.MathEvaluationResult.Computation).numericValue, 0.001)
    }

    @Test
    fun testBitwiseAndBaseConversions() {
        val bitwiseRes = MathematicalExpressionEngine.evaluate("0xFF AND 0x0F")
        assertTrue(bitwiseRes is MathematicalExpressionEngine.MathEvaluationResult.Bitwise)
        val bit = bitwiseRes as MathematicalExpressionEngine.MathEvaluationResult.Bitwise
        assertEquals(15L, bit.decimalValue)
        assertEquals("0xF", bit.hexValue)

        val shiftRes = MathematicalExpressionEngine.evaluate("1 << 8")
        assertTrue(shiftRes is MathematicalExpressionEngine.MathEvaluationResult.Bitwise)
        assertEquals(256L, (shiftRes as MathematicalExpressionEngine.MathEvaluationResult.Bitwise).decimalValue)

        val baseRes = MathematicalExpressionEngine.evaluate("255 to hex")
        assertTrue(baseRes is MathematicalExpressionEngine.MathEvaluationResult.Bitwise)
        val bitwise = baseRes as MathematicalExpressionEngine.MathEvaluationResult.Bitwise
        assertEquals(255L, bitwise.decimalValue)
        assertEquals("0xFF", bitwise.hexValue)
    }

    @Test
    fun testUnitAndCurrencyConversion() {
        val lengthRes = MathematicalExpressionEngine.evaluate("10 km to m")
        assertTrue(lengthRes is MathematicalExpressionEngine.MathEvaluationResult.UnitConversion)
        val conv = lengthRes as MathematicalExpressionEngine.MathEvaluationResult.UnitConversion
        assertEquals(10000.0, conv.toValue, 0.1)

        val tempRes = MathematicalExpressionEngine.evaluate("32 f to c")
        assertTrue(tempRes is MathematicalExpressionEngine.MathEvaluationResult.UnitConversion)
        assertEquals(0.0, (tempRes as MathematicalExpressionEngine.MathEvaluationResult.UnitConversion).toValue, 0.1)

        val currRes = MathematicalExpressionEngine.evaluate("100 usd to eur")
        assertTrue(currRes is MathematicalExpressionEngine.MathEvaluationResult.UnitConversion)
        assertEquals(92.0, (currRes as MathematicalExpressionEngine.MathEvaluationResult.UnitConversion).toValue, 1.0)
    }

    @Test
    fun testUnsigned64BitMask() {
        val maskRes = MathematicalExpressionEngine.evaluate("0xFFFFFFFFFFFFFFFF AND 0x0F")
        assertTrue(maskRes is MathematicalExpressionEngine.MathEvaluationResult.Bitwise)
        val bit = maskRes as MathematicalExpressionEngine.MathEvaluationResult.Bitwise
        assertEquals(15L, bit.decimalValue)
    }

    @Test
    fun testTinyNumberFormat() {
        val res = MathematicalExpressionEngine.evaluate("1e-18")
        assertTrue(res is MathematicalExpressionEngine.MathEvaluationResult.Computation)
        val comp = res as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals("1.0000e-18", comp.formattedResult)
    }

    @Test
    fun testUnsigned64BitDecimalConversion() {
        val res = MathematicalExpressionEngine.evaluate("0xFFFFFFFFFFFFFFFF to dec")
        assertTrue(res is MathematicalExpressionEngine.MathEvaluationResult.Bitwise)
        val bit = res as MathematicalExpressionEngine.MathEvaluationResult.Bitwise
        assertEquals("0xFFFFFFFFFFFFFFFF to dec = 18446744073709551615", bit.expression)
    }

    @Test
    fun testNegativeTemperatureConversion() {
        val res = MathematicalExpressionEngine.evaluate("-40 f to c")
        assertTrue(res is MathematicalExpressionEngine.MathEvaluationResult.UnitConversion)
        val conv = res as MathematicalExpressionEngine.MathEvaluationResult.UnitConversion
        assertEquals(-40.0, conv.toValue, 0.1)
    }

    @Test
    fun testPureScientificNotation() {
        val res = MathematicalExpressionEngine.evaluate("1e6")
        assertTrue("Pure 1e6 must evaluate to Computation", res is MathematicalExpressionEngine.MathEvaluationResult.Computation)
        val comp = res as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals(1000000.0, comp.numericValue, 0.001)
        assertEquals("1000000", comp.formattedResult)
    }

    @Test
    fun testDivisionAndModuloByZeroSafety() {
        val divZero = MathematicalExpressionEngine.evaluate("10 / 0")
        assertNull("Division by zero must safely evaluate to null", divZero)

        val modZero = MathematicalExpressionEngine.evaluate("10 % 0")
        assertNull("Modulo by zero must safely evaluate to null", modZero)
    }

    @Test
    fun testFunctionWithoutParens() {
        val res = MathematicalExpressionEngine.evaluate("sqrt 144")
        assertTrue("sqrt without parens must evaluate to Computation", res is MathematicalExpressionEngine.MathEvaluationResult.Computation)
        val comp = res as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals(12.0, comp.numericValue, 0.001)
    }

    @Test
    fun testBitwiseNot() {
        val res = MathematicalExpressionEngine.evaluate("~0x0F")
        assertTrue("Bitwise NOT must return Bitwise result", res is MathematicalExpressionEngine.MathEvaluationResult.Bitwise)
        val bit = res as MathematicalExpressionEngine.MathEvaluationResult.Bitwise
        assertEquals(15L.inv(), bit.decimalValue)
    }

    @Test
    fun phoneNumbersAndDatesAreNotSubtraction() {
        assertNull(MathematicalExpressionEngine.evaluate("555-1234"))
        assertNull(MathematicalExpressionEngine.evaluate("1-800-555-1234"))
        assertNull(MathematicalExpressionEngine.evaluate("2024-01-01"))
        assertNull(MathematicalExpressionEngine.evaluate("01-31-2024"))
        // Plain subtraction still works.
        val res = MathematicalExpressionEngine.evaluate("100-20-5")
        assertEquals(75.0, (res as MathematicalExpressionEngine.MathEvaluationResult.Computation).numericValue, 0.001)
    }

    @Test
    fun aLoneSignedNumberIsNotACalculation() {
        // International phone numbers are typed with a plus; echoing them back as "= 14155552671" is noise.
        assertNull(MathematicalExpressionEngine.evaluate("+14155552671"))
        assertNull(MathematicalExpressionEngine.evaluate("+447700900123"))
        assertNull(MathematicalExpressionEngine.evaluate("-5"))
        // A sign in front of real arithmetic still counts.
        val sum = MathematicalExpressionEngine.evaluate("-5+3") as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals(-2.0, sum.numericValue, 0.0)
    }

    @Test
    fun roundIsHalfAwayFromZero() {
        val up = MathematicalExpressionEngine.evaluate("round(2.5)") as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals(3.0, up.numericValue, 0.0)
        val down = MathematicalExpressionEngine.evaluate("round(-2.5)") as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals(-3.0, down.numericValue, 0.0)
    }

    @Test
    fun smallResultsKeepSignificantDigits() {
        // Ten significant digits, not four: 1/3000 was 0.0003333 and 1/3 was 0.3333.
        val tiny = MathematicalExpressionEngine.evaluate("1/3000") as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals("0.0003333333333", tiny.formattedResult)
        val third = MathematicalExpressionEngine.evaluate("1/3") as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals("0.3333333333", third.formattedResult)
        val sum = MathematicalExpressionEngine.evaluate("0.1+0.2") as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals("0.3", sum.formattedResult)
    }

    @Test
    fun thousandsSeparatorsInNumericExpressions() {
        val res = MathematicalExpressionEngine.evaluate("1,000*2") as MathematicalExpressionEngine.MathEvaluationResult.Computation
        assertEquals(2000.0, res.numericValue, 0.001)
    }

    @Test
    fun shiftsOfSixtyFourOrMoreAreNotAnswered() {
        val ok = MathematicalExpressionEngine.evaluate("1 << 10") as MathematicalExpressionEngine.MathEvaluationResult.Bitwise
        assertEquals(1024L, ok.decimalValue)
        assertNull(MathematicalExpressionEngine.evaluate("1 << 64"))
    }

    private fun converted(query: String): MathematicalExpressionEngine.MathEvaluationResult.UnitConversion? =
        MathematicalExpressionEngine.evaluate(query) as? MathematicalExpressionEngine.MathEvaluationResult.UnitConversion

    private fun <T> inLocale(locale: java.util.Locale, block: () -> T): T {
        val saved = java.util.Locale.getDefault()
        java.util.Locale.setDefault(locale)
        try {
            return block()
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }

    @Test
    fun percentagesAreHundredthsAndAddOrTakeOfTheLeft() {
        // "%" was only modulo: "50%" had no answer and "80 + 15%" was 95.
        assertEquals("0.5", computed("50%"))
        assertEquals("12", computed("15% of 80"))
        assertEquals("92", computed("80 + 15%"))
        assertEquals("68", computed("80 - 15%"))
        assertEquals("47.992", computed("20% off 59.99"))
        assertEquals("30", computed("200 * 15%"))
        // Between two numbers it is still modulo.
        assertEquals("1", computed("10 % 3"))
        assertEquals("1", computed("10%3"))
    }

    @Test
    fun aDotGroupsThousandsWhereTheCommaIsTheDecimalPoint() {
        inLocale(java.util.Locale.GERMANY) {
            // "1.000" was read as 1.0, so "1.000 + 1" gave 2. Answers are written with the comma too.
            assertEquals("1001", computed("1.000 + 1"))
            assertEquals("1234567,5", computed("1.234.567,5 * 1"))
            assertEquals("2,5", computed("1,5 + 1"))
            assertEquals("2,5", computed("1.5 + 1"))
            assertEquals("0,25", computed("0.125 * 2"))
            assertEquals(1500.0, converted("1,5 km in m")!!.toValue, 1e-9)
            assertEquals(1_000_000.0, converted("1.000 km in m")!!.toValue, 1e-9)
        }
        inLocale(java.util.Locale.US) {
            assertEquals("2.5", computed("1.5 + 1"))
            assertEquals("1001", computed("1,000 + 1"))
            assertEquals(1500.0, converted("1.5 km in m")!!.toValue, 1e-9)
            assertEquals(1_000_000.0, converted("1,000 km in m")!!.toValue, 1e-9)
        }
    }

    @Test
    fun andOrWordsAreBitwiseOnlyBesidePrefixedNumbers() {
        // "10 or 12" showed 14 and "1 and 2" showed 0.
        assertNull(MathematicalExpressionEngine.evaluate("10 or 12"))
        assertNull(MathematicalExpressionEngine.evaluate("1 and 2"))
        val and = MathematicalExpressionEngine.evaluate("0xFF and 15") as MathematicalExpressionEngine.MathEvaluationResult.Bitwise
        assertEquals(15L, and.decimalValue)
        val or = MathematicalExpressionEngine.evaluate("10 | 12") as MathematicalExpressionEngine.MathEvaluationResult.Bitwise
        assertEquals(14L, or.decimalValue)
        val ampersand = MathematicalExpressionEngine.evaluate("12 & 10") as MathematicalExpressionEngine.MathEvaluationResult.Bitwise
        assertEquals(8L, ampersand.decimalValue)
    }

    @Test
    fun aTonIsTheShortTonInTheUsAndATonneElsewhere() {
        inLocale(java.util.Locale.US) {
            assertEquals(2000.0, converted("1 ton in lbs")!!.toValue, 1e-6)
            assertEquals(2204.6226, converted("1 tonne in lbs")!!.toValue, 1e-3)
            assertEquals(1000.0, converted("1 metric ton in kg")!!.toValue, 1e-9)
        }
        inLocale(java.util.Locale.UK) {
            assertEquals(2204.6226, converted("1 ton in lbs")!!.toValue, 1e-3)
            assertEquals(2000.0, converted("1 short ton in lbs")!!.toValue, 1e-6)
        }
    }

    @Test
    fun fluidOuncesSquareUnitsAndKitchenMeasures() {
        val floz = converted("8 fl oz to ml")!!
        assertEquals(236.588, floz.toValue, 1e-3)
        assertEquals("8 fl oz = 236.588 ml", floz.formatted)
        // An ounce converted to a volume can only be a fluid ounce.
        assertEquals(236.588, converted("8 oz to ml")!!.toValue, 1e-3)
        assertEquals(0.236588, converted("8 oz to l")!!.toValue, 1e-6)
        assertEquals(226.796, converted("8 oz to g")!!.toValue, 1e-3)
        assertEquals(9.2903, converted("100 sq ft in sq m")!!.toValue, 1e-3)
        assertEquals(3.0, converted("1 tbsp to tsp")!!.toValue, 1e-3)
        assertEquals(16.0, converted("1 cup to tbsp")!!.toValue, 1e-2)
    }

    @Test
    fun pressureAndEnergyConvert() {
        assertEquals(14.5038, converted("1 bar to psi")!!.toValue, 1e-3)
        assertEquals(100.0, converted("1 bar to kpa")!!.toValue, 1e-9)
        assertEquals(4.184, converted("1 kcal to kj")!!.toValue, 1e-9)
        assertEquals(3600.0, converted("1 kwh to kj")!!.toValue, 1e-9)
    }

    @Test
    fun megabitsAndMegabytesAreDistinguished() {
        val bits = MathematicalExpressionEngine.evaluate("100 Mb to MB") as MathematicalExpressionEngine.MathEvaluationResult.UnitConversion
        assertEquals(12.5, bits.toValue, 0.0001)
        val bytes = MathematicalExpressionEngine.evaluate("1 MB to kb") as MathematicalExpressionEngine.MathEvaluationResult.UnitConversion
        assertEquals(1000.0, bytes.toValue, 0.0001)
    }

    private fun bitwise(query: String) =
        MathematicalExpressionEngine.evaluate(query) as? MathematicalExpressionEngine.MathEvaluationResult.Bitwise

    @Test
    fun basesCanBeNamedInFullAndBareHexIsRead() {
        // Only "hex", "dec", "bin" and "oct" were understood: "0xff to binary" had no answer.
        assertEquals("0xff to bin = 0b11111111", bitwise("0xff to binary")?.expression)
        assertEquals("0b1010 to hex = 0xA", bitwise("0b1010 to hex")?.expression)
        assertEquals("255 to bin = 0b11111111", bitwise("255 to binary")?.expression)
        assertEquals("255 to hex = 0xFF", bitwise("255 in hexadecimal")?.expression)
        assertEquals("8 to oct = 0o10", bitwise("8 to octal")?.expression)
        assertEquals("ff to dec = 255", bitwise("ff in decimal")?.expression)
        assertEquals("0x1F to dec = 31", bitwise("0x1F to decimal")?.expression)
    }

    @Test
    fun currencyNamesConvertLikeCodes() {
        // Only codes and symbols were understood: "50 euros in dollars" had no answer.
        val euros = converted("50 euros in dollars")!!
        assertEquals("EUR", euros.fromUnit)
        assertEquals("USD", euros.toUnit)
        assertEquals(50 / 0.92, euros.toValue, 1e-9)
        assertEquals("USD", converted("50 dollars to euros")!!.fromUnit)
        assertEquals("JPY", converted("100 yen to usd")!!.fromUnit)
        assertEquals("GBP", converted("20 pounds to euro")!!.fromUnit)
        assertEquals("CAD", converted("10 canadian dollars to us dollars")?.fromUnit)
        assertEquals("USD", converted("10 canadian dollars to us dollars")?.toUnit)
        assertEquals("EUR", converted("euro to dollar")?.fromUnit)
        // A pound is still a weight beside one.
        assertEquals(0.45359237, converted("1 pound to kg")!!.toValue, 1e-9)
        assertNull(MathematicalExpressionEngine.evaluate("time to go"))
    }

    @Test
    fun moneyHasTheCurrencysDecimalsAndGrouping() {
        inLocale(java.util.Locale.US) {
            // Written with 4 decimals and no grouping: "1000000 USD ≈ 155200000 JPY", "100 USD ≈ 92 EUR".
            assertEquals("1,000,000 USD ≈ 155,200,000 JPY", converted("1000000 usd to jpy")!!.formatted)
            assertEquals("100 USD ≈ 92.00 EUR", converted("100 usd to eur")!!.formatted)
            assertEquals("1,234.50 USD ≈ 1,135.74 EUR", converted("1234.5 usd to eur")!!.formatted)
            assertEquals("1 USD ≈ 1,370 KRW", converted("usd to krw")!!.formatted)
        }
        inLocale(java.util.Locale.GERMANY) {
            assertEquals("1.234,50 USD ≈ 1.135,74 EUR", converted("1234,5 usd to eur")!!.formatted)
        }
    }

    @Test
    fun phoneNumbersAndZipCodesWithDashesAreNotSubtraction() {
        for (query in listOf("030-12345678", "0171-1234567", "98765-43210", "020-7946-0958", "0044-20-7946-0958", "94103-1234", "555-1234", "1-800-555-0199")) {
            assertNull(query, MathematicalExpressionEngine.evaluate(query))
        }
        // Sums with dashes still are.
        assertEquals("7", computed("10-3"))
        assertEquals("25", computed("2024-1999"))
        assertEquals("75", computed("100-20-5"))
        assertEquals("8500", computed("12000-3500"))
    }

    @Test
    fun roundingLeftOverFromAZeroIsZeroAndSmallDifferencesShow() {
        // sin(pi) showed 1.2246e-16, tan(pi/2) 1.6331e+16, 1.00001 showed as 1 and 2^50 as 1.1259e+15.
        assertEquals("0", computed("sin(pi)"))
        assertEquals("0", computed("cos(pi/2)"))
        assertEquals("0", computed("0.1+0.2-0.3"))
        assertEquals("0", computed("0.3-0.1-0.2"))
        assertEquals("0", computed("tan(pi)"))
        assertNull(computed("tan(pi/2)"))
        assertEquals("1", computed("tan(pi/4)"))
        assertEquals("0.5", computed("sin(pi/6)"))
        assertEquals("1.00001", computed("1.00001*1"))
        assertEquals("2.99999", computed("3-0.00001"))
        assertEquals("1125899906842624", computed("2^50"))
        assertEquals("1000000000000000", computed("999999999999999+1"))
        assertEquals("4611686018427387904", computed("2^62"))
        assertEquals("9.2234e+18", computed("2^63"))
        // A real difference far below the numbers is still shown.
        assertEquals("1.0000e-07", computed("1e6-999999.9999999"))
        assertEquals("3.141592654", computed("pi*1"))
    }

    @Test
    fun aYearIs365DaysAndAMonthATwelfthOfIt() {
        // A year was 365.2425 days and a month 30.4369.
        assertEquals("1 year = 365 days", converted("1 year to days")!!.formatted)
        assertEquals("1 month = 30.4167 days", converted("1 month to days")!!.formatted)
        assertEquals(12.0, converted("1 year to months")!!.toValue, 1e-9)
    }

    @Test
    fun squareAndCubicUnitsWithDigitsOrWordsConvert() {
        // "m2", "km2" and "m3" could never match: units had no digits.
        assertEquals(10.7639, converted("1 m2 to ft2")!!.toValue, 1e-3)
        assertEquals(100.0, converted("1 km2 to ha")!!.toValue, 1e-9)
        assertEquals(1000.0, converted("1 m3 to l")!!.toValue, 1e-9)
        assertEquals(1000.0, converted("1 m³ to l")!!.toValue, 1e-9)
        assertEquals(10.7639, converted("1 sq m to sq ft")!!.toValue, 1e-3)
        assertEquals(9.2903, converted("100 square feet to square meters")!!.toValue, 1e-3)
        assertEquals(35.3147, converted("1 cubic meter to cubic feet")!!.toValue, 1e-3)
        assertEquals("1 sq mi = 2.58999 km2", converted("1 square mile in km2")!!.formatted)
    }

    @Test
    fun aDecimalCommaWorksInFunctionsAndIsWrittenInAnswers() {
        inLocale(java.util.Locale.GERMANY) {
            assertEquals("2,5", computed("sqrt(6,25)"))
            assertEquals("7,853981634", computed("2,5*pi"))
            assertEquals("125,125", computed("1001/8"))
            assertEquals("1,5 km = 1500 m", converted("1,5 km in m")!!.formatted)
        }
        inLocale(java.util.Locale.FRANCE) {
            assertEquals("2,5", computed("sqrt(6,25)"))
        }
        inLocale(java.util.Locale.US) {
            assertEquals("1002", computed("1,000+2"))
            assertEquals("125.125", computed("1001/8"))
            assertEquals("31.6227766", computed("sqrt(1,000)"))
            assertNull(computed("1,5+2,25"))
        }
    }

    @Test
    fun aPlainGallonOrPintIsImperialInTheUkAndIreland() {
        inLocale(java.util.Locale.UK) {
            // Under en-GB a gallon was the US one, 3.785 l.
            assertEquals(4.54609, converted("1 gallon to liters")!!.toValue, 1e-9)
            assertEquals(568.26125, converted("1 pint to ml")!!.toValue, 1e-6)
            assertEquals(3.78541, converted("1 us gallon to liters")!!.toValue, 1e-9)
        }
        inLocale(java.util.Locale.forLanguageTag("en-IE")) {
            assertEquals(4.54609, converted("1 gal to l")!!.toValue, 1e-9)
        }
        inLocale(java.util.Locale.US) {
            assertEquals(3.78541, converted("1 gallon to liters")!!.toValue, 1e-9)
            assertEquals(4.54609, converted("1 imperial gallon to liters")!!.toValue, 1e-9)
            assertEquals(568.26125, converted("1 uk pint to ml")!!.toValue, 1e-6)
            assertEquals("2 imp gal = 2.4019 US gal", converted("2 imperial gallons in us gallons")!!.formatted)
            assertEquals(28.4130625, converted("1 imperial fl oz to ml")!!.toValue, 1e-6)
        }
    }

    @Test
    fun calculatorPhrasingsAreAnswered() {
        // Each had no answer.
        assertEquals("4", computed("2+2="))
        assertEquals("4", computed("=2+2"))
        assertEquals("4", computed("what is 2+2"))
        assertEquals("4", computed("What is 2+2?"))
        assertEquals("12", computed("what's 15% of 80"))
        assertEquals("4", computed("√16"))
        assertEquals("4", computed("√(16)"))
        assertEquals("8", computed("2√16"))
        assertEquals("27", computed("3(4+5)"))
        assertEquals("6", computed("(2)(3)"))
        assertEquals("7.283185307", computed("2pi+1"))
        // Words that aren't a sum stay searches.
        assertNull(MathematicalExpressionEngine.evaluate("what is love"))
        assertNull(MathematicalExpressionEngine.evaluate("what is pi"))
        assertNull(MathematicalExpressionEngine.evaluate("="))
        assertNull(MathematicalExpressionEngine.evaluate("call 3 (555)"))
        assertNull(MathematicalExpressionEngine.evaluate("(555) 1234"))
    }

    @Test
    fun unitAndCurrencyNamesAreKnownToTheSpellChecker() {
        for (word in listOf("km", "ft2", "m3", "usd", "euros", "yen", "kwh", "mph", "°F")) {
            assertTrue(word, MathematicalExpressionEngine.isUnitName(word))
        }
        for (word in listOf("post", "pst", "hello")) {
            assertFalse(word, MathematicalExpressionEngine.isUnitName(word))
        }
    }
}
