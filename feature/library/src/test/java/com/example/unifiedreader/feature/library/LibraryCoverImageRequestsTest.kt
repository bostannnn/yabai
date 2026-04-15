package com.example.unifiedreader.feature.library

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryCoverImageRequestsTest {
    @Test
    fun `bucketedCoverSize rounds grid covers into stable buckets`() {
        assertEquals(
            IntSize(384, 576),
            bucketedCoverSize(
                targetSize = IntSize(377, 566),
                variant = LibraryCoverImageVariant.Grid,
            ),
        )
        assertEquals(
            IntSize(320, 448),
            bucketedCoverSize(
                targetSize = IntSize(295, 419),
                variant = LibraryCoverImageVariant.ShelfRow,
            ),
        )
    }

    @Test
    fun `bucketedCoverSize ignores zero sized requests`() {
        assertNull(
            bucketedCoverSize(
                targetSize = IntSize.Zero,
                variant = LibraryCoverImageVariant.Detailed,
            ),
        )
    }
}
