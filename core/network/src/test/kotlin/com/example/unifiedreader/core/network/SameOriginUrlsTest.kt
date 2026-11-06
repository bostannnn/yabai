package com.example.unifiedreader.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SameOriginUrlsTest {
    private val base = "http://192.168.50.60:5000"

    @Test
    fun resolvesARelativePathAgainstTheServer() {
        assertEquals("http://192.168.50.60:5000/api/image/1", "/api/image/1".resolveSameOriginUrl(base))
        assertEquals("http://192.168.50.60:5000/api/image/1", "api/image/1".resolveSameOriginUrl("$base/"))
    }

    @Test
    fun keepsAnAbsoluteUrlOnTheSameServer() {
        assertEquals("http://192.168.50.60:5000/cover.jpg", "http://192.168.50.60:5000/cover.jpg".resolveSameOriginUrl(base))
        assertEquals("HTTP://192.168.50.60:5000/cover.jpg", "HTTP://192.168.50.60:5000/cover.jpg".resolveSameOriginUrl(base))
    }

    @Test
    fun treatsTheDefaultPortAsWrittenOut() {
        assertEquals("https://books.example:443/c.jpg", "https://books.example:443/c.jpg".resolveSameOriginUrl("https://books.example"))
    }

    @Test
    fun refusesAnotherServer() {
        assertNull("http://evil.example/cover.jpg".resolveSameOriginUrl(base))
        assertNull("http://192.168.50.60:6060/cover.jpg".resolveSameOriginUrl(base))
        assertNull("https://192.168.50.60:5000/cover.jpg".resolveSameOriginUrl(base))
        assertNull("//evil.example/cover.jpg".resolveSameOriginUrl(base))
    }

    @Test
    fun refusesSomethingThatIsNotAUrl() {
        assertNull("http://exa mple/".resolveSameOriginUrl(base))
    }
}
