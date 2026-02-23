package com.deepfakeshield.data.repository

import com.deepfakeshield.data.dao.DomainReputationDao
import com.deepfakeshield.data.entity.DomainReputationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DomainReputationRepository @Inject constructor(
    private val domainReputationDao: DomainReputationDao
) {
    companion object {
        // Infrastructure domains the app relies on — must never be blocked/reported.
        // Blocking these would cut off threat intelligence feeds, Safe Browsing API,
        // and cloud hash checking, silently degrading the app's protection.
        private val PROTECTED_INFRASTRUCTURE_DOMAINS = setOf(
            "googleapis.com",          // Google infrastructure
            "osint.digitalside.it",    // Threat intelligence feed
            "githubusercontent.com",   // Threat intelligence feed (GitHub raw)
            "raw.githubusercontent.com",
            "virustotal.com",          // Security vendor
            "phishtank.org",           // Phishing feed
            "openphish.com",           // Phishing feed
            "urlhaus-api.abuse.ch",    // Malware URL feed
            "urlhaus.abuse.ch"         // URLhaus feed
        )

        /** Returns true if [domain] is part of the app's own infrastructure and must not be flagged. */
        private fun isProtectedDomain(domain: String): Boolean {
            val lower = domain.lowercase()
            return PROTECTED_INFRASTRUCTURE_DOMAINS.any { lower == it || lower.endsWith(".$it") }
        }
    }

    suspend fun getReputation(domain: String): DomainReputationEntity? =
        domainReputationDao.getReputation(domain)
    
    fun getBlockedDomains(): Flow<List<DomainReputationEntity>> =
        domainReputationDao.getBlockedDomains()
    
    suspend fun reportAsScam(domain: String, category: String? = null) {
        if (isProtectedDomain(domain)) return // Never flag our own infrastructure
        try {
            val existing = domainReputationDao.getReputation(domain)
            if (existing != null) {
                domainReputationDao.incrementScamReports(domain, System.currentTimeMillis())
            } else {
                domainReputationDao.insertOrUpdate(
                    DomainReputationEntity(
                        domain = domain,
                        trustScore = -50,
                        reportCount = 1,
                        scamReports = 1,
                        safeReports = 0,
                        lastReportedAt = System.currentTimeMillis(),
                        category = category
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("DomainReputationRepo", "Failed to report domain as scam: $domain", e)
        }
    }
    
    suspend fun addToWhitelist(domain: String) = reportAsSafe(domain)

    suspend fun isWhitelisted(domain: String): Boolean {
        val rep = domainReputationDao.getReputation(domain) ?: return false
        return rep.trustScore > 0 || rep.safeReports > 0
    }

    suspend fun reportAsSafe(domain: String) {
        try {
            val existing = domainReputationDao.getReputation(domain)
            if (existing != null) {
                domainReputationDao.incrementSafeReports(domain, System.currentTimeMillis())
            } else {
                domainReputationDao.insertOrUpdate(
                    DomainReputationEntity(
                        domain = domain,
                        trustScore = 50,
                        reportCount = 1,
                        scamReports = 0,
                        safeReports = 1,
                        lastReportedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("DomainReputationRepo", "Failed to report domain as safe: $domain", e)
        }
    }

    suspend fun blockDomain(domain: String) {
        if (isProtectedDomain(domain)) return // Never block our own infrastructure
        val existing = domainReputationDao.getReputation(domain)
        if (existing != null) {
            domainReputationDao.setBlocked(domain, true)
        } else {
            domainReputationDao.insertOrUpdate(
                DomainReputationEntity(
                    domain = domain,
                    trustScore = -100,
                    isBlocked = true,
                    reportCount = 1,
                    scamReports = 1,
                    safeReports = 0,
                    lastReportedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun unblockDomain(domain: String) {
        domainReputationDao.setBlocked(domain, false)
    }

    suspend fun deleteReputation(domain: String) {
        domainReputationDao.deleteByDomain(domain)
    }
}
