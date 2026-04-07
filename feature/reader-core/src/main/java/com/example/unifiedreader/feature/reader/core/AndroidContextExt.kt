package com.example.unifiedreader.feature.reader.core

import android.content.Context
import android.content.ContextWrapper

inline fun <reified T> Context.findContextAncestor(): T? {
    var current: Context? = this
    while (current != null) {
        if (current is T) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}
