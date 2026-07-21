package com.timetochilltoo.fileviewer.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.timetochilltoo.fileviewer.app.theme.FileViewerTheme
import com.timetochilltoo.fileviewer.core.files.Ingress
import com.timetochilltoo.fileviewer.core.files.IntentRouter
import com.timetochilltoo.fileviewer.feature.shell.AppViewModel
import com.timetochilltoo.fileviewer.feature.shell.ShellScreen

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FileViewerTheme {
                ShellScreen(viewModel)
            }
        }
        if (savedInstanceState == null) {
            if (IntentRouter.route(intent) != Ingress.None) {
                viewModel.skipSessionRestore()
            }
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.skipSessionRestore()
        handleIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        viewModel.persistState()
    }

    private fun handleIntent(intent: Intent?) {
        val ingress = IntentRouter.route(intent)
        android.util.Log.i("FileViewer", "handleIntent: $ingress")
        when (ingress) {
            is Ingress.OpenUris -> viewModel.openUris(ingress.uris.map { it.toString() })
            is Ingress.SharedText -> viewModel.newMarkdownDocument(initialText = ingress.text)
            Ingress.None -> Unit
        }
    }
}
