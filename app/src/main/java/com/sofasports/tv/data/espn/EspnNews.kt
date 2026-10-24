package com.sofasports.tv.data.espn

import com.sofasports.tv.data.SofaJson
import com.sofasports.tv.data.fixture.FixtureSource
import com.sofasports.tv.data.provider.ApiBases
import com.sofasports.tv.data.provider.DataModeSource
import com.sofasports.tv.domain.NewsItem
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Serializable private data class NewsDto(val articles: List<ArticleDto> = emptyList())

@Serializable
private data class ArticleDto(
    val id: JsonElement? = null,
    val headline: String? = null,
    val published: String? = null,
    val categories: List<CategoryDto> = emptyList(),
)

@Serializable
private data class CategoryDto(
    val type: String? = null,
    val description: String? = null,
    val athleteId: JsonElement? = null,
    val team: TeamRefDto? = null,
)

@Serializable private data class TeamRefDto(val abbreviation: String? = null)

/** ESPN NFL news feed → headlines. Headlines only: article bodies are never fetched or shown. */
object EspnNewsParser {
    fun parse(json: String): List<NewsItem> =
        SofaJson.decodeFromString(NewsDto.serializer(), json).articles.mapNotNull { a ->
            val headline = a.headline?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val athletes = a.categories.filter { it.type == "athlete" }
            NewsItem(
                id = (a.id as? JsonPrimitive)?.contentOrNull ?: headline.hashCode().toString(),
                headline = headline,
                publishedMs = EspnParser.parseDate(a.published),
                teamAbbrs = a.categories.filter { it.type == "team" }.mapNotNull { it.team?.abbreviation }.toSet(),
                athleteIds = athletes.mapNotNull { (it.athleteId as? JsonPrimitive)?.contentOrNull }.toSet(),
                athleteNames = athletes.mapNotNull { it.description }.toSet(),
            )
        }
}

interface NewsProvider {
    suspend fun headlines(): List<NewsItem>
}

@Singleton
class EspnNewsProvider @Inject constructor(private val http: HttpClient, private val bases: ApiBases) : NewsProvider {
    override suspend fun headlines(): List<NewsItem> {
        val res = http.get("${bases.espnNflBase}/news?limit=25")
        if (!res.status.isSuccess()) error("ESPN news HTTP ${res.status.value}")
        return EspnNewsParser.parse(res.bodyAsText())
    }
}

@Singleton
class FixtureNewsProvider @Inject constructor(private val source: FixtureSource) : NewsProvider {
    override suspend fun headlines() = source.read("espn/news.json")?.let(EspnNewsParser::parse).orEmpty()
}

@Singleton
class SwitchingNewsProvider @Inject constructor(
    private val mode: DataModeSource,
    private val live: EspnNewsProvider,
    private val fixture: FixtureNewsProvider,
) : NewsProvider {
    override suspend fun headlines() = (if (mode.useFixtures()) fixture else live).headlines()
}
