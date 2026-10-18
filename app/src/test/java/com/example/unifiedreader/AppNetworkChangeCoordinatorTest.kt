package com.example.unifiedreader

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppNetworkChangeCoordinatorTest {
    private val wifi = AppNetworkState(isAvailable = true, defaultNetworkId = 100L)
    private val cellular = AppNetworkState(isAvailable = true, defaultNetworkId = 200L)
    private val none = AppNetworkState(isAvailable = false)

    @Test
    fun `a new connection is coming back online or switching networks while online`() {
        assertFalse(wifi.isNewConnectionAfter(previous = null))
        assertTrue(wifi.isNewConnectionAfter(previous = none))
        // Wi-Fi to cellular reports the new default network with no loss in between.
        assertTrue(cellular.isNewConnectionAfter(previous = wifi))
        assertFalse(wifi.isNewConnectionAfter(previous = wifi))
        assertFalse(none.isNewConnectionAfter(previous = wifi))
    }

    @Test
    fun `a handoff points the sources back at their primary address, then runs the retry`() = runTest(UnconfinedTestDispatcher()) {
        val monitor = FakeAppNetworkConnectivityMonitor(initialAvailable = true)
        val events = mutableListOf<String>()
        val coordinator = AppNetworkChangeCoordinator(
            networkConnectivityMonitor = monitor,
            serverAddressPreference = object : RemoteServerAddressPreference {
                override suspend fun preferPrimaryAddresses() {
                    events += "primary"
                }
            },
        )
        val job = launch { coordinator.observeNewConnections { events += "retry" } }

        assertEquals(emptyList<String>(), events)
        monitor.switchNetwork()
        assertEquals(listOf("primary", "retry"), events)
        monitor.setNetworkAvailable(false)
        monitor.setNetworkAvailable(true)
        assertEquals(listOf("primary", "retry", "primary", "retry"), events)
        job.cancel()
    }
}
