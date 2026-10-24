package com.example.unifiedreader.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * One page colour of the reader: the [page], its [text], the quieter [number] for the page
 * number and chapter line, and the [raised] surface a note card takes on that page.
 */
@Immutable
data class ReaderPageColors(
    val page: Color,
    val text: Color,
    val number: Color,
    val raised: Color,
) {
    val isLight: Boolean get() = page.luminance() > 0.5f
}

/**
 * The reading page's colours, in the order the settings show them. Text on page is at least
 * 10:1 in each; the number between 3.4:1 and 4.1:1.
 */
enum class ReaderPageColor(
    val label: String,
    val colors: ReaderPageColors,
) {
    Paper(
        "Paper",
        ReaderPageColors(page = Color(0xFFFAF7F0), text = Color(0xFF1E1C19), number = Color(0xFF8A857C), raised = Color(0xFFF0ECE2)),
    ),
    Sepia(
        "Sepia",
        ReaderPageColors(page = Color(0xFFF2E6CC), text = Color(0xFF3B2E1C), number = Color(0xFF857358), raised = Color(0xFFE8DABB)),
    ),
    Grey(
        "Grey",
        ReaderPageColors(page = Color(0xFFD8D8D4), text = Color(0xFF212121), number = Color(0xFF6B6B67), raised = Color(0xFFCCCCC7)),
    ),
    Dusk(
        "Dusk",
        ReaderPageColors(page = Color(0xFF24272C), text = Color(0xFFD7D4CC), number = Color(0xFF878580), raised = Color(0xFF30343A)),
    ),
    Black(
        "Black",
        ReaderPageColors(page = Color(0xFF000000), text = Color(0xFFC9C6BF), number = Color(0xFF6F6D68), raised = Color(0xFF17171A)),
    ),
}
