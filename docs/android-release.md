# Android app and GitHub releases

The Android app bundles the Netcon interface and registers a native Capacitor plugin for Android `VpnService` and the Xray core. Its VPN controls run from Capacitor's local app origin; they do not depend on the separately deployed Lovable website. The public directory requires internet access. Gemini recommendations that use server functions are not available in the bundled Android shell. Browser-only use does not create a VPN connection. Review [native-vpn-integration.md](native-vpn-integration.md) for protection limitations and the required real-device acceptance tests.

## Local Android project

Requirements: Node.js 22+, Java 21, Android Studio, and Android SDK platform/build tools.

```sh
npm ci
npm run android:sync
npm run android:open
```

Run `npm run android:assets` after changing the shared icon to regenerate Android launcher and splash images. `npm run android:build` generates a client-side shell and copies its assets into the ignored `android-dist/` directory consumed by Capacitor.

## Configure GitHub releases

Add the following Actions secrets under **Settings > Secrets and variables > Actions**:

- Secret `ANDROID_KEYSTORE_BASE64`: base64-encoded Android release keystore.
- Secret `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- Secret `ANDROID_KEY_ALIAS`: signing key alias.
- Secret `ANDROID_KEY_PASSWORD`: signing key password.

Generate a release keystore once and keep it backed up securely; losing it prevents updates from being signed as the same Android app:

```sh
keytool -genkeypair -v -keystore netcon-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias netcon
base64 -w 0 netcon-release.jks
```

Store the encoded output as `ANDROID_KEYSTORE_BASE64` and never commit the keystore or passwords. Push a version tag such as `v1.0.0` to build, sign, verify, and attach `Netcon.apk` to a GitHub Release:

```sh
git tag v1.0.0
git push origin v1.0.0
```

The release build creates a local SPA shell for Capacitor, bundles the client UI into the APK, and syncs the Android project. The release APK is signed for direct installation. Its version name comes from the tag, and its version code increments with each workflow run. Google Play distribution may require a separately configured Android App Bundle and Play signing.