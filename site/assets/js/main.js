/* SolaceAudio site — interactions, no dependencies. */
(function () {
  "use strict";

  const canIntercept = typeof navigator !== "undefined" && !!navigator.clipboard;

  /* ---------------------------------------------------------------- mobile nav */
  const toggle = document.querySelector("[data-menu-toggle]");
  const mobileNav = document.getElementById("mobile-nav");

  if (toggle && mobileNav) {
    const setOpen = (open) => {
      toggle.setAttribute("aria-expanded", String(open));
      mobileNav.dataset.open = String(open);
      mobileNav.hidden = !open;
      document.body.classList.toggle("nav-open", open);
    };

    setOpen(false);

    toggle.addEventListener("click", () => {
      setOpen(toggle.getAttribute("aria-expanded") !== "true");
    });

    mobileNav.addEventListener("click", (event) => {
      if (event.target.closest("a")) setOpen(false);
    });

    document.addEventListener("keydown", (event) => {
      if (event.key === "Escape" && toggle.getAttribute("aria-expanded") === "true") {
        setOpen(false);
        toggle.focus();
      }
    });

    document.addEventListener("click", (event) => {
      if (
        toggle.getAttribute("aria-expanded") === "true" &&
        !mobileNav.contains(event.target) &&
        !toggle.contains(event.target)
      ) {
        setOpen(false);
      }
    });

    window.addEventListener("resize", () => {
      if (window.innerWidth > 940 && toggle.getAttribute("aria-expanded") === "true") {
        setOpen(false);
      }
    });
  }

  /* ------------------------------------------------------------------- reveal */
  const revealNodes = document.querySelectorAll(".reveal");

  if (revealNodes.length) {
    if (!("IntersectionObserver" in window)) {
      revealNodes.forEach((node) => node.classList.add("is-in"));
    } else {
      document.documentElement.classList.add("has-reveal");

      const observer = new IntersectionObserver(
        (entries) => {
          entries.forEach((entry) => {
            if (!entry.isIntersecting) return;
            entry.target.classList.add("is-in");
            observer.unobserve(entry.target);
          });
        },
        { rootMargin: "0px 0px -8% 0px", threshold: 0.08 }
      );

      revealNodes.forEach((node) => observer.observe(node));
    }
  }

  /* --------------------------------------------------------------------- tabs */
  document.querySelectorAll("[data-tabs]").forEach((group) => {
    const tabs = Array.from(group.querySelectorAll('[role="tab"]'));
    if (!tabs.length) return;

    const panels = tabs
      .map((tab) => document.getElementById(tab.getAttribute("aria-controls")))
      .filter(Boolean);

    const activate = (nextTab, focus) => {
      tabs.forEach((tab) => {
        const selected = tab === nextTab;
        tab.setAttribute("aria-selected", String(selected));
        tab.tabIndex = selected ? 0 : -1;
      });

      panels.forEach((panel) => {
        panel.hidden = panel.id !== nextTab.getAttribute("aria-controls");
      });

      if (focus) nextTab.focus();
    };

    tabs.forEach((tab, index) => {
      tab.addEventListener("click", () => activate(tab, false));

      tab.addEventListener("keydown", (event) => {
        const keys = ["ArrowRight", "ArrowLeft", "Home", "End"];
        if (!keys.includes(event.key)) return;
        event.preventDefault();

        let next = index;
        if (event.key === "ArrowRight") next = (index + 1) % tabs.length;
        if (event.key === "ArrowLeft") next = (index - 1 + tabs.length) % tabs.length;
        if (event.key === "Home") next = 0;
        if (event.key === "End") next = tabs.length - 1;

        activate(tabs[next], true);
      });
    });

    const initiallySelected = tabs.find((tab) => tab.getAttribute("aria-selected") === "true") || tabs[0];
    activate(initiallySelected, false);
  });

  /* --------------------------------------------------------------------- copy */
  const copyIcon =
    '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.3" aria-hidden="true"><rect x="5.5" y="5.5" width="8" height="8" rx="1"/><path d="M10.5 5.5v-2a1 1 0 0 0-1-1h-6a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2"/></svg>';
  const checkIcon =
    '<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.6" aria-hidden="true"><path d="M3 8.5 6.2 11.5 13 4.5"/></svg>';

  const resetCopyButton = (button, label, icon) => {
    window.clearTimeout(Number(button.dataset.timer || 0));
    button.dataset.state = "";
    button.innerHTML = icon + "<span>" + label + "</span>";
  };

  const copyText = async (text) => {
    if (canIntercept) return navigator.clipboard.writeText(text);

    const textarea = document.createElement("textarea");
    textarea.value = text;
    textarea.setAttribute("readonly", "");
    textarea.style.position = "fixed";
    textarea.style.opacity = "0";
    document.body.appendChild(textarea);
    textarea.select();

    try {
      const ok = document.execCommand("copy");
      if (!ok) throw new Error("copy failed");
    } finally {
      textarea.remove();
    }
  };

  document.querySelectorAll("[data-copy]").forEach((button) => {
    const idleLabel = button.dataset.label || "Copy";
    if (button.querySelector("svg")) {
      // static icon present; keep a reference to the idle markup
      button.dataset.idle = button.innerHTML;
    } else {
      button.innerHTML =
        copyIcon + (button.querySelector("span") ? "" : "<span>" + idleLabel + "</span>");
      button.dataset.idle = button.innerHTML;
    }

    button.addEventListener("click", async () => {
      let value = button.dataset.copy;

      if (value === "target") {
        const target = document.querySelector(button.dataset.copyTarget || "");
        value = target ? target.innerText : "";
      }

      if (!value) {
        button.dataset.state = "error";
        button.innerHTML = "<span>Copy failed</span>";
        return;
      }

      try {
        await copyText(value.trim());
        button.dataset.state = "copied";
        button.innerHTML = checkIcon + "<span>Copied</span>";
        button.dataset.timer = String(
          window.setTimeout(() => {
            button.innerHTML = button.dataset.idle;
            button.dataset.state = "";
          }, 2200)
        );
      } catch (error) {
        button.dataset.state = "error";
        button.innerHTML = "<span>Press Ctrl+C</span>";
        window.setTimeout(() => resetCopyButton(button, idleLabel, copyIcon), 2600);
      }
    });
  });

  /* ---------------------------------------------------------------- scrollspy */
  const tocLinks = Array.from(document.querySelectorAll(".toc a[href^='#']"));
  const spySections = tocLinks
    .map((link) => document.querySelector(link.getAttribute("href")))
    .filter(Boolean);

  if (tocLinks.length && spySections.length && "IntersectionObserver" in window) {
    const linkFor = new Map();
    spySections.forEach((section, index) => {
      const link = tocLinks.find((item) => item.getAttribute("href") === "#" + section.id);
      if (link) linkFor.set(section.id, link);
    });

    const setActive = (id) => {
      tocLinks.forEach((link) => {
        const active = link.getAttribute("href") === "#" + id;
        link.classList.toggle("is-active", active);
        if (active) link.setAttribute("aria-current", "true");
        else link.removeAttribute("aria-current");
      });
    };

    const visible = new Map();

    const spy = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) visible.set(entry.target.id, entry.boundingClientRect.top);
          else visible.delete(entry.target.id);
        });

        if (!visible.size) return;
        const first = [...visible.entries()].sort((a, b) => a[1] - b[1])[0];
        setActive(first[0]);
      },
      { rootMargin: "-96px 0px -55% 0px", threshold: 0 }
    );

    spySections.forEach((section) => spy.observe(section));

    // Keep the hash-linked section highlighted when the page loads directly.
    if (location.hash) {
      const id = location.hash.slice(1);
      if (linkFor.has(id)) setActive(id);
    }
  }

  /* -------------------------------------------------- changelog version spy */
  let releaseSpy = null;

  const initReleaseSpy = () => {
    const links = Array.from(document.querySelectorAll(".release-index a[href^='#']"));
    if (!links.length || !("IntersectionObserver" in window)) return;

    const pairs = links
      .map((link) => [link, document.querySelector(link.getAttribute("href"))])
      .filter((pair) => pair[1]);

    if (!pairs.length) return;
    if (releaseSpy) releaseSpy.disconnect();

    const visible = new Map();

    const setActive = (id) => {
      links.forEach((link) => {
        link.classList.toggle("is-active", link.getAttribute("href") === "#" + id);
      });
    };

    releaseSpy = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) visible.set(entry.target.id, entry.boundingClientRect.top);
          else visible.delete(entry.target.id);
        });

        if (!visible.size) {
          // Above the first release nothing intersects the band yet, so keep
          // the newest release highlighted. Below the list the last match stays.
          if (pairs[0][1].getBoundingClientRect().top > 0) setActive(pairs[0][1].id);
          return;
        }

        const first = [...visible.entries()].sort((a, b) => a[1] - b[1])[0][0];
        setActive(first);
      },
      { rootMargin: "-88px 0px -58% 0px", threshold: 0 }
    );

    pairs.forEach((pair) => releaseSpy.observe(pair[1]));
  };

  initReleaseSpy();
  document.addEventListener("solaceaudio:updated", () => {
    window.setTimeout(initReleaseSpy, 80);
  });

  /* ------------------------------------------------------------- scroll bar */
  const progress = document.createElement("div");
  progress.className = "scroll-progress";
  progress.setAttribute("aria-hidden", "true");
  progress.innerHTML = "<span></span>";
  document.body.prepend(progress);

  const progressBar = progress.firstElementChild;
  let progressTicking = false;

  const paintProgress = () => {
    const scrollable = document.documentElement.scrollHeight - window.innerHeight;
    const ratio = scrollable > 0 ? Math.min(1, Math.max(0, window.scrollY / scrollable)) : 0;
    progressBar.style.transform = "scaleX(" + ratio + ")";
    progressTicking = false;
  };

  const requestProgress = () => {
    if (progressTicking) return;
    progressTicking = true;
    window.requestAnimationFrame(paintProgress);
  };

  window.addEventListener("scroll", requestProgress, { passive: true });
  window.addEventListener("resize", requestProgress);
  paintProgress();

  /* --------------------------------------------------- current year (footer) */
  document.querySelectorAll("[data-year]").forEach((node) => {
    node.textContent = String(new Date().getFullYear());
  });
})();
