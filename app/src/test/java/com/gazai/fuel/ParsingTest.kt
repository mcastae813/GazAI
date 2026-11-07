package com.gazai.fuel

import com.gazai.fuel.ai.OpenRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric gives us the real org.json implementation.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ParsingTest {
    @Test
    fun numbers() {
        assertEquals(12.345, OpenRouter.parseNumber("12.345")!!, 1e-9)
        assertEquals(12.345, OpenRouter.parseNumber("12,345 L")!!, 1e-9)
        assertEquals(1234.56, OpenRouter.parseNumber("1,234.56")!!, 1e-9)
        assertEquals(1234.56, OpenRouter.parseNumber("1.234,56")!!, 1e-9)
        assertEquals(45.2, OpenRouter.parseNumber("$45.20")!!, 1e-9)
        assertEquals(850.0, OpenRouter.parseNumber("850.00 MXN")!!, 1e-9)
        assertNull(OpenRouter.parseNumber("n/a"))
    }

    @Test
    fun moneyWithThreeDigitsAfterOneSeparatorIsThousands() {
        assertEquals(1050.0, OpenRouter.parseNumber("1,050", money = true)!!, 1e-9)
        assertEquals(1050.0, OpenRouter.parseNumber("$ 1.050", money = true)!!, 1e-9)
        assertEquals(45.2, OpenRouter.parseNumber("45,20", money = true)!!, 1e-9)
        assertEquals(1234.56, OpenRouter.parseNumber("1.234,56", money = true)!!, 1e-9)
        // Volumes keep reading a single separator as the decimal point.
        assertEquals(12.345, OpenRouter.parseNumber("12,345")!!, 1e-9)
    }

    @Test
    fun totalCostStringIsReadAsMoney() {
        val p = OpenRouter.parseReply("{\"volume\": \"40,250\", \"total_cost\": \"1,050\", \"price_per_unit\": null}")
        assertEquals(40.25, p.volume!!, 1e-9)
        assertEquals(1050.0, p.totalCost!!, 1e-9)
    }

    @Test
    fun replyInMarkdownWithStrings() {
        val reply = "Sure! Here it is:\n```json\n{\"vehicle\": null, \"date\": \"2026-11-03\", \"odometer\": null, " +
            "\"volume\": \"32,415\", \"volume_unit\": \"L\", \"total_cost\": \"$ 789.50\", \"price_per_unit\": 24.36, " +
            "\"full_tank\": \"true\", \"station\": null}\n```"
        val p = OpenRouter.parseReply(reply)
        assertEquals(32.415, p.volume!!, 1e-9)
        assertEquals(789.5, p.totalCost!!, 1e-9)
        assertEquals(true, p.fullTank)
        assertTrue(p.isComplete)
    }

    @Test
    fun allNullReplyIsNotComplete() {
        val p = OpenRouter.parseReply("{\"volume\": null, \"total_cost\": null, \"price_per_unit\": null}")
        assertFalse(p.isComplete)
        assertEquals(0, p.score)
    }
}
