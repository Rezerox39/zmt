# ⚡ ZMT — Terminal-Inspired Music Player for Android

[![Release](https://img.shields.io/github/v/release/Rezerox39/zmt?style=flat&label=release&color=FF6B35)](https://github.com/Rezerox39/zmt/releases/latest)
[![Build](https://img.shields.io/github/actions/workflow/status/Rezerox39/zmt/build.yml?style=flat&label=build&color=FF6B35)](https://github.com/Rezerox39/zmt/actions)
[![License](https://img.shields.io/badge/license-MIT-FF6B35?style=flat)](LICENSE)
[![Stars](https://img.shields.io/github/stars/Rezerox39/zmt?style=flat&color=FF6B35)](https://github.com/Rezerox39/zmt/stargazers)

**ZMT** is a terminal-style (TUI-inspired) music player for Android. Play local audio files, stream music from **Jellyfin** servers, sync audio from **Telegram channels**, and play **YouTube Music** — all in one app with a dark AMOLED-optimized interface.

Forked from [DMT](https://github.com/imjyotiraditya/dmt) by Jyotiraditya — extended with Telegram sync, YouTube Music playback, and more.

---

## ✨ Features

### 🎧 Playback & Audio
- **Local music** — plays MP3, FLAC, M4A, OGG, WAV, AAC, OPUS (bundled FFmpeg decoders)
- **YouTube Music** — search and stream via yt-dlp + Innertube API
- **Jellyfin** — stream from your personal media server (covers, lyrics, format info)
- **Telegram sync** — connect any public Telegram channel as a music source
- Replay gain volume normalization
- Sleep timer (15/30/60 min) | Playback speed (0.75× – 2×) | Shuffle & repeat
- System equalizer support

### 🎨 Dark AMOLED Themes
- **Classic terminal** — pure black background, monospace typography
- **AMOLED Black** — every pixel black, battery-friendly 🔥
- **Red AMOLED** — dark with deep crimson accents
- All themes respect AMOLED — true blacks, no wasted battery

### 📝 Lyrics
- Synced TTML and LRC lyrics with karaoke-style highlighting
- Transliteration and translation support
- Dual-singer duet side pinning
- Auto-fetch missing lyrics from LRCLIB

### 📱 Library & Organization
- Browse by tracks, albums, artists, folders, playlists
- Cue sheet album splitting
- m3u8 playlist support
- Folder blocklist to hide junk files
- Listening stats: play counts, time played, most played

### 🎵 Player
- Fullscreen expanded player with ASCII cover art
- Mini player with widget support (home screen widget)
- Queue management with bottom sheet
- Session restore — picks up where you left off
- Android Auto support (tracks, albums, folders, voice search)

### 📥 Download
- **Download YouTube tracks** directly to device via MediaStore
- 64KB chunked streaming with progress reporting
- Saves to Music directory — visible in any music app

### 🤖 Telegram Integration
- Phone number + OTP + 2FA login flow
- Browse and stream from Telegram channels
- Works alongside local files and Jellyfin
- Powered by TDLib (JNI)

---

## 📸 Screenshots

<table>
  <tr>
    <td width="68%">
      <img src=".github/screenshots/library-landscape.png" alt="ZMT library in landscape mode showing track listing" />
    </td>
    <td width="32%" rowspan="2">
      <img src=".github/screenshots/player-portrait.png" alt="ZMT expanded player with album art and controls" />
    </td>
  </tr>
  <tr>
    <td>
      <img src=".github/screenshots/player-landscape.png" alt="ZMT player in landscape orientation" />
    </td>
  </tr>
</table>

---

## 📥 Download

Download the latest APK from the **[Releases page](https://github.com/Rezerox39/zmt/releases/latest)**.

| Build | Download |
|-------|----------|
| Latest stable | [![Release](https://img.shields.io/github/v/release/Rezerox39/zmt?style=flat&label=APK&color=FF6B35)](https://github.com/Rezerox39/zmt/releases/latest) |
| CI build (bleeding edge) | GitHub Actions → build artifacts |

**Minimum Android**: API 30 (Android 11)
**Target Android**: API 37 (Android 17)

---

## 🔧 Build from Source

```bash
# Clone
git clone https://github.com/Rezerox39/zmt.git
cd zmt

# Build release APK
./gradlew assembleRelease

# Install directly
./gradlew installDebug
```

Requires Android Studio, JDK 21, Android NDK 29, CMake 4.1.

### Build Configuration

Set Telegram API credentials (required for Telegram sync):

```bash
# In local.properties:
TELEGRAM_API_ID=your_api_id
TELEGRAM_API_HASH=your_api_hash

# Or as environment variables:
export TELEGRAM_API_ID=your_api_id
export TELEGRAM_API_HASH=your_api_hash
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | **Kotlin** 2.4 |
| UI | **Jetpack Compose**, Material3 |
| Media | **Media3** (ExoPlayer), FFmpeg decoder |
| DI | **Hilt** (Dagger) |
| Network | **Ktor** (CIO + OkHttp), **OkHttp** |
| Telegram | **TDLib** (JNI) |
| YouTube | **yt-dlp** (Chaquopy Python bridge), **Innertube** API |
| Data | **DataStore** Preferences |
| Serialization | **kotlinx.serialization** |
| Build | **Gradle** 9.x, AGP, KSP, R8 |

No ads. No analytics. No tracking. Network is used only for your Jellyfin server, LRCLIB, Telegram, and YouTube Music.

---

## 📊 Repository Stats

![Alt](https://repobeats.axiom.co/api/embed/REPO_BEATS_ID_HERE "Repository activity")

---

## 🙏 Credits

- **Jyotiraditya** ([@imjyotiraditya](https://github.com/imjyotiraditya)) — creator of the original DMT
- **Abhi** ([@Rezerox39](https://github.com/Rezerox39)) — ZMT maintainer, Telegram sync, YouTube Music, downloads
- Built with ❤️ for music lovers who prefer terminals over GUIs

---

## 📄 License

This project is a fork of [DMT](https://github.com/imjyotiraditya/dmt). Licensed under MIT.
