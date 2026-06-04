package com.example.unifiedreader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val UnifiedReaderShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

internal val Terminal95UnifiedReaderShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

internal val CrimsonCourtUnifiedReaderShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

internal val PanelPopUnifiedReaderShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

internal val BlueprintUnifiedReaderShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(4.dp),
    large = RoundedCornerShape(6.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

internal val VhsNightUnifiedReaderShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

internal val DetectiveFileUnifiedReaderShapes = Shapes(
    extraSmall = RoundedCornerShape(1.dp),
    small = RoundedCornerShape(3.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

internal val UnifiedReaderTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

internal val Terminal95UnifiedReaderTypography = UnifiedReaderTypography.withFamilies(
    displayFamily = FontFamily.Monospace,
    titleFamily = FontFamily.Monospace,
    bodyFamily = FontFamily.Monospace,
    labelFamily = FontFamily.Monospace,
).copy(
    titleMedium = UnifiedReaderTypography.titleMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = UnifiedReaderTypography.labelLarge.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
    ),
    labelMedium = UnifiedReaderTypography.labelMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
    ),
)

internal val CrimsonCourtUnifiedReaderTypography = UnifiedReaderTypography.withFamilies(
    displayFamily = FontFamily.Serif,
    titleFamily = FontFamily.Serif,
    bodyFamily = FontFamily.Default,
    labelFamily = FontFamily.Serif,
).copy(
    headlineMedium = UnifiedReaderTypography.headlineMedium.copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
    ),
    headlineSmall = UnifiedReaderTypography.headlineSmall.copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
    ),
    titleMedium = UnifiedReaderTypography.titleMedium.copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
    ),
    labelLarge = UnifiedReaderTypography.labelLarge.copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
    ),
)

internal val PanelPopUnifiedReaderTypography = UnifiedReaderTypography.copy(
    displayLarge = UnifiedReaderTypography.displayLarge.copy(fontWeight = FontWeight.ExtraBold),
    displayMedium = UnifiedReaderTypography.displayMedium.copy(fontWeight = FontWeight.ExtraBold),
    displaySmall = UnifiedReaderTypography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
    headlineLarge = UnifiedReaderTypography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = UnifiedReaderTypography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = UnifiedReaderTypography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = UnifiedReaderTypography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
    titleMedium = UnifiedReaderTypography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
    titleSmall = UnifiedReaderTypography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
    labelLarge = UnifiedReaderTypography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
    labelMedium = UnifiedReaderTypography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
)

internal val BlueprintUnifiedReaderTypography = UnifiedReaderTypography.copy(
    titleMedium = UnifiedReaderTypography.titleMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
    ),
    titleSmall = UnifiedReaderTypography.titleSmall.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
    ),
    labelLarge = UnifiedReaderTypography.labelLarge.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
    ),
    labelMedium = UnifiedReaderTypography.labelMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
    ),
    labelSmall = UnifiedReaderTypography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
    ),
)

internal val VhsNightUnifiedReaderTypography = UnifiedReaderTypography.copy(
    headlineSmall = UnifiedReaderTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = UnifiedReaderTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = UnifiedReaderTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
    labelLarge = UnifiedReaderTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
    labelMedium = UnifiedReaderTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
)

internal val DetectiveFileUnifiedReaderTypography = UnifiedReaderTypography.withFamilies(
    displayFamily = FontFamily.Serif,
    titleFamily = FontFamily.Serif,
    bodyFamily = FontFamily.Default,
    labelFamily = FontFamily.Monospace,
).copy(
    titleMedium = UnifiedReaderTypography.titleMedium.copy(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
    ),
    labelMedium = UnifiedReaderTypography.labelMedium.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
    ),
)

private fun Typography.withFamilies(
    displayFamily: FontFamily,
    titleFamily: FontFamily,
    bodyFamily: FontFamily,
    labelFamily: FontFamily,
): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = displayFamily),
    displayMedium = displayMedium.copy(fontFamily = displayFamily),
    displaySmall = displaySmall.copy(fontFamily = displayFamily),
    headlineLarge = headlineLarge.copy(fontFamily = displayFamily),
    headlineMedium = headlineMedium.copy(fontFamily = displayFamily),
    headlineSmall = headlineSmall.copy(fontFamily = displayFamily),
    titleLarge = titleLarge.copy(fontFamily = titleFamily),
    titleMedium = titleMedium.copy(fontFamily = titleFamily),
    titleSmall = titleSmall.copy(fontFamily = titleFamily),
    bodyLarge = bodyLarge.copy(fontFamily = bodyFamily),
    bodyMedium = bodyMedium.copy(fontFamily = bodyFamily),
    bodySmall = bodySmall.copy(fontFamily = bodyFamily),
    labelLarge = labelLarge.copy(fontFamily = labelFamily),
    labelMedium = labelMedium.copy(fontFamily = labelFamily),
    labelSmall = labelSmall.copy(fontFamily = labelFamily),
)

internal object UnifiedReaderPalette {
    val Ink = Color(0xFF1B1C1F)
    val Paper = Color(0xFFFFF8F0)
    val WarmSurface = Color(0xFFFFFBF7)
    val SoftOutline = Color(0xFFD2C8BC)
    val CoverBlue = Color(0xFF405F6B)
    val ReadGreen = Color(0xFF476A53)
    val BookmarkRed = Color(0xFF86504E)

    val DarkInk = Color(0xFFE8E1D7)
    val DarkPaper = Color(0xFF111417)
    val DarkSurface = Color(0xFF191C20)
    val DarkSurfaceVariant = Color(0xFF424A4F)
    val DarkOutline = Color(0xFF8C9499)
}
