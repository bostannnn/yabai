package com.example.unifiedreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.unifiedreader.ui.theme.UnifiedReaderThemeTokens

/**
 * The alphabet as keys, to open a long list of names at a letter: nine to a row, 34 tall. A letter not in
 * [available] is drawn at 35% and does nothing. Each alphabet in [alphabets] (Cyrillic, Latin) starts its own
 * rows; one with no available letter is left out.
 */
@Composable
fun AppLetterGrid(
    alphabets: List<List<String>>,
    available: Set<String>,
    onLetter: (String) -> Unit,
    modifier: Modifier = Modifier,
    letterLabel: (String) -> String = { letter -> "Open at $letter" },
) {
    val shown = alphabets.filter { letters -> letters.any { it in available } }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppLetterGridDefaults.gap)) {
        shown.forEach { letters ->
            letters.chunked(AppLetterGridDefaults.columns).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppLetterGridDefaults.gap)) {
                    row.forEach { letter ->
                        AppLetterKey(
                            letter = letter,
                            enabled = letter in available,
                            onClick = { onLetter(letter) },
                            label = letterLabel(letter),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(AppLetterGridDefaults.columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun AppLetterKey(letter: String, enabled: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(AppLetterGridDefaults.keyHeight)
            .clip(RoundedCornerShape(UnifiedReaderThemeTokens.presentation.innerCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .alpha(if (enabled) 1f else AppLetterGridDefaults.emptyAlpha),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

object AppLetterGridDefaults {
    const val columns: Int = 9
    val keyHeight: Dp = 34.dp
    val gap: Dp = 4.dp
    const val emptyAlpha: Float = 0.35f
    val latin: List<String> = ('A'..'Z').map(Char::toString)
    val cyrillic: List<String> = "АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЭЮЯ".map(Char::toString)
}
