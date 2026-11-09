package com.andrerinas.openheadunit.autoshow

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the car session is doing, for the app's main screen. Lives outside [AutoShowService] so the
 * screen can show "waiting for the car" while the service is not running at all.
 */
object AutoShowState {
    enum class Phase { WAITING, CONNECTING, STARTING_ANDROID_AUTO, RUNNING, STANDBY, ENDED }

    data class Snapshot(
        val phase: Phase = Phase.WAITING,
        /** One line for the current step, or why the last session ended. */
        val headline: String = "",
        val touch: String = "Waiting for the car",
        val bluetooth: String = "Not checked yet",
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
