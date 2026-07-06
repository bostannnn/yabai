package com.example.unifiedreader.feature.reader.comics

import android.os.Trace

internal inline fun <T> traceComicReaderSection(
    sectionName: String,
    block: () -> T,
): T {
    Trace.beginSection(sectionName)
    return try {
        block()
    } finally {
        Trace.endSection()
    }
}
