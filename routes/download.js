/**
 * Download route — APK file download for Android app.
 * Owns: GET /api/download/android (streams APK with proper headers).
 * Does NOT own: build process (CI builds and uploads to R2).
 *
 * Render's static CDN blocks .apk file types by default (403).
 * This route bypasses the CDN restriction by serving the file via Express.
 */
const express = require('express');
const router = express.Router();
const path = require('path');
const fs = require('fs');
const { S3Client, GetObjectCommand } = require('@aws-sdk/client-s3');
const { buildLandingContext } = require('../lib/landing-context');

const APK_PATH = path.join(__dirname, '../public/holdoff.apk');

function getR2Client() {
  return new S3Client({
    region: 'auto',
    endpoint: `https://${process.env.R2_ACCOUNT_ID}.r2.cloudflarestorage.com`,
    credentials: {
      accessKeyId: process.env.R2_ACCESS_KEY_ID,
      secretAccessKey: process.env.R2_SECRET_ACCESS_KEY,
    },
  });
}

async function streamFromR2(res) {
  const client = getR2Client();
  const command = new GetObjectCommand({
    Bucket: process.env.R2_BUCKET,
    Key: 'holdoff.apk',
  });
  const r2Res = await client.send(command);
  res.setHeader('Content-Type', 'application/vnd.android.package-archive');
  res.setHeader('Content-Disposition', 'attachment; filename="holdoff.apk"');
  res.setHeader('Content-Length', r2Res.ContentLength);
  res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
  r2Res.Body.pipe(res);
}

router.get('/android', (_req, res) => {
  res.redirect(301, '/android-app.apk');
});

module.exports = router;