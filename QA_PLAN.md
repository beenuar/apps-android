# DeepFake Shield – QA Plan

## Overview

Full-blown QA strategy for DeepFake Shield: scammers, deepfake, and malware protection.

---

## 1. Test Pyramid

| Layer | Scope | Tools | Location |
|-------|-------|-------|----------|
| **Unit** | Engines, analyzers, DAOs, utilities | JUnit 4, Kotlin Coroutines Test, Robolectric | `*/src/test/` |
| **Integration** | DB + Repository, Hilt modules | Room Testing, Hilt Testing | `*/src/test/` |
| **UI** | Screens, navigation | Compose UI Test, Espresso | `*/src/androidTest/` |
| **E2E** | Critical user flows | Manual / future automation | – |

---

## 2. Test Coverage

### Core Module (`:core`)
- **RiskIntelligenceEngine** – Text, URL, call, video analysis
- **UrlSafetyEngine** – Domain, TLD, lookalike detection

### AV Module (`:av`)
- **MalwareSignatureDatabase** – Hash lookup, byte patterns
- **HeuristicMalwareAnalyzer** – File + app heuristics
- **AntivirusEngine** – Scan flow, result mapping

### Data Module (`:data`)
- **AlertDao** – CRUD, unhandled count
- **PhoneReputationDao** – Reputation updates
- **DomainReputationDao** – Domain blocking

### App Module (`:app`)
- Services and workers (optional unit tests)
- Hilt dependency graph

---

## 3. Running Tests

```bash
# All unit tests
./gradlew testReleaseUnitTest

# Single module
./gradlew :av:testReleaseUnitTest
./gradlew :core:testReleaseUnitTest
./gradlew :data:testReleaseUnitTest

# With report
./gradlew testReleaseUnitTest && open app/build/reports/tests/testReleaseUnitTest/index.html
```

---

## 4. Static Analysis

### Detekt
```bash
./gradlew detekt
```
- Config: `config/detekt/detekt.yml`
- Complexity, naming, potential bugs

### Lint
```bash
./gradlew lint
```

---

## 5. QA Scripts

| Script | Purpose |
|--------|---------|
| `scripts/qa_full.sh` | Run all tests + detekt + lint |
| `scripts/qa_quick.sh` | Quick unit tests only |

---

## 6. Pre-Release Checklist

- [ ] All unit tests pass
- [ ] No critical lint issues
- [ ] Detekt passes (max issues: 0)
- [ ] Release build succeeds
- [ ] Manual smoke: install, onboarding, shield toggle, scan
- [ ] No crash on cold start

---

## 7. Test Data

- **Malware patterns**: Use non-malicious files with known headers (MZ, ELF, ZIP) for byte-pattern tests
- **Scam text**: Synthetic OTP, urgency, impersonation samples
- **URLs**: Lookalike domains, suspicious TLDs

---

## 8. CI (Future)

Recommended GitHub Actions workflow:

1. Checkout
2. Set up JDK 17
3. Run `./gradlew testReleaseUnitTest`
4. Run `./gradlew detekt`
5. Run `./gradlew lint`
6. Build release APK
