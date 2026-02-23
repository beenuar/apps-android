package com.deepfakeshield.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.deepfakeshield.core.model.RiskSeverity
import com.deepfakeshield.core.model.ThreatSource
import com.deepfakeshield.core.model.ThreatType
import com.deepfakeshield.data.database.DeepfakeShieldDatabase
import com.deepfakeshield.data.entity.AlertEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AlertDaoTest {

    private lateinit var database: DeepfakeShieldDatabase
    private lateinit var alertDao: AlertDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, DeepfakeShieldDatabase::class.java)
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
        alertDao = database.alertDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun `insert and retrieve alert`() = runTest {
        val alert = AlertEntity(
            threatType = ThreatType.SCAM_MESSAGE,
            source = ThreatSource.SMS,
            severity = RiskSeverity.HIGH,
            score = 75,
            confidence = 0.9f,
            title = "Test Scam",
            summary = "Suspicious message",
            content = "Share OTP",
            senderInfo = "+1234567890",
            timestamp = System.currentTimeMillis()
        )
        val id = alertDao.insertAlert(alert)
        assertTrue(id > 0)

        val retrieved = alertDao.getAlertById(id)
        assertNotNull(retrieved)
        assertEquals("Test Scam", retrieved!!.title)
        assertEquals(ThreatType.SCAM_MESSAGE, retrieved.threatType)
    }

    @Test
    fun `getUnhandledCount returns correct count`() = runTest {
        assertEquals(0, alertDao.getUnhandledCount().first())

        alertDao.insertAlert(AlertEntity(
            threatType = ThreatType.SCAM_MESSAGE,
            source = ThreatSource.SMS,
            severity = RiskSeverity.MEDIUM,
            score = 50,
            confidence = 0.8f,
            title = "A",
            summary = "B",
            content = null,
            senderInfo = null,
            timestamp = System.currentTimeMillis()
        ))
        assertEquals(1, alertDao.getUnhandledCount().first())
    }

    @Test
    fun `markAsHandled updates alert`() = runTest {
        val id = alertDao.insertAlert(AlertEntity(
            threatType = ThreatType.PHISHING_ATTEMPT,
            source = ThreatSource.SMS,
            severity = RiskSeverity.HIGH,
            score = 80,
            confidence = 0.95f,
            title = "Phish",
            summary = "Phishing attempt",
            content = null,
            senderInfo = null,
            timestamp = System.currentTimeMillis()
        ))
        alertDao.markAsHandled(id, System.currentTimeMillis())

        val retrieved = alertDao.getAlertById(id)
        assertNotNull(retrieved)
        assertTrue(retrieved!!.isHandled)
    }
}
