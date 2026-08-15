package com.craftworks.music.data.repository

import androidx.media3.common.MediaItem
import com.craftworks.music.data.model.DiscoveryMetadataKeys
import com.craftworks.music.data.model.DiscoveryMixMode
import com.craftworks.music.data.model.DiscoveryMixRequest
import java.time.Duration
import java.time.Instant

internal fun discoveryRequestForMode(
    mode: DiscoveryMixMode,
    count: Int,
    excludeIds: List<String>,
    randomSeed: String,
    requestedMood: String?,
    intent: String? = null
): DiscoveryMixRequest {
    val common = DiscoveryMixRequest(
        mode = mode,
        count = count,
        excludeIds = excludeIds,
        randomSeed = randomSeed,
        intent = intent?.trim()?.takeIf(String::isNotEmpty),
        strictIntent = true
    )
    return when (mode) {
        DiscoveryMixMode.SMART -> common.copy(
            mood = requestedMood,
            discovery = 0.42f,
            variety = 0.78f,
            energyCurve = "arc"
        )
        DiscoveryMixMode.HIDDEN_GEMS -> common.copy(
            mood = requestedMood,
            discovery = 0.82f,
            variety = 0.92f
        )
        DiscoveryMixMode.REDISCOVER -> common.copy(
            mood = requestedMood,
            discovery = 0.68f,
            variety = 0.86f
        )
        DiscoveryMixMode.ENERGY_RISE -> common.copy(
            mood = requestedMood,
            discovery = 0.48f,
            variety = 0.72f,
            energyCurve = "rise"
        )
        DiscoveryMixMode.COOLDOWN -> common.copy(
            mood = requestedMood ?: "relaxed",
            discovery = 0.34f,
            variety = 0.68f,
            energy = 0.28f,
            danceability = 0.30f,
            energyCurve = "fall"
        )
        DiscoveryMixMode.CHILLOUT -> common.copy(
            discovery = 0.44f,
            variety = 0.74f,
            energyCurve = "steady"
        )
        DiscoveryMixMode.INSTRUMENTAL -> common.copy(
            mood = requestedMood,
            voice = "instrumental",
            discovery = 0.46f,
            variety = 0.76f
        )
        DiscoveryMixMode.HARMONIC -> common.copy(
            mood = requestedMood,
            discovery = 0.22f,
            variety = 0.58f
        )
        DiscoveryMixMode.SIMILAR,
        DiscoveryMixMode.GENRE_BRIDGE -> common.copy(
            mood = requestedMood,
            discovery = 0.28f,
            variety = 0.62f
        )
    }
}

internal fun filterDiscoveryCandidatesForMode(
    songs: List<MediaItem>,
    mode: DiscoveryMixMode
): List<MediaItem> = when (mode) {
    DiscoveryMixMode.COOLDOWN -> songs.filter(MediaItem::isCredibleCooldownTrack)
    DiscoveryMixMode.CHILLOUT -> songs.filter(MediaItem::isCredibleChilloutTrack)
    DiscoveryMixMode.INSTRUMENTAL -> songs.filter(MediaItem::isCredibleInstrumentalTrack)
    DiscoveryMixMode.HARMONIC -> songs.filter(MediaItem::hasCredibleHarmonicMetadata)
    DiscoveryMixMode.HIDDEN_GEMS -> songs.filter(MediaItem::isCredibleHiddenGem)
    DiscoveryMixMode.REDISCOVER -> songs.filter(MediaItem::isCredibleRediscovery)
    else -> songs
}

internal fun discoveryServerCandidateCount(mode: DiscoveryMixMode, outputLimit: Int): Int =
    if (mode == DiscoveryMixMode.CHILLOUT) {
        maxOf(outputLimit, 200)
    } else {
        outputLimit
    }

private fun MediaItem.isCredibleChilloutTrack(): Boolean {
    val extras = mediaMetadata.extras ?: return false
    if (!extras.containsKey(DiscoveryMetadataKeys.ENERGY) ||
        !extras.containsKey(DiscoveryMetadataKeys.DANCEABILITY)
    ) return false

    return isCredibleChilloutAnalysis(
        energy = extras.getFloat(DiscoveryMetadataKeys.ENERGY),
        danceability = extras.getFloat(DiscoveryMetadataKeys.DANCEABILITY),
        bpmConfidence = extras.getFloat(DiscoveryMetadataKeys.BPM_CONFIDENCE),
        aggressive = extras.getFloat(DiscoveryMetadataKeys.MOOD_AGGRESSIVE),
		loudnessRange = extras.getFloat(DiscoveryMetadataKeys.LOUDNESS_RANGE),
		harmonicTurbulence = extras.getFloat(DiscoveryMetadataKeys.HARMONIC_TURBULENCE)
    )
}

internal fun isCredibleChilloutAnalysis(
    energy: Float,
    danceability: Float,
    bpmConfidence: Float,
    aggressive: Float,
	loudnessRange: Float,
	harmonicTurbulence: Float = 0f
): Boolean {
    val pulseInsistence = (danceability * bpmConfidence).coerceIn(0f, 1f)
    val dynamicSpace = (loudnessRange / 15f).coerceIn(0f, 1f)
    val smoothness = (1f - energy) * 0.32f +
        (1f - aggressive) * 0.28f +
        (1f - pulseInsistence) * 0.26f +
        dynamicSpace * 0.14f
    val hardPulse = danceability > 0.82f && bpmConfidence > 0.72f
	return energy <= 0.72f && aggressive <= 0.60f && harmonicTurbulence <= 0.40f &&
		!hardPulse && smoothness >= 0.58f
}

private fun MediaItem.isCredibleHiddenGem(): Boolean {
    val extras = mediaMetadata.extras ?: return false
    if (extras.getInt("playCount", Int.MAX_VALUE) > 2) return false
    return !extras.wasPlayedWithin(Duration.ofDays(14))
}

private fun MediaItem.isCredibleRediscovery(): Boolean {
    val extras = mediaMetadata.extras ?: return false
    if (!extras.getString("starred").isNullOrBlank()) return true
    if (extras.getInt("playCount", 0) <= 0) return false
    return !extras.wasPlayedWithin(Duration.ofDays(60))
}

private fun android.os.Bundle.wasPlayedWithin(duration: Duration): Boolean {
    val played = getString("lastPlayed")?.takeIf(String::isNotBlank) ?: return false
    val instant = runCatching { Instant.parse(played) }.getOrNull() ?: return false
    return instant.isAfter(Instant.now().minus(duration))
}

private fun MediaItem.isCredibleCooldownTrack(): Boolean {
	val extras = mediaMetadata.extras ?: return false
	if (!extras.containsKey(DiscoveryMetadataKeys.ENERGY) ||
		!extras.containsKey(DiscoveryMetadataKeys.DANCEABILITY)
	) return false

	val energy = extras.getFloat(DiscoveryMetadataKeys.ENERGY)
	val arousal = extras.getFloat(DiscoveryMetadataKeys.AROUSAL).normalizedAnalyzerValue()
	val danceability = extras.getFloat(DiscoveryMetadataKeys.DANCEABILITY)
	val aggressive = extras.getFloat(DiscoveryMetadataKeys.MOOD_AGGRESSIVE)
	val party = extras.getFloat(DiscoveryMetadataKeys.MOOD_PARTY)
	return isCredibleCooldownAnalysis(energy, arousal, danceability, aggressive, party)
}

internal fun isCredibleCooldownAnalysis(
	energy: Float,
	arousal: Float,
	danceability: Float,
	aggressive: Float,
	party: Float
): Boolean {
	val rhythm = danceability * 0.34f + energy * 0.22f +
		party * 0.18f + aggressive * 0.16f
	return energy <= 0.52f && arousal <= 0.58f && danceability <= 0.52f &&
		aggressive <= 0.38f && party <= 0.46f && rhythm <= 0.52f
}

private fun MediaItem.isCredibleInstrumentalTrack(): Boolean {
    val extras = mediaMetadata.extras
    val voice = extras?.getString(DiscoveryMetadataKeys.VOICE).orEmpty().normalizedDiscoveryText()
    val confidence = extras?.getFloat(DiscoveryMetadataKeys.VOICE_CONFIDENCE) ?: 0f
	return isCredibleInstrumentalClassification(voice, confidence)
}

internal fun isCredibleInstrumentalClassification(
    voice: String,
	confidence: Float,
	genre: String = ""
): Boolean {
    val normalizedVoice = voice.normalizedDiscoveryText()
	return normalizedVoice.contains("instrumental") && confidence >= 0.55f
}

private fun MediaItem.hasCredibleHarmonicMetadata(): Boolean {
    val extras = mediaMetadata.extras ?: return false
    return listOf(
        extras.getString(DiscoveryMetadataKeys.CAMELOT),
        extras.getString("camelot"),
        extras.getString("keyCamelot"),
        extras.getString(DiscoveryMetadataKeys.KEY),
        extras.getString("key")
    ).any { !it.isNullOrBlank() }
}

internal fun Float.normalizedAnalyzerValue(): Float = when {
    this in 1f..9f -> ((this - 1f) / 8f).coerceIn(0f, 1f)
    this in -1f..1f -> ((this + 1f) / 2f).coerceIn(0f, 1f)
    else -> coerceIn(0f, 1f)
}

private fun String.normalizedDiscoveryText(): String = lowercase()
    .replace("&", " and ")
    .replace(Regex("[^a-z0-9]+"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
