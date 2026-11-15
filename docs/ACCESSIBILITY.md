# Dostępność i sterowanie zewnętrzne

Piggi Player obsługuje TalkBack przez standardowe elementy Androida i semantykę Compose: opisy przycisków, nagłówki, stany przełączników, suwaki i akcje dodatkowe. Pełny odtwarzacz otwiera osobne okno dostępności, aby jego nawigacja nie przechodziła do Biblioteki znajdującej się pod spodem.

Na okładce dostępne są akcje „Następny utwór”, „Poprzedni utwór” i „Zminimalizuj odtwarzacz”. Na suwaku pozycji można wybrać „Cofnij o 10 sekund” lub „Przewiń do przodu o 10 sekund”. Te same czynności nie wymagają wykonywania gestów przeciągania okładki.

Czas jest przekazywany czytnikowi jako czas trwania, np. „1 godzina 53 minuty 49 sekund”. Przyciski losowania, powtarzania i timera udostępniają bieżący stan. Zmiana utworu w miniaturze ogłasza dane utworu, bez ponownego odczytywania całego rzędu przycisków.

## Przyciski głośności

Android nie udostępnia zwykłemu odtwarzaczowi uniwersalnego sposobu przejmowania długiego przytrzymania przycisków głośności przy wygaszonym ekranie. Skrót ustawia się w funkcji producenta lub w narzędziu, które zamienia przyciski głośności na standardowe polecenia multimedialne. Mogą to być Pixel Center, aplikacja Volume lub Sound Assistant na obsługiwanych Samsungach.

Piggi Player przyjmuje standardowe polecenia następnego/poprzedniego utworu oraz odtwarzania/pauzy przez sesję multimedialną. Ręczna zmiana utworu zawija kolejkę, także przy wyłączonym powtarzaniu. Tryb losowy uwzględnia kolejność odtwarzacza. Polecenie „poprzedni” przechodzi do poprzedniego pliku, zamiast wracać tylko do początku bieżącego.

Jeśli dodatek wymaga dostępu do powiadomień, musi otrzymać ten dostęp od użytkownika. Projekt deklaruje widoczność usług powiadomień i dostępności, aby sprawdzanie uprawnień przez Media3 nie odrzucało uprawnionego dodatku z powodu filtrowania pakietów Androida. Nie wymaga to uprawnienia do odczytu wszystkich zainstalowanych aplikacji. Domyślne ograniczenia sterowania dla nieuprawnionych kontrolerów pozostają aktywne.

Jeśli skrót nie działa:

1. Uruchom odtwarzanie w Piggi Player i sprawdź następny/poprzedni na jego ekranie.
2. Włącz skrót w narzędziu telefonu i wymagane przez nie usługi.
3. Sprawdź, czy narzędzie steruje właściwą aplikacją oraz czy skrót dotyczy włączonego, czy wygaszonego ekranu.
4. Sprawdź ustawienia pracy w tle tego narzędzia zgodnie z instrukcjami producenta telefonu.

Sama zgodność z poleceniami sesji nie gwarantuje działania każdego dodatku na każdym modelu i każdej wersji systemu. Wynik testów na konkretnym urządzeniu należy odróżniać od obsługi standardowego protokołu.

## Powtarzanie audytu

Narzędzia w [scripts/device-audit](../scripts/device-audit/README.md) pozwalają sprawdzać etykiety, role, stany, akcje i kontrolery przy pozostawionym aktywnym TalkBacku. Odczyt drzewa jest testem struktury. Wypowiedź wygenerowaną przez TalkBack trzeba sprawdzić oddzielnie. Wstrzyknięcie klawisza jest testem ścieżki programowej; fizyczny przycisk i inne modele telefonów wymagają oddzielnego sprawdzenia.

## Wyniki audytu 1.02, 15 listopada 2026

Urządzenie: Pixel 11, Android 17 / API 37, TalkBack 17.1. Zainstalowano podpisany wariant release przez systemowy instalator. Suma SHA-256 APK pobranego z zainstalowanego pakietu była zgodna z plikiem wydania.

| Próba | Wynik |
| --- | --- |
| Biblioteka, katalogi, menu utworu, formularz nowej playlisty, ustawienia i wyszukiwanie | Zbadano drzewo dostępności, role i rzeczywiste wypowiedzi TalkBacka podczas nawigacji. |
| Pełny odtwarzacz | Osobne okno; główna biblioteka nie pojawia się w aktywnym drzewie okna odtwarzacza. |
| Pozycja odtwarzania | TalkBack odczytuje nazwę, czas w polskich słowach i rolę suwaka. Akcje +10 i -10 sekund zmieniały pozycję dokładnie o 10 000 ms podczas pauzy. |
| Ulubione, timer, losowanie i powtarzanie | Sprawdzono zmianę stanu i wypowiedzi. Przywrócono początkowe stany po próbach. |
| Korektor | Sprawdzono przełącznik i nazwę oraz poziom pasma; przywrócono wyłączenie korektora. |
| Większy tekst i orientacja | Sprawdzono skalę czcionki 150% i 200% oraz układ poziomy. W finalnej wersji pełny odtwarzacz zachował się po obrocie. Przywrócono skalę 100% i orientację pionową. |
| Wstecz w wyszukiwaniu | Zamyka wyszukiwanie, pozostawiając aplikację na ekranie. |
| Pixel Center, wygaszony ekran, odtwarzanie | Wstrzyknięte długie naciśnięcie głośniej zmieniło indeks 0 na 1, a ciszej przywróciło 0. Log Media3 potwierdził zaufany kontroler Pixel Center. |
| Pixel Center, krótkie naciśnięcia | Głośniej i ciszej nie zmieniały indeksu utworu. |
| Zawijanie kolejki | Systemowy kontroler oraz Pixel Center: podczas pauzy długie ciszej zmieniło 0 na 30, a głośniej 30 na 0. Pauza została zachowana. |

Przykładowe wypowiedzi zapisane z działającego TalkBacka:

- „Pozycja odtwarzania: 3 sekundy z 1 godzina 53 minuty 49 sekund. Suwak”.
- „Wyłączony. Timer snu. Przycisk”.
- „Dodaj do ulubionych. Przycisk”.
- „Włączone. Odtwarzanie losowe. Pole wyboru”.
- „Cała kolejka. Zmień tryb powtarzania. Przycisk”.

Kontrola automatyczna: 10 testów jednostkowych, `lintDebug`, `lintRelease` i `assembleRelease` zakończone powodzeniem; lint zgłaszał 0 błędów i 30 ostrzeżeń dotyczących m.in. dostępnych aktualizacji zależności, istniejących zaleceń KTX i eksportowanych komponentów multimedialnych.

Granice audytu: zarejestrowano treść wypowiedzi generowanych przez TalkBack i jego wywołania syntezy mowy; nie oceniano odsłuchowo dźwięku głośnika. Klawisze wstrzykiwano programowo. Nie wykonano fizycznego naciskania przycisków, prób Samsunga z Sound Assistant, aplikacji Volume na innych telefonach, urządzenia z Androidem 10, zestawu Bluetooth, Chromecast ani Android Auto. Te scenariusze wymagają osobnych prób.
