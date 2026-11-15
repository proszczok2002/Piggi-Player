# Historia zmian

Android rozpoznaje kolejność aktualizacji przez rosnący `versionCode`; numer widoczny użytkownikowi zapisujemy jako `versionName`.

## 1.02 — 2026-11-15

- poprawiono sterowanie przez sesję multimedialną, w tym narzędzia przypisujące zmianę utworów przyciskom głośności,
- zadeklarowano widoczność usług narzędzi sterujących, aby Media3 mogło rozpoznać kontrolery z uprawnieniami nadanymi przez użytkownika,
- polecenia następny/poprzedni poprawnie zgłaszają obsługę i zawijają kolejkę również przy wyłączonym powtarzaniu,
- pełny odtwarzacz ma własne okno dostępności; dodano opisy miniatury, stanów losowania, powtarzania, ulubionych i timera,
- TalkBack otrzymuje czas w polskich słowach i akcje przewijania o 10 sekund; usunięto mylącą instrukcję gestów z odczytu okładki,
- ograniczono automatyczne ogłoszenia miniatury do danych utworu,
- Wstecz zamyka wyszukiwanie; główna zakładka i pełny odtwarzacz zachowują stan po obrocie ekranu,
- dodano testy nawigacji kolejki, odczytu czasu oraz narzędzia audytu urządzenia.

## 1.01 — 2026-11-01

- testowe wydanie potwierdzające działanie aktualizacji przez GitHub Releases,
- bez zmian funkcjonalnych względem 1.1.0.

## 1.1.0 — 2026-11-01

- przeniesiono kanał aktualizacji do GitHub Releases,
- dodano obsługę przekierowań GitHub i kontrolę kodów HTTP,
- zachowano obowiązkową weryfikację SHA-256 pobranego APK,
- dodano testy parsera manifestu aktualizacji,
- dodano w Ustawieniach sekcję „Wsparcie” z PayPal i Revolut,
- przygotowano publiczną dokumentację projektu i automatyczny Android CI.

## 1.0.0 — 2026-11-01

- pierwsze publiczne wydanie Piggi Player na GitHub,
- stabilna baza dotychczasowej aplikacji: lokalna biblioteka, Media3, Chromecast, Android Auto i obsługa Androida 10+.
