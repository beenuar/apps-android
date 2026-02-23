package com.deepfakeshield.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.deepfakeshield.data.dao.*
import com.deepfakeshield.data.entity.*

@Database(
    entities = [
        AlertEntity::class,
        PhoneReputationEntity::class,
        DomainReputationEntity::class,
        UserFeedbackEntity::class,
        VaultEntryEntity::class,
        ScamPatternEntity::class,
        ExportHistoryEntity::class,
        CommunityThreatEntity::class,
        BehaviorProfileEntity::class,
        ScammerFingerprintEntity::class,
        PatternWeightEntity::class,
        LearnedPatternEntity::class,
        ScamCampaignEntity::class,
        ThreatPredictionEntity::class,
        UserRiskProfileEntity::class,
        AuditLogEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class DeepfakeShieldDatabase : RoomDatabase() {
    abstract fun alertDao(): AlertDao
    abstract fun phoneReputationDao(): PhoneReputationDao
    abstract fun domainReputationDao(): DomainReputationDao
    abstract fun userFeedbackDao(): UserFeedbackDao
    abstract fun vaultDao(): VaultDao
    abstract fun scamPatternDao(): ScamPatternDao
    abstract fun exportHistoryDao(): ExportHistoryDao
    abstract fun communityThreatDao(): CommunityThreatDao
    abstract fun behaviorProfileDao(): BehaviorProfileDao
    abstract fun scammerFingerprintDao(): ScammerFingerprintDao
    abstract fun patternWeightDao(): PatternWeightDao
    abstract fun learnedPatternDao(): LearnedPatternDao
    abstract fun scamCampaignDao(): ScamCampaignDao
    abstract fun threatPredictionDao(): ThreatPredictionDao
    abstract fun userRiskProfileDao(): UserRiskProfileDao
    abstract fun auditLogDao(): AuditLogDao
}
