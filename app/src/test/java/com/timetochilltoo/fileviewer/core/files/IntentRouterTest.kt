package com.timetochilltoo.fileviewer.core.files

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntentRouterTest {

    @Test
    fun `VIEW intent routes its data uri`() {
        val uri = Uri.parse("content://docs/report.pdf")
        val intent = Intent(Intent.ACTION_VIEW).setData(uri)

        val ingress = IntentRouter.route(intent)

        assertEquals(Ingress.OpenUris(listOf(uri)), ingress)
    }

    @Test
    fun `SEND with stream routes the stream uri`() {
        val uri = Uri.parse("content://docs/note.md")
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri)

        val ingress = IntentRouter.route(intent)

        assertEquals(Ingress.OpenUris(listOf(uri)), ingress)
    }

    @Test
    fun `SEND with plain text routes shared text`() {
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "hello world")

        val ingress = IntentRouter.route(intent)

        assertEquals(Ingress.SharedText("hello world"), ingress)
    }

    @Test
    fun `SEND_MULTIPLE routes all stream uris`() {
        val uris = arrayListOf<android.os.Parcelable>(
            Uri.parse("content://docs/a.pdf"),
            Uri.parse("content://docs/b.md"),
        )
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)

        val ingress = IntentRouter.route(intent)

        assertTrue(ingress is Ingress.OpenUris)
        assertEquals(2, (ingress as Ingress.OpenUris).uris.size)
    }

    @Test
    fun `unrelated or empty intents route to None`() {
        assertEquals(Ingress.None, IntentRouter.route(null))
        assertEquals(Ingress.None, IntentRouter.route(Intent(Intent.ACTION_MAIN)))
        assertEquals(Ingress.None, IntentRouter.route(Intent(Intent.ACTION_VIEW)))
    }
}
