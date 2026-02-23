package com.deepfakeshield.di

import com.deepfakeshield.core.notification.AlertNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AlertNotifierModule {

    @Binds
    @Singleton
    abstract fun bindAlertNotifier(impl: AlertNotifierImpl): AlertNotifier
}
