package kz.lvk.languagelearning.core.network

import java.net.HttpURLConnection
import java.net.URI

class JdkHttpClient(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) : HttpClient {
    override fun get(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "LanguageLearning-Android")

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                error("HTTP $responseCode while requesting $url")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
