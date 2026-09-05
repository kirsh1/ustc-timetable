package com.ustc.timetable.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ustc.timetable.appearance.WallpaperImageLoader
import com.ustc.timetable.appearance.wallpaperScrim
import com.ustc.timetable.appearance.wallpaperImageAlpha
import com.ustc.timetable.timetable.data.DEFAULT_WALLPAPER_VISIBILITY_PERCENT
import com.ustc.timetable.appearance.AppearanceMode
import com.ustc.timetable.appearance.ResolvedAppearance
import com.ustc.timetable.appearance.resolveAppearance
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DaylightColors = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    surface = Color(0xFFFFFBFE),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerLow = Color(0xFFF7F2FA),
    onSurface = Color(0xFF1D1B20),
    onSurfaceVariant = Color(0xFF49454F),
    outlineVariant = Color(0xFFCAC4D0),
    error = Color(0xFFBA1A1A),
)

private val NightColors = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    surface = Color(0xFF141218),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerLow = Color(0xFF1D1B20),
    onSurface = Color(0xFFE6E0E9),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Immutable
data class TimetableSpacing(
    val pageHorizontal: androidx.compose.ui.unit.Dp = 16.dp,
    val compactHorizontal: androidx.compose.ui.unit.Dp = 8.dp,
    val sectionGap: androidx.compose.ui.unit.Dp = 16.dp,
    val rowVertical: androidx.compose.ui.unit.Dp = 10.dp,
    val cardContent: androidx.compose.ui.unit.Dp = 12.dp,
)

val LocalTimetableSpacing = staticCompositionLocalOf { TimetableSpacing() }
val LocalResolvedAppearance = staticCompositionLocalOf { ResolvedAppearance.LIGHT }

object TimetableTypography {
    val courseTitle = TextStyle(fontSize = 11.sp, lineHeight = 12.5.sp, fontWeight = FontWeight.SemiBold)
    val courseLocation = TextStyle(
        fontSize = 10.sp,
        lineHeight = 11.5.sp,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
    )
    val courseMetadata = TextStyle(fontSize = 9.sp, lineHeight = 10.5.sp, fontWeight = FontWeight.Normal)
}

@Composable
fun TimetableTheme(mode: AppearanceMode = AppearanceMode.LIGHT, content: @Composable () -> Unit) {
    val resolved = resolveAppearance(mode, isSystemInDarkTheme())
    val colors = if (resolved == ResolvedAppearance.DARK) NightColors else DaylightColors
    androidx.compose.runtime.CompositionLocalProvider(
        LocalTimetableSpacing provides TimetableSpacing(),
        LocalResolvedAppearance provides resolved,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface, content = content)
        }
    }
}

/** Solid daylight background today; later wallpaper/scrim modes can replace only this layer. */
@Composable
fun AppBackgroundLayer(
    wallpaperUri: String? = null,
    wallpaperVisibilityPercent: Int = DEFAULT_WALLPAPER_VISIBILITY_PERCENT,
    onWallpaperUnavailable: () -> Unit = {},
    onWallpaperAvailable: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    BoxWithConstraints(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).testTag("solid_timetable_background"),
    ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
            val heightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
            val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, wallpaperUri, widthPx, heightPx) {
                value = if (wallpaperUri == null) null else withContext(Dispatchers.IO) {
                    WallpaperImageLoader.load(context, wallpaperUri, widthPx, heightPx)
                }
                if (wallpaperUri != null && value == null) onWallpaperUnavailable()
                if (wallpaperUri != null && value != null) onWallpaperAvailable()
            }
            if (image != null) {
                Image(
                    bitmap = image!!,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = wallpaperImageAlpha(wallpaperVisibilityPercent),
                    modifier = Modifier.fillMaxSize().testTag("timetable_wallpaper_image"),
                )
                Box(Modifier.fillMaxSize().background(wallpaperScrim(LocalResolvedAppearance.current, wallpaperVisibilityPercent)).testTag("timetable_wallpaper_scrim"))
            }
        Box(Modifier.fillMaxSize()) { content() }
    }
}
