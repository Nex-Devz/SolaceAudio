/* Live release data from GitHub. Fallbacks stay in the HTML. */
(function () {
  "use strict";

  const OWNER = "Nex-Devz";
  const REPO = "SolaceAudio";
  const BRANCH = "main";
  const REPO_URL = `https://github.com/${OWNER}/${REPO}`;
  const API_RELEASES = `https://api.github.com/repos/${OWNER}/${REPO}/releases?per_page=12`;
  const API_REPO = `https://api.github.com/repos/${OWNER}/${REPO}`;
  const CHANGELOG_URL = `https://raw.githubusercontent.com/${OWNER}/${REPO}/${BRANCH}/CHANGELOG.md`;
  const CACHE_KEY = "solaceaudio:live:v1";
  const TTL = 10 * 60 * 1000;

  const els = (selector) => Array.from(document.querySelectorAll(selector));
  const setText = (selector, value) => {
    els(selector).forEach((el) => {
      el.textContent = value;
    });
  };

  const escapeHTML = (value) =>
    String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");

  const inline = (value) => {
    let out = escapeHTML(value);
    out = out.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, (_, text, url) => {
      const safe = url.replace(/&amp;/g, "&");
      return `<a href="${escapeHTML(safe)}" rel="noopener">${text}</a>`;
    });
    out = out.replace(/`([^`]+)`/g, "<code>$1</code>");
    out = out.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
    return out;
  };

  const safeUrl = (value) => (/^https:\/\//i.test(value || "") ? value : "#");

  const dateValue = (value) => {
    if (!value) return null;
    const raw = /^\d{4}-\d{2}-\d{2}$/.test(value) ? value + "T00:00:00Z" : value;
    const date = new Date(raw);
    return Number.isNaN(date.getTime()) ? null : date;
  };

  const formatDate = (value) => {
    const date = dateValue(value);
    if (!date) return "Date unknown";
    return new Intl.DateTimeFormat("en-GB", {
      day: "numeric",
      month: "short",
      year: "numeric",
      timeZone: "UTC",
    }).format(date);
  };

  const formatCount = (value) => {
    if (typeof value !== "number") return "—";
    return new Intl.NumberFormat("en-GB", { notation: value >= 10000 ? "compact" : "standard" }).format(value);
  };

  const formatSize = (bytes) => {
    if (typeof bytes !== "number" || bytes <= 0) return null;
    if (bytes >= 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + " MB";
    return Math.round(bytes / 1024) + " KB";
  };

  const relativeTime = (timestamp) => {
    const minutes = Math.max(0, Math.round((Date.now() - timestamp) / 60000));
    if (minutes < 1) return "just now";
    if (minutes < 60) return minutes + " min ago";
    const hours = Math.round(minutes / 60);
    if (hours < 48) return hours + " h ago";
    return Math.round(hours / 24) + " d ago";
  };

  const parseChangelog = (text) => {
    const lines = text.replace(/\r\n/g, "\n").split("\n");
    const releases = [];
    let release = null;
    let group = null;
    let item = null;

    const flushItem = () => {
      if (release && group && item) {
        const trimmed = item.trim();
        if (trimmed) group.items.push(trimmed);
      }
      item = null;
    };

    const flushGroup = () => {
      flushItem();
      if (release && group && group.items.length) release.groups.push(group);
      group = null;
    };

    for (const rawLine of lines) {
      const line = rawLine.trimEnd();
      const heading = line.match(/^##\s+\[?(\d[^\]]*?)\]?(?:\s*-\s*(\d{4}-\d{2}-\d{2}))?\s*$/);

      if (heading) {
        flushGroup();
        release = {
          version: heading[1].trim(),
          date: heading[2] || null,
          groups: [],
        };
        releases.push(release);
        continue;
      }

      if (!release) continue;

      const groupHeading = line.match(/^###\s+(.+?)\s*$/);
      if (groupHeading) {
        flushGroup();
        group = { name: groupHeading[1], items: [] };
        continue;
      }

      const bullet = rawLine.match(/^\s*[-*]\s+(.+)$/);
      if (bullet) {
        flushItem();
        if (!group) group = { name: "Changed", items: [] };
        item = bullet[1];
        continue;
      }

      if (item && /^\s{2,}\S/.test(rawLine)) {
        item += " " + line.trim();
        continue;
      }

      if (!line.trim()) flushItem();
    }

    flushGroup();
    return releases.filter((entry) => entry.groups.length > 0);
  };

  const pickJar = (assets) => {
    if (!Array.isArray(assets)) return null;
    const jar = assets.find((asset) => /\.jar$/i.test(asset.name || ""));
    if (!jar) return null;
    return { name: jar.name, size: jar.size, downloadUrl: jar.browser_download_url };
  };

  const apiReleaseNote = (release) => {
    const body = (release.body || "").replace(/\r\n/g, "\n").trim();
    const bullets = body
      .split("\n")
      .map((line) => line.match(/^\s*[-*]\s+(.+)$/))
      .filter(Boolean)
      .map((match) => match[1]);

    if (bullets.length) {
      return [{ name: "Release", items: bullets }];
    }

    return [{ name: "Release", items: ["Release notes are published on GitHub."] }];
  };

  const mergeReleases = (changelog, apiReleases) => {
    const apiByTag = new Map();
    (apiReleases || []).forEach((release) => {
      const tag = String(release.tag_name || "").replace(/^v/i, "");
      if (tag) apiByTag.set(tag, release);
    });

    if (changelog && changelog.length) {
      return changelog.map((entry) => {
        const meta = apiByTag.get(entry.version);
        const asset = pickJar(meta && meta.assets);
        return {
          version: entry.version,
          date: entry.date || (meta && meta.published_at) || null,
          publishedAt: (meta && meta.published_at) || null,
          groups: entry.groups,
          url: safeUrl((meta && meta.html_url) || `${REPO_URL}/releases/tag/${encodeURIComponent(entry.version)}`),
          asset,
          prerelease: Boolean(meta && meta.prerelease),
        };
      });
    }

    return (apiReleases || [])
      .map((release) => ({
        version: String(release.tag_name || "").replace(/^v/i, ""),
        date: release.published_at || null,
        publishedAt: release.published_at || null,
        groups: apiReleaseNote(release),
        url: safeUrl(release.html_url || REPO_URL + "/releases"),
        asset: pickJar(release.assets),
        prerelease: Boolean(release.prerelease),
      }))
      .filter((release) => release.version)
      .sort((a, b) => new Date(b.publishedAt || 0) - new Date(a.publishedAt || 0));
  };

  const readCache = () => {
    try {
      const raw = window.localStorage.getItem(CACHE_KEY);
      if (!raw) return null;
      const data = JSON.parse(raw);
      if (!data || !Array.isArray(data.releases) || !data.releases.length) return null;
      return data;
    } catch (error) {
      return null;
    }
  };

  const writeCache = (data) => {
    try {
      window.localStorage.setItem(CACHE_KEY, JSON.stringify(data));
    } catch (error) {
      /* storage full or blocked; live data still renders */
    }
  };

  const fetchText = async (url, options) => {
    const controller = new AbortController();
    const timer = window.setTimeout(() => controller.abort(), 9000);
    try {
      const response = await fetch(url, {
        ...options,
        cache: "no-cache",
        signal: controller.signal,
      });
      if (!response.ok) throw new Error("HTTP " + response.status);
      return await response.text();
    } finally {
      window.clearTimeout(timer);
    }
  };

  const fetchJSON = async (url) => {
    const text = await fetchText(url, {
      headers: { Accept: "application/vnd.github+json" },
    });
    return JSON.parse(text);
  };

  const fallbackVersion = () => {
    const node = document.querySelector("[data-live-version]");
    return (node && node.textContent.trim()) || "1.0.34";
  };

  const updateCopyTemplates = (version) => {
    els("[data-copy-template]").forEach((button) => {
      button.dataset.copy = button.dataset.copyTemplate.replace(/\{version\}/g, version);
    });
  };

  const setMode = (mode, savedAt) => {
    const labels = {
      live: "Live from GitHub",
      cache: "Synced from GitHub",
      syncing: "Checking GitHub",
      offline: "Bundled notes",
    };

    setText("[data-live-status]", labels[mode] || labels.offline);

    els("[data-live-dot]").forEach((dot) => {
      dot.dataset.state = mode === "live" ? "live" : mode === "syncing" ? "syncing" : "cached";
    });

    els("[data-live-synced]").forEach((node) => {
      if (mode === "live") node.textContent = "Checked " + relativeTime(Date.now());
      else if (mode === "cache" && savedAt) node.textContent = "Synced " + relativeTime(savedAt);
      else if (mode === "syncing") node.textContent = "Requesting the latest release…";
      else node.textContent = "GitHub was unreachable; showing bundled notes.";
    });

    els("[data-live-error]").forEach((node) => {
      node.hidden = mode !== "offline";
    });
  };

  const renderPreview = (releases) => {
    const container = document.querySelector("[data-live-preview]");
    if (!container || !releases.length) return;

    const items = [];
    releases.slice(0, 2).forEach((release) => {
      release.groups.forEach((group) => {
        group.items.forEach((text) => {
          if (items.length < 4) items.push({ version: release.version, text });
        });
      });
    });

    container.innerHTML =
      '<p class="release-preview__label label">Latest changes</p>' +
      '<ul class="release-notes">' +
      items
        .map(
          (item) =>
            `<li><span class="release-tag">${escapeHTML(item.version)}</span><span>${inline(item.text)}</span></li>`
        )
        .join("") +
      "</ul>";
  };

  const groupSlug = (name) => {
    const value = String(name || "").toLowerCase();
    if (value.startsWith("add")) return "added";
    if (value.startsWith("fix")) return "fixed";
    if (value.startsWith("chang")) return "changed";
    if (value.startsWith("remov")) return "removed";
    if (value.startsWith("deprecat")) return "deprecated";
    if (value.startsWith("secur")) return "security";
    return "other";
  };

  const plural = (count, word) => `${count} ${word}${count === 1 ? "" : "s"}`;

  const renderChangelog = (releases) => {
    const container = document.querySelector("[data-live-changelog]");
    if (!container || !releases.length) return;

    container.innerHTML = releases
      .slice(0, 14)
      .map((release, index) => {
        const slug = release.version.replace(/[^0-9a-z]+/gi, "-").toLowerCase();
        const total = release.groups.reduce((sum, group) => sum + group.items.length, 0);
        const asset = release.asset
          ? `<a class="chip-asset" href="${escapeHTML(release.asset.downloadUrl)}" rel="noopener">jar · ${escapeHTML(
              formatSize(release.asset.size) || "download"
            )}</a>`
          : "";

        const groups = release.groups
          .map(
            (group) => `<section class="release-group release-group--${groupSlug(group.name)}">
              <h3><span class="release-group__dot" aria-hidden="true"></span>${escapeHTML(
                group.name
              )}<span class="release-group__count">${group.items.length}</span></h3>
              <ul>${group.items.map((item) => `<li>${inline(item)}</li>`).join("")}</ul>
            </section>`
          )
          .join("");

        return `<article class="release-entry" id="v${escapeHTML(slug)}">
          <header class="release-entry__head">
            <div class="release-entry__id">
              <h2>
                <a class="release-entry__anchor" href="#v${escapeHTML(slug)}" aria-label="Permalink to SolaceAudio ${escapeHTML(
          release.version
        )}">${escapeHTML(release.version)}</a>
                ${index === 0 ? '<span class="badge-latest">Latest</span>' : ""}
              </h2>
              <p class="release-entry__meta">
                <span>${escapeHTML(formatDate(release.date))}</span>
                <span class="meta-sep" aria-hidden="true"></span>
                <span>${escapeHTML(plural(total || 1, "change"))}</span>
                ${
                  release.prerelease
                    ? '<span class="meta-sep" aria-hidden="true"></span><span>Pre-release</span>'
                    : ""
                }
              </p>
            </div>
            <div class="release-entry__actions">
              ${asset}
              <a class="link-arrow" href="${escapeHTML(release.url)}" rel="noopener">Release on GitHub
                <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" aria-hidden="true"><path d="M4 12 12 4M6 4h6v6"/></svg>
              </a>
            </div>
          </header>
          <div class="release-entry__body">${
            groups || '<p class="release-empty">No notes were attached to this release.</p>'
          }</div>
        </article>`;
      })
      .join("");
  };

  const renderReleaseIndex = (releases) => {
    const container = document.querySelector("[data-live-index]");
    if (!container || !releases.length) return;

    container.innerHTML = releases
      .slice(0, 8)
      .map((release, index) => {
        const slug = release.version.replace(/[^0-9a-z]+/gi, "-").toLowerCase();
        return `<li><a href="#v${escapeHTML(slug)}">
          <span>${escapeHTML(release.version)}</span>
          <em>${index === 0 ? "Latest" : escapeHTML(formatDate(release.date))}</em>
        </a></li>`;
      })
      .join("");
  };

  const renderRepo = (repo) => {
    if (!repo) return;
    const block = document.querySelector("[data-live-repo]");
    if (!block) return;

    block.hidden = false;
    setText("[data-repo-stars]", formatCount(repo.stargazers_count));
    setText("[data-repo-forks]", formatCount(repo.forks_count));
    setText("[data-repo-issues]", formatCount(repo.open_issues_count));
    setText("[data-repo-pushed]", repo.pushed_at ? formatDate(repo.pushed_at) : "—");
  };

  const applyData = (data, mode) => {
    const latest = data.releases[0];
    if (!latest) return;

    setText("[data-live-version]", latest.version);
    setText("[data-live-date]", formatDate(latest.date));
    updateCopyTemplates(latest.version);

    const size = latest.asset ? formatSize(latest.asset.size) : null;
    setText("[data-live-jar-size]", size || "release");

    const download = document.querySelector("[data-live-download]");
    if (download) {
      download.href = latest.asset ? latest.asset.downloadUrl : latest.url;
      download.setAttribute(
        "aria-label",
        latest.asset ? `Download SolaceAudio ${latest.version} jar` : `Open SolaceAudio ${latest.version} release`
      );
    }
    setText("[data-live-download-label]", latest.asset ? "Download jar" : "Open release");

    els("[data-live-release-link]").forEach((link) => {
      link.href = latest.url;
    });

    els("[data-live-version-badge]").forEach((badge) => {
      badge.title = `Latest release: ${latest.version} · ${formatDate(latest.date)}`;
    });

    renderPreview(data.releases);
    renderChangelog(data.releases);
    renderReleaseIndex(data.releases);
    renderRepo(data.repo);
    setMode(mode, data.savedAt);

    document.dispatchEvent(
      new CustomEvent("solaceaudio:updated", {
        detail: { version: latest.version, mode },
      })
    );
  };

  let running = false;

  const refresh = async (force) => {
    if (running) return;
    running = true;
    setMode("syncing");

    const refreshButtons = els("[data-live-refresh]");
    refreshButtons.forEach((button) => {
      button.disabled = true;
      button.dataset.busy = "true";
    });

    const [changelogResult, releasesResult, repoResult] = await Promise.allSettled([
      fetchText(CHANGELOG_URL),
      fetchJSON(API_RELEASES),
      fetchJSON(API_REPO),
    ]);

    let changelog = null;
    let apiReleases = null;
    let repo = null;
    let failures = 0;

    if (changelogResult.status === "fulfilled") {
      try {
        changelog = parseChangelog(changelogResult.value);
      } catch (error) {
        failures += 1;
      }
    } else {
      failures += 1;
    }

    if (releasesResult.status === "fulfilled" && Array.isArray(releasesResult.value)) {
      apiReleases = releasesResult.value;
    } else {
      failures += 1;
    }

    if (repoResult.status === "fulfilled") {
      repo = repoResult.value;
    }

    const cached = readCache();
    const merged = mergeReleases(changelog, apiReleases);

    if (merged.length) {
      const data = {
        savedAt: Date.now(),
        releases: merged,
        repo: repo || (cached && cached.repo) || null,
      };
      writeCache(data);
      applyData(data, "live");
    } else if (cached) {
      applyData(cached, "cache");
    } else {
      setMode("offline");
    }

    if (failures === 3) {
      setMode(cached ? "cache" : "offline", cached ? cached.savedAt : undefined);
      const error = document.querySelector("[data-live-error]");
      if (error) error.hidden = false;
    }

    refreshButtons.forEach((button) => {
      button.disabled = false;
      delete button.dataset.busy;
    });
    running = false;

    if (force) {
      const status = document.querySelector("[data-live-status]");
      if (status) status.textContent = merged.length ? "Live from GitHub" : "Bundled notes";
    }
  };

  const init = () => {
    updateCopyTemplates(fallbackVersion());

    els("[data-live-refresh]").forEach((button) => {
      button.addEventListener("click", () => refresh(true));
    });

    const cached = readCache();
    if (cached) {
      applyData(cached, "cache");
      if (Date.now() - cached.savedAt < TTL) return;
    }

    refresh(false);
  };

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
