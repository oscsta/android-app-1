package com.github.oscsta.runni

import java.util.Locale
import kotlin.time.Duration

fun Duration.toHourMinuteSecondColonDelimited(): String {
    return this.toComponents { hh, mm, ss, _ ->
        String.format(
            Locale.ROOT, "%02d:%02d:%02d", hh, mm, ss
        )
    }
}