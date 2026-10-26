/*
 * Baziq Calculator website: the single place for release details and links.
 *
 * Every download button, the QR code and the support links are derived from these values
 * by site.js. To point the site at the GitHub repository, set github.owner and github.repo;
 * the download link then becomes
 *   https://github.com/<owner>/<repo>/releases/latest/download/baziq-calculator.apk
 * and stays the same for every later release (each release attaches the same file name).
 * Then run `python scripts/make_download_qr.py` to regenerate and verify the QR code.
 */
window.BAZIQ_SITE = {
  productName: "Baziq Calculator",
  brand: "Baziq Hue",

  version: "1.1.4",
  versionCode: 6,
  releaseDate: "October 2026",
  minAndroid: "Android 8.0 or later",
  apkFileName: "baziq-calculator.apk",
  apkSize: "1.6 MB",
  apkSha256: "7088ac22aa70a577dc9c5f15bb44b90ef887fae989ac3f2664d119ebf84b3b2c",
  // SHA-256 of the release signing certificate; every official APK carries this signature.
  signingCertSha256: "22:C1:D2:06:21:9A:B2:EC:16:2E:06:91:22:51:3B:E3:5E:C0:61:8E:E3:DE:CD:63:F3:41:26:83:DC:8B:EA:75",

  // Nothing links to GitHub while these are empty.
  github: {
    owner: "Jow75",
    repo: "baziq-calculator"
  },

  // Written by scripts/make_download_qr.py: the URL encoded in assets/img/download-qr.svg. The QR
  // code is shown only when it equals the download URL above.
  qrEncodedUrl: "https://github.com/Jow75/baziq-calculator/releases/latest/download/baziq-calculator.apk"
};
