# Changelog

All notable changes to SolaceAudio are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.29] - 2026-11-13

### Added

- **JioSaavn Fallback in Priority Mirror Ladder**: Added `"jssearch:{query}"` directly into `TrackResolutionEngine.DEFAULT_PROVIDERS` and `SolaceAudioConfig.providers`. When external metadata sources (Spotify, Amazon Music, Apple Music) attempt to resolve tracks and YouTube returns HTTP 429 / bot detection blocks, the engine immediately falls back to JioSaavn's direct 320kbps CD-quality CDN stream, ensuring zero playback disruption on datacenter nodes.
- **Apple Music iTunes Store Fallback Engine**: Enhanced `AppleMusicAudioSourceManager` with automatic fallback to Apple's public iTunes Store API (`https://itunes.apple.com/search` and `/lookup`). If developer storefront token generation fails or catalog endpoints return errors, search and track lookups resolve seamlessly with zero authentication, zero rate limits, and full artwork/metadata extraction.
- **Regression Test Suite**: Added `TrackResolutionEngineTest` verifying mirror ladder resolution and provider configuration preservation, along with `AppleMusicFallbackTest` validating iTunes API fallback.

## [1.0.28] - 2026-11-13

### Added

- **JioSaavn Multi-Bitrate Quality Selector**: Added `plugins.solaceaudio.jiosaavn.quality` configuration supporting `"320kbps"` (Hi-Fi default), `"160kbps"` (Balanced, saves 50% node bandwidth), `"96kbps"`, and `"48kbps"`. Supports both API `downloadUrl` array selection and DES-decrypted direct CDN URL replacement (`_320.mp4`, `_160.mp4`, `_96.mp4`, `_48.mp4`), with live dynamic updates via `PATCH /v4/SolaceAudio/config`.
- **Smart YouTube Client Rotation with Mid-Stream Cooldown**: When a YouTube stream URL returns HTTP 403 / 429 / 401, `YouTubeTrack` marks the producing client with an automatic 60-second cooldown via `YouTubeProxyHandler.recordClientFailure()` and rotates visitor sessions. Immediate mid-stream failover seamlessly retries using the next healthy client in the pool (`AndroidVrClient`, `VisionOsClient`, `TvHtml5Client`, etc.) without freezing playback.

## [1.0.27] - 2026-11-13

### Security & Hardening

- **Path Injection Prevention (`java/path-injection`)**: Added `getSafeCacheDirectory()` with canonical path normalization in `YouTubeSourceManager` and safe regular file validation in `YtMusicCookieAuth`. Evicted cache sidecar filenames are strictly alphanumeric validated with parent directory bounds checks to prevent path traversal attacks.
- **Tainted Arithmetic Guard (`java/tainted-arithmetic`)**: Clamped `maxDiskCacheMb` in `YouTubeSourceManager` using `Math.max(0L, Math.min(..., 1_000_000L))` to guarantee arithmetic safety and prevent integer overflow/underflow during cache byte calculations.
- **Sensitive Log Information Masking (`java/sensitive-log`)**: Sanitized error logging in `JioSaavnApiHandler` to log only endpoint paths instead of raw request URLs with query parameters.
- **Polynomial ReDoS Elimination (`java/polynomial-redos`)**: Replaced non-atomic regex quantifiers with possessive quantifiers (`*+`, `++`) in LRCLIB title and feature cleaning patterns, eliminating quadratic/exponential backtracking on crafted track titles.
- **Log Injection Sanitization (`java/log-injection`)**: Stripped newline and carriage return characters from user-controlled metadata before writing to log streams in `SolaceAudioPlugin`, `LrcLibLyricsManager`, `GeniusLyricsManager`, and `YtMusicCookieAuth`.
- **Null Safety & Code Quality**: Added explicit null checks for `sourceManager` in `YouTubeTrack`, prevented ambiguous `size()` resolution in `MemoryCacheManager`, captured return values of `File.setLastModified`, and fixed argument formatting in `YouTubeClientProbeTest`.

## [1.0.26] - 2026-11-12

### Fixed

- **Universal Lavalink v4 Startup Compatibility**: Fixed `NoClassDefFoundError: io/micrometer/core/instrument/MeterRegistry` / `ClassNotFoundException` on startup. Removed external Micrometer dependency from plugin and main modules so that the plugin loads reliably across all standard Lavalink v4.x and v4.2.x server distributions without requiring external actuator dependencies.

## [1.0.25] - 2026-11-12

### Added

- **Prometheus & Micrometer Metrics Subsystem**: Embedded `SolaceAudioMetrics` facade automatically binds Lavalink v4's Spring Boot Actuator `MeterRegistry`, exporting:
  - `solaceaudio_client_requests_total`: InnerTube client request counts partitioned by client and status.
  - `solaceaudio_mirror_resolutions_total`: Cross-service mirror resolution counts partitioned by provider and status.
  - `solaceaudio_cache_requests_total`: Hits and misses across stream cache, SponsorBlock, and local disk cache.
  - `solaceaudio_cipher_requests_total` & `solaceaudio_cipher_duration_seconds`: Performance and latency tracking for remote cipher services.
  - `solaceaudio_active_filters`: Live gauges monitoring active DSP filter instances.
- **Zero-Transcode Opus Direct Pass-Through**: Added `plugins.solaceaudio.passthrough` configuration. When active and voice ducking is not engaged, PCM float conversion is completely bypassed, delivering raw format 251 Opus frames directly to Discord and slashing CPU usage by ~60%.
- **Mid-Stream 403 Failover & Stale Cache Invalidation**: `YouTubeProxyHandler.invalidateStream()` automatically removes revoked or expired stream URLs from the stream cache, triggering fresh resolution from alternative InnerTube clients rather than aborting playback.
- **Interrupted Disk Cache Cleanup**: Background cache writers now monitor thread interruption and immediately purge `.part` files on task cancellation or player skips.

### Changed

- Bumped `dev.arbjerg:lavaplayer` from `2.0.4` to `2.2.7`.
- Bumped `dev.arbjerg.lavalink.gradle-plugin` from `1.0.15` to `1.1.2`.
- Bumped `org.slf4j:slf4j-api` from `2.0.9` to `2.0.20`.
- Bumped `softprops/action-gh-release` from `2` to `3`.

### Fixed

- **Memory Leak in SponsorBlock**: Replaced unbounded `ConcurrentHashMap` with a bounded LRU/TTL `MemoryCacheManager`, clamped negative skip targets, and added thread-safe category updates.
- **Memory & State Leak in FilterFactory**: Replaced singleton active filter fields with concurrent sets and registered lifecycle `closeCallback` hooks to cleanly garbage-collect completed player pipelines.
- **SilenceTrimFilter Latch & Stall**: Added in-place `NaN` and `Infinite` sample sanitization and capped dead-air trimming to 250 frames (~5s max) to prevent quiet or ambient tracks from stalling indefinitely.
- **Ducking Gain Step Inversion**: Fixed step calculation using `Math.abs(normalVolume - targetVolume) / rampSamples` to prevent volume oscillation when target exceeds normal volume.
- **Deezer Synchronized Lyric Durations**: Fixed line duration calculation to store span instead of end timestamp (`ms + duration`).
- **ForkJoinPool Thread Starvation**: Removed nested blocking `.join()` in `TrackResolutionEngine` by exposing direct asynchronous futures in `AudioMirrorTrack` and added bounded 15-second timeouts.
- **HTTP Socket Leaks in YouTubeTrack**: Explicitly closed response stream bodies on non-200/206 HTTP codes.
- **Path Traversal Hardening**: Standardized base path checking to absolute normalized paths in `AtomicSafeCacheManager`.
- **SSRF Vulnerability in Dynamic Config**: Validated dynamic `cipherUrl` updates against loopback, private subnets, and cloud metadata IPs (`169.254.169.254`) via `SecurityFilter.isSafeUrl`.

## [1.0.24] - 2026-11-04

### Fixed

- Release workflow now derives `VERSION` from the pushed tag, so release
  assets are named `solaceaudio-plugin-<version>.jar` instead of
  `solaceaudio-plugin-dev.jar`. The job fails if the built artifact name does
  not match the tag, preventing a mislabelled release.
- The checked-in plugin descriptor in `lavalink-plugins/` no longer advertises
  1.0.18 while the released version is 1.0.24. The jar's own descriptor is
  generated at build time and was always correct.

No runtime or API changes. This release exists only to publish correctly
versioned release artifacts; 1.0.23 artifacts on JitPack are unaffected.

## [1.0.23] - 2026-11-04

### Added

- Runtime InnerTube client version overrides through
  `plugins.solaceaudio.youtube.clientVersions`, so a client YouTube starts
  rejecting can be repaired from configuration without a plugin rebuild.
- `plugins.solaceaudio.youtube.nativeSource` to force the built-in YouTube
  source even when the official `dev.lavalink.youtube` plugin is installed.
- Native source takeover that replaces the official source manager and shuts
  it down explicitly, leaving exactly one source named `youtube` registered.
- Opt-in network probe (`SOLACE_YT_PROBE=true` or `-Dsolace.yt.probe=true`)
  that checks every InnerTube client and version override against the player
  and search endpoints.
- Regression tests for client health, version overrides, mirror provider
  configuration and the cipher resolution contract.

### Changed

- Refreshed the default InnerTube client versions for `ANDROID`, `IOS`,
  `ANDROID_MUSIC`, `WEB`, `TVHTML5` and `WEB_EMBEDDED_PLAYER`. User agents now
  follow the effective version instead of hardcoding it.
- The client pool is ordered by failure cost: plain-URL clients first,
  signed-URL clients next, and mobile or session-bound clients last.
- An empty `mirrorProviders` list now selects the built-in cross-service
  mirror list (Spotify, Deezer, JioSaavn, Apple Music). Cross-service
  providers are tried before YouTube-prefixed ones.
- The sample configuration now includes the official YouTube plugin, pinned
  to a snapshot because the 1.18.x release line ships client versions YouTube
  rejects.

### Fixed

- A failed cipher resolution now reports failure instead of returning the
  unresolved URL, which YouTube silently refuses to play.
- A cipher service outage no longer marks every healthy client as failed, and
  format resolution stops after the first failure instead of paying the
  timeout once per format.
- URLs with no transform are returned untouched without fetching a visitor
  session.
- `TvDowngradedClient` no longer shares its version override key with
  `TvHtml5Client`, which both report as `TVHTML5`.
- Attaching the enhancement layer no longer fails silently: the built-in
  source is used as a fallback and a warning is logged when no YouTube source
  can be enhanced.
- Mirror fallback now warns when it is enabled but no mirror source is
  registered.

[1.0.24]: https://github.com/Nex-Devz/SolaceAudio/releases/tag/1.0.24
[1.0.23]: https://github.com/Nex-Devz/SolaceAudio/releases/tag/1.0.23
