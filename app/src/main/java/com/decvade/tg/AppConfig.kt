package com.decvade.tg

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class AppConfig(
    val domains: List<DomainRule>,
    val search: SearchConfig,
    val generator: GeneratorConfig,
    val scoring: ScoringConfig
)

data class DomainRule(
    val id: String,
    val enabled: Boolean,
    val urlTemplate: String,
    val responseType: String,
    val takenSignal: String,
    val retryHeader: String,
    val timeoutMs: Int,
    val unknownWhenSignalMissing: Boolean,
    val notFoundMeansAvailable: Boolean
)

data class SearchConfig(
    val minLength: Int,
    val maxLength: Int,
    val allowDigits: Boolean,
    val allowUnderscore: Boolean,
    val pauseMsBase: Long,
    val pauseMsMax: Long,
    val retryBackoffMs: Long,
    val limitWaitSecondsMax: Int,
    val dedupe: Boolean
)

data class GeneratorConfig(
    val mode: String,
    val useDictionary: Boolean,
    val dictionaryFile: String,
    val patternOrder: List<String>,
    val vowels: String,
    val consonants: String,
    val preferredStarts: List<String>,
    val preferredEnds: List<String>,
    val prefixWords: List<String>,
    val suffixWords: List<String>
)

data class ScoringConfig(
    val lengthTargetMin: Int,
    val lengthTargetMax: Int,
    val highValueCharacters: List<String>,
    val penaltyForRepeats: Int,
    val bonusForMemorability: Int,
    val bonusForBrandish: Int,
    val bonusForCipherStyle: Int,
    val minAcceptScore: Int
)

object UsernameConfigLoader {
    fun load(context: Context): AppConfig {
        val json = try {
            context.assets.open("default.dcfg").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            throw IOException("Не удалось прочитать app/src/main/assets/default.dcfg", e)
        }

        try {
            val root = JSONObject(json)
            val domains = root.getJSONArray("domains").toObjects { item ->
                DomainRule(
                    id = item.getString("id"),
                    enabled = item.getBoolean("enabled"),
                    urlTemplate = item.getString("urlTemplate"),
                    responseType = item.getString("responseType"),
                    takenSignal = item.getString("takenSignal"),
                    retryHeader = item.getString("retryHeader"),
                    timeoutMs = item.getInt("timeoutMs"),
                    unknownWhenSignalMissing = item.optBoolean("unknownWhenSignalMissing", false),
                    notFoundMeansAvailable = item.optBoolean("notFoundMeansAvailable", true)
                )
            }
            val searchJson = root.getJSONObject("search")
            val generatorJson = root.getJSONObject("generator")
            val scoringJson = root.getJSONObject("scoring")

            return AppConfig(
                domains = domains,
                search = SearchConfig(
                    minLength = searchJson.getInt("minLength"),
                    maxLength = searchJson.getInt("maxLength"),
                    allowDigits = searchJson.getBoolean("allowDigits"),
                    allowUnderscore = searchJson.getBoolean("allowUnderscore"),
                    pauseMsBase = searchJson.getLong("pauseMsBase"),
                    pauseMsMax = searchJson.getLong("pauseMsMax"),
                    retryBackoffMs = searchJson.getLong("retryBackoffMs"),
                    limitWaitSecondsMax = searchJson.getInt("limitWaitSecondsMax"),
                    dedupe = searchJson.getBoolean("dedupe")
                ),
                generator = GeneratorConfig(
                    mode = generatorJson.getString("mode"),
                    useDictionary = generatorJson.getBoolean("useDictionary"),
                    dictionaryFile = generatorJson.getString("dictionaryFile"),
                    patternOrder = generatorJson.getJSONArray("patternOrder").toStringList(),
                    vowels = generatorJson.getString("vowels"),
                    consonants = generatorJson.getString("consonants"),
                    preferredStarts = generatorJson.getJSONArray("preferredStarts").toStringList(),
                    preferredEnds = generatorJson.getJSONArray("preferredEnds").toStringList(),
                    prefixWords = generatorJson.getJSONArray("prefixWords").toStringList(),
                    suffixWords = generatorJson.getJSONArray("suffixWords").toStringList()
                ),
                scoring = ScoringConfig(
                    lengthTargetMin = scoringJson.getInt("lengthTargetMin"),
                    lengthTargetMax = scoringJson.getInt("lengthTargetMax"),
                    highValueCharacters = scoringJson.getJSONArray("highValueCharacters").toStringList(),
                    penaltyForRepeats = scoringJson.getInt("penaltyForRepeats"),
                    bonusForMemorability = scoringJson.getInt("bonusForMemorability"),
                    bonusForBrandish = scoringJson.getInt("bonusForBrandish"),
                    bonusForCipherStyle = scoringJson.getInt("bonusForCipherStyle"),
                    minAcceptScore = scoringJson.getInt("minAcceptScore")
                )
            )
        } catch (e: Exception) {
            throw IllegalStateException("Некорректная конфигурация default.dcfg: ${e.message}", e)
        }
    }

    fun loadDictionary(context: Context, config: GeneratorConfig): List<String> {
        if (!config.useDictionary) return emptyList()

        return try {
            context.assets.open(config.dictionaryFile).bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }
                    .filter { it.isNotEmpty() }
                    .toList()
            }
        } catch (e: IOException) {
            throw IOException("Не удалось прочитать словарь ${config.dictionaryFile}", e)
        }
    }

    private fun JSONArray.toStringList(): List<String> =
        (0 until length()).map { getString(it) }

    private inline fun <T> JSONArray.toObjects(map: (JSONObject) -> T): List<T> =
        (0 until length()).map { map(getJSONObject(it)) }
}
