package com.github.diegoberaldin.raccoonforlemmy.unit.manageban.di

import com.github.diegoberaldin.raccoonforlemmy.unit.manageban.ManageBanMviModel
import com.github.diegoberaldin.raccoonforlemmy.unit.manageban.ManageBanViewModel
import org.koin.dsl.module

val manageBanModule =
    module {
        factory<ManageBanMviModel> {
            ManageBanViewModel(
                identityRepository = get(),
                siteRepository = get(),
                settingsRepository = get(),
                userRepository = get(),
                communityRepository = get(),
            )
        }
    }
