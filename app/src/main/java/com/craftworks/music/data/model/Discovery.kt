package com.craftworks.music.data.model

enum class DiscoveryMixMode(
    val apiValue: String,
    val title: String,
    val description: String
) {
    SMART(
        apiValue = "smart",
        title = "Smart DJ",
        description = "A good run with a few left turns"
    ),
    HIDDEN_GEMS(
        apiValue = "hidden-gems",
        title = "Hidden Gems",
        description = "The good stuff gathering dust"
    ),
    REDISCOVER(
        apiValue = "rediscover",
        title = "Rediscover",
        description = "Songs you forgot you knew by heart"
    ),
    ENERGY_RISE(
        apiValue = "energy-rise",
        title = "Energy Lift",
        description = "Starts steady, ends up somewhere loud"
    ),
    COOLDOWN(
        apiValue = "cooldown",
        title = "Wind Down",
        description = "Easy does it"
    ),
    CHILLOUT(
        apiValue = "chillout",
        title = "Chillout",
        description = "Low-key tracks with a little movement"
    ),
    INSTRUMENTAL(
        apiValue = "instrumental",
        title = "Instrumental Focus",
        description = "Fewer words, more room to think"
    ),
    SIMILAR(
        apiValue = "similar",
        title = "Radio",
        description = "More from the same neighbourhood"
    ),
    HARMONIC(
        apiValue = "harmonic",
        title = "Harmonic Radio",
        description = "Same key, similar pace"
    ),
    GENRE_BRIDGE(
        apiValue = "genre-bridge",
        title = "Music Journey",
        description = "A route from one track to another"
    )
}

data class DiscoveryMixRequest(
    val mode: DiscoveryMixMode = DiscoveryMixMode.SMART,
    val count: Int = 50,
    val seedIds: List<String> = emptyList(),
    val excludeIds: List<String> = emptyList(),
    val randomSeed: String? = null,
    val endId: String? = null,
    val mood: String? = null,
    val voice: String? = null,
    val discovery: Float? = null,
    val variety: Float? = null,
    val energy: Float? = null,
    val valence: Float? = null,
    val danceability: Float? = null,
    val energyCurve: String? = null,
    val includeSeeds: Boolean = false,
    val intent: String? = null,
    val strictIntent: Boolean = true
)

object DiscoveryMetadataKeys {
    const val MODE = "discoveryMode"
    const val SCORE = "discoveryScore"
    const val SIMILARITY = "discoverySimilarity"
    const val REASON = "discoveryReason"
    const val BPM = "discoveryBpm"
    const val BPM_CONFIDENCE = "discoveryBpmConfidence"
    const val KEY = "discoveryKey"
    const val CAMELOT = "discoveryCamelot"
	const val HARMONIC_TURBULENCE = "discoveryHarmonicTurbulence"
    const val ENERGY = "discoveryEnergy"
    const val AROUSAL = "discoveryArousal"
    const val VALENCE = "discoveryValence"
    const val DANCEABILITY = "discoveryDanceability"
    const val LOUDNESS_RANGE = "discoveryLoudnessRange"
    const val MOOD = "discoveryMood"
    const val MOOD_AGGRESSIVE = "discoveryMoodAggressive"
    const val MOOD_RELAXED = "discoveryMoodRelaxed"
    const val MOOD_PARTY = "discoveryMoodParty"
    const val GENRE = "discoveryGenre"
    const val GENRES = "discoveryGenres"
    const val VOICE = "discoveryVoice"
    const val VOICE_CONFIDENCE = "discoveryVoiceConfidence"
}
