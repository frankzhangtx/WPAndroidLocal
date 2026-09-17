package org.wordpress.android.ui.debug.cookies

import com.google.gson.GsonBuilder
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertNull
import org.junit.Test

class DebugCookieTest {
    private val gson = GsonBuilder().serializeNulls().create()

    @Test
    fun `given null encoded string, when decoding, then returns null`() {
        assertThat(DebugCookie.decode(gson, null)).isNull()
    }

    @Test
    fun `given malformed json text, when decoding, then returns null`() {
        assertThat(DebugCookie.decode(gson, "not-json")).isNull()
    }

    @Test
    fun `given json array text, when decoding, then returns null`() {
        assertThat(DebugCookie.decode(gson, "[1,2]")).isNull()
    }

    @Test
    fun `given json object missing required fields, when decoding, then returns null`() {
        assertNull(DebugCookie.decode(gson, "{}"))
        assertNull(DebugCookie.decode(gson, "{\"name\":\"session\"}"))
        assertNull(DebugCookie.decode(gson, "{\"host\":\"example.com\"}"))
    }

    @Test
    fun `given json object with blank required fields, when decoding, then returns null`() {
        assertNull(DebugCookie.decode(gson, "{\"host\":\"\",\"name\":\"session\"}"))
        assertNull(DebugCookie.decode(gson, "{\"host\":\" \",\"name\":\"session\"}"))
        assertNull(DebugCookie.decode(gson, "{\"host\":\"example.com\",\"name\":\" \"}"))
    }

    @Test
    fun `given valid cookie, when encoding and decoding, then the cookie is preserved`() {
        listOf(
            DebugCookie("example.com", "session", "abc"),
            DebugCookie("example.com", "empty", ""),
            DebugCookie("example.com", "novalue", null)
        ).forEach { cookie ->
            assertThat(DebugCookie.decode(gson, cookie.encode(gson))).isEqualTo(cookie)
        }
    }

    @Test
    fun `given valid cookie, when encoding, then the json matches the captured baseline`() {
        val cookie = DebugCookie("example.com", "session", "abc")

        assertThat(cookie.encode(gson)).isEqualTo(
            "{\"host\":\"example.com\",\"name\":\"session\",\"value\":\"abc\"," +
                "\"key\":\"example.com_session\",\"oldRfcDomain\":\".example.com\"," +
                "\"headerValue\":\"session\\u003dabc\"}"
        )
    }

    @Test
    fun `given valid cookie, when reading derived members, then the values match the captured baseline`() {
        val cookie = DebugCookie("example.com", "session", "abc")

        assertThat(cookie.key).isEqualTo("example.com_session")
        assertThat(cookie.oldRfcDomain).isEqualTo(".example.com")
        assertThat(cookie.headerValue).isEqualTo("session=abc")
    }
}
