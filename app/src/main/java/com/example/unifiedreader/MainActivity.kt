package com.example.unifiedreader

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.example.unifiedreader.feature.reader.epub.discardRestoredEpubNavigators
import com.example.unifiedreader.feature.reader.epub.installEpubNavigatorRestoreFactory
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installEpubNavigatorRestoreFactory()
        super.onCreate(savedInstanceState)
        discardRestoredEpubNavigators()
        enableEdgeToEdge()
        setContent {
            UnifiedReaderRoute()
        }
    }
}
