package com.timetochilltoo.fileviewer.core.files

import android.content.Intent
import android.net.Uri
import android.os.Parcelable

sealed interface Ingress {
    data class OpenUris(val uris: List<Uri>) : Ingress
    data class SharedText(val text: String) : Ingress
    data object None : Ingress
}

object IntentRouter {

    @Suppress("DEPRECATION")
    fun route(intent: Intent?): Ingress {
        if (intent == null) return Ingress.None
        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { Ingress.OpenUris(listOf(it)) } ?: Ingress.None
            }

            Intent.ACTION_SEND -> {
                val stream = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> Ingress.OpenUris(listOf(stream))
                    !text.isNullOrEmpty() -> Ingress.SharedText(text)
                    else -> Ingress.None
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val streams = intent
                    .getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
                    ?.filterIsInstance<Uri>()
                if (!streams.isNullOrEmpty()) Ingress.OpenUris(streams) else Ingress.None
            }

            else -> Ingress.None
        }
    }
}
