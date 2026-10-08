<div align="center">

# SolaceAudio

### Resilient, Multi-Source Audio Resolution & Playback for Lavalink v4

[![Java](https://img.shields.io/badge/Java-17%2B-informational?style=flat-square&logo=openjdk&logoColor=white)](https://adoptium.net)
[![Lavalink](https://img.shields.io/badge/Lavalink-v4.0%2B-blueviolet?style=flat-square)](https://github.com/lavalink-devs/Lavalink)
[![Release](https://img.shields.io/badge/Release-v1.0.6-success?style=flat-square)](https://github.com/Nex-Devz/SolaceAudio/releases/tag/v1.0.6)
[![JitPack](https://img.shields.io/badge/JitPack-v1.0.6-blue?style=flat-square)](https://jitpack.io/#Nex-Devz/SolaceAudio)
[![License](https://img.shields.io/badge/License-Apache_2.0-lightgrey?style=flat-square)](LICENSE)

<p align="center">
  A production-ready Lavalink v4 plugin providing high-fidelity streaming and metadata resolution across Spotify, JioSaavn, Gaana, Deezer, Amazon Music, Pandora, YouTube, and FloweryTTS. Built with native async I/O, candidate scoring, single-flight request coalescing, and autonomous client rotation.
</p>

[Quick Start](#-quick-start) • [Capabilities](#-capabilities) • [Configuration](#-configuration) • [PoToken Support](#-potoken-support) • [Resolution Engine](#-resolution-engine)

---

</div>

## Overview

SolaceAudio is engineered to resolve common audio playback bottlenecks in production Discord bots:

* **Request Coalescing (Single-Flight)** — Eliminates rate-limit spikes by coalescing concurrent duplicate searches into a single upstream call.
* **Deterministic Candidate Scoring** — Compares audio metadata, duration deltas, and tags to reject acoustic covers, live bootlegs, and noisy music video edits during mirror playback.
* **Multi-Source Failover** — Automatically falls back across configured providers (`Deezer -> YouTube -> SoundCloud -> Bandcamp`) if an upstream source is blocked or returns no matches.
* **Autonomous Anti-Ban Routing** — Rotates between Android VR (Quest 2 unthrottled direct stream), Android Music, and web clients with automatic cooldown backoff on HTTP 403 / 429 responses.
* **Zero Infrastructure Overhead** — Runs completely in-process within your Lavalink node with crash-proof atomic caching (`.part` file swaps). No external databases or microservices required.

---

## Supported Sources

| Source | Playback Mode | Quality | Authentication |
| :--- | :---: | :---: | :---: |
| **Spotify** | ISRC / Metadata Mirror | 320kbps / 160kbps | None Required |
| **JioSaavn** | Native Direct Stream | Up to 320kbps MP4 | None Required |
| **Gaana** | Native Chunked HLS | Native CDN Stream | None Required |
| **Deezer** | Direct / ISRC Audio | Direct MP3 Stream | None Required |
| **YouTube** | Standalone / Enhanced | Opus / AAC Stream | Optional PoToken |
| **Amazon Music** | Metadata Mirror | High-Fidelity Mirror | None Required |
| **Pandora** | Direct / Mirror | Adaptive Stream | None Required |
| **FloweryTTS** | Direct Voice Synthesis | Pristine Audio Output | None Required |
| **Last.fm** | Smart Recommendation Engine | Metadata Provider | Optional API Key |

---

## Quick Start

### 1. Add SolaceAudio to Lavalink

Add the plugin to your Lavalink `application.yml`:

```yaml
lavalink:
  plugins:
    - dependency: "com.github.Nex-Devz.SolaceAudio:solaceaudio-plugin:1.0.13"
      repository: "https://jitpack.io"
```

### 2. Configure Plugin Defaults

```yaml
plugins:
  solaceaudio:
    sources:
      spotify: true
      jiosaavn: true
      gaana: true
      deezer: true
      youtube: true
      amazonmusic: true
      pandora: true
      flowerytts: true

    # Mirror fallback sequence (fully customizable / toggleable)
    providers:
      - "dzisrc:{isrc}"        # 1. Exact ISRC Deezer studio match
      - "ytsearch:\"{isrc}\""  # 2. Exact ISRC YouTube search
      - "ytsearch:{query}"     # 3. YouTube title + artist search
      - "scsearch:{query}"     # 4. SoundCloud fallback
      - "bcsearch:{query}"     # 5. Bandcamp fallback

    youtube:
      localDiskCache: true
      diskCachePath: "youtube-cache"
      maxDiskCacheMb: 10240
      cipherUrl: "https://cipher.kikkia.dev"
      # Option 1: Burner cookie file (recommended for datacenter IPs - bypasses PoToken requirements)
      cookieFile: "ytburner.txt"
      # Option 2: Free, hosted 24/7 PoToken auto-rotator:
      potokenUrl: "https://potoken-generator.vercel.app/token"

    spotify:
      countryCode: "US"
      playlistLoadLimit: 6
      albumLoadLimit: 6
      resolveArtistsInSearch: true

    flowerytts:
      voice: "en-US-Standard-A"
      speed: 1.0
```

---

## 🔥 Burner Account Cookie Support (`cookieFile`)

If your Lavalink node runs on datacenter IPs (Hetzner, OVH, DigitalOcean, Pterodactyl), provide your burner Google account cookies in `ytburner.txt` or configure `cookieFile: "ytburner.txt"`. 

SolaceAudio dynamically computes real-time `SAPISIDHASH` authentication headers and signs every request to `WEB_REMIX`, providing direct stream extraction on cloud hosts without bot challenges or PoToken requirements.

---

## 🛡️ PoToken Support (YouTube Anti-Bot)

SolaceAudio natively consumes auto-rotated Proof-of-Origin Tokens (PoToken) to bypass YouTube bot challenges and 403 Forbidden errors on datacenter IPs.

### Free Hosted Generator
You can use the official free generator right away in your `application.yml`:
```yaml
potokenUrl: "https://potoken-generator.vercel.app/token"
```

### Self-Hosting (Optional)
If you prefer running your own private token generator, deploy our 1-click serverless template on Vercel:

[![Deploy with Vercel](https://vercel.com/button)](https://vercel.com/new/clone?repository-url=https://github.com/Nex-Devz/potoken-generator)

Source repository: [Nex-Devz/potoken-generator](https://github.com/Nex-Devz/potoken-generator)

---

## 🔍 Resolution & Scoring Engine

When querying mirror tracks (e.g., Spotify, Apple Music, or Amazon Music), SolaceAudio uses a deterministic scoring pipeline rather than blindly selecting the first search result:

1. **Title & Artist Normalization**: Strips audio noise markers like `(Official Video)`, `[Remastered]`, `ft.`, `4K`, and punctuation differences.
2. **Duration Delta Validation**: Matches against target track length. Results within $\pm 2$ seconds receive maximum score bonuses.
3. **Marker Penalty Filter**: Heavily penalizes terms like `live`, `acoustic`, `cover`, `slowed`, and `karaoke` unless requested in the original track title.

---

## REST Endpoints

### Track Autoplay Recommendations
Get algorithmic autoplay suggestions based on the currently playing track context:

```http
GET /v4/sessions/{sessionId}/players/{guildId}/recommendation?limit=10
```

---

## License

Licensed under the [Apache License, Version 2.0](LICENSE).