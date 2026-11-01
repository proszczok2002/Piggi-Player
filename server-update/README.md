# Aktualizacje Piggi Player

Cały kanał aktualizacji znajduje się wyłącznie w katalogu `/Piggi Player/`:

- `/Piggi Player/piggi-player-update.json` — publiczny manifest,
- `/Piggi Player/piggi-player-1.0.15-release.apk` — jedyny i najnowszy APK.

Aktualne wydanie to `1.0.15` (`versionCode 16`), z minimalnym Androidem 10 (`minSdk 29`).

Przy każdym wydaniu zwiększ `versionCode`, ustaw czytelną wartość `versionName`, wpisz opis zmian i SHA-256 dokładnie tego APK. Android dodatkowo wymaga, aby każda aktualizacja była podpisana tym samym kluczem co pierwsza instalacja.

Używaj nowej nazwy APK dla każdego wydania. Stała nazwa może zostać zatrzymana w cache serwera lub CDN i nie zgadzać się z nową sumą SHA-256. Najpierw wgraj APK, zweryfikuj jego publiczne pobranie, a manifest podmień jako ostatni.

Przykład obliczenia sumy w PowerShell:

```powershell
(Get-FileHash -Algorithm SHA256 .\piggi-player-release.apk).Hash.ToLowerInvariant()
```

Manifest musi wskazywać względną nazwę jedynego APK z katalogu `/Piggi Player/` oraz zawierać jego dokładny SHA-256. Po potwierdzeniu nowego wydania przez publiczny HTTPS usuń poprzedni APK, aby na serwerze pozostał tylko najnowszy plik.
