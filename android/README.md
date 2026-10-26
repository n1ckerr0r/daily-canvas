# Android

The native client lives in `android/` and uses Kotlin + Jetpack Compose.

## Files

- `app/build.gradle.kts` - Android module, dependencies, BuildConfig, and `dailycanvas.api.baseUrl` loading.
- `app/src/main/java/.../ui/` - Compose screens, navigation, and ViewModel.
- `app/src/main/java/.../data/` - repository, DTOs, and API client.
- `gradle/wrapper/` - wrapper files for Android Studio and CLI.
- `local.properties.example` - sample local SDK and backend URL configuration.

## Android Studio Startup Order

1. Start the backend and Postgres first:

```bash
cd backend
docker compose up --build
```

2. Open the `android/` directory in Android Studio as a separate Gradle project.

3. After opening it, create `android/local.properties` from `android/local.properties.example` and set the Android SDK path:

```properties
sdk.dir=/home/<user>/Android/Sdk
dailycanvas.api.baseUrl=http://10.0.2.2:37117/api/v1/
```

4. Wait for Gradle Sync. If Android Studio asks whether to trust the Gradle scripts or wrapper, approve it.

5. Start an emulator. For the Android emulator, the backend URL is:

```text
http://10.0.2.2:37117/api/v1/
```

6. Run the `app` configuration.

## Implementation Order

1. Removed the web mockup from `apps/mobile` because it was not an Android client.
2. Created a new Gradle project in `android/` for Kotlin/Compose.
3. Added navigation, ViewModel, Retrofit, and serialization.
4. Built the screen structure: auth, home, gallery, favorites, settings, and detail.
5. Connected the client to the backend through `dailycanvas.api.baseUrl` instead of a hardcoded URL in source code.
6. Removed the backend dependency on the old frontend preview.
7. Added the wrapper structure and `local.properties` example so the project opens cleanly in Android Studio.

## Note

The debug build has been verified locally on the Pixel 8 Android 17 emulator.
The checked-in Gradle wrapper is the standard wrapper for Gradle 8.14.4; its
network timeout is extended for slower first-time distribution downloads.
