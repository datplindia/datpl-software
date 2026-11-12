/* Reception overlays (logo, clock, caption, QR, ticker) shared by the TV player and the admin layout editor.
 * Everything is laid out on a fixed design canvas — 1920×1080 (landscape) or 1080×1920 (portrait) — and the
 * whole canvas is scaled to the real screen, so a value of "100px" means the same thing on every TV. */
(function () {
  const DEFAULTS = {
    landscape: {
      W: 1920, H: 1080,
      logo:    { x: 54,   y: 54,   h: 100 },
      clock:   { x: 1590, y: 54,   fs: 90 },
      caption: { x: 54,   y: 860,  fs: 78 },
      qr:      { x: 1594, y: 620,  s: 240 },
      ticker:  { y: 1004, h: 76,   fs: 46 },
      welcome: { fs: 170 }
    },
    portrait: {
      W: 1080, H: 1920,
      logo:    { x: 54,   y: 54,   h: 100 },
      clock:   { x: 760,  y: 54,   fs: 80 },
      caption: { x: 54,   y: 1660, fs: 72 },
      qr:      { x: 754,  y: 1440, s: 240 },
      ticker:  { y: 1844, h: 76,   fs: 46 },
      welcome: { fs: 140 }
    }
  };
  const WIDGETS = ['logo', 'clock', 'caption', 'qr', 'ticker', 'welcome'];

  // Screen shapes. The short side of the design canvas is always 1080 px, so text sizes mean the same on every shape.
  const SIZES = { '16:9': [16, 9], '16:10': [16, 10], '4:3': [4, 3], '5:4': [5, 4], '21:9': [21, 9], '32:9': [32, 9] };

  const orientOf = s => [90, 270].includes(parseInt((s || {}).rotation, 10) || 0) ? 'portrait' : 'landscape';
  const num = (v, d) => (Number.isFinite(+v) && v !== '' && v !== null ? +v : d);

  // Landscape canvas [W, H] for the chosen screen size
  function canvasOf(s) {
    s = s || {};
    let a = SIZES[s.screenSize];
    if (s.screenSize === 'custom') {
      const cw = num(s.screenW, 0), ch = num(s.screenH, 0);
      if (cw > 0 && ch > 0) a = [Math.max(cw, ch), Math.min(cw, ch)];
    }
    if (!a) a = SIZES['16:9'];
    const W = Math.round(Math.min(4, Math.max(1, a[0] / a[1])) * 1080);
    return [W, 1080];
  }

  // Defaults merged with the saved layout for the current orientation + screen size
  function layoutFor(settings) {
    const s = settings || {}, o = orientOf(s), base = DEFAULTS[o];
    const [cw, ch] = canvasOf(s);
    const W = o === 'portrait' ? ch : cw, H = o === 'portrait' ? cw : ch;
    const std = W === base.W && H === base.H;
    const key = std ? o : `${o}@${W}x${H}`;
    const saved = (s.layout || {})[key] || {};
    const L = { o, W, H, key };
    for (const w of WIDGETS) {
      L[w] = {};
      for (const k of Object.keys(base[w])) {
        let d = base[w][k];
        // on another screen shape keep right/bottom widgets the same distance from the right/bottom edge
        if (!std && k === 'x' && d > base.W / 2) d = W - (base.W - d) - 20;
        if (!std && k === 'y' && d > base.H / 2) d = H - (base.H - d);
        L[w][k] = Math.round(num((saved[w] || {})[k], d));
      }
    }
    return L;
  }

  const CSS = `
  .ovl { position:absolute; left:0; top:0; transform-origin:0 0; pointer-events:none; color:#fff;
         font-family: system-ui, "Noto Sans Devanagari", sans-serif; }
  .ovl .ow { position:absolute; box-sizing:border-box; }
  .ovl .ow[hidden] { display:none !important; }
  .ovl .glass { background:rgba(0,0,0,.45); -webkit-backdrop-filter:blur(6px); backdrop-filter:blur(6px); }
  .ovl .ow-logo { padding:14px 20px; border-radius:16px; }
  .ovl .ow-logo img { display:block; width:auto; }
  .ovl .ow-clock { padding:.14em .3em; border-radius:.2em; text-align:right; white-space:nowrap; }
  .ovl .ow-clock .t { font-size:1em; font-weight:700; line-height:1; font-variant-numeric:tabular-nums; letter-spacing:.02em; }
  .ovl .ow-clock .t small { font-size:.42em; font-weight:600; margin-left:.12em; opacity:.85; }
  .ovl .ow-clock .d { font-size:.38em; opacity:.85; margin-top:.12em; }
  .ovl .ow-caption { padding:.18em .36em .18em .28em; border-radius:.2em; border-left:.11em solid var(--accent,#f97316);
                     transition:opacity .6s ease, transform .6s ease; }
  .ovl .ow-caption.off { opacity:0; transform:translateX(-.3em); }
  .ovl .ow-caption .k { font-size:.3em; text-transform:uppercase; letter-spacing:.18em; opacity:.75; }
  .ovl .ow-caption .n { font-size:1em; font-weight:800; line-height:1.15; }
  .ovl .ow-qr { background:#fff; color:#111; border-radius:16px; padding:14px; text-align:center; box-shadow:0 10px 30px rgba(0,0,0,.35); }
  .ovl .ow-qr canvas { display:block; image-rendering:pixelated; }
  .ovl .ow-qr .l { font-weight:700; line-height:1.25; margin-top:.5em; }
  .ovl .ow-ticker { left:0; background:var(--accent,#f97316); overflow:hidden; display:flex; align-items:center;
                    box-shadow:0 -6px 20px rgba(0,0,0,.3); }
  .ovl .ow-ticker .tr { display:inline-block; white-space:nowrap; font-weight:700; padding-left:100%; animation:ovl-tick linear infinite; }
  @keyframes ovl-tick { from { transform:translateX(0); } to { transform:translateX(-100%); } }`;

  function injectCSS() {
    if (document.getElementById('ovl-css')) return;
    const st = document.createElement('style'); st.id = 'ovl-css'; st.textContent = CSS;
    document.head.appendChild(st);
  }

  // Build the widgets once inside `host`; returns handles used by apply()/setCaption()/tick()
  function mount(host, frame) {
    injectCSS();
    const root = document.createElement('div');
    root.className = 'ovl';
    root.innerHTML = `
      <div class="ow ow-logo glass" data-w="logo" hidden><img alt=""></div>
      <div class="ow ow-clock glass" data-w="clock" hidden><div class="t"></div><div class="d"></div></div>
      <div class="ow ow-caption glass off" data-w="caption"><div class="k"></div><div class="n"></div></div>
      <div class="ow ow-qr" data-w="qr" hidden><canvas></canvas><div class="l"></div></div>
      <div class="ow ow-ticker" data-w="ticker" hidden><span class="tr"></span></div>`;
    host.appendChild(root);
    const q = s => root.querySelector(s);
    return { host, root, logo: q('.ow-logo'), clock: q('.ow-clock'), caption: q('.ow-caption'), qr: q('.ow-qr'), ticker: q('.ow-ticker'),
             qrDrawn: '', tickerText: '', tickerKey: '', L: null, k: 1, frame: frame || null };
  }

  function qrTarget(raw) {
    const v = String(raw || '').trim();
    if (!v) return '';
    const digits = v.replace(/[\s+()-]/g, '');
    if (/^\d{10,13}$/.test(digits)) return 'https://wa.me/' + (digits.length === 10 ? '91' + digits : digits);
    return v;
  }
  function drawQR(h, text) {
    const c = h.qr.querySelector('canvas');
    if (text === h.qrDrawn || !text || typeof qrcode !== 'function') return;
    try {
      const q = qrcode(0, 'M'); q.addData(text, 'Byte'); q.make();
      const n = q.getModuleCount(), sc = 8;
      c.width = c.height = n * sc;
      const g = c.getContext('2d');
      g.fillStyle = '#fff'; g.fillRect(0, 0, c.width, c.height); g.fillStyle = '#111';
      for (let r = 0; r < n; r++) for (let k = 0; k < n; k++) if (q.isDark(r, k)) g.fillRect(k * sc, r * sc, sc, sc);
      h.qrDrawn = text;
    } catch { h.qrDrawn = ''; }
  }

  // Scale the design canvas to fit the host (both width and height), centred.
  // Photos/videos go in h.frame (same box), so the picture is cut/fitted exactly as in the editor.
  function fit(h) {
    const L = h.L; if (!L) return 1;
    const hw = h.host.clientWidth, hh = h.host.clientHeight || hw * L.H / L.W;
    const k = Math.min(hw / L.W, hh / L.H) || 1;
    const ox = (hw - L.W * k) / 2, oy = (hh - L.H * k) / 2;
    h.k = k;
    h.root.style.width = L.W + 'px'; h.root.style.height = L.H + 'px';
    h.root.style.transform = `translate(${ox}px, ${oy}px) scale(${k})`;
    h.ticker.style.left = '0px';
    h.ticker.style.width = L.W + 'px';
    if (h.frame) {
      const f = h.frame.style;
      f.position = 'absolute'; f.overflow = 'hidden';
      f.left = ox + 'px'; f.top = oy + 'px'; f.width = (L.W * k) + 'px'; f.height = (L.H * k) + 'px';
    }
    return k;
  }

  /* opts: { brandUrl(file) -> url, showAll (editor: show widgets even when switched off) } */
  function apply(h, settings, opts = {}) {
    const s = settings || {}, L = layoutFor(s);
    h.L = L;
    const accent = /^#[0-9a-f]{6}$/i.test(s.accent || '') ? s.accent : '#f97316';
    h.root.style.setProperty('--accent', accent);
    h.host.style.setProperty('--accent', accent);
    const place = (el, p) => { el.style.left = p.x + 'px'; el.style.top = p.y + 'px'; };
    const flag = (el, on) => { el.hidden = !on && !opts.showAll; el.classList.toggle('is-off', !on); };

    // logo
    flag(h.logo, !!s.logo);
    place(h.logo, L.logo);
    const img = h.logo.querySelector('img');
    img.style.height = L.logo.h + 'px'; img.style.maxWidth = (L.logo.h * 4) + 'px';
    if (s.logo && opts.brandUrl) { const u = opts.brandUrl(s.logo); if (img.getAttribute('src') !== u) img.src = u; }
    else if (!s.logo) { img.removeAttribute('src'); img.alt = opts.showAll ? 'LOGO' : ''; }

    // clock
    flag(h.clock, s.clock !== false);
    place(h.clock, L.clock); h.clock.style.fontSize = L.clock.fs + 'px';
    tick(h);

    // caption (text is set per photo by setCaption)
    flag(h.caption, s.captions !== false);
    place(h.caption, L.caption); h.caption.style.fontSize = L.caption.fs + 'px';
    h.caption.style.maxWidth = Math.max(200, L.W - L.caption.x - 40) + 'px';

    // QR
    const target = qrTarget(s.qr) || (opts.showAll ? 'https://wa.me/910000000000' : '');
    flag(h.qr, !!qrTarget(s.qr));
    place(h.qr, L.qr);
    const cv = h.qr.querySelector('canvas'); cv.style.width = cv.style.height = L.qr.s + 'px';
    if (target) drawQR(h, target);
    const lab = h.qr.querySelector('.l');
    lab.textContent = s.qrLabel || ''; lab.hidden = !s.qrLabel;
    lab.style.fontSize = Math.round(L.qr.s * 0.105) + 'px'; lab.style.maxWidth = L.qr.s + 'px';

    // ticker
    const tickerOn = s.tickerOn !== false && !!(s.ticker || '').trim();
    flag(h.ticker, tickerOn);
    const tk = h.ticker;
    tk.style.top = L.ticker.y + 'px'; tk.style.width = L.W + 'px'; tk.style.height = L.ticker.h + 'px';
    const tr = tk.querySelector('.tr');
    tr.style.fontSize = L.ticker.fs + 'px';
    const text = (s.ticker || (opts.showAll ? 'Ticker text…' : '')).replace(/\s*\n\s*/g, '     •     ');
    const key = text + '|' + L.ticker.fs + '|' + (s.tickerSpeed || 1);
    if (key !== h.tickerKey) {
      h.tickerKey = key; tr.textContent = text;
      requestAnimationFrame(() => {
        const speed = Math.max(40, (parseFloat(s.tickerSpeed) || 1) * 160);   // design px per second
        tr.style.animationDuration = Math.max(8, tr.scrollWidth / speed) + 's';
      });
    }
    fit(h);
    return L;
  }

  function tick(h) {
    const d = new Date(); let hr = d.getHours(); const ap = hr >= 12 ? 'PM' : 'AM'; hr = hr % 12 || 12;
    h.clock.querySelector('.t').innerHTML = `${hr}:${String(d.getMinutes()).padStart(2, '0')}<small>${ap}</small>`;
    h.clock.querySelector('.d').textContent = d.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' });
  }

  function setCaption(h, text, label, instant) {
    const el = h.caption, n = el.querySelector('.n'), k = el.querySelector('.k');
    if (!text) { el.classList.add('off'); return; }
    if (n.textContent === text && !el.classList.contains('off')) return;
    const put = () => { k.textContent = label || ''; k.hidden = !label; n.textContent = text; el.classList.remove('off'); };
    if (instant) return put();
    el.classList.add('off'); setTimeout(put, 350);
  }

  window.Overlay = { DEFAULTS, SIZES, canvasOf, WIDGETS, orientOf, layoutFor, mount, apply, fit, tick, setCaption, qrTarget };
})();
