const CACHE_NAME = 'lazycent-static-v1';
// Список файлів, які ми хочемо зберегти в кеш при першому завантаженні
const ASSETS_TO_CACHE = [
    '/',
    '/css/app.css',
    '/lib/bootstrap/css/bootstrap.min.css',
    '/lib/bootstrap/js/bootstrap.bundle.min.js',
    '/lib/fontawesome/css/all.min.css',
    '/logo.png',
    '/manifest.json'
];

// 1. Встановлення Service Worker (кешуємо статику)
self.addEventListener('install', (event) => {
    event.waitUntil(
        caches.open(CACHE_NAME).then((cache) => {
            console.log('Відкрито кеш');
            return cache.addAll(ASSETS_TO_CACHE);
        })
    );
    self.skipWaiting();
});

// 2. Активація (видаляємо старі версії кешу)
self.addEventListener('activate', (event) => {
    event.waitUntil(
        caches.keys().then((cacheNames) => {
            return Promise.all(
                cacheNames.map((cacheName) => {
                    if (cacheName !== CACHE_NAME) {
                        console.log('Видалено старий кеш:', cacheName);
                        return caches.delete(cacheName);
                    }
                })
            );
        })
    );
    self.clients.claim();
});

// 3. Перехоплення запитів (Fetch)
self.addEventListener('fetch', (event) => {
    // Не кешуємо запити, які змінюють дані (POST, PUT, DELETE) або ведуть на інші домени
    if (event.request.method !== 'GET' || !event.request.url.startsWith(self.location.origin)) {
        return;
    }

    // Стратегія: Cache First, falling back to network
    event.respondWith(
        caches.match(event.request).then((cachedResponse) => {
            if (cachedResponse) {
                return cachedResponse; // Віддаємо з кешу
            }
            // Якщо немає в кеші — йдемо в інтернет
            return fetch(event.request).then((networkResponse) => {
                // Можна додати динамічне кешування для зображень, але для початку достатньо і цього
                return networkResponse;
            });
        }).catch(() => {
            // Якщо інтернету немає і файлу немає в кеші (наприклад, запит HTML сторінки)
            // Можна повернути кастомну offline-сторінку, якщо вона є
            // return caches.match('/offline.html');
        })
    );
});