package com.timetochilltoo.fileviewer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.timetochilltoo.fileviewer.app.theme.FileViewerTheme
import com.timetochilltoo.fileviewer.feature.shell.ShellScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FileViewerTheme {
                ShellScreen()
            }
        }
    }
}
