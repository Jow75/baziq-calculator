/* Baziq Calculator website: fills release details and links from site-config.js. */
(function () {
  "use strict";

  var cfg = window.BAZIQ_SITE || {};
  var gh = cfg.github || {};
  var hasRepo = Boolean(gh.owner && gh.repo);
  var repoUrl = hasRepo ? "https://github.com/" + gh.owner + "/" + gh.repo : "";
  var links = {
    download: hasRepo ? repoUrl + "/releases/latest/download/" + cfg.apkFileName : "",
    releases: hasRepo ? repoUrl + "/releases/latest" : "",
    issues: hasRepo ? repoUrl + "/issues" : "",
    repo: repoUrl
  };

  // Text values: <span data-config="version"></span>
  document.querySelectorAll("[data-config]").forEach(function (el) {
    var value = cfg[el.getAttribute("data-config")];
    if (value !== undefined && value !== "") el.textContent = value;
  });

  // Links: <a data-link="download">. Until the repository exists they are shown as unavailable
  // instead of pointing anywhere.
  document.querySelectorAll("[data-link]").forEach(function (el) {
    var url = links[el.getAttribute("data-link")];
    if (url) {
      el.setAttribute("href", url);
      el.removeAttribute("aria-disabled");
    } else {
      el.removeAttribute("href");
      el.setAttribute("aria-disabled", "true");
      el.setAttribute("title", "Available once the GitHub release is published");
      el.classList.add("is-pending");
    }
  });

  document.querySelectorAll("[data-when-repo]").forEach(function (el) {
    el.hidden = !hasRepo;
  });
  document.querySelectorAll("[data-when-no-repo]").forEach(function (el) {
    el.hidden = hasRepo;
  });

  // QR code: shown only when the generated image encodes exactly the configured download URL.
  var qr = document.querySelector("[data-qr]");
  if (qr) {
    var ready = links.download && cfg.qrEncodedUrl === links.download;
    qr.classList.toggle("is-ready", Boolean(ready));
    var img = qr.querySelector("img");
    if (ready && img) {
      img.src = img.getAttribute("data-src");
      img.alt = "QR code linking to " + links.download;
    }
  }

  var shaEl = document.querySelector("[data-sha]");
  if (shaEl) {
    shaEl.textContent = cfg.apkSha256 || "Published with the release";
  }

  // Mobile navigation
  var toggle = document.querySelector(".nav-toggle");
  var nav = document.getElementById("site-nav");
  if (toggle && nav) {
    var setOpen = function (open) {
      toggle.setAttribute("aria-expanded", String(open));
      nav.classList.toggle("is-open", open);
    };
    toggle.addEventListener("click", function () {
      setOpen(toggle.getAttribute("aria-expanded") !== "true");
    });
    nav.querySelectorAll("a").forEach(function (a) {
      a.addEventListener("click", function () { setOpen(false); });
    });
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape") setOpen(false);
    });
  }

  var year = document.querySelector("[data-year]");
  if (year) year.textContent = String(new Date().getFullYear());
})();
