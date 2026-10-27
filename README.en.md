# StarPass Router · Android

[Download APK](https://github.com/LONG10222/starpass-router-android/releases/latest) · [中文](README.md) · [Issues](https://github.com/LONG10222/starpass-router-android/issues)

A LAN login client for OpenWrt / iStoreOS LuCI. Requires Android 8.0 or later. The app keeps the original router web interface.

## Start

1. Connect your phone to the router's LAN.
2. Enter **Website address → Username → Password**, then tap **Log in and remember**.
3. Use the original LuCI page in immersive fullscreen. The bottom-right **菜单** menu provides fullscreen switching, refresh, login settings and updates. The next launch remembers the display preference and reuses the saved session.

Fullscreen hides the app toolbar and system bars, and extends through the camera-cutout area. Edge swipes temporarily reveal system navigation. Login settings retain safe insets and keyboard avoidance. Redmi K70 Pro / HyperOS still needs physical-device validation.

## In-app updates

Tap the login-page update button or **菜单 → 检查更新**. The app checks this repository's latest stable GitHub Release, asks before downloading, shows cancellable progress, verifies SHA-256, package identity, increasing version and the installed signing identity, then opens the system installer. The first update may require enabling installation from this app in Android settings. Installation always requires system confirmation.

Update requests go directly to GitHub and never contain router credentials, cookies or the router address. Internet access to GitHub is required. There are no automatic or forced update checks.

Releases must use tags such as `v1.1.0`, an asset named `StarPass-Router-1.1.0.apk`, and a GitHub asset `sha256:` digest. The APK versionName must match the tag, versionCode must increase, and all compatible updates must use the same signing key. Drafts, previews and old versions are ignored.

The original 1.0.0 private signing key cannot be recovered from its APK. A new signing identity cannot replace that installation. Recover the original `.signing/` before publishing compatible updates, or explicitly plan a fresh installation with any necessary user data saved first.

Use your own private IPv4 address, for example `http://192.168.1.1`, or a complete LuCI path such as `http://192.168.1.1/cgi-bin/luci/admin/services/portalproxy`. Custom ports and HTTP/HTTPS are supported.

An active session is reused. If the server returns the login page after expiry, the app submits the saved credentials once. A rejected login returns to settings rather than looping. It does not request logout, change router session timeouts or send keep-alive requests.

## Web interface

The router's pages, menus, mobile user agent and responsive styles are retained. JavaScript dialogs, the system file chooser and same-origin downloads are supported. External links open in the system browser; login cookies are never forwarded to an external download host. Older WebViews get a standard `replaceChildren` fallback without changing page styles.

The profile is encrypted with AES-GCM using Android Keystore and stored in the app's private directory. System backup is disabled. Router credentials and signing keys are not embedded in the APK or repository. HTTP has the same transport properties as the original router site; use HTTPS with a valid certificate if your router supports it.

## Build

No third-party runtime dependencies or proxy core are included. Use Android Studio/Gradle, or build offline with an installed SDK and JDK:

```sh
export ANDROID_HOME=/path/to/android-sdk
export JAVA_HOME=/path/to/jdk
python3 scripts/build.py
python3 scripts/audit.py
```

The offline builder supports Windows, macOS and Linux with `platforms/android-36*`, installed build-tools, and JDK 17 or later. The version comes from `app/build.gradle`; current output is `build/release/StarPass-Router-1.1.0.apk`. The first local build creates a signing identity in `.signing/`; later builds reuse it. `ROUTER_SIGNING_DIR` can select an existing signing directory. Keep this private directory to sign compatible updates. It must never be committed or included in a source archive. Use `--require-existing-key` for releases to prevent accidental signing-identity replacement.

The Gradle project uses AGP 8.13.2, compileSdk 36 and targetSdk 36. The private test runner and generated update fixtures are included only in debug builds. CI runs Android 11/15 emulators as well as the address/origin and login-script tests. Run `python3 scripts/build.py --debug --test-update-fixtures` and then `python3 scripts/emulator_test.py` against a disposable emulator. The test script clears this app's emulator data; do not run it against a phone with a real profile.

The manually dispatched `signed-release` workflow requires repository secrets `STARPASS_KEYSTORE_BASE64` (the original JKS, base64-encoded) and `STARPASS_KEYSTORE_PASSWORD`, using alias `starpass-router`. It restores the existing key, requires a tag matching the source version, uploads a draft, verifies the GitHub asset digest and publishes the stable release. Run it only after device verification; ordinary checks do not publish APKs.

## Validation

- 33 address/origin assertions, 35 update-policy assertions and 7 login-script cases.
- Android 11/15 emulator suite: 1440×3200 fullscreen bounds, rotation, reachable menus, field alignment, rejected-password loop prevention, encrypted profiles and cookie preservation.
- Update tests cover release parsing, download integrity/cancellation, matching and mismatched actual APK signing identities, version mismatch, downgrade rejection and the read-only provider boundary.
- Force-stop/reopen reused the session without another login. Expiring the test server's session caused one automatic login. No logout requests were sent.
- The 1.0.0 baseline had actual iStoreOS/StarPass verification; new vendor-specific behavior still needs device confirmation.

Physical phones, every Android/WebView version and vendor-specific file workflows still need their own validation.

Implementation references: [CookieManager](https://developer.android.com/reference/android/webkit/CookieManager), [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [WebView native interface boundaries](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges). No JavaScript-to-native bridge or TLS certificate bypass is used.

[MIT license](LICENSE).
