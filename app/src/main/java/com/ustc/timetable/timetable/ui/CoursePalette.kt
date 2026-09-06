package com.ustc.timetable.timetable.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 稳定课程配色（SPEC §4.7）：paletteIndex = MD5(colorKey)[0]（unsigned byte）% 12。
 * 同一学期同一课程（stable colorKey）跨启动同色；非当前周淡化策略为纯函数。
 */
object CoursePalette {

    const val NON_CURRENT_WEEK_ALPHA: Float = 0.35f
    const val PALETTE_SIZE: Int = 12
    const val PALETTE_VARIANT_COUNT: Int = 24

    fun variantFor(seed: Long): Int = Math.floorMod(seed, PALETTE_VARIANT_COUNT.toLong()).toInt()

    fun previewIndices(seed: Long): List<Int> {
        val variant = variantFor(seed)
        val rotation = variant % PALETTE_SIZE
        return List(PALETTE_SIZE) { index ->
            val rotated = (index + rotation) % PALETTE_SIZE
            if (variant < PALETTE_SIZE) rotated else PALETTE_SIZE - 1 - rotated
        }
    }

    private val lightPairs: List<Pair<Color, Color>> = listOf(
        Color(0xFFB9DCFF) to Color(0xFF102A43),
        Color(0xFFE0C7F2) to Color(0xFF32164A),
        Color(0xFFBFE3C4) to Color(0xFF17351C),
        Color(0xFFFFD1AD) to Color(0xFF4A2200),
        Color(0xFFAFE2E7) to Color(0xFF07383C),
        Color(0xFFF2C3D2) to Color(0xFF4A1427),
        Color(0xFFF0DA91) to Color(0xFF3D3000),
        Color(0xFFC9D3D8) to Color(0xFF1E2B31),
        Color(0xFFC8CEEE) to Color(0xFF202B5C),
        Color(0xFFD8C9C2) to Color(0xFF39251C),
        Color(0xFFB7DDD6) to Color(0xFF12362F),
        Color(0xFFF1C5B9) to Color(0xFF4A1E14),
    )

    private val darkPairs: List<Pair<Color, Color>> = listOf(
        Color(0xFF284760) to Color(0xFFE3F2FF), Color(0xFF4B365B) to Color(0xFFF4E5FF),
        Color(0xFF284D31) to Color(0xFFE0F7E4), Color(0xFF5A3A22) to Color(0xFFFFE8D5),
        Color(0xFF204B50) to Color(0xFFD9F7FA), Color(0xFF57313E) to Color(0xFFFFE2EA),
        Color(0xFF514821) to Color(0xFFFFF2BC), Color(0xFF35464D) to Color(0xFFE7F1F5),
        Color(0xFF384269) to Color(0xFFE7EAFF), Color(0xFF4B3931) to Color(0xFFFFEAE0),
        Color(0xFF285048) to Color(0xFFDCF8F1), Color(0xFF58382E) to Color(0xFFFFE7DF),
    )

    init {
        require(lightPairs.size == PALETTE_SIZE && darkPairs.size == PALETTE_SIZE)
    }

    /** MD5 首字节按 unsigned（& 0xFF）对 12 取模。 */
    fun colorIndexFor(colorKey: String): Int = colorIndexFor(colorKey, com.ustc.timetable.timetable.data.DEFAULT_COURSE_PALETTE_SEED)

    fun colorIndexFor(colorKey: String, seed: Long): Int {
        val first = MessageDigest.getInstance("MD5")
            .digest(colorKey.toByteArray(Charsets.UTF_8))[0]
            .toInt() and 0xFF
        return previewIndices(seed)[first % PALETTE_SIZE]
    }

    /** 当前周恒 1f；非当前周仅在开关开启时淡化，开关关闭时不渲染（由调用方跳过）。 */
    fun alphaFor(isCurrentWeek: Boolean, showNonCurrentWeek: Boolean): Float =
        if (!isCurrentWeek && showNonCurrentWeek) NON_CURRENT_WEEK_ALPHA else 1f

    fun containerColor(index: Int, dark: Boolean = false): Color = (if (dark) darkPairs else lightPairs)[index].first

    fun onContainerColor(index: Int, dark: Boolean = false): Color = (if (dark) darkPairs else lightPairs)[index].second

    /** A compact in-card label tone derived from the owning course swatch. */
    fun boundaryPillContainerColor(index: Int, dark: Boolean = false): Color {
        val emphasis = if (dark) 0.22f else 0.14f
        return lerp(containerColor(index, dark), onContainerColor(index, dark), emphasis)
    }

    fun boundaryPillContentColor(index: Int, dark: Boolean = false): Color =
        onContainerColor(index, dark)

    fun boundaryPillContrastRatio(index: Int, dark: Boolean = false): Double =
        contrastRatio(boundaryPillContainerColor(index, dark), boundaryPillContentColor(index, dark))

    fun contrastRatio(index: Int, dark: Boolean = false): Double {
        return contrastRatio(containerColor(index, dark), onContainerColor(index, dark))
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
