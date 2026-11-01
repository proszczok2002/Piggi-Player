# Współpraca przy Piggi Player

Dziękujemy za zainteresowanie projektem.

## Zgłaszanie błędów

Przed utworzeniem zgłoszenia sprawdź istniejące Issues. Podaj:

- wersję Piggi Player i Androida,
- model urządzenia,
- kroki pozwalające odtworzyć problem,
- oczekiwane oraz rzeczywiste zachowanie,
- logi bez prywatnych nazw plików, jeśli są potrzebne.

## Zmiany w kodzie

1. Utwórz osobną gałąź.
2. Zachowaj dotychczasowy styl Kotlin i Compose.
3. Nie dodawaj telemetrii, reklam ani zewnętrznych kont.
4. Dodaj testy dla nowej logiki, jeśli jest to możliwe.
5. Uruchom:

```shell
./gradlew testDebugUnitTest lintDebug assembleDebug
```

6. Opisz zmianę i sposób jej przetestowania w Pull Request.

Publikowanie oficjalnych APK i używanie klucza produkcyjnego pozostaje wyłącznie po stronie właściciela projektu.
