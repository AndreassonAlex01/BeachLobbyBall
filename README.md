# BeachLobbyBall

An app for organizing beach volleyball games through public and private lobbies. Android first, iOS later.

Built with Kotlin and Compose Multiplatform. The backend will be Firebase.

## Run it

Open the folder in Android Studio and run the `composeApp` configuration, or from a terminal:

```
./gradlew :composeApp:installDebug
```

## Tests

```
./gradlew :composeApp:testDebugUnitTest
```

Every push and pull request builds a debug APK on GitHub Actions. You can download it from the run's artifacts.

The latest debug APK of `main` is always at
<https://github.com/AndreassonAlex01/BeachLobbyBall/releases/download/test-build/beachlobbyball-test.apk>,
ready to install on a phone.

## Firestore security rules

The rules live in `firestore.rules`. To test them against Firebase's rules test API (nothing is deployed), sign in with `gcloud auth login` and run:

```
python3 tools/test_firestore_rules.py
```

## Firestore indexes

Account deletion finds a user's chat messages in every lobby with a collection group query, so `senderId` on the `messages` collection group needs a collection group index (ascending). It was added in the Firebase console under Firestore > Indexes > Single field.

## Release builds for Google Play

Every push to `main` also runs the "Release build" workflow, which builds a signed app bundle (`.aab`) to upload to Play. It needs two repository secrets, under Settings > Secrets and variables > Actions:

- `UPLOAD_KEYSTORE_BASE64`: the upload keystore, base64-encoded
- `UPLOAD_KEYSTORE_PASSWORD`: its password (the key alias is `upload`)

Without them the workflow skips the build. Google Play re-signs the app with its own app signing key, so add that key's SHA-1 and SHA-256 (Play Console > Test and release > App integrity) to the Firebase Android app, or Google sign-in won't work in Play builds.

## Website

`site/` holds the pages Google Play links to: the privacy policy and the account deletion page. They're served by Firebase Hosting at <https://beachlobbyball.web.app> (`/privacy` and `/delete-account`). To publish changes, sign in with `gcloud auth login` and run:

```
python3 tools/deploy_site.py
```
