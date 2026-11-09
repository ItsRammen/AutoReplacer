package com.andrerinas.openheadunit.autoshow

import com.andrerinas.openheadunit.main.settings.SettingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoShowUiTest {
    private fun header(id: String) = SettingItem.CategoryHeader(id, 0)
    private fun entry(id: String) = SettingItem.InfoBanner(id, 0, id)
    private fun ids(items: List<SettingItem>) = items.map { it.stableId }

    @Test fun `hidden sections vanish with their header and entries`() {
        val items = listOf(
            header("general"), entry("autoOptimize"),
            header("wirelessConnection"), entry("wifiConnectionMode"), entry("hotspotBand"),
            header("audio"), entry("enableAudioSink"),
            header("moreFeatures"), entry("enableCarLauncher"),
            header("debug"), entry("debugMode")
        )
        assertEquals(
            listOf("general", "autoOptimize", "audio", "enableAudioSink", "debug", "debugMode"),
            ids(AutoShowUi.filterSettings(items))
        )
    }

    @Test fun `single hidden settings go while their section stays`() {
        val items = listOf(
            header("general"), entry("connectionMode"), entry("appLanguage"), entry("permissions"),
            header("graphic"), entry("resolution"), entry("dpiPixelDensity")
        )
        assertEquals(
            listOf("general", "appLanguage", "permissions", "graphic", "dpiPixelDensity"),
            ids(AutoShowUi.filterSettings(items))
        )
    }

    @Test fun `needed sections and debug export stay`() {
        val keep = listOf("general", "graphic", "video", "audio", "backup", "debug", "info")
        assertTrue(keep.none { it in AutoShowUi.hiddenSettingsSections })
    }

    @Test fun `items before any header are kept`() {
        val items = listOf(entry("intro"), header("general"), entry("autoOptimize"))
        assertEquals(listOf("intro", "general", "autoOptimize"), ids(AutoShowUi.filterSettings(items)))
    }

    @Test fun `welcome safety permissions and ready steps stay in the wizard`() {
        for (step in listOf(0, 1, 12, 13)) assertFalse("step $step", AutoShowUi.hidesOnboardingStep(step))
        for (step in 2..11) assertTrue("step $step", AutoShowUi.hidesOnboardingStep(step))
    }
}
