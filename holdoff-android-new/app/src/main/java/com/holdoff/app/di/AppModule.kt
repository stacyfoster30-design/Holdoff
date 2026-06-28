package com.holdoff.app.di

import android.content.Context
import com.holdoff.app.api.AiEngine
import com.holdoff.app.api.HoldOffCloudApi
import com.holdoff.app.util.PreferencesManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun providePreferencesManager(@ApplicationContext context: Context): PreferencesManager {
        return PreferencesManager(context)
    }

    @Provides
    @Singleton
    fun provideCloudApi(): HoldOffCloudApi {
        return HoldOffCloudApi()
    }

    @Provides
    @Singleton
    fun provideAiEngine(
        @ApplicationContext context: Context,
        cloudApi: HoldOffCloudApi,
        preferencesManager: PreferencesManager
    ): AiEngine {
        return AiEngine(context, cloudApi, preferencesManager)
    }
}
