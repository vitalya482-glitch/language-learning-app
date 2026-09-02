package kz.lvk.languagelearning.app

import android.app.Application

class LanguageLearningApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
