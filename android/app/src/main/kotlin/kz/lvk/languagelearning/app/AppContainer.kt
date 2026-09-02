package kz.lvk.languagelearning.app

import android.content.Context
import kz.lvk.languagelearning.core.network.JdkHttpClient
import kz.lvk.languagelearning.core.update.LvkUpdateRepository
import kz.lvk.languagelearning.core.update.UpdateManager

class AppContainer(context: Context) {
    private val httpClient = JdkHttpClient()
    private val updateRepository = LvkUpdateRepository(httpClient)

    val updateManager = UpdateManager(
        context = context.applicationContext,
        repository = updateRepository,
        manifestUrl = BuildConfig.UPDATE_MANIFEST_URL,
        currentVersionName = BuildConfig.VERSION_NAME,
        currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
        currentPackageName = BuildConfig.APPLICATION_ID,
    )
}
