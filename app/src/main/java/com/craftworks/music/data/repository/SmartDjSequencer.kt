package com.craftworks.music.data.repository

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.craftworks.music.data.model.DiscoveryMetadataKeys
import com.craftworks.music.data.model.DiscoveryMixMode
import com.craftworks.music.data.model.MediaCategory
import kotlin.math.abs

internal const val DISCOVERY_CANDIDATE_LIMIT = 200
internal const val DISCOVERY_OUTPUT_LIMIT = 50
internal const val RECENT_ARTIST_WINDOW = 3
internal const val RECENT_ALBUM_WINDOW = 2

internal data class SmartDjCandidate(
    val key: String,
    val genre: String? = null,
    val artistKey: String = "",
    val albumKey: String = "",
    val year: Int? = null,
    val bpm: Int? = null,
    val energy: Float? = null,
    val camelot: String? = null,
    val isPlayable: Boolean = true,
    val isAudiobook: Boolean = false,
    val item: MediaItem? = null
)

internal object SmartDjSequencer {
    fun sequence(
        candidates: List<MediaItem>,
        mode: DiscoveryMixMode = DiscoveryMixMode.SMART,
        outputLimit: Int = DISCOVERY_OUTPUT_LIMIT,
        candidateLimit: Int = DISCOVERY_CANDIDATE_LIMIT
    ): List<MediaItem> =
        sequenceCandidates(
            candidates.map { it.toSmartDjCandidate() },
            mode = mode,
            outputLimit = outputLimit,
            candidateLimit = candidateLimit
        ).mapNotNull { it.item }

    internal fun sequenceCandidates(
        candidates: List<SmartDjCandidate>,
        mode: DiscoveryMixMode = DiscoveryMixMode.SMART,
        outputLimit: Int = DISCOVERY_OUTPUT_LIMIT,
        candidateLimit: Int = DISCOVERY_CANDIDATE_LIMIT
    ): List<SmartDjCandidate> {
        val boundedOutputLimit = outputLimit.coerceIn(0, DISCOVERY_OUTPUT_LIMIT)
        val boundedCandidateLimit = candidateLimit.coerceIn(0, DISCOVERY_CANDIDATE_LIMIT)
        if (boundedOutputLimit == 0 || boundedCandidateLimit == 0) return emptyList()

        val seenKeys = LinkedHashSet<String>()
        val profiles = candidates
            .take(boundedCandidateLimit)
            .mapIndexedNotNull { index, item ->
                item.takeIf { it.isPlayable && !it.isAudiobook }
                    ?.let { SongProfile.from(it, index) }
            }
            .filter { seenKeys.add(it.key) }

        if (profiles.isEmpty()) return emptyList()

        val lane = chooseLane(profiles)
        val pool = choosePool(profiles, lane, boundedOutputLimit)
        if (pool.isEmpty()) return emptyList()

        val remaining = pool.toMutableList()
        val output = mutableListOf<SongProfile>()
        var current = chooseSeed(remaining, lane, mode)

        output += current
        remaining.remove(current)

        val recentArtists = ArrayDeque<String>()
        val recentAlbums = ArrayDeque<String>()
        rememberRecent(current, recentArtists, recentAlbums)

        while (remaining.isNotEmpty() && output.size < boundedOutputLimit) {
            val artistSpaced = remaining.filter { profile ->
                profile.artistKey.isBlank() || profile.artistKey !in recentArtists
            }.takeIf { it.isNotEmpty() } ?: remaining

            val next = artistSpaced
                .sortedWith(
                    compareByDescending<SongProfile> {
                        transitionScore(
                            current = current,
                            candidate = it,
                            recentArtists = recentArtists,
                            recentAlbums = recentAlbums,
                            mode = mode,
                            position = output.size,
                            outputLimit = boundedOutputLimit
                        )
                    }.thenBy { it.index }
                )
                .first()

            output += next
            remaining.remove(next)
            current = next
            rememberRecent(current, recentArtists, recentAlbums)
        }

        return output.map { it.candidate }
    }

    private fun choosePool(
        profiles: List<SongProfile>,
        lane: GenreFamily?,
        outputLimit: Int
    ): List<SongProfile> {
        if (lane == null) return profiles

        val taggedCount = profiles.count { it.families.isNotEmpty() }
        if (taggedCount == 0) return profiles

        val preferred = profiles.filter { profile ->
            profile.families.isEmpty() || profile.families.any { lane.isCompatibleWith(it) }
        }
        val preferredTaggedCount = preferred.count { it.families.isNotEmpty() }
        val usefulTaggedCount = minOf(2, outputLimit, taggedCount).coerceAtLeast(1)

        return if (preferredTaggedCount >= usefulTaggedCount) preferred else profiles
    }

    private fun chooseLane(profiles: List<SongProfile>): GenreFamily? {
        val families = profiles.flatMap { it.families }.distinct()
        if (families.isEmpty()) return null

        val firstExactIndex = families.associateWith { family ->
            profiles.firstOrNull { family in it.families }?.index ?: Int.MAX_VALUE
        }

        return families.sortedWith(
            compareByDescending<GenreFamily> { family ->
                profiles.count { profile ->
                    profile.families.any { family.isCompatibleWith(it) }
                }
            }.thenBy { firstExactIndex[it] ?: Int.MAX_VALUE }
                .thenBy { it.name }
        ).first()
    }

    private fun chooseSeed(
        pool: List<SongProfile>,
        lane: GenreFamily?,
        mode: DiscoveryMixMode
    ): SongProfile = when (mode) {
        DiscoveryMixMode.ENERGY_RISE -> pool.sortedWith(
            compareBy<SongProfile> { it.resolvedEnergy() }
                .thenByDescending { seedLaneRank(it, lane) }
                .thenBy { it.index }
        ).first()
        DiscoveryMixMode.COOLDOWN,
        DiscoveryMixMode.CHILLOUT -> pool.sortedWith(
            compareByDescending<SongProfile> { it.resolvedEnergy() }
                .thenByDescending { seedLaneRank(it, lane) }
                .thenBy { it.index }
        ).first()
        else ->
        pool.sortedWith(
            compareByDescending<SongProfile> { profile -> seedLaneRank(profile, lane) }
                .thenBy { it.energy ?: 2 }
                .thenByDescending { profile ->
                    seedSupport(profile, pool, lane)
                }.thenBy { it.index }
        ).first()
    }

    private fun seedLaneRank(profile: SongProfile, lane: GenreFamily?): Int =
        when {
            lane == null && profile.families.isNotEmpty() -> 2
            lane == null -> 1
            profile.families.any { lane.isCompatibleWith(it) } -> 2
            profile.families.isEmpty() -> 1
            else -> 0
        }

    private fun seedSupport(
        profile: SongProfile,
        pool: List<SongProfile>,
        lane: GenreFamily?
    ): Int {
        val laneBonus = when {
            lane == null -> 0
            lane in profile.families -> 4
            profile.families.any { lane.isCompatibleWith(it) } -> 2
            profile.families.isEmpty() -> 1
            else -> 0
        }

        val transitionSupport = pool.count { other ->
            other !== profile && genreCompatibility(profile, other) >= COMPATIBLE_FAMILY
        }

        return laneBonus + transitionSupport
    }

    private fun transitionScore(
        current: SongProfile,
        candidate: SongProfile,
        recentArtists: ArrayDeque<String>,
        recentAlbums: ArrayDeque<String>,
        mode: DiscoveryMixMode,
        position: Int,
        outputLimit: Int
    ): Int {
        var score = genreCompatibility(current, candidate) * 10_000

        score += energyTransitionScore(current.energy, candidate.energy) * 250
        score += bpmTransitionScore(current.bpm, candidate.bpm) * 10
        score += yearTransitionScore(current.year, candidate.year)

        when (mode) {
            DiscoveryMixMode.ENERGY_RISE,
            DiscoveryMixMode.COOLDOWN,
            DiscoveryMixMode.CHILLOUT -> {
                val desired = desiredEnergy(mode, position, outputLimit)
                score += (4f - abs(candidate.resolvedEnergy() - desired))
                    .coerceAtLeast(0f)
                    .times(2_600)
                    .toInt()
            }
            DiscoveryMixMode.HARMONIC -> {
                score += harmonicCompatibility(current.camelot, candidate.camelot) * 3_200
            }
            else -> Unit
        }

        if (candidate.artistKey.isNotBlank() && candidate.artistKey in recentArtists) {
            score -= 1_500
        }

        if (candidate.albumKey.isNotBlank()) {
            when {
                candidate.albumKey == current.albumKey -> score -= 700
                candidate.albumKey in recentAlbums -> score -= 250
            }
        }

        return score
    }

    private fun genreCompatibility(first: SongProfile, second: SongProfile): Int {
        if (first.genreTokens.isNotEmpty() && first.genreTokens.any { it in second.genreTokens }) {
            return SHARED_GENRE
        }

        if (first.families.isNotEmpty() && first.families.any { it in second.families }) {
            return SAME_FAMILY
        }

        if (
            first.families.isNotEmpty() &&
            second.families.isNotEmpty() &&
            first.families.any { firstFamily ->
                second.families.any { secondFamily -> firstFamily.isCompatibleWith(secondFamily) }
            }
        ) {
            return COMPATIBLE_FAMILY
        }

        if (first.families.isEmpty() || second.families.isEmpty()) {
            return UNKNOWN_FALLBACK
        }

        return UNRELATED_FAMILY
    }

    private fun energyTransitionScore(first: Int?, second: Int?): Int {
        if (first == null || second == null) return 0

        val delta = abs(first - second)
        val smoothness = when (delta) {
            0 -> 8
            1 -> 6
            2 -> -8
            else -> -14
        }

        val progression = if (second >= first && delta <= 1) 3 else 0
        return smoothness + progression
    }

    private fun bpmTransitionScore(first: Int?, second: Int?): Int {
        if (first == null || second == null) return 0

        val delta = abs(first - second)
        return when {
            delta <= 5 -> 30
            delta <= 15 -> 20
            delta <= 30 -> 8
            else -> -5
        }
    }

    private fun yearTransitionScore(first: Int?, second: Int?): Int {
        if (first == null || second == null || first <= 0 || second <= 0) return 0
        return (10 - (abs(first - second) / 2)).coerceAtLeast(0)
    }

    private fun rememberRecent(
        profile: SongProfile,
        recentArtists: ArrayDeque<String>,
        recentAlbums: ArrayDeque<String>
    ) {
        if (profile.artistKey.isNotBlank()) {
            recentArtists.addLast(profile.artistKey)
            while (recentArtists.size > RECENT_ARTIST_WINDOW) recentArtists.removeFirst()
        }

        if (profile.albumKey.isNotBlank()) {
            recentAlbums.addLast(profile.albumKey)
            while (recentAlbums.size > RECENT_ALBUM_WINDOW) recentAlbums.removeFirst()
        }
    }

    private fun MediaItem.toSmartDjCandidate(): SmartDjCandidate {
        val metadata = mediaMetadata
        val extras = metadata.extras

        return SmartDjCandidate(
            key = stableSongKey(this),
            genre = metadata.genre?.toString(),
            artistKey = stableLowerKey(
                extras?.getString("artistId"),
                metadata.artist?.toString()
            ),
            albumKey = stableLowerKey(
                extras?.getString("albumId"),
                metadata.albumTitle?.toString()
            ),
            year = metadata.recordingYear ?: metadata.releaseYear,
            bpm = extras?.readCredibleBpm(),
            energy = extras?.readDiscoveryEnergy(),
            camelot = extras?.readCamelot(),
            isPlayable = metadata.isPlayable != false,
            isAudiobook = isAudiobook(metadata, extras),
            item = this
        )
    }

    private fun isAudiobook(metadata: MediaMetadata, extras: Bundle?): Boolean {
        if (
            metadata.mediaType == MediaMetadata.MEDIA_TYPE_AUDIO_BOOK ||
            metadata.mediaType == MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER
        ) {
            return true
        }

        val category = extras?.getString("mediaCategory")
        val type = extras?.getString("type")
        val format = extras?.getString("format")
        val path = extras?.getString("path")

        if (category.equals(MediaCategory.AUDIOBOOK, ignoreCase = true)) return true
        if (type.equals(MediaCategory.AUDIOBOOK, ignoreCase = true)) return true

        return MediaCategory.resolve(explicit = category, path = path, format = format) == MediaCategory.AUDIOBOOK
    }

    private data class SongProfile(
        val candidate: SmartDjCandidate,
        val index: Int,
        val key: String,
        val artistKey: String,
        val albumKey: String,
        val year: Int?,
        val genreTokens: Set<String>,
        val families: Set<GenreFamily>,
        val energy: Int?,
        val bpm: Int?,
        val analyzerEnergy: Float?,
        val camelot: String?
    ) {
        companion object {
            fun from(candidate: SmartDjCandidate, index: Int): SongProfile {
                val genreTokens = normalizeGenreTokens(candidate.genre)
                val families = genreTokens.mapNotNull(::familyForToken).toSet()

                return SongProfile(
                    candidate = candidate,
                    index = index,
                    key = candidate.key,
                    artistKey = stableLowerKey(candidate.artistKey, null),
                    albumKey = stableLowerKey(candidate.albumKey, null),
                    year = candidate.year,
                    genreTokens = genreTokens,
                    families = families,
                    energy = genreTokens.mapNotNull(::energyForToken).maxOrNull(),
                    bpm = candidate.bpm?.takeIf { it in 40..220 },
                    analyzerEnergy = candidate.energy?.takeIf { it in 0f..1f },
                    camelot = candidate.camelot?.trim()?.uppercase()?.takeIf(String::isNotBlank)
                )
            }
        }

        fun resolvedEnergy(): Float = analyzerEnergy?.times(4f) ?: energy?.toFloat() ?: 2f
    }

    private enum class GenreFamily {
        AMBIENT,
        BASS,
        CLASSICAL,
        COUNTRY,
        ELECTRONIC,
        FOLK,
        HIP_HOP,
        JAZZ,
        LATIN,
        METAL,
        POP,
        REGGAE,
        ROCK,
        SOUL,
        WORLD
    }

    private fun GenreFamily.isCompatibleWith(other: GenreFamily): Boolean =
        this == other ||
            other in compatibleFamilies[this].orEmpty() ||
            this in compatibleFamilies[other].orEmpty()

    private val compatibleFamilies = mapOf(
        GenreFamily.AMBIENT to setOf(
            GenreFamily.CLASSICAL,
            GenreFamily.ELECTRONIC,
            GenreFamily.FOLK,
            GenreFamily.JAZZ
        ),
        GenreFamily.BASS to setOf(
            GenreFamily.ELECTRONIC,
            GenreFamily.HIP_HOP,
            GenreFamily.POP
        ),
        GenreFamily.CLASSICAL to setOf(
            GenreFamily.AMBIENT,
            GenreFamily.FOLK,
            GenreFamily.JAZZ
        ),
        GenreFamily.COUNTRY to setOf(
            GenreFamily.FOLK,
            GenreFamily.POP,
            GenreFamily.ROCK
        ),
        GenreFamily.ELECTRONIC to setOf(
            GenreFamily.AMBIENT,
            GenreFamily.BASS,
            GenreFamily.HIP_HOP,
            GenreFamily.POP,
            GenreFamily.SOUL
        ),
        GenreFamily.FOLK to setOf(
            GenreFamily.AMBIENT,
            GenreFamily.CLASSICAL,
            GenreFamily.COUNTRY,
            GenreFamily.POP,
            GenreFamily.ROCK
        ),
        GenreFamily.HIP_HOP to setOf(
            GenreFamily.BASS,
            GenreFamily.ELECTRONIC,
            GenreFamily.POP,
            GenreFamily.SOUL
        ),
        GenreFamily.JAZZ to setOf(
            GenreFamily.AMBIENT,
            GenreFamily.CLASSICAL,
            GenreFamily.SOUL,
            GenreFamily.WORLD
        ),
        GenreFamily.LATIN to setOf(
            GenreFamily.POP,
            GenreFamily.SOUL,
            GenreFamily.WORLD
        ),
        GenreFamily.METAL to setOf(
            GenreFamily.ROCK
        ),
        GenreFamily.POP to setOf(
            GenreFamily.BASS,
            GenreFamily.COUNTRY,
            GenreFamily.ELECTRONIC,
            GenreFamily.FOLK,
            GenreFamily.HIP_HOP,
            GenreFamily.LATIN,
            GenreFamily.ROCK,
            GenreFamily.SOUL
        ),
        GenreFamily.REGGAE to setOf(
            GenreFamily.SOUL,
            GenreFamily.WORLD
        ),
        GenreFamily.ROCK to setOf(
            GenreFamily.COUNTRY,
            GenreFamily.FOLK,
            GenreFamily.METAL,
            GenreFamily.POP,
            GenreFamily.SOUL
        ),
        GenreFamily.SOUL to setOf(
            GenreFamily.ELECTRONIC,
            GenreFamily.HIP_HOP,
            GenreFamily.JAZZ,
            GenreFamily.LATIN,
            GenreFamily.POP,
            GenreFamily.REGGAE,
            GenreFamily.ROCK
        ),
        GenreFamily.WORLD to setOf(
            GenreFamily.JAZZ,
            GenreFamily.LATIN,
            GenreFamily.REGGAE
        )
    )

    private fun normalizeGenreTokens(rawGenre: String?): Set<String> =
        rawGenre.orEmpty()
            .split(Regex("[,;/|]+"))
            .mapNotNull { rawToken ->
                rawToken.lowercase()
                    .replace("&", " ")
                    .replace(Regex("[^a-z0-9+ ]+"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .let(::canonicalGenreToken)
                    .takeIf { it.isNotBlank() }
            }
            .toCollection(LinkedHashSet())

    private fun canonicalGenreToken(token: String): String =
        when (token) {
            "dnb", "d n b", "drum n bass", "drum bass" -> "drum and bass"
            "r b", "rhythm blues", "rhythm and blues" -> "rnb"
            "hiphop" -> "hip hop"
            "edm" -> "electronic"
            else -> token
        }

    private fun familyForToken(token: String): GenreFamily? =
        when {
            token.containsAny("dubstep", "drum and bass", "bass music", "brostep", "breakbeat") ->
                GenreFamily.BASS
            token == "bass" || token.endsWith(" bass") -> GenreFamily.BASS
            token.containsAny("metal", "hardcore", "grindcore", "deathcore") -> GenreFamily.METAL
            token.containsAny(
                "classical",
                "symphony",
                "symphonic",
                "orchestral",
                "opera",
                "baroque",
                "chamber",
                "concerto"
            ) -> GenreFamily.CLASSICAL
            token.containsAny("ambient", "downtempo", "chillout", "chillwave", "new age", "lounge") ->
                GenreFamily.AMBIENT
            token.containsAny(
                "electronic",
                "electronica",
                "house",
                "techno",
                "trance",
                "dance",
                "disco",
                "synthpop",
                "idm",
                "garage"
            ) -> GenreFamily.ELECTRONIC
            token.containsAny("hip hop", "rap", "trap", "grime") -> GenreFamily.HIP_HOP
            token.containsAny("jazz", "bebop", "swing") -> GenreFamily.JAZZ
            token.containsAny("punk", "rock", "alternative", "indie", "grunge", "shoegaze") ->
                GenreFamily.ROCK
            token.containsAny("pop", "top 40") -> GenreFamily.POP
            token.containsAny("folk", "singer songwriter", "acoustic", "bluegrass") -> GenreFamily.FOLK
            token.containsAny("country", "americana") -> GenreFamily.COUNTRY
            token.containsAny("soul", "funk", "rnb", "motown", "blues") -> GenreFamily.SOUL
            token.containsAny("latin", "salsa", "bossa", "reggaeton", "bachata", "cumbia") ->
                GenreFamily.LATIN
            token.containsAny("reggae", "dub", "ska") -> GenreFamily.REGGAE
            token.containsAny("world", "afrobeat", "flamenco", "klezmer") -> GenreFamily.WORLD
            else -> null
        }

    private fun energyForToken(token: String): Int? =
        when {
            token.containsAny("ambient", "downtempo", "chillout", "new age", "lounge") -> 0
            token.containsAny("classical", "chamber", "folk", "acoustic", "jazz") -> 1
            token.containsAny("pop", "soul", "funk", "reggae", "latin", "hip hop", "electronic") -> 2
            token.containsAny("house", "techno", "trance", "dance", "disco", "garage") -> 3
            token.containsAny("dubstep", "drum and bass", "bass", "metal", "hardcore", "grime") -> 4
            else -> null
        }

    private fun stableSongKey(item: MediaItem): String {
        val navidromeId = item.mediaMetadata.extras
            ?.getString("navidromeID")
            ?.takeIf { it.isNotBlank() }

        return navidromeId ?: item.mediaId
    }

    private fun stableLowerKey(primary: String?, fallback: String?): String =
        (primary?.takeIf { it.isNotBlank() } ?: fallback.orEmpty())
            .trim()
            .lowercase()

    @Suppress("DEPRECATION")
    private fun Bundle.readCredibleBpm(): Int? {
        val raw = when {
            containsKey("bpm") -> get("bpm")
            containsKey("BPM") -> get("BPM")
            else -> null
        }

        val parsed = when (raw) {
            is Int -> raw
            is Number -> raw.toInt()
            is String -> raw.toIntOrNull()
            else -> null
        }

        return parsed?.takeIf { it in 40..220 }
    }

    private fun Bundle.readDiscoveryEnergy(): Float? = when {
        containsKey(DiscoveryMetadataKeys.ENERGY) -> getFloat(DiscoveryMetadataKeys.ENERGY)
        else -> null
    }?.takeIf { it in 0f..1f }

    private fun Bundle.readCamelot(): String? = listOf(
        getString(DiscoveryMetadataKeys.CAMELOT),
        getString("camelot"),
        getString("keyCamelot")
    ).firstOrNull { !it.isNullOrBlank() }

    private fun desiredEnergy(mode: DiscoveryMixMode, position: Int, count: Int): Float {
        val progress = (position.toFloat() / (count - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        return when (mode) {
            DiscoveryMixMode.ENERGY_RISE -> 0.4f + 3.4f * progress
            DiscoveryMixMode.COOLDOWN -> 2.2f - 2.0f * progress
            DiscoveryMixMode.CHILLOUT -> 1.6f
            else -> 2f
        }
    }

    private fun harmonicCompatibility(first: String?, second: String?): Int {
        val left = parseCamelot(first) ?: return 0
        val right = parseCamelot(second) ?: return 0
        if (left == right) return 4
        if (left.first == right.first && left.second != right.second) return 3
        val wheelDistance = minOf(
            (left.first - right.first).mod(12),
            (right.first - left.first).mod(12)
        )
        return when {
            left.second == right.second && wheelDistance == 1 -> 3
            left.second == right.second && wheelDistance == 2 -> 1
            else -> 0
        }
    }

    private fun parseCamelot(value: String?): Pair<Int, Char>? {
        val normalized = value?.trim()?.uppercase() ?: return null
        val number = normalized.dropLast(1).toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val letter = normalized.lastOrNull()?.takeIf { it == 'A' || it == 'B' } ?: return null
        return number to letter
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any(::contains)

    private const val UNRELATED_FAMILY = 0
    private const val UNKNOWN_FALLBACK = 1
    private const val COMPATIBLE_FAMILY = 2
    private const val SAME_FAMILY = 3
    private const val SHARED_GENRE = 4
}
