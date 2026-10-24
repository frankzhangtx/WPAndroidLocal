package org.wordpress.android.fluxc.utils

import org.junit.Test
import kotlin.test.assertEquals

class WhatsNewAppVersionOverflowTest {
    @Test
    fun `versionNameToInt returns -1 when the major version digits overflow Int`() {
        val result = runCatching { WhatsNewAppVersionUtils.versionNameToInt("2147483648.1") }

        assertEquals(-1, result.getOrNull())
    }

    @Test
    fun `versionNameToInt returns -1 when the minor version digits overflow Int`() {
        val result = runCatching { WhatsNewAppVersionUtils.versionNameToInt("1.2147483648") }

        assertEquals(-1, result.getOrNull())
    }

    @Test
    fun `versionNameToInt keeps converting a normal major and minor version`() {
        assertEquals(141, WhatsNewAppVersionUtils.versionNameToInt("14.1.2"))
    }

    @Test
    fun `versionNameToInt keeps returning -1 for a malformed version name`() {
        assertEquals(-1, WhatsNewAppVersionUtils.versionNameToInt("alpha-222"))
    }
}
