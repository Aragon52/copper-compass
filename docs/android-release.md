# Android app and GitHub releases

The Android app is a Capacitor shell around the existing Netcon web interface. It loads the deployed HTTPS app configured for the release, so the web UI and its server-backed features stay consistent with the website. An internet connection is required. The shell does not add a native VPN service; connection protection remains explicitly simulated until the service described in [native-vpn-integration.md](native-vpn-integration.md) exists.

## Local Android project

Requirements: Node.js 22+, Java 21, Android Studio, and Android SDK platform/build tools.

Set `ANDROID_APP_URL` to the HTTPS origin where Netcon is deployed, then run:

```sh
npm ci
npm run android:sync
npm run android:open
```

Run `npm run android:assets` after changing the shared icon to regenerate Android launcher and splash images. The local `android-web/index.html` is only a branded fallback for Capacitor tooling. The release app loads `ANDROID_APP_URL`; it is not an offline copy of the server-rendered site.

## Configure GitHub releases

Add the following repository variable and Actions secrets under **Settings > Secrets and variables > Actions**:

- Variable `ANDROID_APP_URL`: the deployed HTTPS origin, for example `https://netcon.example.com`.
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

The release APK is signed for direct installation. Its version name comes from the tag, and its version code increments with each workflow run. Google Play distribution may require a separately configured Android App Bundle and Play signing.