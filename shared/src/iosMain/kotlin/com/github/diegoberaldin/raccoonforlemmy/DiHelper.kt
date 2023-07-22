package com.github.diegoberaldin.raccoonforlemmy

import com.github.diegoberaldin.raccoonforlemmy.core_appearance.di.coreAppearanceModule
import com.github.diegoberaldin.raccoonforlemmy.core_preferences.di.corePreferencesModule
import com.github.diegoberaldin.raccoonforlemmy.feature_inbox.inboxTabModule
import com.github.diegoberaldin.raccoonforlemmy.feature_profile.profileTabModule
import com.github.diegoberaldin.raccoonforlemmy.feature_search.searchTabModule
import com.github.diegoberaldin.raccoonforlemmy.feature_home.homeTabModule
import com.github.diegoberaldin.raccoonforlemmy.feature_settings.settingsTabModule
import com.github.diegoberaldin.raccoonforlemmy.resources.localizationModule
import org.koin.core.context.startKoin

fun initKoin() {
    startKoin {
        modules(
            coreAppearanceModule,
            corePreferencesModule,
            localizationModule,
            homeTabModule,
            inboxTabModule,
            profileTabModule,
            searchTabModule,
            settingsTabModule,
        )
    }
}