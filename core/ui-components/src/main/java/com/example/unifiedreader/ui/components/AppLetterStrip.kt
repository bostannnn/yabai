package com.example.unifiedreader.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The index down the end edge of a long list sorted by name: [letters] ('#' for names that start
 * with a digit or a sign, then A to Z), one per [letterHeight]. A letter not in [present] is
 * drawn faint and moves on to the next one that is. Touching or dragging calls [onLetter] with
 * each new letter and shows it in a bubble beside the finger; [current] (the group at the top
 * of the window) sits on `primaryContainer`. TalkBack reads it as "Jump to a letter", each
 * present letter an action.
 */
@Composable
fun AppLetterStrip(
    letters: List<Char>,
    present: Set<Char>,
    current: Char?,
    onLetter: (Char) -> Unit,
    modifier: Modifier = Modifier,
    letterHeight: Dp = AppLetterStripDefaults.letterHeight,
) {
    val latestOnLetter by rememberUpdatedState(onLetter)
    val heightPx = with(LocalDensity.current) { letterHeight.toPx() }
    var touched by remember { mutableStateOf<Char?>(null) }
    fun letterAt(y: Float): Char? {
        val index = (y / heightPx).toInt().coerceIn(0, letters.lastIndex)
        // The nearest letter that has a group, looking down first.
        return letters.drop(index).firstOrNull { it in present } ?: letters.take(index).lastOrNull { it in present }
    }
    Box(
        modifier = modifier
            .width(AppLetterStripDefaults.width)
            .semantics {
                contentDescription = "Jump to a letter"
                customActions = letters.filter { it in present }.map { letter ->
                    CustomAccessibilityAction(label = letter.toString()) { latestOnLetter(letter); true }
                }
            }
            .pointerInput(letters, present, heightPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var last = letterAt(down.position.y)
                    touched = last
                    last?.let(latestOnLetter)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        change.consume()
                        val letter = letterAt(change.position.y)
                        if (letter != null && letter != last) {
                            last = letter
                            touched = letter
                            latestOnLetter(letter)
                        }
                    }
                    touched = null
                }
            },
    ) {
        Column {
            letters.forEach { letter ->
                val isCurrent = letter == current
                Box(
                    modifier = Modifier
                        .size(AppLetterStripDefaults.width, letterHeight)
                        .then(if (isCurrent) Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape) else Modifier)
                        .alpha(if (letter in present) 1f else AppLetterStripDefaults.missingAlpha),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letter.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, lineHeight = 12.sp),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val shown = touched
        if (shown != null) {
            val index = letters.indexOf(shown)
            Box(
                modifier = Modifier
                    .offset(
                        x = -(AppLetterStripDefaults.bubbleSize + AppLetterStripDefaults.bubbleGap),
                        y = letterHeight * index - (AppLetterStripDefaults.bubbleSize - letterHeight) / 2,
                    )
                    .size(AppLetterStripDefaults.bubbleSize)
                    .background(MaterialTheme.colorScheme.primary, AppLetterStripDefaults.bubbleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = shown.toString(),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

object AppLetterStripDefaults {
    val width: Dp = 18.dp
    val letterHeight: Dp = 17.dp
    val letterHeightCompact: Dp = 15.dp
    val bubbleSize: Dp = 60.dp
    val bubbleGap: Dp = 12.dp
    val bubbleShape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomEnd = 6.dp, bottomStart = 30.dp)
    const val missingAlpha: Float = 0.38f

    /** '#' then A to Z, always shown. */
    val alphabet: List<Char> = listOf('#') + ('A'..'Z')

    /** The strip's letter for a name: its first letter, upper case, or '#' for a digit or a sign. */
    fun letterOf(name: String): Char {
        val first = name.trimStart().firstOrNull() ?: return '#'
        return if (first.isLetter()) first.uppercaseChar() else '#'
    }

    /** The strip's letters: [alphabet], then any other letter names start with (Cyrillic, say), in order. */
    fun lettersFor(present: Set<Char>): List<Char> = alphabet + (present - alphabet.toSet()).sorted()

    /** How many lists a section needs before the strip shows. */
    const val minimumCount: Int = 20
}
