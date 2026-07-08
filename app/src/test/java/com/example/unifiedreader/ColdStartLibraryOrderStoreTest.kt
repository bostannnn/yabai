package com.example.unifiedreader

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.unifiedreader.core.model.SourceType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ColdStartLibraryOrderStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `saves and restores ordered ids per source`() {
        val store = SharedPreferencesColdStartLibraryOrderStore(context)

        store.save(SourceType.KAVITA, listOf("k-3", "k-1", "k-2"))
        store.save(SourceType.STUMP, listOf("s-9"))

        assertEquals(listOf("k-3", "k-1", "k-2"), store.restore(SourceType.KAVITA))
        assertEquals(listOf("s-9"), store.restore(SourceType.STUMP))
        assertEquals(emptyList<String>(), store.restore(SourceType.GRIMMORY))
    }

    @Test
    fun `caps the number of persisted ids and drops blanks and duplicates`() {
        val store = SharedPreferencesColdStartLibraryOrderStore(context)
        val ids = buildList {
            add("first")
            add("")
            add("first")
            addAll((0 until COLD_START_LIBRARY_ORDER_LIMIT + 50).map { index -> "id-$index" })
        }

        store.save(SourceType.KAVITA, ids)

        val restored = store.restore(SourceType.KAVITA)
        assertEquals(COLD_START_LIBRARY_ORDER_LIMIT, restored.size)
        assertEquals("first", restored.first())
        assertEquals(restored.distinct(), restored)
    }

    @Test
    fun `latest save overwrites previous order for a source`() {
        val store = SharedPreferencesColdStartLibraryOrderStore(context)

        store.save(SourceType.KAVITA, listOf("a", "b"))
        store.save(SourceType.KAVITA, listOf("c"))

        assertEquals(listOf("c"), store.restore(SourceType.KAVITA))
    }
}
