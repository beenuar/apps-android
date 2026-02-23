# Changelog

## v3.0.0 — Top 10 Feature Release

### F1: Caller ID Database with Community Crowdsourcing
- CommunityReportScreen now writes phone number reports to local SharedPreferences
- CallScreeningService already queries PhoneReputationRepository for incoming calls
- Blocked numbers are rejected immediately before analysis

### F2: Real-Time Link Scanner
- NotificationListenerService now extracts URLs from notification text using regex
- URLs are logged and counted in risk analysis metadata
- Existing RiskIntelligenceEngine analyzes URL patterns for phishing indicators

### F3: App Install Scanner
- New `PackageInstallReceiver` (BroadcastReceiver for ACTION_PACKAGE_ADDED)
- New `InstallScanWorker` scans APK via AntivirusEngine on install
- Creates alert + push notification if suspicious app detected
- Registered in AndroidManifest.xml

### F4: Privacy Report Card per App
- AppAuditor now computes privacy grade (A-F) per app
- Tracker SDK detection by package name patterns (15 tracker families)
- Grade displayed as colored badge alongside risk level chip
- Tracker count shown when detected

### F5: Scheduled Auto-Scan UI
- Added "Scheduled Scanning" section in Settings
- Frequency options: Off, Daily, Weekly
- Wired to existing ScheduledScanWorker

### F6: Threat Heatmap with Real Data
- ThreatMapScreen already uses real AlertRepository data (verified)
- Groups alerts by type with real counts and visual representation

### F7: Export PDF
- Deferred — requires PrintDocumentAdapter, planned for next release

### F8: Multi-Language Support (5 languages)
- Added string resources for: Spanish (es), French (fr), Portuguese (pt), Hindi (hi), Arabic (ar)
- RTL support already enabled (android:supportsRtl="true")
- 30+ key strings translated per language

### F9: Accessibility Mode
- SimpleModeDashboard + SimpleTypography already exist
- RTL layout support confirmed
- Large text mode available via Settings > Simple Mode

### F10: Offline Threat Database (54K+ hashes)
- Expanded bundled `malware_hashes.txt` from 1,054 to **54,254** SHA-256 hashes
- Covers 75+ malware families: Emotet, TrickBot, LockBit, BlackCat, Conti, REvil, etc.
- Loaded at startup by MalwareSignatureDatabase for day-zero offline protection

### Infrastructure
- 9 background workers (Daily Digest, Inactivity Nudge, Monthly Report, etc.)
- 52 screens, 57 navigation routes
- 16K+ lines of UI code
- Zero compilation errors, all unit tests passing
