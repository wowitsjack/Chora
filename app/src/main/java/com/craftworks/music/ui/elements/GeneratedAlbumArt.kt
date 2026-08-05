package com.craftworks.music.ui.elements

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.craftworks.music.R
import com.craftworks.music.managers.settings.ArtworkSettingsManager
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private data class MutationSpec(
    val rotationDegrees: Float,
    val mirrorX: Boolean,
    val mirrorY: Boolean,
    val scale: Float,
    val pivotX: Float,
    val pivotY: Float,
    val tintStart: Color,
    val tintEnd: Color,
    val tintDirection: Int,
    val accentX: Float,
    val accentY: Float,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float
)

private data class StyleProfile(
    val tintAlpha: Float,
    val accentAlpha: Float,
    val vignetteAlpha: Float,
    val scaleBoost: Float,
    val saturationScale: Float,
    val contrastScale: Float
)

/** Stable identity hash for list artwork; a variation creates a fresh mutation. */
internal fun generatedArtworkSeed(
    title: String,
    artist: String?,
    album: String?,
    variationSeed: Long = 0L
): Long {
    var hash = 0x2F5E2B3C4D5E6F7AL xor variationSeed
    val content = buildString {
        append(title)
        append('\u001F')
        append(artist.orEmpty())
        append('\u001F')
        append(album.orEmpty())
    }
    content.forEach { character ->
        hash = (hash xor character.code.toLong()) * 1099511628211L
    }
    hash = hash xor (hash ushr 30)
    hash *= 0x3C79AC492BA7B653L
    hash = hash xor (hash ushr 27)
    hash *= 0x1C69B3F74AC4AE35L
    return hash xor (hash ushr 33)
}

internal fun generatedArtworkColorSignature(
    title: String,
    artist: String?,
    album: String?,
    variationSeed: Long = 0L,
    palette: ArtworkSettingsManager.ColorPalette = ArtworkSettingsManager.ColorPalette.MATERIAL_YOU
): Pair<Int, Int> {
    val (start, end) = generatedColors(
        generatedArtworkSeed(title, artist, album, variationSeed),
        palette
    )
    return start.toArgb() to end.toArgb()
}

@Composable
fun GeneratedAlbumArt(
    title: String,
    artist: String?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    animate: Boolean = true,
    album: String? = null,
    colors: List<Color>? = null
) {
    val context = LocalContext.current
    val settingsManager = remember { ArtworkSettingsManager(context) }
    val style by settingsManager.artworkStyleFlow.collectAsStateWithLifecycle(
        ArtworkSettingsManager.ArtworkStyle.GRADIENT
    )
    val palette by settingsManager.colorPaletteFlow.collectAsStateWithLifecycle(
        ArtworkSettingsManager.ColorPalette.MATERIAL_YOU
    )
    val shouldAnimate by settingsManager.animateArtworkFlow.collectAsStateWithLifecycle(false)
    val variationSeed = remember(title, artist, album) { Random.nextLong() }

    GeneratedArtwork(
        title = title,
        artist = artist,
        album = album,
        variationSeed = variationSeed,
        modifier = modifier,
        artSize = size,
        style = style,
        palette = palette,
        colors = colors,
        motion = artworkMotion(animate && shouldAnimate)
    )
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun GeneratedAlbumArtStatic(
    title: String,
    artist: String?,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    album: String? = null,
    colors: List<Color>? = null,
    artworkStyle: ArtworkSettingsManager.ArtworkStyle? = null,
    colorPalette: ArtworkSettingsManager.ColorPalette? = null,
    showInitialsOverride: Boolean? = null
) {
    val style: ArtworkSettingsManager.ArtworkStyle
    val palette: ArtworkSettingsManager.ColorPalette
    if (artworkStyle != null && colorPalette != null) {
        style = artworkStyle
        palette = colorPalette
    } else {
        val context = LocalContext.current
        val settingsManager = remember { ArtworkSettingsManager(context) }
        style = artworkStyle ?: settingsManager.artworkStyleFlow.collectAsStateWithLifecycle(
            ArtworkSettingsManager.ArtworkStyle.GRADIENT
        ).value
        palette = colorPalette ?: settingsManager.colorPaletteFlow.collectAsStateWithLifecycle(
            ArtworkSettingsManager.ColorPalette.MATERIAL_YOU
        ).value
    }

    GeneratedArtwork(
        title = title,
        artist = artist,
        album = album,
        variationSeed = 0L,
        modifier = modifier,
        artSize = size,
        style = style,
        palette = palette,
        colors = colors,
        motion = 0f
    )
}

@Composable
private fun artworkMotion(enabled: Boolean): Float {
    if (!enabled) return 0f
    val transition = rememberInfiniteTransition(label = "generated_artwork_motion")
    val motion by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "generated_artwork_phase"
    )
    return motion
}

@Composable
private fun GeneratedArtwork(
    title: String,
    artist: String?,
    album: String?,
    variationSeed: Long,
    modifier: Modifier,
    artSize: Dp,
    style: ArtworkSettingsManager.ArtworkStyle,
    palette: ArtworkSettingsManager.ColorPalette,
    colors: List<Color>?,
    motion: Float
) {
    val mutation = remember(title, artist, album, variationSeed, palette, colors) {
        createMutationSpec(title, artist, album, variationSeed, palette, colors)
    }
    val profile = remember(style) { styleProfile(style) }
    val colorFilter = remember(mutation, profile) {
        ColorFilter.colorMatrix(
            adjustedColorMatrix(
                saturation = mutation.saturation * profile.saturationScale,
                contrast = mutation.contrast * profile.contrastScale,
                brightness = mutation.brightness
            )
        )
    }
    val animatedDrift = sin(motion * PI.toFloat())
    val finalScale = mutation.scale + profile.scaleBoost + animatedDrift * 0.012f

    Box(
        modifier = modifier
            .size(artSize)
            .clipToBounds()
    ) {
        Image(
            painter = painterResource(R.drawable.generated_artwork_base),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = mutation.rotationDegrees + animatedDrift * 1.4f
                    scaleX = finalScale * if (mutation.mirrorX) -1f else 1f
                    scaleY = finalScale * if (mutation.mirrorY) -1f else 1f
                    transformOrigin = TransformOrigin(mutation.pivotX, mutation.pivotY)
                }
                .drawWithCache {
                    val start = when (mutation.tintDirection) {
                        0 -> Offset.Zero
                        1 -> Offset(size.width, 0f)
                        2 -> Offset(0f, size.height)
                        else -> Offset(size.width, size.height)
                    }
                    val end = Offset(size.width - start.x, size.height - start.y)
                    val tint = Brush.linearGradient(
                        colors = listOf(mutation.tintStart, mutation.tintEnd),
                        start = start,
                        end = end
                    )
                    val vignette = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = profile.vignetteAlpha)),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension * 0.76f
                    )

                    onDrawWithContent {
                        drawContent()
                        drawRect(
                            brush = tint,
                            alpha = profile.tintAlpha,
                            blendMode = BlendMode.Color
                        )
                        drawCircle(
                            color = mutation.tintEnd.copy(alpha = profile.accentAlpha),
                            radius = size.maxDimension * 0.32f,
                            center = Offset(
                                size.width * mutation.accentX,
                                size.height * mutation.accentY
                            ),
                            blendMode = BlendMode.Softlight
                        )
                        drawRect(brush = vignette)
                    }
                }
        )
    }
}

private fun createMutationSpec(
    title: String,
    artist: String?,
    album: String?,
    variationSeed: Long,
    palette: ArtworkSettingsManager.ColorPalette,
    passedColors: List<Color>?
): MutationSpec {
    val seed = generatedArtworkSeed(title, artist, album, variationSeed)
    val random = Random((seed xor (seed ushr 32)).toInt())
    val selectedColors = when {
        passedColors != null && passedColors.size >= 2 -> passedColors[0] to passedColors[1]
        else -> generatedColors(seed, palette)
    }

    val saturation = when (palette) {
        ArtworkSettingsManager.ColorPalette.VIBRANT -> 1.18f
        ArtworkSettingsManager.ColorPalette.PASTEL -> 0.72f
        ArtworkSettingsManager.ColorPalette.MONOCHROME -> 0f
        else -> 0.94f
    }

    return MutationSpec(
        rotationDegrees = random.nextFloat() * 360f,
        mirrorX = random.nextBoolean(),
        mirrorY = random.nextBoolean(),
        // A square needs at most sqrt(2) scale to cover its bounds at any rotation.
        // Staying above that threshold gives every identity a distinct free rotation
        // without ever exposing the container behind the bitmap.
        scale = 1.46f + random.nextFloat() * 0.16f,
        pivotX = 0.5f,
        pivotY = 0.5f,
        tintStart = selectedColors.first,
        tintEnd = selectedColors.second,
        tintDirection = random.nextInt(4),
        accentX = if (random.nextBoolean()) -0.08f else 1.08f,
        accentY = 0.18f + random.nextFloat() * 0.64f,
        saturation = saturation,
        contrast = 0.94f + random.nextFloat() * 0.16f,
        brightness = -0.025f + random.nextFloat() * 0.05f
    )
}

private fun styleProfile(style: ArtworkSettingsManager.ArtworkStyle): StyleProfile = when (style) {
    ArtworkSettingsManager.ArtworkStyle.GRADIENT -> StyleProfile(0.68f, 0.20f, 0.18f, 0f, 0.94f, 0.98f)
    ArtworkSettingsManager.ArtworkStyle.SOLID -> StyleProfile(0.80f, 0.28f, 0.24f, 0.02f, 1.10f, 1.08f)
    ArtworkSettingsManager.ArtworkStyle.PATTERN -> StyleProfile(0.58f, 0.23f, 0.14f, 0.07f, 1f, 1.03f)
    ArtworkSettingsManager.ArtworkStyle.WAVEFORM -> StyleProfile(0.72f, 0.15f, 0.28f, 0.12f, 0.82f, 1.12f)
    ArtworkSettingsManager.ArtworkStyle.MINIMAL -> StyleProfile(0.42f, 0.09f, 0.10f, 0.16f, 0.72f, 0.94f)
}

private fun generatedColors(
    seed: Long,
    palette: ArtworkSettingsManager.ColorPalette
): Pair<Color, Color> {
    val positiveSeed = seed and Long.MAX_VALUE
    val hue = (positiveSeed % 36_000L).toFloat() / 100f
    val hueOffset = 72f + ((positiveSeed ushr 16) % 9_600L).toFloat() / 100f
    val secondHue = (hue + hueOffset) % 360f

    return when (palette) {
        ArtworkSettingsManager.ColorPalette.MATERIAL_YOU ->
            Color.hsl(hue, 0.62f, 0.40f) to Color.hsl(secondHue, 0.66f, 0.64f)
        ArtworkSettingsManager.ColorPalette.VIBRANT ->
            Color.hsl(hue, 0.84f, 0.44f) to Color.hsl(secondHue, 0.78f, 0.60f)
        ArtworkSettingsManager.ColorPalette.PASTEL ->
            Color.hsl(hue, 0.42f, 0.72f) to Color.hsl(secondHue, 0.38f, 0.80f)
        ArtworkSettingsManager.ColorPalette.MONOCHROME ->
            Color.hsl(hue, 0.07f, 0.28f) to Color.hsl(secondHue, 0.09f, 0.68f)
        ArtworkSettingsManager.ColorPalette.DYNAMIC ->
            Color.hsl(hue, 0.66f, 0.42f) to Color.hsl(secondHue, 0.62f, 0.64f)
    }
}

private fun adjustedColorMatrix(
    saturation: Float,
    contrast: Float,
    brightness: Float
): ColorMatrix {
    val inverseSaturation = 1f - saturation
    val red = 0.2126f * inverseSaturation
    val green = 0.7152f * inverseSaturation
    val blue = 0.0722f * inverseSaturation
    val translation = (1f - contrast) * 128f + brightness * 255f

    return ColorMatrix(
        floatArrayOf(
            (red + saturation) * contrast, green * contrast, blue * contrast, 0f, translation,
            red * contrast, (green + saturation) * contrast, blue * contrast, 0f, translation,
            red * contrast, green * contrast, (blue + saturation) * contrast, 0f, translation,
            0f, 0f, 0f, 1f, 0f
        )
    )
}
