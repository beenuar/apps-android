package com.deepfakeshield.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Previously provided API keys for cloud services (VirusTotal, Google Safe Browsing).
 * All cloud API dependencies have been removed — the app operates fully on-device.
 * This module is retained as an empty placeholder for Hilt graph stability.
 */
@Module
@InstallIn(SingletonComponent::class)
object ApiKeysModule
