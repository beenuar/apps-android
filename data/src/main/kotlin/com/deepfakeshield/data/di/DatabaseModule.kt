package com.deepfakeshield.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.deepfakeshield.data.dao.*
import com.deepfakeshield.data.database.DeepfakeShieldDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // Migration from v2 to v3: add indexes for frequently queried columns
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_phone_reputation_isBlocked` ON `phone_reputation`(`isBlocked`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_domain_reputation_isBlocked` ON `domain_reputation`(`isBlocked`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_feedback_alertId` ON `user_feedback`(`alertId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vault_entries_entryType` ON `vault_entries`(`entryType`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_scam_patterns_patternType` ON `scam_patterns`(`patternType`)")
        }
    }

    // Migration from v1 to v2: add new tables for enhanced features
    // CRITICAL: SQL must exactly match @Entity definitions in Entities.kt and AuditLogEntity.kt
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `community_threats` (`threatHash` TEXT NOT NULL, `threatType` TEXT NOT NULL, `severity` INTEGER NOT NULL, `reportCount` INTEGER NOT NULL, `firstSeen` INTEGER NOT NULL, `lastSeen` INTEGER NOT NULL, `geographicRegion` TEXT, `metadata` TEXT, PRIMARY KEY(`threatHash`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `behavior_profiles` (`senderId` TEXT NOT NULL, `messageCount` INTEGER NOT NULL, `callCount` INTEGER NOT NULL, `avgResponseTime` INTEGER NOT NULL, `avgMessageLength` INTEGER NOT NULL, `activeHours` TEXT NOT NULL, `typingSpeed` REAL, `suspiciousPatterns` TEXT, `trustScore` INTEGER NOT NULL, `firstContact` INTEGER NOT NULL, `lastContact` INTEGER NOT NULL, PRIMARY KEY(`senderId`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `scammer_fingerprints` (`fingerprintId` TEXT NOT NULL, `phoneNumbers` TEXT NOT NULL, `deviceSignatures` TEXT, `writingStyleHash` TEXT NOT NULL, `activeHours` TEXT NOT NULL, `targetedVictims` INTEGER NOT NULL, `campaignIds` TEXT, `confidence` REAL NOT NULL, `firstSeen` INTEGER NOT NULL, `lastSeen` INTEGER NOT NULL, PRIMARY KEY(`fingerprintId`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `pattern_weights` (`patternId` TEXT NOT NULL, `pattern` TEXT NOT NULL, `weight` REAL NOT NULL, `accuracy` REAL NOT NULL, `falsePositiveRate` REAL NOT NULL, `truePositiveCount` INTEGER NOT NULL, `falsePositiveCount` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`patternId`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `learned_patterns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `pattern` TEXT NOT NULL, `confidence` REAL NOT NULL, `occurrences` INTEGER NOT NULL, `threatType` TEXT NOT NULL, `discoveredAt` INTEGER NOT NULL, `language` TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `scam_campaigns` (`campaignId` TEXT NOT NULL, `campaignType` TEXT NOT NULL, `affectedUsers` INTEGER NOT NULL, `scammerCount` INTEGER NOT NULL, `commonPatterns` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `endDate` INTEGER, `isActive` INTEGER NOT NULL, PRIMARY KEY(`campaignId`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `threat_predictions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `threatType` TEXT NOT NULL, `probability` REAL NOT NULL, `expectedTimeframe` TEXT NOT NULL, `targetAudience` TEXT NOT NULL, `reasoning` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `isActive` INTEGER NOT NULL DEFAULT 1)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `user_risk_profile` (`id` INTEGER NOT NULL, `overallRiskScore` INTEGER NOT NULL, `age` INTEGER, `occupation` TEXT, `techSavviness` TEXT, `hasSharedPersonalInfo` INTEGER NOT NULL, `clickedSuspiciousLinks` INTEGER NOT NULL, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `audit_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `action` TEXT NOT NULL, `entityType` TEXT NOT NULL, `entityId` TEXT, `userId` TEXT NOT NULL DEFAULT 'local', `timestamp` INTEGER NOT NULL, `metadata` TEXT, `hash` TEXT)")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DeepfakeShieldDatabase {
        return Room.databaseBuilder(
            context,
            DeepfakeShieldDatabase::class.java,
            "deepfake_shield.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            // Never use destructive fallback — user safety data (alerts, quarantine records,
            // reputation scores) must never be silently wiped. If a migration is missing,
            // the app will crash on upgrade which is preferable to silent data loss.
            // Always write proper migrations for every database version bump.
            .build()
    }

    @Provides
    fun provideAlertDao(database: DeepfakeShieldDatabase): AlertDao = database.alertDao()

    @Provides
    fun providePhoneReputationDao(database: DeepfakeShieldDatabase): PhoneReputationDao =
        database.phoneReputationDao()

    @Provides
    fun provideDomainReputationDao(database: DeepfakeShieldDatabase): DomainReputationDao =
        database.domainReputationDao()

    @Provides
    fun provideUserFeedbackDao(database: DeepfakeShieldDatabase): UserFeedbackDao =
        database.userFeedbackDao()

    @Provides
    fun provideVaultDao(database: DeepfakeShieldDatabase): VaultDao = database.vaultDao()

    @Provides
    fun provideScamPatternDao(database: DeepfakeShieldDatabase): ScamPatternDao =
        database.scamPatternDao()

    @Provides
    fun provideExportHistoryDao(database: DeepfakeShieldDatabase): ExportHistoryDao =
        database.exportHistoryDao()
    
    @Provides
    fun provideCommunityThreatDao(database: DeepfakeShieldDatabase): CommunityThreatDao =
        database.communityThreatDao()
    
    @Provides
    fun provideBehaviorProfileDao(database: DeepfakeShieldDatabase): BehaviorProfileDao =
        database.behaviorProfileDao()
    
    @Provides
    fun provideScammerFingerprintDao(database: DeepfakeShieldDatabase): ScammerFingerprintDao =
        database.scammerFingerprintDao()
    
    @Provides
    fun providePatternWeightDao(database: DeepfakeShieldDatabase): PatternWeightDao =
        database.patternWeightDao()
    
    @Provides
    fun provideLearnedPatternDao(database: DeepfakeShieldDatabase): LearnedPatternDao =
        database.learnedPatternDao()
    
    @Provides
    fun provideScamCampaignDao(database: DeepfakeShieldDatabase): ScamCampaignDao =
        database.scamCampaignDao()
    
    @Provides
    fun provideThreatPredictionDao(database: DeepfakeShieldDatabase): ThreatPredictionDao =
        database.threatPredictionDao()
    
    @Provides
    fun provideUserRiskProfileDao(database: DeepfakeShieldDatabase): UserRiskProfileDao =
        database.userRiskProfileDao()

    @Provides
    fun provideAuditLogDao(database: DeepfakeShieldDatabase): AuditLogDao =
        database.auditLogDao()
}
