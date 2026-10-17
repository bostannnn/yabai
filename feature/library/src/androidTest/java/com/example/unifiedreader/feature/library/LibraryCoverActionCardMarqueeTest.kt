package com.example.unifiedreader.feature.library

import android.graphics.Bitmap
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unifiedreader.core.model.FormatType
import com.example.unifiedreader.core.model.ReadableItem
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryCoverActionCardMarqueeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun long_title_marquee_settles_after_its_pass() {
        val title = "An Extraordinarily Long Comic Title For Marquee Testing"
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                LibraryCoverActionCard(
                    item = ReadableItem(
                        id = "local:marquee",
                        sourceType = SourceType.LOCAL,
                        libraryId = "local",
                        title = title,
                        format = FormatType.CBZ,
                    ),
                    title = title,
                    coverImageUrlProvider = { _, _ -> null },
                    imageVariant = LibraryCoverImageVariant.Grid,
                    isDownloaded = false,
                    isRead = false,
                    onClick = {},
                    modifier = Modifier.width(160.dp),
                    coverModifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(3_000)
        val scrolling = captureTitle(title)
        composeRule.mainClock.advanceTimeBy(500)
        assertFalse("long title should scroll", scrolling.sameAs(captureTitle(title)))

        // Sampled across more than the marquee's 1.2 s repeat delay, so an endless marquee can't pass.
        composeRule.mainClock.advanceTimeBy(60_000)
        val settled = captureTitle(title)
        repeat(6) {
            composeRule.mainClock.advanceTimeBy(500)
            assertTrue("idle title should stop redrawing", settled.sameAs(captureTitle(title)))
        }
    }

    private fun captureTitle(title: String): Bitmap =
        composeRule.onNodeWithText(title).captureToImage().asAndroidBitmap()
}
