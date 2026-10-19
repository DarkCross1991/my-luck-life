package life.myluck.w124.core

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPolicyTest {
    @Test
    fun masterHasNoGarage() {
        val hint = SyncPolicy.missingGarageHint("master")
        assertNotNull(hint)
        assertTrue(hint!!.contains(SyncPolicy.DATA_BRANCH))
    }

    @Test
    fun dataBranchMayCreateFiles() {
        assertNull(SyncPolicy.missingGarageHint(SyncPolicy.DATA_BRANCH))
    }
}
