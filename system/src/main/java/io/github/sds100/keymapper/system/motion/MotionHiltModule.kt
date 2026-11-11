package io.github.sds100.keymapper.system.motion

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MotionHiltModule {
    @Singleton
    @Binds
    abstract fun provideMotionAdapter(impl: AndroidMotionAdapter): MotionAdapter

    @Singleton
    @Binds
    abstract fun provideMotionComboDetector(impl: MotionComboDetectorImpl): MotionComboDetector
}
