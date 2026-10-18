package com.example.unifiedreader.feature.reader.epub

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EpubNavigatorFragmentRestoreTest {
    @Test
    fun `recreating the activity with a navigator added drops the restored navigator`() {
        val controller = Robolectric.buildActivity(EpubNavigatorRestoreHostActivity::class.java).setup()
        val activity = controller.get()
        val navigator = EpubNavigatorFragment.createDummyFactory()
            .instantiate(activity.classLoader, EpubNavigatorFragment::class.java.name)
        // Capped at CREATED: the crash is in re-instantiating the saved fragment, and its view
        // would need Readium's layouts, which these unit tests don't load.
        activity.supportFragmentManager.beginTransaction()
            .add(navigator, "readium-epub-test")
            .setMaxLifecycle(navigator, Lifecycle.State.CREATED)
            .commitNow()

        val recreated = controller.recreate().get()

        assertTrue(recreated.supportFragmentManager.fragments.none { it is EpubNavigatorFragment })
    }
}

class EpubNavigatorRestoreHostActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installEpubNavigatorRestoreFactory()
        super.onCreate(savedInstanceState)
        discardRestoredEpubNavigators()
    }
}
