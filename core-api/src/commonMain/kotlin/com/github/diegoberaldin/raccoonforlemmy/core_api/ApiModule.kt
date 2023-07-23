package com.github.diegoberaldin.raccoonforlemmy.core_api

import com.github.diegoberaldin.raccoonforlemmy.core_api.provider.DefaultServiceProvider
import com.github.diegoberaldin.raccoonforlemmy.core_api.provider.ServiceProvider
import org.koin.dsl.module

val coreApiModule = module {
    single<ServiceProvider> {
        DefaultServiceProvider()
    }
    single {
        val provider: ServiceProvider = get()
        provider.postService
    }
}