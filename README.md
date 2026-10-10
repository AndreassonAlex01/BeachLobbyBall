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

## Firestore security rules

The rules live in `firestore.rules`. To test them against Firebase's rules test API (nothing is deployed), sign in with `gcloud auth login` and run:

```
python3 tools/test_firestore_rules.py
```
