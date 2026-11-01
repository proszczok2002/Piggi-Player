# Budowanie Piggi Player

## Wymagania

- JDK 17,
- Android SDK Platform 37,
- Android SDK Build Tools 37.0.0,
- Git.

Na Windows używaj `gradlew.bat`, a na Linux/macOS `./gradlew`.

## Build debug

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

APK debug powstaje w:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Build release

Publiczny klon nie zawiera klucza produkcyjnego. Bez lokalnego `keystore.properties` Gradle może zbudować niesygnowany wariant release.

Oficjalny build właściciela korzysta z ignorowanego przez Git pliku:

```properties
storeFile=signing/proszczok-player-release.p12
storePassword=UZUPEŁNIJ_LOKALNIE
keyAlias=proszczok-player
keyPassword=UZUPEŁNIJ_LOKALNIE
```

Polecenie:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleRelease lintRelease
```

Nigdy nie commituj pliku `keystore.properties`, klucza `.p12` ani haseł. Są one wykluczone w `.gitignore`.
