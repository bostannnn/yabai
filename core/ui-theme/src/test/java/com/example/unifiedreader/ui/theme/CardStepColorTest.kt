package com.example.unifiedreader.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

class CardStepColorTest {

    @Test
    fun `the card stands one step from a dark ground and a light one`() {
        listOf(
            Color(0xFF101214) to Color(0xFFE7EAEE),
            Color(0xFF000000) to Color(0xFFFFFFFF),
            Color(0xFFF7EBD8) to Color(0xFF211A12),
            Color(0xFFFFFDF6) to Color(0xFF15171C),
        ).forEach { (ground, ink) ->
            val card = cardStepColor(ground, ink)
            val contrast = contrastRatio(ground, card)
            assertTrue("$ground: $contrast", contrast >= CardStepContrast && contrast < 1.3f)
        }
    }

    @Test
    fun `the card moves toward the text colour`() {
        assertTrue(cardStepColor(Color(0xFF101214), Color.White).red > Color(0xFF101214).red)
        assertTrue(cardStepColor(Color(0xFFFFF8F0), Color.Black).red < Color(0xFFFFF8F0).red)
    }
}
