package com.example.unifiedreader.core.model

import java.security.MessageDigest

/**
 * The SHA-256 of this text's UTF-8 bytes, as 64 lowercase hex digits.
 *
 * Cache file names are built from it, so its output must never change: a different spelling would orphan
 * every cached page, archive and cover on the device.
 */
fun String.sha256Hex(): String = toByteArray(Charsets.UTF_8).sha256().toLowerHex()

/** The SHA-256 digest of these bytes. */
fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

/** These bytes as lowercase hex, two digits each: `0f a0` is "0fa0". */
fun ByteArray.toLowerHex(): String = buildString(size * 2) {
    this@toLowerHex.forEach { byte ->
        val value = byte.toInt() and 0xff
        append(HEX_DIGITS[value ushr 4])
        append(HEX_DIGITS[value and 0x0f])
    }
}

private const val HEX_DIGITS = "0123456789abcdef"
