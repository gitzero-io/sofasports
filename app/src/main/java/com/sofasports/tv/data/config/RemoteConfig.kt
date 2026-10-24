package com.sofasports.tv.data.config

import com.sofasports.tv.data.SofaJson
import com.sofasports.tv.data.provider.Endpoints
import kotlinx.serialization.Serializable

/**
 * Remote config (config.json). Every field has a safe default, so a missing, partial or broken
 * file leaves the app working exactly as built. See /release-assets/config.json for a sample.
 */
@Serializable
data class RemoteConfig(
    val schemaVersion: Int = 1,
    val killSwitch: KillSwitch = KillSwitch(),
    val pollIntervalLiveSec: Int = 30,
    val pollIntervalIdleSec: Int = 300,
    val closeGame: CloseGameRule = CloseGameRule(),
    val features: Features = Features(),
    val endpoints: EndpointConfig = EndpointConfig(),
    /** Optional one-line banner on Home (e.g. "ESPN data delayed today"). */
    val announcement: String? = null,
) {
    /** Clamp anything that could hurt the device or the APIs if a bad value is pushed. */
    fun sanitized(): RemoteConfig = copy(
        pollIntervalLiveSec = pollIntervalLiveSec.coerceIn(MIN_POLL_SEC, 600),
        pollIntervalIdleSec = pollIntervalIdleSec.coerceIn(60, 3600),
        closeGame = closeGame.copy(
            maxPointDiff = closeGame.maxPointDiff.coerceIn(0, 21),
            minPeriod = closeGame.minPeriod.coerceIn(1, 5),
        ),
        endpoints = endpoints.copy(
            espnNflBase = endpoints.espnNflBase.takeIf { it.startsWith("https://") } ?: Endpoints.ESPN_NFL_BASE,
            sleeperBase = endpoints.sleeperBase.takeIf { it.startsWith("https://") } ?: Endpoints.SLEEPER_BASE,
        ),
        announcement = announcement?.takeIf { it.isNotBlank() }?.take(140),
    )

    companion object {
        const val MIN_POLL_SEC = 15
        val DEFAULT = RemoteConfig()

        /** Never throws: anything unparseable yields null so the caller keeps what it had. */
        fun parseOrNull(json: String): RemoteConfig? =
            runCatching { SofaJson.decodeFromString(serializer(), json).sanitized() }.getOrNull()
    }
}

@Serializable
data class KillSwitch(
    val enabled: Boolean = false,
    val message: String = "Sofa Sports is taking a short break. Please check back soon.",
    /** Only versions at or below this are killed; 0 = all versions. */
    val maxVersionCode: Int = 0,
) {
    fun appliesTo(versionCode: Int) = enabled && (maxVersionCode == 0 || versionCode <= maxVersionCode)
}

/** A game is "close" when live, in period >= [minPeriod] and within [maxPointDiff]. Overtime is always close. */
@Serializable
data class CloseGameRule(val maxPointDiff: Int = 8, val minPeriod: Int = 4)

@Serializable
data class Features(
    val overlay: Boolean = true,
    val fantasy: Boolean = true,
    val gameCenter: Boolean = true,
    val winProbability: Boolean = true,
    val updateCheck: Boolean = true,
    val usageEvents: Boolean = true,
    /** DraftKings lines on cards/overlay. Remote off-switch for betting content. */
    val odds: Boolean = true,
    /** Manual PrizePicks / DraftKings pick tracker. */
    val picks: Boolean = true,
)

@Serializable
data class EndpointConfig(
    val espnNflBase: String = Endpoints.ESPN_NFL_BASE,
    val sleeperBase: String = Endpoints.SLEEPER_BASE,
)
