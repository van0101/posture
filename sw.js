// Cache name carries the app version. Keep in sync with APP_VERSION in app.js
// and CHANGELOG.md (CI checks it). A new name makes phones download the new release.
const VERSION = "posture-v1.1.0";
const SHELL = [
  "./",
  "./index.html",
  "./app.js",
  "./manifest.webmanifest",
  "./icons/icon-192.png",
  "./icons/icon-512.png",
  "./icons/icon-maskable-512.png",
  "./icons/apple-touch-icon.png"
];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

// App files: cache first, then network. Google Fonts: cached the first time they load, so the app works offline.
self.addEventListener("fetch", (e) => {
  if (e.request.method !== "GET") return;
  e.respondWith(
    caches.match(e.request).then((hit) => {
      if (hit) return hit;
      return fetch(e.request).then((res) => {
        const url = new URL(e.request.url);
        const cacheable = url.origin === location.origin ||
          url.hostname === "fonts.googleapis.com" || url.hostname === "fonts.gstatic.com";
        if (cacheable && (res.ok || res.type === "opaque")) {
          const copy = res.clone();
          caches.open(VERSION).then((c) => c.put(e.request, copy));
        }
        return res;
      }).catch(() => (e.request.mode === "navigate" ? caches.match("./index.html") : undefined));
    })
  );
});
