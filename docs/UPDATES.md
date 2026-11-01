# Kanał aktualizacji GitHub

Od Piggi Player 1.1.0 aplikacja sprawdza:

```text
https://github.com/proszczok2002/Piggi-Player/releases/latest/download/piggi-player-update.json
```

GitHub przekierowuje ten adres do assetu najnowszego wydania. Obok manifestu musi znajdować się APK o nazwie wpisanej w polu `apk`.

## Format manifestu

```json
{
  "versionCode": 17,
  "versionName": "1.1.0",
  "apk": "Piggi-Player-1.1.0.apk",
  "sha256": "64-znakowa-suma-sha256",
  "notes": "Opis zmian widoczny w aplikacji."
}
```

Zasady:

- `versionCode` musi rosnąć przy każdym wydaniu,
- `versionName` powinno odpowiadać tagowi bez prefiksu `v`,
- `apk` musi wskazywać asset tego samego GitHub Release,
- wszystkie adresy muszą używać HTTPS,
- `sha256` musi być sumą dokładnie opublikowanego APK,
- APK musi być podpisany tym samym certyfikatem co wcześniejsze wydania.

Updater odrzuca niepoprawną sumę i nie uruchamia instalatora.

Manifest można wygenerować skryptem:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\New-UpdateManifest.ps1 `
  -ApkPath .\releases\Piggi-Player-1.1.0.apk `
  -VersionCode 17 `
  -VersionName 1.1.0 `
  -Notes "Opis wydania"
```

`ExecutionPolicy Bypass` dotyczy wyłącznie tego pojedynczego procesu PowerShell i nie zmienia ustawień systemowych.
