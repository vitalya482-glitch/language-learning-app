package kz.lvk.languagelearning.core.network

interface HttpClient {
    fun get(url: String): String
}
