package com.timetochilltoo.fileviewer.core.files

import com.timetochilltoo.fileviewer.core.model.DocumentKind
import com.timetochilltoo.fileviewer.core.model.PdfScaleMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SessionCodecTest {

    @Test
    fun `session round trip preserves tabs order and selection`() {
        val session = SessionCodec.Session(
            tabs = listOf(
                SessionCodec.Entry("content://a/note.md", DocumentKind.MARKDOWN),
                SessionCodec.Entry("content://b/report.pdf", DocumentKind.PDF),
            ),
            selectedUri = "content://b/report.pdf",
        )

        val decoded = SessionCodec.decode(SessionCodec.encode(session))

        assertEquals(session, decoded)
    }

    @Test
    fun `null selection round trips as null`() {
        val session = SessionCodec.Session(
            tabs = listOf(SessionCodec.Entry("content://a/note.md", DocumentKind.MARKDOWN)),
            selectedUri = null,
        )

        val decoded = SessionCodec.decode(SessionCodec.encode(session))

        assertNull(decoded.selectedUri)
        assertEquals(1, decoded.tabs.size)
    }

    @Test
    fun `malformed json decodes to empty session`() {
        val decoded = SessionCodec.decode("{not json")
        assertTrue(decoded.tabs.isEmpty())
        assertNull(decoded.selectedUri)
    }

    @Test
    fun `blank json decodes to empty session`() {
        assertEquals(SessionCodec.Session(emptyList(), null), SessionCodec.decode(null))
        assertEquals(SessionCodec.Session(emptyList(), null), SessionCodec.decode(""))
    }

    @Test
    fun `entries with unknown kind are skipped`() {
        val json = """{"tabs":[{"uri":"content://a","kind":"MARKDOWN"},{"uri":"content://b","kind":"EPUB"}],"selected":"content://a"}"""
        val decoded = SessionCodec.decode(json)
        assertEquals(1, decoded.tabs.size)
        assertEquals("content://a", decoded.tabs[0].uri)
    }

    @Test
    fun `scroll map round trip`() {
        val map = mapOf("content://a/note.md" to 420, "content://b/other.md" to 0)
        assertEquals(map, SessionCodec.decodeScroll(SessionCodec.encodeScroll(map)))
    }

    @Test
    fun `malformed scroll json decodes to empty map`() {
        assertTrue(SessionCodec.decodeScroll("oops").isEmpty())
        assertTrue(SessionCodec.decodeScroll(null).isEmpty())
    }

    @Test
    fun `pdf state map round trip preserves page scale and mode`() {
        val map = mapOf(
            "content://a/report.pdf" to SessionCodec.PdfState(4, 1.5f, PdfScaleMode.FREE),
            "content://b/book.pdf" to SessionCodec.PdfState(0, 1.0f, PdfScaleMode.FIT_WIDTH),
        )

        val decoded = SessionCodec.decodePdfState(SessionCodec.encodePdfState(map))

        assertEquals(map, decoded)
    }

    @Test
    fun `pdf state with invalid mode falls back to fit width`() {
        val json = """{"content://a.pdf":{"page":2,"scale":1.25,"mode":"ZOOM_400"}}"""
        val decoded = SessionCodec.decodePdfState(json)
        assertEquals(2, decoded["content://a.pdf"]?.page)
        assertEquals(1.25f, decoded["content://a.pdf"]?.scale)
        assertEquals(PdfScaleMode.FIT_WIDTH, decoded["content://a.pdf"]?.mode)
    }
}
