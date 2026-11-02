package com.decvade.tg

import java.net.HttpURLConnection
import java.net.URL

data class DomainCheckResult(
    val domain: String,
    val available: Boolean?,
    val statusCode: Int,
    val waitSeconds: Int,
    val error: String? = null
)

class DomainChecker {
    fun check(name: String, rule: DomainRule): DomainCheckResult {
        val conn = try {
            val url = URL(rule.urlTemplate.replace("{name}", name))
            (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = rule.timeoutMs
                readTimeout = rule.timeoutMs
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Safari/537.36"
                )
            }
        } catch (e: Exception) {
            return DomainCheckResult(rule.id, null, -1, 0, e.message ?: "Не удалось создать запрос")
        }

        return try {
            val code = conn.responseCode
            if (code == 429) {
                val wait = conn.getHeaderField(rule.retryHeader)?.toIntOrNull() ?: 0
                return DomainCheckResult(rule.id, null, code, wait)
            }

            if (code == 404) {
                return DomainCheckResult(
                    domain = rule.id,
                    available = if (rule.notFoundMeansAvailable) true else null,
                    statusCode = code,
                    waitSeconds = 0
                )
            }

            if (code !in 200..299) {
                return DomainCheckResult(rule.id, null, code, 0, "HTTP $code")
            }

            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val taken = rule.takenSignal.isNotBlank() &&
                html.contains(rule.takenSignal, ignoreCase = true)
            return DomainCheckResult(
                domain = rule.id,
                available = when {
                    taken -> false
                    rule.unknownWhenSignalMissing -> null
                    else -> true
                },
                statusCode = code,
                waitSeconds = 0
            )
        } catch (e: Exception) {
            DomainCheckResult(rule.id, null, -1, 0, e.message ?: "Ошибка сети")
        } finally {
            conn.disconnect()
        }
    }
}
