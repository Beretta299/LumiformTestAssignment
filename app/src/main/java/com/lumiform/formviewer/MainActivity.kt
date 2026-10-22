package com.lumiform.formviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lumiform.formviewer.ui.navigation.FormViewerNavHost
import com.lumiform.formviewer.ui.theme.FormViewerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FormViewerTheme {
                FormViewerNavHost()
            }
        }
    }
}
