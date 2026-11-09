package com.andrerinas.openheadunit.autoshow

import android.content.Context

/**
 * What AutoReplacer has learned about the car's touch area, and the user's override of it. The
 * largest touch coordinate seen on each axis is kept between sessions, so the right mapping is used
 * from the first touch of the next drive.
 */
object AutoShowTouchArea {
    private const val PREFS = "autoshow_touch"
    private const val MAX_X = "maxX"
    private const val MAX_Y = "maxY"
    private const val DECLARED_W = "declaredW"
    private const val DECLARED_H = "declaredH"
    private const val OVERRIDE_H = "overrideHeight"

    /** Heights offered by the menu: automatic (0), the declared picture height, then common panel heights. */
    private val choices = intArrayOf(0, -1, 800)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun maxX(context: Context): Int = prefs(context).getInt(MAX_X, 0)
    fun maxY(context: Context): Int = prefs(context).getInt(MAX_Y, 0)

    /** Remembers a larger coordinate. Writes only when the maximum grows, which is rare. */
    fun learn(context: Context, x: Int, y: Int) {
        val p = prefs(context)
        val edit = p.edit()
        var changed = false
        if (x in 1..4000 && x > p.getInt(MAX_X, 0)) { edit.putInt(MAX_X, x); changed = true }
        if (y in 1..4000 && y > p.getInt(MAX_Y, 0)) { edit.putInt(MAX_Y, y); changed = true }
        if (changed) edit.apply()
    }

    fun rememberDeclared(context: Context, width: Int, height: Int) {
        prefs(context).edit().putInt(DECLARED_W, width).putInt(DECLARED_H, height).apply()
    }

    /** 0 = automatic, otherwise the touch height the user chose. */
    fun overrideHeight(context: Context): Int = prefs(context).getInt(OVERRIDE_H, 0)

    fun heightExtent(context: Context, declared: Int): Int =
        TouchArea.extent(declared, maxY(context), overrideHeight(context), true)

    fun widthExtent(context: Context, declared: Int): Int =
        TouchArea.extent(declared, maxX(context), 0, false)

    fun describe(context: Context): String {
        val p = prefs(context)
        val dw = p.getInt(DECLARED_W, 1280).takeIf { it > 0 } ?: 1280
        val dh = p.getInt(DECLARED_H, 720).takeIf { it > 0 } ?: 720
        val override = overrideHeight(context)
        val w = widthExtent(context, dw)
        return if (override > 0) "${w}x$override (chosen)" else "Auto (${w}x${heightExtent(context, dh)})"
    }

    /** Auto, then the picture's own height (no scaling), then 800; learned maxima are cleared on the way back to Auto. */
    fun cycle(context: Context): String {
        val p = prefs(context)
        val declaredH = p.getInt(DECLARED_H, 720).takeIf { it > 0 } ?: 720
        val current = overrideHeight(context)
        val index = when {
            current == 0 -> 0
            current == declaredH -> 1
            else -> 2
        }
        val next = (index + 1) % choices.size
        val edit = p.edit()
        when (choices[next]) {
            0 -> edit.putInt(OVERRIDE_H, 0).remove(MAX_X).remove(MAX_Y)
            -1 -> edit.putInt(OVERRIDE_H, declaredH)
            else -> edit.putInt(OVERRIDE_H, choices[next])
        }
        edit.apply()
        return describe(context)
    }
}
