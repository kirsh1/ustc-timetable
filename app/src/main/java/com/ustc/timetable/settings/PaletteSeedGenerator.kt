package com.ustc.timetable.settings

import com.ustc.timetable.timetable.ui.CoursePalette
import java.security.SecureRandom

fun interface PaletteSeedSource { fun nextLong(): Long }

object SecurePaletteSeedSource : PaletteSeedSource {
    private val random by lazy { SecureRandom() }
    override fun nextLong(): Long = random.nextLong()
}

object PaletteSeedGenerator {
    fun nextDistinct(currentSeed: Long, source: PaletteSeedSource): Long {
        val candidate = source.nextLong()
        return if (CoursePalette.variantFor(candidate) != CoursePalette.variantFor(currentSeed)) candidate
        else ((CoursePalette.variantFor(currentSeed) + 1) % CoursePalette.PALETTE_VARIANT_COUNT).toLong()
    }
}
