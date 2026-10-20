package com.swan.notes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.swan.notes.ui.SwanNotesApp

class MainActivity : ComponentActivity() {
    private var pendingSharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingSharedText = intent.readSharedText()

        val container = (application as SwanApp).container
        setContent {
            SwanNotesApp(
                container = container,
                sharedText = pendingSharedText,
                onSharedTextConsumed = { pendingSharedText = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val text = intent.readSharedText()
        if (!text.isNullOrBlank()) {
            pendingSharedText = text
        }
    }

    private fun Intent.readSharedText(): String? {
        if (action != Intent.ACTION_SEND) return null
        if (type?.startsWith("text/") != true) return null
        return getStringExtra(Intent.EXTRA_TEXT)
    }
}
