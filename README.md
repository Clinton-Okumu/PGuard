# PGuard 🛡️

> **Shield Your Mind. Reclaim Your Focus.**  
> An Android digital wellbeing and adult-content blocker backed by a Go REST API service.

---

[![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](./mobile)
[![Go](https://img.shields.io/badge/Go-Backend-00ADD8?style=for-the-badge&logo=go&logoColor=white)](./Backend)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-4285F4?style=for-the-badge&logo=android&logoColor=white)](./mobile)

---

## 📌 Overview

**PGuard** helps users break compulsive habits, block adult content and short-form video feeds, and build sustainable screentime routines by combining an on-device Android Accessibility protection engine with a Go + PostgreSQL backend for authentication, settings sync, habit streaks, and accountability partner verification.

---

## ✨ Implemented Features

### 🛡️ On-Device Content Blocking & Protection
- **Browser URL & Keyword Filtering:** Inspects supported browsers on-device via `AccessibilityService` (`ContentFilter` + `BrowserInspector`) to block adult domains, NSFW keywords, and custom blocklist entries while honoring custom whitelists.
- **Short-Form Feed & App Controls:** Supports blocking YouTube Shorts, Instagram Reels, WhatsApp Channels discovery, TikTok, custom blocked apps, unsupported browsers, and newly installed apps.
- **Focus Mode & 2-Minute Urge Lockdown:** Scheduled Focus Mode app allowlists plus an emergency 2-minute phone lockdown protocol.
- **Tamper Resistance & Accountability Guard:** Protects toggles, blocklist removals, focus schedules, and Android App Info/uninstall screens using `DevicePolicyManager` + anti-tamper monitoring and three friction modes: Accountability Partner (6-digit server-verified approval code), Myself (60s typing challenge), or 24-Hour Time Delay.

### 📈 Habit & Streak Tracking
- **Clean Streak & Milestones:** Tracks clean days, best streak, urges prevented, weekly/monthly consistency calendars, daily Stoic mindset prompts, and 1-year milestone badges.
- **Daily Habits & Relapse Reflection:** Manage daily replacement habits and log lapse triggers.

### 🤝 Accountability System
- **Partner Invite & Web Acceptance:** Invite a trusted partner by email (dispatched via Resend) with a confirmation link (`GET` review page + `POST` confirmation).
- **Server-Verified Unlock Codes:** Single-use, 10-minute expiring 6-digit approval codes stored hashed in PostgreSQL with attempt rate-limiting.

### 🗺️ Planned / Roadmap
- Local `VpnService` DNS-level filtering
- Web portal / dashboard

---

## 🏗️ Architecture & Tech Stack

| Layer | Technology | Key Highlights |
| :--- | :--- | :--- |
| **Mobile App** | **Kotlin**, Jetpack Compose, Retrofit, DataStore | Native Android app, Material 3 UI, `AccessibilityService` filtering & `DevicePolicyManager` |
| **Backend API** | **Go**, `chi`, GORM, PostgreSQL | JWT + rotating refresh tokens, Google ID token auth, Resend mailer, rate limiting |

---

## 📁 Repository Structure

```plaintext
PGuard/
├── mobile/                   # Native Android Application (Kotlin + Compose)
│   ├── app/
│   │   ├── src/main/         # Compose UI, engine (accessibility/admin/filter/receiver), network, repositories
│   │   ├── src/debug/        # Debug network security config (cleartext LAN dev allowed)
│   │   ├── src/test/         # JVM unit tests (ContentFilter, BrowserInspector, FocusSchedule, AntiTamperGuard)
│   │   └── build.gradle.kts
│   ├── gradle/libs.versions.toml
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── Backend/                  # Core Backend API Service (Go + PostgreSQL)
│   ├── internal/
│   │   ├── api/              # HTTP handlers & unit tests (auth, settings, blocklist, habits, accountability)
│   │   ├── app/              # Application wiring & healthcheck
│   │   ├── auth/             # Google ID token verifier
│   │   ├── mailer/           # Resend transactional emails
│   │   ├── middleware/       # JWT auth middleware, body size limits & rate limiter
│   │   ├── routes/           # Chi router & CORS setup
│   │   ├── store/            # GORM PostgreSQL models & repositories
│   │   ├── tokens/           # JWT & SHA-256 refresh token helpers
│   │   └── utils/            # JSON envelope helpers
│   ├── main.go               # Server entrypoint with graceful shutdown
│   ├── .env.example          # Environment variable template
│   ├── go.mod
│   └── go.sum
│
└── .github/workflows/ci.yml  # Go & Android CI checks
```

---

## 🚀 Getting Started

### Prerequisites
- **Android:** Android Studio with JDK 17+ and Android SDK 34+
- **Backend:** Go 1.24+ and PostgreSQL

### 1. Backend Setup (Go)
```bash
cd Backend
cp .env.example .env   # Configure DATABASE_URL, JWT_SECRET (>=32 chars), GOOGLE_CLIENT_IDS, RESEND_API_KEY
go mod tidy
go run main.go
```

Run backend checks:
```bash
cd Backend
go vet ./...
go test -race -v ./...
```

### 2. Mobile App Setup (Kotlin / Android)
Optionally set `pguard.apiBaseUrl` and `pguard.googleWebClientId` in `mobile/local.properties` for local debug builds.

```bash
cd mobile
./gradlew testDebugUnitTest
./gradlew assembleDebug
```
