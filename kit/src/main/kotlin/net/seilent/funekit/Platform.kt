package net.seilent.funekit

import android.app.Activity
import android.graphics.Color.TRANSPARENT
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.window.layout.WindowMetricsCalculator
import androidx.core.graphics.Insets as PlatformInsets

object Platform {
    private const val EXCLUSION_DP = 200

    fun edgeToEdge(activity: ComponentActivity) {
        activity.enableEdgeToEdge(SystemBarStyle.dark(TRANSPARENT), SystemBarStyle.dark(TRANSPARENT))
        val window = activity.window
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    fun hideStatusBar(activity: Activity) {
        val c = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.statusBars())
    }

    fun window(activity: Activity, insets: WindowInsetsCompat): Window = window(activity, insets, dpi(activity))

    fun window(activity: Activity, insets: WindowInsetsCompat, d: Dpi): Window {
        val b = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity).bounds
        return window(b.width(), b.height(), Build.VERSION.SDK_INT, insets, d)
    }

    @Suppress("DEPRECATION")
    fun dpi(activity: Activity): Dpi {
        val display = ContextCompat.getDisplayOrDefault(activity)
        val m = DisplayMetrics().also { display.getRealMetrics(it) }
        val mode = display.mode
        val logical = WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(activity).bounds
        return Dpi(
            x = m.xdpi,
            y = m.ydpi,
            stable = DisplayMetrics.DENSITY_DEVICE_STABLE,
            modeW = mode.physicalWidth,
            modeH = mode.physicalHeight,
            modeSizes = (display.supportedModes.map { it.physicalWidth to it.physicalHeight } + (mode.physicalWidth to mode.physicalHeight)).distinct().size,
            logicalW = logical.width(),
            logicalH = logical.height(),
        )
    }

    fun window(widthPx: Int, heightPx: Int, sdk: Int, insets: WindowInsetsCompat, dpi: Dpi? = null): Window = Window(
        widthPx = widthPx,
        heightPx = heightPx,
        sdk = sdk,
        statusBars = of(status(insets, sdk)),
        navigationBars = of(navigation(insets, sdk)),
        systemGestures = of(insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemGestures())),
        cutout = of(insets.getInsets(WindowInsetsCompat.Type.displayCutout())),
        ime = of(insets.getInsets(WindowInsetsCompat.Type.ime())),
        dpi = dpi,
    )

    @Suppress("DEPRECATION")
    private fun status(insets: WindowInsetsCompat, sdk: Int): PlatformInsets {
        val bar = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars())
        if (sdk >= Build.VERSION_CODES.R) return bar
        return PlatformInsets.max(bar, PlatformInsets.of(0, insets.stableInsets.top, 0, 0))
    }

    @Suppress("DEPRECATION")
    private fun navigation(insets: WindowInsetsCompat, sdk: Int): PlatformInsets {
        val bar = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
        if (sdk >= Build.VERSION_CODES.R) return bar
        val stable = insets.stableInsets
        return PlatformInsets.max(bar, PlatformInsets.of(stable.left, 0, stable.right, stable.bottom))
    }

    fun exclusionCapPx(density: Float): Int = (EXCLUSION_DP * density).toInt()

    private fun of(i: PlatformInsets) = Insets(i.left, i.top, i.right, i.bottom)
}
