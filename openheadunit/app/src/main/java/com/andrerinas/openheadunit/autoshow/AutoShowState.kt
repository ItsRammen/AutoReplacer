package com.andrerinas.openheadunit.autoshow

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the car session is doing, for the app's main screen. Lives outside [AutoShowService] so the
 * screen can show "waiting for the car" while the service is not running at all.
 */
object AutoShowState {
    enum class Phase { WAITING, CONNECTING, STARTING_ANDROID_AUTO, RUNNING, STANDBY, ENDED }

    /** What the car's touch link is doing. Kept apart from the display text, which is translated. */
    enum class TouchState { WAITING, CONNECTED, WORKING, BLOCKED }

    data class Snapshot(
        val phase: Phase = Phase.WAITING,
        /** One line for the current step, or why the last session ended. */
        val headline: String = "",
        /** The touch line for the screen; empty until something is known (the screen then says "waiting for the car"). */
        val touch: String = "",
        val touchState: TouchState = TouchState.WAITING,
        /** The Bluetooth line for the screen; empty until the first check. */
        val bluetooth: String = "",
        val picture: Boolean = false,
    )

    private val flow = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> get() = flow

    @Synchronized fun update(change: (Snapshot) -> Snapshot) { flow.value = change(flow.value) }

    /** A new car session starts: forget the previous one's touch, picture and Bluetooth lines. */
    fun reset(phase: Phase, headline: String) {
        flow.value = Snapshot(phase = phase, headline = headline)
    }
}
