# Cyble — All-in-One Android Security Suite

<p align="center">
  <img src="docs/icon.png" width="120" alt="Cyble Logo"/>
</p>

Cyble is a comprehensive Android security suite that provides enterprise-grade protection in a consumer-friendly package. Everything runs on-device — no cloud dependency, no API keys required, no data collection.

## Features

### Core Protection
- **Deepfake Detection** — Real-time AI-powered video and media analysis using TensorFlow Lite and Google ML Kit
- **Scam Call & SMS Shield** — Screens incoming calls and messages for fraud patterns and phishing links
- **Antivirus Engine** — Signature + heuristic + YARA rule scanning with 54,000+ malware signatures and 9 real-time threat feeds
- **Tor VPN** — Embedded Tor binary routes ALL device traffic through the Tor network with 24 exit country options

### Security Intelligence
- **Dark Web Monitor** — Real HIBP breach database checking (800+ breaches, password k-anonymity)
- **Breach Monitor** — Scans device accounts against known breaches with per-account risk scoring
- **Network Monitor** — Live connection tracking from /proc/net/* with server identification (Google, Meta, Cloudflare, etc.)
- **Privacy Score** — 25 real on-device security checks across 5 categories with weighted scoring

### Analysis Tools
- **App Auditor** — Deep per-app analysis: 25 permission categories, 21 tracker SDKs, SHA-256 verification
- **Scam Number Lookup** — 300+ area codes, call log scan, SMS phishing detection (48 patterns)
- **Email Phishing Scanner** — 20+ NLP indicators for phishing, impersonation, and credential theft
- **Fake Review Detector** — 20+ indicators including AI/ChatGPT text detection
- **Photo Forensics** — EXIF deep analysis, sensor noise detection, edge sharpness, AI generation indicators
- **Digital Footprint** — 18 deep scans mapping your data exposure across apps, accounts, and networks

### Additional Features
- Call Shield with call log analysis and hour-by-hour activity heatmap
- Security News Feed from 8 live RSS sources (BleepingComputer, CISA, Krebs, etc.)
- Password Checker with real HIBP k-anonymity breach check
- Wi-Fi Scanner with encryption, captive portal, and ARP analysis
- Fake Domain Monitor with typosquatting and homoglyph detection
- Data Broker Opt-Out guide with 14 real broker opt-out links
- SIM Swap Protection with carrier-specific instructions
- Secure Notes vault with biometric lock
- Photo Metadata Stripper
- QR Code Scanner with URL safety analysis
- Daily Security Challenge quiz
- Floating protection bubble overlay

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material 3
- **Architecture:** Multi-module (16 Gradle modules), MVVM, Hilt DI
- **AI/ML:** TensorFlow Lite, Google ML Kit
- **Database:** Room
- **Preferences:** DataStore
- **Background:** WorkManager
- **Networking:** HttpURLConnection + Tor SOCKS5 proxy
- **Privacy:** Embedded Tor (Guardian Project tor-android)
- **VPN:** Android VpnService for system-wide traffic routing

## Building

```bash
# Clone
git clone https://github.com/beenuar/apps-android.git
cd cyble

# Add your Firebase config
# Place your google-services.json in app/

# Build release APK
./gradlew assembleRelease

# Build debug APK
./gradlew assembleDebug
```

### Requirements
- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 34
- Minimum deployment target: Android 8.0 (API 26)

## Download

Download the latest APK from [Releases](https://github.com/beenuar/apps-android/releases).

## Privacy

- All analysis runs on-device by default
- No data collection or telemetry
- No commercial API keys required
- Tor integration for IP privacy
- Passwords checked via k-anonymity (never sent in full)
- SSIDs and sensitive data stored as hashes only

## License

MIT License. See [LICENSE](LICENSE) for details.

## Contributing

Contributions welcome. Please open an issue first to discuss proposed changes.
