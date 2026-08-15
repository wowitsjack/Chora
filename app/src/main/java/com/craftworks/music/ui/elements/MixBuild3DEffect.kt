package com.craftworks.music.ui.elements

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MIX_BUILD_CYCLE_MS = 1_650

internal data class MixBuildTransform(
    val rotationX: Float,
    val rotationY: Float,
    val rotationZ: Float,
    val scale: Float,
    val translationYDp: Float,
    val shadowElevationDp: Float
)

internal fun mixBuildTransform(progress: Float): MixBuildTransform {
    val radians = progress * (PI * 2.0).toFloat()
    val orbitDepth = (1f - cos(radians)) / 2f
    return MixBuildTransform(
        rotationX = -orbitDepth * 5f,
        rotationY = sin(radians) * 7f,
        rotationZ = 0f,
        scale = 1f - orbitDepth * 0.035f,
        translationYDp = -orbitDepth * 7f,
        shadowElevationDp = orbitDepth * 26f
    )
}

@Composable
fun Modifier.mixBuild3DEffect(active: Boolean): Modifier {
    val rotation = remember { Animatable(0f) }
    val density = LocalDensity.current.density

    LaunchedEffect(active) {
        if (active) {
            rotation.snapTo(0f)
            while (currentCoroutineContext().isActive) {
                rotation.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = MIX_BUILD_CYCLE_MS, easing = LinearEasing)
                )
                rotation.snapTo(0f)
            }
        } else {
            val remainingCycle = (1f - rotation.value).coerceIn(0f, 1f)
            rotation.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = (MIX_BUILD_CYCLE_MS * remainingCycle)
                        .roundToInt()
                        .coerceAtLeast(80),
                    easing = LinearEasing
                )
            )
            rotation.snapTo(0f)
        }
    }

    return graphicsLayer {
        val transform = mixBuildTransform(rotation.value)
        rotationY = transform.rotationY
        rotationX = transform.rotationX
        rotationZ = transform.rotationZ
        scaleX = transform.scale
        scaleY = transform.scale
        translationX = 0f
        translationY = transform.translationYDp * density
        cameraDistance = 8f * density
        shadowElevation = transform.shadowElevationDp * density
        transformOrigin = TransformOrigin.Center
    }
}
