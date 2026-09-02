package kz.lvk.languagelearning.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    @Test
    fun newerVersionIsDetected() {
        assertTrue(VersionComparator.isNewer("0.1.1", "0.1.0"))
        assertTrue(VersionComparator.isNewer("1.0.0", "0.99.99"))
    }

    @Test
    fun sameOrOlderVersionIsNotNewer() {
        assertFalse(VersionComparator.isNewer("0.1.0", "0.1.0"))
        assertFalse(VersionComparator.isNewer("0.0.9", "0.1.0"))
    }
}
