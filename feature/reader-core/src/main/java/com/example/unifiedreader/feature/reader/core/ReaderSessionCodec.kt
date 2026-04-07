package com.example.unifiedreader.feature.reader.core

import org.json.JSONObject

inline fun <reified T : Enum<T>> String.enumValueOrNull(): T? = runCatching {
    enumValueOf<T>(this)
}.getOrNull()

inline fun <reified T : Enum<T>> String.enumValueOrDefault(
    defaultValue: T,
): T = enumValueOrNull<T>() ?: defaultValue

fun String.toReaderPreferencesMap(): Map<String, String> = runCatching {
    val json = JSONObject(this)
    buildMap {
        json.keys().forEach { key ->
            put(key, json.optString(key))
        }
    }
}.getOrDefault(emptyMap())

fun Map<String, String>.toReaderPreferencesJson(): String = JSONObject(this).toString()
