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
        "customInsets", "pixelAspectRatioE4", "viewMode", "forcedScale", "hudMirroring",
        // General / video: head-unit display tuning and decoder choices. The decoder must keep
        // drawing to a Surface (the car encoder's), which the software decoder does not do.
        "autoOptimize", "videoCodec", "narrowBandProfileCap", "forceSoftwareDecoding",
        "softwareVideoDecoder", "debugVideoLowLatency",
        // Audio: Self mode announces only system sounds to Android Auto, so the media/speech sink
        // tuning below has nothing to act on. The microphone and playback-resume settings stay.
        "enableAudioSink", "staticAudioFocus", "playbackFocusMode", "playbackFocusModeStaticHint",
        "audioStreamSettings", "useAAudioOutput", "useAacAudio", "attachHwDspEqualizer",
        "syncMediaSessionAaMetadata", "audioVolumeOffsets", "audioMatchingMode",
        "audioMatchingModeHint", "audioLatencyMultiplier", "audioQueueCapacity",
        // Debug: developer fault-injection tools and an overlay that belongs to the unused projection screen.
        "performanceOverlay", "debugForceMemoryProfile", "debugVideoFeedHold",
        "debugVideoFaultInjection", "debugVideoFaultRate", "debugVideoFaultBudget",
        // Info: upstream's update check and issue flow point at the original Open Headunit project.
        "check_for_updates", "support"
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

    /**
     * Permission rows not needed here: "appear on top" (only used to raise Open Headunit's own
     * screen from the background), write system settings and legacy storage.
     */
    val hiddenPermissionRows: Set<String> = setOf("overlay", "write_settings", "storage")

    fun hidesPermissionRow(id: String): Boolean = ENABLED && id in hiddenPermissionRows
}
