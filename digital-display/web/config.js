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
  version: '1.0.7'
};
// Public address of an uploaded file (photos, videos, logo)
self.DN.fileUrl = key => `${self.DN.supabaseUrl}/storage/v1/object/public/${self.DN.bucket}/${String(key).split('/').map(encodeURIComponent).join('/')}`;
