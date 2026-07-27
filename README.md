# ⚡ zmt ⚡

> *a tui-inspired local music player for android — feel the beat*

---

## 🎵 what is zmt?

zmt is a **terminal-style music player** for Android that plays your local music, streams from [Jellyfin](https://jellyfin.org) servers, and syncs from **Telegram channels**. built with love, dark aesthetics, and zero compromises.

forked from [dmt](https://github.com/imjyotiraditya/dmt) by jyotiraditya — improved, extended, and made our own.

---

## ✨ features

### 🎧 playback
- plays local audio files with **bundled FFmpeg decoders** — every format just works
- streams from **Jellyfin** servers with covers, lyrics, and format info
- 🆕 **Telegram channel sync** — connect any public channel as a music source
- replay gain volume normalization
- sleep timer (15/30/60 min)
- playback speed (0.75x – 2x)
- shuffle & repeat
- system equalizer support

### 🎨 themes
- **AMOLED Black** — pure black, easy on the eyes 🔥
- **Red AMOLED** — dark with crimson accents ❤️‍🔥
- **Liquid Glass** — cool blue glass aesthetic 🪟

### 📝 lyrics
- synced **TTML** and **LRC** lyrics with karaoke highlighting
- transliteration & translation support
- dual singer duet side pinning
- fetch missing lyrics from **LRCLIB** on demand

### 📱 android auto
- browse by tracks, albums, and folders
- voice search
- shuffle & repeat buttons on the car screen

### 🎵 library
- library, albums, artists, folders, and playlists tabs
- folder blocklist to hide junk
- cue sheet album splitting
- m3u8 playlist support
- listening stats: time played, play counts, most played

### 📊 player
- fullscreen player with landscape support
- mini player with ASCII cover art
- 🆕 **home screen widget** — control playback from your launcher
- queue management with bottom sheet
- session restore — picks up where you left off

### 🤖 telegram sync
- connect any public Telegram channel as a music source
- phone number + code login flow
- stream audio without downloading full files
- works alongside local and Jellyfin sources

---

## 📸 screenshots

<table>
  <tr>
    <td width="68%">
      <img src=".github/screenshots/library-landscape.png" alt="library, landscape" />
    </td>
    <td width="32%" rowspan="2">
      <img src=".github/screenshots/player-portrait.png" alt="player, portrait" />
    </td>
  </tr>
  <tr>
    <td>
      <img src=".github/screenshots/player-landscape.png" alt="player, landscape" />
    </td>
  </tr>
</table>

---

## 🔧 building

open in android studio and hit run. **minSdk 30**.

```bash
./gradlew assembleRelease
```

release builds are minified and land around **12mb**.

---

## 🛠️ stack

kotlin · compose · media3 · datastore · tdlib (JNI) · hilt

**no ads, no analytics.** the network is only used for your own jellyfin server, lrclib, and telegram. it just plays music.

---

## 📦 releases

download the latest APK from [releases](https://github.com/Rezerox39/zmt/releases).

---

## 🙏 credits

- [imjyotiraditya](https://github.com/imjyotiraditya) — original dmt
- **made by Abhi ❤️**

---

## 📄 license

this project is a fork of [dmt](https://github.com/imjyotiraditya/dmt). check the original for license details.
