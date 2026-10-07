[![](https://img.shields.io/badge/Java-17+-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://java.com)
[![](https://img.shields.io/badge/Lavalink-4.0+-7289DA?style=for-the-badge)](https://github.com/lavalink-devs/Lavalink)
[![](https://img.shields.io/badge/Discord-Join%20Community-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/devz)
[![](https://img.shields.io/badge/License-Apache_2.0-764ba2?style=for-the-badge)](LICENSE)
[![](https://img.shields.io/badge/Sources-6-667eea?style=for-the-badge)](#sources)
[![](https://img.shields.io/badge/Recommendations-Smart-FF6F61?style=for-the-badge)](#recommendation-api)

# SolaceAudio

> [!NOTE]
> High-throughput, zero-throttle multi-source audio engine for Lavalink v4 featuring Spotify, JioSaavn, Gaana, Amazon Music, Pandora, YouTube, and Last.fm with zero rate limits, zero credentials required, and built entirely using Java's native `HttpClient`.
> 
> Need support or have feature suggestions? Join our Discord: **[discord.gg/devz](https://discord.gg/devz)**

## Summary

* [Sources](#sources)
    * [Features](#features)
    * [What is Mirroring?](#what-is-mirroring)
* [Lavalink Usage](#lavalink-usage)
    * [Installation](#installation)
    * [Configuration](#configuration)
* [Recommendation API](#recommendation-api)
* [Supported URLs and Queries](#supported-urls-and-queries)
* [Credits](#credits)
* [License](#license)

---

# Sources

| Source | Features | Playback |
|---|---|---|
| Spotify | tracks, albums, playlists, artists, recommendations | [Mirror](#what-is-mirroring) |
| JioSaavn | songs, albums, playlists, artists, recommendations | Direct Stream (320kbps / MP4) |
| Gaana | songs, albums, playlists, artists | Native Stream (HLS) |
| Amazon Music | tracks, albums, playlists, artists | [Mirror](#what-is-mirroring) |
| Pandora | tracks, albums, playlists, artists, stations | [Mirror](#what-is-mirroring) |
| YouTube | tracks, searches, oEmbed, streams | Direct / [Mirror](#what-is-mirroring) |
| Last.fm | scrobbler recommendations, similar tracks | [Mirror](#what-is-mirroring) |

### Features

- **Mirror System** — ISRC-first resolution with automatic query fallback for mirrored sources.
- **In-Memory LRU Search Cache** — Ultra-fast memory cache resolving repeated search requests in `< 1ms` without querying remote APIs.
- **Automated Disk Quota Management** — Enforces hard disk caps (e.g. 10GB) with oldest-track LRU auto-eviction to prevent host storage saturation.
- **Resilient Spotify Multi-Market Failover Ring** — Automatically cascades across secondary markets (`US` -> `GB` -> `DE` -> `IN`) when tracks are geo-restricted.
- **YouTube Client Rotation** — Rotates between WEB, ANDROID, IOS, TVHTML5, and WEB_EMBEDDED clients with cooldown tracking.
- **PoToken Session Warmer & Pool** — Autonomous visitor session pool with background renewal every 30 minutes.
- **Adaptive Range Streaming** — Throttling mitigation using HTTP Range requests and multi-format audio fallback candidate stepping.
- **ATV Counterpart Track Swapping** — Detects music videos and swaps in clean YouTube Music audio tracks (`MUSIC_VIDEO_TYPE_ATV`) to bypass video skits and intro chatter.
- **Dual-Format Disk Cache & Sidecars** — Verifies cached WebM/M4A audio headers and maintains structured `<videoId>.json` metadata sidecars.
- **Search Autocomplete Endpoint** — Provides real-time search query suggestions via `/v4/solaceaudio/youtube/suggest`.
- **Bot & PoToken Protection** — Soft-fails blocked clients on 429s, 403s, and login/bot challenges, rotating to next available client.
- **Region & Availability Bypass** — Retries unavailable or geo-blocked tracks with alternate region parameters before failing.
- **Spotify Canvas Extraction** — Resolves animated canvas MP4 video URLs for tracks via `spclient.wg.spotify.com/canvaz-cache`.
- **Multi-Seed Recommendations** — Supports `sprec:` queries with multiple track and artist seeds (`seed_tracks=`, `seed_artists=`).
- **Gaana Native Streaming** — Fully persistent HLS chunk buffering directly from Akamai CDN.
- **Native Lyrics** — Built-in integration with LavaLyrics for Spotify color lyrics.
- **Smart Recommendations** — Source-aware recommendation engine using Spotify Radio, YouTube RD Mix, and Last.fm audioscrobbler.
- **Spring Boot 3.2+ Compatible** — Explicit parameter name mappings for Lavalink REST controllers.
- **Zero HTTP Dependencies** — Relies entirely on Java's native `HttpClient` for maximal performance.

> [!IMPORTANT]
> ### What is Mirroring?
>
> Mirroring is the process of taking the metadata resolved from one source and using it to retrieve a playable `AudioTrack` from another provider.

---

# Lavalink Usage

### Installation

#### Option 1: Automatic Download via `application.yml` (Recommended)

Lavalink v4 can automatically download SolaceAudio at server boot! Simply add the plugin definition inside the `lavalink` block in your `application.yml`:

```yaml
lavalink:
  plugins:
    - dependency: "com.github.Nex-Devz:SolaceAudio:v1.0.0"
      repository: "https://jitpack.io"
```

#### Option 2: Manual Download

Download `solaceaudio-plugin.jar` from [Releases](https://github.com/Nex-Devz/SolaceAudio/releases/latest) and place it directly into your Lavalink `plugins` folder:

```
plugins/
  └── solaceaudio-plugin.jar
```

Or build directly from source using Gradle:

```bash
./gradlew clean build -x test
```

The compiled jar will be located at `plugin/build/libs/solaceaudio-plugin-dev.jar`.

---

### Configuration

Add the following block to your `application.yml` file:

```yaml
lavalink:
  plugins:
    - dependency: "com.github.Nex-Devz:SolaceAudio:v1.0.0"
      repository: "https://jitpack.io"

plugins:
  solaceaudio:
    sources:
      spotify: true
      jiosaavn: true
      gaana: true
      amazonmusic: true
      pandora: true
      youtube: true
    spotify:
      market: "US"
      fallbackMarkets: ["GB", "DE", "IN"]
    jiosaavn:
      apiUrl: "https://saavn.dev" # Optional custom JioSaavn API instance
      playlistLoadLimit: 50
    cache:
      maxDiskCacheMb: 10240        # Maximum disk storage cap (in MB)
      maxSearchMemoryEntries: 5000  # Number of in-memory search queries to cache
    lastfm:
      apiKey: ""                   # Optional Last.fm API Key for smart recommendations
```

---

# Recommendation API

SolaceAudio exposes a REST endpoint to query intelligent track recommendations based on the currently playing track context:

```http
GET /v4/sessions/{sessionId}/players/{guildId}/recommendation?limit=10
```

### Parameters

| Name | Type | Description |
|---|---|---|
| `sessionId` | String | Active Lavalink session ID |
| `guildId` | String | Guild / Player ID |
| `track` | String | *(Optional)* Encoded track to recommend from (defaults to currently playing track) |
| `limit` | Integer | *(Optional)* Number of recommendations to return (default: `10`) |

---

# Supported URLs and Queries

### Spotify
- `https://open.spotify.com/track/...`
- `https://open.spotify.com/album/...`
- `https://open.spotify.com/playlist/...`
- `https://open.spotify.com/artist/...`
- `spsearch:query`
- `sprec:seed_tracks=...&seed_artists=...`

### JioSaavn
- `https://www.jiosaavn.com/song/...`
- `https://www.jiosaavn.com/album/...`
- `https://www.jiosaavn.com/featured/...`
- `https://www.jiosaavn.com/artist/...`
- `jssearch:query`
- `jsrec:songId`

### Gaana
- `https://gaana.com/song/...`
- `https://gaana.com/album/...`
- `https://gaana.com/playlist/...`
- `https://gaana.com/artist/...`
- `gnsearch:query`
- `gaanasearch:query`
- `gnrec:seokey`

### Amazon Music
- `https://music.amazon.com/albums/...`
- `https://music.amazon.com/tracks/...`
- `https://music.amazon.com/playlists/...`
- `amsearch:query`

### Pandora
- `https://www.pandora.com/artist/...`
- `https://www.pandora.com/playlist/...`
- `https://www.pandora.com/station/...`

### YouTube
- `https://www.youtube.com/watch?v=...`
- `https://youtu.be/...`
- `https://music.youtube.com/watch?v=...`
- `ytsearch:query`
- `ytmsearch:query`

---

## Credits

- **[lavalink-devs](https://github.com/lavalink-devs/lavalink-plugin-template)** — Official Lavalink plugin template.
- **[SlugYZeon](https://github.com/xylen-py/SlugYZeon)** — Original source implementation and concepts.

---

## License

Licensed under the **Apache License 2.0**.

See [LICENSE](LICENSE) for full details.

---

<div align="center">

<b>Maintained by <a href="https://github.com/nex-devz/solaceaudio">Nex Devz</a></b>

</div>