<p align="center"><img src="website/assets/img/baziq-calculator-icon.svg" width="96" alt="Baziq Calculator icon"></p>

# Baziq Calculator

**Everyday calculations and currency conversion, made simple.** An Android app by **Baziq Hue**.

- Standard and scientific calculator (trigonometric, inverse and hyperbolic functions, logarithms, roots, powers, factorials, percentages, memory), using decimal arithmetic.
- Multi-currency converter: keep a list of currencies from more than 150, type into any row and the others convert as you type. The base currency is remembered between sessions, and can follow the country you are in (optional, approximate location).
- Exchange rates with the precision of their source, refreshed online (open.er-api.com, falling back to api.frankfurter.dev) and saved for offline use.
- 30 tools in all: unit conversion across 16 kinds of measurement, finance, dates and time zones, health, geometry, statistics, a programmer calculator and more.
- No account, ads or analytics. Backup & Restore in Settings.

Android 8.0 (API 26) or later. Application ID: `com.omnicalc.platform` (the identity of installed copies; it is kept unchanged).

## Download

The APK is published on this repository's **Releases** page as `baziq-calculator.apk`. The latest version is always at:

```text
https://github.com/Jow75/baziq-calculator/releases/latest/download/baziq-calculator.apk
```

It is not on Google Play. To install, open the downloaded file on the phone and allow your browser to install apps if Android asks.

Every official release is signed with the same key. Signing certificate SHA-256:
`22:C1:D2:06:21:9A:B2:EC:16:2E:06:91:22:51:3B:E3:5E:C0:61:8E:E3:DE:CD:63:F3:41:26:83:DC:8B:EA:75`

## Repository layout

| Path | Contents |
| --- | --- |
| `app/` | Android app (Kotlin, Jetpack Compose, Room, DataStore) |
| `app/src/test/` | Unit tests, including Compose UI tests run locally with Robolectric |
| `website/` | Static landing page (no build step); `website/assets/js/site-config.js` holds all release links |
| `scripts/make_download_qr.py` | Generates and verifies the website's download QR code |
| `docs/` | Engineering reports and `release-notes/` |
| `.github/workflows/` | `ci.yml` (tests on every push), `release.yml` (signed release on a version tag) |
| `netlify.toml` | Website hosting settings (publishes `website/`) |

## Building

Requirements: JDK 17 and the Android SDK (compileSdk 34).

```sh
./gradlew testDebugUnitTest      # all unit tests
./gradlew assembleDebug          # debug build
./gradlew assembleRelease        # release build, signed only if the release key is configured
```

Without the release key, `assembleRelease` produces `app-release-unsigned.apk`; a release is never signed with a debug key.

> **Do not run `connectedAndroidTest` on a phone that has the app with real data:** it installs the debug variant and uninstalls it afterwards. The UI tests run as local tests instead.

### Release signing

The release key is never stored in this repository (`.gitignore` excludes keystores and signing files). Gradle looks for a properties file with `storeFile`, `storePassword`, `keyAlias` and `keyPassword` in this order:

1. the `BAZIQ_SIGNING_PROPERTIES` environment variable (used by the release workflow),
2. the `baziq.signingProperties` Gradle property, e.g. in `~/.gradle/gradle.properties`,
3. `keystore.properties` in the project root (ignored by git).

A relative `storeFile` is resolved against the properties file's folder.

## Publishing a release

1. Raise `versionCode` and `versionName` in `app/build.gradle.kts` and add `docs/release-notes/v<version>.md`.
2. Make sure the release secrets exist (once): `BAZIQ_KEYSTORE_BASE64`, `BAZIQ_KEYSTORE_PASSWORD`, `BAZIQ_KEY_ALIAS`, `BAZIQ_KEY_PASSWORD`.
3. Tag and push: `git tag v1.1.4 && git push origin v1.1.4`.

The `Release` workflow runs the tests, builds and signs the APK, checks the application ID, the signing certificate, that it is not debuggable and that the tag matches `versionName`, then publishes `baziq-calculator.apk` with its SHA-256 checksum. Untagged commits are only tested, never published.

Until the secrets are added, the workflow stops with a warning and publishes nothing. A release can then be made from the computer that holds the key: `./gradlew assembleRelease`, check the APK with `apksigner verify --print-certs` (certificate above) and `aapt2 dump badging` (no `application-debuggable`), and attach it to a GitHub Release for the tag as `baziq-calculator.apk` together with its `.sha256` file. 1.1.3 was published this way. The workflow never replaces a release that already exists.

## Website

```sh
python -m http.server 8080 --directory website   # preview at http://localhost:8080
```

All release details and links live in `website/assets/js/site-config.js`. After changing the repository there, run `python scripts/make_download_qr.py` (needs `segno`, `zxing-cpp`, `pillow`) to regenerate the QR code for the stable download link. The script decodes the QR code and refuses to save it unless it contains exactly that link. The stable link does not change between releases, so the QR code does not need regenerating for a new version.

## Privacy

See [`website/privacy.html`](website/privacy.html). In short: data stays on the phone; the app contacts exchange-rate services to refresh rates, and uses approximate location (only if allowed) to find the country through Android's geocoding service, keeping only the country code.

© Baziq Hue
