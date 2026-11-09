const CACHE_VERSION = 'lazycent-static-v2';   // ← bump при зміні ASSETS
const HTML_CACHE    = `${CACHE_VERSION}-html`;
const STATIC_CACHE  = `${CACHE_VERSION}-static`;

/**
 * Список "довгограючих" статичних ресурсів.
 * HTML ('/') сюди НЕ входить — для нього Network First (див. нижче).
 */
const PRECACHE_ASSETS = [
    '/css/app.css',
    '/lib/bootstrap/css/bootstrap.min.css',
    '/lib/bootstrap/js/bootstrap.bundle.min.js',
    '/lib/fontawesome/css/all.min.css',
    '/logo.png',
    '/manifest.json'
];

// ============================================================
// INSTALL — прекешуємо тільки статику
// ============================================================
self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(STATIC_CACHE)
            .then(cache => cache.addAll(PRECACHE_ASSETS))
            .then(() => self.skipWaiting())
    );
});

// ============================================================
// ACTIVATE — чистимо старі версії кешу
// ============================================================
self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys()
            .then(keys => Promise.all(
                keys
                    .filter(key => !key.startsWith(CACHE_VERSION))
                    .map(key => {
                        console.log('[SW] Видаляю старий кеш:', key);
                        return caches.delete(key);
                    })
            ))
            .then(() => self.clients.claim())
    );
});

// ============================================================
// FETCH — стратегія залежно від типу запиту
// ============================================================
self.addEventListener('fetch', (event) => {
    const req = event.request;

    // Не кешуємо: не-GET, non-http(s), крос-домен, range-запити (відео)
    if (req.method !== 'GET') return;
    if (!req.url.startsWith(self.location.origin)) return;
    if (req.headers.has('Range')) return;   // відео/аудіо — погано кешувати

    // 1) HTML-навігація → NETWORK FIRST (свіжий контент, fallback на кеш)
    if (req.mode === 'navigate' || (req.headers.get('Accept') || '').includes('text/html')) {
        event.respondWith(networkFirstHtml(req));
        return;
    }

    // 2) /api/* — NETWORK ONLY (динаміка, не кешуємо)
    if (req.url.includes('/api/')) return;

    // 3) Статика (/css, /js, /lib, /img) → STALE-WHILE-REVALIDATE
    if (/\.(css|js|png|jpg|jpeg|svg|webp|woff2?|ttf)$/i.test(req.url)) {
        event.respondWith(staleWhileRevalidate(req, STATIC_CACHE));
        return;
    }

    // 4) Все інше → просто в мережу
});

// ============================================================
// Стратегія: Network First з fallback на кеш
// ============================================================
async function networkFirstHtml(req) {
    const cache = await caches.open(HTML_CACHE);
    try {
        const networkRes = await fetch(req);
        // Кешуємо успішні 200
        if (networkRes && networkRes.status === 200) {
            cache.put(req, networkRes.clone()).catch(() => {});
        }
        return networkRes;
    } catch (err) {
        // Мережа недоступна → шукаємо в кеші
        const cached = await cache.match(req);
        if (cached) return cached;

        // Fallback на головну (якщо це навігація до підсторінки)
        const rootCached = await cache.match('/');
        if (rootCached) return rootCached;

        // Останній шанс — проста заглушка
        return new Response(
            '<!doctype html><html><head><meta charset="utf-8"><title>Offline</title></head>' +
            '<body style="font-family:system-ui;text-align:center;padding:3rem;">' +
            '<h1>📡 Немає з\'єднання</h1>' +
            '<p>Перевірте інтернет і оновіть сторінку.</p></body></html>',
            { status: 503, headers: { 'Content-Type': 'text/html; charset=utf-8' } }
        );
    }
}

// ============================================================
// Стратегія: Stale-While-Revalidate (для CSS/JS/шрифтів)
// ============================================================
async function staleWhileRevalidate(req, cacheName) {
    const cache = await caches.open(cacheName);
    const cached = await cache.match(req);

    // Паралельно запускаємо оновлення з мережі
    const networkPromise = fetch(req)
        .then(res => {
            if (res && res.status === 200) {
                cache.put(req, res.clone()).catch(() => {});
            }
            return res;
        })
        .catch(() => null);

    // Якщо є кеш — віддаємо його одразу, оновлення йде у фон
    if (cached) {
        networkPromise.catch(() => {});
        return cached;
    }

    // Немає кешу — чекаємо на мережу
    const networkRes = await networkPromise;
    if (networkRes) return networkRes;

    return new Response('', { status: 504, statusText: 'Gateway Timeout' });
}