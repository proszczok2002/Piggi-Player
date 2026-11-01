# Piggi Player

[![Android CI](https://github.com/proszczok2002/Piggi-Player/actions/workflows/android.yml/badge.svg)](https://github.com/proszczok2002/Piggi-Player/actions/workflows/android.yml)
[![Latest release](https://img.shields.io/github/v/release/proszczok2002/Piggi-Player?display_name=tag)](https://github.com/proszczok2002/Piggi-Player/releases/latest)

Lokalny odtwarzacz muzyki dla Androida 10 lub nowszego. Piggi Player korzysta z Material 3 i Android Media3, działa bez konta, reklam, telemetrii oraz usług streamingowych.

## Pobieranie

Gotowy, podpisany APK znajduje się w sekcji [GitHub Releases](https://github.com/proszczok2002/Piggi-Player/releases/latest).

Instalując aktualizację, nie odinstalowuj poprzedniej wersji — podpis wydawniczy pozostaje ten sam, więc Android zachowa bibliotekę, playlisty i ustawienia aplikacji.

## Najważniejsze funkcje

- biblioteka MediaStore: utwory, albumy, wykonawcy i katalogi,
- lokalne playlisty, ulubione, kolejka i wyszukiwanie,
- shuffle, repeat, timer snu i systemowy korektor,
- odtwarzanie w tle, ekran blokady, przyciski zestawu słuchawkowego i Android Auto,
- Chromecast dla lokalnych plików przez tymczasowy serwer wyłącznie w sieci LAN,
- jasny, ciemny i systemowy motyw,
- adaptacyjny układ dla telefonu i tabletu,
- aktualizacje z GitHub Releases z obowiązkową kontrolą SHA-256,
- dostępność dla TalkBack i innych czytników ekranu.

## Aktualizacje

Od wersji 1.1.0 aplikacja pobiera manifest oraz APK bezpośrednio z GitHub Releases. Stabilny punkt wejścia:

```text
https://github.com/proszczok2002/Piggi-Player/releases/latest/download/piggi-player-update.json
```

Każdy pobrany APK jest sprawdzany przy użyciu SHA-256 przed uruchomieniem instalatora. Szczegóły formatu i procesu publikacji znajdują się w [dokumentacji aktualizacji](docs/UPDATES.md).

## Budowanie

Wymagane są JDK 17, Android SDK 37 i Build Tools 37.0.0.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

Pełna instrukcja: [docs/BUILDING.md](docs/BUILDING.md).

## Dokumentacja

- [Budowanie projektu](docs/BUILDING.md)
- [Kanał aktualizacji](docs/UPDATES.md)
- [Procedura wydania](docs/RELEASING.md)
- [Prywatność](PRIVACY.md)
- [Zgłaszanie problemów bezpieczeństwa](SECURITY.md)
- [Historia zmian](CHANGELOG.md)
- [Współpraca przy projekcie](CONTRIBUTING.md)

## Wsparcie

Jeśli podoba Ci się Piggi Player, możesz wesprzeć dalszy rozwój:

- [PayPal](https://paypal.me/proszczok)
- [Revolut](https://revolut.me/r_rosiejka)

Te same odnośniki są dostępne w Ustawieniach aplikacji.

## Licencja

Kod jest publicznie dostępny do wglądu i współpracy. Szczegóły znajdują się w pliku [LICENSE](LICENSE).
