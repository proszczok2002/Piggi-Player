# Piggi Player

Lokalny odtwarzacz muzyki dla Androida, zaprojektowany w Material 3 ze szczególnym uwzględnieniem telefonów Pixel, wszystkich pozostałych urządzeń z Androidem, tabletów i dostępności TalkBack/CSR/Joshua.

## Najważniejsze funkcje

- biblioteka MediaStore: utwory, albumy, wykonawcy i katalogi,
- lokalne playlisty i ulubione,
- kolejka, wyszukiwanie, shuffle, repeat, timer snu i systemowy korektor,
- odtwarzanie w tle, ekran blokady, zestawy słuchawkowe i Android Auto przez Media3,
- Chromecast dla lokalnych plików przez tymczasowy serwer wyłącznie w sieci LAN,
- jasny, ciemny i systemowy motyw o podwyższonym kontraście,
- adaptacyjny układ telefonu/tabletu,
- aktualizacje z własnego serwera na podstawie podpisanego APK i SHA-256,
- brak kont, reklam, telemetrii i usług streamingowych.

## Budowanie

Projekt wymaga JDK 17+, Android SDK 37 i Build Tools 36.0.0.

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest lintDebug
```

APK debug powstaje w `app/build/outputs/apk/debug/app-debug.apk`.

## Aktualizacje

Format manifestu i instrukcja publikacji znajdują się w katalogu `server-update`. Każdy APK aktualizacyjny musi być podpisany dokładnie tym samym kluczem co pierwsza instalacja. Źródłowy adres pobierania nie jest wyświetlany w interfejsie aplikacji.

## Dostępność

Wszystkie kontrolki ikonowe mają polskie etykiety, cele dotykowe mają co najmniej 48 dp, stan odtwarzania jest ogłaszany jako live region, a suwak podaje czytnikowi aktualny czas i długość nagrania. Gesty okładki mają równoważne nazwane akcje dostępności; żadna funkcja nie wymaga rozpoznawania koloru.
