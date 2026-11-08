package com.andrerinas.openheadunit.autoshow

import com.andrerinas.openheadunit.main.settings.SettingItem

/**
 * What the AutoReplacer build hides from Open Headunit's screens. Auto+ Show mode only uses the
 * phone's own Android Auto session, so wireless/USB connection set-up, head-unit launcher and
 * display options do not apply. Only the screens change: the code behind them is untouched, and
 * [ENABLED] = false restores the original lists.
 */
object AutoShowUi {
    const val ENABLED = true

    /** Whole settings sections that make no sense for Auto+ Show mode. */
    val hiddenSettingsSections: Set<String> = setOf(
        "wirelessConnection", // Wi-Fi, hotspot, native wireless
        "automation",         // auto-start / auto-connect / exit actions of a head unit
        "input",              // key mapping for head unit buttons
        "navigation",         // external GPS for a connected phone
        "theming",            // launcher look
        "moreFeatures"        // car launcher, floating button, OEM apps
    )

    /** Single settings hidden inside sections that stay visible. */
    val hiddenSettingIds: Set<String> = setOf(
        "connectionMode", "listenForUsbDevices", "useLibusb", "bluetoothAddress",
        "resolution", "screenOrientation", "startInFullscreenMode", "videoFitMode",
        "customInsets", "pixelAspectRatioE4", "viewMode", "forcedScale", "hudMirroring"
    )

    /** Removes the hidden sections (header and entries) and entries from a settings list. */
    fun filterSettings(items: List<SettingItem>): List<SettingItem> {
        if (!ENABLED) return items
        val out = ArrayList<SettingItem>(items.size)
        var skipping = false
        for (item in items) {
            if (item is SettingItem.CategoryHeader) skipping = item.stableId in hiddenSettingsSections
            if (skipping) continue
            if (item.stableId in hiddenSettingIds) continue
            out.add(item)
        }
        return out
    }

    /**
     * First-run wizard steps skipped (by index): connection type, video test, display, DPI,
     * appearance, sound, automation, car launcher, location and vehicle. Welcome, safety,
     * permissions and the final summary stay.
     */
    val hiddenOnboardingSteps: Set<Int> = setOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11)

    fun hidesOnboardingStep(step: Int): Boolean = ENABLED && step in hiddenOnboardingSteps
}
