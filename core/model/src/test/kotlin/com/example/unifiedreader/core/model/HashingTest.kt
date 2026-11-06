package com.example.unifiedreader.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Cache file names come from these, so their spelling is pinned. */
class HashingTest {
    @Test
    fun sha256HexIsLowercaseHexOfTheUtf8Bytes() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", "abc".sha256Hex())
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "".sha256Hex())
    }

    @Test
    fun toLowerHexKeepsLeadingZeros() {
        assertEquals("000fa0ff", byteArrayOf(0x00, 0x0f, 0xa0.toByte(), 0xff.toByte()).toLowerHex())
    }
}
