package com.example.unifiedreader.feature.reader.epub

import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.DelicateReadiumApi
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class, DelicateReadiumApi::class)
internal fun epubNavigatorFragmentConfiguration(): EpubNavigatorFragment.Configuration =
    EpubNavigatorFragment.Configuration(
        shouldApplyInsetsPadding = false,
    ).apply {
        useReadiumCssFontSize = false
    }
