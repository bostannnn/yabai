package com.example.unifiedreader.feature.library.menu

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

internal object ReadingDirectionIcons {
    val Ltr: ImageVector by lazy {
        Builder(
            name = "ReadingDirectionLtr",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(4.5f, 5.5f)
                lineTo(19.5f, 5.5f)
                quadTo(20.5f, 5.5f, 20.5f, 6.5f)
                lineTo(20.5f, 17.5f)
                quadTo(20.5f, 18.5f, 19.5f, 18.5f)
                lineTo(4.5f, 18.5f)
                quadTo(3.5f, 18.5f, 3.5f, 17.5f)
                lineTo(3.5f, 6.5f)
                quadTo(3.5f, 5.5f, 4.5f, 5.5f)
                close()
            }
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(14.6f, 7.3f)
                lineTo(17.9f, 7.3f)
                quadTo(18.7f, 7.3f, 18.7f, 8.1f)
                lineTo(18.7f, 15.9f)
                quadTo(18.7f, 16.7f, 17.9f, 16.7f)
                lineTo(14.6f, 16.7f)
                quadTo(13.8f, 16.7f, 13.8f, 15.9f)
                lineTo(13.8f, 8.1f)
                quadTo(13.8f, 7.3f, 14.6f, 7.3f)
                close()
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(7.2f, 11.95f)
                lineTo(13.0f, 11.95f)
                moveTo(10.25f, 9.25f)
                lineTo(13.0f, 11.95f)
                lineTo(10.25f, 14.65f)
            }
        }.build()
    }

    val Rtl: ImageVector by lazy {
        Builder(
            name = "ReadingDirectionRtl",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(4.5f, 5.5f)
                lineTo(19.5f, 5.5f)
                quadTo(20.5f, 5.5f, 20.5f, 6.5f)
                lineTo(20.5f, 17.5f)
                quadTo(20.5f, 18.5f, 19.5f, 18.5f)
                lineTo(4.5f, 18.5f)
                quadTo(3.5f, 18.5f, 3.5f, 17.5f)
                lineTo(3.5f, 6.5f)
                quadTo(3.5f, 5.5f, 4.5f, 5.5f)
                close()
            }
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(6.1f, 7.3f)
                lineTo(9.4f, 7.3f)
                quadTo(10.2f, 7.3f, 10.2f, 8.1f)
                lineTo(10.2f, 15.9f)
                quadTo(10.2f, 16.7f, 9.4f, 16.7f)
                lineTo(6.1f, 16.7f)
                quadTo(5.3f, 16.7f, 5.3f, 15.9f)
                lineTo(5.3f, 8.1f)
                quadTo(5.3f, 7.3f, 6.1f, 7.3f)
                close()
            }
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(16.8f, 11.95f)
                lineTo(11.0f, 11.95f)
                moveTo(13.75f, 9.25f)
                lineTo(11.0f, 11.95f)
                lineTo(13.75f, 14.65f)
            }
        }.build()
    }
}
