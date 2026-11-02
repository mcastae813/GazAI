package com.gazai.fuel

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.gazai.fuel.ui.App

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val green = Color(0xFF0B6E4F)
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFF6FD3A8))
                else lightColorScheme(primary = green),
            ) { App(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT)
        @Suppress("DEPRECATION")
        val uri = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        vm.handleShared(text, uri)
    }
}
