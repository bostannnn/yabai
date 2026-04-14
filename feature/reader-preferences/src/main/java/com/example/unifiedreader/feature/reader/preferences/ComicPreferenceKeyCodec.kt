package com.example.unifiedreader.feature.reader.preferences

import java.util.Base64

internal fun encodeComicPreferenceId(
    value: String,
): String = Base64.getUrlEncoder()
    .withoutPadding()
    .encodeToString(value.toByteArray(Charsets.UTF_8))

internal fun decodeComicPreferenceId(
    value: String,
): String? = runCatching {
    String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}.getOrNull()
