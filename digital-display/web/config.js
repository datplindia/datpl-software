/* Digital Display — public settings shared by the dashboard, the TV player and its offline worker.
 * The key below is Supabase's *publishable* key: it is meant to be public. Security comes from the
 * database rules (row level security), not from hiding this key. */
self.DN = {
  supabaseUrl: 'https://chpxvjqfwoxtndilmpox.supabase.co',
  supabaseKey: 'sb_publishable_GZvaCNNytfgJnn-2jaoDSg_s9dNzrif',
  bucket: 'dn-media',
  appName: 'Digital Display',
  // Public address of the website (dashboard at /, TV player at /tv/)
  publicUrl: 'https://display.datpl.com/',
  version: '1.0.8'
};
// Public address of an uploaded file (photos, videos, logo)
self.DN.fileUrl = key => `${self.DN.supabaseUrl}/storage/v1/object/public/${self.DN.bucket}/${String(key).split('/').map(encodeURIComponent).join('/')}`;

/* ---------- Customers' own cloud storage (files are never stored by Digital Display) ----------
 * Google: a browser API key (restricted to display.datpl.com + Drive/Picker APIs) and an OAuth client ID
 * for "Connect Google Drive". Both are public by design; Google restricts where they work. */
self.DN.googleApiKey = 'AIzaSyAvweAOX_bF6ceO2BxkaJjQlQkV-qA7j2A';
self.DN.googleClientId = '1006016416257-56fgcijfs8ihckhkadhi6f94egjudtab.apps.googleusercontent.com';
self.DN.googleAppId = '1006016416257';        // Google Cloud project number (for the folder picker)

self.DN.isVideo = function (name, mime) {
  return /^video\//.test(mime || '') || /\.(mp4|m4v|webm|mov|mkv|3gp)(\?|#|$)/i.test(name || '');
};
self.DN.isMedia = function (name, mime) {
  return /^(image|video)\//.test(mime || '') || /\.(jpe?g|png|gif|webp|bmp|svg|mp4|m4v|webm|mov|mkv|3gp)(\?|#|$)/i.test(name || '');
};
self.DN.driveUrl = function (id) {
  return 'https://www.googleapis.com/drive/v3/files/' + encodeURIComponent(id) + '?alt=media&key=' + encodeURIComponent(self.DN.googleApiKey);
};
// Where to load a playlist item from: a Google Drive file, any web link, or (older accounts) our storage
self.DN.mediaUrl = function (it) {
  if (!it) return '';
  if (it.fid) return self.DN.driveUrl(it.fid);
  if (it.url) return it.url;
  return self.DN.fileUrl(it.key || it.path);
};
// A logo / picture setting: "gdrive:<id>", a web link, or an old storage key
self.DN.assetUrl = function (v) {
  v = String(v || '');
  if (!v) return '';
  if (v.indexOf('gdrive:') === 0) return self.DN.driveUrl(v.slice(7));
  if (/^https?:\/\//i.test(v)) return v;
  return self.DN.fileUrl(v);
};

// What kind of link did the customer paste?
self.DN.parseLink = function (raw) {
  var s = String(raw || '').trim(), m;
  if (!/^https?:\/\//i.test(s)) return { kind: 'bad' };
  if ((m = /drive\.google\.com\/(?:drive\/(?:u\/\d+\/)?)?folders\/([\w-]{10,})/.exec(s))) return { kind: 'gdrive-folder', id: m[1] };
  if ((m = /drive\.google\.com\/file\/d\/([\w-]{10,})/.exec(s)) || (m = /drive\.google\.com\/(?:open|uc)\?(?:.*&)?id=([\w-]{10,})/.exec(s))) return { kind: 'gdrive-file', id: m[1] };
  if (/(\/\/|\.)dropbox\.com\/(scl\/fo|sh)\//i.test(s)) return { kind: 'dropbox-folder' };
  if (/(\/\/|\.)dropbox\.com\/(scl\/fi|s)\//i.test(s)) {
    var u = s.replace(/([?&])dl=[01]/, '$1raw=1');
    if (!/[?&]raw=1/.test(u)) u += (u.indexOf('?') >= 0 ? '&' : '?') + 'raw=1';
    var nm = decodeURIComponent((/\/([^/?#]+)(?:\?|#|$)/.exec(s) || [])[1] || 'Dropbox file');
    return { kind: 'url', url: u, name: nm, type: self.DN.isVideo(nm) ? 'video' : 'image' };
  }
  if (/(\/\/|\.)(1drv\.ms|onedrive\.live\.com|sharepoint\.com)/i.test(s)) return { kind: 'onedrive' };
  var name = decodeURIComponent((/\/([^/?#]+)(?:\?|#|$)/.exec(s.replace(/^https?:\/\/[^/]+/, '')) || [])[1] || s.replace(/^https?:\/\//, ''));
  return { kind: 'url', url: s, name: name, type: self.DN.isVideo(name) ? 'video' : 'image' };
};

// Photos and videos in a shared Google Drive folder (and up to 2 levels of sub-folders)
self.DN.listDrive = async function (folderId, depth, prefix) {
  depth = depth === undefined ? 2 : depth; prefix = prefix || '';
  var out = [], token = '';
  do {
    var q = encodeURIComponent("'" + folderId + "' in parents and trashed=false");
    var r = await fetch('https://www.googleapis.com/drive/v3/files?q=' + q +
      '&fields=nextPageToken,files(id,name,mimeType,size,modifiedTime)&pageSize=1000&orderBy=name' +
      '&supportsAllDrives=true&includeItemsFromAllDrives=true&key=' + encodeURIComponent(self.DN.googleApiKey) +
      (token ? '&pageToken=' + encodeURIComponent(token) : ''));
    if (!r.ok) { var e = new Error('drive ' + r.status); e.status = r.status; throw e; }
    var j = await r.json();
    for (var i = 0; i < (j.files || []).length; i++) {
      var f = j.files[i];
      if (f.mimeType === 'application/vnd.google-apps.folder') {
        if (depth > 0) out = out.concat(await self.DN.listDrive(f.id, depth - 1, prefix + f.name + '/'));
      } else if (self.DN.isMedia(f.name, f.mimeType)) {
        out.push({ fid: f.id, path: prefix + f.name, type: self.DN.isVideo(f.name, f.mimeType) ? 'video' : 'image',
                   size: +f.size || 0, mtime: f.modifiedTime || '' });
      }
    }
    token = j.nextPageToken || '';
  } while (token);
  return out;
};
self.DN.driveInfo = async function (id) {
  var r = await fetch('https://www.googleapis.com/drive/v3/files/' + encodeURIComponent(id) +
    '?fields=id,name,mimeType,size&supportsAllDrives=true&key=' + encodeURIComponent(self.DN.googleApiKey));
  if (!r.ok) { var e = new Error('drive ' + r.status); e.status = r.status; throw e; }
  return r.json();
};
