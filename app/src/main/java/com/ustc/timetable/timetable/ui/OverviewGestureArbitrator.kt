package com.ustc.timetable.timetable.ui

import kotlin.math.abs
import kotlin.math.hypot

enum class GridGestureOwner {
    IDLE,
    PRESS_PENDING,
    DIRECTION_PENDING,
    CLICK_OWNED,
    LONG_PRESS_OWNED,
    HORIZONTAL_OWNED,
    VERTICAL_OWNED,
}

enum class GridPressTarget { EMPTY, SCHOOL_CARD, MANUAL_CARD }

enum class VerticalOverviewAction { EXPAND, COLLAPSE }

sealed interface GridGestureDecision {
    data object None : GridGestureDecision
    data object Click : GridGestureDecision
    data object LongPress : GridGestureDecision
    data object ConsumeCardLongPress : GridGestureDecision
    data object YieldToHorizontalPager : GridGestureDecision
    data class ChangeOverview(val action: VerticalOverviewAction) : GridGestureDecision
}

class OverviewGestureArbitrator(private val touchSlopPx: Float) {
    init {
        require(touchSlopPx > 0f)
    }

    var owner: GridGestureOwner = GridGestureOwner.IDLE
        private set

    private var target: GridPressTarget? = null

    fun onDown(target: GridPressTarget) {
        require(owner == GridGestureOwner.IDLE)
        this.target = target
        owner = GridGestureOwner.PRESS_PENDING
    }

    fun onMove(totalDxPx: Float, totalDyPx: Float): GridGestureDecision = when (owner) {
        GridGestureOwner.PRESS_PENDING -> {
            if (hypot(totalDxPx, totalDyPx) <= touchSlopPx) {
                GridGestureDecision.None
            } else {
                claimDirection(totalDxPx, totalDyPx)
            }
        }
        GridGestureOwner.DIRECTION_PENDING -> claimDirection(totalDxPx, totalDyPx)
        GridGestureOwner.HORIZONTAL_OWNED -> GridGestureDecision.YieldToHorizontalPager
        GridGestureOwner.IDLE,
        GridGestureOwner.CLICK_OWNED,
        GridGestureOwner.LONG_PRESS_OWNED,
        GridGestureOwner.VERTICAL_OWNED,
        -> GridGestureDecision.None
    }

    fun onLongPressTimeout(): GridGestureDecision {
        if (owner != GridGestureOwner.PRESS_PENDING) return GridGestureDecision.None
        owner = GridGestureOwner.LONG_PRESS_OWNED
        return when (target) {
            GridPressTarget.EMPTY -> GridGestureDecision.LongPress
            GridPressTarget.SCHOOL_CARD,
            GridPressTarget.MANUAL_CARD,
            -> GridGestureDecision.ConsumeCardLongPress
            null -> GridGestureDecision.None
        }
    }

    fun onUp(): GridGestureDecision {
        val decision = if (owner == GridGestureOwner.PRESS_PENDING) {
            owner = GridGestureOwner.CLICK_OWNED
            GridGestureDecision.Click
        } else {
            GridGestureDecision.None
        }
        reset()
        return decision
    }

    fun onCancel() {
        reset()
    }

    private fun claimDirection(totalDxPx: Float, totalDyPx: Float): GridGestureDecision {
        val absX = abs(totalDxPx)
        val absY = abs(totalDyPx)
        return when {
            absX > absY -> {
                owner = GridGestureOwner.HORIZONTAL_OWNED
                GridGestureDecision.YieldToHorizontalPager
            }
            absY > absX -> {
                owner = GridGestureOwner.VERTICAL_OWNED
                GridGestureDecision.ChangeOverview(
                    if (totalDyPx > 0f) VerticalOverviewAction.EXPAND else VerticalOverviewAction.COLLAPSE,
                )
            }
            else -> {
                owner = GridGestureOwner.DIRECTION_PENDING
                GridGestureDecision.None
            }
        }
    }

    private fun reset() {
        owner = GridGestureOwner.IDLE
        target = null
    }
}
