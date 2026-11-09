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

    /** The estimate used until the touch has been calibrated: the largest coordinate seen, rounded to a panel size. */
    fun heightExtent(context: Context, declared: Int): Int =
        TouchArea.extent(declared, maxY(context), 0, true)

    fun widthExtent(context: Context, declared: Int): Int =
        TouchArea.extent(declared, maxX(context), 0, false)

    /** The calibrated mapping {ax, bx, ay, by} (measured = a * picture fraction + b), or null. */
    fun calibration(context: Context): DoubleArray? {
        val p = prefs(context)
        if (!p.getBoolean("calibrated", false)) return null
        return doubleArrayOf(
            p.getString("calAx", null)?.toDoubleOrNull() ?: return null, p.getString("calBx", null)?.toDoubleOrNull() ?: return null,
            p.getString("calAy", null)?.toDoubleOrNull() ?: return null, p.getString("calBy", null)?.toDoubleOrNull() ?: return null)
    }

    /** Keeps the current calibration as "previous", so a reset or a bad new calibration can be undone. */
    private fun rememberPrevious(context: Context) {
        val c = calibration(context) ?: return
        prefs(context).edit().putBoolean("hasPrevious", true)
            .putString("prevAx", c[0].toString()).putString("prevBx", c[1].toString())
            .putString("prevAy", c[2].toString()).putString("prevBy", c[3].toString()).apply()
    }

    fun previous(context: Context): DoubleArray? {
        val p = prefs(context)
        if (!p.getBoolean("hasPrevious", false)) return null
        return doubleArrayOf(
            p.getString("prevAx", null)?.toDoubleOrNull() ?: return null, p.getString("prevBx", null)?.toDoubleOrNull() ?: return null,
            p.getString("prevAy", null)?.toDoubleOrNull() ?: return null, p.getString("prevBy", null)?.toDoubleOrNull() ?: return null)
    }

    fun describePrevious(context: Context): String? = previous(context)?.let {
        String.format(java.util.Locale.US, "width %.2f%+.2f, height %.2f%+.2f", it[0], it[1], it[2], it[3])
    }

    /** Puts the previous calibration back. The one it replaces (if any) becomes the new "previous". */
    fun restorePrevious(context: Context): Boolean {
        val prev = previous(context) ?: return false
        rememberPrevious(context)
        prefs(context).edit().putBoolean("calibrated", true)
            .putString("calAx", prev[0].toString()).putString("calBx", prev[1].toString())
            .putString("calAy", prev[2].toString()).putString("calBy", prev[3].toString()).apply()
        return true
    }

    fun saveCalibration(context: Context, ax: Double, bx: Double, ay: Double, by: Double) {
        rememberPrevious(context)
        prefs(context).edit().putBoolean("calibrated", true)
            .putString("calAx", ax.toString()).putString("calBx", bx.toString())
            .putString("calAy", ay.toString()).putString("calBy", by.toString()).apply()
    }

    fun clearCalibration(context: Context) {
        rememberPrevious(context)
        prefs(context).edit().putBoolean("calibrated", false).apply()
    }

    /** One line for the settings screen and the menu. */
    fun describe(context: Context): String {
        calibration(context)?.let {
            return String.format(java.util.Locale.US, "Calibrated (width %.2f%+.2f, height %.2f%+.2f)", it[0], it[1], it[2], it[3])
        }
        return "Not calibrated (estimated from your taps)"
    }

    fun isCalibrated(context: Context): Boolean = calibration(context) != null
}
