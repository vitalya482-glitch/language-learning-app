package kz.lvk.languagelearning.app

import android.content.Context
import kz.lvk.languagelearning.core.ai.LanguageModelEngine
import kz.lvk.languagelearning.core.ai.nativeengine.NativeLanguageModelEngine
import kz.lvk.languagelearning.core.models.LocalModelManager
import kz.lvk.languagelearning.core.network.JdkHttpClient
import kz.lvk.languagelearning.core.settings.SettingsRepository
import kz.lvk.languagelearning.core.settings.SharedPreferencesSettingsRepository
import kz.lvk.languagelearning.core.update.LvkUpdateRepository
import kz.lvk.languagelearning.core.update.UpdateManager

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val httpClient = JdkHttpClient()
    private val updateRepository = LvkUpdateRepository(httpClient)

    val settingsRepository: SettingsRepository = SharedPreferencesSettingsRepository(appContext)
    val localModelManager: LocalModelManager = LocalModelManager(appContext)

    val languageModelEngine: LanguageModelEngine by lazy {
        NativeLanguageModelEngine()
    }

    val updateManager = UpdateManager(
        context = appContext,
        repository = updateRepository,
        manifestUrl = BuildConfig.UPDATE_MANIFEST_URL,
        currentVersionName = BuildConfig.VERSION_NAME,
        currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
        currentPackageName = BuildConfig.APPLICATION_ID,
    )
}
