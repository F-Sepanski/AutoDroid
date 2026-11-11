package io.github.sds100.keymapper.system.gemini

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class GeminiHiltModule {

    @Binds
    abstract fun bindGeminiAdapter(adapter: AndroidGeminiAdapter): GeminiAdapter
}
