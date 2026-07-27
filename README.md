# zmt

a tui-inspired local music player for android with Telegram channel sync.

forked from [dmt](https://github.com/imjyotiraditya/dmt) by jyotiraditya.

## what zmt adds over dmt

### Telegram channel sync

- connect any public Telegram channel as a music source
- browse and play audio files directly from Telegram channels
- stream tracks without downloading the full file first
- works alongside local files and Jellyfin — switch sources from the settings
- phone number + code login flow, session persists across restarts

### how it works

zmt uses TDLib (Telegram Database Library) via JNI to connect to Telegram's
API. when you add a Telegram source, you authenticate with your phone number,
then point it at a channel ID. zmt scans the channel for audio messages and
treats them as a playable library.

### known issues

- **telegram sync may fail for some users** — the bundled `libtdjni.so` native
  library is a minimal stub. for full TDLib functionality, replace it with a
  properly compiled TDLib JNI library built from
  [td/tdlib](https://github.com/tdlib/td) for your target architectures.
- the api credentials (`api_id: 94575`) are the default TDLib test credentials.
  for production use, register your own app at https://my.telegram.org and
  update `TelegramClient.kt`.

### other changes from upstream

- merged upstream fixes: lyrics parser, AMOLED theme, playback stats, ffmpeg
  decoder preference
- CI build pipeline for signed APKs on every push
- updated gradle dependencies

## building

open in android studio and hit run. minSdk 30.

release builds are minified. CI builds a signed release APK on every push.

## stack

kotlin, compose, media3, datastore, tdlib (JNI). no ads, no analytics.
