package com.mw.offlineupi.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version comparison gates whether the in-app updater offers a download at all. The bug these
 * cover: the previous implementation returned false for every same-numeric-version pair, so no
 * beta could ever see a later beta — the only upgrade path this app actually ships on.
 */
class UpdateCheckerTest {

    @Test
    fun `a higher numeric version is newer`() {
        assertTrue(UpdateChecker.isNewerVersion("0.2.0", "0.1.1"))
        assertTrue(UpdateChecker.isNewerVersion("1.0.0", "0.9.9"))
        assertTrue(UpdateChecker.isNewerVersion("0.1.2", "0.1.1"))
    }

    @Test
    fun `a lower or equal numeric version is not newer`() {
        assertFalse(UpdateChecker.isNewerVersion("0.1.0", "0.1.1"))
        assertFalse(UpdateChecker.isNewerVersion("0.1.1", "0.1.1"))
        assertFalse(UpdateChecker.isNewerVersion("0.9.9", "1.0.0"))
    }

    @Test
    fun `a leading v is ignored`() {
        assertTrue(UpdateChecker.isNewerVersion("v0.2.0", "0.1.1"))
        assertTrue(UpdateChecker.isNewerVersion("v0.2.0", "v0.1.1"))
    }

    @Test
    fun `missing components count as zero`() {
        assertTrue(UpdateChecker.isNewerVersion("0.2", "0.1.9"))
        assertFalse(UpdateChecker.isNewerVersion("0.1", "0.1.0"))
    }

    @Test
    fun `a later beta of the same version is newer`() {
        // The headline regression.
        assertTrue(UpdateChecker.isNewerVersion("0.1.1-beta2", "0.1.1-beta1"))
        assertTrue(UpdateChecker.isNewerVersion("0.1.1-beta", "0.1.1-alpha"))
        assertTrue(UpdateChecker.isNewerVersion("0.1.1-rc1", "0.1.1-beta9"))
    }

    @Test
    fun `beta10 sorts after beta2 rather than before it`() {
        // A plain string comparison gets this backwards, which is why the suffix is split into
        // alphabetic and numeric runs.
        assertTrue(UpdateChecker.isNewerVersion("0.1.1-beta10", "0.1.1-beta2"))
        assertFalse(UpdateChecker.isNewerVersion("0.1.1-beta2", "0.1.1-beta10"))
    }

    @Test
    fun `an earlier beta is not newer`() {
        assertFalse(UpdateChecker.isNewerVersion("0.1.1-beta1", "0.1.1-beta2"))
        assertFalse(UpdateChecker.isNewerVersion("0.1.1-alpha", "0.1.1-beta"))
        assertFalse(UpdateChecker.isNewerVersion("0.1.1-beta", "0.1.1-beta"))
    }

    @Test
    fun `a bare suffix is earlier than the same suffix with a number`() {
        assertTrue(UpdateChecker.isNewerVersion("0.1.1-beta2", "0.1.1-beta"))
        assertFalse(UpdateChecker.isNewerVersion("0.1.1-beta", "0.1.1-beta2"))
    }

    @Test
    fun `a release outranks any pre-release of the same version`() {
        assertTrue(UpdateChecker.isNewerVersion("0.1.1", "0.1.1-beta9"))
        assertFalse(UpdateChecker.isNewerVersion("0.1.1-beta9", "0.1.1"))
    }

    @Test
    fun `a higher numeric version beats a pre-release suffix`() {
        assertTrue(UpdateChecker.isNewerVersion("0.1.2-beta1", "0.1.1"))
        assertTrue(UpdateChecker.isNewerVersion("0.1.2-alpha", "0.1.1-beta9"))
        assertFalse(UpdateChecker.isNewerVersion("0.1.1", "0.1.2-beta1"))
    }

    @Test
    fun `dotted suffixes compare component-wise`() {
        assertTrue(UpdateChecker.isNewerVersion("0.1.1-rc.2", "0.1.1-rc.1"))
        assertTrue(UpdateChecker.isNewerVersion("0.1.1-rc.10", "0.1.1-rc.2"))
    }

    @Test
    fun `the shipped upgrade path works`() {
        // What the app itself will actually be asked, given versionName 0.1.1-beta.
        assertTrue(UpdateChecker.isNewerVersion("v0.1.2-beta", "0.1.1-beta"))
        assertTrue(UpdateChecker.isNewerVersion("v0.1.1-beta2", "0.1.1-beta"))
        assertFalse(UpdateChecker.isNewerVersion("v0.1.1-beta", "0.1.1-beta"))
    }
}
