/**
 * SEO landing pages — long-tail anxious-attachment queries.
 * Owns: /spirals + SEO article pages (/should-i-double-text, etc.)
 * Does NOT own: main app routes, auth, filter, checkout.
 *
 * Pages are static HTML files in public/seo/ — no EJS rendering needed.
 */
const express = require('express');
const router = express.Router();
const path = require('path');

const SEO_DIR = path.join(__dirname, '..', 'public', 'seo');

// Maps route → static HTML filename in public/seo/
const seoFiles = {
  '/spirals': 'spirals.html',
  '/should-i-double-text': 'should-i-double-text.html',
  '/what-to-text-when-he-hasnt-replied': 'what-to-text-when-he-hasnt-replied.html',
  '/why-do-i-overtext-when-anxious': 'why-do-i-overtext-when-anxious.html',
  '/should-i-text-him-first': 'should-i-text-him-first.html',
  '/11pm-text-anxious-attachment': '11pm-text-anxious-attachment.html',
  '/why-do-i-keep-double-texting': 'why-do-i-keep-double-texting.html',
  '/anxious-attachment-texting-rules': 'anxious-attachment-texting-rules.html',
  '/he-stopped-texting-back-am-i-being-ignored': 'he-stopped-texting-back-am-i-being-ignored.html',
  '/should-i-text-him-good-morning': 'should-i-text-him-good-morning.html',
  '/why-am-i-obsessing-over-his-last-text': 'why-am-i-obsessing-over-his-last-text.html',
  '/why-does-he-take-hours-to-text-back': 'why-does-he-take-hours-to-text-back.html',
  '/left-on-read-anxiety': 'left-on-read-anxiety.html',
  '/avoidant-shutdown': 'avoidant-shutdown.html',
  '/patterns/avoidant-shutdown': 'avoidant-shutdown.html',
  '/patterns/avoidant-deactivation': 'avoidant-deactivation.html',
};

Object.entries(seoFiles).forEach(([route, filename]) => {
  const filePath = path.join(SEO_DIR, filename);
  router.get(route, (req, res) => {
    res.sendFile(filePath, (err) => {
      if (err) {
        console.error(`[/seo${route}] sendFile error:`, err?.message);
        res.status(500).send('Internal error — see server logs.');
      }
    });
  });
});

module.exports = router;
