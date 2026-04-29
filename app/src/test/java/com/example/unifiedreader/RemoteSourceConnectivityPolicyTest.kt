package com.example.unifiedreader

import com.example.unifiedreader.core.model.SourceType
import com.example.unifiedreader.core.model.SourceConnectivityState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSourceConnectivityPolicyTest {
    @Test
    fun `remote work gates by operation type and reachability`() {
        val policy = RemoteSourceConnectivityPolicy()

        assertEquals(SourceConnectivityState.Unknown, policy.reachabilityFor(SourceType.KAVITA))
        assertFalse(policy.canOpenRemoteItem(SourceType.KAVITA))
        assertFalse(policy.canFetchCover(SourceType.KAVITA))
        assertFalse(policy.canScheduleDownload(SourceType.KAVITA))
        assertTrue(policy.canAttemptProgressSync(SourceType.KAVITA))

        policy.markChecking(SourceType.KAVITA)

        assertFalse(policy.canOpenRemoteItem(SourceType.KAVITA))
        assertFalse(policy.canFetchCover(SourceType.KAVITA))
        assertFalse(policy.canScheduleDownload(SourceType.KAVITA))
        assertTrue(policy.canAttemptProgressSync(SourceType.KAVITA))

        policy.markUnavailable(SourceType.KAVITA)

        assertFalse(policy.canOpenRemoteItem(SourceType.KAVITA))
        assertFalse(policy.canFetchCover(SourceType.KAVITA))
        assertFalse(policy.canScheduleDownload(SourceType.KAVITA))
        assertFalse(policy.canAttemptProgressSync(SourceType.KAVITA))

        policy.markReachable(SourceType.KAVITA)

        assertTrue(policy.canOpenRemoteItem(SourceType.KAVITA))
        assertTrue(policy.canFetchCover(SourceType.KAVITA))
        assertTrue(policy.canScheduleDownload(SourceType.KAVITA))
        assertTrue(policy.canAttemptProgressSync(SourceType.KAVITA))

        assertFalse(policy.canAttemptProgressSync(SourceType.LOCAL))
    }

    @Test
    fun `explicit probes are allowed for remote sources`() {
        val policy = RemoteSourceConnectivityPolicy()

        assertTrue(policy.canRunExplicitProbe(SourceType.KAVITA))
        assertFalse(policy.canRunExplicitProbe(SourceType.LOCAL))
    }
}
