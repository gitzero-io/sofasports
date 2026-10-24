package com.sofasports.tv.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Lives as long as the process: background refreshes that outlive a screen. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {
    @Provides @Singleton @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
