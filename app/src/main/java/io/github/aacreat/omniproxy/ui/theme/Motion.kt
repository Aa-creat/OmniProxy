package io.github.aacreat.omniproxy.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object ExpressiveMotion {
    fun <T> bouncySpring(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> fluidSpring(): AnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )

    fun <T> smoothTween(durationMillis: Int = 300): AnimationSpec<T> = tween(
        durationMillis = durationMillis,
        easing = FastOutSlowInEasing
    )
}
