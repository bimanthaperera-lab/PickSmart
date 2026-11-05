package lk.picksmart.data.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCheckTest {

    @Test
    fun `App Check switched off in the project can't be what refused a request`() {
        assertTrue(
            AppCheck.isNotSetUp(
                "Error returned from API. code: 403 body: Firebase App Check API has not been used in project 1 before or it is disabled.",
            ),
        )
    }

    @Test
    fun `a failed check on a set-up project still counts as unverified`() {
        assertFalse(AppCheck.isNotSetUp("Error returned from API. code: 403 body: App attestation failed."))
        assertFalse(AppCheck.isNotSetUp(null))
    }
}
