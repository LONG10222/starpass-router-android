# StarPass Router · Android

[Download APK](https://github.com/LONG10222/starpass-router-android/releases/latest) · [中文](README.md) · [Issues](https://github.com/LONG10222/starpass-router-android/issues)

A LAN login client for OpenWrt / iStoreOS LuCI. Requires Android 8.0 or later. The app keeps the original router web interface.

## Start

1. Connect your phone to the router's LAN.
2. Enter **Website address → Username → Password**, then tap **Log in and remember**.
3. Use the normal LuCI page. The next launch reuses the saved profile and session. **Login settings** opens the profile without clearing cookies.

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

The offline builder uses `platforms/android-36.1`, `build-tools/36.1.0`, and JDK 17 or later. Output: `build/release/StarPass-Router-1.0.0.apk`. The first local build creates a signing identity in `.signing/`; later builds reuse it. Keep this private directory to sign compatible updates. It must never be committed or included in a source archive.

The Gradle project uses AGP 8.13.2, compileSdk 36 and targetSdk 36. The private test runner is included only by `python3 scripts/build.py --debug`, not in the release APK. CI runs the address/origin and login-script tests; it does not publish randomly signed builds as release updates.

## Validation

- 33 address/origin assertions and 7 login-script cases.
- Android 11 ARM64 emulator: field order/alignment, portrait/landscape bounds, rejected-password loop prevention, encrypted profile storage and preserving cookies while opening settings.
- Force-stop/reopen reused the session without another login. Expiring the test server's session caused one automatic login. No logout requests were sent.
- An actual iStoreOS login and the original StarPass page, including asynchronous status data, were verified.

Physical phones, every Android/WebView version and vendor-specific file workflows still need their own validation.

Implementation references: [CookieManager](https://developer.android.com/reference/android/webkit/CookieManager), [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [WebView native interface boundaries](https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges). No JavaScript-to-native bridge or TLS certificate bypass is used.

[MIT license](LICENSE).
