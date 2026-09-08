# ⚡ Spark Assist

> **The Voice-First Mobile AI Assistant Built for High-Performance Execution**  
> *Project Lead & Architect: Sejan (Supreme)*

[![Build & Release Signed APK](https://github.com/sejan/spark-assist/actions/workflows/release.yml/badge.svg)](https://github.com/sejan/spark-assist/actions/workflows/release.yml)
[![Platform](https://img.shields.io/badge/Platform-Android_14_--_16-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-blue.svg)](https://developer.android.com/jetpack/compose)
[![AI Engine](https://img.shields.io/badge/Gemini_Live-3.5_Flash_Lite-orange.svg)](https://ai.google.dev)
[![Local DB](https://img.shields.io/badge/Persistence-Room_2.7.0-red.svg)](https://developer.android.com/training/data-storage/room)

---

## 📖 Table of Contents

- [Overview](#-overview)
- [Core Architecture & Data Flow](#-core-architecture--data-flow)
- [Key Features](#-key-features)
  - [Quantum HUD & Dynamic Waveform](#1-quantum-hud--dynamic-waveform)
  - [Zero-Latency Audio Engine with AES](#2-zero-latency-audio-engine-with-aes)
  - [16 Native Hardware & System Tools](#3-16-native-hardware--system-tools)
  - [Core Memory Vault (Offline-First)](#4-core-memory-vault-offline-first)
  - [Floating System Overlay Bubble](#5-floating-system-overlay-bubble)
- [Automated Release Pipeline (CI/CD)](#-automated-release-pipeline-cicd)
  - [Workflow Overview](#workflow-overview)
  - [GitHub Secrets Configuration](#github-secrets-configuration)
  - [Generating an Upload Keystore](#generating-an-upload-keystore)
- [Local Setup & Development](#-local-setup--development)
  - [Prerequisites](#prerequisites)
  - [Clone & Build](#clone--build)
  - [Running Unit & Screenshot Tests](#running-unit--screenshot-tests)
- [Voice Command Reference](#-voice-command-reference)
- [Permissions & Privacy](#-permissions--privacy)
- [License](#-license)

---

## 🌟 Overview

**Spark Assist** is a native Android AI assistant designed from the ground up for low-latency, hands-free, voice-driven execution. Unlike conventional chatbots that wait for entire sentences and rely on server-rendered text cards, Spark Assist streams audio bidirectionally over WebSockets to **Gemini 3.5 Flash Lite** (`BidiGenerateContent`), enabling natural conversational turn-taking, instant interruptions, and direct native execution of Android hardware, telephony, and system services.

---

## 📐 Core Architecture & Data Flow

Spark Assist operates on a real-time reactive pipeline:

```text
 ┌────────────────┐         16kHz PCM          ┌─────────────────────────┐
 │ Spoken Voice   │ ─────────────────────────> │ Gemini 3.5 Flash Lite   │
 │ (Microphone)   │                            │ (WebSocket Live Bidi)   │
 └────────────────┘                            └────────────┬────────────┘
         ▲                                                  │
         │ Acoustic Echo Shield (AES)                       │ Function Calls &
         │                                                  │ 24kHz PCM Audio
         ▼                                                  ▼
 ┌────────────────┐     Action Executions      ┌─────────────────────────┐
 │ Native Android │ <───────────────────────── │ Spark Assist Native     │
 │ Engines        │                            │ Tool Execution Registry │
 └────────────────┘                            └────────────┬────────────┘
   • Telephony (Call/SMS)                                   │
   • WhatsApp Dispatch                                      │ Audio Frames
   • Media Control & Volume                                 ▼
   • Hardware Settings & Flashlight            ┌─────────────────────────┐
   • Calendar & System Alarms                  │ Zero-Latency AudioTrack │
   • Offline Room Memory Vault                 │ Player + Interruption   │
                                               └─────────────────────────┘
```

### Real-Time Execution Pipeline
1. **Spoken Voice Capture**: 16kHz 16-bit Mono Linear PCM audio captured using low-latency `AudioRecord` with dynamic energy RMS tracking.
2. **Gemini Live Bidi Stream**: Direct WebSocket pipe sending `realtime_input` base64-encoded PCM chunks to Gemini Live endpoint.
3. **Acoustic Echo Shielding (AES)**: While Spark is speaking, input microphone energy is shielded and buffered to prevent self-triggering loops, with a 200ms decay gate post-speech.
4. **Instant Interruption Flushing**: When user speech is detected while Spark is speaking, the native `AudioTrack` buffer is flushed and cleared in under 10ms.
5. **Tool Execution Engine**: Structured Gemini tool calls are resolved against the registered native Kotlin tool registry and return execution confirmations directly back to the active model context.

---

## 🚀 Key Features

### 1. Quantum HUD & Dynamic Waveform
- **Energy Orb**: Centrally mounted, pulsating circular orb with dynamic radial gradient and breathing animation indicating current state (`LISTENING`, `SPEAKING`, `PROCESSING`, `IDLE`).
- **32-Bar Real-Time Waveform**: High-performance Jetpack Compose Canvas visualizing microphone amplitude and output speech frequencies.
- **Live Telemetry Strip**: Displays live WebSocket round-trip latency (ms), speech frame rate (FPS), and connection status.

### 2. Zero-Latency Audio Engine with AES
- **Capture**: Continuous coroutine-driven `AudioRecord` operating at 16kHz Mono.
- **Playback**: Direct streaming to `AudioTrack` at 24kHz Mono, with concurrent sample queuing.
- **Smart Muting & Turn-taking**: Prevents feedback loops without requiring hardware AEC chips.

### 3. 16 Native Hardware & System Tools

| Tool Identifier | Description | Parameters |
|---|---|---|
| `control_device_hardware` | Toggle flashlight, launch Wi-Fi / Bluetooth / Location / Sound settings | `action`, `state` |
| `call_contact` | Screen or place a phone call to a contact or raw phone number | `contactName`, `phoneNumber` |
| `send_whatsapp_message` | Compose and dispatch WhatsApp messages via direct intent | `contactName`, `message`, `phoneNumber` |
| `send_sms` | Send direct SMS message to a phone number | `phoneNumber`, `message` |
| `search_contacts` | Look up stored contacts and phone numbers matching a query | `query` |
| `control_media_playback` | Control media: `play`, `pause`, `next`, `previous`, `volume_up`, `volume_down`, `mute` | `command`, `volumeLevel` |
| `create_calendar_event` | Insert events or appointments into Google Calendar | `title`, `startTime`, `endTime`, `description` |
| `open_installed_app` | Launch any Android app by package or common name | `appName` |
| `save_core_memory` | Save persistent facts/notes to the local offline Room database | `key`, `value`, `category` |
| `retrieve_core_memory` | Fetch stored facts/notes from the local offline Room database | `key`, `category` |
| `search_web` | Execute web queries for live external information | `query` |
| `manage_notification_listener` | Inspect unread notifications and toggle auto-reply agent | `action`, `enabled` |

### 4. Core Memory Vault (Offline-First)
- Backed by **Room 2.7.0** (`SparkDatabase`, `SparkDao`).
- Retains user memories (e.g. "My car is parked on Level 3, Bay 14", "My wife's birthday is June 12") permanently across app restarts.
- Zero cloud leakage: Memories remain stored on the local SQLite device storage.

### 5. Floating System Overlay Bubble
- Android `SYSTEM_ALERT_WINDOW` implementation (`SparkOverlayService`).
- Draggable glassmorphic floating head with quick mic activation, flashlight toggle, and status indicators.
- Allows using Spark Assist over navigation apps, browsers, or games without switching apps.

---

## 🔄 Automated Release Pipeline (CI/CD)

Spark Assist includes a GitHub Actions workflow located at [`.github/workflows/release.yml`](.github/workflows/release.yml). Every push to `main`, `master`, or tags automatically builds, signs, and packages a release APK, publishing it directly to **GitHub Releases**.

### Workflow Overview

```text
 Push to main / tag
         │
         ▼
 [GitHub Actions: ubuntu-latest]
         │
         ├─► Checkout Code (actions/checkout@v4)
         ├─► Setup Temurin JDK 21 (actions/setup-java@v4)
         ├─► Setup Android SDK Tools (android-actions/setup-android@v3)
         ├─► Gradle Caching (gradle/actions/setup-gradle@v4)
         ├─► Keystore Preparation (Decodes secrets OR bootstraps self-signed key)
         ├─► Environment Injection (.env / GEMINI_API_KEY)
         ├─► Build Release APK (`./gradlew assembleRelease`)
         ├─► Checksum Generation (SHA-256)
         ├─► Upload Workflow Artifacts (actions/upload-artifact@v4)
         └─► Publish GitHub Release with APK & Checksums (softprops/action-gh-release@v2)
```

### GitHub Secrets Configuration

To use your production signing key and API credentials in GitHub Actions, navigate to **Settings > Secrets and variables > Actions** in your repository and configure the following secrets:

| Secret Name | Required | Description |
|---|---|---|
| `KEYSTORE_BASE64` | Recommended | Base64-encoded string of your `my-upload-key.jks` release keystore file. |
| `STORE_PASSWORD` | Recommended | Password for the release keystore. |
| `KEY_PASSWORD` | Recommended | Password for the key alias `upload`. |
| `GEMINI_API_KEY` | Optional | Your Gemini API key. Injected into `.env` during the build. (Can also be set in-app). |
| `GITHUB_TOKEN` | Automatic | Provided automatically by GitHub Actions to create the release. |

> 💡 **Automatic Fallback Mode**: If `KEYSTORE_BASE64` or `STORE_PASSWORD` are not configured in your repository secrets, the pipeline automatically generates a self-signed release keystore on the fly. This guarantees that your CI build never fails on fresh forks or initial clones!

### Generating an Upload Keystore

To create a production upload keystore on your local development machine:

```bash
# Generate the keystore file
keytool -genkeypair -v \
  -keystore my-upload-key.jks \
  -alias upload \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass "YourStrongKeystorePassword" \
  -keypass "YourStrongKeyPassword" \
  -dname "CN=SparkAssist, OU=Mobile, O=Spark, L=SanFrancisco, ST=CA, C=US"

# Convert to base64 for GitHub Secrets
base64 -i my-upload-key.jks | tr -d '\n' > keystore_base64.txt
```

Copy the string from `keystore_base64.txt` into the `KEYSTORE_BASE64` secret in GitHub.

---

## 💻 Local Setup & Development

### Prerequisites
- **Android Studio** Ladybug (2024.2+) or newer
- **JDK 21** (Temurin or OpenJDK)
- **Android SDK** API 24 (minimum) to API 36 (target)

### Clone & Build

```bash
# 1. Clone the repository
git clone https://github.com/sejan/spark-assist.git
cd spark-assist

# 2. Configure API Key
cp .env.example .env
echo "GEMINI_API_KEY=your_gemini_api_key_here" >> .env

# 3. Build Debug APK
./gradlew assembleDebug

# 4. Build Signed Release APK locally
KEYSTORE_PATH=/path/to/my-upload-key.jks \
STORE_PASSWORD=YourPassword \
KEY_PASSWORD=YourPassword \
./gradlew assembleRelease
```

The compiled release APK will be located at:
```text
app/build/outputs/apk/release/app-release.apk
```

### Running Unit & Screenshot Tests

Spark Assist uses **Robolectric** for fast JVM-based tests and **Roborazzi** for visual screenshot verification:

```bash
# Run all unit and Robolectric tests
./gradlew testDebugUnitTest

# Verify screenshot visual regressions
./gradlew verifyRoborazziDebug

# Record new reference screenshots
./gradlew recordRoborazziDebug
```

---

## 🎙️ Voice Command Reference

Try these commands with Spark Assist:

| Category | Example Voice Prompt |
|---|---|
| **Hardware** | *"Turn on the flashlight."*<br>*"Open Bluetooth settings."*<br>*"Turn off the torch."* |
| **Telephony** | *"Call Sarah on speaker."*<br>*"Dial 555-0199."*<br>*"Decline this call."* |
| **WhatsApp & SMS** | *"Send a WhatsApp message to Alex saying I will be there in 10 minutes."*<br>*"Send an SMS to John with my current address."* |
| **Media & Audio** | *"Play music."*<br>*"Skip to the next song."*<br>*"Turn up the volume to 80 percent."* |
| **Memory Vault** | *"Remember that my passport is in the blue luggage bag."*<br>*"Where did I put my passport?"*<br>*"Save a memory: Mom's favorite flowers are yellow tulips."* |
| **Calendar & Alarms** | *"Schedule a meeting with David tomorrow at 3 PM called Budget Review."*<br>*"What is on my schedule for today?"* |
| **App Launching** | *"Open Spotify."*<br>*"Launch YouTube Music."* |

---

## 🛡️ Permissions & Privacy

Spark Assist requests only permissions strictly necessary for native execution:

- `RECORD_AUDIO`: Voice input streaming to Gemini Live.
- `CALL_PHONE` & `READ_CONTACTS`: Contact lookup and automated dialing.
- `SEND_SMS`: Sending SMS dispatches upon voice request.
- `CAMERA`: Flashlight control via `CameraManager`.
- `READ_CALENDAR` & `WRITE_CALENDAR`: Creating and viewing calendar events.
- `SYSTEM_ALERT_WINDOW`: Displaying the draggable floating assistant HUD.
- `BIND_NOTIFICATION_LISTENER_SERVICE`: Reading notifications for caller announcement and auto-replies.

**Privacy Guarantee**: Your voice stream is transmitted directly to the Gemini API endpoint using your own API key. No audio or memory data is routed through third-party servers.

---

## 📄 License

This project is licensed under the Apache 2.0 License. See the [LICENSE](LICENSE) file for details.
