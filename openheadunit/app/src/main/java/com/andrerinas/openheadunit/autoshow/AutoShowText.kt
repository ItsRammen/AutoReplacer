package com.andrerinas.openheadunit.autoshow

import android.content.Context
import com.andrerinas.openheadunit.R

/** Turns the plain-Java classes' reason codes into the words in the app's string resources, so they can be translated. */
object AutoShowText {
    fun reason(context: Context, code: DisconnectReason.Code): String = context.getString(when (code) {
        DisconnectReason.Code.NOT_READY -> R.string.as_dc_not_ready
        DisconnectReason.Code.NO_RESPONSE -> R.string.as_dc_no_response
        DisconnectReason.Code.NO_FIRST_BUFFER -> R.string.as_dc_no_first_buffer
        DisconnectReason.Code.NO_PROGRESS -> R.string.as_dc_no_progress
        DisconnectReason.Code.MOVING -> R.string.as_dc_moving
        DisconnectReason.Code.USB_OPEN -> R.string.as_dc_usb_open
        DisconnectReason.Code.STALLED -> R.string.as_dc_stalled
        DisconnectReason.Code.UNSUPPORTED -> R.string.as_unsupported_format
        DisconnectReason.Code.USB_LOST -> R.string.as_dc_lost
        DisconnectReason.Code.UNEXPECTED -> R.string.as_dc_error
        DisconnectReason.Code.CLOSED -> R.string.as_dc_closed
    })

    fun bluetooth(context: Context, action: BluetoothPlan.Action): String = context.getString(when (action) {
        BluetoothPlan.Action.NO_ADDRESS -> R.string.as_bt_no_address
        BluetoothPlan.Action.NO_BLUETOOTH -> R.string.as_bt_none
        BluetoothPlan.Action.NEEDS_PERMISSION -> R.string.as_bt_permission
        BluetoothPlan.Action.TURN_ON_BLUETOOTH -> R.string.as_bt_turn_on
        BluetoothPlan.Action.PAIR -> R.string.as_bt_pairing
        BluetoothPlan.Action.CHECK_AUDIO -> R.string.as_bt_paired_waiting
    })

    fun audio(context: Context, audio: BluetoothPlan.Audio): String = context.getString(when (audio) {
        BluetoothPlan.Audio.MEDIA_AND_CALLS -> R.string.as_bt_audio_both
        BluetoothPlan.Audio.MEDIA_ONLY -> R.string.as_bt_audio_media
        BluetoothPlan.Audio.CALLS_ONLY -> R.string.as_bt_audio_calls
        BluetoothPlan.Audio.NONE -> R.string.as_bt_audio_none
    })

    /** The app's strings in English whatever the phone's language is, for the saved log that gets sent to the developer. */
    fun english(context: Context): Context {
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(java.util.Locale.ENGLISH)
        return context.createConfigurationContext(config)
    }

    /** Two sentences side by side (a space between them in English, none in Chinese). */
    fun join(context: Context, first: String, second: String): String = context.getString(R.string.as_join, first, second)
}
