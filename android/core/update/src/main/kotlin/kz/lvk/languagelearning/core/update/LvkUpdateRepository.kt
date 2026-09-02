package kz.lvk.languagelearning.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kz.lvk.languagelearning.core.network.HttpClient

class LvkUpdateRepository(
    private val httpClient: HttpClient,
    private val parser: UpdateManifestParser = UpdateManifestParser(),
) : UpdateRepository {
    override suspend fun getManifest(url: String): UpdateManifest = withContext(Dispatchers.IO) {
        parser.parse(httpClient.get(url))
    }
}
