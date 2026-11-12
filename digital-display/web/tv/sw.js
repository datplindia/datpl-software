/* Digital Display TV — offline worker.
 * - Keeps the player page itself, so the TV opens even without internet.
 * - Keeps a copy of every photo/video in the playlist (the page sends the list), and serves videos
 *   from that copy with byte ranges so they can seek and loop offline. */
importScripts('../config.js');

const SHELL = 'dn-shell-v2';
const MEDIA = 'dn-media-v1';
const SHELL_FILES = ['./', './index.html', '../config.js', '../qrcode.js', '../overlay.js'];
const FILES_PREFIX = `${self.DN.supabaseUrl}/storage/v1/object/public/${self.DN.bucket}/`;

self.addEventListener('install', e => {
  e.waitUntil(caches.open(SHELL).then(c => c.addAll(SHELL_FILES)).catch(() => {}).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil((async () => {
    for (const k of await caches.keys()) if (![SHELL, MEDIA].includes(k)) await caches.delete(k);
    await self.clients.claim();
  })());
});

// Page shell: network first (so updates arrive), saved copy when offline
async function shell(req) {
  const c = await caches.open(SHELL);
  try {
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), 6000);
    // fetch by URL: older Chrome (66) throws when a navigation Request is copied with options,
    // which used to leave TVs stuck on their first saved copy of the page
    const r = await fetch(req.url, { signal: ctl.signal, cache: 'no-store', credentials: 'same-origin' });
    clearTimeout(t);
    if (r.ok) c.put(req.url.split('?')[0], r.clone());
    return r;
  } catch {
    const hit = await c.match(req.url.split('?')[0]) || await c.match(new URL('./', self.location).href) || await c.match(new URL('./index.html', self.location).href);
    return hit || Response.error();
  }
}

// Build a 206 answer from a fully cached file
async function ranged(full, range) {
  const buf = await full.blob();          // a Blob from the cache stays on disk; slicing it is cheap
  const size = buf.size;
  const m = /bytes=(\d*)-(\d*)/.exec(range || '');
  let start = m && m[1] ? +m[1] : 0, end = m && m[2] ? +m[2] : size - 1;
  if (m && !m[1] && m[2]) { start = Math.max(0, size - +m[2]); end = size - 1; }   // suffix range
  if (start >= size) return new Response(null, { status: 416, headers: { 'Content-Range': `bytes */${size}` } });
  end = Math.min(end, size - 1);
  return new Response(buf.slice(start, end + 1), {
    status: 206,
    headers: {
      'Content-Type': full.headers.get('Content-Type') || 'application/octet-stream',
      'Content-Range': `bytes ${start}-${end}/${size}`,
      'Content-Length': String(end - start + 1),
      'Accept-Ranges': 'bytes'
    }
  });
}

const inflight = new Map();
function store(url) {
  if (inflight.has(url)) return inflight.get(url);
  const p = (async () => {
    const c = await caches.open(MEDIA);
    if (await c.match(url)) return;
    const r = await fetch(url, { mode: 'cors', credentials: 'omit' });
    if (r.ok && r.status === 200) await c.put(url, r);
  })().catch(() => {}).finally(() => inflight.delete(url));
  inflight.set(url, p);
  return p;
}

async function media(req) {
  const url = req.url.split('#')[0];
  const c = await caches.open(MEDIA);
  const hit = await c.match(url);
  const range = req.headers.get('range');
  if (hit) return range ? ranged(hit, range) : hit;
  // not saved yet: answer from the network now and save a full copy in the background
  store(url);
  try { return await fetch(req); } catch { return Response.error(); }
}

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = req.url;
  if (url.startsWith(FILES_PREFIX)) return e.respondWith(media(req));
  const u = new URL(url);
  if (u.origin === self.location.origin && (req.mode === 'navigate' || SHELL_FILES.some(f => u.pathname === new URL(f, self.location).pathname)))
    return e.respondWith(shell(req));
});

// The page sends the files of the current playlist: download missing ones one by one, drop the rest
let syncing = Promise.resolve();
self.addEventListener('message', e => {
  const d = e.data || {};
  if (d.type !== 'cache' || !Array.isArray(d.urls)) return;
  const want = new Set(d.urls.filter(u => typeof u === 'string' && u.startsWith(FILES_PREFIX)));
  syncing = syncing.then(async () => {
    const c = await caches.open(MEDIA);
    for (const req of await c.keys()) if (!want.has(req.url)) await c.delete(req);
    for (const u of want) await store(u);
  }).catch(() => {});
  if (e.waitUntil) e.waitUntil(syncing);
});
