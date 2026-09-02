package kz.lvk.languagelearning.core.update

interface UpdateRepository {
    suspend fun getManifest(url: String): UpdateManifest
}
