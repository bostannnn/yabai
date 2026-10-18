package com.example.unifiedreader.feature.reader.epub

import androidx.fragment.app.FragmentActivity
import org.readium.r2.navigator.epub.EpubNavigatorFragment

/**
 * Lets the activity be recreated (rotation, theme change, window resize, process death) with an
 * EPUB open. Android saves the Readium navigator fragment with the activity and rebuilds it by
 * reflection, which crashes: the fragment has no no-argument constructor. With this factory the
 * rebuild produces Readium's placeholder instead.
 *
 * Call before `super.onCreate`, then [discardRestoredEpubNavigators] right after it.
 */
fun FragmentActivity.installEpubNavigatorRestoreFactory() {
    supportFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
}

/**
 * Removes navigators restored from saved state. The reader adds a fresh navigator at the saved
 * position whenever it is composed, so a restored one is never used, and Readium requires its
 * placeholder to be gone before the activity resumes.
 */
fun FragmentActivity.discardRestoredEpubNavigators() {
    val restored = supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>()
    if (restored.isEmpty()) return
    supportFragmentManager.beginTransaction()
        .apply { restored.forEach(::remove) }
        .commitNow()
}
