package com.bits.facultyai.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `newer patch version is detected`() {
        assertTrue(UpdateChecker.isNewer("1.6.1", "1.6.0"))
    }

    @Test
    fun `numeric segments compare numerically not lexically`() {
        // A string compare would score 1.10.0 below 1.9.0.
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.0"))
        assertFalse(UpdateChecker.isNewer("1.9.0", "1.10.0"))
    }

    @Test
    fun `same version is not an update`() {
        assertFalse(UpdateChecker.isNewer("1.6.0", "1.6.0"))
    }

    @Test
    fun `older latest is rejected`() {
        assertFalse(UpdateChecker.isNewer("1.5.9", "1.6.0"))
    }

    @Test
    fun `missing segments are treated as zero`() {
        assertTrue(UpdateChecker.isNewer("1.7", "1.6.9"))
        assertFalse(UpdateChecker.isNewer("1.6", "1.6.0"))
    }

    @Test
    fun `v prefix and non-digits are ignored`() {
        assertTrue(UpdateChecker.isNewer("v1.7.0", "1.6.0"))
        assertFalse(UpdateChecker.isNewer("1.6.0-rc1", "1.6.0"))
    }

    @Test
    fun `unparsable input falls back to zero safely`() {
        assertTrue(UpdateChecker.isNewer("2", "1.9.9"))
        assertFalse(UpdateChecker.isNewer("beta", "0.0.1"))
    }
}
