package com.example.hsiaowear.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
data class AppSpacing(
    val xs: androidx.compose.ui.unit.Dp = 4.dp,
    val sm: androidx.compose.ui.unit.Dp = 8.dp,
    val md: androidx.compose.ui.unit.Dp = 12.dp,
    val lg: androidx.compose.ui.unit.Dp = 16.dp,
    val xl: androidx.compose.ui.unit.Dp = 20.dp,
    val xxl: androidx.compose.ui.unit.Dp = 24.dp,
    val xxxl: androidx.compose.ui.unit.Dp = 32.dp,
    val huge: androidx.compose.ui.unit.Dp = 48.dp,
    val giant: androidx.compose.ui.unit.Dp = 64.dp,
)

@Immutable
data class AppShape(
    val small: Shape = RoundedCornerShape(8.dp),
    val medium: Shape = RoundedCornerShape(12.dp),
    val large: Shape = RoundedCornerShape(16.dp),
    val extraLarge: Shape = RoundedCornerShape(20.dp),
    val pill: Shape = RoundedCornerShape(999.dp),
    val sheet: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    val card: Shape = RoundedCornerShape(18.dp),
    val button: Shape = RoundedCornerShape(14.dp),
    val input: Shape = RoundedCornerShape(12.dp),
)

@Immutable
data class AppElevation(
    val none: androidx.compose.ui.unit.Dp = 0.dp,
    val sm: androidx.compose.ui.unit.Dp = 1.dp,
    val md: androidx.compose.ui.unit.Dp = 2.dp,
    val lg: androidx.compose.ui.unit.Dp = 4.dp,
    val card: androidx.compose.ui.unit.Dp = 0.dp,
)

val LocalAppSpacing = staticCompositionLocalOf { AppSpacing() }
val LocalAppShape = staticCompositionLocalOf { AppShape() }
val LocalAppElevation = staticCompositionLocalOf { AppElevation() }
