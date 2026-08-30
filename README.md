# Muzammil's browser

A minimal WebView-based Android browser, written in pure Java.

- Package: `com.muzammil.browser`
- Min SDK: 14 (Android 4.0 Ice Cream Sandwich)
- Target SDK: 34 (Android 14)

## How to build

1. Open this folder in Android Studio (File > Open > select this folder).
2. Let Gradle sync (it will download the wrapper/plugin the first time).
3. Run on a device/emulator, or build an APK via **Build > Build Bundle(s) / APK(s) > Build APK(s)**.

Alternatively, from the command line (with Android SDK + Gradle installed):

```
./gradlew assembleDebug
```

The output APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## What's included

- `AndroidManifest.xml` — Internet permission, cleartext traffic allowed, network security config.
- `activity_main.xml` — URL bar (EditText), "Go" button, progress bar, full-screen WebView.
- `MainActivity.java` — WebView setup with JavaScript + DOM storage enabled, a hardcoded modern Chrome-on-Android user agent, and SSL error bypassing.

## ⚠️ Security warning

This project intentionally **disables HTTPS certificate validation**:

- `WebViewClient.onReceivedSslError()` calls `handler.proceed()` for every SSL error (expired certs, self-signed certs, hostname mismatches, untrusted CAs, etc.), so the WebView will silently load pages with broken certificates instead of warning the user.
- A custom `X509TrustManager` that accepts all certificates is installed as the app's default `SSLContext`, which affects any plain Java networking (`HttpsURLConnection`) done outside the WebView.
- `usesCleartextTraffic="true"` and a permissive `network_security_config.xml` allow plain HTTP traffic as well.

This removes protection against man-in-the-middle attacks and makes the app trust malicious or misconfigured servers. It's useful for things like testing against an internal server with a self-signed/expired certificate, but **it should not be shipped in a production app** that handles sensitive data or is distributed to real users. If you plan to publish this app, remove `onReceivedSslError`'s `handler.proceed()` override (let it call `handler.cancel()` instead, which is the safe default) and remove the custom `TrustManager`.
