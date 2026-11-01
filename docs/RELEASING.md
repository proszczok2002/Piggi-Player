# Procedura wydania

1. Zwiększ `versionCode` i ustaw `versionName` w `app/build.gradle.kts`.
2. Uzupełnij `CHANGELOG.md`.
3. Uruchom pełną weryfikację:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleRelease lintRelease
```

4. Zweryfikuj pakiet, minimalny Android i certyfikat za pomocą `aapt2` oraz `apksigner`.
5. Skopiuj podpisany APK do `releases/Piggi-Player-X.Y.Z.apk`.
6. Wygeneruj `releases/piggi-player-update.json` skryptem `scripts/New-UpdateManifest.ps1`. Jeśli lokalna polityka blokuje skrypty, uruchom go przez `powershell.exe -NoProfile -ExecutionPolicy Bypass -File`; zmiana obowiązuje tylko ten proces.
7. Commituj kod i manifest, utwórz tag `vX.Y.Z`, a następnie wypchnij gałąź i tag.
8. Utwórz GitHub Release i dołącz:
   - `Piggi-Player-X.Y.Z.apk`,
   - `piggi-player-update.json`.
9. Pobierz oba assety przez adresy `releases/latest/download` i ponownie porównaj SHA-256.
10. Zainstaluj opublikowany APK na urządzeniu testowym jako aktualizację, bez odinstalowywania poprzedniej wersji.

Nie publikuj release, jeśli build, lint, podpis lub suma kontrolna nie zostały potwierdzone.
