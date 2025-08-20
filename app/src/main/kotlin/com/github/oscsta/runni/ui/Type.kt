package com.github.oscsta.runni.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography.labelExtraLarge: TextStyle
    get() = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
    )
val AppTypography = Typography()
