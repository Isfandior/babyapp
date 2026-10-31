# Birth & Baby

One Android app for two phones. Each phone picks **Mother** or **Partner** and the two pair with a 6-digit code. The mother's phone times contractions and has the urgent buttons; the partner's phone rings like an alarm (through silent and Do Not Disturb) when something needs him.

## What's in phase 1 (labour)

- Contraction timer that syncs between both phones, with a breathing guide while a contraction is going. Either phone can log.
- NHS rules (`core/`, unit tested):
  - *Time to call*: regular contractions every 5 minutes or more often (editable in Settings to match the midwife's advice).
  - *Call now* (fixed, can't be switched off): any contraction over 2 minutes, or 6 or more in 10 minutes.
- Urgent buttons: waters broke, bleeding, baby moving less, I need you. Each one rings the partner's phone, texts him only if his phone hasn't started ringing within 20 seconds, and tells the mother to call the Delivery Suite. Her screen ticks off Sent, His phone rang and On his way, with a buzz when he answers.
- If the partner hasn't tapped **On my way** within 2 minutes, the mother's phone tells her to call him or the backup person.
- The mother's phone warns if the partner's phone hasn't checked in for 30 minutes.
- Settings on the partner's phone for every number, name, the call rule, taxi app and backup minicab. Defaults are Homerton's published numbers.
- Tracing: every event goes to an on-phone log (Diagnostics), Crashlytics breadcrumbs and crash reports, and the critical alert chain (created → SMS → received → ringing → acknowledged) is written to Firestore so delays can be traced across both phones.

## How it works

- Native Android (Kotlin, Jetpack Compose), minimum Android 8.
- Firebase: anonymous sign-in, Firestore for sync (works offline and catches up), Crashlytics.
- No server: the partner's phone keeps a small always-on listener (the "Listening for labour alerts" notification) so alerts arrive in a second or two over any network.

## One-time setup

1. **Firebase**: in the Firebase console, add an Android app with package name `app.birthbaby`, download `google-services.json`, enable **Authentication > Anonymous**, create a **Firestore** database (London, `europe-west2`), and publish the rules in `firestore.rules`.
2. **GitHub secrets** (repo Settings > Secrets and variables > Actions):
   - `GOOGLE_SERVICES_JSON`: the whole contents of `google-services.json`.
   - `ANDROID_KEYSTORE_B64` and `ANDROID_KEYSTORE_PASSWORD`: the signing key, so updates install over the previous version.
3. Each push builds an APK under the **Android** workflow's artifacts. Download it on each phone and install.
4. On the partner's phone, work through the **Finish setup** card until it disappears. On Samsung also add the app to *Settings > Battery > Background usage limits > Never sleeping apps*.
5. Tap **Test alarm** on the mother's phone with the partner's phone locked and on silent.

## Build locally

```
./gradlew :core:test :app:assembleRelease
```
