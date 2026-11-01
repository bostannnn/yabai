package com.example.unifiedreader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.unifiedreader.ui.theme.AppLaunchSplash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchSplashDrawableTest {
    @Test
    fun nativeSplashInflatesInEveryPaletteAndItsEyesAreOpaque() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            AppLaunchSplash.entries.forEach { palette ->
                val context = ContextThemeWrapper(instrumentation.targetContext, palette.style)
                val drawable = requireNotNull(context.getDrawable(R.drawable.launch_mark))
                if (Build.VERSION.SDK_INT >= 31) assertTrue(drawable is AnimatedVectorDrawable)
                val bitmap = Bitmap.createBitmap(288, 288, Bitmap.Config.ARGB_8888)
                try {
                    drawable.setBounds(0, 0, 288, 288)
                    drawable.draw(Canvas(bitmap))
                    // Inside the left eye, away from its pupil. The canvas is transparent:
                    // an old punched-out eye would leave alpha 0 instead of theme paper.
                    assertEquals("${palette.name} eye", 255, Color.alpha(bitmap.getPixel(130, 97)))
                    assertTrue("${palette.name} mark renders", (88..222).any { y ->
                        (86..201).any { x -> Color.alpha(bitmap.getPixel(x, y)) > 0 }
                    })
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }
}
